package dev.gavenda.kozeki.data

import android.content.Intent
import dev.gavenda.kozeki.data.backup.BackupCodec
import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.backup.DriveLink
import dev.gavenda.kozeki.data.backup.LibraryArchive
import dev.gavenda.kozeki.data.backup.LibraryBackup
import dev.gavenda.kozeki.data.backup.RestorePlan
import dev.gavenda.kozeki.data.backup.drive.DriveApi
import dev.gavenda.kozeki.data.backup.drive.DriveAuthException
import dev.gavenda.kozeki.data.backup.drive.DriveAuthorizer
import dev.gavenda.kozeki.data.backup.drive.DriveBackup
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Failure
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Notice
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Prompt
import dev.gavenda.kozeki.data.backup.drive.DriveGrant
import dev.gavenda.kozeki.data.backup.drive.DriveLinkStore
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupTest {

    /** Google Drive, as far as the app uses it: one account with at most one file in the app's own folder. */
    private class FakeDrive : Interceptor {
        class Stored(val id: String, val content: ByteArray, val properties: Map<String, String>)

        var file: Stored? = null
        var offline = false

        /** Tokens Drive answers 401 to, as it does to one that has lapsed. */
        val lapsed = mutableSetOf<String>()

        /** Answered to everything, such as the 403 of a project whose Drive API is switched off. */
        var refusal: Pair<Int, String>? = null
        val requests = mutableListOf<Request>()

        /** What the uploads said about the file, the newest last. */
        val metadata = mutableListOf<JsonObject>()
        private var created = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            if (offline) throw IOException("No route to host")
            refusal?.let { (code, body) -> return answer(request, code, body) }
            if (request.header("Authorization")?.removePrefix("Bearer ") in lapsed) {
                return answer(request, 401, """{ "error": { "code": 401, "status": "UNAUTHENTICATED" } }""")
            }
            val path = request.url.encodedPath
            val stored = file
            return when {
                path == "/drive/v3/about" ->
                    answer(request, 200, """{ "user": { "emailAddress": "reader@example.com", "photoLink": "https://example.com/me.png" } }""")

                path == "/drive/v3/files" && request.method == "GET" ->
                    answer(request, 200, """{ "files": [${stored?.let(::describe).orEmpty()}] }""")

                path == "/upload/drive/v3/files" && request.method == "POST" -> store(request, "file-${++created}")

                path.startsWith("/upload/drive/v3/files/") && request.method == "PATCH" ->
                    if (stored != null && path.endsWith("/${stored.id}")) store(request, stored.id) else missing(request)

                path.startsWith("/drive/v3/files/") && request.url.queryParameter("alt") == "media" ->
                    if (stored != null && path.endsWith("/${stored.id}")) answer(request, 200, stored.content) else missing(request)

                else -> missing(request)
            }
        }

        private fun store(request: Request, id: String): Response {
            val body = request.body!!
            val sent = Buffer().also(body::writeTo)
            MultipartReader(sent, body.contentType()!!.parameter("boundary")!!).use { parts ->
                val about = Json.parseToJsonElement(parts.nextPart()!!.body.readUtf8()).jsonObject
                val content = parts.nextPart()!!.body.readByteArray()
                metadata += about
                val properties = about.getValue("appProperties").jsonObject.mapValues { it.value.jsonPrimitive.content }
                file = Stored(id, content, properties)
            }
            return answer(request, 200, describe(file!!))
        }

        private fun describe(stored: Stored): String {
            val properties = stored.properties.entries.joinToString { (name, value) -> "\"$name\": \"$value\"" }
            return """{ "id": "${stored.id}", "modifiedTime": "2026-10-11T21:14:00.000Z", "appProperties": { $properties } }"""
        }

        private fun missing(request: Request) =
            answer(request, 404, """{ "error": { "code": 404, "errors": [ { "reason": "notFound" } ] } }""")

        private fun answer(request: Request, code: Int, body: String) = answer(request, code, body.encodeToByteArray())

        private fun answer(request: Request, code: Int, body: ByteArray) = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private class FakeAuthorizer : DriveAuthorizer {
        var failure: DriveAuthException? = null
        val forgotten = mutableListOf<String>()

        /** The accounts tokens were asked for, null being whichever the user picks. */
        val askedAs = mutableListOf<String?>()
        private var issued = 0

        override suspend fun authorize(email: String?, chooseAccount: Boolean): DriveGrant {
            failure?.let { throw it }
            askedAs += email
            return DriveGrant.Token("token-${++issued}")
        }

        override fun granted(result: Intent?): String = error("No consent screen comes up in these tests")

        override suspend fun forget(token: String) {
            forgotten += token
        }
    }

    private class FakeLibrary(var books: List<BookEntity>) : LibraryArchive {
        override val schemaVersion = 6
        var restored: LibraryBackup? = null
        private var now = 1_000L

        override suspend fun snapshot() =
            LibraryBackup(schemaVersion = 6, createdAt = now++, appVersion = "1.7", device = "Pixel 9", books = books)

        override suspend fun preview(backup: LibraryBackup) = RestorePlan(
            info = BackupInfo(backup.createdAt, backup.device, backup.books.size),
            booksRemoved = books.count { here -> backup.books.none { it.id == here.id } },
            booksWithoutFile = 0,
        )

        override suspend fun restore(backup: LibraryBackup): RestorePlan = preview(backup).also {
            restored = backup
            books = backup.books
        }
    }

    private class FakeLinks(link: DriveLink?) : DriveLinkStore {
        override val driveLink = MutableStateFlow(link)

        override suspend fun setDriveLink(link: DriveLink?) {
            driveLink.value = link
        }
    }

    private fun book(id: String) = BookEntity(id = id, title = "Book $id", sync = SyncStamp.created(0))

    /** A device with its own library and its own idea of what is on Drive. */
    private inner class Device(scope: TestScope, books: List<String>, link: DriveLink? = null, drive: FakeDrive = FakeDrive()) {
        val drive = drive
        val auth = FakeAuthorizer()
        val library = FakeLibrary(books.map(::book))
        val links = FakeLinks(link)
        var restoredCalls = 0
        val backup = DriveBackup(
            auth = auth,
            drive = DriveApi(OkHttpClient.Builder().addInterceptor(drive).build()),
            library = library,
            links = links,
            scope = scope.backgroundScope,
            onRestored = { restoredCalls++ },
        )

        /** The state once the work asked for has ended. */
        suspend fun settled(): DriveBackupState = backup.state.first { it.working == null }

        val link: DriveLink? get() = links.driveLink.value
    }

    private val signedIn = DriveLink(email = "reader@example.com")

    @Test
    fun `signing in keeps the account and what is already on Drive`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.backup.backUp()
        phone.settled()

        val tablet = Device(this, books = emptyList(), drive = phone.drive)
        tablet.backup.signIn()
        val state = tablet.settled()

        assertNull(state.notice)
        assertEquals("reader@example.com", state.link?.email)
        assertEquals("https://example.com/me.png", state.link?.photoUrl)
        assertEquals(BackupInfo(createdAt = 1_000, device = "Pixel 9", books = 1), state.link?.latest)
        // Found, not made or put back here.
        assertNull(state.link?.seen)
        assertEquals(state.link, tablet.link)
        // Nobody was signed in, so the account was the user's to pick.
        assertEquals(listOf<String?>(null), tablet.auth.askedAs)
    }

    @Test
    fun `the first backup is made in the app's own folder and says what it is`() = runTest {
        val phone = Device(this, books = listOf("a", "b"), link = signedIn)

        phone.backup.backUp()
        val state = phone.settled()

        assertNull(state.notice)
        assertNull(state.prompt)
        val info = BackupInfo(createdAt = 1_000, device = "Pixel 9", books = 2)
        assertEquals(signedIn.copy(latest = info, seen = 1_000), state.link)

        val about = phone.drive.metadata.single()
        assertEquals("kozeki-backup.json.gz", about.getValue("name").jsonPrimitive.content)
        assertEquals(listOf("appDataFolder"), about.getValue("parents").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(mapOf("createdAt" to "1000", "device" to "Pixel 9", "books" to "2"), phone.drive.file!!.properties)
        assertEquals(listOf("a", "b"), BackupCodec.decode(phone.drive.file!!.content, 6).books.map { it.id })

        val upload = phone.drive.requests.last()
        assertEquals("multipart", upload.url.queryParameter("uploadType"))
        assertTrue(upload.body!!.contentType().toString().startsWith("multipart/related"))
        // Every request is made as the user, and only looks where the app's own files are.
        assertTrue(phone.drive.requests.all { it.header("Authorization") == "Bearer token-1" })
        val search = phone.drive.requests.first().url
        assertEquals("appDataFolder", search.queryParameter("spaces"))
        assertEquals("name = 'kozeki-backup.json.gz'", search.queryParameter("q"))
        assertEquals(listOf<String?>("reader@example.com"), phone.auth.askedAs)
    }

    @Test
    fun `a backup this device made is replaced in place without asking`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.backup.backUp()
        phone.settled()
        phone.library.books += book("b")

        phone.backup.backUp()
        val state = phone.settled()

        assertNull(state.prompt)
        assertEquals("file-1", phone.drive.file!!.id)
        assertEquals("PATCH", phone.drive.requests.last().method)
        // The file is where it is and called what it is called; only what it holds is new.
        assertEquals(setOf("appProperties"), phone.drive.metadata.last().keys)
        assertEquals(2, state.link?.latest?.books)
        assertEquals(listOf("a", "b"), BackupCodec.decode(phone.drive.file!!.content, 6).books.map { it.id })
    }

    @Test
    fun `a backup this device has not seen is not replaced until the user says so`() = runTest {
        val phone = Device(this, books = listOf("a", "b", "c"), link = signedIn)
        phone.backup.backUp()
        phone.settled()
        val kept = phone.drive.file!!.content

        // A new install, signed in, backing up before putting anything back.
        val fresh = Device(this, books = emptyList(), link = signedIn, drive = phone.drive)
        fresh.backup.backUp()
        val asked = fresh.settled()

        assertEquals(Prompt.Replace(BackupInfo(createdAt = 1_000, device = "Pixel 9", books = 3)), asked.prompt)
        assertTrue(phone.drive.file!!.content.contentEquals(kept))

        fresh.backup.dismiss()
        assertNull(fresh.backup.state.value.prompt)
        assertTrue(phone.drive.file!!.content.contentEquals(kept))

        fresh.backup.backUp(replace = true)
        val replaced = fresh.settled()

        assertNull(replaced.prompt)
        assertTrue(BackupCodec.decode(phone.drive.file!!.content, 6).books.isEmpty())
        assertEquals(replaced.link?.latest?.createdAt, replaced.link?.seen)
    }

    @Test
    fun `restoring asks first, then puts the backup in the library's place`() = runTest {
        val phone = Device(this, books = listOf("a", "b"), link = signedIn)
        phone.backup.backUp()
        phone.settled()

        val tablet = Device(this, books = listOf("x"), link = signedIn, drive = phone.drive)
        tablet.backup.fetchBackup()
        val asked = tablet.settled()

        val plan = RestorePlan(BackupInfo(createdAt = 1_000, device = "Pixel 9", books = 2), booksRemoved = 1, booksWithoutFile = 0)
        assertEquals(Prompt.Restore(plan), asked.prompt)
        // Nothing has happened to the library yet.
        assertNull(tablet.library.restored)
        assertEquals(listOf("x"), tablet.library.books.map { it.id })

        tablet.backup.restore()
        val state = tablet.settled()

        // The same as was asked about: the one book that was only here went.
        assertEquals(Notice.Restored(plan), state.notice)
        assertNull(state.prompt)
        assertEquals(listOf("a", "b"), tablet.library.books.map { it.id })
        assertEquals(1, tablet.restoredCalls)
        assertEquals(1_000L, state.link?.seen)

        // What was put back is what is on Drive, so backing up again needs no asking.
        tablet.backup.backUp()
        assertNull(tablet.settled().prompt)
    }

    @Test
    fun `turning the restore down leaves the library alone`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.backup.backUp()
        phone.settled()
        val tablet = Device(this, books = listOf("x"), link = signedIn, drive = phone.drive)
        tablet.backup.fetchBackup()
        tablet.settled()

        tablet.backup.dismiss()
        tablet.backup.restore()
        val state = tablet.settled()

        assertNull(state.prompt)
        assertNull(state.notice)
        assertNull(tablet.library.restored)
        assertEquals(0, tablet.restoredCalls)
    }

    @Test
    fun `restoring with nothing on Drive says so`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn.copy(latest = BackupInfo(1, "Pixel 9", 1)))

        phone.backup.fetchBackup()
        val state = phone.settled()

        assertEquals(Notice.Failed(Failure.NO_BACKUP), state.notice)
        assertNull(state.prompt)
        // The backup it knew of is gone, so it stops showing it.
        assertNull(state.link?.latest)
    }

    @Test
    fun `a backup that cannot be read is not offered`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.drive.file = FakeDrive.Stored("file-9", byteArrayOf(1, 2, 3), mapOf("createdAt" to "5"))

        phone.backup.fetchBackup()
        val state = phone.settled()

        assertEquals(Notice.Failed(Failure.UNREADABLE), state.notice)
        assertNull(state.prompt)
        phone.backup.restore()
        assertNull(phone.library.restored)
    }

    @Test
    fun `a backup from a newer build is not offered`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        val newer = LibraryBackup(schemaVersion = 7, createdAt = 5, appVersion = "2.0", device = "Pixel 10")
        phone.drive.file = FakeDrive.Stored("file-9", BackupCodec.encode(newer), mapOf("createdAt" to "5"))

        phone.backup.fetchBackup()

        assertEquals(Notice.Failed(Failure.TOO_NEW), phone.settled().notice)
    }

    @Test
    fun `a token that has lapsed is asked for again, once`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.drive.lapsed += "token-1"

        phone.backup.backUp()
        val state = phone.settled()

        assertNull(state.notice)
        assertNotNull(phone.drive.file)
        assertEquals(listOf("token-1"), phone.auth.forgotten)
        assertEquals("Bearer token-2", phone.drive.requests.last().header("Authorization"))

        // When the new one is refused too, that is the end of it.
        phone.drive.lapsed += listOf("token-3", "token-4")
        phone.backup.backUp()
        assertEquals(Notice.Failed(Failure.DRIVE_REFUSED, "401 UNAUTHENTICATED"), phone.settled().notice)
    }

    @Test
    fun `no connection is reported as that, and nothing is lost`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.drive.offline = true

        phone.backup.backUp()
        val state = phone.settled()

        assertEquals(Notice.Failed(Failure.OFFLINE), state.notice)
        assertEquals(signedIn, state.link)

        phone.backup.dismiss()
        phone.drive.offline = false
        phone.backup.backUp()
        assertNull(phone.settled().notice)
        assertNotNull(phone.drive.file)
    }

    @Test
    fun `Drive refusing says why, for whoever set the project up`() = runTest {
        val phone = Device(this, books = listOf("a"))
        phone.drive.refusal = 403 to """
            { "error": { "code": 403, "message": "Google Drive API has not been used in project 1 before or it is disabled.",
                "errors": [ { "domain": "usageLimits", "reason": "accessNotConfigured" } ], "status": "PERMISSION_DENIED" } }
        """.trimIndent()

        phone.backup.signIn()
        val state = phone.settled()

        assertEquals(Notice.Failed(Failure.DRIVE_REFUSED, "403 accessNotConfigured"), state.notice)
        // Not signed in to a Drive that cannot be used.
        assertNull(state.link)
    }

    @Test
    fun `Google refusing the sign-in says with what code`() = runTest {
        val phone = Device(this, books = listOf("a"))
        phone.auth.failure = DriveAuthException(10)

        phone.backup.signIn()
        val state = phone.settled()

        assertEquals(Notice.Failed(Failure.SIGN_IN_REJECTED, "10"), state.notice)
        assertNull(state.link)
        assertTrue(phone.drive.requests.isEmpty())
    }

    @Test
    fun `signing out forgets the account and leaves the backup where it is`() = runTest {
        val phone = Device(this, books = listOf("a"), link = signedIn)
        phone.backup.backUp()
        phone.settled()
        val requests = phone.drive.requests.size

        phone.backup.signOut()
        val state = phone.backup.state.first { it.link == null }

        assertNull(phone.link)
        assertNull(state.notice)
        assertNotNull(phone.drive.file)
        assertEquals(requests, phone.drive.requests.size)
    }
}
