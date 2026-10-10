package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.files.IncomingEpubs
import dev.gavenda.kozeki.data.files.IncomingEpubs.Request
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingEpubsTest {

    private val incoming = IncomingEpubs()

    // No file is read here, so what was asked for is all that tells the requests apart.
    private val opened = Request(emptyList(), open = true)
    private val shared = Request(emptyList(), open = false)

    @Test
    fun `requests are handed out in the order they arrived`() {
        incoming.add(opened)
        incoming.add(shared)

        assertEquals(listOf(opened, shared), incoming.waiting.value)
        assertEquals(opened, incoming.take())
        assertEquals(shared, incoming.take())
    }

    @Test
    fun `a request is handed out once`() {
        incoming.add(shared)

        assertEquals(shared, incoming.take())
        assertNull(incoming.take())
        assertEquals(emptyList<Request>(), incoming.waiting.value)
    }

    @Test
    fun `the same thing handed over twice is taken in twice`() {
        incoming.add(opened)
        incoming.add(opened)

        assertEquals(opened, incoming.take())
        assertEquals(opened, incoming.take())
        assertNull(incoming.take())
    }

    @Test
    fun `a book to open waits for the one who opens it`() = runTest {
        incoming.open("first")
        incoming.open("second")

        assertEquals("first", incoming.toOpen.first())
        assertEquals("second", incoming.toOpen.first())
    }
}
