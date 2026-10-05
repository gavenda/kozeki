package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.ui.PreviewData
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (note == null) R.string.note_add else R.string.note_edit)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.note_hint)) },
                minLines = 4,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * The page reached in a physical copy and how many pages that copy has. The total is optional:
 * without it the page is still kept, only no percentage can be worked out. [onSave] gets a null
 * page when the user stops tracking the physical copy.
 */
@Composable
fun PhysicalProgressDialog(
    book: Book,
    onSave: (page: Int?, pageCount: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens with the old page selected, so typing the new one replaces it.
    var page by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = book.physicalPage?.toString().orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    var pageCount by rememberSaveable { mutableStateOf(book.physicalPageCount?.toString().orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val pageNumber = page.text.toIntOrNull()
    val total = pageCount.toIntOrNull()
    val pageCountValid = pageCount.isEmpty() || (total != null && total > 0)
    val pageValid = pageNumber != null && (total == null || pageNumber <= total)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.physical_dialog_title)) },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = { onSave(pageNumber, total) }, enabled = pageValid && pageCountValid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (book.physicalPage != null) {
                    TextButton(onClick = { onSave(null, total) }) {
                        Text(stringResource(R.string.physical_stop), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * Price, currency, date and place of a purchase. A blank price is allowed: the book is owned, cost
 * unknown. [locationHistory] holds the places entered before, which are suggested while typing.
 */
@Composable
fun PurchaseDialog(
    book: Book,
    lastCurrency: String?,
    locationHistory: List<String>,
    onSave: (priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialCurrency = book.purchaseCurrency ?: lastCurrency ?: defaultCurrency().currencyCode
    var price by rememberSaveable {
        mutableStateOf(book.purchasePriceMinor?.let { priceToInput(it, initialCurrency) }.orEmpty())
    }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }
    var epochDay by rememberSaveable { mutableLongStateOf((book.purchasedOn ?: LocalDate.now()).toEpochDay()) }
    var location by rememberSaveable { mutableStateOf(book.purchaseLocation.orEmpty()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val currencyValid = currency.length == 3 &&
        runCatching { Currency.getInstance(currency) }.isSuccess
    val priceMinor = if (price.isBlank()) null else parsePrice(price, currency)
    val priceValid = price.isBlank() || priceMinor != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    // A read-only field swallows clicks, so the tap that opens the calendar is caught on the way down.
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) pickingDate = true
                        }
                    },
                )
                LocationField(location, onValueChange = { location = it }, history = locationHistory)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(
                        priceMinor,
                        currency.takeIf { priceMinor != null },
                        LocalDate.ofEpochDay(epochDay),
                        location.trim().ifEmpty { null },
                    )
                },
                enabled = priceValid && currencyValid,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (pickingDate) {
        // The picker works in UTC midnights, so convert through UTC rather than the device zone.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        pickingDate = false
                    },
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

/** Free text. Opening the menu lists the places typed for earlier purchases; typing narrows them down. */
@Composable
private fun LocationField(value: String, onValueChange: (String) -> Unit, history: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    // Only what was typed since the menu opened filters it, so a saved place does not hide the others.
    var filtering by remember { mutableStateOf(false) }
    val typed = value.trim()
    val suggestions = if (filtering) {
        history.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }
    } else {
        history
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            filtering = false
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
                filtering = true
            },
            label = { Text(stringResource(R.string.purchase_location)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            trailingIcon = {
                if (history.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun DeleteBookDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_book_title)) },
        text = { Text(stringResource(R.string.delete_book_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The same warning for several books at once, which are counted rather than named. */
@Composable
fun DeleteBooksDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.delete_books_title, count, count)) },
        text = { Text(stringResource(R.string.delete_books_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@PreviewLightDark
@Composable
private fun NoteDialogPreview() {
    AppTheme { NoteDialog(note = PreviewData.notes.first(), onSave = {}, onDelete = {}, onDismiss = {}) }
}

@PreviewLightDark
@Composable
private fun PurchaseDialogPreview() {
    AppTheme {
        PurchaseDialog(
            book = PreviewData.books.first(),
            lastCurrency = "USD",
            locationHistory = listOf("Kobo", "Kinokuniya"),
            onSave = { _, _, _, _ -> },
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PhysicalProgressDialogPreview() {
    AppTheme { PhysicalProgressDialog(book = PreviewData.books[5], onSave = { _, _ -> }, onDismiss = {}) }
}

@PreviewLightDark
@Composable
private fun DeleteBookDialogPreview() {
    AppTheme { DeleteBookDialog(title = "Piranesi", onConfirm = {}, onDismiss = {}) }
}
