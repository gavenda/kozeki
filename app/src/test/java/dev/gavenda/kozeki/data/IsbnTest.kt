package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.epub.findIsbn
import dev.gavenda.kozeki.data.model.Isbn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {

    @Test
    fun `accepts a valid ISBN-13 and rejects a wrong check digit`() {
        assertTrue(Isbn.isValid13("9780306406157"))
        assertFalse(Isbn.isValid13("9780306406158"))
    }

    @Test
    fun `accepts a valid ISBN-10 including an X check digit`() {
        assertTrue(Isbn.isValid10("0306406152"))
        assertTrue(Isbn.isValid10("080442957X"))
        assertFalse(Isbn.isValid10("0306406153"))
    }

    @Test
    fun `converts between ISBN-10 and ISBN-13`() {
        assertEquals("9780306406157", Isbn.toIsbn13("0306406152"))
        assertEquals("0306406152", Isbn.toIsbn10("9780306406157"))
        assertEquals("9780306406157", Isbn.toIsbn13("978-0-306-40615-7"))
    }

    @Test
    fun `a 979 prefix has no ISBN-10 form`() {
        assertTrue(Isbn.isValid13("9791032305690"))
        assertNull(Isbn.toIsbn10("9791032305690"))
    }

    @Test
    fun `finds an ISBN inside an identifier`() {
        assertEquals("9780306406157", Isbn.find("urn:isbn:978-0-306-40615-7"))
        assertEquals("9780306406157", Isbn.find("ISBN 0-306-40615-2"))
        assertNull(Isbn.find("not an identifier"))
        assertNull(Isbn.find(null))
    }

    @Test
    fun `prefers identifiers labelled as ISBN and never reads one out of a UUID`() {
        val identifiers = listOf(
            "urn:uuid:12345678-9780-3064-0615-700000000000",
            "urn:isbn:9780306406157",
        )
        assertEquals("9780306406157", findIsbn(identifiers))
        assertNull(findIsbn(listOf("urn:uuid:0306406152-0000-0000-0000-000000000000")))
    }
}
