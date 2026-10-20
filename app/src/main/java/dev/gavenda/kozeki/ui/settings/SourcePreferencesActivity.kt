package dev.gavenda.kozeki.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.ComicSources
import dev.gavenda.kozeki.ui.theme.AppTheme
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.preferenceKey
import org.koin.core.context.GlobalContext

/**
 * The preferences a Mihon source declares for itself. The source fills a [PreferenceScreen] with
 * its own preferences and this draws them in the app's own theme. The common kinds are drawn:
 * switches, checkboxes, text, single and multiple choice, and plain rows that only act when tapped.
 */
class SourcePreferencesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, 0L)
        setContent {
            AppTheme {
                val source by produceState<ConfigurableSource?>(null, sourceId) {
                    value = GlobalContext.get().get<ComicSources>().get(sourceId) as? ConfigurableSource
                }
                // Gone when the extension was removed while this was open.
                val loaded = source ?: return@AppTheme
                SourcePreferencesScreen(loaded, onBack = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"

        fun intent(context: Context, sourceId: Long) =
            Intent(context, SourcePreferencesActivity::class.java).putExtra(EXTRA_SOURCE_ID, sourceId)
    }
}

// The PreferenceManager constructor is library-group restricted, but a source can only be given a
// PreferenceScreen made by one, and Mihon builds it the same way.
@SuppressLint("RestrictedApi")
@Composable
private fun SourcePreferencesScreen(source: ConfigurableSource, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val screen = remember(source) {
        val manager = PreferenceManager(context)
        manager.sharedPreferencesName = source.preferenceKey()
        manager.createPreferenceScreen(context).also(source::setupPreferenceScreen)
    }
    // The preferences keep their own values; this only makes the screen read them again after a change.
    var revision by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((source as Source).name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_48px),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val groups = remember(screen) { screen.groups() }
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(groups) { group ->
                    val rows = group.preferences.filter { it.isVisible }
                    if (rows.isEmpty()) return@items
                    Column(Modifier.padding(vertical = 8.dp)) {
                        if (group.title != null) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                            )
                        }
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                rows.forEach { PreferenceRow(it, revision) { revision++ } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A run of preferences drawn as one card, under a category's title if it came from one. */
private class PreferenceGroupRows(val title: String?, val preferences: List<Preference>)

/** The screen's preferences as cards: one per category, and one for the loose ones between them. */
private fun PreferenceGroup.groups(): List<PreferenceGroupRows> {
    val result = mutableListOf<PreferenceGroupRows>()
    var loose = mutableListOf<Preference>()
    fun flushLoose() {
        if (loose.isNotEmpty()) result += PreferenceGroupRows(null, loose)
        loose = mutableListOf()
    }
    for (i in 0 until preferenceCount) {
        when (val preference = getPreference(i)) {
            is PreferenceCategory -> {
                flushLoose()
                result += PreferenceGroupRows(preference.title?.toString(), preference.leaves())
            }
            is PreferenceGroup -> loose += preference.leaves()
            else -> loose += preference
        }
    }
    flushLoose()
    return result
}

private fun PreferenceGroup.leaves(): List<Preference> = (0 until preferenceCount).flatMap { i ->
    when (val preference = getPreference(i)) {
        is PreferenceGroup -> preference.leaves()
        else -> listOf(preference)
    }
}

@Composable
private fun PreferenceRow(
    preference: Preference,
    // Preferences are not observable. A new revision is what makes the row read its value again,
    // and a change redraws every row, as one preference can enable or retitle another.
    @Suppress("UNUSED_PARAMETER") revision: Int,
    onChanged: () -> Unit,
) {
    var dialog by remember(preference) { mutableStateOf(false) }
    // This row's own trigger to read its value again; the revision above is for the other rows.
    var own by remember(preference) { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") own
    val enabled = preference.isEnabled
    val title = preference.title?.toString().orEmpty()
    val summary = preference.summary?.toString()?.takeIf { it.isNotBlank() }

    /** Offers the new value to the source, which can refuse it, and keeps it if it does not. */
    fun <T> change(value: T, set: (T) -> Unit) {
        val accepted = preference.callChangeListener(value)
        if (accepted) {
            set(value)
            own++
            onChanged()
        }
    }

    when (preference) {
        is TwoStatePreference -> {
            // Read here and not in the lambdas below, which are kept as they are unless what they
            // capture changes, and a preference is not something Compose can see change.
            val checked = preference.isChecked
            SettingRow(
                title = title,
                subtitle = summary ?: (if (checked) preference.summaryOn else preference.summaryOff)?.toString(),
                modifier = Modifier.clickable(enabled = enabled) { change(!checked) { preference.isChecked = it } },
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = { change(it) { value -> preference.isChecked = value } },
                    enabled = enabled,
                )
            }
        }

        is ListPreference -> {
            SettingRow(
                    title = title,
                subtitle = summary,
                modifier = Modifier.clickable(enabled = enabled) { dialog = true },
            )
            if (dialog) {
                val entries = preference.entries.orEmpty().map { it.toString() }
                val values = preference.entryValues.orEmpty().map { it.toString() }
                ChoiceDialog(title, onDismiss = { dialog = false }) {
                    entries.forEachIndexed { index, entry ->
                        val value = values.getOrNull(index) ?: return@forEachIndexed
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidthClickable {
                                dialog = false
                                change(value) { preference.value = it }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = preference.value == value, onClick = null)
                            Text(entry, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }

        is MultiSelectListPreference -> {
            SettingRow(
                    title = title,
                subtitle = summary,
                modifier = Modifier.clickable(enabled = enabled) { dialog = true },
            )
            if (dialog) {
                val entries = preference.entries.orEmpty().map { it.toString() }
                val values = preference.entryValues.orEmpty().map { it.toString() }
                var picked by remember { mutableStateOf(preference.values.toSet()) }
                ChoiceDialog(
                    title,
                    onDismiss = { dialog = false },
                    confirm = {
                        dialog = false
                        change(picked) { preference.values = it }
                    },
                ) {
                    entries.forEachIndexed { index, entry ->
                        val value = values.getOrNull(index) ?: return@forEachIndexed
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidthClickable {
                                picked = if (value in picked) picked - value else picked + value
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = value in picked, onCheckedChange = null)
                            Text(entry, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }

        is EditTextPreference -> {
            SettingRow(
                    title = title,
                subtitle = summary ?: preference.text?.takeIf { it.isNotEmpty() },
                modifier = Modifier.clickable(enabled = enabled) { dialog = true },
            )
            if (dialog) {
                var text by remember { mutableStateOf(preference.text.orEmpty()) }
                AlertDialog(
                    onDismissRequest = { dialog = false },
                    title = { Text(preference.dialogTitle?.toString() ?: title) },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            dialog = false
                            change(text) { preference.text = it }
                        }) { Text(stringResource(android.R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = false }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }
        }

        else -> SettingRow(
            title = title,
            subtitle = summary,
            modifier = Modifier.clickable(enabled = enabled && preference.onPreferenceClickListener != null) {
                preference.onPreferenceClickListener?.onPreferenceClick(preference)
                onChanged()
            },
        )
    }
}

private fun Modifier.fillMaxWidthClickable(onClick: () -> Unit): Modifier =
    fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)

@Composable
private fun ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { content() } },
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = confirm) { Text(stringResource(android.R.string.ok)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
