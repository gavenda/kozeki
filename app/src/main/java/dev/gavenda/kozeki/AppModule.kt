package dev.gavenda.kozeki

import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.epub.Readium
import dev.gavenda.kozeki.data.files.BookStorage
import dev.gavenda.kozeki.data.metadata.CoverDownloader
import dev.gavenda.kozeki.data.metadata.MatchService
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverProvider
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.metadata.hardcover.TokenStore
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.repository.StatsRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.data.work.MatchWorker
import dev.gavenda.kozeki.ui.addbook.AddBookViewModel
import dev.gavenda.kozeki.ui.book.BookDetailViewModel
import dev.gavenda.kozeki.ui.calendar.CalendarViewModel
import dev.gavenda.kozeki.ui.library.LibraryViewModel
import dev.gavenda.kozeki.ui.reader.ReaderViewModel
import dev.gavenda.kozeki.ui.search.SearchViewModel
import dev.gavenda.kozeki.ui.settings.SettingsViewModel
import dev.gavenda.kozeki.ui.statistics.StatisticsViewModel
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.module

private const val USER_AGENT = "Kozeki/${BuildConfig.VERSION_NAME} (Android)"

/** The app's dependency graph. Singles are created lazily and live as long as the process. */
val appModule = module {

    // The application-wide scope, for work that must outlive the screen that started it.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<Clock> { Clock.systemDefaultZone() }

    single { KozekiDatabase.create(androidContext()) }

    single { SettingsRepository(androidContext()) }

    single { BookStorage(androidContext()) }

    single { Readium(androidApplication()) }

    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    single { LibraryRepository(get(), get(), get(), CoverDownloader(get()), get(), get()) }

    single { StatsRepository(get(), get(), get(), get()) }

    single {
        HardcoverAuth(
            client = get(),
            store = TokenStore(androidContext()),
            clock = get(),
            clientId = BuildConfig.HARDCOVER_CLIENT_ID,
            redirectUri = BuildConfig.HARDCOVER_REDIRECT_URI,
            scopes = BuildConfig.HARDCOVER_SCOPES,
        )
    }

    // Signing in may be what books imported earlier were waiting for.
    single { HardcoverSignIn(get(), get(), scheduleMatching()) }

    single { MetadataRepository(HardcoverProvider(get(), get()), get<KozekiDatabase>().metadataCacheDao(), get()) }

    single { MatchService(get(), get()) }

    viewModel { LibraryViewModel(get(), get(), scheduleMatching()) }

    viewModel { AddBookViewModel(get(), get()) }

    viewModel { SearchViewModel(get()) }

    viewModel { CalendarViewModel(get(), get()) }

    viewModel { params -> StatisticsViewModel(get(), get(), initialDate = params.getOrNull()) }

    viewModel { (bookId: String) -> BookDetailViewModel(bookId, get(), get(), get(), get(), scheduleMatching()) }

    viewModel { (bookId: String) ->
        ReaderViewModel(bookId, androidApplication(), get(), get(), get(), get(), get())
    }

    viewModel {
        SettingsViewModel(
            settings = get(),
            metadata = get(),
            stats = get(),
            hardcoverAuth = get(),
            hardcoverSignIn = get(),
            versionName = BuildConfig.VERSION_NAME,
            clock = get(),
        )
    }
}

/** Queues a metadata lookup for every book still waiting for one. Safe to call repeatedly. */
private fun Scope.scheduleMatching(): () -> Unit {
    val context = androidContext()
    return { MatchWorker.enqueue(context) }
}
