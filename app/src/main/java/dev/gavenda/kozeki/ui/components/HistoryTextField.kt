package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private val Separators = charArrayOf(',', ';')

/**
 * Free text with what was typed before on offer. Opening the menu lists [history]; typing narrows it down.
 *
 * @param multiple The field holds several entries separated by commas. Only the one being typed
 *   is completed, and the ones already in the field are not offered again.
 */
@Composable
fun HistoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    history: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    multiple: Boolean = false,
) {
    // Kept with its cursor, which goes to the end when a suggestion fills the field in.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))

    var expanded by remember { mutableStateOf(false) }
    // Only what was typed since the menu opened filters it, so a saved entry does not hide the others.
    var filtering by remember { mutableStateOf(false) }

    // With several entries, typing completes the last one, and opening the menu adds another.
    val typedFrom = if (multiple && filtering) value.lastIndexOfAny(Separators) + 1 else 0
    val typed = if (multiple && !filtering) "" else value.substring(typedFrom).trim()
    val kept = if (multiple) value.substring(0, if (filtering) typedFrom else value.length) else ""
    val entered = kept.split(*Separators).map { it.trim() }
    val suggestions = history.filter { entry ->
        entered.none { it.equals(entry, ignoreCase = true) } &&
            (!filtering || (entry.contains(typed, ignoreCase = true) && !entry.equals(typed, ignoreCase = true)))
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            filtering = false
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                val edited = it.text != field.text
                field = it
                if (edited) {
                    onValueChange(it.text)
                    expanded = true
                    filtering = true
                }
            },
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
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
                        val before = kept.trimEnd().trimEnd(*Separators).trimEnd()
                        val filled = if (before.isEmpty()) suggestion else "$before, $suggestion"
                        field = TextFieldValue(filled, TextRange(filled.length))
                        onValueChange(filled)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
