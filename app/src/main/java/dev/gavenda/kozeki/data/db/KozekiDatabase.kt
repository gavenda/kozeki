package dev.gavenda.kozeki.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BookEntity::class,
        ReadThroughEntity::class,
        ReadingSessionEntity::class,
        NoteEntity::class,
        YearlyGoalEntity::class,
        MetadataCacheEntity::class,
    ],
    version = 1,
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
            Room.databaseBuilder(context, KozekiDatabase::class.java, "kozeki.db").build()
    }
}
