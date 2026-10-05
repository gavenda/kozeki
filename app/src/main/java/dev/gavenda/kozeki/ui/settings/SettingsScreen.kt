package dev.gavenda.kozeki.ui.settings

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAccount
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

private const val MIN_DAILY_GOAL = 5
private const val MAX_DAILY_GOAL = 180
private const val DAILY_GOAL_STEP = 5
private const val DEFAULT_YEARLY_GOAL = 12
private val AVATAR_SIZE = 36.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.OpenBrowser -> try {
                    CustomTabsIntent.Builder().build().launchUrl(context, event.uri)
                } catch (e: ActivityNotFoundException) {
                    // No browser to sign in with; treat it like closing the browser straight away.
                    viewModel.onResumed()
                }
            }
        }
    }
    // Coming back from the browser without a redirect means the sign-in was abandoned.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    SettingsContent(
        state = state,
        onBack = onBack,
        onSelectSource = viewModel::selectSource,
        onSignIn = viewModel::signIn,
        onSignOut = viewModel::signOut,
        onDismissSignInError = viewModel::dismissSignInError,
        onSetDynamicColor = viewModel::setDynamicColor,
        onSetDailyGoal = viewModel::setDailyGoal,
        onSetYearlyGoal = viewModel::setYearlyGoal,
        onClearCache = viewModel::clearCache,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelectSource: (MetadataSource) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissSignInError: () -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDailyGoal: (Int) -> Unit,
    onSetYearlyGoal: (Int) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "source") {
                    Section(title = stringResource(R.string.settings_source_title)) {
                        SettingRow(
                            icon = Icons.Rounded.TravelExplore,
                            title = stringResource(R.string.settings_source_provider),
                            subtitle = stringResource(R.string.settings_source_description),
                        )
                        SegmentedChoice(
                            options = listOf(
                                MetadataSource.GOOGLE_BOOKS to stringResource(R.string.source_google_books),
                                MetadataSource.HARDCOVER to stringResource(R.string.source_hardcover),
                            ),
                            selected = state.source,
                            onSelect = onSelectSource,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        SettingRow(
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            title = stringResource(R.string.source_google_books),
                            subtitle = if (state.googleBooksConfigured) {
                                pluralStringResource(
                                    R.plurals.settings_google_requests,
                                    state.googleRequestsToday,
                                    state.googleRequestsToday,
                                )
                            } else {
                                stringResource(R.string.settings_google_missing_key)
                            },
                        )
                        HardcoverSetting(state, onSignIn, onSignOut, onDismissSignInError)
                    }
                }
                item(key = "appearance") {
                    Section(title = stringResource(R.string.settings_appearance_title)) {
                        SettingRow(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_description),
                            modifier = Modifier.toggleable(
                                value = state.dynamicColor,
                                role = Role.Switch,
                                onValueChange = onSetDynamicColor,
                            ),
                        ) {
                            Switch(checked = state.dynamicColor, onCheckedChange = null)
                        }
                    }
                }
                item(key = "goals") {
                    Section(title = stringResource(R.string.settings_goals_title)) {
                        DailyGoalSetting(state.dailyGoalMinutes, onSetDailyGoal)
                        YearlyGoalSetting(state.year, state.yearlyGoalBooks, onSetYearlyGoal)
                    }
                }
                item(key = "cache") {
                    Section(title = stringResource(R.string.settings_cache_title)) {
                        SettingRow(
                            icon = Icons.Rounded.Storage,
                            title = pluralStringResource(
                                R.plurals.settings_cache_entries,
                                state.cachedLookups,
                                state.cachedLookups,
                            ),
                            subtitle = stringResource(R.string.settings_cache_description),
                        ) {
                            TextButton(onClick = onClearCache, enabled = state.cachedLookups > 0) {
                                Text(stringResource(R.string.settings_cache_clear))
                            }
                        }
                    }
                }
                item(key = "about") {
                    Section(title = stringResource(R.string.settings_about_title)) {
                        SettingRow(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.settings_about_version, state.versionName),
                            subtitle = stringResource(R.string.settings_about_credits),
                        )
                    }
                }
            }
        }
    }
}

/** A titled group of settings, drawn as one card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** One setting inside a [Section]: an icon, what it is, its current value, and an optional control. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SettingRow(
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        trailing = trailing,
    )
}

/** A [SettingRow] whose [leading] slot is not a plain icon. It is laid out in the icon's 24dp box. */
@Composable
private fun SettingRow(
    leading: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** A pill track with one filled segment, for picking between a few short options. */
@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The Hardcover account. Tapping it signs in, or offers to sign out once signed in. */
@Composable
private fun HardcoverSetting(
    state: SettingsUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
) {
    val account = state.hardcover
    val working = state.signIn in HardcoverSignIn.WORKING
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    SettingRow(
        leading = { HardcoverAvatar((account as? HardcoverAccount.SignedIn)?.avatarUrl) },
        title = stringResource(R.string.source_hardcover),
        subtitle = when (account) {
            HardcoverAccount.SignedOut -> stringResource(R.string.settings_hardcover_signed_out)
            is HardcoverAccount.SignedIn -> account.email
                ?: account.username?.let { stringResource(R.string.settings_hardcover_signed_in_as, it) }
                ?: stringResource(R.string.settings_hardcover_signed_in)
        },
        modifier = Modifier.clickable(
            enabled = !working,
            onClickLabel = stringResource(
                when (account) {
                    HardcoverAccount.SignedOut -> R.string.settings_hardcover_sign_in
                    is HardcoverAccount.SignedIn -> R.string.settings_hardcover_sign_out
                },
            ),
            role = Role.Button,
        ) {
            when (account) {
                HardcoverAccount.SignedOut -> onSignIn()
                is HardcoverAccount.SignedIn -> confirmSignOut = true
            }
        },
        trailing = if (working) {
            { LoadingIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
    )

    val error = when (state.signIn) {
        HardcoverSignIn.Status.DENIED -> stringResource(R.string.settings_hardcover_denied)
        HardcoverSignIn.Status.OFFLINE -> stringResource(R.string.lookup_error_offline)
        HardcoverSignIn.Status.REJECTED ->
            stringResource(R.string.settings_hardcover_rejected, state.hardcoverRedirectUri)
        HardcoverSignIn.Status.IDLE,
        HardcoverSignIn.Status.AWAITING_REDIRECT,
        HardcoverSignIn.Status.EXCHANGING,
        -> null
    }
    if (error != null) {
        // Indented to sit under the row's text rather than its icon.
        Column(Modifier.padding(start = 60.dp, end = 20.dp, bottom = 8.dp)) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.action_ok))
            }
        }
    }

    if (confirmSignOut && account is HardcoverAccount.SignedIn) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_hardcover_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_hardcover_sign_out_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                ) {
                    Text(stringResource(R.string.settings_hardcover_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** The account's picture, or the generic account icon while there is none to show. */
@Composable
private fun HardcoverAvatar(url: String?) {
    val painter = rememberAsyncImagePainter(url)
    val state by painter.state.collectAsStateWithLifecycle()
    if (state !is AsyncImagePainter.State.Success) {
        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Always composed, because the picture only starts loading once it is asked to draw. It is
    // larger than an icon and deliberately overflows the row's icon box so the text stays aligned.
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.requiredSize(AVATAR_SIZE).clip(CircleShape),
    )
}

@Composable
private fun DailyGoalSetting(minutes: Int, onSet: (Int) -> Unit) {
    // The slider moves freely; the setting is only written when the finger lifts.
    var dragged by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    SettingRow(
        icon = Icons.Rounded.Timer,
        title = stringResource(R.string.settings_daily_goal),
        subtitle = stringResource(R.string.settings_daily_goal_value, dragged.roundToInt()),
    )
    Slider(
        value = dragged,
        onValueChange = { dragged = (it / DAILY_GOAL_STEP).roundToInt() * DAILY_GOAL_STEP.toFloat() },
        onValueChangeFinished = { onSet(dragged.roundToInt()) },
        valueRange = MIN_DAILY_GOAL.toFloat()..MAX_DAILY_GOAL.toFloat(),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun YearlyGoalSetting(year: Int, books: Int?, onSet: (Int) -> Unit) {
    SettingRow(
        icon = Icons.Rounded.Flag,
        title = stringResource(R.string.settings_yearly_goal, year),
        subtitle = books?.toString() ?: stringResource(R.string.settings_goal_not_set),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = { onSet((books ?: DEFAULT_YEARLY_GOAL) - 1) },
                enabled = (books ?: 0) > 1,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.settings_goal_decrease))
            }
            FilledTonalIconButton(onClick = { onSet(books?.plus(1) ?: DEFAULT_YEARLY_GOAL) }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.settings_goal_increase))
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun SettingsContentPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsUiState(
                source = MetadataSource.GOOGLE_BOOKS,
                googleBooksConfigured = true,
                googleRequestsToday = 14,
                hardcover = HardcoverAccount.SignedIn("enda", email = "enda@example.com"),
                hardcoverRedirectUri = "kozeki://oauth/hardcover",
                dailyGoalMinutes = 20,
                year = 2026,
                yearlyGoalBooks = 24,
                cachedLookups = 128,
                versionName = "1.0",
            ),
            onBack = {},
            onSelectSource = {},
            onSignIn = {},
            onSignOut = {},
            onDismissSignInError = {},
            onSetDynamicColor = {},
            onSetDailyGoal = {},
            onSetYearlyGoal = {},
            onClearCache = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun SettingsSignInFailedPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsUiState(
                source = MetadataSource.HARDCOVER,
                signIn = HardcoverSignIn.Status.REJECTED,
                hardcoverRedirectUri = "kozeki://oauth/hardcover",
                year = 2026,
                versionName = "1.0",
            ),
            onBack = {},
            onSelectSource = {},
            onSignIn = {},
            onSignOut = {},
            onDismissSignInError = {},
            onSetDynamicColor = {},
            onSetDailyGoal = {},
            onSetYearlyGoal = {},
            onClearCache = {},
        )
    }
}
