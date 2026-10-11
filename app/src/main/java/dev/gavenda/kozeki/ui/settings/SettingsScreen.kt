package dev.gavenda.kozeki.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import dev.gavenda.kozeki.data.settings.ThemeMode
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.Portrait
import dev.gavenda.kozeki.ui.components.SpeechBubble
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

private const val MIN_DAILY_GOAL = 5
private const val MAX_DAILY_GOAL = 180
private const val DAILY_GOAL_STEP = 5
private const val DEFAULT_YEARLY_GOAL = 12
private val AVATAR_SIZE = 36.dp
private val ABOUT_PORTRAIT_WIDTH = 96.dp

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

    // Google asks on a screen of its own whether the app may keep its backup on the user's Drive.
    val driveConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDriveConsent(result.data.takeIf { result.resultCode == Activity.RESULT_OK })
    }
    val consent = state.backup.consent
    LaunchedEffect(consent) {
        consent ?: return@LaunchedEffect
        viewModel.onDriveConsentShown()
        try {
            driveConsent.launch(IntentSenderRequest.Builder(consent.intentSender).build())
        } catch (e: Exception) {
            // The screen cannot be opened, which leaves things as if it had been closed unanswered.
            viewModel.onDriveConsent(null)
        }
    }
    val backupActions = remember(viewModel) {
        BackupActions(
            signIn = viewModel::signInToDrive,
            signOut = viewModel::signOutOfDrive,
            backUp = viewModel::backUp,
            fetch = viewModel::fetchBackup,
            restore = viewModel::restoreBackup,
            dismiss = viewModel::dismissBackupNotice,
        )
    }

    SettingsContent(
        state = state,
        onBack = onBack,
        onSignIn = viewModel::signIn,
        onSignOut = viewModel::signOut,
        onDismissSignInError = viewModel::dismissSignInError,
        onSetThemeMode = viewModel::setThemeMode,
        onSetDailyGoal = viewModel::setDailyGoal,
        onSetYearlyGoal = viewModel::setYearlyGoal,
        onClearCache = viewModel::clearCache,
        backupActions = backupActions,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissSignInError: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDailyGoal: (Int) -> Unit,
    onSetYearlyGoal: (Int) -> Unit,
    onClearCache: () -> Unit,
    backupActions: BackupActions,
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
                        HardcoverSetting(state, onSignIn, onSignOut, onDismissSignInError)
                    }
                }
                item(key = "appearance") {
                    Section(title = stringResource(R.string.settings_appearance_title)) {
                        ThemeModeSetting(state.themeMode, onSetThemeMode)
                    }
                }
                item(key = "goals") {
                    Section(title = stringResource(R.string.settings_goals_title)) {
                        DailyGoalSetting(state.dailyGoalMinutes, onSetDailyGoal)
                        YearlyGoalSetting(state.year, state.yearlyGoalBooks, onSetYearlyGoal)
                    }
                }
                item(key = "backup") {
                    Section(title = stringResource(R.string.settings_backup_title)) {
                        BackupSetting(state.backup, backupActions)
                    }
                }
                item(key = "cache") {
                    Section(title = stringResource(R.string.settings_cache_title)) {
                        SettingRow(
                            icon = painterResource(R.drawable.storage_48px),
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
                item(key = "about") { About(state.versionName) }
            }
        }
    }
}

/** A titled group of settings, drawn as one card. */
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
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
internal fun SettingRow(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SettingRow(
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        trailing = trailing,
    )
}

/** A [SettingRow] whose [leading] slot is not a plain icon. It is laid out in the icon's 24dp box. */
@Composable
internal fun SettingRow(
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

/** Ui saying what she is here for, over the app's name and version. It ends the list and is no setting, so it has no card. */
@Composable
private fun About(versionName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Portrait(Expression.CONTENT, Modifier.width(ABOUT_PORTRAIT_WIDTH))
            // As tall as the plate she is on, which is as tall as it is wide, so the bubble's tail
            // is level with her face and not with the halo over it.
            Box(
                modifier = Modifier.weight(1f, fill = false).heightIn(min = ABOUT_PORTRAIT_WIDTH),
                contentAlignment = Alignment.CenterStart,
            ) {
                SpeechBubble(stringResource(R.string.settings_about_credits))
            }
        }
        Text(stringResource(R.string.settings_about_version, versionName), style = MaterialTheme.typography.titleMedium)
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
        leading = { AccountAvatar((account as? HardcoverAccount.SignedIn)?.avatarUrl) },
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
            // With an icon the dialog centres its headline, as Material 3 lays out a dialog that has one.
            icon = { Icon(painterResource(R.drawable.logout_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            iconContentColor = MaterialTheme.colorScheme.error,
            title = { Text(stringResource(R.string.settings_hardcover_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_hardcover_sign_out_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                    shapes = ButtonDefaults.shapes(),
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
internal fun AccountAvatar(url: String?) {
    val painter = rememberAsyncImagePainter(url)
    val state by painter.state.collectAsStateWithLifecycle()
    if (state !is AsyncImagePainter.State.Success) {
        Icon(painterResource(R.drawable.account_circle_fill_48px), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
private fun ThemeModeSetting(mode: ThemeMode, onSet: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    SettingRow(
        icon = painterResource(R.drawable.palette_fill_48px),
        title = stringResource(R.string.settings_display_mode),
        subtitle = stringResource(R.string.settings_display_mode_description),
    )
    ConnectedButtonGroup(
        options = modes.map { stringResource(it.labelRes) },
        selectedIndex = modes.indexOf(mode),
        onSelect = { onSet(modes[it]) },
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        // The default unselected colour is the card's own, which would hide the group's shape.
        colors = FilledTonalToggleButtonDefaults.colors(),
        icons = modes.map { it.icon },
    )
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private val ThemeMode.icon: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.drawable.brightness_auto_fill_48px
        ThemeMode.LIGHT -> R.drawable.light_mode_fill_48px
        ThemeMode.DARK -> R.drawable.dark_mode_fill_48px
    }

@Composable
private fun DailyGoalSetting(minutes: Int, onSet: (Int) -> Unit) {
    // The slider moves freely; the setting is only written when the finger lifts.
    var dragged by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    val sliderState = remember { SliderState(dragged, trackRange = MIN_DAILY_GOAL.toFloat()..MAX_DAILY_GOAL.toFloat()) }
    sliderState.value = dragged
    SettingRow(
        icon = painterResource(R.drawable.timer_fill_48px),
        title = stringResource(R.string.settings_daily_goal),
        subtitle = stringResource(R.string.settings_daily_goal_value, dragged.roundToInt()),
    )
    Slider(
        state = sliderState,
        onValueChange = { dragged = (it / DAILY_GOAL_STEP).roundToInt() * DAILY_GOAL_STEP.toFloat() },
        onValueChangeFinished = { onSet(dragged.roundToInt()) },
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun YearlyGoalSetting(year: Int, books: Int?, onSet: (Int) -> Unit) {
    SettingRow(
        icon = painterResource(R.drawable.flag_fill_48px),
        title = stringResource(R.string.settings_yearly_goal, year),
        subtitle = books?.toString() ?: stringResource(R.string.settings_goal_not_set),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = { onSet((books ?: DEFAULT_YEARLY_GOAL) - 1) },
                enabled = (books ?: 0) > 1,
            ) {
                Icon(painterResource(R.drawable.remove_48px), contentDescription = stringResource(R.string.settings_goal_decrease), modifier = Modifier.size(24.dp))
            }
            FilledTonalIconButton(onClick = { onSet(books?.plus(1) ?: DEFAULT_YEARLY_GOAL) }) {
                Icon(painterResource(R.drawable.add_48px), contentDescription = stringResource(R.string.settings_goal_increase), modifier = Modifier.size(24.dp))
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
                hardcover = HardcoverAccount.SignedIn("enda", email = "enda@example.com"),
                hardcoverRedirectUri = "kozeki://oauth/hardcover",
                dailyGoalMinutes = 20,
                year = 2026,
                yearlyGoalBooks = 24,
                cachedLookups = 128,
                versionName = "1.0",
            ),
            onBack = {},
            onSignIn = {},
            onSignOut = {},
            onDismissSignInError = {},
            onSetThemeMode = {},
            onSetDailyGoal = {},
            onSetYearlyGoal = {},
            onClearCache = {},
            backupActions = BackupActions(),
        )
    }
}

@ScreenPreviews
@Composable
private fun SettingsSignInFailedPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsUiState(
                signIn = HardcoverSignIn.Status.REJECTED,
                hardcoverRedirectUri = "kozeki://oauth/hardcover",
                year = 2026,
                versionName = "1.0",
            ),
            onBack = {},
            onSignIn = {},
            onSignOut = {},
            onDismissSignInError = {},
            onSetThemeMode = {},
            onSetDailyGoal = {},
            onSetYearlyGoal = {},
            onClearCache = {},
            backupActions = BackupActions(),
        )
    }
}
