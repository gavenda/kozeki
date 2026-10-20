package dev.gavenda.kozeki.data.backup

import androidx.room.withTransaction
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.files.BookStorage
import dev.gavenda.kozeki.data.settings.SettingsRepository
import java.time.Clock

/** The library as something to copy and to put back, which is all a backup needs of it. */
interface LibraryArchive {

    /** Version of the database a backup is put back into. */
    val schemaVersion: Int

    suspend fun snapshot(): LibraryBackup

    /** What [restore] would do with [backup], without doing it. */
    suspend fun preview(backup: LibraryBackup): RestorePlan

    /** Replaces the library with [backup] and says what that did. */
    suspend fun restore(backup: LibraryBackup): RestorePlan
}

/** What putting a backup back does to the library it replaces. */
data class RestorePlan(
    val info: BackupInfo,
    /** Books here that the backup does not have. They go, and their EPUBs with them. */
    val booksRemoved: Int,
    /** Books of the backup whose EPUB is not on this device and has to be imported again. */
    val booksWithoutFile: Int,
)

/**
 * Copies the library out of the database and puts a copy back in its place.
 *
 * Putting one back replaces every record, where a sync would merge them: what is here and not in
 * the backup is gone afterwards. The files are not part of a backup, so the ones on the device
 * are matched to the books that come back, and a book whose EPUB is not here waits for it to be
 * imported again.
 */
class BackupRepository(
    private val db: KozekiDatabase,
    private val storage: BookStorage,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val appVersion: String,
    private val device: String,
) : LibraryArchive {

    private val dao = db.backupDao()

    override val schemaVersion: Int get() = db.openHelper.readableDatabase.version

    override suspend fun snapshot(): LibraryBackup {
        val savedSettings = settings.snapshot()
        // One transaction, so a session written meanwhile is not copied without its book.
        return db.withTransaction {
            LibraryBackup(
                schemaVersion = schemaVersion,
                createdAt = clock.millis(),
                appVersion = appVersion,
                device = device,
                books = dao.books(),
                readThroughs = dao.readThroughs(),
                readingSessions = dao.readingSessions(),
                physicalReadings = dao.physicalReadings(),
                notes = dao.notes(),
                yearlyGoals = dao.yearlyGoals(),
                comicChapters = dao.comicChapters(),
                comicTracks = dao.comicTracks(),
                settings = savedSettings,
            )
        }
    }

    override suspend fun preview(backup: LibraryBackup): RestorePlan = layOut(backup).plan(backup)

    override suspend fun restore(backup: LibraryBackup): RestorePlan {
        val layout = layOut(backup)
        val bookIds = layout.books.mapTo(HashSet()) { it.id }
        db.withTransaction {
            dao.clearBooks()
            dao.clearReadThroughs()
            dao.clearReadingSessions()
            dao.clearPhysicalReadings()
            dao.clearNotes()
            dao.clearYearlyGoals()
            dao.clearComicChapters()
            dao.clearComicTracks()
            dao.putBooks(layout.books)
            // A row of a book the backup does not have would count in the statistics under no title.
            dao.putReadThroughs(backup.readThroughs.filter { it.bookId in bookIds })
            dao.putReadingSessions(backup.readingSessions.filter { it.bookId in bookIds })
            dao.putPhysicalReadings(backup.physicalReadings.filter { it.bookId in bookIds })
            dao.putNotes(backup.notes.filter { it.bookId in bookIds })
            dao.putYearlyGoals(backup.yearlyGoals)
            dao.putComicChapters(backup.comicChapters.filter { it.bookId in bookIds })
            dao.putComicTracks(backup.comicTracks.filter { it.bookId in bookIds })
        }
        storage.keepOnly(layout.epubs, layout.books.mapNotNullTo(HashSet()) { it.coverFile })
        settings.restore(backup.settings)
        return layout.plan(backup)
    }

    private suspend fun layOut(backup: LibraryBackup): RestoreLayout = layOutRestore(
        incoming = backup.books,
        here = dao.books(),
        hasEpub = storage::hasEpub,
        hasCover = { storage.coverFile(it).isFile },
    )
}

/** How the books of a backup line up with the books and files that are on the device. */
internal class RestoreLayout(
    /** The books as they go in, each with a cover only if its picture is here. */
    val books: List<BookEntity>,
    /** The book each EPUB that stays is filed under now, mapped onto the book that gets it. */
    val epubs: Map<String, String>,
    private val booksRemoved: Int,
) {
    fun plan(backup: LibraryBackup) = RestorePlan(
        info = BackupInfo(backup.createdAt, backup.device, books.size),
        booksRemoved = booksRemoved,
        booksWithoutFile = books.count { it.fileHash != null && it.id !in epubs.values },
    )
}

/**
 * Matches the books of a backup to what the device holds. A book takes the EPUB that is the same
 * file as its own was, going by the hash, whichever book has it now: the same EPUB imported on a
 * new device before the backup is put back is filed under an ID of its own. The cover goes the
 * same way, since a backup has the name of a picture and not the picture.
 */
@Suppress("DestructuringDeclaration")
internal fun layOutRestore(
    incoming: List<BookEntity>,
    here: List<BookEntity>,
    hasEpub: (bookId: String) -> Boolean,
    hasCover: (name: String) -> Boolean,
): RestoreLayout {
    val hereById = here.associateBy { it.id }
    val holders = here.filter { it.fileHash != null && hasEpub(it.id) }
    val spare = holders.groupByTo(HashMap(), { it.fileHash }, { it.id })
    // Incoming book to the book whose EPUB it gets.
    val source = HashMap<String, String>()
    // A book that is here under its own ID keeps its file before any other book is given one.
    for (book in incoming) {
        if (book.fileHash != null && spare[book.fileHash]?.remove(book.id) == true) source[book.id] = book.id
    }
    for (book in incoming) {
        if (book.fileHash == null || book.id in source) continue
        source[book.id] = spare[book.fileHash]?.removeFirstOrNull() ?: continue
    }

    val coversTaken = HashSet<String>()
    val books = incoming.map { book ->
        // The book here that this one takes the place of, whose cover it can keep.
        val replaced = hereById[book.id] ?: source[book.id]?.let(hereById::get)
        val cover = listOfNotNull(book, replaced)
            .firstOrNull { it.coverFile != null && hasCover(it.coverFile) && coversTaken.add(it.coverFile) }
        book.copy(coverFile = cover?.coverFile, customCover = cover?.customCover == true)
    }

    val incomingIds = incoming.mapTo(HashSet()) { it.id }
    val givingFile = source.values.toSet()
    return RestoreLayout(
        books = books,
        epubs = source.entries.associate { (book, holder) -> holder to book },
        booksRemoved = here.count { it.id !in incomingIds && it.id !in givingFile },
    )
}
