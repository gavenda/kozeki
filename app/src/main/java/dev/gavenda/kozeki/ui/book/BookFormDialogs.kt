package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.data.model.PhysicalReading
import dev.gavenda.kozeki.data.model.pagesUnread
import dev.gavenda.kozeki.ui.DialogPreviews
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.components.HistoryTextField
import dev.gavenda.kozeki.ui.defaultCurrency
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.parsePrice
import dev.gavenda.kozeki.ui.priceToInput
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

/** Creates a note, or edits and optionally deletes [note]. */
@Composable
fun NoteDialog(
    note: Note?,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(note?.id) { mutableStateOf(note?.text.orEmpty()) }

    FormDialog(
        title = stringResource(if (note == null) R.string.note_add else R.string.note_edit),
        saveLabel = stringResource(R.string.note_save),
        saveEnabled = text.isNotBlank(),
        onSave = { onSave(text) },
        onDismiss = onDismiss,
        removeLabel = stringResource(R.string.note_remove),
        onRemove = onDelete,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.note_hint)) },
            minLines = 4,
            maxLines = 10,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The page reached in a physical copy and how many pages that copy has. The total is optional:
 * without it the page is still kept, only no percentage can be worked out. [onSave] gets a null
 * page when the user stops tracking the physical copy.
 *
 * Going back to an earlier page, or stopping, unreads the [readings] on record past it. When there
 * are any, the user is told so and asked before [onSave] is called.
 */
@Composable
fun PhysicalProgressDialog(
    book: Book,
    readings: List<PhysicalReading>,
    onSave: (page: Int?, pageCount: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens with the old page selected, so typing the new one replaces it.
    var page by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = book.physicalPage?.toString().orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    var pageCount by rememberSaveable { mutableStateOf(book.physicalPageCount?.toString().orEmpty()) }
    // Going back and stopping both unread pages that are on record, so each asks first.
    var confirmingRewind by rememberSaveable { mutableStateOf(false) }
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val pageNumber = page.text.toIntOrNull()
    val total = pageCount.toIntOrNull()
    val pageCountValid = pageCount.isEmpty() || (total != null && total > 0)
    val pageValid = pageNumber != null && (total == null || pageNumber <= total)
    // The page as it will be kept: a copy with no count of its own ends where the source says the book does.
    val target = pageNumber?.coerceAtMost(total ?: book.pageCount?.takeIf { it > 0 } ?: Int.MAX_VALUE)
    val unreadByPage = if (target != null) pagesUnread(readings, book.physicalPage, target) else 0
    val unreadByStopping = pagesUnread(readings, book.physicalPage, null)

    FormDialog(
        title = stringResource(R.string.physical_dialog_title),
        saveLabel = stringResource(R.string.physical_save),
        saveEnabled = pageValid && pageCountValid,
        onSave = { if (unreadByPage > 0) confirmingRewind = true else onSave(pageNumber, total) },
        onDismiss = onDismiss,
        removeLabel = stringResource(R.string.physical_stop),
        onRemove = if (book.physicalPage != null) {
            { if (unreadByStopping > 0) confirmingStop = true else onSave(null, total) }
        } else {
            null
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = page,
                onValueChange = { page = it.copy(text = it.text.filter(Char::isDigit).take(5)) },
                label = { Text(stringResource(R.string.physical_page)) },
                isError = page.text.isNotEmpty() && !pageValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            OutlinedTextField(
                value = pageCount,
                onValueChange = { pageCount = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.physical_page_count)) },
                isError = !pageCountValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Shown over the form, so that turning it down leaves what was typed where it was.
    if (confirmingRewind && target != null) {
        RewindDialog(
            page = target,
            pagesLost = unreadByPage,
            onConfirm = {
                confirmingRewind = false
                onSave(pageNumber, total)
            },
            onDismiss = { confirmingRewind = false },
        )
    }
    if (confirmingStop) {
        RewindDialog(
            page = null,
            pagesLost = unreadByStopping,
            onConfirm = {
                confirmingStop = false
                onSave(null, total)
            },
            onDismiss = { confirmingStop = false },
        )
    }
}

/**
 * Asks before a physical copy is taken back to [page], since the [pagesLost] that are on record
 * past it leave the timeline with it. A null page is for no longer tracking the copy at all.
 */
@Composable
private fun RewindDialog(page: Int?, pagesLost: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // With an icon the dialog centres its headline, as Material 3 lays out a dialog that has one.
        icon = { Icon(painterResource(R.drawable.history_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = {
            Text(
                if (page != null) {
                    stringResource(R.string.physical_rewind_title, page)
                } else {
                    stringResource(R.string.physical_stop_title)
                },
            )
        },
        text = {
            Text(
                if (page != null) {
                    pluralStringResource(R.plurals.physical_rewind_message, pagesLost, pagesLost, page)
                } else {
                    pluralStringResource(R.plurals.physical_stop_message, pagesLost, pagesLost)
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(if (page != null) R.string.physical_rewind_confirm else R.string.physical_stop))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Price, currency, date, place and memo of a purchase. A blank price is allowed: the book is owned, cost
 * unknown. [locationHistory] holds the places entered before, which are suggested while typing.
 */
@Composable
fun PurchaseDialog(
    book: Book,
    lastCurrency: String?,
    locationHistory: List<String>,
    onSave: (priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?, memo: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialCurrency = book.purchaseCurrency ?: lastCurrency ?: defaultCurrency().currencyCode
    var price by rememberSaveable {
        mutableStateOf(book.purchasePriceMinor?.let { priceToInput(it, initialCurrency) }.orEmpty())
    }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }
    var epochDay by rememberSaveable { mutableLongStateOf((book.purchasedOn ?: LocalDate.now()).toEpochDay()) }
    var location by rememberSaveable { mutableStateOf(book.purchaseLocation.orEmpty()) }
    var memo by rememberSaveable { mutableStateOf(book.purchaseMemo.orEmpty()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val currencyValid = currency.length == 3 &&
        runCatching { Currency.getInstance(currency) }.isSuccess
    val priceMinor = if (price.isBlank()) null else parsePrice(price, currency)
    val priceValid = price.isBlank() || priceMinor != null

    FormDialog(
        title = stringResource(R.string.purchase_dialog_title),
        saveLabel = stringResource(R.string.purchase_save),
        saveEnabled = priceValid && currencyValid,
        onSave = {
            onSave(
                priceMinor,
                currency.takeIf { priceMinor != null },
                LocalDate.ofEpochDay(epochDay),
                location.trim().ifEmpty { null },
                memo.trim().ifEmpty { null },
            )
        },
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(stringResource(R.string.purchase_price)) },
                isError = !priceValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase().filter(Char::isLetter).take(3) },
                label = { Text(stringResource(R.string.purchase_currency)) },
                isError = !currencyValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.width(104.dp),
            )
        }
        OutlinedTextField(
            value = formatDate(LocalDate.ofEpochDay(epochDay)),
            onValueChange = {},
            label = { Text(stringResource(R.string.purchase_date)) },
            readOnly = true,
            singleLine = true,
            trailingIcon = { Icon(painterResource(R.drawable.calendar_month_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            // A read-only field swallows clicks, so the tap that opens the calendar is caught on the way down.
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) pickingDate = true
                }
            },
        )
        HistoryTextField(
            value = location,
            onValueChange = { location = it },
            history = locationHistory,
            label = stringResource(R.string.purchase_location),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text(stringResource(R.string.purchase_memo)) },
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickingDate) {
        // The picker works in UTC midnights, so convert through UTC rather than the device zone.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                Button(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        pickingDate = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The dialog the editing forms share: a title, the fields, then the actions, with the destructive
 * one first when there is one. A tap outside does not close it, so what was typed is only
 * discarded on purpose, with Cancel or Back.
 */
@Composable
private fun FormDialog(
    title: String,
    saveLabel: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onSave, enabled = saveEnabled, shapes = ButtonDefaults.shapes()) { Text(saveLabel) }
        },
        dismissButton = {
            // The dialog lays these out from the end, so the one written last comes first.
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            if (removeLabel != null && onRemove != null) {
                TextButton(onClick = onRemove) {
                    Text(removeLabel, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

@DialogPreviews
@Composable
private fun NoteDialogPreview() {
    AppTheme { NoteDialog(note = PreviewData.notes.first(), onSave = {}, onDelete = {}, onDismiss = {}) }
}

@DialogPreviews
@Composable
private fun PurchaseDialogPreview() {
    AppTheme {
        PurchaseDialog(
            book = PreviewData.books.first(),
            lastCurrency = "USD",
            locationHistory = listOf("Kobo", "Kinokuniya"),
            onSave = { _, _, _, _, _ -> },
            onDismiss = {},
        )
    }
}

@DialogPreviews
@Composable
private fun PhysicalProgressDialogPreview() {
    AppTheme {
        PhysicalProgressDialog(
            book = PreviewData.books[5],
            readings = emptyList(),
            onSave = { _, _ -> },
            onDismiss = {},
        )
    }
}

@DialogPreviews
@Composable
private fun RewindDialogPreview() {
    AppTheme { RewindDialog(page = 150, pagesLost = 62, onConfirm = {}, onDismiss = {}) }
}

@DialogPreviews
@Composable
private fun StopTrackingDialogPreview() {
    AppTheme { RewindDialog(page = null, pagesLost = 212, onConfirm = {}, onDismiss = {}) }
}
