package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.metadata.googlebooks.GoogleBooksProvider
import dev.gavenda.kozeki.data.model.MetadataSource
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleBooksProviderTest {

    /** The requests the provider sent, in order. */
    private val requests = mutableListOf<Request>()

    /** A provider whose requests are all answered with [code] and [body], and never leave the test. */
    private fun provider(body: String, code: Int = 200, apiKey: String = "key"): GoogleBooksProvider {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return GoogleBooksProvider(client, apiKey, androidPackage = "dev.gavenda.kozeki", androidCertSha1 = "AB12")
    }

    private fun volume(id: String, title: String) = """{ "id": "$id", "volumeInfo": { "title": "$title" } }"""

    @Test
    fun `a volume becomes a book of the app's own`() = runTest {
        val body = """
            { "items": [ { "id": "qyeDCgAAQBAJ", "volumeInfo": {
                "title": "The  Dispossessed", "subtitle": "An Ambiguous Utopia",
                "authors": ["Ursula K. Le Guin"], "publisher": "Hachette UK", "publishedDate": "2015-09-30",
                "description": "There was a wall.",
                "industryIdentifiers": [ { "type": "ISBN_10", "identifier": "1473206065" },
                                         { "type": "ISBN_13", "identifier": "9781473206069" } ],
                "pageCount": 373, "categories": ["Fiction"], "averageRating": 4.5, "language": "en",
                "imageLinks": { "thumbnail": "http://books.google.com/books/content?id=qyeDCgAAQBAJ&img=1&zoom=1&edge=curl&source=gbs_api" },
                "infoLink": "http://books.google.com/books?id=qyeDCgAAQBAJ",
                "canonicalVolumeLink": "https://books.google.com/books/about/The_Dispossessed.html?id=qyeDCgAAQBAJ"
            } } ] }
        """.trimIndent()

        val book = provider(body).search("the dispossessed").books.single()

        assertEquals(MetadataSource.GOOGLE_BOOKS, book.source)
        assertEquals("qyeDCgAAQBAJ", book.sourceId)
        assertEquals("The Dispossessed", book.title)
        assertEquals("An Ambiguous Utopia", book.subtitle)
        assertEquals(listOf("Ursula K. Le Guin"), book.authors)
        assertEquals("9781473206069", book.isbn13)
        assertEquals("1473206065", book.isbn10)
        assertEquals(373, book.pageCount)
        assertEquals(4.5f, book.averageRating)
        // Served over https, and without the page curl drawn over the corner.
        assertEquals("https://books.google.com/books/content?id=qyeDCgAAQBAJ&img=1&zoom=1&source=gbs_api", book.coverUrl)
        assertEquals("https://books.google.com/books/about/The_Dispossessed.html?id=qyeDCgAAQBAJ", book.infoUrl)
        // Google knows authors by name only.
        assertTrue(book.authorRefs.isEmpty())
    }

    @Test
    fun `a request says which app is asking, since the key is restricted to it`() = runTest {
        provider("{}").search("dune")

        val request = requests.single()
        assertEquals("key", request.url.queryParameter("key"))
        assertEquals("dev.gavenda.kozeki", request.header("X-Android-Package"))
        assertEquals("AB12", request.header("X-Android-Cert"))
    }

    @Test
    fun `a later page starts where the one before it ended`() = runTest {
        val full = (1..20).joinToString(",") { volume("id$it", "Book $it") }
        val provider = provider("""{ "items": [ $full ] }""")

        val first = provider.search("dune", page = 1)
        provider.search("dune", page = 3)

        assertEquals(20, first.books.size)
        assertTrue(first.hasMore)
        assertEquals(listOf("0", "40"), requests.map { it.url.queryParameter("startIndex") })
    }

    @Test
    fun `a page that is not full is the last one`() = runTest {
        val page = provider("""{ "items": [ ${volume("a", "Dune")} ] }""").search("dune")

        assertEquals(1, page.books.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun `no results is an empty page and not a failure`() = runTest {
        val page = provider("""{ "totalItems": 0 }""").search("zzzz")

        assertTrue(page.books.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `an ISBN is looked up as one`() = runTest {
        provider("{}").findByIsbn("9781473206069")

        assertEquals("isbn:9781473206069", requests.single().url.queryParameter("q"))
    }

    @Test
    fun `a title and author nobody matches word for word are searched again loosely`() = runTest {
        provider("{}").searchByTitleAndAuthor("Dune", "Frank Herbert")

        assertEquals(
            listOf("intitle:\"Dune\" inauthor:\"Frank Herbert\"", "Dune Frank Herbert"),
            requests.map { it.url.queryParameter("q") },
        )
    }

    @Test
    fun `a book asked for by ID has the markup taken out of its description`() = runTest {
        val body = """{ "id": "a", "volumeInfo": { "title": "Dune", "description": "<p><b>Arrakis.</b></p><p>Desert planet.</p>" } }"""

        val book = provider(body).book("a")

        assertEquals("Arrakis.\nDesert planet.", book?.description)
        assertEquals("/books/v1/volumes/a", requests.single().url.encodedPath)
    }

    @Test
    fun `an ID Google does not know is no book`() = runTest {
        assertNull(provider("""{ "error": { "code": 404, "message": "The volume ID could not be found." } }""", code = 404).book("nope"))
    }

    @Test
    fun `without an API key nothing is sent`() = runTest {
        val provider = provider("{}", apiKey = "")

        assertEquals(MetadataProvider.Unavailable.NOT_CONFIGURED, provider.unavailableReason())
        try {
            provider.search("dune")
            fail("Expected the search to be refused")
        } catch (e: MetadataException.Unavailable) {
            assertEquals(MetadataProvider.Unavailable.NOT_CONFIGURED, e.reason)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a key Google will not take is told apart from a spent quota`() = runTest {
        val invalid = """
            { "error": { "code": 400, "message": "API key not valid.", "status": "INVALID_ARGUMENT",
                "errors": [ { "reason": "badRequest" } ],
                "details": [ { "@type": "type.googleapis.com/google.rpc.ErrorInfo", "reason": "API_KEY_INVALID" } ] } }
        """.trimIndent()
        val blocked = """
            { "error": { "code": 403, "message": "Requests from this Android client application are blocked.",
                "status": "PERMISSION_DENIED", "errors": [ { "reason": "forbidden" } ] } }
        """.trimIndent()
        val spent = """
            { "error": { "code": 403, "message": "Quota exceeded.", "errors": [ { "reason": "dailyLimitExceeded" } ] } }
        """.trimIndent()

        assertTrue(failure(invalid, 400) is MetadataException.KeyRejected)
        assertTrue(failure(blocked, 403) is MetadataException.KeyRejected)
        assertTrue(failure(spent, 403) is MetadataException.RateLimited)
        assertTrue(failure("{}", 429) is MetadataException.RateLimited)
        assertTrue(failure("oops", 500) is MetadataException.Server)
    }

    @Test
    fun `it has neither reviews nor pages for authors`() = runTest {
        val provider = provider("{}")

        assertTrue(provider.reviews("a").reviews.isEmpty())
        assertNull(provider.author("a").author)
        assertFalse(MetadataSource.GOOGLE_BOOKS.hasReviews)
        assertFalse(MetadataSource.GOOGLE_BOOKS.hasAuthorPages)
        assertTrue(requests.isEmpty())
    }

    private suspend fun failure(body: String, code: Int): MetadataException =
        try {
            provider(body, code).search("dune")
            throw AssertionError("Expected the search to fail")
        } catch (e: MetadataException) {
            e
        }
}
