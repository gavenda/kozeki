package dev.gavenda.kozeki.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import dev.gavenda.kozeki.R
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute : NavKey

@Serializable
data object CalendarRoute : NavKey

@Serializable
data object StatisticsRoute : NavKey

@Serializable
data class BookRoute(val bookId: String) : NavKey

@Serializable
data class ReaderRoute(val bookId: String) : NavKey

/** The chapter of a comic in the library with the URL [chapterUrl] on its source, being read. */
@Serializable
data class ComicReaderRoute(val bookId: String, val chapterUrl: String) : NavKey

/** Finding a book online to add. */
@Serializable
data object AddBookRoute : NavKey

/** The form a book's details are typed into by hand: for a new book, or to edit the one with [bookId]. */
@Serializable
data class BookFormRoute(val bookId: String? = null) : NavKey

/** An author's page on the metadata source, reached from a book found there or one in the library. */
@Serializable
data class AuthorRoute(val authorId: String, val name: String) : NavKey

/** A series by its name: the user's books in it, then the rest of it on the metadata source. */
@Serializable
data class SeriesRoute(val name: String) : NavKey

/** The chapters of comics waiting to be downloaded or coming in. */
@Serializable
data object DownloadQueueRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/**
 * The repositories Mihon extensions are installed from, reached from Settings or a website's link.
 * [addUrl] is a repository from such a link, shown in the add dialog to be confirmed.
 */
@Serializable
data class ExtensionReposRoute(val addUrl: String? = null) : NavKey

/**
 * A book found on a metadata source that is not among the user's own yet. [metadata] is the whole
 * [dev.gavenda.kozeki.data.metadata.BookMetadata] as JSON, since nothing is stored to look it up from.
 */
@Serializable
data class FoundBookRoute(val metadata: String) : NavKey

/** Daily statistics for one specific day, reached from the calendar. */
@Serializable
data class DayRoute(val epochDay: Long) : NavKey

/** The three destinations in the navigation bar and rail. */
enum class TopLevelDestination(
    val route: NavKey,
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
