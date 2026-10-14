package dev.gavenda.kozeki

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.gavenda.kozeki.data.backup.BackupRepository
import dev.gavenda.kozeki.data.backup.drive.DriveApi
import dev.gavenda.kozeki.data.backup.drive.DriveBackup
import dev.gavenda.kozeki.data.backup.drive.PlayServicesDriveAuthorizer
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.epub.Readium
import dev.gavenda.kozeki.data.files.BookStorage
import dev.gavenda.kozeki.data.files.IncomingEpubs
import dev.gavenda.kozeki.data.manga.MangaChapterRepository
import dev.gavenda.kozeki.data.manga.MangaImages
import dev.gavenda.kozeki.data.manga.MangaPages
import dev.gavenda.kozeki.data.manga.MangaReaderPreferences
import dev.gavenda.kozeki.data.manga.MangaProvider
import dev.gavenda.kozeki.data.manga.MangaSources
import dev.gavenda.kozeki.data.metadata.CoverDownloader
import dev.gavenda.kozeki.data.metadata.MatchService
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.googlebooks.GoogleBooksProvider
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverProvider
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.metadata.hardcover.TokenStore
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.repository.StatsRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.data.work.MatchWorker
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory

private const val USER_AGENT = "Kozeki/${BuildConfig.VERSION_NAME} (Android)"

/** The app's dependency graph. Singles are created lazily and live as long as the process. */
@OptIn(ExperimentalSerializationApi::class)
fun appModule(app: Application) = module {
    single { app }
    single<Context> { app }

    // The application-wide scope, for work that must outlive the screen that started it.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<Clock> { Clock.systemDefaultZone() }

    single { KozekiDatabase.create(app) }

    single { SettingsRepository(app) }

    single { BookStorage(app) }

    single { IncomingEpubs() }

    single { Readium(app) }

    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    single {
        LibraryRepository(get(), get(), get(), CoverDownloader(get(), get()), get(), get())
    }

    single { StatsRepository(get(), get(), get(), get()) }

    single {
        HardcoverAuth(
            client = get(),
            store = TokenStore(app),
            clock = get(),
            clientId = BuildConfig.HARDCOVER_CLIENT_ID,
            redirectUri = BuildConfig.HARDCOVER_REDIRECT_URI,
            scopes = BuildConfig.HARDCOVER_SCOPES,
        )
    }

    // Signing in may be what books imported earlier were waiting for.
    single { HardcoverSignIn(get(), get(), scheduleMatching()) }

    single {
        val providers = listOf(
            HardcoverProvider(get(), get()),
            GoogleBooksProvider(
                client = get(),
                apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY,
                androidPackage = app.packageName,
                androidCertSha1 = signingCertificateSha1(app),
            ),
            MangaProvider(get(), get()),
        )
        MetadataRepository(providers, get<KozekiDatabase>().metadataCacheDao(), get())
    }

    single { MatchService(get(), get()) }

    single { BackupRepository(get(), get(), get(), get(), BuildConfig.VERSION_NAME, Build.MODEL) }

    single {
        val library = get<LibraryRepository>()
        val scope = get<CoroutineScope>()
        val matchPending = scheduleMatching()
        DriveBackup(
            auth = PlayServicesDriveAuthorizer(app),
            drive = DriveApi(get()),
            library = get<BackupRepository>(),
            links = get<SettingsRepository>(),
            scope = scope,
            // The books come back without their cover pictures, and some still waiting for a lookup.
            onRestored = {
                matchPending()
                scope.launch { library.downloadMissingCovers() }
            },
        )
    }

    // What Mihon extensions look up, see [exposeToExtensions]. The set is fixed by the extension
    // API (together with Application and Context above).
    single { Json { ignoreUnknownKeys = true; explicitNulls = false } }

    single<ProtoBuf> { ProtoBuf }

    single {
        XML.v1 {
            policy {
                ignoreUnknownChildren()
                autoPolymorphic = true
            }
            xmlDeclMode = XmlDeclMode.Charset
            xmlVersion = XmlVersion.XML10
            setIndent(2)
        }
    }

    single { NetworkHelper(scope = get(), context = app) }

    single { JavaScriptEngine(app) }

    single { MangaSources(app, get()) }

    single { MangaImages(get(), get()) }

    single { MangaChapterRepository(get(), get(), get()) }

    single { MangaPages(app, get()) }

    single { MangaReaderPreferences(app) }
}

/**
 * Mihon extensions are compiled against Injekt and look their dependencies up there by type, so
 * they cannot see Koin. This hands them the few types the extension API promises, each resolved
 * from Koin on first use so there is still only one instance. Call it before any extension loads.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Koin.exposeToExtensions() {
    val koin = this
    Injekt.addSingletonFactory<Application> { koin.get() }
    Injekt.addSingletonFactory<Context> { koin.get() }
    Injekt.addSingletonFactory<NetworkHelper> { koin.get() }
    Injekt.addSingletonFactory<JavaScriptEngine> { koin.get() }
    Injekt.addSingletonFactory<Json> { koin.get() }
    Injekt.addSingletonFactory<ProtoBuf> { koin.get() }
    Injekt.addSingletonFactory<XML> { koin.get() }
}

/** Queues a metadata lookup for every book still waiting for one. Safe to call repeatedly. */
internal fun scheduleMatching(): () -> Unit {
    val context = GlobalContext.get().get<Application>()
    return { MatchWorker.enqueue(context) }
}

/** SHA-1 of the certificate this build is signed with, as Google wants it: upper-case hex, no colons. */
private fun signingCertificateSha1(context: Context): String? = runCatching {
    val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
    )
    val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
    MessageDigest.getInstance("SHA-1").digest(signer.toByteArray()).joinToString("") { "%02X".format(it) }
}.getOrNull()
