package dev.gavenda.kozeki.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.Shelf
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState

/** A shelf as being asked about: to be made, renamed or deleted. */
sealed interface ShelfPrompt {
    data object Create : ShelfPrompt
    data class Rename(val shelf: Shelf) : ShelfPrompt
    data class Delete(val shelf: Shelf) : ShelfPrompt
}

/** The dialog for [prompt], if there is one. Deleting a shelf leaves its books in the library. */
@Composable
fun ShelfPrompts(
    prompt: ShelfPrompt?,
    onCreate: (name: String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        ShelfPrompt.Create -> ShelfNameDialog(
            title = R.string.shelf_new,
            confirm = R.string.shelf_create,
            initial = "",
            onConfirm = {
                onDismiss()
                onCreate(it)
            },
            onDismiss = onDismiss,
        )
        is ShelfPrompt.Rename -> ShelfNameDialog(
            title = R.string.shelf_rename,
            confirm = R.string.shelf_save,
            initial = prompt.shelf.name,
            onConfirm = {
                onDismiss()
                onRename(prompt.shelf.id, it)
            },
            onDismiss = onDismiss,
        )
        is ShelfPrompt.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(painterResource(R.drawable.delete_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            iconContentColor = MaterialTheme.colorScheme.error,
            title = { Text(stringResource(R.string.shelf_delete_title, prompt.shelf.name)) },
            text = { Text(stringResource(R.string.shelf_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        onDelete(prompt.shelf.id)
                    },
                ) { Text(stringResource(R.string.shelf_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}

/** Asks for a shelf's name, which must have something in it. */
@Composable
fun ShelfNameDialog(
    title: Int,
    confirm: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shelf_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Checkboxes for putting books on shelves and taking them off. [memberCounts] says how many of the
 * [picked] books are on each shelf, so a shelf with some of them shows a dash; a tap puts all of
 * them on it. Shelves made here get the books too. Nothing changes until Done.
 */
@Composable
fun ShelfChecklistSheet(
    shelves: List<Shelf>,
    memberCounts: Map<String, Int>,
    picked: Int,
    onApply: (ShelfChange) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(shelves, memberCounts, picked) {
        shelves.associate { it.id to shelfState(memberCounts[it.id] ?: 0, picked) }
    }
    val chosen = remember { mutableStateMapOf<String, ToggleableState>() }
    val created = remember { mutableStateListOf<String>() }
    var naming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.shelf_add_to_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(shelves, key = { it.id }) { shelf ->
                    val state = chosen[shelf.id] ?: initial[shelf.id] ?: ToggleableState.Off
                    val toggle = { chosen[shelf.id] = state.tapped() }
                    ListItem(
                        headlineContent = { Text(shelf.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { TriStateCheckbox(state = state, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable(onClick = toggle),
                    )
                }
                items(created.toList(), key = { "new-$it" }) { name ->
                    ListItem(
                        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { TriStateCheckbox(state = ToggleableState.On, onClick = null) },
                        trailingContent = {
                            IconButton(onClick = { created.remove(name) }) {
                                Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.action_remove), modifier = Modifier.size(24.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.shelf_new)) },
                leadingContent = { Icon(painterResource(R.drawable.add_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable { naming = true },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Box(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onApply(shelfChange(initial, chosen.toMap()).copy(create = created.toList()))
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.shelf_done)) }
            }
        }
    }
    if (naming) {
        ShelfNameDialog(
            title = R.string.shelf_new,
            confirm = R.string.shelf_create,
            initial = "",
            onConfirm = { name ->
                naming = false
                val trimmed = name.trim()
                if (trimmed.isNotEmpty() && trimmed !in created) created += trimmed
            },
            onDismiss = { naming = false },
        )
    }
}

/**
 * The books of the library not yet on the open shelf, to tick and put on it. [candidates] is the
 * lot; the search narrows it by title or author.
 */
@Composable
fun ShelfAddBooksSheet(
    candidates: List<Book>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val ticked = remember { mutableStateListOf<String>() }
    val shown = remember(candidates, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            candidates
        } else {
            candidates.filter { book ->
                book.title.contains(needle, ignoreCase = true) ||
                    book.authors.any { it.contains(needle, ignoreCase = true) }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.shelf_add_books),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.shelf_add_books_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.shelf_add_books_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.id }) { book ->
                        val on = book.id in ticked
                        ListItem(
                            headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = book.authors.takeIf { it.isNotEmpty() }?.let { authors ->
                                { Text(authors.joinToString(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            },
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TriStateCheckbox(
                                        state = if (on) ToggleableState.On else ToggleableState.Off,
                                        onClick = null,
                                    )
                                    BookCover(book, Modifier.width(40.dp), showTitleOnPlaceholder = false)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { if (on) ticked.remove(book.id) else ticked.add(book.id) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Box(Modifier.width(8.dp))
                Button(
                    enabled = ticked.isNotEmpty(),
                    onClick = {
                        onAdd(ticked.toList())
                        onDismiss()
                    },
                ) {
                    Text(pluralStringResource(R.plurals.shelf_add_count, ticked.size, ticked.size))
                }
            }
        }
    }
}
