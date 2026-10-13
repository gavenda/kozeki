package dev.gavenda.kozeki

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dev.gavenda.kozeki.data.manga.MangaCoverFetcher
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.work.MatchWorker
import eu.kanade.tachiyomi.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class KozekiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppInfo.install(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        Injekt.importModule(AppModule(this))
        // Manga covers are fetched through their source, which a plain network fetch is refused by.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(MangaCoverFetcher.Factory { Injekt.get() }) }
                .build()
        }
        Injekt.get<CoroutineScope>().launch { Injekt.get<HardcoverAuth>().restore() }
        // Picks up books imported offline, or left pending because no source was configured yet.
        MatchWorker.enqueue(this)
    }
}
