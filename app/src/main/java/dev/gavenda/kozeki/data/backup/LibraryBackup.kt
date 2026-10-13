package dev.gavenda.kozeki.data.backup

import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.MangaChapterEntity
import dev.gavenda.kozeki.data.db.NoteEntity
import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
import dev.gavenda.kozeki.data.db.ReadThroughEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.YearlyGoalEntity
import dev.gavenda.kozeki.data.settings.SettingsSnapshot
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A library as it is written to a backup: the records the user's reading is made of, and the
 * settings. The calendar, the timeline and the statistics are all worked out from these.
 *
 * What is left out on purpose: the EPUBs and the cover pictures, which are files and stay on the
 * device, what was looked up in a catalogue before, which can be looked up again, and anything a
 * sign-in rests on. The Hardcover session is kept outside the database and the settings, so there
 * is no token here to leave out.
 */
@Serializable
data class LibraryBackup(
    val format: Int = FORMAT,
    /** Version of the database the rows were read from. */
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    /** The device it was made on, as its maker names it. */
    val device: String,
    val books: List<BookEntity> = emptyList(),
    val readThroughs: List<ReadThroughEntity> = emptyList(),
    val readingSessions: List<ReadingSessionEntity> = emptyList(),
    val physicalReadings: List<PhysicalReadingEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val yearlyGoals: List<YearlyGoalEntity> = emptyList(),
    /** Absent from backups made before manga could be read. */
    val mangaChapters: List<MangaChapterEntity> = emptyList(),
    val settings: SettingsSnapshot = SettingsSnapshot(),
) {
    companion object {
        /** Goes up when the file changes in a way an older build could not read. */
        const val FORMAT = 1
    }
}

/** What a backup says of itself, so it can be told apart without being fetched. */
@Serializable
data class BackupInfo(
    val createdAt: Long,
    /** Empty when the backup does not say. */
    val device: String = "",
    val books: Int = 0,
)

/**
 * The Google account backups go to and what is known of the backup there, kept so that both show
 * without a connection.
 */
@Serializable
data class DriveLink(
    val email: String,
    val photoUrl: String? = null,
    /** The backup on Drive, as it was when last looked at. */
    val latest: BackupInfo? = null,
    /**
     * [BackupInfo.createdAt] of the backup this device last made or put back. A backup on Drive
     * with another date holds something this device has not seen, and is not replaced unasked.
     */
    val seen: Long? = null,
)

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not a backup, or a damaged one. */
        UNREADABLE,

        /** Written by a build newer than this one, which may have changed what the rows mean. */
        TOO_NEW,
    }
}

/** Turns a [LibraryBackup] into the bytes of its file and back: JSON, compressed. */
object BackupCodec {

    // Defaults are written out so that a file says what it means without the build that wrote it.
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())

    fun encode(backup: LibraryBackup): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(json.encodeToString(backup).encodeToByteArray()) }
        return bytes.toByteArray()
    }

    /**
     * @param schemaVersion Version of the database the backup is to go into.
     * @throws BackupException when [bytes] cannot be read as a backup this build can take.
     */
    fun decode(bytes: ByteArray, schemaVersion: Int): LibraryBackup {
        val root = try {
            // Taken uncompressed as well, in case something on the way unpacked it.
            val compressed = bytes.size > 2 && bytes[0] == GZIP_MAGIC[0] && bytes[1] == GZIP_MAGIC[1]
            val text = if (compressed) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
            json.parseToJsonElement(text.decodeToString()).jsonObject
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.UNREADABLE, e)
        }
        // Looked at before the rest, which a newer build may have written in a way this one cannot read.
        val format = runCatching { root.getValue("format").jsonPrimitive.int }.getOrNull()
        val schema = runCatching { root.getValue("schemaVersion").jsonPrimitive.int }.getOrNull()
        if (format == null || schema == null) throw BackupException(BackupException.Reason.UNREADABLE)
        if (format > LibraryBackup.FORMAT || schema > schemaVersion) throw BackupException(BackupException.Reason.TOO_NEW)
        return try {
            json.decodeFromJsonElement<LibraryBackup>(root)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.UNREADABLE, e)
        }
    }
}
