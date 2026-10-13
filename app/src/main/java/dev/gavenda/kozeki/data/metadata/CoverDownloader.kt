package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.manga.MangaCover
import dev.gavenda.kozeki.data.manga.MangaImages
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches cover images so that they can be kept on the device and shown offline. */
class CoverDownloader(private val client: OkHttpClient, private val manga: MangaImages) {

    /** Returns the image bytes, or null when the cover cannot be fetched right now. */
    suspend fun download(url: String): ByteArray? = runCatching {
        // A manga cover is fetched through its source, which may want headers of its own.
        val response = MangaCover.parse(url)?.let { manga.open(it.sourceId, it.url) }
            ?: client.newCall(Request.Builder().url(url).build()).await()
        response.use {
            if (!response.isSuccessful) return@use null
            val type = response.header("Content-Type").orEmpty()
            if (!type.startsWith("image/")) return@use null
            response.body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
        }
    }.getOrNull()

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
