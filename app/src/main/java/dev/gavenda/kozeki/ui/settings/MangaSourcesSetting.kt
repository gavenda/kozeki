package dev.gavenda.kozeki.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.manga.BrokenExtension
import dev.gavenda.kozeki.data.manga.ContentWarning
import dev.gavenda.kozeki.data.manga.LoadedExtension
import dev.gavenda.kozeki.data.manga.MangaExtension
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import eu.kanade.tachiyomi.source.ConfigurableSource

/**
 * The Mihon extensions Kozeki found on the device, each with the sources it gives or the reason it
 * could not be used. [extensions] is null until they have been looked for.
 */
@Composable
internal fun MangaSourcesSetting(extensions: List<MangaExtension>?, onRefresh: () -> Unit) {
    if (extensions == null) {
        SettingRow(
            leading = { HaloLoadingIndicator(Modifier.size(24.dp)) },
            title = stringResource(R.string.settings_manga_loading),
        )
        return
    }

    // There can be a great many, so the list is only there once asked for.
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        icon = painterResource(R.drawable.auto_stories_48px),
        title = pluralStringResource(R.plurals.settings_manga_extensions, extensions.size, extensions.size),
        subtitle = stringResource(R.string.settings_manga_description),
        modifier = Modifier.clickable(enabled = extensions.isNotEmpty()) { expanded = !expanded },
    ) {
        FilledTonalIconButton(onClick = onRefresh) {
            Icon(
                painterResource(R.drawable.refresh_48px),
                contentDescription = stringResource(R.string.settings_manga_refresh),
                modifier = Modifier.size(24.dp),
            )
        }
    }
    if (extensions.isEmpty()) {
        SettingRow(
            icon = painterResource(R.drawable.auto_stories_off_48px),
            title = stringResource(R.string.settings_manga_none_title),
            subtitle = stringResource(R.string.settings_manga_none_description),
        )
    }
    if (expanded) {
        val context = LocalContext.current
        // An extension with several sources of its own settings (one per language) asks which.
        var choosing by rememberSaveable { mutableStateOf<String?>(null) }
        extensions.forEach { extension ->
            val configurable = (extension as? LoadedExtension)?.sources.orEmpty().filterIsInstance<ConfigurableSource>()
            SettingRow(
                leading = { ExtensionIcon(extension.packageName) },
                title = extension.name,
                subtitle = extension.summary(),
                modifier = Modifier.clickable(enabled = configurable.isNotEmpty()) {
                    if (configurable.size == 1) {
                        context.startActivity(SourcePreferencesActivity.intent(context, configurable.first().id))
                    } else {
                        choosing = if (choosing == extension.packageName) null else extension.packageName
                    }
                },
            )
            if (choosing == extension.packageName) {
                configurable.forEach { source ->
                    SettingRow(
                        icon = painterResource(R.drawable.auto_stories_48px),
                        title = source.name,
                        subtitle = source.lang,
                        modifier = Modifier.clickable {
                            context.startActivity(SourcePreferencesActivity.intent(context, source.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaExtension.summary(): String = when (this) {
    is LoadedExtension -> listOfNotNull(
        versionName,
        stringResource(R.string.settings_manga_nsfw).takeIf { contentWarning == ContentWarning.NSFW },
    ).joinToString(" · ")

    is BrokenExtension -> when (val reason = reason) {
        is BrokenExtension.Reason.UnsupportedLib ->
            if (reason.libVersion != null) {
                stringResource(R.string.settings_manga_broken_lib, reason.libVersion.toString())
            } else {
                stringResource(R.string.settings_manga_broken_lib_unknown)
            }

        BrokenExtension.Reason.Malformed -> stringResource(R.string.settings_manga_broken_malformed)
        is BrokenExtension.Reason.Failed -> stringResource(R.string.settings_manga_broken_failed, reason.message)
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
