package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * How near a label may come to the sides of its button. The options share the width equally, so
 * this only shows once a label nearly fills its share, where the 16 dp a button keeps by default
 * would cut it short: five options on a narrow phone.
 */
private val LabelSidePadding = 4.dp

/** How far a neighbour of a pressed button in a [StandardButtonGroup] may shrink. */
private val PressCompressionLimit = 16.dp

/**
 * A single-choice row of connected toggle buttons, the Material 3 Expressive replacement for
 * segmented buttons. Options share the width equally and all take [colors]; a row whose options
 * take a colour each is a [StandardButtonGroup], since a connected group keeps to one colour.
 * [icons], when given, has one drawable per option. An option in [iconOnly] shows its icon alone,
 * is only as wide as that takes, and has its label read out instead.
 */
@Composable
fun ConnectedButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    icons: List<Int>? = null,
    iconOnly: Set<Int> = emptySet(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            OptionButton(
                label = label,
                checked = index == selectedIndex,
                onClick = { onSelect(index) },
                colors = colors,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                icon = icons?.get(index),
                iconOnly = index in iconOnly,
                modifier = if (index in iconOnly) Modifier else Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single-choice Expressive button group of separate toggle buttons, for options that take a
 * colour each from [optionColors]. Options share the width equally; a pressed one grows and its
 * neighbours give way, then all settle back. [icons] and [iconOnly] are as in [ConnectedButtonGroup].
 */
@Composable
fun StandardButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    optionColors: List<ToggleButtonColors>,
    modifier: Modifier = Modifier,
    icons: List<Int>? = null,
    iconOnly: Set<Int> = emptySet(),
) {
    val interactionSources = remember(options.size) { List(options.size) { MutableInteractionSource() } }
    ButtonGroup(
        // The options share the width, so they never overflow.
        overflowIndicator = {},
        modifier = modifier,
    ) {
        options.forEachIndexed { index, label ->
            customItem(
                buttonGroupContent = {
                    OptionButton(
                        label = label,
                        checked = index == selectedIndex,
                        onClick = { onSelect(index) },
                        colors = optionColors[index],
                        shapes = ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size),
                        icon = icons?.get(index),
                        iconOnly = index in iconOnly,
                        interactionSource = interactionSources[index],
                        modifier = (if (index in iconOnly) Modifier else Modifier.weight(1f))
                            .animateWidth(interactionSources[index], compressionLimit = PressCompressionLimit),
                    )
                },
                menuContent = {},
            )
        }
    }
}

/** One option of either group: an icon and its label, or the icon alone with the label read out. */
@Composable
private fun OptionButton(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    colors: ToggleButtonColors,
    shapes: ToggleButtonShapes,
    icon: Int?,
    iconOnly: Boolean,
    modifier: Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val defaultPadding = ToggleButtonDefaults.contentPaddingFor(ToggleButtonDefaults.size)
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = modifier.semantics { role = Role.RadioButton },
        colors = colors,
        shapes = shapes,
        contentPadding = if (iconOnly) {
            defaultPadding
        } else {
            PaddingValues(
                start = LabelSidePadding,
                top = defaultPadding.calculateTopPadding(),
                end = LabelSidePadding,
                bottom = defaultPadding.calculateBottomPadding(),
            )
        },
        interactionSource = interactionSource,
    ) {
        if (icon != null && iconOnly) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
        } else {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One of the theme's three colours, for a [StandardButtonGroup] whose options take a colour each, or
 * a [ConnectedButtonGroup] all in one. An unselected
 * button is in its colour's container and a selected one in the solid colour, so the colours can
 * be told apart in the light theme, where gold and leather are solid browns much alike.
 */
enum class OptionColor { VIOLET, GOLD, LEATHER }

@Composable
fun OptionColor.toggleButtonColors(): ToggleButtonColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        OptionColor.VIOLET -> ToggleButtonDefaults.colors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        )
        OptionColor.GOLD -> ToggleButtonDefaults.colors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            checkedContainerColor = scheme.secondary,
            checkedContentColor = scheme.onSecondary,
        )
        OptionColor.LEATHER -> ToggleButtonDefaults.colors(
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            checkedContainerColor = scheme.tertiary,
            checkedContentColor = scheme.onTertiary,
        )
    }
}

/**
 * The same three colours for a tonal icon button: the colour's container, as its toggle buttons
 * have when not selected.
 */
@Composable
fun OptionColor.iconButtonColors(): IconButtonColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        OptionColor.VIOLET -> IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
        OptionColor.GOLD -> IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        )
        OptionColor.LEATHER -> IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        )
    }
}

/**
 * The same three colours for an icon button in a top app bar: the surface container, with the
 * colour on the icon.
 */
@Composable
fun OptionColor.appBarIconButtonColors(): IconButtonColors {
    val scheme = MaterialTheme.colorScheme
    return appBarIconButtonColors(
        when (this) {
            OptionColor.VIOLET -> scheme.primary
            OptionColor.GOLD -> scheme.secondary
            OptionColor.LEATHER -> scheme.tertiary
        },
    )
}

/** An icon button in a top app bar with no colour of its own: the surface container, the icon as usual. */
@Composable
fun appBarIconButtonColors(contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant): IconButtonColors =
    IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = contentColor,
    )

/** A toggle in a top app bar: the surface container while off, filled violet while on. */
@Composable
fun appBarIconToggleButtonColors(): IconToggleButtonColors {
    val scheme = MaterialTheme.colorScheme
    return IconButtonDefaults.filledTonalIconToggleButtonColors(
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurfaceVariant,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
}

/**
 * A narrow icon button: 32dp wide inside the usual 48dp touch target, which also keeps it clear of
 * whatever sits next to it.
 */
fun Modifier.narrowIconButton(): Modifier = minimumInteractiveComponentSize()
    .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow))

/**
 * A wide icon button: 52dp wide inside the usual 48dp touch target, for the one action a screen
 * wants noticed beside narrow ones.
 */
fun Modifier.wideIconButton(): Modifier = minimumInteractiveComponentSize()
    .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))

@PreviewLightDark
@Composable
private fun ButtonGroupsPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var connected by remember { mutableIntStateOf(1) }
                ConnectedButtonGroup(
                    options = listOf("Off", "Daily", "3 days", "Weekly"),
                    selectedIndex = connected,
                    onSelect = { connected = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OptionColor.VIOLET.toggleButtonColors(),
                )
                var standard by remember { mutableIntStateOf(1) }
                StandardButtonGroup(
                    options = listOf("Day", "Week", "Month", "Year", "All"),
                    selectedIndex = standard,
                    onSelect = { standard = it },
                    optionColors = listOf(OptionColor.VIOLET, OptionColor.GOLD, OptionColor.GOLD, OptionColor.GOLD, OptionColor.LEATHER)
                        .map { it.toggleButtonColors() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
