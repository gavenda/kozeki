package dev.gavenda.kozeki.data.epub

import android.graphics.Bitmap
import android.text.Html
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.singleLineOrNull
import dev.gavenda.kozeki.data.model.singleLines
import org.json.JSONArray
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.positions

/** What an EPUB says about itself. Used as-is until a metadata source provides something better. */
data class EpubInfo(
    val title: String?,
    val subtitle: String?,
    val authors: List<String>,
    val description: String?,
    val publisher: String?,
    val publishedDate: String?,
    val language: String?,
    val isbn13: String?,
    val subjects: List<String>,
    val positionCount: Int?,
    val cover: Bitmap?,
)

private const val DC_IDENTIFIER = "http://purl.org/dc/terms/identifier"

suspend fun Publication.extractInfo(): EpubInfo {
    val metadata = metadata
    return EpubInfo(
        title = metadata.title?.singleLineOrNull(),
        subtitle = metadata.localizedSubtitle?.string?.singleLineOrNull(),
        authors = metadata.authors.map { it.name }.singleLines(),
        description = metadata.description?.let(::plainText),
        publisher = metadata.publishers.firstOrNull()?.name?.singleLineOrNull(),
        // ISO-8601 instant; the calendar date is all a book needs.
        publishedDate = metadata.published?.toString()?.take(10),
        language = metadata.languages.firstOrNull(),
        isbn13 = findIsbn(identifiers()),
        subjects = metadata.subjects.map { it.name }.singleLines(),
        positionCount = runCatching { positions().size }.getOrNull()?.takeIf { it > 0 },
        cover = runCatching { cover() }.getOrNull(),
    )
}

/**
 * Readium exposes only the EPUB's unique identifier as `metadata.identifier`; any further
 * `dc:identifier` elements, which is where the ISBN often is, end up in `otherMetadata`.
 */
private fun Publication.identifiers(): List<String> = buildList {
    metadata.identifier?.let(::add)
    when (val other = metadata.otherMetadata[DC_IDENTIFIER]) {
        is String -> add(other)
        is Collection<*> -> other.forEach { add(it.toString()) }
        is JSONArray -> for (index in 0 until other.length()) add(other.opt(index).toString())
        null -> Unit
        else -> add(other.toString())
    }
}

/** Prefers identifiers labelled as ISBNs, and never mistakes a digit run inside a UUID for one. */
internal fun findIsbn(identifiers: List<String>): String? =
    identifiers.filter { it.contains("isbn", ignoreCase = true) }.firstNotNullOfOrNull(Isbn::find)
        ?: identifiers.filterNot { it.contains("uuid", ignoreCase = true) }.firstNotNullOfOrNull(Isbn::find)

/** EPUB descriptions are frequently HTML fragments. */
private fun plainText(html: String): String? =
    Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim().takeIf { it.isNotEmpty() }
