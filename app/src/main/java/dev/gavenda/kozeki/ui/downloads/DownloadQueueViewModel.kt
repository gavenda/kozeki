package dev.gavenda.kozeki.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.comic.ChapterDownload
import dev.gavenda.kozeki.data.comic.ComicDownloads
import dev.gavenda.kozeki.data.comic.QueuedDownload
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The chapters of one comic in the queue, in the order they will come. */
data class QueuedComic(val book: Book, val chapters: List<QueuedDownload>)

data class DownloadQueueUiState(
    /** The comics with chapters in the queue, the one whose chapter comes first at the top. */
    val comics: List<QueuedComic> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && comics.isEmpty()
    val chapterCount: Int get() = comics.sumOf { it.chapters.size }
    val anyFailed: Boolean get() = comics.any { comic -> comic.chapters.any { it.state is ChapterDownload.Failed } }
}

/** The chapters of comics waiting to be downloaded or coming in, by comic. */
class DownloadQueueViewModel(
    private val downloads: ComicDownloads,
    library: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<DownloadQueueUiState> =
        combine(downloads.queued, library.observeAll()) { queued, books ->
            val byId = books.associateBy { it.id }
            // A comic stays where its first chapter in the queue puts it.
            val comics = queued.groupBy { it.bookId }.mapNotNull { (bookId, chapters) ->
                byId[bookId]?.let { QueuedComic(it, chapters) }
            }
            DownloadQueueUiState(comics = comics, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQueueUiState())

    fun cancel(item: QueuedDownload) = downloads.cancel(item.bookId, listOf(item.chapter.url))

    fun cancelComic(bookId: String) {
        val urls = uiState.value.comics.firstOrNull { it.book.id == bookId }?.chapters?.map { it.chapter.url }.orEmpty()
        if (urls.isNotEmpty()) downloads.cancel(bookId, urls)
    }

    fun retry(item: QueuedDownload) = downloads.enqueue(item.bookId, listOf(item.chapter))

    fun moveToFront(item: QueuedDownload) = downloads.moveToFront(item.bookId, item.chapter.url)

    fun retryFailed() = downloads.retryFailed()

    fun cancelAll() = downloads.cancelAll()
}
