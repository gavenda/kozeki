package dev.gavenda.kozeki.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.BookMatcher
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What a search found, split the way the app keeps its books apart. */
data class SearchUiState(
    /** The query these results answer, so the two can never disagree on screen. */
    val query: String = "",
    /** Books with an EPUB. */
    val library: List<Book> = emptyList(),
    val wishlist: List<Book> = emptyList(),
    /** Books that are owned but have no EPUB yet. */
    val purchased: List<Book> = emptyList(),
) {
    val isEmpty: Boolean get() = library.isEmpty() && wishlist.isEmpty() && purchased.isEmpty()
}

// "Ender's Game" should also be found by "enders game", so an apostrophe joins instead of splitting.
private val Apostrophes = Regex("['’ʼ]")

private fun searchable(text: String): String = BookMatcher.normalize(text.replace(Apostrophes, ""))

/** Every book next to the text a query is matched against, prepared once so a keystroke only costs a scan. */
internal class SearchIndex(books: List<Book>) {

    private val entries = books.map { book ->
        book to searchable((listOfNotNull(book.title, book.subtitle) + book.authors).joinToString(" "))
    }

    /**
     * The books in which every word of [query] appears, in the title, the subtitle or an author's
     * name. Case, accents and punctuation make no difference, and a word may be part of a longer
     * one. Books keep the order they were given in.
     */
    fun search(query: String): SearchUiState {
        val words = searchable(query).split(' ').filter { it.isNotEmpty() }
        // No words would match every book.
        val found = if (words.isEmpty()) {
            emptyList()
        } else {
            entries.filter { (_, text) -> words.all(text::contains) }.map { (book, _) -> book }
        }
        val (library, withoutFile) = found.partition { it.inLibrary }
        val (wishlist, purchased) = withoutFile.partition { it.acquisition == Acquisition.WISHLIST }
        return SearchUiState(query.trim(), library, wishlist, purchased)
    }
}

class SearchViewModel(library: LibraryRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> =
        combine(library.observeAll().map(::SearchIndex), query) { index, text -> index.search(text) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }
}
