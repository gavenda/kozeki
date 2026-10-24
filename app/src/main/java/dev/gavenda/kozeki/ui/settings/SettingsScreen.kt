package dev.gavenda.kozeki.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.BrokenExtension
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAccount
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.tracking.anilist.AniListAccount
import dev.gavenda.kozeki.data.tracking.anilist.AniListAuth
import dev.gavenda.kozeki.data.settings.ThemeMode
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.data.update.AppUpdateState
import dev.gavenda.kozeki.data.update.UpdateCheck
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.Portrait
import dev.gavenda.kozeki.ui.components.SpeechBubble
import dev.gavenda.kozeki.ui.components.narrowIconButton
import dev.gavenda.kozeki.ui.components.toggleButtonColors
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.settingsViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.roundToInt

private const val MIN_DAILY_GOAL = 5
private const val MAX_DAILY_GOAL = 180
private const val DAILY_GOAL_STEP = 5
private const val DEFAULT_YEARLY_GOAL = 12
private val AVATAR_SIZE = 36.dp
private val ABOUT_PORTRAIT_WIDTH = 96.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel { settingsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.OpenBrowser -> try {
                    CustomTabsIntent.Builder().build().launchUrl(context, event.uri)
                } catch (_: ActivityNotFoundException) {
                    // No browser to sign in with; treat it like closing the browser straight away.
                    viewModel.onResumed()
                }
                is SettingsEvent.Download -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, event.uri))
                } catch (_: ActivityNotFoundException) {
                    // Nothing on the device opens links.
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
        } catch (_: Exception) {
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

    // The system's pickers stand in for a path: the folder backups go to, and a backup to restore.
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::pickBackupFolder)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickBackupFile)
    }
    val localBackupActions = remember(viewModel, folderPicker, filePicker) {
        LocalBackupActions(
            pickFolder = {
                val current = viewModel.uiState.value.localBackup.folder?.let(Uri::parse)
                try {
                    folderPicker.launch(current)
                } catch (_: ActivityNotFoundException) {
                    // A device without a documents UI has no way to pick one.
                }
            },
            setInterval = viewModel::setLocalBackupInterval,
            backUp = viewModel::backUpLocally,
            // The provider says what type a backup is, so any file can be picked.
            pickFile = {
                try {
                    filePicker.launch(arrayOf("*/*"))
                } catch (_: ActivityNotFoundException) {
                }
            },
            restore = viewModel::restoreLocalBackup,
            dismiss = viewModel::dismissLocalBackupNotice,
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
        localBackupActions = localBackupActions,
        onRefreshExtensions = viewModel::refreshExtensions,
        onSetComicLanguageShown = viewModel::setComicLanguageShown,
        onSetUpdateInterval = viewModel::setUpdateInterval,
        onAniListSignIn = viewModel::signInToAniList,
        onAniListSignOut = viewModel::signOutOfAniList,
        onDismissAniListError = viewModel::dismissAniListError,
        onCheckForAppUpdate = viewModel::checkForAppUpdate,
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
    localBackupActions: LocalBackupActions = LocalBackupActions(),
    onRefreshExtensions: () -> Unit = {},
    onSetComicLanguageShown: (packageName: String, language: String, shown: Boolean) -> Unit = { _, _, _ -> },
    onSetUpdateInterval: (Int) -> Unit = {},
    onAniListSignIn: () -> Unit = {},
    onAniListSignOut: () -> Unit = {},
    onDismissAniListError: () -> Unit = {},
    onCheckForAppUpdate: () -> Unit = {},
) {
    // The bar takes the surface container once the page scrolls under it, as the library's does.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
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
                        ComicSourcesSetting(
                            extensions = state.extensions,
                            hiddenLanguages = state.hiddenComicLanguages,
                            onRefresh = onRefreshExtensions,
                            onSetLanguageShown = onSetComicLanguageShown,
                        )
                        UpdateIntervalSetting(state.updateIntervalHours, onSetUpdateInterval)
                    }
                }
                item(key = "tracking") {
                    Section(title = stringResource(R.string.settings_tracking_title)) {
                        AniListSetting(state, onAniListSignIn, onAniListSignOut, onDismissAniListError)
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
                    SplitSection(title = stringResource(R.string.settings_backup_title)) {
                        SettingSubgroup(SubgroupPosition.FIRST) {
                            BackupSetting(state.backup, backupActions)
                        }
                        SettingSubgroup(SubgroupPosition.LAST) {
                            LocalBackupSetting(state.localBackup, localBackupActions)
                        }
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
                            FilledTonalButton(onClick = onClearCache, enabled = state.cachedLookups > 0) {
                                Text(stringResource(R.string.settings_cache_clear))
                            }
                        }
                    }
                }
                item(key = "about") { About(state.versionName, state.birthday, state.appUpdate, onCheckForAppUpdate) }
            }
        }
    }
}

/** Settings of a [SplitSection] that belong together, as a card of their own. */
@Composable
internal fun SettingSubgroup(
    position: SubgroupPosition = SubgroupPosition.ALONE,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Round where the section's edge is, nearly square where it meets the next card, as the
    // buttons of a connected group meet.
    val top = if (position == SubgroupPosition.FIRST || position == SubgroupPosition.ALONE) OuterCorner else InnerCorner
    val bottom = if (position == SubgroupPosition.LAST || position == SubgroupPosition.ALONE) OuterCorner else InnerCorner
    Card(
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp), content = content)
    }
}

/** A titled group of settings, drawn as one card. */
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** Where a [SettingSubgroup] stands among the cards of its [SplitSection]. */
internal enum class SubgroupPosition { ALONE, FIRST, MIDDLE, LAST }

private val OuterCorner = 28.dp
private val InnerCorner = 4.dp

/** A titled group of settings split into [SettingSubgroup] cards, joined like a connected button group. */
@Composable
internal fun SplitSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** One setting inside a [Section]: an icon, what it is, its current value, and an optional control. */
@Composable
internal fun SettingRow(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
) {
    SettingRow(
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        trailing = trailing,
    )
}

/**
 * A [SettingRow] whose [leading] slot is not a plain icon. It is laid out in the icon's 24dp box,
 * and without one there is no box, so the text starts at the edge.
 */
@Composable
internal fun SettingRow(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    // Less inside a container of its own that is already inset, so the text lines up with the rest.
    horizontalPadding: Dp = 20.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Ui saying what she is here for, over the app's name and version, then who she is and who drew her.
 * On her birthday she says so instead. It ends the list and is no setting, so it has no card.
 *
 * Tapping her checks for a newer version, or fetches one she has found. A second sentence in her
 * bubble says how the check went, and her face goes with it. Being up to date adds no sentence
 * until the version under her is tapped, and only then is she happy.
 */
@Composable
private fun About(
    versionName: String,
    birthday: Boolean,
    update: AppUpdateState = AppUpdateState.Idle,
    onTapUi: () -> Unit = {},
) {
    val result = (update as? AppUpdateState.Checked)?.result
    val upToDate = result == UpdateCheck.UpToDate
    // The version under her has been tapped, so she says it is the latest, and is happy about it.
    var showLatest by remember { mutableStateOf(false) }
    LaunchedEffect(upToDate) { if (!upToDate) showLatest = false }
    val expression = when {
        update == AppUpdateState.Checking -> Expression.STUDIOUS
        result is UpdateCheck.Available -> Expression.CURIOUS
        upToDate && showLatest -> Expression.HAPPY
        result == UpdateCheck.Offline || result == UpdateCheck.RateLimited -> Expression.WORRIED
        result is UpdateCheck.Failed -> Expression.DISMAYED
        else -> Expression.CONTENT
    }
    val line = stringResource(if (birthday) R.string.settings_about_birthday else R.string.settings_about_credits)
    val updateLine = when {
        update == AppUpdateState.Checking -> stringResource(R.string.settings_about_update_checking)
        else -> when (result) {
            null -> null
            is UpdateCheck.Available -> stringResource(R.string.settings_about_update_available, result.version)
            UpdateCheck.UpToDate -> if (showLatest) stringResource(R.string.settings_about_update_latest) else null
            UpdateCheck.Offline -> stringResource(R.string.settings_about_update_offline)
            UpdateCheck.RateLimited -> stringResource(R.string.settings_about_update_rate_limited)
            is UpdateCheck.Refused -> stringResource(R.string.settings_about_update_refused, result.code)
            UpdateCheck.Unreadable -> stringResource(R.string.settings_about_update_unreadable)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // No ripple: a box of it over her and her halo looks pasted on, and her face answers the tap.
            Crossfade(
                targetState = expression,
                modifier = Modifier
                    .width(ABOUT_PORTRAIT_WIDTH)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = update != AppUpdateState.Checking,
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (result is UpdateCheck.Available) R.string.settings_about_update_download else R.string.settings_about_update_check,
                        ),
                        onClick = onTapUi,
                    ),
                label = "expression",
            ) { face -> Portrait(face, Modifier.fillMaxWidth()) }
            // As tall as the plate she is on, which is as tall as it is wide, so the bubble's tail
            // is level with her face and not with the halo over it.
            Box(
                modifier = Modifier.weight(1f, fill = false).heightIn(min = ABOUT_PORTRAIT_WIDTH),
                contentAlignment = Alignment.CenterStart,
            ) {
                SpeechBubble(if (updateLine != null) "$line $updateLine" else line)
            }
        }
        Text(
            text = stringResource(R.string.settings_about_version, versionName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(
                interactionSource = null,
                indication = null,
                enabled = upToDate,
                role = Role.Button,
                onClick = { showLatest = !showLatest },
            ),
        )
        // Two plain lines in the third person, as they are about her and not from her. The credit
        // is the quieter of the two.
        val name = stringResource(R.string.character_name)
        val keeper = stringResource(R.string.settings_about_keeper, name)
        val nameColor = MaterialTheme.colorScheme.primary
        Text(
            // Her name alone is picked out, wherever a translation puts it in the line.
            text = remember(keeper, name, nameColor) {
                buildAnnotatedString {
                    append(keeper)
                    val start = keeper.indexOf(name)
                    if (start >= 0) addStyle(SpanStyle(color = nameColor), start, start + name.length)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.settings_about_illustrator),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

/** The AniList account comics are tracked on. Tapping it signs in, or offers to sign out once signed in. */
@Composable
private fun AniListSetting(
    state: SettingsUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
) {
    val account = state.aniList
    val working = state.aniListSignIn in AniListAuth.WORKING
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    SettingRow(
        leading = { AccountAvatar((account as? AniListAccount.SignedIn)?.avatarUrl) },
        title = stringResource(R.string.source_anilist),
        subtitle = when (account) {
            AniListAccount.SignedOut -> stringResource(R.string.settings_anilist_signed_out)
            is AniListAccount.SignedIn -> stringResource(R.string.settings_hardcover_signed_in_as, account.username)
        },
        modifier = Modifier.clickable(
            enabled = !working,
            onClickLabel = stringResource(
                when (account) {
                    AniListAccount.SignedOut -> R.string.settings_hardcover_sign_in
                    is AniListAccount.SignedIn -> R.string.settings_hardcover_sign_out
                },
            ),
            role = Role.Button,
        ) {
            when (account) {
                AniListAccount.SignedOut -> onSignIn()
                is AniListAccount.SignedIn -> confirmSignOut = true
            }
        },
        trailing = if (working) {
            { HaloLoadingIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
    )

    val error = when (state.aniListSignIn) {
        AniListAuth.Status.DENIED -> stringResource(R.string.settings_anilist_denied)
        AniListAuth.Status.OFFLINE -> stringResource(R.string.lookup_error_offline)
        AniListAuth.Status.REJECTED -> stringResource(R.string.settings_anilist_rejected)
        AniListAuth.Status.IDLE,
        AniListAuth.Status.AWAITING_REDIRECT,
        AniListAuth.Status.EXCHANGING,
        -> null
    }
    if (error != null) {
        Column(Modifier.padding(start = 60.dp, end = 20.dp, bottom = 8.dp)) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.action_ok))
            }
        }
    }

    if (confirmSignOut && account is AniListAccount.SignedIn) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(painterResource(R.drawable.logout_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            iconContentColor = MaterialTheme.colorScheme.error,
            title = { Text(stringResource(R.string.settings_anilist_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_anilist_sign_out_message)) },
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
            { HaloLoadingIndicator(Modifier.size(24.dp)) }
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

private val UPDATE_INTERVALS = listOf(0, 6, 12, 24)

/** How often the comics being followed are asked for new chapters, which the library then shows. */
@Composable
private fun UpdateIntervalSetting(hours: Int, onSet: (Int) -> Unit) {
    SettingRow(
        icon = painterResource(R.drawable.refresh_48px),
        title = stringResource(R.string.settings_updates_title),
        subtitle = stringResource(R.string.settings_updates_description),
    )
    // A period kept from elsewhere that is none of the choices shows as the nearest one.
    val selected = UPDATE_INTERVALS.indexOf(hours).takeIf { it >= 0 }
        ?: UPDATE_INTERVALS.indices.minBy { kotlin.math.abs(UPDATE_INTERVALS[it] - hours) }
    ConnectedButtonGroup(
        options = UPDATE_INTERVALS.map {
            if (it == 0) stringResource(R.string.settings_updates_off) else stringResource(R.string.settings_updates_hours, it)
        },
        selectedIndex = selected,
        onSelect = { onSet(UPDATE_INTERVALS[it]) },
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        optionColors = UPDATE_INTERVALS.map { OptionColor.VIOLET.toggleButtonColors() },
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
        icons = modes.map { it.icon },
        // One of the theme's three colours each. An unselected button is in its colour's container,
        // since the default is the card's own colour and would hide the group's shape.
        optionColors = modes.map { it.color.toggleButtonColors() },
        iconOnly = setOf(modes.indexOf(ThemeMode.SYSTEM)),
    )
}

private val ThemeMode.color: OptionColor
    get() = when (this) {
        ThemeMode.SYSTEM -> OptionColor.VIOLET
        ThemeMode.LIGHT -> OptionColor.GOLD
        ThemeMode.DARK -> OptionColor.LEATHER
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
                modifier = Modifier.narrowIconButton(),
            ) {
                Icon(painterResource(R.drawable.remove_48px), contentDescription = stringResource(R.string.settings_goal_decrease), modifier = Modifier.size(24.dp))
            }
            FilledTonalIconButton(onClick = { onSet(books?.plus(1) ?: DEFAULT_YEARLY_GOAL) }, modifier = Modifier.narrowIconButton()) {
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
                extensions = listOf(
                    BrokenExtension(
                        name = "MangaDex",
                        packageName = "eu.kanade.tachiyomi.extension.all.mangadex",
                        versionName = "1.4.190",
                        versionCode = 190,
                        reason = BrokenExtension.Reason.UnsupportedLib(1.4),
                    ),
                    BrokenExtension(
                        name = "Some Source",
                        packageName = "eu.kanade.tachiyomi.extension.en.some",
                        versionName = "1.6.3",
                        versionCode = 3,
                        reason = BrokenExtension.Reason.Failed("ClassNotFoundException: ..."),
                    ),
                ),
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

@PreviewLightDark
@Composable
private fun AboutPreview() {
    AppTheme {
        Surface {
            About(versionName = "2.0", birthday = false)
        }
    }
}

/** Her face and second line through a check: checking, a newer version found, and offline. */
@PreviewLightDark
@Composable
private fun AboutUpdatePreview() {
    AppTheme {
        Surface {
            Column {
                About(versionName = "2.6.0", birthday = false, update = AppUpdateState.Checking)
                About(versionName = "2.6.0", birthday = false, update = AppUpdateState.Checked(UpdateCheck.Available("2.7.0", "")))
                About(versionName = "2.6.0", birthday = false, update = AppUpdateState.Checked(UpdateCheck.Offline))
            }
        }
    }
}

/** The speech bubble on 23 April. */
@PreviewLightDark
@Composable
private fun AboutBirthdayPreview() {
    AppTheme {
        Surface {
            About(versionName = "2.0", birthday = true)
        }
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
