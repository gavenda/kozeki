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
        MangaChapterEntity::class,
        MangaTrackEntity::class,
    ],
    version = 8,
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
    abstract fun mangaChapterDao(): MangaChapterDao
    abstract fun mangaTrackDao(): MangaTrackDao
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
                    AddMangaChapters,
                    AddMangaTracks,
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

        /** Manga are read chapter by chapter, and the user's place in each is kept. */
        private val AddMangaChapters = object : Migration(6, 7) {
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

        /** A manga can be followed on AniList, and what it was last told is kept. */
        private val AddMangaTracks = object : Migration(7, 8) {
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

        /** A purchase can carry a memo. None of the ones already on record has one. */
        private val AddPurchaseMemo = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN purchaseMemo TEXT")
            }
        }
    }
}
