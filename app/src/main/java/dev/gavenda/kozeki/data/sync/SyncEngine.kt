package dev.gavenda.kozeki.data.sync

import android.util.Log
import androidx.room.withTransaction
import dev.gavenda.kozeki.data.account.Account
import dev.gavenda.kozeki.data.account.AccountAuth
import dev.gavenda.kozeki.data.account.AccountState
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.db.isComic
import dev.gavenda.kozeki.data.files.BookStorage
import dev.gavenda.kozeki.data.settings.SettingsRepository
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/** What the sync is doing, for the screens that show it. */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    /** [sent] of [total] rows that were waiting have gone, and whatever changed elsewhere is coming down. */
    data class Running(val sent: Int, val total: Int) : SyncStatus

    /** [code] is the server's answer when it refused, or null. */
    data class Failed(val reason: SyncFailure, val code: Int? = null) : SyncStatus
}

enum class SyncFailure {
    /** The server could not be reached, or no fresh token could be had. */
    OFFLINE,

    /** The server answered and refused. */
    REFUSED,
}

/**
 * Keeps the book side of the library in step with the account on the server.
 *
 * Room stays the library's own copy, and the app works on it as before. What changed since it was
 * last synced is pushed, and what changed on the server since the last pull comes down, in the
 * same requests. Every row carries when it was last edited, and the later edit wins wherever two
 * devices changed the same row. The first sync after signing in is also how a library from before
 * there was an account gets to the server: none of its rows had been sent.
 */
class SyncEngine(
    private val db: KozekiDatabase,
    private val api: SyncApi,
    private val store: SyncStore,
    private val settings: SettingsRepository,
    private val storage: BookStorage,
    private val auth: AccountAuth,
    private val clock: Clock,
) {
    private val dao = db.syncDao()
    private val lock = Mutex()
    private val coversNotOnServer = mutableSetOf<String>()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /**
     * Makes this device's library [account]'s. The first account to sign in takes over the
     * library as it is, to be sent on the first sync. Another account after it starts from an
     * empty library and gets its own from the server; the comics stay, being nobody's.
     */
    suspend fun adopt(account: Account) = lock.withLock {
        val owner = store.owner()
        if (owner == account.id) return@withLock
        if (owner != null) {
            db.withTransaction {
                dao.forgetReadThroughs()
                dao.forgetReadingSessions()
                dao.forgetPhysicalReadings()
                dao.forgetNotes()
                dao.forgetShelfBooks()
                dao.forgetEmptyShelves()
                dao.forgetYearlyGoals()
                dao.forgetBooks()
            }
        }
        settings.unsendSharedSettings()
        store.startOver(account.id)
    }

    /**
     * A backup was put back over the synced library. Its rows are sent again, and everything on
     * the server comes down again, the later edit of each row winning.
     */
    suspend fun startOverAfterRestore() = lock.withLock {
        db.withTransaction {
            dao.unsendBooks()
            dao.unsendReadThroughs()
            dao.unsendReadingSessions()
            dao.unsendPhysicalReadings()
            dao.unsendNotes()
            dao.unsendYearlyGoals()
            dao.unsendShelves()
            dao.unsendShelfBooks()
        }
        settings.unsendSharedSettings()
        store.resetCursor()
    }

    /** How many books the first sync would copy to the account. */
    suspend fun booksToCopy(): Int = dao.bookCount()

    /** Whether anything changed here that the server has not had yet. */
    suspend fun hasUnsent(): Boolean = dao.unsentCount() > 0 || settings.unsentSharedSettings().isNotEmpty()

    /**
     * Sends what is waiting and takes what changed elsewhere, until neither is left.
     *
     * @return false when it did not get through, which [status] says why.
     */
    suspend fun sync(): Boolean = lock.withLock {
        val account = (auth.state.value as? AccountState.SignedIn)?.account
        // Signed out, or the library not handed to this account yet: nothing can be sent, and a screen
        // waiting on the sync must hear so rather than wait on.
        if (account == null || store.owner() != account.id) {
            _status.value = SyncStatus.Failed(SyncFailure.OFFLINE)
            return@withLock false
        }
        try {
            settleUnsentDeletions()
            val total = dao.unsentCount() + settings.unsentSharedSettings().size
            var sent = 0
            _status.value = SyncStatus.Running(0, total)

            var rounds = 0
            while (rounds++ < MAX_ROUNDS) {
                val push = unsent(BATCH)
                val response = api.sync(SyncRequest(store.cursor(), push))
                db.withTransaction {
                    markSent(push)
                    receive(response.rows)
                }
                push.settings.forEach { settings.sentSharedSetting(it.key, it.sync.updatedAt) }
                response.rows.settings.forEach { settings.receiveSharedSetting(it) }
                store.setCursor(response.cursor)

                sent += push.size
                _status.value = SyncStatus.Running(sent, maxOf(total, sent))
                if (push.size == 0 && !response.more) break
            }

            syncCovers()
            store.finished(clock.millis())
            _status.value = SyncStatus.Idle
            true
        } catch (e: SyncException) {
            Log.w(TAG, "Sync stopped", e)
            _status.value = when (e) {
                is SyncException.Refused -> SyncStatus.Failed(SyncFailure.REFUSED, e.code)
                is SyncException.Offline -> SyncStatus.Failed(SyncFailure.OFFLINE)
            }
            false
        } catch (e: SerializationException) {
            // An answer this version cannot read. Nothing was applied from it.
            Log.w(TAG, "Sync answer unreadable", e)
            _status.value = SyncStatus.Failed(SyncFailure.REFUSED)
            false
        }
    }

    private suspend fun settleUnsentDeletions() = db.withTransaction {
        dao.settleUnsentBooks()
        dao.settleUnsentReadThroughs()
        dao.settleUnsentReadingSessions()
        dao.settleUnsentPhysicalReadings()
        dao.settleUnsentNotes()
        dao.settleUnsentYearlyGoals()
        dao.settleUnsentShelves()
        dao.settleUnsentShelfBooks()
    }

    /** Up to [limit] rows waiting to be sent, parents before the rows that point at them. */
    private suspend fun unsent(limit: Int): SyncRows {
        var room = limit
        fun <T> take(rows: List<T>): List<T> = rows.also { room -= it.size }
        val books = take(dao.unsentBooks(room))
        val readThroughs = take(if (room > 0) dao.unsentReadThroughs(room) else emptyList())
        val sessions = take(if (room > 0) dao.unsentReadingSessions(room) else emptyList())
        val physical = take(if (room > 0) dao.unsentPhysicalReadings(room) else emptyList())
        val notes = take(if (room > 0) dao.unsentNotes(room) else emptyList())
        val goals = take(if (room > 0) dao.unsentYearlyGoals(room) else emptyList())
        val shelves = take(if (room > 0) dao.unsentShelves(room) else emptyList())
        val shelfBooks = take(if (room > 0) dao.unsentShelfBooks(room) else emptyList())
        val shared = if (room > 0) settings.unsentSharedSettings() else emptyList()
        return SyncRows(books, readThroughs, sessions, physical, notes, goals, shelves, shelfBooks, shared)
    }

    private suspend fun markSent(rows: SyncRows) {
        rows.books.forEach { dao.sentBook(it.id, it.version) }
        rows.readThroughs.forEach { dao.sentReadThrough(it.id, it.sync.updatedAt) }
        rows.readingSessions.forEach { dao.sentReadingSession(it.id, it.sync.updatedAt) }
        rows.physicalReadings.forEach { dao.sentPhysicalReading(it.id, it.sync.updatedAt) }
        rows.notes.forEach { dao.sentNote(it.id, it.sync.updatedAt) }
        rows.yearlyGoals.forEach { dao.sentYearlyGoal(it.year, it.sync.updatedAt) }
        rows.shelves.forEach { dao.sentShelf(it.id, it.sync.updatedAt) }
        rows.shelfBooks.forEach { dao.sentShelfBook(it.shelfId, it.bookId, it.sync.updatedAt) }
    }

    /**
     * Takes each row from the server unless the one here was edited later. What is taken counts
     * as sent, being the server's own version.
     */
    private suspend fun receive(rows: SyncRows) {
        fun SyncStamp.received() = copy(syncedAt = updatedAt)
        fun SyncStamp.wins(local: Long?) = local == null || updatedAt >= local

        rows.books.forEach { row ->
            val local = dao.book(row.id)
            // An id the server has for a book that is a comic here cannot happen unless something went wrong; the comic wins.
            if (local?.isComic == true) return@forEach
            mergeBook(local, row)?.let { dao.putBook(it) }
        }
        rows.readThroughs.forEach { row ->
            if (row.sync.wins(dao.readThroughUpdatedAt(row.id))) dao.putReadThrough(row.copy(sync = row.sync.received()))
        }
        rows.readingSessions.forEach { row ->
            if (row.sync.wins(dao.readingSessionUpdatedAt(row.id))) dao.putReadingSession(row.copy(sync = row.sync.received()))
        }
        rows.physicalReadings.forEach { row ->
            if (row.sync.wins(dao.physicalReadingUpdatedAt(row.id))) dao.putPhysicalReading(row.copy(sync = row.sync.received()))
        }
        rows.notes.forEach { row ->
            if (row.sync.wins(dao.noteUpdatedAt(row.id))) dao.putNote(row.copy(sync = row.sync.received()))
        }
        rows.yearlyGoals.forEach { row ->
            if (row.sync.wins(dao.yearlyGoalUpdatedAt(row.year))) dao.putYearlyGoal(row.copy(sync = row.sync.received()))
        }
        rows.shelves.forEach { row ->
            if (!row.sync.wins(dao.shelfUpdatedAt(row.id))) return@forEach
            dao.putShelf(row.copy(sync = row.sync.received()))
            row.sync.deletedAt?.let { dao.removeComicsFromShelf(row.id, it) }
        }
        rows.shelfBooks.forEach { row ->
            if (row.sync.wins(dao.shelfBookUpdatedAt(row.shelfId, row.bookId))) dao.putShelfBook(row.copy(sync = row.sync.received()))
        }
    }

    /**
     * Sends the covers the server does not have, and fetches the ones this device does not.
     * Covers are best effort: one that fails is tried again on the next sync.
     */
    private suspend fun syncCovers() {
        // A name from the server is only ever a file name; anything else would write outside the covers.
        val named = dao.coverFiles().filter { COVER_NAME.matches(it) }
        val known = store.uploadedCovers()

        val toCheck = named.filter { it !in known && storage.coverFile(it).isFile }
        toCheck.chunked(COVER_CHUNK).forEach { chunk ->
            val missing = api.missingCovers(chunk).toSet()
            val present = chunk.filter { name ->
                if (name !in missing) return@filter true
                runCatching { api.uploadCover(name, storage.coverFile(name)) }
                    .onFailure { Log.w(TAG, "Cover upload failed: $name", it) }
                    .isSuccess
            }
            store.addUploadedCovers(present)
        }

        // A cover the server does not have is not asked for again until the app starts over.
        val fetched = named.filter { !storage.coverFile(it).isFile && it !in coversNotOnServer }.filter { name ->
            val found = runCatching { api.downloadCover(name, storage.coverFile(name)) }
                .onFailure { Log.w(TAG, "Cover download failed: $name", it) }
                .getOrNull()
            if (found == false) coversNotOnServer += name
            found == true
        }
        store.addUploadedCovers(fetched)
    }

    private companion object {
        const val TAG = "SyncEngine"
        // Rows sent per request. The server writes each table in one statement whatever the count,
        // so this is about the size of a request and the Worker's time on it.
        const val BATCH = 100
        const val COVER_CHUNK = 200

        /** As `COVER_NAME` in webapp/shared/tables.ts. */
        val COVER_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.(jpe?g|png|webp)$")

        // A library of tens of thousands of rows takes a few hundred; this only stops a loop that never ends.
        const val MAX_ROUNDS = 10_000
    }
}
