package dev.gavenda.kozeki.ui.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.formatPercent
import dev.gavenda.kozeki.ui.book.NoteDialog
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.readium.navigator.common.InputListener
import org.readium.navigator.common.TapContext
import org.readium.navigator.common.TapEvent
import org.readium.navigator.common.defaultHyperlinkListener
import org.readium.navigator.common.defaultInputListener
import org.readium.navigator.web.fixedlayout.FixedWebGoLocation
import org.readium.navigator.web.fixedlayout.FixedWebRendition
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.ReflowableWebRendition
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

private enum class ReaderSheet { NONE, CONTENTS, APPEARANCE, NOTE }

/** How long the screen stays awake after the last page turn or tap. */
private const val KEEP_AWAKE_MS = 10 * 60_000L

/** The app theme animates between light and dark; wait for it to settle before repainting pages. */
private const val THEME_SETTLE_MS = 600L

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(key = "reader-$bookId") { parametersOf(bookId) },
) {
    val currentColors = readerSystemColors()
    val latestColors by rememberUpdatedState(currentColors)
    var systemColors by remember { mutableStateOf(currentColors) }
    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) {
        delay(THEME_SETTLE_MS)
        systemColors = latestColors
    }

    LaunchedEffect(viewModel) { viewModel.open(currentColors) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        ReaderUiState.Loading -> ReaderLoading()
        is ReaderUiState.Failed -> ReaderFailed(state.reason, onBack)
        is ReaderUiState.Ready -> ReaderReady(state, viewModel, systemColors, onBack)
    }
}

@Composable
private fun readerSystemColors(): ReaderColors {
    val scheme = MaterialTheme.colorScheme
    return ReaderColors(
        background = scheme.surface.toArgb(),
        text = scheme.onSurface.toArgb(),
        isDark = scheme.surface.luminance() < 0.5f,
    )
}

@Composable
private fun ReaderReady(
    state: ReaderUiState.Ready,
    viewModel: ReaderViewModel,
    systemColors: ReaderColors,
    onBack: () -> Unit,
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current

    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(ReaderSheet.NONE) }
    var interactions by remember { mutableIntStateOf(0) }
    val rendition = state.rendition

    ImmersiveMode(hideSystemBars = !chromeVisible)
    KeepScreenOn(interactions)
    RendererGuard(onRendererGone = viewModel::onRendererGone)

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                ReaderEvent.Completed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.reader_completed),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoCompleted()
                }
            }
        }
    }

    // Created once: Readium keeps hold of the listener, so it must read the latest state itself.
    val tapListener = remember {
        object : InputListener {
            override fun onTap(event: TapEvent, context: TapContext) {
                chromeVisible = !chromeVisible
                interactions++
                viewModel.onInteraction()
            }
        }
    }
    val onLocationChanged: (Locator, Boolean) -> Unit = { locator, atEnd ->
        interactions++
        viewModel.onLocationChanged(locator, atEnd)
    }

    val (pageBackground, pageText) = preferences.pageColors(systemColors)

    ReaderScaffold(
        title = state.title,
        progress = progress,
        positionCount = state.positionCount,
        chromeVisible = chromeVisible,
        pageBackground = Color(pageBackground),
        pageText = Color(pageText),
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSeek = { position ->
            viewModel.locatorForPosition(position)?.let { locator -> scope.launch { rendition.goTo(locator) } }
        },
        onOpenContents = { sheet = ReaderSheet.CONTENTS },
        onOpenAppearance = { sheet = ReaderSheet.APPEARANCE },
        onAddNote = { sheet = ReaderSheet.NOTE },
    ) { modifier ->
        // Keyed so that a rebuilt navigator starts from scratch instead of inheriting dead WebViews.
        key(rendition) {
            when (rendition) {
                is Rendition.Reflowable -> {
                    val controller = rendition.state.controller
                    if (controller != null) {
                        LaunchedEffect(controller, preferences, systemColors) {
                            controller.preferences = preferences.toReflowable(systemColors)
                        }
                        LaunchedEffect(controller, state.positionCount) {
                            snapshotFlow { controller.location }.collect { location ->
                                val lastVisible = controller.viewport.positions.endInclusive.value
                                val atEnd = !controller.canMoveForward && lastVisible >= state.positionCount
                                onLocationChanged(location.toLocator(), atEnd)
                            }
                        }
                    }
                    ReflowableWebRendition(
                        state = rendition.state,
                        modifier = modifier,
                        inputListener = defaultInputListener(controller = controller, fallbackListener = tapListener),
                        hyperlinkListener = defaultHyperlinkListener(
                            controller = controller,
                            onExternalLinkActivated = { url, _ -> uriHandler.openUri(url.toString()) },
                        ),
                    )
                }

                is Rendition.Fixed -> {
                    val controller = rendition.state.controller
                    if (controller != null) {
                        LaunchedEffect(controller, state.positionCount) {
                            snapshotFlow { controller.location }.collect { location ->
                                val atEnd = !controller.canMoveForward && location.position.value >= state.positionCount
                                onLocationChanged(location.toLocator(), atEnd)
                            }
                        }
                    }
                    FixedWebRendition(
                        state = rendition.state,
                        modifier = modifier,
                        backgroundColor = Color(pageBackground),
                        inputListener = defaultInputListener(controller = controller, fallbackListener = tapListener),
                        hyperlinkListener = defaultHyperlinkListener(
                            controller = controller,
                            onExternalLinkActivated = { url, _ -> uriHandler.openUri(url.toString()) },
                        ),
                    )
                }
            }
        }
    }

    when (sheet) {
        ReaderSheet.NONE -> Unit

        ReaderSheet.CONTENTS -> ContentsSheet(
            entries = state.toc,
            currentIndex = progress.tocIndex,
            onSelect = { entry ->
                sheet = ReaderSheet.NONE
                chromeVisible = false
                scope.launch { rendition.goTo(entry.href) }
            },
            onDismiss = { sheet = ReaderSheet.NONE },
        )

        ReaderSheet.APPEARANCE -> AppearanceSheet(
            preferences = preferences,
            textSettingsApply = rendition is Rendition.Reflowable,
            onChange = viewModel::setPreferences,
            onDismiss = { sheet = ReaderSheet.NONE },
        )

        ReaderSheet.NOTE -> NoteDialog(
            note = null,
            onSave = { text ->
                viewModel.saveNote(text)
                sheet = ReaderSheet.NONE
            },
            onDelete = null,
            onDismiss = { sheet = ReaderSheet.NONE },
        )
    }
}

private suspend fun Rendition.goTo(locator: Locator) {
    when (this) {
        is Rendition.Reflowable -> state.controller?.goTo(ReflowableWebGoLocation(locator))
        is Rendition.Fixed -> state.controller?.goTo(FixedWebGoLocation(locator))
    }
}

private suspend fun Rendition.goTo(url: Url) {
    when (this) {
        is Rendition.Reflowable -> state.controller?.goTo(url)
        is Rendition.Fixed -> state.controller?.goTo(url)
    }
}

/**
 * The reader's frame: the page, a footer that always shows where you are, and controls that appear
 * on a tap. The page itself is a slot so the frame can be previewed without a book.
 */
@Composable
fun ReaderScaffold(
    title: String,
    progress: ReaderProgress,
    positionCount: Int,
    chromeVisible: Boolean,
    pageBackground: Color,
    pageText: Color,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onOpenContents: () -> Unit,
    onOpenAppearance: () -> Unit,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { page(Modifier.fillMaxSize()) }
            ReaderFooter(progress, positionCount, pageText)
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        progress.chapterTitle?.let { chapter ->
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            ReaderControls(progress, positionCount, onSeek, onOpenContents, onOpenAppearance, onAddNote)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ReaderFooter(progress: ReaderProgress, positionCount: Int, pageText: Color) {
    val color = pageText.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = progress.chapterTitle.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val position = progress.position
        Text(
            text = if (position != null && positionCount > 0) {
                stringResource(R.string.reader_footer_position, position, positionCount, formatPercent(progress.progression))
            } else {
                formatPercent(progress.progression)
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderControls(
    progress: ReaderProgress,
    positionCount: Int,
    onSeek: (Int) -> Unit,
    onOpenContents: () -> Unit,
    onOpenAppearance: () -> Unit,
    onAddNote: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(FloatingToolbarDefaults.ScreenOffset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (positionCount > 1) {
            // While dragging, the thumb follows the finger rather than the page actually shown.
            var dragged by remember { mutableStateOf<Float?>(null) }
            val shown = dragged ?: (progress.position ?: 1).toFloat()
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(shown.roundToInt().toString(), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = shown,
                        onValueChange = { dragged = it },
                        onValueChangeFinished = {
                            dragged?.let { onSeek(it.roundToInt()) }
                            dragged = null
                        },
                        valueRange = 1f..positionCount.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(positionCount.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            IconButton(onClick = onOpenContents) {
                Icon(Icons.AutoMirrored.Rounded.Toc, contentDescription = stringResource(R.string.reader_contents))
            }
            IconButton(onClick = onOpenAppearance) {
                Icon(Icons.Rounded.FormatSize, contentDescription = stringResource(R.string.reader_appearance))
            }
            IconButton(onClick = onAddNote) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = stringResource(R.string.note_add))
            }
        }
    }
}

@Composable
private fun ReaderLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun ReaderFailed(reason: ReaderFailure, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(R.string.reader_failed_title),
                message = stringResource(
                    when (reason) {
                        ReaderFailure.NOT_FOUND -> R.string.book_not_found
                        ReaderFailure.FILE_MISSING -> R.string.book_file_missing_message
                        ReaderFailure.UNSUPPORTED -> R.string.reader_failed_unsupported
                        ReaderFailure.UNREADABLE -> R.string.reader_failed_unreadable
                    },
                ),
                action = { Button(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        }
    }
}

/** Hides the status and navigation bars while reading; a swipe brings them back briefly. */
@Composable
private fun ImmersiveMode(hideSystemBars: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, hideSystemBars) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideSystemBars) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Keeps the display awake while pages are being turned, but not for a book left lying open. */
@Composable
private fun KeepScreenOn(interactions: Int) {
    val view = LocalView.current
    LaunchedEffect(view, interactions) {
        view.keepScreenOn = true
        delay(KEEP_AWAKE_MS)
        view.keepScreenOn = false
    }
    DisposableEffect(view) {
        onDispose { view.keepScreenOn = false }
    }
}

@PreviewLightDark
@Composable
private fun ReaderScaffoldPreview() {
    AppTheme {
        ReaderScaffold(
            title = "The Left Hand of Darkness",
            progress = ReaderProgress(0.42, 128, "The Question of Sex", 6),
            positionCount = 304,
            chromeVisible = true,
            pageBackground = MaterialTheme.colorScheme.surface,
            pageText = MaterialTheme.colorScheme.onSurface,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onSeek = {},
            onOpenContents = {},
            onOpenAppearance = {},
            onAddNote = {},
        ) { modifier ->
            Text(
                text = "I'll make my report as if I told a story, for I was taught as a child on my " +
                    "homeworld that Truth is a matter of the imagination.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = modifier.padding(horizontal = 24.dp, vertical = 96.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReaderLoadingPreview() {
    AppTheme { ReaderLoading() }
}

@PreviewLightDark
@Composable
private fun ReaderFailedPreview() {
    AppTheme { ReaderFailed(ReaderFailure.FILE_MISSING, onBack = {}) }
}
