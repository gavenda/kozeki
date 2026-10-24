package dev.gavenda.kozeki.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gavenda.kozeki.BuildConfig
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.cancelMatching
import dev.gavenda.kozeki.scheduleMatching
import dev.gavenda.kozeki.ui.addbook.AddBookViewModel
import dev.gavenda.kozeki.ui.addbook.BookFormViewModel
import dev.gavenda.kozeki.ui.author.AuthorViewModel
import dev.gavenda.kozeki.ui.series.SeriesViewModel
import dev.gavenda.kozeki.ui.book.BookDetailViewModel
import dev.gavenda.kozeki.ui.book.FoundBookViewModel
import dev.gavenda.kozeki.ui.calendar.CalendarViewModel
import dev.gavenda.kozeki.ui.downloads.DownloadQueueViewModel
import dev.gavenda.kozeki.ui.library.LibraryViewModel
import dev.gavenda.kozeki.ui.comic.ComicReaderViewModel
import dev.gavenda.kozeki.ui.reader.ReaderViewModel
import dev.gavenda.kozeki.ui.search.SearchViewModel
import dev.gavenda.kozeki.ui.settings.SettingsViewModel
import dev.gavenda.kozeki.ui.statistics.StatisticsViewModel
import java.time.LocalDate
import org.koin.core.context.GlobalContext

/**
 * A screen's ViewModel, built from what Koin holds and kept for as long as [key] stays on the
 * back stack. The factories below are the one place that knows what each ViewModel is made of.
 */
@Composable
inline fun <reified VM : ViewModel> koinViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key) { create() }

private val koin get() = GlobalContext.get()

fun libraryViewModel() =
    LibraryViewModel(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), scheduleMatching(), cancelMatching())

fun addBookViewModel() = AddBookViewModel(koin.get(), koin.get(), koin.get(), koin.get())

fun bookFormViewModel(bookId: String?) = BookFormViewModel(bookId, koin.get())

fun authorViewModel(authorId: String, name: String) = AuthorViewModel(authorId, name, koin.get(), koin.get())

fun seriesViewModel(name: String) = SeriesViewModel(name, koin.get(), koin.get())

fun downloadQueueViewModel() = DownloadQueueViewModel(koin.get(), koin.get())

fun searchViewModel() = SearchViewModel(koin.get())

fun calendarViewModel() = CalendarViewModel(koin.get(), koin.get())

fun statisticsViewModel(initialDate: LocalDate?) = StatisticsViewModel(koin.get(), koin.get(), initialDate)

fun foundBookViewModel(result: BookMetadata) =
    FoundBookViewModel(result, koin.get(), koin.get(), koin.get())

fun bookDetailViewModel(bookId: String) =
    BookDetailViewModel(
        bookId, koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), scheduleMatching(), koin.get(), koin.get(), koin.get(),
        koin.get(),
    )

fun readerViewModel(bookId: String) = ReaderViewModel(
    bookId,
    koin.get<Application>(),
    koin.get(),
    koin.get(),
    koin.get(),
    koin.get(),
    koin.get(),
)

fun comicReaderViewModel(bookId: String, chapterUrl: String) = ComicReaderViewModel(
    bookId,
    chapterUrl,
    koin.get(),
    koin.get(),
    koin.get(),
    koin.get(),
    koin.get(),
    koin.get(),
    koin.get(),
)

fun settingsViewModel() = SettingsViewModel(
    settings = koin.get(),
    metadata = koin.get(),
    stats = koin.get(),
    hardcoverAuth = koin.get(),
    hardcoverSignIn = koin.get(),
    aniListAuth = koin.get(),
    driveBackup = koin.get(),
    localBackup = koin.get(),
    comicSources = koin.get(),
    comicPages = koin.get(),
    appUpdates = koin.get(),
    versionName = BuildConfig.VERSION_NAME,
    clock = koin.get(),
)
