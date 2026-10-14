package dev.gavenda.kozeki

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dev.gavenda.kozeki.data.manga.PageImages
import dev.gavenda.kozeki.data.manga.MangaCoverFetcher
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.work.MatchWorker
import eu.kanade.tachiyomi.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

class KozekiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppInfo.install(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        val koin = startKoin { modules(appModule(this@KozekiApplication)) }.koin
        // Before anything can load an extension.
        koin.exposeToExtensions()
        // Manga covers are fetched through their source, which a plain network fetch is refused by.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(MangaCoverFetcher.Factory { koin.get() })
                    // AVIF and JPEG XL pages, which the platform cannot decode at a webtoon's height.
                    add(PageImages.CoilDecoder.Factory())
                }
                .build()
        }
        koin.get<CoroutineScope>().launch { koin.get<HardcoverAuth>().restore() }
        // Picks up books imported offline, or left pending because no source was configured yet.
        MatchWorker.enqueue(this)
    }
}
