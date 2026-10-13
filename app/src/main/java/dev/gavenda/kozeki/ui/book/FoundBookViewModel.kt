package dev.gavenda.kozeki.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.manga.MangaChapterRepository
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.inReadingOrder
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.ui.LookupError
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The book was added and is now the user's own, under [bookId]. With [readChapter] the user asked
 * to read that chapter of it, which is where the page goes on to.
 */
data class FoundBookAdded(val bookId: String, val readChapter: String? = null)

/**
 * A book found on a metadata source, shown as its page would look before it is the user's own.
 * Nothing is stored until [add] is called.
 */
class FoundBookViewModel(
    private val result: BookMetadata,
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
    private val mangaChapters: MangaChapterRepository,
) : ViewModel() {

    private val isManga = result.source == MetadataSource.MANGA

    /** What the source says about the book. A manga's search result says little, so it is filled in. */
    private val details = MutableStateFlow(result)

    /** Null when the source has no reviews, which leaves nothing to ask for. */
    private val reviews = MutableStateFlow<ReviewsUiState?>(null)

    /** What the source lists of a manga, not yet stored. Null for a book that is not one. */
    private val chapters = MutableStateFlow<MangaChaptersUiState?>(null)
    private var remoteChapters: List<SChapter>? = null
    private var reviewsJob: Job? = null
    private var chaptersJob: Job? = null
    private var addJob: Job? = null

    val uiState: StateFlow<BookDetailUiState> = combine(details, reviews, chapters) { details, reviews, chapters ->
        BookDetailUiState(loading = false, book = details.toBook(), reviews = reviews, chapters = chapters)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BookDetailUiState(loading = false, book = result.toBook()),
    )

    private val eventChannel = Channel<FoundBookAdded>(Channel.BUFFERED)
    val events: Flow<FoundBookAdded> = eventChannel.receiveAsFlow()

    init {
        loadReviews()
        if (isManga) {
            loadChapters()
        }
    }

    /** Makes the book the user's own. A second press while the first is being stored is ignored. */
    fun add(acquisition: Acquisition) = store(acquisition, readChapter = null)

    /**
     * Makes the manga the user's own and goes on to read it: [chapterUrl], or the first chapter
     * when it is null.
     */
    fun startReading(chapterUrl: String?) {
        val url = chapterUrl ?: chapters.value?.chapters?.firstOrNull()?.url ?: return
        store(Acquisition.DOWNLOADED, readChapter = url)
    }

    private fun store(acquisition: Acquisition, readChapter: String?) {
        if (addJob?.isActive == true) return
        addJob = viewModelScope.launch {
            val id = library.addFromMetadata(details.value, acquisition)
            // What was listed while the page was open is the book's chapters from the start.
            remoteChapters?.let { mangaChapters.sync(id, it) }
            eventChannel.send(FoundBookAdded(id, readChapter))
        }
    }

    fun retryReviews() = loadReviews()

    fun retryChapters() = loadChapters()

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

    /** What the search result left out of the page, from what the source says of the manga itself. */
    private fun fillIn(full: BookMetadata) {
        details.value = result.copy(
            title = full.title.ifBlank { result.title },
            authors = full.authors.ifEmpty { result.authors },
            description = full.description ?: result.description,
            categories = full.categories.ifEmpty { result.categories },
            coverUrl = full.coverUrl ?: result.coverUrl,
            infoUrl = full.infoUrl ?: result.infoUrl,
        )
    }

    private fun loadChapters() {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            chapters.value = MangaChaptersUiState(loading = true)
            chapters.value = try {
                // One question for both: a source does not take two about the same manga at once.
                val update = mangaChapters.fetch(
                    result.sourceId,
                    result.title,
                    result.sourceMemo,
                    details = true,
                    chapters = true,
                )
                update.details?.let { fillIn(it) }
                val remote = update.chapters.orEmpty()
                remoteChapters = remote
                MangaChaptersUiState(chapters = mangaChapters.preview(remote).inReadingOrder())
            } catch (e: CancellationException) {
                throw e
            } catch (e: MetadataException) {
                MangaChaptersUiState(error = LookupError.from(e))
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
    isManga = source == MetadataSource.MANGA,
)
