package dev.gavenda.kozeki.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.BookMatcher
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.OwnedBooks
import dev.gavenda.kozeki.data.metadata.SeriesPage
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.ui.LookupError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One of the user's books in the series, with the number it has there. */
data class OwnedInSeries(val book: Book, val number: Double?)

data class SeriesUiState(
    /** The name the series was opened under, shown until the source answers with its own. */
    val name: String,
    /** The user's books in the series, in order. */
    val owned: List<OwnedInSeries> = emptyList(),
    /** The rest of the series on the source, in order: the books the user does not have. */
    val others: List<BookMetadata> = emptyList(),
    /** The series' page on the source, once it has answered. */
    val infoUrl: String? = null,
    val loading: Boolean = true,
    val error: LookupError? = null,
    /** The source answered, and has no such series. */
    val notFound: Boolean = false,
)

/**
 * A series: the user's own books in it, then the rest of it as the metadata source lists it. A book
 * of theirs counts as in it when it carries the series' name, or when it is one of the source's
 * books of the series, which catches an EPUB that never named its series.
 */
class SeriesViewModel(
    private val name: String,
    private val metadata: MetadataRepository,
    library: LibraryRepository,
) : ViewModel() {

    private val source = MetadataSource.HARDCOVER

    private val remote = MutableStateFlow(Remote())

    private data class Remote(
        val page: SeriesPage? = null,
        val loading: Boolean = true,
        val error: LookupError? = null,
    )

    val uiState: StateFlow<SeriesUiState> =
        combine(remote, library.observeAll()) { remote, books -> build(remote, books) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesUiState(name = name))

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Asks the source for the series, again after a failure. */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            remote.update { it.copy(loading = true, error = null) }
            try {
                val page = metadata.series(source, name)
                remote.update { Remote(page = page, loading = false) }
            } catch (e: MetadataException) {
                remote.update { it.copy(loading = false, error = LookupError.from(e)) }
            }
        }
    }

    private fun build(remote: Remote, books: List<Book>): SeriesUiState =
        seriesState(name, remote.page, books).copy(loading = remote.loading, error = remote.error)
}

/**
 * The series [name] as [page] has it from the source, or null while it has not answered, with the
 * user's [books] in it taken out into their own list.
 */
internal fun seriesState(name: String, page: SeriesPage?, books: List<Book>): SeriesUiState {
    // Under the name it was opened by and the source's own, which can differ: "Tearmoon Empire" in
    // an EPUB is "Tearmoon Empire (Light Novel)" on Hardcover.
    val names = listOfNotNull(name, page?.name).map(BookMatcher::normalize).toSet()
    val byName = books.filter { book -> book.series?.let { BookMatcher.normalize(it) in names } == true }

    val ownedBooks = OwnedBooks(books)
    val others = mutableListOf<BookMetadata>()
    val matched = mutableMapOf<String, Double?>()
    for (result in page?.books.orEmpty()) {
        val book = ownedBooks.find(result)
        if (book == null) others += result else matched.putIfAbsent(book.id, result.seriesNumber)
    }

    val owned = (byName + books.filter { it.id in matched && it !in byName })
        .map { book ->
            // The number the user's book carries comes first; the source's fills a gap.
            val own = book.seriesNumber.takeIf { book in byName }
            OwnedInSeries(book, own ?: matched[book.id])
        }
        .sortedWith(compareBy<OwnedInSeries> { it.number == null }.thenBy { it.number }.thenBy { it.book.title })

    return SeriesUiState(
        name = page?.name ?: name,
        owned = owned,
        others = others,
        infoUrl = page?.infoUrl,
        loading = false,
        notFound = page != null && page.name == null,
    )
}
