package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.AuthorRef

/**
 * The names in [authors], separated by commas. Tapping one of those in [refs], the ones the
 * metadata source keeps a page for, calls [onOpenAuthor]; a tap anywhere else is left to whatever
 * is behind the text.
 *
 * The taps are worked out from where they land in a single Text, so that the names shorten as one
 * when there is no room for all of them.
 */
@Composable
fun AuthorLinks(
    authors: List<String>,
    refs: List<AuthorRef>,
    onOpenAuthor: (AuthorRef) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val linked = remember(authors, refs) { authors.mapNotNull { name -> refs.firstOrNull { it.name == name } } }
    val text = remember(authors, refs, linkColor) {
        buildAnnotatedString {
            authors.forEachIndexed { index, name ->
                if (index > 0) append(", ")
                val ref = refs.firstOrNull { it.name == name }
                if (ref == null) {
                    append(name)
                } else {
                    withStyle(SpanStyle(color = linkColor)) {
                        withAnnotation(AUTHOR_TAG, ref.id) { append(name) }
                    }
                }
            }
        }
    }
    val openAuthor by rememberUpdatedState(onOpenAuthor)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val openLabel = stringResource(R.string.result_open_author)
    Text(
        text = text,
        modifier = modifier
            .pointerInput(text) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val measured = layout ?: return@awaitEachGesture
                    // Past the end of a line there is no name, though the nearest offset is one.
                    val line = measured.getLineForVerticalPosition(down.position.y)
                    if (down.position.x !in measured.getLineLeft(line)..measured.getLineRight(line)) {
                        return@awaitEachGesture
                    }
                    val offset = measured.getOffsetForPosition(down.position)
                    val id = text.getStringAnnotations(AUTHOR_TAG, offset, offset).firstOrNull()?.item
                    val ref = linked.firstOrNull { it.id == id } ?: return@awaitEachGesture
                    down.consume()
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    up.consume()
                    openAuthor(ref)
                }
            }
            .semantics {
                customActions = linked.map { ref ->
                    CustomAccessibilityAction("$openLabel ${ref.name}") {
                        openAuthor(ref)
                        true
                    }
                }
            },
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

private const val AUTHOR_TAG = "author"
