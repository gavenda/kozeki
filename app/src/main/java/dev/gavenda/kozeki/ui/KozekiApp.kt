package dev.gavenda.kozeki.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.gavenda.kozeki.ui.addbook.AddBookScreen
import dev.gavenda.kozeki.ui.addbook.BookFormScreen
import dev.gavenda.kozeki.ui.author.AuthorScreen
import dev.gavenda.kozeki.ui.series.SeriesScreen
import dev.gavenda.kozeki.data.files.IncomingEpubs
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.ui.book.BookDetailScreen
import dev.gavenda.kozeki.ui.book.FoundBookScreen
import dev.gavenda.kozeki.ui.calendar.CalendarScreen
import dev.gavenda.kozeki.ui.downloads.DownloadQueueScreen
import dev.gavenda.kozeki.ui.library.LibraryScreen
import dev.gavenda.kozeki.ui.comic.ComicReaderScreen
import dev.gavenda.kozeki.ui.navigation.AddBookRoute
import dev.gavenda.kozeki.ui.navigation.AuthorRoute
import dev.gavenda.kozeki.ui.navigation.SeriesRoute
import dev.gavenda.kozeki.ui.navigation.BookRoute
import dev.gavenda.kozeki.ui.navigation.CalendarRoute
import dev.gavenda.kozeki.ui.navigation.DayRoute
import dev.gavenda.kozeki.ui.navigation.DownloadQueueRoute
import dev.gavenda.kozeki.ui.navigation.FoundBookRoute
import dev.gavenda.kozeki.ui.navigation.LibraryRoute
import dev.gavenda.kozeki.ui.navigation.BookFormRoute
import dev.gavenda.kozeki.ui.navigation.ComicReaderRoute
import dev.gavenda.kozeki.ui.navigation.ReaderRoute
import dev.gavenda.kozeki.ui.navigation.SettingsRoute
import dev.gavenda.kozeki.ui.navigation.StatisticsRoute
import dev.gavenda.kozeki.ui.navigation.TopLevelDestination
import dev.gavenda.kozeki.ui.reader.ReaderScreen
import dev.gavenda.kozeki.ui.settings.SettingsScreen
import dev.gavenda.kozeki.ui.statistics.StatisticsScreen
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** The whole app: the navigation frame around the back stack of screens. */
@Composable
fun KozekiApp(incomingEpubs: IncomingEpubs) {
    // Kept across process death, like the rest of the saved state.
    val backStack = rememberNavBackStack(LibraryRoute)
    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == backStack.last() }

    // EPUBs another app hands over are taken in by the library, so it is brought back whatever
    // was showing. One that was opened rather than shared is then read, over its own page as if
    // it had been picked there.
    LaunchedEffect(backStack, incomingEpubs) {
        launch {
            incomingEpubs.waiting.collect { if (it.isNotEmpty()) backStack.backToLibrary() }
        }
        incomingEpubs.toOpen.collect { bookId ->
            backStack.backToLibrary()
            backStack.add(BookRoute(bookId))
            backStack.add(ReaderRoute(bookId))
        }
    }

    KozekiNavigationScaffold(current = topLevel, onSelect = backStack::navigateToTopLevel) {
        NavDisplay(
            backStack = backStack,
            onBack = backStack::pop,
            // A page sliding out sideways would otherwise pass over the navigation rail and hide it.
            modifier = Modifier.clipToBounds(),
            // Each page keeps its own saved state and ViewModels, cleared once it leaves the stack.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { slide(forward = true, slideSpec()) },
            popTransitionSpec = { slide(forward = false, slideSpec()) },
            // The back gesture scrubs through this. Without it the page shrinks instead.
            predictivePopTransitionSpec = { slide(forward = false, dragSpec()) },
            entryProvider = entryProvider {
                entry<LibraryRoute>(metadata = topLevelMetadata(TopLevelDestination.LIBRARY)) {
                    val nav = rememberEntryNavigator(backStack)
                    LibraryScreen(
                        onOpenBook = { nav.open(BookRoute(it)) },
                        onOpenAuthor = { nav.open(AuthorRoute(it.id, it.name)) },
                        onAddBook = { nav.open(AddBookRoute) },
                        onAddManually = { nav.open(BookFormRoute()) },
                        onOpenSettings = { nav.open(SettingsRoute) },
                        onOpenDownloads = { nav.open(DownloadQueueRoute) },
                    )
                }
                entry<CalendarRoute>(metadata = topLevelMetadata(TopLevelDestination.CALENDAR)) {
                    val nav = rememberEntryNavigator(backStack)
                    CalendarScreen(
                        onOpenDay = { nav.open(DayRoute(it.toEpochDay())) },
                        onOpenSettings = { nav.open(SettingsRoute) },
                    )
                }
                entry<StatisticsRoute>(metadata = topLevelMetadata(TopLevelDestination.STATISTICS)) {
                    val nav = rememberEntryNavigator(backStack)
                    StatisticsScreen(
                        onOpenBook = { nav.open(BookRoute(it)) },
                        onOpenSettings = { nav.open(SettingsRoute) },
                    )
                }
                entry<DayRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    StatisticsScreen(
                        onOpenBook = { nav.open(BookRoute(it)) },
                        onOpenSettings = { nav.open(SettingsRoute) },
                        initialDate = LocalDate.ofEpochDay(route.epochDay),
                        onBack = nav::back,
                    )
                }
                entry<AddBookRoute> {
                    val nav = rememberEntryNavigator(backStack)
                    AddBookScreen(
                        onBack = nav::back,
                        onOpenBook = { nav.open(BookRoute(it)) },
                        onOpenResult = { nav.open(FoundBookRoute(Json.encodeToString(it))) },
                        onOpenAuthor = { nav.open(AuthorRoute(it.id, it.name)) },
                        onOpenSettings = { nav.open(SettingsRoute) },
                    )
                }
                entry<FoundBookRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    val result = remember(route) { Json.decodeFromString<BookMetadata>(route.metadata) }
                    FoundBookScreen(
                        result = result,
                        onBack = nav::back,
                        onRead = { nav.open(ReaderRoute(it)) },
                        onReadChapter = { bookId, url -> nav.open(ComicReaderRoute(bookId, url)) },
                        onEdit = { nav.open(BookFormRoute(it)) },
                        onOpenAuthor = { nav.open(AuthorRoute(it.id, it.name)) },
                        onOpenSeries = { nav.open(SeriesRoute(it)) },
                    )
                }
                entry<BookFormRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    BookFormScreen(
                        bookId = route.bookId,
                        onBack = nav::back,
                        // The book's page takes the form's place, so Back from it does not return to a spent form.
                        onAdded = { backStack.replaceLastForm(BookRoute(it)) },
                    )
                }
                entry<AuthorRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    AuthorScreen(
                        authorId = route.authorId,
                        name = route.name,
                        onBack = nav::back,
                        onOpenBook = { nav.open(BookRoute(it)) },
                        onOpenResult = { nav.open(FoundBookRoute(Json.encodeToString(it))) },
                    )
                }
                entry<SeriesRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    SeriesScreen(
                        name = route.name,
                        onBack = nav::back,
                        onOpenBook = { nav.open(BookRoute(it)) },
                        onOpenResult = { nav.open(FoundBookRoute(Json.encodeToString(it))) },
                    )
                }
                entry<DownloadQueueRoute> {
                    val nav = rememberEntryNavigator(backStack)
                    DownloadQueueScreen(onBack = nav::back, onOpenComic = { nav.open(BookRoute(it)) })
                }
                entry<SettingsRoute> {
                    val nav = rememberEntryNavigator(backStack)
                    SettingsScreen(onBack = nav::back)
                }
                entry<BookRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    BookDetailScreen(
                        bookId = route.bookId,
                        onBack = nav::back,
                        onRead = { nav.open(ReaderRoute(it)) },
                        onReadChapter = { bookId, url -> nav.open(ComicReaderRoute(bookId, url)) },
                        onEdit = { nav.open(BookFormRoute(it)) },
                        onOpenAuthor = { nav.open(AuthorRoute(it.id, it.name)) },
                        onOpenSeries = { nav.open(SeriesRoute(it)) },
                        onOpenDownloads = { nav.open(DownloadQueueRoute) },
                    )
                }
                entry<ComicReaderRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    ComicReaderScreen(bookId = route.bookId, chapterUrl = route.chapterUrl, onBack = nav::back)
                }
                entry<ReaderRoute> { route ->
                    val nav = rememberEntryNavigator(backStack)
                    ReaderScreen(bookId = route.bookId, onBack = nav::back)
                }
            },
        )
    }
}

/**
 * Opens and closes pages from one entry on the back stack. A tap that lands while the entry is
 * still sliding in or out is ignored, since only a settled entry is resumed. Otherwise a double
 * tap opens two copies of a page, or pops two pages for one press of Back.
 */
private class EntryNavigator(private val backStack: MutableList<NavKey>, private val lifecycle: Lifecycle) {
    private val isResumed: Boolean
        get() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun open(route: NavKey) {
        if (isResumed) backStack.add(route)
    }

    fun back() {
        if (isResumed) backStack.pop()
    }
}

@Composable
private fun rememberEntryNavigator(backStack: MutableList<NavKey>): EntryNavigator {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(backStack, lifecycle) { EntryNavigator(backStack, lifecycle) }
}

private const val SLIDE_MS = 400

// Material's emphasized curve: quick to get going, long to settle.
private val SlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slideSpec() = tween<IntOffset>(SLIDE_MS, easing = SlideEasing)

// Linear, so that during a back gesture the page moves exactly as far as the finger has.
private fun dragSpec() = tween<IntOffset>(SLIDE_MS, easing = LinearEasing)

/** Marks a tab's page with its place in the bar, which the slide between two tabs goes by. */
private const val TOP_LEVEL_INDEX = "kozeki.topLevelIndex"

private fun topLevelMetadata(destination: TopLevelDestination): Map<String, Any> =
    mapOf(TOP_LEVEL_INDEX to destination.ordinal)

private val Scene<NavKey>.topLevelIndex: Int
    get() = metadata[TOP_LEVEL_INDEX] as? Int ?: -1

/**
 * Both pages slide the same way. Going deeper slides towards the start and going back towards
 * the end. Between two tabs the order in the bar decides instead, so a tab always comes in from
 * its side.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.slide(
    forward: Boolean,
    spec: FiniteAnimationSpec<IntOffset>,
): ContentTransform {
    val from = initialState.topLevelIndex
    val to = targetState.topLevelIndex
    val towardsStart = if (from >= 0 && to >= 0) to > from else forward
    val direction = if (towardsStart) SlideDirection.Start else SlideDirection.End
    return slideIntoContainer(direction, spec) togetherWith slideOutOfContainer(direction, spec)
}

/**
 * The navigation frame: the three top-level destinations in a navigation bar on phones and in a
 * rail on wider windows. It slides away when [current] is null, which is how detail screens and
 * the reader get the full window.
 */
@Composable
fun KozekiNavigationScaffold(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scaffoldState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(current != null) {
        if (current != null) scaffoldState.show() else scaffoldState.hide()
    }

    NavigationSuiteScaffold(
        navigationItems = {
            TopLevelDestination.entries.forEach { item ->
                val selected = item == current
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { onSelect(item) },
                    icon = { Icon(painterResource(if (selected) item.selectedIcon else item.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    label = { Text(stringResource(item.label)) },
                )
            }
        },
        modifier = modifier,
        state = scaffoldState,
        content = content,
    )
}

/**
 * Switches tabs and keeps the one being left underneath, so Back walks through the tabs in the
 * order they were visited instead of jumping straight to the library.
 */
private fun MutableList<NavKey>.navigateToTopLevel(destination: TopLevelDestination) {
    if (last() != destination.route) add(destination.route)
}

/** Closes the top page. The library at the bottom is never closed, Back from it leaves the app. */
private fun MutableList<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/** Closes whatever was opened since the library was last showing. */
private fun MutableList<NavKey>.backToLibrary() {
    while (size > 1 && last() != LibraryRoute) removeAt(lastIndex)
}

/** Closes the book form nearest the top, and everything over it, and opens [route] in its place. */
private fun MutableList<NavKey>.replaceLastForm(route: NavKey) {
    val form = indexOfLast { it is BookFormRoute }
    if (form > 0) subList(form, size).clear()
    add(route)
}

@ScreenPreviews
@Composable
private fun KozekiNavigationScaffoldPreview() {
    AppTheme {
        KozekiNavigationScaffold(current = TopLevelDestination.CALENDAR, onSelect = {}) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(TopLevelDestination.CALENDAR.label), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
