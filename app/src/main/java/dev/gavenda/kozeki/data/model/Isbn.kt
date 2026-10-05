package dev.gavenda.kozeki.data.model

/** ISBN parsing and validation. All results are bare digits (plus a trailing X for ISBN-10). */
object Isbn {

    // Ten to thirteen digits, optionally grouped with spaces or hyphens; the checksum decides the rest.
    private val Candidate = Regex("""(?<![0-9])[0-9][0-9 \-]{8,15}[0-9Xx](?![0-9])""")

    /** Finds the first valid ISBN in free text such as `urn:isbn:978-0-306-40615-7`, as an ISBN-13. */
    fun find(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return Candidate.findAll(text)
            .map { it.value.filter { c -> c.isDigit() || c == 'X' || c == 'x' }.uppercase() }
            .firstNotNullOfOrNull(::toIsbn13)
    }

    /** Normalises a valid ISBN-10 or ISBN-13 to ISBN-13, or returns null. */
    fun toIsbn13(isbn: String): String? {
        val clean = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        return when {
            isValid13(clean) -> clean
            isValid10(clean) -> {
                val body = "978" + clean.dropLast(1)
                body + checkDigit13(body)
            }
            else -> null
        }
    }

    /** The ISBN-10 form of a 978-prefixed ISBN-13, or null when there is none. */
    fun toIsbn10(isbn13: String): String? {
        if (!isValid13(isbn13) || !isbn13.startsWith("978")) return null
        val body = isbn13.substring(3, 12)
        val sum = body.mapIndexed { index, c -> (10 - index) * c.digitToInt() }.sum()
        val check = (11 - sum % 11) % 11
        return body + if (check == 10) "X" else check.toString()
    }

    fun isValid13(isbn: String): Boolean =
        isbn.length == 13 && isbn.all(Char::isDigit) &&
            (isbn.startsWith("978") || isbn.startsWith("979")) &&
            checkDigit13(isbn.dropLast(1)) == isbn.last()

    fun isValid10(isbn: String): Boolean {
        if (isbn.length != 10 || !isbn.dropLast(1).all(Char::isDigit)) return false
        val last = isbn.last()
        if (!last.isDigit() && last != 'X') return false
        val sum = isbn.mapIndexed { index, c ->
            (10 - index) * if (c == 'X') 10 else c.digitToInt()
        }.sum()
        return sum % 11 == 0
    }

    private fun checkDigit13(firstTwelve: String): Char {
        val sum = firstTwelve.mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum()
        return ((10 - sum % 10) % 10).digitToChar()
    }
}
