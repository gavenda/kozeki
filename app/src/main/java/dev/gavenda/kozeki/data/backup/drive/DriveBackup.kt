package dev.gavenda.kozeki.data.backup.drive

import android.app.PendingIntent
import android.content.Intent
import dev.gavenda.kozeki.data.backup.BackupCodec
import dev.gavenda.kozeki.data.backup.BackupException
import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.backup.DriveLink
import dev.gavenda.kozeki.data.backup.LibraryArchive
import dev.gavenda.kozeki.data.backup.LibraryBackup
import dev.gavenda.kozeki.data.backup.RestorePlan
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Failure
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Notice
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Prompt
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Work
import dev.gavenda.kozeki.data.metadata.MetadataException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the [DriveLink] is kept between launches. */
interface DriveLinkStore {
    val driveLink: Flow<DriveLink?>

    suspend fun setDriveLink(link: DriveLink?)
}

/** The backup on Google Drive, as Settings shows it. */
data class DriveBackupState(
    /** Null while nobody is signed in. */
    val link: DriveLink? = null,
    val working: Work? = null,
    /** A screen of Google's the user has to answer before the work can go on, until it has been opened. */
    val consent: PendingIntent? = null,
    /** Something only the user can decide before the work goes on. */
    val prompt: Prompt? = null,
    /** How the last thing asked for ended, until the user has seen it. */
    val notice: Notice? = null,
) {
    enum class Work {
        SIGNING_IN,
        BACKING_UP,

        /** Getting the backup, to show what putting it back would do. */
        FETCHING,
        RESTORING,
    }

    sealed interface Prompt {
        /** Backing up would replace a backup this device neither made nor put back. */
        data class Replace(val existing: BackupInfo) : Prompt

        data class Restore(val plan: RestorePlan) : Prompt
    }

    sealed interface Notice {
        data class Restored(val plan: RestorePlan) : Notice

        /** @param detail What the other side gave as the reason, for whoever set the app up. */
        data class Failed(val failure: Failure, val detail: String? = null) : Notice
    }

    enum class Failure {
        OFFLINE,

        /** Google gave no access, most often because the app is not registered for this build's signature. */
        SIGN_IN_REJECTED,

        /** Access was given and Drive still refused, as it does while its API is not switched on. */
        DRIVE_REFUSED,
        NO_BACKUP,
        UNREADABLE,
        TOO_NEW,
        OTHER,
    }
}

/**
 * Backs the library up to the user's Google Drive and puts a backup back. Held for as long as the
 * process, so the work goes on when Settings is left, and its outcome is there on coming back.
 *
 * There is one backup per account. A device replaces it without asking only while it is the one
 * that device made or put back; otherwise the user is asked first, so a new install does not
 * overwrite the library it was about to get back.
 */
class DriveBackup(
    private val auth: DriveAuthorizer,
    private val drive: DriveApi,
    private val library: LibraryArchive,
    private val links: DriveLinkStore,
    private val scope: CoroutineScope,
    /** Called once a backup is in place, for what its books still need from the network. */
    private val onRestored: () -> Unit,
) {
    private val _state = MutableStateFlow(DriveBackupState())
    val state: StateFlow<DriveBackupState> = _state.asStateFlow()

    /** What the consent screen interrupted, to go on with when it is answered. */
    @Volatile private var awaitingConsent: Task? = null

    /** The backup [Prompt.Restore] asks about. */
    @Volatile private var fetched: LibraryBackup? = null

    init {
        scope.launch { links.driveLink.collect { link -> _state.update { it.copy(link = link) } } }
    }

    fun signIn() = begin(Task.SignIn)

    /** @param replace The user has agreed to replace a backup that is not this device's. */
    fun backUp(replace: Boolean = false) = begin(Task.BackUp(replace))

    /** Gets the backup and asks, with [Prompt.Restore], whether to put it back. */
    fun fetchBackup() = begin(Task.Fetch)

    /** Puts back the backup the user was asked about. */
    fun restore() {
        val backup = fetched ?: return
        if (!claim(Work.RESTORING)) return
        scope.launch {
            finishing {
                val plan = library.restore(backup)
                fetched = null
                links.driveLink.first()?.let { save(it.copy(latest = plan.info, seen = backup.createdAt)) }
                onRestored()
                _state.update { it.copy(notice = Notice.Restored(plan)) }
            }
        }
    }

    /** The consent screen is being opened, and is not to be opened a second time. */
    fun consentShown() = _state.update { it.copy(consent = null) }

    /**
     * The consent screen was answered.
     *
     * @param result What it came back with, or null when the user left it without allowing anything.
     */
    fun consentAnswered(result: Intent?) {
        val task = awaitingConsent ?: return
        awaitingConsent = null
        scope.launch {
            finishing {
                val token = result?.let(auth::granted) ?: return@finishing
                perform(task, token)
            }
        }
    }

    /**
     * Forgets the account on this device. The access itself is left as it is, to be withdrawn in
     * the Google account: Drive deletes an app's folder when the app is disconnected from it, and
     * signing out here should not cost the user their backup.
     */
    fun signOut() {
        if (_state.value.working != null) return
        dismiss()
        scope.launch { save(null) }
    }

    /** The user has read the notice, or turned the prompt down. */
    fun dismiss() {
        fetched = null
        _state.update { it.copy(prompt = null, notice = null) }
    }

    private fun begin(task: Task) {
        if (!claim(task.work)) return
        scope.launch {
            finishing {
                val email = links.driveLink.first()?.email
                when (val grant = auth.authorize(email, chooseAccount = task is Task.SignIn)) {
                    is DriveGrant.Token -> perform(task, grant.value)
                    is DriveGrant.NeedsConsent -> {
                        awaitingConsent = task
                        _state.update { it.copy(consent = grant.intent) }
                    }
                }
            }
        }
    }

    /** Keeps [link], and shows it at once rather than when the store reports it back. */
    private suspend fun save(link: DriveLink?) {
        links.setDriveLink(link)
        _state.update { it.copy(link = link) }
    }

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
            // Leaving Google's screen with Back is no failure to report.
            if (!(e is DriveAuthException && e.cancelled)) _state.update { it.copy(notice = failureOf(e)) }
        } finally {
            // While the consent screen is up the work is not over, only waiting.
            if (awaitingConsent == null) _state.update { it.copy(working = null) }
        }
    }

    private suspend fun perform(task: Task, token: String) {
        val link = links.driveLink.first()
        val session = Session(token, link?.email)
        when (task) {
            Task.SignIn -> {
                val account = session.call { drive.account(it) }
                val latest = session.call { drive.find(it) }?.info
                save(DriveLink(email = account.email, photoUrl = account.photoUrl, latest = latest))
            }

            is Task.BackUp -> {
                link ?: return
                val existing = session.call { drive.find(it) }
                if (existing != null && existing.info.createdAt != link.seen && !task.replace) {
                    save(link.copy(latest = existing.info))
                    _state.update { it.copy(prompt = Prompt.Replace(existing.info)) }
                    return
                }
                val backup = library.snapshot()
                val info = BackupInfo(backup.createdAt, backup.device, backup.books.size)
                val content = BackupCodec.encode(backup)
                session.call { drive.upload(it, content, info, replacing = existing?.id) }
                save(link.copy(latest = info, seen = info.createdAt))
            }

            Task.Fetch -> {
                link ?: return
                val existing = session.call { drive.find(it) }
                if (existing == null) {
                    save(link.copy(latest = null))
                    _state.update { it.copy(notice = Notice.Failed(Failure.NO_BACKUP)) }
                    return
                }
                val backup = BackupCodec.decode(session.call { drive.download(it, existing.id) }, library.schemaVersion)
                val plan = library.preview(backup)
                fetched = backup
                _state.update { it.copy(prompt = Prompt.Restore(plan)) }
            }
        }
    }

    private fun failureOf(e: Exception): Notice.Failed = when (e) {
        is MetadataException.Network -> Notice.Failed(Failure.OFFLINE)
        is DriveAuthException ->
            if (e.offline) Notice.Failed(Failure.OFFLINE) else Notice.Failed(Failure.SIGN_IN_REJECTED, e.code.toString())
        is DriveException ->
            Notice.Failed(Failure.DRIVE_REFUSED, listOfNotNull(e.code.toString(), e.reason).joinToString(" "))
        is BackupException -> Notice.Failed(
            when (e.reason) {
                BackupException.Reason.TOO_NEW -> Failure.TOO_NEW
                BackupException.Reason.UNREADABLE -> Failure.UNREADABLE
            },
        )
        else -> Notice.Failed(Failure.OTHER)
    }

    /** What the user asked for, kept so it can go on after a consent screen. */
    private sealed interface Task {
        val work: Work

        data object SignIn : Task {
            override val work = Work.SIGNING_IN
        }

        data class BackUp(val replace: Boolean) : Task {
            override val work = Work.BACKING_UP
        }

        data object Fetch : Task {
            override val work = Work.FETCHING
        }
    }

    /** Calls to Drive with one token, which is asked for again once if Drive says it has lapsed. */
    private inner class Session(private var token: String, private val email: String?) {
        suspend fun <T> call(request: suspend (token: String) -> T): T = try {
            request(token)
        } catch (e: DriveException) {
            if (e.code != 401) throw e
            auth.forget(token)
            token = (auth.authorize(email) as? DriveGrant.Token)?.value ?: throw e
            request(token)
        }
    }
}
