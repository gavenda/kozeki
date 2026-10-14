package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.backup.BackupCodec
import dev.gavenda.kozeki.data.backup.BackupException
import dev.gavenda.kozeki.data.backup.LibraryBackup
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.MangaChapterEntity
import dev.gavenda.kozeki.data.db.NoteEntity
import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
import dev.gavenda.kozeki.data.db.ReadThroughEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.db.YearlyGoalEntity
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.ReadOutcome
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.settings.ReaderPreferences
import dev.gavenda.kozeki.data.settings.ReaderTheme
import dev.gavenda.kozeki.data.settings.SettingsSnapshot
import dev.gavenda.kozeki.data.settings.ThemeMode
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val stamp = SyncStamp(createdAt = 1_790_000_000_000, updatedAt = 1_790_000_500_000)

    /** A library with every kind of record, and a book with every field filled in. */
    private val sample = LibraryBackup(
        schemaVersion = 6,
        createdAt = 1_791_234_840_000,
        appVersion = "1.7",
        device = "Pixel 9",
        books = listOf(
            BookEntity(
                id = "book-1",
                title = "The Dispossessed",
                subtitle = "An Ambiguous Utopia",
                authors = listOf("Ursula K. Le Guin"),
                authorRefs = listOf(AuthorRef("204", "Ursula K. Le Guin")),
                description = "There was a wall.",
                publisher = "Harper & Row",
                publishedDate = "1974-05",
                language = "en",
                pageCount = 387,
                isbn10 = "0061054887",
                isbn13 = "9780061054884",
                categories = listOf("Science fiction"),
                coverFile = "book-1-1790000000000.jpg",
                coverUrl = "https://example.com/cover.jpg",
                customCover = true,
                state = ReadingState.READING,
                acquisition = Acquisition.PURCHASED,
                isFavorite = true,
                rating = 4.5f,
                purchasePriceMinor = 1299,
                purchaseCurrency = "EUR",
                purchasedOn = 20_700,
                purchaseLocation = "Hodges Figgis",
                purchaseMemo = "Second-hand",
                source = MetadataSource.HARDCOVER,
                sourceId = "1234",
                sourceUrl = "https://hardcover.app/books/the-dispossessed",
                matchStatus = MatchStatus.MATCHED,
                fileHash = "abc123",
                fileSize = 512_000,
                originalFileName = "dispossessed.epub",
                locator = "{\"href\":\"ch3.xhtml\"}",
                progression = 0.42,
                position = 120,
                positionCount = 286,
                chapterTitle = "Chapter 3",
                chapterIndex = 3,
                chapterCount = 13,
                lastReadAt = 1_790_000_400_000,
                physicalPage = 150,
                physicalPageCount = 400,
                sync = stamp,
            ),
            BookEntity(id = "book-2", title = "Piranesi", sync = stamp),
        ),
        readThroughs = listOf(
            ReadThroughEntity(
                id = "rt-1",
                bookId = "book-1",
                number = 1,
                startedAt = 1_790_000_000_000,
                finishedAt = 1_790_000_300_000,
                outcome = ReadOutcome.COMPLETED,
                finishedOn = 20_718,
                sync = stamp,
            ),
            ReadThroughEntity(id = "rt-2", bookId = "book-1", number = 2, startedAt = 1_790_000_350_000, sync = stamp),
        ),
        readingSessions = listOf(
            ReadingSessionEntity(
                id = "s-1",
                bookId = "book-1",
                readThroughId = "rt-2",
                startedAt = 1_790_000_360_000,
                endedAt = 1_790_000_400_000,
                durationMs = 38_000,
                day = 20_718,
                startProgression = 0.4,
                endProgression = 0.42,
                startPosition = 114,
                endPosition = 120,
                pages = 6,
                startChapter = "Chapter 2",
                endChapter = "Chapter 3",
                sync = stamp,
            ),
        ),
        physicalReadings = listOf(
            PhysicalReadingEntity(
                id = "p-1",
                bookId = "book-1",
                readThroughId = "rt-2",
                recordedAt = 1_790_000_450_000,
                day = 20_718,
                startPage = 120,
                endPage = 150,
                sync = stamp,
            ),
        ),
        notes = listOf(
            NoteEntity(
                id = "n-1",
                bookId = "book-1",
                text = "True journey is return.",
                chapterTitle = "Chapter 3",
                progression = 0.42,
                sync = stamp,
            ),
        ),
        yearlyGoals = listOf(YearlyGoalEntity(2026, books = 24, sync = stamp)),
        settings = SettingsSnapshot(
            dailyGoalMinutes = 30,
            themeMode = ThemeMode.DARK,
            readerPreferences = ReaderPreferences(fontScale = 1.2, theme = ReaderTheme.SEPIA, justify = true),
            lastCurrency = "EUR",
            librarySort = "TITLE",
            libraryDisplay = "LIST",
            searchSource = MetadataSource.GOOGLE_BOOKS,
        ),
    )

    private fun reasonFor(bytes: ByteArray, schemaVersion: Int = 6): BackupException.Reason = try {
        BackupCodec.decode(bytes, schemaVersion)
        fail("Expected the backup to be refused")
        error("unreachable")
    } catch (e: BackupException) {
        e.reason
    }

    private fun json(encoded: ByteArray): JsonObject =
        Json.parseToJsonElement(GZIPInputStream(encoded.inputStream()).use { it.readBytes() }.decodeToString()) as JsonObject

    /** Every name used anywhere in [element], however deep. */
    private fun names(element: JsonElement): Set<String> = when (element) {
        is JsonObject -> element.keys + element.values.flatMap(::names)
        is JsonArray -> element.flatMap(::names).toSet()
        else -> emptySet()
    }

    @Test
    fun `what is written is read back as it was`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample), schemaVersion = 6))
    }

    /**
     * The file is a backup as the first build that made them wrote it. A column added since has to
     * come with a default for this to go on passing, which is what keeps the backups already made readable.
     */
    @Test
    fun `a backup written when the format was new is still read`() {
        val file = javaClass.getResource("/backup-format-1.json")!!.readBytes()

        assertEquals(sample, BackupCodec.decode(file, schemaVersion = 6))
    }

    @Test
    fun `manga chapters are written and read back`() {
        val withChapters = sample.copy(
            schemaVersion = 7,
            mangaChapters = listOf(
                MangaChapterEntity(
                    bookId = "book-2",
                    url = "/chapter/1",
                    name = "Chapter 1",
                    chapterNumber = 1f,
                    scanlator = "Group",
                    uploadedAt = 1_790_000_000_000,
                    sourceOrder = 3,
                    read = true,
                    lastPageRead = 17,
                    pageCount = 18,
                    sync = stamp,
                ),
            ),
        )

        assertEquals(withChapters, BackupCodec.decode(BackupCodec.encode(withChapters), schemaVersion = 7))
    }

    @Test
    fun `a backup from an older database goes into a newer one`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample), schemaVersion = 9))
    }

    @Test
    fun `a backup from a newer database is refused`() {
        assertEquals(BackupException.Reason.TOO_NEW, reasonFor(BackupCodec.encode(sample), schemaVersion = 5))
    }

    @Test
    fun `a backup in a newer format is refused before anything in it is read`() {
        val newer = """{ "format": 2, "schemaVersion": 6, "books": "not what this build expects" }"""

        assertEquals(BackupException.Reason.TOO_NEW, reasonFor(newer.encodeToByteArray()))
    }

    @Test
    fun `what is not a backup is refused`() {
        assertEquals(BackupException.Reason.UNREADABLE, reasonFor(byteArrayOf(1, 2, 3, 4)))
        assertEquals(BackupException.Reason.UNREADABLE, reasonFor("""{ "books": [] }""".encodeToByteArray()))
        // Cut off halfway, as a download that broke would be.
        val whole = BackupCodec.encode(sample)
        assertEquals(BackupException.Reason.UNREADABLE, reasonFor(whole.copyOf(whole.size / 2)))
    }

    @Test
    fun `what this build does not know is passed over`() {
        val backup = """
            { "format": 1, "schemaVersion": 6, "createdAt": 1, "appVersion": "1.7", "device": "Pixel 9",
              "somethingNew": { "a": 1 },
              "settings": { "themeMode": "AMOLED", "dailyGoalMinutes": 45 } }
        """.trimIndent()

        val settings = BackupCodec.decode(backup.encodeToByteArray(), schemaVersion = 6).settings

        assertNull(settings.themeMode)
        assertEquals(45, settings.dailyGoalMinutes)
    }

    @Test
    fun `a backup holds the records and the settings and nothing else`() {
        val written = json(BackupCodec.encode(sample))

        assertEquals(
            setOf(
                "format", "schemaVersion", "createdAt", "appVersion", "device", "books", "readThroughs",
                "readingSessions", "physicalReadings", "notes", "yearlyGoals", "mangaChapters", "mangaTracks", "settings",
            ),
            written.keys,
        )
        assertEquals(
            setOf(
                "dailyGoalMinutes", "themeMode", "readerPreferences", "lastCurrency", "librarySort",
                "libraryDisplay", "searchSource",
            ),
            (written.getValue("settings") as JsonObject).keys,
        )
        // Nothing a sign-in rests on, and not the account the backup goes to either.
        val suspect = Regex("token|secret|password|credential|email|account", RegexOption.IGNORE_CASE)
        assertTrue(names(written).none { suspect.containsMatchIn(it) })
    }
}
