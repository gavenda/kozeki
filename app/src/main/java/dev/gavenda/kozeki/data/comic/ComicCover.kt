package dev.gavenda.kozeki.data.comic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import okhttp3.Headers
import okhttp3.Response

/**
 * A picture of a comic source, named so it can be handled as the plain string cover URLs are.
 *
 * Extensions often need their own headers on image requests, a Referer for one. A bare image URL
 * would be fetched without them, so the URL of such a cover also says which source to fetch it
 * through: `kozeki-comic://<source id>/<url-encoded image URL>`.
 */
object ComicCover {
    private const val PREFIX = "kozeki-comic://"

    /** What [PREFIX] was before comics were called comics. Old backups still hold covers named so. */
    const val LEGACY_PREFIX = "kozeki-manga://"

    data class Parsed(val sourceId: Long, val url: String)

    fun url(sourceId: Long, imageUrl: String): String =
        "$PREFIX$sourceId/${URLEncoder.encode(imageUrl, Charsets.UTF_8)}"

    /** The source and image URL inside [url], or null when it is an ordinary URL. */
    fun parse(url: String): Parsed? {
        val rest = when {
            url.startsWith(PREFIX) -> url.removePrefix(PREFIX)
            url.startsWith(LEGACY_PREFIX) -> url.removePrefix(LEGACY_PREFIX)
            else -> return null
        }
        val sourceId = rest.substringBefore('/').toLongOrNull() ?: return null
        val image = rest.substringAfter('/', "").takeIf { it.isNotEmpty() } ?: return null
        return Parsed(sourceId, URLDecoder.decode(image, Charsets.UTF_8))
    }
}

/** Fetches images through the source that has them, with the headers its extension asks for. */
class ComicImages(private val sources: ComicSources, private val network: NetworkHelper) {

    /** The response for [url] from the source [sourceId]. Throws when it is not installed or refuses. */
    suspend fun open(sourceId: Long, url: String): Response {
        val source = sources.get(sourceId) ?: throw IOException("Comic source $sourceId is not installed")
        val http = source as? HttpSource
        val client = http?.client ?: network.client
        return client.newCall(GET(url, http?.headers ?: Headers.EMPTY)).awaitSuccess()
    }
}
