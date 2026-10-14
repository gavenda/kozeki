package dev.gavenda.kozeki.data.tracking

import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.MangaTrackEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.manga.MangaChapterRepository
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.model.MangaChapter
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.TrackStatus
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.tracking.anilist.AniListAccount
import dev.gavenda.kozeki.data.tracking.anilist.AniListApi
import dev.gavenda.kozeki.data.tracking.anilist.AniListAuth
import dev.gavenda.kozeki.data.tracking.anilist.AniListEntry
import dev.gavenda.kozeki.data.tracking.anilist.AniListEntryUpdate
import dev.gavenda.kozeki.data.tracking.anilist.AniListMedia
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps manga in step with the user's AniList, the way Mihon does: chapters read there are read
 * here, chapters finished here are told to AniList, and the status goes with them.
 *
 * What cannot be told right away, because AniList could not be reached, stays marked on the track
 * and is sent by [retryPending] once it can be.
 */
class TrackRepository(
    db: KozekiDatabase,
    private val api: AniListApi,
    private val auth: AniListAuth,
    private val chapters: MangaChapterRepository,
    private val library: LibraryRepository,
    private val clock: Clock,
    scope: CoroutineScope,
    private val scheduleRetry: () -> Unit,
) {
    private val tracks = db.mangaTrackDao()
    private val lock = Mutex()

    init {
        scope.launch { chapters.chapterReads.collect { bookId -> pushProgress(bookId) } }
        scope.launch { library.stateChanges.collect { (bookId, state) -> pushStatus(bookId, TrackStatus.of(state)) } }
        // Whatever waited for a sign-in goes out as soon as there is one.
        scope.launch {
            auth.account.filterIsInstance<AniListAccount.SignedIn>().collect { retryPending() }
        }
    }

    fun observe(bookId: String): Flow<MangaTrackEntity?> = tracks.observe(bookId)

    /** Manga on AniList that match [text]. */
    suspend fun search(text: String): List<AniListMedia> = withToken { api.search(it, text) }

    /**
     * Follows the book as [media], the manga the user picked. An entry the user already has for it
     * is taken as it is, and what is read here and there is then brought together.
     */
    suspend fun bind(bookId: String, media: AniListMedia) {
        val book = library.getBook(bookId) ?: return
        val entry = media.entry
        val now = clock.millis()
        lock.withLock {
            val track = MangaTrackEntity(
                bookId = bookId,
                mediaId = media.id,
                entryId = entry?.id,
                title = media.title,
                siteUrl = media.siteUrl,
                coverUrl = media.coverUrl,
                totalChapters = media.chapters,
                publication = media.publication,
                status = entry?.status ?: TrackStatus.of(book.state),
                progress = entry?.progress ?: 0,
                score = entry?.score ?: 0,
                startedOn = entry?.startedOn?.toEpochDay(),
                completedOn = entry?.completedOn?.toEpochDay(),
                // No entry yet: following it is what puts the manga on the list.
                pendingPush = entry == null,
                sync = SyncStamp.created(now),
            )
            tracks.upsert(track)
            reconcileLocked(track)
        }
    }

    /** Stops following. The entry on AniList is removed too when [fromList] is true. */
    suspend fun unbind(bookId: String, fromList: Boolean) {
        lock.withLock {
            val track = tracks.get(bookId) ?: return
            if (fromList && track.entryId != null) withToken { api.delete(it, track.entryId) }
            tracks.upsert(track.copy(pendingPush = false, sync = track.sync.deleted(clock.millis())))
        }
    }

    /** Asks AniList how the manga stands and brings both sides together. */
    suspend fun refresh(bookId: String) {
        lock.withLock {
            val track = tracks.get(bookId) ?: return
            if (track.pendingPush) {
                reconcileLocked(track)
                return
            }
            val media = withToken { api.media(it, track.mediaId) } ?: return
            val entry = media.entry
            var next = track.copy(
                title = media.title,
                siteUrl = media.siteUrl ?: track.siteUrl,
                coverUrl = media.coverUrl ?: track.coverUrl,
                totalChapters = media.chapters,
                publication = media.publication ?: track.publication,
            )
            if (entry == null) {
                // Taken off the list there; the next chapter read puts it back.
                next = next.copy(entryId = null)
            } else {
                next = applyRemote(next, entry)
            }
            if (next != track) tracks.upsert(next.copy(sync = next.sync.touched(clock.millis())))
            if (entry != null) pullIntoBook(track, next)
            reconcileLocked(next)
        }
    }

    /** Changes what is on the track, here and on AniList. A higher progress marks the chapters read. */
    suspend fun edit(bookId: String, change: (MangaTrackEntity) -> MangaTrackEntity) {
        lock.withLock {
            val track = tracks.get(bookId) ?: return
            val next = change(track)
            if (next == track) return
            tracks.upsert(next.copy(pendingPush = true, sync = next.sync.touched(clock.millis())))
            pullIntoBook(track, next)
            pushLocked(next, throwErrors = true)
        }
    }

    /** Sends what AniList was not told earlier. True when something is still waiting. */
    suspend fun retryPending(): Boolean {
        if (auth.accessToken() == null) return false
        return lock.withLock {
            tracks.pending().fold(false) { waiting, track -> pushLocked(track, throwErrors = false).not() || waiting }
        }
    }

    // ---- What happened in the app ---------------------------------------------------------

    private suspend fun pushProgress(bookId: String) {
        lock.withLock {
            val track = tracks.get(bookId) ?: return
            val progress = highestChapterRead(chapters.get(bookId))
            val next = advance(track, progress, today())
            if (next == track) return
            tracks.upsert(next.copy(pendingPush = true, sync = next.sync.touched(clock.millis())))
            pushLocked(next, throwErrors = false)
        }
    }

    private suspend fun pushStatus(bookId: String, status: TrackStatus) {
        lock.withLock {
            val track = tracks.get(bookId) ?: return
            val next = withStatus(track, status, today())
            if (next == track) return
            tracks.upsert(next.copy(pendingPush = true, sync = next.sync.touched(clock.millis())))
            pushLocked(next, throwErrors = false)
        }
    }

    // ---- Both sides together ---------------------------------------------------------------

    /** Sends what waits, then lets whichever side is ahead in chapters bring the other along. */
    private suspend fun reconcileLocked(track: MangaTrackEntity) {
        var current = track
        if (current.pendingPush && !pushLocked(current, throwErrors = false)) return
        current = tracks.get(current.bookId) ?: return
        pullIntoBook(null, current)
        val next = advance(current, highestChapterRead(chapters.get(current.bookId)), today())
        if (next != current) {
            tracks.upsert(next.copy(pendingPush = true, sync = next.sync.touched(clock.millis())))
            pushLocked(next, throwErrors = false)
        }
    }

    /** What AniList says replaces what was kept, except score and dates it has none of. */
    private fun applyRemote(track: MangaTrackEntity, entry: AniListEntry): MangaTrackEntity = track.copy(
        entryId = entry.id,
        status = entry.status,
        progress = entry.progress,
        score = entry.score,
        startedOn = entry.startedOn?.toEpochDay() ?: track.startedOn,
        completedOn = entry.completedOn?.toEpochDay() ?: track.completedOn,
    )

    /**
     * Chapters up to the track's progress are read here, and the book takes the status the track
     * has. [before] is how the track stood, and null for a first look, where both count as new.
     */
    private suspend fun pullIntoBook(before: MangaTrackEntity?, after: MangaTrackEntity) {
        if (before == null || after.progress > before.progress) {
            val unread = chaptersToMarkRead(chapters.get(after.bookId), after.progress)
            if (unread.isNotEmpty()) chapters.markRead(after.bookId, unread, read = true)
        }
        if (before == null || after.status != before.status) {
            val book = library.getBook(after.bookId)
            if (book != null && TrackStatus.of(book.state) != after.status &&
                !(after.status == TrackStatus.REPEATING && book.state == ReadingState.READING)
            ) {
                library.setState(after.bookId, after.status.readingState)
            }
        }
    }

    /**
     * Tells AniList what the track holds. On success the track becomes what AniList answered.
     * Returns true when nothing waits afterwards.
     */
    private suspend fun pushLocked(track: MangaTrackEntity, throwErrors: Boolean): Boolean {
        val update = AniListEntryUpdate(
            status = track.status,
            progress = track.progress,
            score = track.score,
            startedOn = track.startedOn?.let(LocalDate::ofEpochDay),
            completedOn = track.completedOn?.let(LocalDate::ofEpochDay),
        )
        return try {
            val entry = withToken { api.save(it, track.mediaId, update) }
            tracks.upsert(
                applyRemote(track, entry).copy(pendingPush = false, sync = track.sync.touched(clock.millis())),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept marked, so that it goes out when AniList can be reached or the user signs in again.
            tracks.upsert(track.copy(pendingPush = true))
            if (e is MetadataException.Network || e is MetadataException.RateLimited || e is MetadataException.Server) {
                scheduleRetry()
            }
            if (throwErrors) throw e
            false
        }
    }

    private suspend fun <T> withToken(call: suspend (String) -> T): T {
        val token = auth.accessToken() ?: throw MetadataException.Unauthorized("Not signed in to AniList")
        return try {
            call(token)
        } catch (e: MetadataException.Unauthorized) {
            auth.invalidate()
            throw e
        }
    }

    private fun today(): Long = LocalDate.now(clock).toEpochDay()
}

/** The number of the furthest chapter read, as AniList counts: whole chapters, and none without a number. */
internal fun highestChapterRead(chapters: List<MangaChapter>): Int =
    chapters.filter { it.read }.mapNotNull { it.number }.maxOrNull()?.toInt()?.coerceAtLeast(0) ?: 0

/** The chapters that AniList's [progress] says were read and that are not yet marked so here. */
internal fun chaptersToMarkRead(chapters: List<MangaChapter>, progress: Int): List<String> =
    if (progress <= 0) emptyList() else chapters.filter { !it.read && it.number != null && it.number <= progress }.map { it.url }

/**
 * The track after reading has reached chapter [progress]. It only moves forward: finishing the last
 * chapter completes it, and the first chapter read, or reading again after a pause, makes it current.
 * [today] is an epoch day.
 */
internal fun advance(track: MangaTrackEntity, progress: Int, today: Long): MangaTrackEntity {
    if (progress <= track.progress) return track
    val moved = track.copy(progress = progress)
    val total = track.totalChapters
    return when {
        total != null && progress >= total -> moved.copy(
            status = TrackStatus.COMPLETED,
            startedOn = track.startedOn ?: today,
            completedOn = track.completedOn ?: today,
        )

        track.status == TrackStatus.PLANNING || track.status == TrackStatus.PAUSED || track.status == TrackStatus.DROPPED ->
            moved.copy(status = TrackStatus.CURRENT, startedOn = track.startedOn ?: today)

        else -> moved
    }
}

/** The track after the user sets the status of the book to [status]. [today] is an epoch day. */
internal fun withStatus(track: MangaTrackEntity, status: TrackStatus, today: Long): MangaTrackEntity {
    if (track.status == status) return track
    // Reading again is what REPEATING is, so the book going back to Reading leaves it be.
    if (track.status == TrackStatus.REPEATING && status == TrackStatus.CURRENT) return track
    return when (status) {
        TrackStatus.CURRENT -> track.copy(status = status, startedOn = track.startedOn ?: today)
        TrackStatus.COMPLETED -> track.copy(
            status = status,
            startedOn = track.startedOn ?: today,
            completedOn = track.completedOn ?: today,
            progress = maxOf(track.progress, track.totalChapters ?: 0),
        )

        else -> track.copy(status = status)
    }
}
