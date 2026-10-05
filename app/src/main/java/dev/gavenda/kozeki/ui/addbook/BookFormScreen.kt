package dev.gavenda.kozeki.ui.addbook

import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.koin.core.parameter.parametersOf
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.BookDraft
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.CoverAspectRatio
import dev.gavenda.kozeki.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

/** Only pictures make a cover. */
internal val CoverPhotoRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

@Composable
fun BookFormScreen(
    bookId: String?,
    onBack: () -> Unit,
    onAdded: (String) -> Unit,
    viewModel: BookFormViewModel = koinViewModel(key = "book-form-$bookId") { parametersOf(bookId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BookFormEvent.Added -> onAdded(event.bookId)
                BookFormEvent.Closed -> onBack()
            }
        }
    }

    BookFormContent(
        state = state,
        editing = bookId != null,
        onBack = onBack,
        onAdd = viewModel::add,
        onSave = viewModel::save,
    )
}

/**
 * A full-screen dialog: closed with the X, which asks first when something was changed, and saved
 * from the top bar.
 *
 * @param editing The form corrects an existing book, [BookFormUiState.book], instead of adding one.
 */
@Composable
fun BookFormContent(
    state: BookFormUiState,
    editing: Boolean,
    onBack: () -> Unit,
    onAdd: (BookDraft, Uri?) -> Unit,
    onSave: (BookDraft, cover: Uri?, removeCover: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The fields start from the book, so they are only laid out once it is there.
    if (!editing || state.book != null) {
        BookForm(state.book, state.saving, onBack, onAdd, onSave, modifier)
    } else {
        BookFormScaffold(editing, onClose = onBack, modifier = modifier) {
            if (state.loading) LoadingIndicator(Modifier.padding(48.dp))
        }
    }
}

/** The top bar every state of the form has, around [content]. [actions] sit at the end of the bar. */
@Composable
private fun BookFormScaffold(
    editing: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editing) R.string.book_form_edit_title else R.string.manual_book_title))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = actions,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).imePadding(), contentAlignment = Alignment.TopCenter) {
            content()
        }
    }
}

/** What the text fields hold, to tell whether anything was typed since the form opened. */
private data class FormText(
    val title: String,
    val authors: String,
    val subtitle: String,
    val publisher: String,
    val published: String,
    val pages: String,
    val isbn: String,
    val description: String,
) {
    constructor(book: Book?) : this(
        title = book?.title.orEmpty(),
        authors = book?.authors.orEmpty().joinToString(", "),
        subtitle = book?.subtitle.orEmpty(),
        publisher = book?.publisher.orEmpty(),
        // As it is stored. Sources give some books only a year, which stays as it is until a date is picked.
        published = book?.publishedDate.orEmpty(),
        pages = book?.pageCount?.toString().orEmpty(),
        isbn = (book?.isbn13 ?: book?.isbn10).orEmpty(),
        description = book?.description.orEmpty(),
    )
}

@Composable
private fun BookForm(
    book: Book?,
    saving: Boolean,
    onBack: () -> Unit,
    onAdd: (BookDraft, Uri?) -> Unit,
    onSave: (BookDraft, cover: Uri?, removeCover: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = remember(book) { FormText(book) }
    // A picture picked here, and whether the one the book had was taken off.
    var cover by rememberSaveable { mutableStateOf<Uri?>(null) }
    var coverRemoved by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var authors by rememberSaveable { mutableStateOf(initial.authors) }
    var subtitle by rememberSaveable { mutableStateOf(initial.subtitle) }
    var publisher by rememberSaveable { mutableStateOf(initial.publisher) }
    var published by rememberSaveable { mutableStateOf(initial.published) }
    var pages by rememberSaveable { mutableStateOf(initial.pages) }
    var isbn by rememberSaveable { mutableStateOf(initial.isbn) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    val changed = cover != null || coverRemoved ||
        FormText(title, authors, subtitle, publisher, published, pages, isbn, description) != initial
    // Not while saving: the form closes itself once the book is stored.
    BackHandler(enabled = changed && !saving) { confirmingDiscard = true }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            cover = uri
            coverRemoved = false
        }
    }

    val authorNames = authors.split(',', ';').filter { it.isNotBlank() }
    val isbnValid = isbn.isBlank() || Isbn.toIsbn13(isbn) != null
    val canSubmit = title.isNotBlank() && authorNames.isNotEmpty() && isbnValid && !saving
    val draft = {
        BookDraft(
            title = title,
            subtitle = subtitle,
            authors = authorNames,
            description = description,
            publisher = publisher,
            publishedDate = published,
            pageCount = pages.toIntOrNull(),
            isbn = isbn,
        )
    }
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

    BookFormScaffold(
        editing = book != null,
        onClose = { if (changed && !saving) confirmingDiscard = true else onBack() },
        modifier = modifier,
        actions = {
            TextButton(
                onClick = { if (book != null) onSave(draft(), cover, coverRemoved) else onAdd(draft(), cover) },
                enabled = canSubmit,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val ownCover = book?.hasCustomCover == true && !coverRemoved
                Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverPicker(
                        cover = cover ?: book?.coverModel.takeIf { !coverRemoved },
                        onClick = { picker.launch(CoverPhotoRequest) },
                    )
                    // Only a cover the user chose can be taken off: first the new pick, then the one before it.
                    if (cover != null || ownCover) {
                        TextButton(onClick = { if (cover != null) cover = null else coverRemoved = true }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.manual_book_field_title)) },
                        keyboardOptions = words,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = authors,
                        onValueChange = { authors = it },
                        label = { Text(stringResource(R.string.manual_book_field_authors)) },
                        supportingText = { Text(stringResource(R.string.manual_book_authors_hint)) },
                        keyboardOptions = words,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text(stringResource(R.string.manual_book_field_subtitle)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = publisher,
                onValueChange = { publisher = it },
                label = { Text(stringResource(R.string.detail_publisher)) },
                keyboardOptions = words,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = publishedDateOf(published)?.let(::formatDate) ?: published,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.detail_published)) },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    // A read-only field swallows clicks, so the tap that opens the calendar is caught on the way down.
                    modifier = Modifier.weight(1.4f).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) pickingDate = true
                        }
                    },
                )
                OutlinedTextField(
                    value = pages,
                    onValueChange = { pages = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.detail_pages)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = isbn,
                onValueChange = { isbn = it.take(20) },
                label = { Text(stringResource(R.string.detail_isbn)) },
                isError = !isbnValid,
                supportingText = if (isbnValid) {
                    null
                } else {
                    { Text(stringResource(R.string.manual_book_isbn_invalid)) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.manual_book_field_description)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            // With an icon the dialog centres its headline, as Material 3 lays out a dialog that has one.
            icon = { Icon(Icons.Rounded.EditOff, contentDescription = null) },
            iconContentColor = MaterialTheme.colorScheme.error,
            title = { Text(stringResource(R.string.book_form_discard_changes)) },
            text = {
                Text(
                    stringResource(
                        if (book != null) {
                            R.string.book_form_discard_changes_message
                        } else {
                            R.string.book_form_discard_book_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDiscard = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.book_form_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) {
                    Text(stringResource(R.string.book_form_keep_editing))
                }
            },
        )
    }

    if (pickingDate) {
        PublishedDateDialog(
            published = published,
            onPick = {
                published = it?.toString().orEmpty()
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The day a stored published date names, or null for one that is empty or only a year or a month. */
private fun publishedDateOf(published: String): LocalDate? = runCatching { LocalDate.parse(published) }.getOrNull()

/** Picks the day a book was published. [onPick] gets null when the date is cleared. */
@Composable
private fun PublishedDateDialog(published: String, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC midnights, so convert through UTC rather than the device zone.
    fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val date = publishedDateOf(published)
    val thisYear = LocalDate.now().year
    // A date that is only a year still opens the calendar in that year.
    val year = published.take(4).toIntOrNull()?.takeIf { it in FIRST_PRINTED_YEAR..thisYear + 1 }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date?.toPickerMillis(),
        initialDisplayedMonthMillis = (date ?: year?.let { LocalDate.of(it, 1, 1) })?.toPickerMillis(),
        yearRange = FIRST_PRINTED_YEAR..thisYear + 1,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                    } else {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (published.isNotEmpty()) {
                    TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.action_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Where the year list of the published date starts: about when books were first printed. */
private const val FIRST_PRINTED_YEAR = 1450

/** The picked cover, or the place to tap to pick one. */
@Composable
private fun CoverPicker(cover: Any?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(CoverAspectRatio)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = stringResource(R.string.cover_choose), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.manual_book_cover_add),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = stringResource(R.string.manual_book_cover),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@ScreenPreviews
@Composable
private fun BookFormAddPreview() {
    AppTheme {
        BookFormContent(BookFormUiState(), editing = false, onBack = {}, onAdd = { _, _ -> }, onSave = { _, _, _ -> })
    }
}

@ScreenPreviews
@Composable
private fun BookFormEditPreview() {
    AppTheme {
        BookFormContent(
            state = BookFormUiState(book = PreviewData.books[0]),
            editing = true,
            onBack = {},
            onAdd = { _, _ -> },
            onSave = { _, _, _ -> },
        )
    }
}
