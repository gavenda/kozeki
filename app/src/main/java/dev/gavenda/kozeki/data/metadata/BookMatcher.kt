package dev.gavenda.kozeki.data.metadata

import java.text.Normalizer

/** Decides which search result, if any, is the same book as a local one. */
object BookMatcher {

    data class Scored(val metadata: BookMetadata, val score: Double)

    /** At or above this, a result is taken without asking the user. */
    const val CONFIDENT = 0.88

    /** Below this, a result is not worth showing as a candidate. */
    const val PLAUSIBLE = 0.45

    private const val CLEAR_LEAD = 0.08

    fun rank(title: String, authors: List<String>, candidates: List<BookMetadata>): List<Scored> =
        candidates
            .map { Scored(it, score(title, authors, it)) }
            .filter { it.score >= PLAUSIBLE }
            .sortedByDescending { it.score }

    /**
     * The best result when it can be trusted: it scores high and nothing different scores nearly
     * as high. Several editions of the same work tie by design, so those do not count as rivals.
     */
    fun confidentMatch(ranked: List<Scored>): BookMetadata? {
        val top = ranked.firstOrNull()?.takeIf { it.score >= CONFIDENT } ?: return null
        val rival = ranked.drop(1).firstOrNull { !sameWork(it.metadata, top.metadata) }
        if (rival != null && top.score - rival.score < CLEAR_LEAD) return null
        return ranked
            .filter { it.score >= top.score - 0.01 && sameWork(it.metadata, top.metadata) }
            .maxByOrNull { completeness(it.metadata) }
            ?.metadata
    }

    fun score(title: String, authors: List<String>, candidate: BookMetadata): Double {
        val titleScore = maxOf(
            similarity(normalize(title), normalize(candidate.title)),
            similarity(normalize(mainTitle(title)), normalize(mainTitle(candidate.title))),
            similarity(normalize(title), normalize(listOfNotNull(candidate.title, candidate.subtitle).joinToString(" "))),
        )
        // Without authors on both sides a title alone is never enough to be confident.
        if (authors.isEmpty() || candidate.authors.isEmpty()) return titleScore * 0.85
        val authorScore = authors.maxOf { local ->
            candidate.authors.maxOf { remote -> authorSimilarity(local, remote) }
        }
        return titleScore * 0.7 + authorScore * 0.3
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** "Dune: Deluxe Edition" and "Dune (Deluxe Edition)" are both "Dune". */
    internal fun mainTitle(title: String): String =
        title.substringBefore(':').substringBefore(" - ").substringBefore('(').ifBlank { title }

    private fun sameWork(a: BookMetadata, b: BookMetadata): Boolean =
        normalize(mainTitle(a.title)) == normalize(mainTitle(b.title)) &&
            (a.authors.isEmpty() || b.authors.isEmpty() || authorSimilarity(a.authors.first(), b.authors.first()) > 0.6)

    private fun completeness(metadata: BookMetadata): Int =
        listOf(metadata.coverUrl, metadata.description, metadata.isbn13, metadata.pageCount, metadata.publisher)
            .count { it != null }

    /** Order-insensitive, so "Tolkien, J. R. R." equals "J.R.R. Tolkien". Initials match full names. */
    internal fun authorSimilarity(a: String, b: String): Double {
        val left = normalize(a).split(' ').filter { it.isNotEmpty() }
        val right = normalize(b).split(' ').filter { it.isNotEmpty() }
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val (shorter, longer) = if (left.size <= right.size) left to right else right to left
        val remaining = longer.toMutableList()
        var matched = 0
        for (token in shorter) {
            val hit = remaining.firstOrNull { it == token }
                ?: remaining.firstOrNull { (token.length == 1 || it.length == 1) && it.first() == token.first() }
            if (hit != null) {
                remaining.remove(hit)
                matched++
            }
        }
        // Surnames carry the identity; a missing middle name should cost little.
        return matched.toDouble() / shorter.size * (0.75 + 0.25 * shorter.size / longer.size)
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val tokensA = a.split(' ').toSet()
        val tokensB = b.split(' ').toSet()
        val jaccard = tokensA.intersect(tokensB).size.toDouble() / tokensA.union(tokensB).size
        val edit = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
        return maxOf(jaccard, edit)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
