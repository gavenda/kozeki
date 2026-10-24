package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R

/** Settings, at the end of the top bar of each main screen. Leather, as the way out of the screen. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = OptionColor.LEATHER.appBarIconButtonColors(),
        modifier = modifier.narrowIconButton(),
    ) {
        Icon(
            painterResource(R.drawable.settings_fill_48px),
            contentDescription = stringResource(R.string.settings_title),
            modifier = Modifier.size(24.dp),
        )
    }
}
