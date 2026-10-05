package dev.gavenda.kozeki.data.metadata

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Runs the call without blocking a thread, mapping transport failures to [MetadataException.Network]. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(MetadataException.Network(e))
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}
