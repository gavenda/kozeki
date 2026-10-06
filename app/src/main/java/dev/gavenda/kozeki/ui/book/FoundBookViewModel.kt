package dev.gavenda.kozeki.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.ui.LookupError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The book was added and is now the user's own, under [bookId]. */
data class FoundBookAdded(val bookId: String)

/**
 * A book found on a metadata source, shown as its page would look before it is the user's own.
 * Nothing is stored until [add] is called.
 */
class FoundBookViewModel(
    private val result: BookMetadata,
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
) : ViewModel() {

    private val book = result.toBook()

    /** Null when the source has no reviews, which leaves nothing to ask for. */
    private val reviews = MutableStateFlow<ReviewsUiState?>(null)
    private var reviewsJob: Job? = null
    private var addJob: Job? = null

    val uiState: StateFlow<BookDetailUiState> = reviews
        .map { BookDetailUiState(loading = false, book = book, reviews = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState(loading = false, book = book))

    private val eventChannel = Channel<FoundBookAdded>(Channel.BUFFERED)
    val events: Flow<FoundBookAdded> = eventChannel.receiveAsFlow()

    init {
        loadReviews()
    }

    /** Makes the book the user's own. A second press while the first is being stored is ignored. */
    fun add(acquisition: Acquisition) {
        if (addJob?.isActive == true) return
        addJob = viewModelScope.launch {
            eventChannel.send(FoundBookAdded(library.addFromMetadata(result, acquisition)))
        }
    }

    fun retryReviews() = loadReviews()

    private fun loadReviews() {
        if (!result.source.hasReviews) return
        reviewsJob?.cancel()
        reviewsJob = viewModelScope.launch {
            reviews.value = ReviewsUiState(loading = true)
            reviews.value = try {
                ReviewsUiState(reviews = metadata.reviews(result.source, result.sourceId))
            } catch (e: MetadataException) {
                ReviewsUiState(error = LookupError.from(e))
            }
        }
    }
}

/** The book as it would be once added, for a page that shows it before it is. It has no ID yet. */
internal fun BookMetadata.toBook(): Book = Book(
    id = "",
    title = title,
    subtitle = subtitle,
    authors = authors,
    authorRefs = authorRefs,
    description = description,
    publisher = publisher,
    publishedDate = publishedDate,
    language = language,
    pageCount = pageCount,
    isbn10 = isbn10,
    isbn13 = isbn13,
    categories = categories,
    coverUrl = coverUrl,
    source = source,
    sourceId = sourceId,
    sourceUrl = infoUrl,
    matchStatus = MatchStatus.MATCHED,
)
