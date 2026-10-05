package dev.gavenda.kozeki

import android.app.Application
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.work.MatchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KozekiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@KozekiApplication)
            modules(appModule)
        }.koin
        koin.get<CoroutineScope>().launch { koin.get<HardcoverAuth>().restore() }
        // Picks up books imported offline, or left pending because no source was configured yet.
        MatchWorker.enqueue(this)
    }
}
