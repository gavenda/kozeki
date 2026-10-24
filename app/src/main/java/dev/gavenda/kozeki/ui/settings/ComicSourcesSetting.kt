package dev.gavenda.kozeki.ui.settings

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.BrokenExtension
import dev.gavenda.kozeki.data.comic.ContentWarning
import dev.gavenda.kozeki.data.comic.LoadedExtension
import dev.gavenda.kozeki.data.comic.ComicExtension
import dev.gavenda.kozeki.data.settings.SettingsRepository.Companion.comicLanguageKey
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.narrowIconButton
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.iconButtonColors
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import java.util.Locale

/**
 * The Mihon extensions Kozeki found on the device, each with the sources it gives or the reason it
 * could not be used. [extensions] is null until they have been looked for. An extension in several
 * languages opens on a tap to a switch for each, and [hiddenLanguages] are those switched off.
 */
@Composable
internal fun ComicSourcesSetting(
    extensions: List<ComicExtension>?,
    hiddenLanguages: Set<String>,
    onRefresh: () -> Unit,
    onSetLanguageShown: (packageName: String, language: String, shown: Boolean) -> Unit,
) {
    if (extensions == null) {
        SettingRow(
            leading = { HaloLoadingIndicator(Modifier.size(24.dp)) },
            title = stringResource(R.string.settings_comic_loading),
        )
        return
    }

    // There can be a great many, so the list is only there once asked for.
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        icon = painterResource(R.drawable.auto_stories_48px),
        title = pluralStringResource(R.plurals.settings_comic_extensions, extensions.size, extensions.size),
        subtitle = stringResource(R.string.settings_comic_description),
        modifier = Modifier.clickable(enabled = extensions.isNotEmpty()) { expanded = !expanded },
    ) {
        FilledTonalIconButton(onClick = onRefresh, modifier = Modifier.narrowIconButton()) {
            Icon(
                painterResource(R.drawable.refresh_48px),
                contentDescription = stringResource(R.string.settings_comic_refresh),
                modifier = Modifier.size(24.dp),
            )
        }
    }
    if (extensions.isEmpty()) {
        SettingRow(
            icon = painterResource(R.drawable.auto_stories_off_48px),
            title = stringResource(R.string.settings_comic_none_title),
            subtitle = stringResource(R.string.settings_comic_none_description),
        )
    }
    if (expanded) {
        extensions.forEach { extension ->
            key(extension.packageName) {
                val languages = (extension as? LoadedExtension)?.sources.orEmpty().groupBy { it.lang }
                if (languages.size > 1) {
                    MultiLanguageExtension(extension, languages, hiddenLanguages, onSetLanguageShown)
                } else {
                    SingleLanguageExtension(extension)
                }
            }
        }
    }
}

/** An extension in one language: a tap opens its settings, when it has any. */
@Composable
private fun SingleLanguageExtension(extension: ComicExtension) {
    val context = LocalContext.current
    val configurable = (extension as? LoadedExtension)?.sources.orEmpty().filterIsInstance<ConfigurableSource>()
    // An extension with several sources of its own settings asks which.
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        leading = { ExtensionIcon(extension.packageName) },
        title = extension.name,
        subtitle = extension.summary(),
        modifier = Modifier.clickable(enabled = configurable.isNotEmpty()) {
            if (configurable.size == 1) {
                context.startActivity(SourcePreferencesActivity.intent(context, configurable.first().id))
            } else {
                choosing = !choosing
            }
        },
    )
    if (choosing) {
        configurable.forEach { source ->
            SettingRow(
                icon = painterResource(R.drawable.auto_stories_48px),
                title = source.name,
                subtitle = languageName(source.lang),
                modifier = Modifier.clickable {
                    context.startActivity(SourcePreferencesActivity.intent(context, source.id))
                },
            )
        }
    }
}

/**
 * An extension in several languages, with a chevron to open it. Open, it stands on the highest
 * surface container with a switch for each language, the last one shown staying on.
 */
@Composable
private fun MultiLanguageExtension(
    extension: ComicExtension,
    languages: Map<String, List<CatalogueSource>>,
    hiddenLanguages: Set<String>,
    onSetLanguageShown: (packageName: String, language: String, shown: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val container by animateColorAsState(
        if (open) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        label = "extension container",
    )
    // On a surface container the text keeps the colours it has on the page.
    val content = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val turn by animateFloatAsState(if (open) 0f else 180f, label = "extension arrow")
    val shown = languages.keys.filter { comicLanguageKey(extension.packageName, it) !in hiddenLanguages }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Column {
            SettingRow(
                leading = { ExtensionIcon(extension.packageName) },
                title = extension.name,
                subtitle = listOf(
                    extension.summary(),
                    pluralStringResource(R.plurals.settings_comic_languages_shown, languages.size, shown.size, languages.size),
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                subtitleColor = subtitleColor,
                horizontalPadding = InsetRowPadding,
                modifier = Modifier.clickable { open = !open },
            ) {
                // Up while the languages show, down while they are folded away.
                FilledTonalIconButton(
                    onClick = { open = !open },
                    shapes = IconButtonDefaults.shapes(),
                    colors = OptionColor.LEATHER.iconButtonColors(),
                    modifier = Modifier.narrowIconButton(),
                ) {
                    Icon(
                        painterResource(R.drawable.keyboard_arrow_up_48px),
                        contentDescription = stringResource(
                            if (open) R.string.settings_comic_languages_hide else R.string.settings_comic_languages_show,
                        ),
                        modifier = Modifier.size(24.dp).rotate(turn),
                    )
                }
            }
            AnimatedVisibility(open) {
                Column {
                    languages.forEach { (language, sources) ->
                        val isShown = language in shown
                        // The last language shown stays on: with none, the extension would give nothing.
                        val canChange = !isShown || shown.size > 1
                        val configurable = sources.filterIsInstance<ConfigurableSource>().firstOrNull()
                        SettingRow(
                            leading = {},
                            title = languageName(language),
                            subtitle = sources.map { it.name }.distinct()
                                .takeIf { it != listOf(extension.name) }?.joinToString(", "),
                            subtitleColor = subtitleColor,
                            horizontalPadding = InsetRowPadding,
                            modifier = Modifier.clickable(enabled = canChange) {
                                onSetLanguageShown(extension.packageName, language, !isShown)
                            },
                        ) {
                            if (configurable != null) {
                                IconButton(onClick = {
                                    context.startActivity(SourcePreferencesActivity.intent(context, configurable.id))
                                }) {
                                    Icon(
                                        painterResource(R.drawable.settings_fill_48px),
                                        contentDescription = stringResource(R.string.settings_comic_source_settings),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Switch(
                                checked = isShown,
                                onCheckedChange = { onSetLanguageShown(extension.packageName, language, it) },
                                enabled = canChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A row's side padding on the open extension's container, which is itself inset by 8dp. */
private val InsetRowPadding = 12.dp

/** A Mihon language tag ("en", "pt-BR", "all") as a name to read, in the device's language. */
@Composable
private fun languageName(language: String): String = when (language) {
    "all" -> stringResource(R.string.settings_comic_language_all)
    "other" -> stringResource(R.string.settings_comic_language_other)
    else -> {
        val locale = Locale.getDefault()
        Locale.forLanguageTag(language).getDisplayName(locale)
            .ifEmpty { language }
            .replaceFirstChar { it.titlecase(locale) }
    }
}

@Composable
private fun ComicExtension.summary(): String = when (this) {
    is LoadedExtension -> listOfNotNull(
        versionName,
        stringResource(R.string.settings_comic_nsfw).takeIf { contentWarning == ContentWarning.NSFW },
    ).joinToString(" · ")

    is BrokenExtension -> when (val reason = reason) {
        is BrokenExtension.Reason.UnsupportedLib ->
            if (reason.libVersion != null) {
                stringResource(R.string.settings_comic_broken_lib, reason.libVersion.toString())
            } else {
                stringResource(R.string.settings_comic_broken_lib_unknown)
            }

        BrokenExtension.Reason.Malformed -> stringResource(R.string.settings_comic_broken_malformed)
        is BrokenExtension.Reason.Failed -> stringResource(R.string.settings_comic_broken_failed, reason.message)
    }
}

/** The icon of the extension's own app, or the app's book when it has none. */
@Composable
private fun ExtensionIcon(packageName: String) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp))
    } else {
        Icon(
            painterResource(R.drawable.auto_stories_48px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
