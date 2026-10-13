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
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

private const val USER_AGENT = "Kozeki/${BuildConfig.VERSION_NAME} (Android)"

/** The app's dependency graph. Singles are created lazily and live as long as the process. */
class AppModule(private val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<Context>(app)

        // The application-wide scope, for work that must outlive the screen that started it.
        addSingletonFactory<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

        addSingletonFactory<Clock> { Clock.systemDefaultZone() }

        addSingletonFactory { KozekiDatabase.create(app) }

        addSingletonFactory { SettingsRepository(app) }

        addSingletonFactory { BookStorage(app) }

        addSingletonFactory { IncomingEpubs() }

        addSingletonFactory { Readium(app) }

        addSingletonFactory {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
                }
                .build()
        }

        addSingletonFactory {
            LibraryRepository(get(), get(), get(), CoverDownloader(get(), get()), get(), get())
        }

        addSingletonFactory { StatsRepository(get(), get(), get(), get()) }

        addSingletonFactory {
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
        addSingletonFactory { HardcoverSignIn(get(), get(), scheduleMatching()) }

        addSingletonFactory {
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

        addSingletonFactory { MatchService(get(), get()) }

        addSingletonFactory { BackupRepository(get(), get(), get(), get(), BuildConfig.VERSION_NAME, Build.MODEL) }

        addSingletonFactory {
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

        // What Mihon extensions look up through Injekt. They ask for these by type, so the set is
        // fixed by the extension API (together with Application and Context above).
        addSingletonFactory { Json { ignoreUnknownKeys = true; explicitNulls = false } }

        addSingletonFactory<ProtoBuf> { ProtoBuf }

        addSingletonFactory {
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

        addSingletonFactory { NetworkHelper(scope = get(), context = app) }

        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory { MangaSources(app, get()) }

        addSingletonFactory { MangaImages(get(), get()) }

        addSingletonFactory { MangaChapterRepository(get(), get(), get()) }

        addSingletonFactory { MangaPages(app, get()) }

        addSingletonFactory { MangaReaderPreferences(app) }
    }
}

/** Queues a metadata lookup for every book still waiting for one. Safe to call repeatedly. */
internal fun scheduleMatching(): () -> Unit {
    val context = Injekt.get<Application>()
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
