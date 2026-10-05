package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
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
fun NoteSheet(
    note: Note?,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        NoteSheetContent(note, onSave, onDelete)
    }
}

@Composable
private fun NoteSheetContent(note: Note?, onSave: (String) -> Unit, onDelete: (() -> Unit)?) {
    var text by rememberSaveable(note?.id) { mutableStateOf(note?.text.orEmpty()) }

    SheetForm(
        title = stringResource(if (note == null) R.string.note_add else R.string.note_edit),
        saveLabel = stringResource(R.string.note_save),
        saveEnabled = text.isNotBlank(),
        onSave = { onSave(text) },
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
 */
@Composable
fun PhysicalProgressSheet(
    book: Book,
    onSave: (page: Int?, pageCount: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        PhysicalProgressSheetContent(book, onSave)
    }
}

@Composable
private fun PhysicalProgressSheetContent(book: Book, onSave: (page: Int?, pageCount: Int?) -> Unit) {
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

    SheetForm(
        title = stringResource(R.string.physical_dialog_title),
        saveLabel = stringResource(R.string.physical_save),
        saveEnabled = pageValid && pageCountValid,
        onSave = { onSave(pageNumber, total) },
        removeLabel = stringResource(R.string.physical_stop),
        onRemove = if (book.physicalPage != null) {
            { onSave(null, total) }
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
}

/**
 * Price, currency, date and place of a purchase. A blank price is allowed: the book is owned, cost
 * unknown. [locationHistory] holds the places entered before, which are suggested while typing.
 */
@Composable
fun PurchaseSheet(
    book: Book,
    lastCurrency: String?,
    locationHistory: List<String>,
    onSave: (priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        PurchaseSheetContent(book, lastCurrency, locationHistory, onSave)
    }
}

@Composable
private fun PurchaseSheetContent(
    book: Book,
    lastCurrency: String?,
    locationHistory: List<String>,
    onSave: (priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?) -> Unit,
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

    SheetForm(
        title = stringResource(R.string.purchase_dialog_title),
        saveLabel = stringResource(R.string.purchase_save),
        saveEnabled = priceValid && currencyValid,
        onSave = {
            onSave(
                priceMinor,
                currency.takeIf { priceMinor != null },
                LocalDate.ofEpochDay(epochDay),
                location.trim().ifEmpty { null },
            )
        },
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

/**
 * The layout the editing sheets share: a title, the fields, then the save button at the end of a
 * row that starts with the destructive action when there is one. Swiping the sheet away discards.
 */
@Composable
private fun SheetForm(
    title: String,
    saveLabel: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (removeLabel != null && onRemove != null) {
                TextButton(onClick = onRemove) {
                    Text(removeLabel, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = saveEnabled) { Text(saveLabel) }
        }
    }
}

@PreviewLightDark
@Composable
private fun NoteSheetContentPreview() {
    AppTheme { Surface { NoteSheetContent(note = PreviewData.notes.first(), onSave = {}, onDelete = {}) } }
}

@PreviewLightDark
@Composable
private fun PurchaseSheetContentPreview() {
    AppTheme {
        Surface {
            PurchaseSheetContent(
                book = PreviewData.books.first(),
                lastCurrency = "USD",
                locationHistory = listOf("Kobo", "Kinokuniya"),
                onSave = { _, _, _, _ -> },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PhysicalProgressSheetContentPreview() {
    AppTheme { Surface { PhysicalProgressSheetContent(book = PreviewData.books[5], onSave = { _, _ -> }) } }
}
