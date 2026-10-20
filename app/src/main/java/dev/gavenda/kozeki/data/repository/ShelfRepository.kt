package dev.gavenda.kozeki.data.repository

import androidx.room.withTransaction
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.ShelfBookEntity
import dev.gavenda.kozeki.data.db.ShelfEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Shelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

/** The user's shelves, and which books are on them. */
class ShelfRepository(
    private val db: KozekiDatabase,
    private val clock: Clock,
) {
    private val dao = db.shelfDao()

    fun observeShelves(): Flow<List<Shelf>> =
        dao.observeShelves().map { rows -> rows.map { Shelf(it.shelf.id, it.shelf.name, it.bookCount) } }

    /** The IDs of the books on the shelf [shelfId]. */
    fun observeBookIds(shelfId: String): Flow<Set<String>> = dao.observeBookIds(shelfId).map { it.toSet() }

    /** For each shelf, the IDs of the books on it. */
    fun observeMemberships(): Flow<Map<String, Set<String>>> = dao.observeMemberships().map { rows ->
        rows.groupBy({ it.shelfId }, { it.bookId }).mapValues { it.value.toSet() }
    }

    /** The IDs of the shelves the book [bookId] is on. */
    fun observeShelfIdsFor(bookId: String): Flow<Set<String>> = dao.observeShelfIdsFor(bookId).map { it.toSet() }

    /** For each shelf, how many of [bookIds] are on it. */
    suspend fun membershipCounts(bookIds: Collection<String>): Map<String, Int> =
        if (bookIds.isEmpty()) {
            emptyMap()
        } else {
            bookIds.chunked(CHUNK).flatMap { dao.membershipsOf(it) }.groupingBy { it.shelfId }.eachCount()
        }

    /** Makes a shelf called [name] and returns its ID, or null for a name with nothing in it. */
    suspend fun create(name: String): String? {
        val trimmed = name.trim().ifEmpty { return null }
        val id = UUID.randomUUID().toString()
        dao.upsert(ShelfEntity(id, trimmed, SyncStamp.created(clock.millis())))
        return id
    }

    suspend fun rename(shelfId: String, name: String) {
        val trimmed = name.trim().ifEmpty { return }
        dao.rename(shelfId, trimmed, clock.millis())
    }

    /** Takes the shelf away. The books on it stay in the library. */
    suspend fun delete(shelfId: String) {
        db.withTransaction {
            val now = clock.millis()
            dao.removeAllBooks(shelfId, now)
            dao.softDelete(shelfId, now)
        }
    }

    suspend fun addBooks(shelfId: String, bookIds: Collection<String>) {
        if (bookIds.isEmpty()) return
        db.withTransaction {
            if (dao.get(shelfId) == null) return@withTransaction
            val now = clock.millis()
            dao.putBooks(bookIds.distinct().map { ShelfBookEntity(shelfId, it, SyncStamp.created(now)) })
        }
    }

    suspend fun removeBooks(shelfId: String, bookIds: Collection<String>) {
        if (bookIds.isEmpty()) return
        val now = clock.millis()
        bookIds.chunked(CHUNK).forEach { dao.removeBooks(shelfId, it, now) }
    }

    /**
     * Puts [bookIds] on the shelves in [add] and takes them off those in [remove], as one change,
     * and on a new shelf for each of the names in [newShelves].
     */
    suspend fun apply(
        bookIds: Collection<String>,
        add: Set<String>,
        remove: Set<String>,
        newShelves: List<String> = emptyList(),
    ) {
        db.withTransaction {
            add.forEach { addBooks(it, bookIds) }
            remove.forEach { removeBooks(it, bookIds) }
            newShelves.forEach { name -> create(name)?.let { addBooks(it, bookIds) } }
        }
    }

    private companion object {
        /** Under SQLite's limit on the variables of one statement. */
        const val CHUNK = 500
    }
}
