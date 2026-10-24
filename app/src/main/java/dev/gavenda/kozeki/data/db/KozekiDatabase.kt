package dev.gavenda.kozeki.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ReadThroughEntity::class,
        ReadingSessionEntity::class,
        PhysicalReadingEntity::class,
        NoteEntity::class,
        YearlyGoalEntity::class,
        MetadataCacheEntity::class,
        ComicChapterEntity::class,
        ComicTrackEntity::class,
        ShelfEntity::class,
        ShelfBookEntity::class,
    ],
    version = 17,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KozekiDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun readThroughDao(): ReadThroughDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun physicalReadingDao(): PhysicalReadingDao
    abstract fun noteDao(): NoteDao
    abstract fun yearlyGoalDao(): YearlyGoalDao
    abstract fun metadataCacheDao(): MetadataCacheDao
    abstract fun comicChapterDao(): ComicChapterDao
    abstract fun comicTrackDao(): ComicTrackDao
    abstract fun shelfDao(): ShelfDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): KozekiDatabase =
            Room.databaseBuilder(context, KozekiDatabase::class.java, "kozeki.db")
                .addMigrations(
                    DropGoogleBooks,
                    AddAuthorRefs,
                    AddCustomCover,
                    AddPhysicalReadings,
                    AddPurchaseMemo,
                    AddComicChapters,
                    AddComicTracks,
                    AddTrackCovers,
                    AddNewChapters,
                    AddTrackPublication,
                    RenameToComics,
                    AddShelves,
                    AddSeries,
                    AddSessionChapters,
                    EstimateSessionChapters,
                    AddDetailsEdited,
                )
                .build()

        /**
         * Google Books stopped being a source for a while. Books linked to it then went back to
         * waiting for a lookup, to be found again on Hardcover, and what was cached from it was
         * dropped. The tables themselves are unchanged.
         */
        private val DropGoogleBooks = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addPreReleaseColumns(db)
                db.execSQL(
                    "UPDATE books SET source = NULL, sourceId = NULL, sourceUrl = NULL, matchStatus = 'PENDING', " +
                        "updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000 WHERE source = 'GOOGLE_BOOKS'",
                )
                db.execSQL("DELETE FROM metadata_cache WHERE source = 'GOOGLE_BOOKS'")
            }
        }

        /**
         * Builds from before 1.0 made version 1 without the paper page counts and the place of
         * purchase, which joined it with no version change. A database from one of those gets them here.
         */
        private fun addPreReleaseColumns(db: SupportSQLiteDatabase) {
            val existing = buildSet {
                db.query("PRAGMA table_info(books)").use { cursor ->
                    val name = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(name))
                }
            }
            val added = listOf("physicalPage" to "INTEGER", "physicalPageCount" to "INTEGER", "purchaseLocation" to "TEXT")
            for ((column, type) in added) {
                if (column !in existing) db.execSQL("ALTER TABLE books ADD COLUMN $column $type")
            }
        }

        /** Books remember their authors' IDs on the source. The ones already linked get theirs looked up later. */
        private val AddAuthorRefs = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN authorRefs TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** Books can carry a cover the user picked. None of the ones already there is one. */
        private val AddCustomCover = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN customCover INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Moving the page of a physical copy on is put on record from here on. The pages entered
         * before left nothing behind to fill it with.
         */
        private val AddPhysicalReadings = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `physical_readings` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                        "`readThroughId` TEXT, `recordedAt` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                        "`startPage` INTEGER NOT NULL, `endPage` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_physical_readings_bookId` ON `physical_readings` (`bookId`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_physical_readings_day` ON `physical_readings` (`day`)")
            }
        }

        /**
         * Comics are read chapter by chapter, and the user's place in each is kept. Up to version 12
         * comics were called manga, and these steps make the tables under the names they had then.
         */
        private val AddComicChapters = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manga_chapters` (`bookId` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `chapterNumber` REAL NOT NULL, `scanlator` TEXT, " +
                        "`uploadedAt` INTEGER NOT NULL, `sourceOrder` INTEGER NOT NULL, `read` INTEGER NOT NULL, " +
                        "`lastPageRead` INTEGER NOT NULL, `pageCount` INTEGER, `memo` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncedAt` INTEGER, " +
                        "PRIMARY KEY(`bookId`, `url`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_chapters_bookId` ON `manga_chapters` (`bookId`)")
                db.execSQL("ALTER TABLE books ADD COLUMN sourceMemo TEXT")
            }
        }

        /** A comic can be followed on AniList, and what it was last told is kept. */
        private val AddComicTracks = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manga_tracks` (`bookId` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, " +
                        "`entryId` INTEGER, `title` TEXT NOT NULL, `siteUrl` TEXT, `totalChapters` INTEGER, " +
                        "`status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `score` INTEGER NOT NULL, " +
                        "`startedOn` INTEGER, `completedOn` INTEGER, `pendingPush` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                        "`syncedAt` INTEGER, PRIMARY KEY(`bookId`))",
                )
            }
        }

        /** What is followed on AniList shows its cover there. Those already followed get it on their next refresh. */
        private val AddTrackCovers = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_tracks ADD COLUMN coverUrl TEXT")
            }
        }

        /**
         * Manga are called comics. The tables are renamed, and so are the source the books found
         * through Mihon carry and the links of their covers. Cached lookups under the old name go.
         */
        private val RenameToComics = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `manga_chapters` RENAME TO `comic_chapters`")
                db.execSQL("DROP INDEX IF EXISTS `index_manga_chapters_bookId`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_comic_chapters_bookId` ON `comic_chapters` (`bookId`)")
                db.execSQL("ALTER TABLE `manga_tracks` RENAME TO `comic_tracks`")
                db.execSQL("UPDATE books SET source = 'COMICS' WHERE source = 'MANGA'")
                db.execSQL(
                    "UPDATE books SET coverUrl = 'kozeki-comic://' || substr(coverUrl, 16) " +
                        "WHERE coverUrl LIKE 'kozeki-manga://%'",
                )
                db.execSQL("DELETE FROM metadata_cache WHERE source = 'MANGA'")
            }
        }

        /** The user can gather books and comics on named shelves. */
        private val AddShelves = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shelves` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                        "`syncedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shelf_books` (`shelfId` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                        "`syncedAt` INTEGER, PRIMARY KEY(`shelfId`, `bookId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shelf_books_bookId` ON `shelf_books` (`bookId`)")
            }
        }

        /** A book can belong to a series, at a number in it. */
        private val AddSeries = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN series TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN seriesNumber REAL")
            }
        }

        /** A book remembers that its details were edited. Which of the ones already there were is not known. */
        private val AddDetailsEdited = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN detailsEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** A session counts the chapters of a comic read in it. */
        private val AddSessionChapters = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_sessions ADD COLUMN chapters INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Estimates the chapters of the comic sessions recorded before they were counted. See [estimatedChapters]. */
        private val EstimateSessionChapters = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val estimates = mutableListOf<Pair<String, Int>>()
                db.query(
                    """
                    SELECT s.id, s.startPosition, s.endPosition, s.pages, s.startChapter = s.endChapter,
                        (SELECT c.pageCount FROM comic_chapters c
                            WHERE c.bookId = s.bookId AND c.name = s.startChapter AND c.deletedAt IS NULL LIMIT 1),
                        (SELECT c.pageCount FROM comic_chapters c
                            WHERE c.bookId = s.bookId AND c.name = s.endChapter AND c.deletedAt IS NULL LIMIT 1)
                    FROM reading_sessions s
                    WHERE s.bookId IN (SELECT id FROM books WHERE source = 'COMICS')
                    """.trimIndent(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        fun int(i: Int) = if (cursor.isNull(i)) null else cursor.getInt(i)
                        val chapters = estimatedChapters(
                            startPosition = int(1),
                            endPosition = int(2),
                            pagesTurned = cursor.getInt(3),
                            sameChapter = int(4) == 1,
                            startPageCount = int(5),
                            endPageCount = int(6),
                        )
                        estimates += cursor.getString(0) to chapters
                    }
                }
                db.execSQL("UPDATE reading_sessions SET chapters = 0 WHERE chapters != 0")
                for ((id, chapters) in estimates.filter { it.second > 0 }) {
                    db.execSQL("UPDATE reading_sessions SET chapters = ? WHERE id = ?", arrayOf<Any>(chapters, id))
                }
            }
        }

        /** A track keeps whether its comic is still coming out. */
        private val AddTrackPublication = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_tracks ADD COLUMN publication TEXT")
            }
        }

        /** A comic counts the chapters that came out since it was last opened, and when it was last checked. */
        private val AddNewChapters = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN newChapters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN chaptersCheckedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** A purchase can carry a memo. None of the ones already on record has one. */
        private val AddPurchaseMemo = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN purchaseMemo TEXT")
            }
        }
    }
}

/**
 * The chapters a comic session recorded before they were counted read, going by what it kept: where
 * it started and ended, by page and chapter name, and how many pages it turned forward. A chapter
 * counts only when it was turned through to its last page, as the reader counts them now, so that
 * jumping ahead is not reading. The chapter it started in counts when it moved on to another, and the
 * one it ended in when it ended on its last page. At most those two.
 */
internal fun estimatedChapters(
    startPosition: Int?,
    endPosition: Int?,
    pagesTurned: Int,
    sameChapter: Boolean,
    startPageCount: Int?,
    endPageCount: Int?,
): Int {
    if (startPosition == null || endPosition == null) return 0
    val endedOnLast = endPageCount != null && endPosition >= endPageCount - 1
    return if (sameChapter) {
        val span = endPosition - startPosition
        if (endedOnLast && span > 0 && pagesTurned >= span * TURNED_THROUGH) 1 else 0
    } else {
        // Pages turned in the start chapter, then in the end chapter from its first page.
        val restOfStart = startPageCount?.let { it - 1 - startPosition } ?: return 0
        val startRead = restOfStart > 0 && pagesTurned >= (restOfStart + endPosition) * TURNED_THROUGH
        val endRead = endedOnLast && endPosition > 0 && startRead
        (if (startRead) 1 else 0) + (if (endRead) 1 else 0)
    }
}

/** The share of a chapter's pages that has to have been turned for it to count as read. */
private const val TURNED_THROUGH = 0.8
