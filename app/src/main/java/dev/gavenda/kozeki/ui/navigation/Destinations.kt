package dev.gavenda.kozeki.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.gavenda.kozeki.R
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object CalendarRoute

@Serializable
data object StatisticsRoute

@Serializable
data class BookRoute(val bookId: String)

@Serializable
data class ReaderRoute(val bookId: String)

@Serializable
data object AddBookRoute

/** The form a book's details are typed into by hand: for a new book, or to edit the one with [bookId]. */
@Serializable
data class BookFormRoute(val bookId: String? = null)

/** An author's page on the metadata source, reached from a book found there or one in the library. */
@Serializable
data class AuthorRoute(val authorId: String, val name: String)

@Serializable
data object SettingsRoute

/**
 * A book found on a metadata source that is not among the user's own yet. [metadata] is the whole
 * [dev.gavenda.kozeki.data.metadata.BookMetadata] as JSON, since nothing is stored to look it up from.
 */
@Serializable
data class FoundBookRoute(val metadata: String)

/** Daily statistics for one specific day, reached from the calendar. */
@Serializable
data class DayRoute(val epochDay: Long)

/** The three destinations in the navigation bar and rail. */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    LIBRARY(
        route = LibraryRoute,
        label = R.string.nav_library,
        icon = R.drawable.shelves_48px,
        selectedIcon = R.drawable.shelves_fill_48px,
    ),
    CALENDAR(
        route = CalendarRoute,
        label = R.string.nav_calendar,
        icon = R.drawable.calendar_month_48px,
        selectedIcon = R.drawable.calendar_month_fill_48px,
    ),
    STATISTICS(
        route = StatisticsRoute,
        label = R.string.nav_statistics,
        icon = R.drawable.leaderboard_48px,
        selectedIcon = R.drawable.leaderboard_fill_48px,
    ),
}
