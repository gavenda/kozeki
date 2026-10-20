package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.comic.ComicCover
import dev.gavenda.kozeki.data.comic.ComicImages
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches cover images so that they can be kept on the device and shown offline. */
class CoverDownloader(private val client: OkHttpClient, private val comic: ComicImages) {

    /** Returns the image bytes, or null when the cover cannot be fetched right now. */
    suspend fun download(url: String): ByteArray? = runCatching {
        // A comic cover is fetched through its source, which may want headers of its own.
        val response = ComicCover.parse(url)?.let { comic.open(it.sourceId, it.url) }
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
