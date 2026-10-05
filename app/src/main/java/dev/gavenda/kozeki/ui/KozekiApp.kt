package dev.gavenda.kozeki.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.gavenda.kozeki.ui.addbook.AddBookScreen
import dev.gavenda.kozeki.ui.addbook.BookFormScreen
import dev.gavenda.kozeki.ui.author.AuthorScreen
import dev.gavenda.kozeki.ui.book.BookDetailScreen
import dev.gavenda.kozeki.ui.calendar.CalendarScreen
import dev.gavenda.kozeki.ui.library.LibraryScreen
import dev.gavenda.kozeki.ui.navigation.AddBookRoute
import dev.gavenda.kozeki.ui.navigation.AuthorRoute
import dev.gavenda.kozeki.ui.navigation.BookRoute
import dev.gavenda.kozeki.ui.navigation.CalendarRoute
import dev.gavenda.kozeki.ui.navigation.DayRoute
import dev.gavenda.kozeki.ui.navigation.LibraryRoute
import dev.gavenda.kozeki.ui.navigation.BookFormRoute
import dev.gavenda.kozeki.ui.navigation.ReaderRoute
import dev.gavenda.kozeki.ui.navigation.SettingsRoute
import dev.gavenda.kozeki.ui.navigation.StatisticsRoute
import dev.gavenda.kozeki.ui.navigation.TopLevelDestination
import dev.gavenda.kozeki.ui.reader.ReaderScreen
import dev.gavenda.kozeki.ui.settings.SettingsScreen
import dev.gavenda.kozeki.ui.statistics.StatisticsScreen
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.LocalDate

/** The whole app: the navigation frame around a graph of screens. */
@Composable
fun KozekiApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // Before the first destination resolves, assume the start destination so the bar does not flicker.
    val topLevel = if (destination == null) {
        TopLevelDestination.LIBRARY
    } else {
        TopLevelDestination.entries.firstOrNull { destination.hasRoute(it.route::class) }
    }

    // A tap that lands while its screen is still sliding in or out is ignored. Otherwise a double
    // tap opens two copies of a page, or pops two pages for one press of Back.
    fun NavBackStackEntry.open(route: Any) {
        if (isResumed) navController.navigate(route)
    }
    fun NavBackStackEntry.back() {
        if (isResumed) navController.popBackStack()
    }

    KozekiNavigationScaffold(current = topLevel, onSelect = navController::navigateToTopLevel) {
        NavHost(
            navController = navController,
            startDestination = LibraryRoute,
            enterTransition = { slideIntoContainer(slideDirection(forward = true), slideSpec()) },
            exitTransition = { slideOutOfContainer(slideDirection(forward = true), slideSpec()) },
            popEnterTransition = { slideIntoContainer(slideDirection(forward = false), slideSpec()) },
            popExitTransition = { slideOutOfContainer(slideDirection(forward = false), slideSpec()) },
            // The back gesture scrubs through these. Without them it shrinks the page instead.
            predictivePopEnterTransition = { slideIntoContainer(slideDirection(forward = false), dragSpec()) },
            predictivePopExitTransition = { slideOutOfContainer(slideDirection(forward = false), dragSpec()) },
        ) {
            composable<LibraryRoute> { entry ->
                LibraryScreen(
                    onOpenBook = { entry.open(BookRoute(it)) },
                    onOpenAuthor = { entry.open(AuthorRoute(it.id, it.name)) },
                    onAddBook = { entry.open(AddBookRoute) },
                    onAddManually = { entry.open(BookFormRoute()) },
                    onOpenSettings = { entry.open(SettingsRoute) },
                )
            }
            composable<CalendarRoute> { entry ->
                CalendarScreen(
                    onOpenDay = { entry.open(DayRoute(it.toEpochDay())) },
                    onOpenSettings = { entry.open(SettingsRoute) },
                )
            }
            composable<StatisticsRoute> { entry ->
                StatisticsScreen(
                    onOpenBook = { entry.open(BookRoute(it)) },
                    onOpenSettings = { entry.open(SettingsRoute) },
                )
            }
            composable<DayRoute> { entry ->
                StatisticsScreen(
                    onOpenBook = { entry.open(BookRoute(it)) },
                    onOpenSettings = { entry.open(SettingsRoute) },
                    initialDate = LocalDate.ofEpochDay(entry.toRoute<DayRoute>().epochDay),
                    onBack = { entry.back() },
                )
            }
            composable<AddBookRoute> { entry ->
                AddBookScreen(
                    onBack = { entry.back() },
                    onOpenBook = { entry.open(BookRoute(it)) },
                    onOpenAuthor = { entry.open(AuthorRoute(it.id, it.name)) },
                    onOpenSettings = { entry.open(SettingsRoute) },
                )
            }
            composable<BookFormRoute> { entry ->
                BookFormScreen(
                    bookId = entry.toRoute<BookFormRoute>().bookId,
                    onBack = { entry.back() },
                    // The book's page takes the form's place, so Back from it does not return to a spent form.
                    onAdded = { navController.navigate(BookRoute(it)) { popUpTo<BookFormRoute> { inclusive = true } } },
                )
            }
            composable<AuthorRoute> { entry ->
                val route = entry.toRoute<AuthorRoute>()
                AuthorScreen(
                    authorId = route.authorId,
                    name = route.name,
                    onBack = { entry.back() },
                    onOpenBook = { entry.open(BookRoute(it)) },
                    onOpenAuthor = { entry.open(AuthorRoute(it.id, it.name)) },
                )
            }
            composable<SettingsRoute> { entry -> SettingsScreen(onBack = { entry.back() }) }
            composable<BookRoute> { entry ->
                BookDetailScreen(
                    bookId = entry.toRoute<BookRoute>().bookId,
                    onBack = { entry.back() },
                    onRead = { entry.open(ReaderRoute(it)) },
                    onEdit = { entry.open(BookFormRoute(it)) },
                    onOpenAuthor = { entry.open(AuthorRoute(it.id, it.name)) },
                )
            }
            composable<ReaderRoute> { entry ->
                ReaderScreen(bookId = entry.toRoute<ReaderRoute>().bookId, onBack = { entry.back() })
            }
        }
    }
}

private const val SLIDE_MS = 400

// Material's emphasized curve: quick to get going, long to settle.
private val SlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slideSpec() = tween<IntOffset>(SLIDE_MS, easing = SlideEasing)

// Linear, so that during a back gesture the page moves exactly as far as the finger has.
private fun dragSpec() = tween<IntOffset>(SLIDE_MS, easing = LinearEasing)

private val NavBackStackEntry.isResumed: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

private val NavBackStackEntry.topLevelIndex: Int
    get() = TopLevelDestination.entries.indexOfFirst { destination.hasRoute(it.route::class) }

/**
 * Which way both pages move. Going deeper slides towards the start and going back towards the end.
 * Between two tabs the order in the bar decides instead, so a tab always comes in from its side.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(forward: Boolean): SlideDirection {
    val from = initialState.topLevelIndex
    val to = targetState.topLevelIndex
    val towardsStart = if (from >= 0 && to >= 0) to > from else forward
    return if (towardsStart) SlideDirection.Start else SlideDirection.End
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
                    icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
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
 * Switches tabs and remembers the one being left, so Back walks through the tabs in the order
 * they were visited instead of jumping straight to the library.
 */
private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) { launchSingleTop = true }
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
