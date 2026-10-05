package dev.gavenda.kozeki.ui.addbook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.BookDraft
import dev.gavenda.kozeki.data.repository.LibraryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookFormUiState(
    /** True while the book to edit is being fetched. The form opens filled in, never empty first. */
    val loading: Boolean = false,
    /** The book being edited, or null when the form adds a new one. */
    val book: Book? = null,
    /** True from the moment the form is submitted, so it cannot be submitted twice. */
    val saving: Boolean = false,
)

sealed interface BookFormEvent {
    data class Added(val bookId: String) : BookFormEvent

    /** The edit was saved, or there was no book left to edit. */
    data object Closed : BookFormEvent
}

/**
 * The form a book's details are typed into, with no metadata source involved: to add a new book,
 * or with [bookId] to correct one that is already there.
 */
class BookFormViewModel(
    private val bookId: String?,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookFormUiState(loading = bookId != null))
    val uiState: StateFlow<BookFormUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BookFormEvent>(Channel.BUFFERED)
    val events: Flow<BookFormEvent> = eventChannel.receiveAsFlow()

    init {
        if (bookId != null) {
            viewModelScope.launch {
                val book = library.getBook(bookId)
                if (book == null) eventChannel.send(BookFormEvent.Closed)
                _uiState.update { it.copy(loading = false, book = book) }
            }
        }
    }

    /** Adds the book as owned: one typed in by hand is taken to be on the shelf already. */
    fun add(draft: BookDraft, cover: Uri?) = submit {
        eventChannel.send(BookFormEvent.Added(library.addManually(draft, Acquisition.PURCHASED, cover)))
    }

    /**
     * Saves the edited details. [cover] is a newly picked picture; [removeCover] drops the one the
     * user had chosen before.
     */
    fun save(draft: BookDraft, cover: Uri?, removeCover: Boolean) = submit {
        val id = bookId ?: return@submit
        library.updateDetails(id, draft)
        when {
            cover != null -> library.setCustomCover(id, cover)
            removeCover -> library.removeCustomCover(id)
        }
        eventChannel.send(BookFormEvent.Closed)
    }

    private fun submit(block: suspend () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }
}
