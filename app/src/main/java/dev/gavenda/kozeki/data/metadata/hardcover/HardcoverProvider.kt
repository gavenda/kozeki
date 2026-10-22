package dev.gavenda.kozeki.data.metadata.hardcover

import dev.gavenda.kozeki.data.metadata.Author
import dev.gavenda.kozeki.data.metadata.AuthorPage
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookPage
import dev.gavenda.kozeki.data.metadata.BookReview
import dev.gavenda.kozeki.data.metadata.BookReviews
import dev.gavenda.kozeki.data.metadata.BookMatcher
import dev.gavenda.kozeki.data.metadata.BrowseOrder
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.metadata.SeriesPage
import dev.gavenda.kozeki.data.metadata.await
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.singleLineOrNull
import dev.gavenda.kozeki.data.model.singleLines
import java.time.Clock
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Hardcover's GraphQL API. Books are found through its `search` action alone, for both free-text
 * and ISBN lookups, because the search index already carries every ISBN of a book. An author's
 * books are their `contributions`. Reviews are the `user_books` rows of other readers, which need
 * wider scopes than search does.
 */
class HardcoverProvider(
    private val client: OkHttpClient,
    private val auth: HardcoverAuth,
    private val clock: Clock = Clock.systemDefaultZone(),
) : MetadataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override val source: MetadataSource = MetadataSource.HARDCOVER

    override suspend fun unavailableReason(): MetadataProvider.Unavailable? =
        if (auth.isSignedIn()) null else MetadataProvider.Unavailable.SIGNED_OUT

    // A search is for one book to read; an ISBN names exactly what was asked for, set or not.
    override suspend fun search(query: String, page: Int): BookPage {
        val hits = searchDocuments(query, page)
        return BookPage(
            books = hits.documents.filterNot(::isCollection).mapNotNull { toMetadata(it, queriedIsbn = null) },
            hasMore = hits.hasMore,
        )
    }

    override suspend fun browseOrders(): List<BrowseOrder> = BrowseOrders

    override suspend fun browse(order: BrowseOrder, page: Int): BookPage {
        val offset = (page - 1) * BROWSE_PAGE_SIZE
        return when (order) {
            BrowseOrder.TRENDING_WEEK -> trending("week", offset)
            BrowseOrder.TRENDING_MONTH -> trending("month", offset)
            BrowseOrder.TRENDING_YEAR -> trending("one_year", offset)
            BrowseOrder.POPULAR -> trending("all", offset)
            BrowseOrder.TOP_RATED -> bookList(TOP_RATED_QUERY, offset)
            BrowseOrder.NEW_RELEASES -> bookList(NEW_RELEASES_QUERY, offset, today = LocalDate.now(clock).toString())
            BrowseOrder.LATEST_UPDATES -> BookPage()
        }
    }

    /**
     * The books most read over [duration], as Hardcover's own trending list. It only names them,
     * so their details take a second request, and come back in no order of their own.
     */
    private suspend fun trending(duration: String, offset: Int): BookPage {
        val idsBody = buildJsonObject {
            put("query", TRENDING_QUERY)
            putJsonObject("variables") {
                put("duration", duration)
                put("limit", BROWSE_PAGE_SIZE)
                put("offset", offset)
            }
        }.toString()
        val ids = (execute(idsBody)["books_trending"].asObject()?.get("ids") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        if (ids.isEmpty()) return BookPage()

        val booksBody = buildJsonObject {
            put("query", BOOKS_BY_IDS_QUERY)
            putJsonObject("variables") { put("ids", JsonArray(ids.map(::JsonPrimitive))) }
        }.toString()
        val rows = (execute(booksBody)["books"] as? JsonArray).orEmpty().mapNotNull { it.asObject() }
        val byId = rows.associateBy { it.string("id")?.toIntOrNull() }
        return BookPage(
            books = ids.mapNotNull { byId[it] }.filterNot(::isCollection).mapNotNull(::bookToMetadata),
            hasMore = ids.size >= BROWSE_PAGE_SIZE,
        )
    }

    /** A page of the `books` table as [query] filters and orders it. */
    private suspend fun bookList(query: String, offset: Int, today: String? = null): BookPage {
        val body = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("limit", BROWSE_PAGE_SIZE)
                put("offset", offset)
                today?.let { put("today", it) }
            }
        }.toString()
        val rows = (execute(body)["books"] as? JsonArray).orEmpty().mapNotNull { it.asObject() }
        return BookPage(
            books = rows.filterNot(::isCollection).mapNotNull(::bookToMetadata),
            hasMore = rows.size >= BROWSE_PAGE_SIZE,
        )
    }

    override suspend fun findByIsbn(isbn13: String): List<BookMetadata> =
        searchDocuments(isbn13, page = 1).documents.mapNotNull { toMetadata(it, isbn13) }.filter { it.isbn13 == isbn13 }

    override suspend fun book(sourceId: String): BookMetadata? {
        // Hardcover's book IDs are integers; anything else cannot be one of its books.
        val id = sourceId.toIntOrNull() ?: return null
        val body = buildJsonObject {
            put("query", BOOK_QUERY)
            putJsonObject("variables") { put("id", id) }
        }.toString()
        return execute(body)["books_by_pk"].asObject()?.let(::bookToMetadata)
    }

    override suspend fun author(authorId: String, page: Int): AuthorPage {
        // Hardcover's author IDs are integers; anything else cannot be one of its authors.
        val id = authorId.toIntOrNull() ?: return AuthorPage()
        val body = buildJsonObject {
            put("query", AUTHOR_QUERY)
            putJsonObject("variables") {
                put("id", id)
                put("limit", AUTHOR_PAGE_SIZE)
                put("offset", (page - 1) * AUTHOR_PAGE_SIZE)
            }
        }.toString()

        val author = execute(body)["authors_by_pk"].asObject() ?: return AuthorPage()
        val name = author.string("name")?.singleLineOrNull() ?: return AuthorPage()
        val rows = (author["contributions"] as? JsonArray).orEmpty().mapNotNull { it.asObject() }
        return AuthorPage(
            author = Author(
                id = authorId,
                name = name,
                bio = author.string("bio")?.let(::plainText)?.takeIf { it.isNotEmpty() },
                bornYear = author.int("born_year"),
                deathYear = author.int("death_year"),
                location = author.string("location")?.singleLineOrNull(),
                booksCount = author.int("books_count") ?: 0,
                imageUrl = author["image"].asObject()?.string("url")?.takeIf { it.startsWith("https://") },
                infoUrl = author.string("slug")?.let { "https://hardcover.app/authors/$it" },
            ),
            books = BookPage(
                // Their own books only, not the ones they translated, narrated or introduced.
                books = rows.filter { isWriter(it.string("contribution")) }
                    .mapNotNull { it["book"].asObject() }
                    .filterNot(::isCollection)
                    .mapNotNull(::bookToMetadata),
                hasMore = rows.size >= AUTHOR_PAGE_SIZE,
            ),
        )
    }

    override suspend fun series(name: String): SeriesPage {
        val wanted = name.trim().takeIf { it.isNotEmpty() } ?: return SeriesPage()
        val series = seriesNamed(wanted) ?: seriesFound(wanted)?.let { seriesWithId(it) } ?: return SeriesPage()
        val seriesName = series.string("name")?.singleLineOrNull() ?: return SeriesPage()
        return SeriesPage(
            name = seriesName,
            infoUrl = series.string("slug")?.let { "https://hardcover.app/series/$it" },
            books = (series["book_series"] as? JsonArray).orEmpty().mapNotNull { entry ->
                val row = entry.asObject() ?: return@mapNotNull null
                row["book"].asObject()?.let(::bookToMetadata)
                    ?.copy(series = seriesName, seriesNumber = row.double("position"))
            },
        )
    }

    /**
     * The series called exactly [name]. Of several, the one with the most books. The API refuses
     * matching that ignores case, which is left to the search after this.
     */
    private suspend fun seriesNamed(name: String): JsonObject? {
        val body = buildJsonObject {
            put("query", SERIES_BY_NAME_QUERY)
            putJsonObject("variables") { put("name", name) }
        }.toString()
        return (execute(body)["series"] as? JsonArray)?.firstOrNull()?.asObject()
    }

    /**
     * The ID of the series a search for [name] puts first, as long as its name begins with [name]:
     * a series named in an EPUB as "Tearmoon Empire" is "Tearmoon Empire (Light Novel)" here.
     */
    private suspend fun seriesFound(name: String): Int? {
        val body = buildJsonObject {
            put("query", SERIES_SEARCH_QUERY)
            putJsonObject("variables") { put("query", name) }
        }.toString()
        val hits = execute(body)["search"].asObject()?.get("results").asObject()?.get("hits") as? JsonArray
        val top = hits?.firstOrNull()?.asObject()?.get("document").asObject() ?: return null
        val found = top.string("name")?.let(BookMatcher::normalize) ?: return null
        if (!found.startsWith(BookMatcher.normalize(name))) return null
        return top.string("id")?.toIntOrNull()
    }

    private suspend fun seriesWithId(id: Int): JsonObject? {
        val body = buildJsonObject {
            put("query", SERIES_BY_ID_QUERY)
            putJsonObject("variables") { put("id", id) }
        }.toString()
        return (execute(body)["series"] as? JsonArray)?.firstOrNull()?.asObject()
    }

    /** Whether a contribution in [role] is the writing of the book. The main author's has no role at all. */
    private fun isWriter(role: String?): Boolean = role.isNullOrBlank() || role.trim().lowercase() in WriterRoles

    /** A row of the `books` table, which names things differently from a search document. */
    private fun bookToMetadata(book: JsonObject): BookMetadata? {
        val id = book.string("id") ?: return null
        val title = book.string("title")?.singleLineOrNull() ?: return null
        val contributors = (book["contributions"] as? JsonArray).orEmpty()
            .mapNotNull { it.asObject() }
            .filter { isWriter(it.string("contribution")) }
            .mapNotNull { it["author"].asObject()?.let(::toAuthorRef) }
            .distinct()
        val isbn13 = listOf("default_physical_edition", "default_ebook_edition")
            .firstNotNullOfOrNull { edition -> book[edition].asObject()?.string("isbn_13")?.let(Isbn::toIsbn13) }
        val series = book["featured_book_series"].asObject()
        val seriesName = series?.get("series").asObject()?.string("name")?.singleLineOrNull()
        return BookMetadata(
            source = MetadataSource.HARDCOVER,
            sourceId = id,
            title = title,
            subtitle = book.string("subtitle")?.singleLineOrNull(),
            series = seriesName,
            seriesNumber = seriesName?.let { series?.double("position") },
            authors = contributors.map { it.name },
            authorRefs = contributors,
            description = book.string("description")?.takeIf { it.isNotBlank() },
            publishedDate = book.string("release_date") ?: book.int("release_year")?.toString(),
            pageCount = book.int("pages")?.takeIf { it > 0 },
            isbn10 = isbn13?.let(Isbn::toIsbn10),
            isbn13 = isbn13,
            coverUrl = book["image"].asObject()?.string("url")?.takeIf { it.startsWith("https://") },
            averageRating = book.float("rating")?.takeIf { it > 0f },
            infoUrl = book.string("slug")?.let { "https://hardcover.app/books/$it" },
        )
    }

    private fun toAuthorRef(author: JsonObject): AuthorRef? {
        val id = author.string("id") ?: return null
        val name = author.string("name")?.singleLineOrNull() ?: return null
        return AuthorRef(id, name)
    }

    override suspend fun reviews(sourceId: String): BookReviews {
        // Hardcover's book IDs are integers; anything else cannot be one of its books.
        val bookId = sourceId.toIntOrNull() ?: return BookReviews()
        val data = try {
            execute(reviewsBody(bookId, REVIEWS_QUERY))
        } catch (e: MetadataException.MissingScope) {
            // A query touching a field the token may not read is refused whole, so when it is only
            // the reviewers' names that are out of reach, settle for the reviews without them.
            if (e.scope?.contains(USERS_SCOPE) != true) throw e
            execute(reviewsBody(bookId, ANONYMOUS_REVIEWS_QUERY))
        }

        val book = data["books_by_pk"].asObject()
        val reviews = (data["user_books"] as? JsonArray).orEmpty().mapNotNull { it.asObject()?.let(::toReview) }
        return BookReviews(
            averageRating = book?.float("rating")?.takeIf { it > 0f },
            ratingsCount = book?.int("ratings_count") ?: 0,
            reviewsCount = maxOf(book?.int("reviews_count") ?: 0, reviews.size),
            reviews = reviews,
        )
    }

    private fun reviewsBody(bookId: Int, query: String): String = buildJsonObject {
        put("query", query)
        putJsonObject("variables") {
            put("id", bookId)
            put("limit", REVIEWS_LIMIT)
        }
    }.toString()

    private fun toReview(entry: JsonObject): BookReview? {
        val id = entry.string("id") ?: return null
        val text = entry.string("review_raw")?.trim()?.takeIf { it.isNotEmpty() }
            // Older reviews only exist as markup.
            ?: entry.string("review")?.let(::plainText)?.takeIf { it.isNotEmpty() }
            ?: return null
        val user = entry["user"].asObject()
        return BookReview(
            id = id,
            reviewer = (user?.string("name") ?: user?.string("username"))?.singleLineOrNull(),
            rating = entry.float("rating")?.takeIf { it > 0f },
            text = text,
            hasSpoilers = (entry["review_has_spoilers"] as? JsonPrimitive)?.booleanOrNull == true,
            // A timestamp without a zone, of which only the date is shown.
            reviewedOn = entry.string("reviewed_at")
                ?.let { runCatching { LocalDate.parse(it.take(10)).toEpochDay() }.getOrNull() },
            likes = entry.int("likes_count") ?: 0,
        )
    }

    private fun plainText(markup: String): String =
        markup.replace(LineBreakTag, "\n").replace(AnyTag, "").trim()

    private class Hits(val documents: List<JsonObject>, val hasMore: Boolean)

    private suspend fun searchDocuments(query: String, page: Int): Hits {
        val body = buildJsonObject {
            put("query", SEARCH_QUERY)
            putJsonObject("variables") {
                put("query", query)
                put("page", page)
                put("perPage", SEARCH_PAGE_SIZE)
            }
        }.toString()

        val results = execute(body)["search"].asObject()?.get("results").asObject()
        val hits = results?.get("hits") as? JsonArray ?: return Hits(emptyList(), hasMore = false)
        // The total is the surer sign of a next page; without it, a full page suggests one.
        val hasMore = results.int("found")?.let { page * SEARCH_PAGE_SIZE < it } ?: (hits.size >= SEARCH_PAGE_SIZE)
        return Hits(hits.mapNotNull { hit -> hit.asObject()?.get("document").asObject() }, hasMore)
    }

    /**
     * Whether the document is several books sold as one: a box set, an omnibus, a bundle. Hardcover
     * flags these as compilations, but not dependably, so the title gets a say as well.
     */
    private fun isCollection(document: JsonObject): Boolean =
        (document["compilation"] as? JsonPrimitive)?.booleanOrNull == true ||
            listOfNotNull(document.string("title"), document.string("subtitle")).any(CollectionTitle::containsMatchIn)

    /** Sends a GraphQL request, refreshing the token once if the API says it has expired. */
    private suspend fun execute(body: String): JsonObject {
        val token = auth.accessToken()
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.SIGNED_OUT)

        var response = send(body, token)
        if (response.code == 401) {
            response.close()
            val fresh = auth.refreshAfterRejection(token)
                ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.SIGNED_OUT)
            response = send(body, fresh)
        }

        response.use {
            val text = it.body.string()
            when {
                it.code == 429 -> throw MetadataException.RateLimited()
                it.code == 403 && errorField(text, "error") == "insufficient_scope" ->
                    throw MetadataException.MissingScope(errorField(text, "scope"))
                it.code == 401 || it.code == 403 -> throw MetadataException.Unauthorized(errorMessage(text))
                !it.isSuccessful -> throw MetadataException.Server(it.code, errorMessage(text))
            }
            val root = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw MetadataException.Server(response.code, "Unreadable response") }
            (root["errors"] as? JsonArray)?.firstOrNull()?.asObject()?.string("message")?.let { message ->
                throw MetadataException.Server(response.code, message)
            }
            return root["data"].asObject() ?: throw MetadataException.Server(response.code, "Empty response")
        }
    }

    private suspend fun send(body: String, token: String) = client.newCall(
        Request.Builder()
            .url(HardcoverAuth.GRAPHQL_ENDPOINT)
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(HardcoverAuth.JSON_MEDIA_TYPE))
            .build(),
    ).await()

    private fun errorMessage(body: String): String =
        errorField(body, "error_description") ?: errorField(body, "message") ?: errorField(body, "error")
            ?: "Request failed"

    private fun errorField(body: String, name: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject.string(name) }.getOrNull()

    private fun toMetadata(document: JsonObject, queriedIsbn: String?): BookMetadata? {
        val id = document.string("id") ?: return null
        val title = document.string("title")?.singleLineOrNull() ?: return null
        val isbns = (document["isbns"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .mapNotNull(Isbn::toIsbn13)
        // A book's search document lists the ISBNs of all its editions; keep the one that was asked for.
        val isbn13 = queriedIsbn?.takeIf { it in isbns } ?: isbns.firstOrNull()
        val slug = document.string("slug")
        // The series the book's page shows; a document without one carries an empty object.
        val series = document["featured_series"].asObject()
        val seriesName = series?.get("series").asObject()?.string("name")?.singleLineOrNull()
        return BookMetadata(
            source = MetadataSource.HARDCOVER,
            sourceId = id,
            title = title,
            subtitle = document.string("subtitle")?.singleLineOrNull(),
            series = seriesName,
            seriesNumber = seriesName?.let { series?.double("position") ?: document.double("featured_series_position") },
            authors = strings(document["author_names"]),
            authorRefs = (document["contributions"] as? JsonArray).orEmpty()
                .mapNotNull { it.asObject()?.get("author").asObject()?.let(::toAuthorRef) }
                .distinct(),
            description = document.string("description")?.takeIf { it.isNotBlank() },
            publishedDate = document.string("release_date") ?: document.int("release_year")?.toString(),
            pageCount = document.int("pages")?.takeIf { it > 0 },
            isbn10 = isbn13?.let(Isbn::toIsbn10),
            isbn13 = isbn13,
            categories = strings(document["genres"]),
            coverUrl = document["image"].asObject()?.string("url")?.takeIf { it.startsWith("https://") },
            averageRating = document.float("rating")?.takeIf { it > 0f },
            infoUrl = slug?.let { "https://hardcover.app/books/$it" },
        )
    }

    private fun strings(element: JsonElement?): List<String> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.singleLines()

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    // Ratings are numerics, which the API may send as a number or as a string.
    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private companion object {
        const val SEARCH_PAGE_SIZE = 12

        const val SEARCH_QUERY = $$"query Search($query: String!, $page: Int!, $perPage: Int!) { " +
            $$"search(query: $query, query_type: \"Book\", per_page: $perPage, page: $page) { results } }"

        const val AUTHOR_PAGE_SIZE = 20

        // Duplicates merged into another book are left out, and the most read books come first.
        private const val AUTHOR_BOOK_ROWS =
            "contributions(where: { contributable_type: { _eq: \"Book\" }, " +
                "book: { canonical_id: { _is_null: true } } }, " +
                $$"order_by: [{ book: { users_count: desc } }, { id: asc }], limit: $limit, offset: $offset)"
        private const val AUTHOR_BOOK_FIELDS =
            "id title subtitle slug description release_date release_year pages rating compilation image { url } " +
                "contributions { contribution author { id name } } " +
                "default_physical_edition { isbn_13 } default_ebook_edition { isbn_13 } " +
                "featured_book_series { position series { name } }"

        const val AUTHOR_QUERY = $$"query Author($id: Int!, $limit: Int!, $offset: Int!) { " +
            $$"authors_by_pk(id: $id) { name bio born_year death_year location books_count slug image { url } " +
            "$AUTHOR_BOOK_ROWS { contribution book { $AUTHOR_BOOK_FIELDS } } } }"

        const val SERIES_BOOKS_LIMIT = 100

        // One book for each place in the series, the most read of any duplicates, as on the series'
        // page on Hardcover: no merged books, no part-books, no box sets of several.
        private const val SERIES_FIELDS =
            "id name slug book_series(distinct_on: position, " +
                "order_by: [{ position: asc }, { book: { users_count: desc } }], " +
                "where: { compilation: { _eq: false }, " +
                "book: { canonical_id: { _is_null: true }, is_partial_book: { _eq: false } } }, " +
                "limit: $SERIES_BOOKS_LIMIT) { position book { $AUTHOR_BOOK_FIELDS } }"

        const val SERIES_BY_NAME_QUERY = $$"query Series($name: String!) { " +
            $$"series(where: { name: { _eq: $name }, canonical_id: { _is_null: true }, books_count: { _gt: 0 } }, " +
            "order_by: { books_count: desc }, limit: 1) { $SERIES_FIELDS } }"
        const val SERIES_BY_ID_QUERY = $$"query Series($id: Int!) { " +
            $$"series(where: { id: { _eq: $id } }, limit: 1) { $$SERIES_FIELDS } }"
        const val SERIES_SEARCH_QUERY = $$"query SeriesSearch($query: String!) { " +
            $$"search(query: $query, query_type: \"Series\", per_page: 1, page: 1) { results } }"

        val BrowseOrders = listOf(
            BrowseOrder.TRENDING_MONTH,
            BrowseOrder.TRENDING_WEEK,
            BrowseOrder.TRENDING_YEAR,
            BrowseOrder.POPULAR,
            BrowseOrder.TOP_RATED,
            BrowseOrder.NEW_RELEASES,
        )

        const val BROWSE_PAGE_SIZE = 24

        const val TRENDING_QUERY = $$"query Trending($duration: TrendingDuration!, $limit: Int!, $offset: Int!) { " +
            $$"books_trending(duration: $duration, limit: $limit, offset: $offset) { ids } }"
        const val BOOKS_BY_IDS_QUERY = $$"query Books($ids: [Int!]!) { " +
            $$"books(where: { id: { _in: $ids } }) { $$AUTHOR_BOOK_FIELDS } }"

        // Books on their own, not merged into another, not part of one, not several in a box.
        private const val WHOLE_BOOKS =
            "canonical_id: { _is_null: true }, compilation: { _eq: false }, is_partial_book: { _eq: false }"

        // Enough ratings that one reader's five stars cannot put a book at the top.
        const val TOP_RATED_QUERY = $$"query TopRated($limit: Int!, $offset: Int!) { " +
            "books(where: { $WHOLE_BOOKS, ratings_count: { _gte: 1000 } }, " +
            $$"order_by: [{ rating: desc_nulls_last }, { id: asc }], limit: $limit, offset: $offset) { $$AUTHOR_BOOK_FIELDS } }"

        // Out already, and read by enough people that it is not just any book with a date.
        const val NEW_RELEASES_QUERY = $$"query NewReleases($today: date!, $limit: Int!, $offset: Int!) { " +
            $$"books(where: { $$WHOLE_BOOKS, release_date: { _lte: $today }, users_count: { _gte: 50 } }, " +
            $$"order_by: [{ release_date: desc }, { users_count: desc }], limit: $limit, offset: $offset) { $$AUTHOR_BOOK_FIELDS } }"

        const val BOOK_QUERY = $$"query Book($id: Int!) { books_by_pk(id: $id) { $$AUTHOR_BOOK_FIELDS } }"

        val WriterRoles = setOf("author", "writer")

        const val REVIEWS_LIMIT = 20

        /** The scope other readers' names and profiles sit behind. */
        const val USERS_SCOPE = "read:users"

        // The most liked reviews first, as on the book's page on Hardcover.
        private const val REVIEW_ROWS =
            $$"user_books(where: { book_id: { _eq: $id }, has_review: { _eq: true } }, " +
                $$"order_by: [{ likes_count: desc }, { reviewed_at: desc_nulls_last }], limit: $limit)"
        private const val REVIEW_FIELDS = "id rating review_raw review review_has_spoilers reviewed_at likes_count"
        private const val BOOK_FIGURES = $$"books_by_pk(id: $id) { rating ratings_count reviews_count }"

        const val REVIEWS_QUERY = $$"query Reviews($id: Int!, $limit: Int!) { " +
            "$BOOK_FIGURES $REVIEW_ROWS { $REVIEW_FIELDS user { name username } } }"
        const val ANONYMOUS_REVIEWS_QUERY = $$"query Reviews($id: Int!, $limit: Int!) { " +
            "$BOOK_FIGURES $REVIEW_ROWS { $REVIEW_FIELDS } }"

        // Deliberately narrow: a "collection" on its own is as often one book of short stories.
        val CollectionTitle = Regex(
            "(?i)\\b(box(ed)?[ -]?set|omnibus|(e?books?|series|trilogy) bundle|\\d+[ -]book\\b|books? \\d+ ?(-|–|—|to|&|and) ?\\d+|" +
                "(complete|entire|whole) (series|saga|trilogy|collection)|(series|trilogy|saga) collection|" +
                "collection set|\\d+ (books|volumes|novels) (collection|set)|" +
                "vol(ume)?s?\\.? ?\\d+ ?[-–—] ?\\d+)",
        )

        val LineBreakTag = Regex("(?i)<br\\s*/?>|</p>")
        val AnyTag = Regex("<[^>]+>")
    }
}
