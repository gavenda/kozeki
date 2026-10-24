package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.update.AppUpdateState
import dev.gavenda.kozeki.data.update.AppUpdates
import dev.gavenda.kozeki.data.update.UpdateCheck
import dev.gavenda.kozeki.data.update.UpdateChecker
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdatesTest {

    private val requests = AtomicInteger()

    /** Every request is answered with [code] and a release tagged [tag]. */
    private fun client(tag: String = "2.7.0", code: Int = 200) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body("""{ "tag_name": "$tag", "html_url": "https://example.com/$tag" }""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private suspend fun AppUpdates.settled() = state.first { it != AppUpdateState.Checking }

    @Test
    fun `a fresh open checks once however often it is asked`() = runTest {
        val updates = AppUpdates(UpdateChecker(client(), "2.6.0"), backgroundScope)
        updates.checkOnLaunch()
        assertEquals(AppUpdateState.Checked(UpdateCheck.Available("2.7.0", "https://example.com/2.7.0")), updates.settled())
        updates.checkOnLaunch()
        updates.checkOnLaunch()
        assertEquals(1, requests.get())
    }

    @Test
    fun `asking checks again after the launch check`() = runTest {
        val updates = AppUpdates(UpdateChecker(client(), "2.6.0"), backgroundScope)
        updates.checkOnLaunch()
        updates.settled()
        updates.check()
        updates.settled()
        assertEquals(2, requests.get())
    }

    @Test
    fun `a failed launch check is not shown, a failed asked one is`() = runTest {
        val updates = AppUpdates(UpdateChecker(client(code = 500), "2.6.0"), backgroundScope)
        updates.checkOnLaunch()
        assertEquals(AppUpdateState.Idle, updates.settled())
        updates.check()
        assertEquals(AppUpdateState.Checked(UpdateCheck.Refused(500)), updates.settled())
    }
}
