package dev.gavenda.kozeki.data.backup.local

import dev.gavenda.kozeki.data.backup.BackupCodec
import dev.gavenda.kozeki.data.backup.BackupException
import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.backup.LibraryArchive
import dev.gavenda.kozeki.data.backup.LibraryBackup
import dev.gavenda.kozeki.data.backup.RestorePlan
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Failure
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Notice
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Work
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** The local backups as they are kept between launches. */
@Serializable
data class LocalBackupConfig(
    /** The folder the user picked, as the system's URI of it, or null while none is picked. */
    val folder: String? = null,
    /** Hours between automatic backups, 0 being none. */
    val intervalHours: Int = 0,
    /** The last backup this device wrote to the folder. */
    val latest: BackupInfo? = null,
    /** Why the last backup did not get written, until one does. */
    val failure: Failure? = null,
)

/** Where the [LocalBackupConfig] is kept. */
interface LocalBackupStore {
    val localBackup: Flow<LocalBackupConfig>

    suspend fun updateLocalBackup(transform: (LocalBackupConfig) -> LocalBackupConfig)
}

/** A backup file found in the folder. */
data class FolderFile(val uri: String, val name: String)

/** The folder backups are written to, reached through the system's file picker and not a path. */
interface BackupFolder {
    /** Lets the app keep the access the picker gave it after a restart. */
    fun keepAccess(folder: String)

    fun releaseAccess(folder: String)

    /** What the folder is called, or null when the app can no longer reach it. */
    fun name(folder: String): String?

    /** @throws Exception when the folder cannot be written to. */
    fun write(folder: String, fileName: String, content: ByteArray)

    fun list(folder: String): List<FolderFile>

    fun delete(file: String)

    /** Reads a file the user picked, from this folder or any other. */
    fun read(file: String): ByteArray
}

/** The local backups, as Settings shows them. */
data class LocalBackupState(
    val folder: String? = null,
    /** Null while there is no folder, or the app has lost its access to the one picked. */
    val folderName: String? = null,
    val intervalHours: Int = 0,
    val latest: BackupInfo? = null,
    val failure: Failure? = null,
    val working: Work? = null,
    /** A backup file read and waiting for the user to say whether it replaces the library. */
    val prompt: RestorePlan? = null,
    val notice: Notice? = null,
) {
    enum class Work { BACKING_UP, READING, RESTORING }

    sealed interface Notice {
        data class Restored(val plan: RestorePlan) : Notice

        data class Failed(val failure: Failure) : Notice
    }

    enum class Failure {
        /** The folder is gone, or the app was no longer allowed into it. */
        NO_ACCESS,
        UNREADABLE,
        TOO_NEW,
        OTHER,
    }
}

/**
 * Writes backups to a folder on the device the user picked, by hand or every so often, and puts
 * one back from a file. Held for as long as the process, like the backup to Drive.
 *
 * Every backup is a file of its own, named after when it was made, and only the newest
 * [BACKUPS_KEPT] the app wrote are kept. Other files in the folder are left alone.
 */
class LocalBackup(
    private val folders: BackupFolder,
    private val library: LibraryArchive,
    private val store: LocalBackupStore,
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Called once a backup is in place, for what its books still need from the network. */
    private val onRestored: () -> Unit,
) {
    private val _state = MutableStateFlow(LocalBackupState())
    val state: StateFlow<LocalBackupState> = _state.asStateFlow()

    /** The backup [LocalBackupState.prompt] asks about. */
    @Volatile private var picked: LibraryBackup? = null

    /** A backup by hand and an automatic one are not written at once. */
    private val writing = Mutex()

    init {
        scope.launch {
            store.localBackup.collect { config ->
                val name = config.folder?.let { withContext(io) { runCatching { folders.name(it) }.getOrNull() } }
                _state.update {
                    it.copy(
                        folder = config.folder,
                        folderName = name,
                        intervalHours = config.intervalHours,
                        latest = config.latest,
                        failure = config.failure,
                    )
                }
            }
        }
    }

    /** Backs up to [folder] from now on, which the picker has just given the app. */
    fun pickFolder(folder: String) {
        scope.launch {
            withContext(io) { folders.keepAccess(folder) }
            var old: String? = null
            store.updateLocalBackup {
                old = it.folder
                it.copy(folder = folder, failure = null)
            }
            old?.takeIf { it != folder }?.let { withContext(io) { runCatching { folders.releaseAccess(it) } } }
        }
    }

    fun setInterval(hours: Int) {
        scope.launch { store.updateLocalBackup { it.copy(intervalHours = hours.coerceAtLeast(0)) } }
    }

    fun backUpNow() {
        if (!claim(Work.BACKING_UP)) return
        scope.launch {
            try {
                backUp()
            } finally {
                _state.update { it.copy(working = null) }
            }
        }
    }

    /**
     * The automatic backup, which writes one if a folder is picked and backups are switched on.
     *
     * @return false when it should be tried again later.
     */
    suspend fun backUpOnSchedule(): Boolean {
        val config = store.localBackup.first()
        if (config.folder == null || config.intervalHours <= 0) return true
        return backUp()
    }

    /** Reads the backup file the user picked and asks whether it should replace the library. */
    fun pickBackup(file: String) {
        if (!claim(Work.READING)) return
        scope.launch {
            finishing {
                val bytes = try {
                    withContext(io) { folders.read(file) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw BackupException(BackupException.Reason.UNREADABLE, e)
                }
                val backup = BackupCodec.decode(bytes, library.schemaVersion)
                val plan = library.preview(backup)
                picked = backup
                _state.update { it.copy(prompt = plan) }
            }
        }
    }

    /** Puts back the backup the user was asked about. */
    fun restore() {
        val backup = picked ?: return
        if (!claim(Work.RESTORING)) return
        scope.launch {
            finishing {
                val plan = library.restore(backup)
                picked = null
                onRestored()
                _state.update { it.copy(notice = Notice.Restored(plan)) }
            }
        }
    }

    /** The user has read the notice, or turned the restore down. */
    fun dismiss() {
        picked = null
        _state.update { it.copy(prompt = null, notice = null) }
    }

    /** Writes a backup and keeps how that went. False when it did not get written. */
    private suspend fun backUp(): Boolean = writing.withLock {
        val folder = store.localBackup.first().folder ?: return false
        try {
            val backup = library.snapshot()
            val content = BackupCodec.encode(backup)
            withContext(io) {
                folders.write(folder, fileName(backup.createdAt), content)
                prune(folder)
            }
            val info = BackupInfo(backup.createdAt, backup.device, backup.books.size)
            store.updateLocalBackup { it.copy(latest = info, failure = null) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = if (runCatching { folders.name(folder) }.getOrNull() == null) Failure.NO_ACCESS else Failure.OTHER
            store.updateLocalBackup { it.copy(failure = failure) }
            false
        }
    }

    /** Deletes the app's backups beyond the newest [BACKUPS_KEPT]. A file that will not go is left. */
    private fun prune(folder: String) {
        folders.list(folder)
            .filter { isBackupName(it.name) }
            // The names sort by when they were made.
            .sortedByDescending { it.name }
            .drop(BACKUPS_KEPT)
            .forEach { runCatching { folders.delete(it.uri) } }
    }

    internal fun fileName(createdAt: Long): String =
        FILE_PREFIX + NAME_TIME.format(Instant.ofEpochMilli(createdAt).atZone(zone())) + FILE_SUFFIX

    /** One thing at a time: false when something is already under way. */
    private fun claim(work: Work): Boolean {
        while (true) {
            val current = _state.value
            if (current.working != null) return false
            if (_state.compareAndSet(current, current.copy(working = work, prompt = null, notice = null))) return true
        }
    }

    /** Runs [block] as the end of the work under way, turning a failure into a notice. */
    private suspend fun finishing(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = when ((e as? BackupException)?.reason) {
                BackupException.Reason.TOO_NEW -> Failure.TOO_NEW
                BackupException.Reason.UNREADABLE -> Failure.UNREADABLE
                null -> Failure.OTHER
            }
            _state.update { it.copy(notice = Notice.Failed(failure)) }
        } finally {
            _state.update { it.copy(working = null) }
        }
    }

    companion object {
        const val BACKUPS_KEPT = 5
        private const val FILE_PREFIX = "kozeki-backup-"
        private const val FILE_SUFFIX = ".json.gz"
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        internal fun isBackupName(name: String) = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
    }
}
