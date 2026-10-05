package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * Five stars in half-star steps. Read-only when [onRatingChange] is null.
 *
 * Tapping a star sets it; tapping the star that is already the rating drops it by half, which is
 * how half stars are reached without tiny touch targets.
 */
@Composable
fun RatingBar(
    rating: Float?,
    modifier: Modifier = Modifier,
    starSize: Dp = 32.dp,
    onRatingChange: ((Float) -> Unit)? = null,
) {
    val value = rating ?: 0f
    val description = if (rating == null) {
        stringResource(R.string.rating_none)
    } else {
        stringResource(R.string.rating_value, rating)
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(if (onRatingChange != null) 4.dp else 0.dp),
    ) {
        for (star in 1..5) {
            val icon = when {
                value >= star -> Icons.Rounded.Star
                value >= star - 0.5f -> Icons.AutoMirrored.Rounded.StarHalf
                else -> Icons.Rounded.StarBorder
            }
            val tint = if (value >= star - 0.5f) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            val label = pluralStringResource(R.plurals.rating_stars, star, star)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (onRatingChange == null) {
                            Modifier
                        } else {
                            Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = label) {
                                    onRatingChange(if (value == star.toFloat()) star - 0.5f else star.toFloat())
                                }
                        },
                    ),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun RatingBarPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingBar(rating = 3.5f, onRatingChange = {})
                RatingBar(rating = null, onRatingChange = {})
                RatingBar(rating = 4f, starSize = 12.dp)
            }
        }
    }
}
