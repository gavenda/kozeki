package dev.gavenda.kozeki.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gavenda.kozeki.BuildConfig
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.scheduleMatching
import dev.gavenda.kozeki.ui.addbook.AddBookViewModel
import dev.gavenda.kozeki.ui.addbook.BookFormViewModel
import dev.gavenda.kozeki.ui.author.AuthorViewModel
import dev.gavenda.kozeki.ui.book.BookDetailViewModel
import dev.gavenda.kozeki.ui.book.FoundBookViewModel
import dev.gavenda.kozeki.ui.calendar.CalendarViewModel
import dev.gavenda.kozeki.ui.library.LibraryViewModel
import dev.gavenda.kozeki.ui.manga.MangaReaderViewModel
import dev.gavenda.kozeki.ui.reader.ReaderViewModel
import dev.gavenda.kozeki.ui.search.SearchViewModel
import dev.gavenda.kozeki.ui.settings.SettingsViewModel
import dev.gavenda.kozeki.ui.statistics.StatisticsViewModel
import java.time.LocalDate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A screen's ViewModel, built from what Injekt holds and kept for as long as [key] stays on the
 * back stack. The factories below are the one place that knows what each ViewModel is made of.
 */
@Composable
inline fun <reified VM : ViewModel> injektViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key) { create() }

fun libraryViewModel() = LibraryViewModel(Injekt.get(), Injekt.get(), Injekt.get(), Injekt.get(), scheduleMatching())

fun addBookViewModel() = AddBookViewModel(Injekt.get(), Injekt.get(), Injekt.get(), Injekt.get())

fun bookFormViewModel(bookId: String?) = BookFormViewModel(bookId, Injekt.get())

fun authorViewModel(authorId: String, name: String) = AuthorViewModel(authorId, name, Injekt.get(), Injekt.get())

fun searchViewModel() = SearchViewModel(Injekt.get())

fun calendarViewModel() = CalendarViewModel(Injekt.get(), Injekt.get())

fun statisticsViewModel(initialDate: LocalDate?) = StatisticsViewModel(Injekt.get(), Injekt.get(), initialDate)

fun foundBookViewModel(result: BookMetadata) = FoundBookViewModel(result, Injekt.get(), Injekt.get(), Injekt.get())

fun bookDetailViewModel(bookId: String) =
    BookDetailViewModel(bookId, Injekt.get(), Injekt.get(), Injekt.get(), Injekt.get(), Injekt.get(), scheduleMatching())

fun readerViewModel(bookId: String) = ReaderViewModel(
    bookId,
    Injekt.get<Application>(),
    Injekt.get(),
    Injekt.get(),
    Injekt.get(),
    Injekt.get(),
    Injekt.get(),
)

fun mangaReaderViewModel(bookId: String, chapterUrl: String) = MangaReaderViewModel(
    bookId,
    chapterUrl,
    Injekt.get(),
    Injekt.get(),
    Injekt.get(),
    Injekt.get(),
    Injekt.get(),
)

fun settingsViewModel() = SettingsViewModel(
    settings = Injekt.get(),
    metadata = Injekt.get(),
    stats = Injekt.get(),
    hardcoverAuth = Injekt.get(),
    hardcoverSignIn = Injekt.get(),
    driveBackup = Injekt.get(),
    mangaSources = Injekt.get(),
    versionName = BuildConfig.VERSION_NAME,
    clock = Injekt.get(),
)
