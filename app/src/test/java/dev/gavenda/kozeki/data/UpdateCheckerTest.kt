package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.update.UpdateCheck
import dev.gavenda.kozeki.data.update.UpdateChecker
import dev.gavenda.kozeki.data.update.isNewer
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    /** A checker whose request is answered with [code] and [body], and never leaves the test. */
    private fun checker(installed: String, body: String = "", code: Int = 200, offline: Boolean = false): UpdateChecker {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (offline) throw IOException("offline")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return UpdateChecker(client, installed)
    }

    private fun release(tag: String, apk: Boolean = true) = """
        { "tag_name": "$tag", "html_url": "https://github.com/gavenda/kozeki/releases/tag/$tag",
          "assets": [ ${if (apk) """{ "name": "kozeki-$tag.apk", "browser_download_url": "https://example.com/kozeki-$tag.apk" }""" else ""} ] }
    """.trimIndent()

    @Test
    fun `a newer release offers its apk`() = runTest {
        assertEquals(
            UpdateCheck.Available("2.7.0", "https://example.com/kozeki-2.7.0.apk"),
            checker("2.6.0", release("2.7.0")).check(),
        )
    }

    @Test
    fun `a release without an apk offers its page`() = runTest {
        assertEquals(
            UpdateCheck.Available("2.7.0", "https://github.com/gavenda/kozeki/releases/tag/2.7.0"),
            checker("2.6.0", release("2.7.0", apk = false)).check(),
        )
    }

    @Test
    fun `the same or an older release is up to date`() = runTest {
        assertEquals(UpdateCheck.UpToDate, checker("2.6.0", release("2.6.0")).check())
        assertEquals(UpdateCheck.UpToDate, checker("2.6.0", release("2.5.9")).check())
    }

    @Test
    fun `failures are told apart`() = runTest {
        assertEquals(UpdateCheck.Offline, checker("2.6.0", offline = true).check())
        assertEquals(UpdateCheck.RateLimited, checker("2.6.0", code = 403).check())
        assertEquals(UpdateCheck.Refused(404), checker("2.6.0", code = 404).check())
        assertEquals(UpdateCheck.Unreadable, checker("2.6.0", body = "<html>").check())
    }

    @Test
    fun `versions compare by number`() {
        assertTrue(isNewer("2.10.0", "2.9.0"))
        assertTrue(isNewer("v2.6.1", "2.6.0"))
        assertTrue(isNewer("3", "2.6.0"))
        assertFalse(isNewer("2.6", "2.6.0"))
        assertFalse(isNewer("2.6.0", "2.6.0-debug"))
        assertFalse(isNewer("2.5.10", "2.6.0"))
    }
}
