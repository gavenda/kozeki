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
        NoteEntity::class,
        YearlyGoalEntity::class,
        MetadataCacheEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KozekiDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun readThroughDao(): ReadThroughDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun noteDao(): NoteDao
    abstract fun yearlyGoalDao(): YearlyGoalDao
    abstract fun metadataCacheDao(): MetadataCacheDao

    companion object {
        fun create(context: Context): KozekiDatabase =
            Room.databaseBuilder(context, KozekiDatabase::class.java, "kozeki.db")
                .addMigrations(DropGoogleBooks, AddAuthorRefs, AddCustomCover)
                .build()

        /**
         * Google Books stopped being a source for a while. Books linked to it then went back to
         * waiting for a lookup, to be found again on Hardcover, and what was cached from it was
         * dropped. The tables themselves are unchanged.
         */
        private val DropGoogleBooks = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE books SET source = NULL, sourceId = NULL, sourceUrl = NULL, matchStatus = 'PENDING', " +
                        "updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000 WHERE source = 'GOOGLE_BOOKS'",
                )
                db.execSQL("DELETE FROM metadata_cache WHERE source = 'GOOGLE_BOOKS'")
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
    }
}
