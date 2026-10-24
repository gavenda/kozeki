package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.backup.BackupCodec
import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.backup.LibraryArchive
import dev.gavenda.kozeki.data.backup.LibraryBackup
import dev.gavenda.kozeki.data.backup.RestorePlan
import dev.gavenda.kozeki.data.backup.local.BackupFolder
import dev.gavenda.kozeki.data.backup.local.FolderFile
import dev.gavenda.kozeki.data.backup.local.LocalBackup
import dev.gavenda.kozeki.data.backup.local.LocalBackupConfig
import dev.gavenda.kozeki.data.backup.local.LocalBackupState
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Failure
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Notice
import dev.gavenda.kozeki.data.backup.local.LocalBackupStore
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import java.io.FileNotFoundException
import java.io.IOException
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupTest {

    /** A folder the app was given, holding files by name. */
    private class FakeFolder : BackupFolder {
        val files = linkedMapOf<String, ByteArray>()
        val kept = mutableSetOf<String>()
        var reachable = true
        var full = false

        override fun keepAccess(folder: String) {
            kept += folder
        }

        override fun releaseAccess(folder: String) {
            kept -= folder
        }

        override fun name(folder: String) = if (reachable) "Backups" else null

        override fun write(folder: String, fileName: String, content: ByteArray) {
            if (!reachable) throw SecurityException("No access")
            if (full) throw IOException("No space left")
            files[fileName] = content
        }

        override fun list(folder: String) = files.keys.map { FolderFile(uri = it, name = it) }

        override fun delete(file: String) {
            files.remove(file)
        }

        override fun read(file: String) = files[file] ?: throw FileNotFoundException(file)
    }

    private class FakeLibrary(var books: List<BookEntity>) : LibraryArchive {
        override val schemaVersion = 6
        var now = 1_791_234_840_000L

        override suspend fun snapshot() =
            LibraryBackup(schemaVersion = 6, createdAt = now, appVersion = "2.5", device = "Pixel 9", books = books)

        override suspend fun preview(backup: LibraryBackup) = RestorePlan(
            info = BackupInfo(backup.createdAt, backup.device, backup.books.size),
            booksRemoved = books.count { here -> backup.books.none { it.id == here.id } },
            booksWithoutFile = 0,
        )

        override suspend fun restore(backup: LibraryBackup): RestorePlan = preview(backup).also { books = backup.books }
    }

    private class FakeStore(config: LocalBackupConfig) : LocalBackupStore {
        override val localBackup = MutableStateFlow(config)

        override suspend fun updateLocalBackup(transform: (LocalBackupConfig) -> LocalBackupConfig) {
            localBackup.value = transform(localBackup.value)
        }
    }

    private fun book(id: String) = BookEntity(id = id, title = "Book $id", sync = SyncStamp.created(0))

    private inner class Device(scope: TestScope, books: List<String>, config: LocalBackupConfig = LocalBackupConfig()) {
        val folder = FakeFolder()
        val library = FakeLibrary(books.map(::book))
        val store = FakeStore(config)
        var restoredCalls = 0
        val backup = LocalBackup(
            folders = folder,
            library = library,
            store = store,
            scope = scope.backgroundScope,
            zone = { ZoneOffset.UTC },
            io = StandardTestDispatcher(scope.testScheduler),
            onRestored = { restoredCalls++ },
        )

        suspend fun settled(): LocalBackupState = backup.state.first { it.working == null }
    }

    private val daily = LocalBackupConfig(folder = "content://tree/backups", intervalHours = 24)

    @Test
    fun `picking a folder keeps the access to it and lets go of the old one`() = runTest {
        val phone = Device(this, books = listOf("a"), config = LocalBackupConfig(folder = "content://old", failure = Failure.NO_ACCESS))
        phone.folder.kept += "content://old"

        phone.backup.pickFolder("content://new")
        // The old folder is let go of once the new one is kept.
        phone.store.localBackup.first { it.folder == "content://new" }
        testScheduler.runCurrent()

        assertEquals(setOf("content://new"), phone.folder.kept)
        assertEquals("content://new", phone.store.localBackup.value.folder)
        assertNull(phone.store.localBackup.value.failure)
    }

    @Test
    fun `the automatic backup writes a file named after when it was made`() = runTest {
        val phone = Device(this, books = listOf("a", "b"), config = daily)

        assertTrue(phone.backup.backUpOnSchedule())

        val (name, content) = phone.folder.files.entries.single()
        assertEquals("kozeki-backup-2026-10-05_21-14-00.json.gz", name)
        assertEquals(listOf("a", "b"), BackupCodec.decode(content, 6).books.map { it.id })
        assertEquals(BackupInfo(1_791_234_840_000, "Pixel 9", 2), phone.store.localBackup.value.latest)
    }

    @Test
    fun `nothing is written while automatic backups are off or there is no folder`() = runTest {
        val off = Device(this, books = listOf("a"), config = daily.copy(intervalHours = 0))
        val nowhere = Device(this, books = listOf("a"), config = daily.copy(folder = null))

        assertTrue(off.backup.backUpOnSchedule())
        assertTrue(nowhere.backup.backUpOnSchedule())

        assertTrue(off.folder.files.isEmpty())
        assertNull(off.store.localBackup.value.latest)
    }

    @Test
    fun `only the newest backups are kept, and other files are left alone`() = runTest {
        val phone = Device(this, books = listOf("a"), config = daily)
        phone.folder.files["notes.txt"] = byteArrayOf(1)

        repeat(LocalBackup.BACKUPS_KEPT + 2) {
            phone.backup.backUpOnSchedule()
            phone.library.now += 24 * 60 * 60 * 1000L
        }

        val backups = phone.folder.files.keys.filter { it.startsWith("kozeki-backup-") }.sorted()
        assertEquals(LocalBackup.BACKUPS_KEPT, backups.size)
        assertEquals("kozeki-backup-2026-10-07_21-14-00.json.gz", backups.first())
        assertTrue("notes.txt" in phone.folder.files)
    }

    @Test
    fun `a folder that cannot be reached is kept as the reason, until a backup gets written`() = runTest {
        val phone = Device(this, books = listOf("a"), config = daily)
        phone.folder.reachable = false

        assertFalse(phone.backup.backUpOnSchedule())
        assertEquals(Failure.NO_ACCESS, phone.store.localBackup.value.failure)

        phone.folder.reachable = true
        phone.folder.full = true
        phone.backup.backUpNow()
        phone.settled()
        assertEquals(Failure.OTHER, phone.store.localBackup.value.failure)

        phone.folder.full = false
        phone.backup.backUpNow()
        val state = phone.settled()
        assertNull(phone.store.localBackup.value.failure)
        assertEquals(1, phone.folder.files.size)
        assertEquals("Backups", state.folderName)
    }

    @Test
    fun `restoring a picked file asks first, then replaces the library`() = runTest {
        val phone = Device(this, books = listOf("a", "b"), config = daily)
        phone.backup.backUpOnSchedule()
        phone.library.books = listOf(book("c"))

        phone.backup.pickBackup(phone.folder.files.keys.single())
        val asked = phone.settled()
        assertEquals(RestorePlan(BackupInfo(1_791_234_840_000, "Pixel 9", 2), booksRemoved = 1, booksWithoutFile = 0), asked.prompt)
        assertEquals(listOf("c"), phone.library.books.map { it.id })

        phone.backup.restore()
        val state = phone.settled()
        assertEquals(Notice.Restored(asked.prompt!!), state.notice)
        assertEquals(listOf("a", "b"), phone.library.books.map { it.id })
        assertEquals(1, phone.restoredCalls)
    }

    @Test
    fun `a file that is not a backup is not offered`() = runTest {
        val phone = Device(this, books = listOf("a"), config = daily)
        phone.folder.files["photo.jpg"] = byteArrayOf(1, 2, 3)

        phone.backup.pickBackup("photo.jpg")
        val state = phone.settled()

        assertNull(state.prompt)
        assertEquals(Notice.Failed(Failure.UNREADABLE), state.notice)
        phone.backup.restore()
        assertEquals(listOf("a"), phone.library.books.map { it.id })
    }
}
