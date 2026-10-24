package dev.gavenda.kozeki.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R

/**
 * Text cut to [collapsedLines] until tapped, with a chevron centred under it that turns over as it
 * opens. The chevron shows only when there is more to see, or the text is already open.
 */
@Composable
fun ExpandableText(
    text: String,
    collapsedLines: Int,
    modifier: Modifier = Modifier,
    key: Any? = null,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var overflows by rememberSaveable(key) { mutableStateOf(false) }
    val expandable = expanded || overflows
    val turn by animateFloatAsState(if (expanded) 0f else 180f, label = "chevron")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = expandable,
                onClickLabel = stringResource(if (expanded) R.string.action_show_less else R.string.action_show_more),
            ) { expanded = !expanded },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        )
        if (expandable) {
            Icon(
                painterResource(R.drawable.keyboard_arrow_up_48px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(turn),
            )
        }
    }
}
