// Open this theme in the builder at https://next.materialkolor.com/t/AWZewikEMhALQnVybnQgRW1iZXIn

package dev.gavenda.kozeki.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.MaterialKolors
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.DynamicScheme

/** The app's own colours, the same on every device: the wallpaper's are not taken up. */
@Composable
fun AppTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(isDark) { appColorScheme(isDark) }
    MaterialExpressiveTheme(
        colorScheme = animateColorScheme(colorScheme, tween(durationMillis = 300)),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

private fun appColorScheme(isDark: Boolean): ColorScheme {
    val scheme = DynamicScheme(
        seedColor = SeedColor,
        isDark = isDark,
        // In place of the two the seed would give. Each sets the hue; how light it comes out is the style's doing.
        secondary = GoldColor,
        tertiary = LeatherColor,
        style = PaletteStyle.Cmf(),
        contrastLevel = 0.5,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    val colors = MaterialKolors(scheme)
    return ColorScheme(
        primary = colors.primary(),
        onPrimary = colors.onPrimary(),
        primaryContainer = colors.primaryContainer(),
        onPrimaryContainer = colors.onPrimaryContainer(),
        inversePrimary = colors.inversePrimary(),
        secondary = colors.secondary(),
        onSecondary = colors.onSecondary(),
        secondaryContainer = colors.secondaryContainer(),
        onSecondaryContainer = colors.onSecondaryContainer(),
        tertiary = colors.tertiary(),
        onTertiary = colors.onTertiary(),
        tertiaryContainer = colors.tertiaryContainer(),
        onTertiaryContainer = colors.onTertiaryContainer(),
        background = colors.background(),
        onBackground = colors.onBackground(),
        surface = colors.surface(),
        onSurface = colors.onSurface(),
        surfaceVariant = colors.surfaceVariant(),
        onSurfaceVariant = colors.onSurfaceVariant(),
        surfaceTint = colors.surfaceTint(),
        inverseSurface = colors.inverseSurface(),
        inverseOnSurface = colors.inverseOnSurface(),
        error = colors.error(),
        onError = colors.onError(),
        errorContainer = colors.errorContainer(),
        onErrorContainer = colors.onErrorContainer(),
        outline = colors.outline(),
        outlineVariant = colors.outlineVariant(),
        scrim = colors.scrim(),
        surfaceBright = colors.surfaceBright(),
        surfaceDim = colors.surfaceDim(),
        surfaceContainer = colors.surfaceContainer(),
        surfaceContainerHigh = colors.surfaceContainerHigh(),
        surfaceContainerHighest = colors.surfaceContainerHighest(),
        surfaceContainerLow = colors.surfaceContainerLow(),
        surfaceContainerLowest = colors.surfaceContainerLowest(),
        primaryFixed = colors.primaryFixed(),
        primaryFixedDim = colors.primaryFixedDim(),
        onPrimaryFixed = colors.onPrimaryFixed(),
        onPrimaryFixedVariant = colors.onPrimaryFixedVariant(),
        secondaryFixed = colors.secondaryFixed(),
        secondaryFixedDim = colors.secondaryFixedDim(),
        onSecondaryFixed = colors.onSecondaryFixed(),
        onSecondaryFixedVariant = colors.onSecondaryFixedVariant(),
        tertiaryFixed = colors.tertiaryFixed(),
        tertiaryFixedDim = colors.tertiaryFixedDim(),
        onTertiaryFixed = colors.onTertiaryFixed(),
        onTertiaryFixedVariant = colors.onTertiaryFixedVariant(),
    )
}

/** Eases every role to its new colour when the scheme changes, as between light and dark. */
@Composable
private fun animateColorScheme(target: ColorScheme, spec: AnimationSpec<Color>): ColorScheme {
    @Composable
    fun Color.animated(): Color = animateColorAsState(this, spec, label = "colorScheme").value

    return with(target) {
        copy(
            primary = primary.animated(),
            onPrimary = onPrimary.animated(),
            primaryContainer = primaryContainer.animated(),
            onPrimaryContainer = onPrimaryContainer.animated(),
            inversePrimary = inversePrimary.animated(),
            secondary = secondary.animated(),
            onSecondary = onSecondary.animated(),
            secondaryContainer = secondaryContainer.animated(),
            onSecondaryContainer = onSecondaryContainer.animated(),
            tertiary = tertiary.animated(),
            onTertiary = onTertiary.animated(),
            tertiaryContainer = tertiaryContainer.animated(),
            onTertiaryContainer = onTertiaryContainer.animated(),
            background = background.animated(),
            onBackground = onBackground.animated(),
            surface = surface.animated(),
            onSurface = onSurface.animated(),
            surfaceVariant = surfaceVariant.animated(),
            onSurfaceVariant = onSurfaceVariant.animated(),
            surfaceTint = surfaceTint.animated(),
            inverseSurface = inverseSurface.animated(),
            inverseOnSurface = inverseOnSurface.animated(),
            error = error.animated(),
            onError = onError.animated(),
            errorContainer = errorContainer.animated(),
            onErrorContainer = onErrorContainer.animated(),
            outline = outline.animated(),
            outlineVariant = outlineVariant.animated(),
            scrim = scrim.animated(),
            surfaceBright = surfaceBright.animated(),
            surfaceDim = surfaceDim.animated(),
            surfaceContainer = surfaceContainer.animated(),
            surfaceContainerHigh = surfaceContainerHigh.animated(),
            surfaceContainerHighest = surfaceContainerHighest.animated(),
            surfaceContainerLow = surfaceContainerLow.animated(),
            surfaceContainerLowest = surfaceContainerLowest.animated(),
            primaryFixed = primaryFixed.animated(),
            primaryFixedDim = primaryFixedDim.animated(),
            onPrimaryFixed = onPrimaryFixed.animated(),
            onPrimaryFixedVariant = onPrimaryFixedVariant.animated(),
            secondaryFixed = secondaryFixed.animated(),
            secondaryFixedDim = secondaryFixedDim.animated(),
            onSecondaryFixed = onSecondaryFixed.animated(),
            onSecondaryFixedVariant = onSecondaryFixedVariant.animated(),
            tertiaryFixed = tertiaryFixed.animated(),
            tertiaryFixedDim = tertiaryFixedDim.animated(),
            onTertiaryFixed = onTertiaryFixed.animated(),
            onTertiaryFixedVariant = onTertiaryFixedVariant.animated(),
        )
    }
}
