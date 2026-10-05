package dev.gavenda.kozeki.data.model

// Zero-width space and BOM: invisible, and never meant to separate words.
private val Invisible = Regex("[\\u200B\\uFEFF]")

// Any run of whitespace, including no-break, ideographic and typographic spaces, tabs and line breaks.
private val Whitespace = Regex("[\\s\\p{Z}]+")

/**
 * A title or name as one tidy line: every run of whitespace becomes a single space. EPUBs and
 * catalogues pad names with full-width spaces, line breaks and indentation ("Kawahara　　Reki").
 */
fun String.singleLine(): String = replace(Invisible, "").replace(Whitespace, " ").trim()

/** [singleLine], or null when nothing is left. */
fun String.singleLineOrNull(): String? = singleLine().takeIf { it.isNotEmpty() }

/** Each entry as a [singleLine], without blanks or repeats. */
fun List<String>.singleLines(): List<String> = mapNotNull { it.singleLineOrNull() }.distinct()
