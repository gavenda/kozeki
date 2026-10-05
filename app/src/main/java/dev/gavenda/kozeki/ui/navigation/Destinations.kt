package dev.gavenda.kozeki.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Daily statistics for one specific day, reached from the calendar. */
@Serializable
data class DayRoute(val epochDay: Long)

/** The three destinations in the navigation bar and rail. */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    LIBRARY(
        route = LibraryRoute,
        label = R.string.nav_library,
        icon = Icons.AutoMirrored.Outlined.LibraryBooks,
        selectedIcon = Icons.AutoMirrored.Rounded.LibraryBooks,
    ),
    CALENDAR(
        route = CalendarRoute,
        label = R.string.nav_calendar,
        icon = Icons.Outlined.CalendarMonth,
        selectedIcon = Icons.Rounded.CalendarMonth,
    ),
    STATISTICS(
        route = StatisticsRoute,
        label = R.string.nav_statistics,
        icon = Icons.Outlined.BarChart,
        selectedIcon = Icons.Rounded.BarChart,
    ),
}
