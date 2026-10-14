package dev.gavenda.kozeki.data.manga

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Mihon extensions installed on this device and the sources they provide. Nothing is loaded
 * until the first call that needs it, and the list follows apps being installed and removed after.
 */
class MangaSources(private val context: Context, private val scope: CoroutineScope) {

    private val loader = ExtensionLoader(context)
    private val loading = Mutex()
    private val _extensions = MutableStateFlow<List<MangaExtension>?>(null)
    private var receiverRegistered = false
    private var reloadJob: Job? = null

    /** The installed extensions, or null until they have been looked for the first time. */
    val extensions: StateFlow<List<MangaExtension>?> = _extensions.asStateFlow()

    /** Every source that can be searched, in the order of their extensions' names. Empty until loaded. */
    val sources: Flow<List<CatalogueSource>> = _extensions.map { list ->
        list.orEmpty().filterIsInstance<LoadedExtension>().flatMap { it.sources }
    }

    /** Looks for extensions if that has not happened yet. */
    suspend fun ensureLoaded() {
        if (_extensions.value == null) refresh()
    }

    /** The source with this ID, once extensions have loaded. Null when its extension is not installed. */
    suspend fun get(sourceId: Long): CatalogueSource? {
        ensureLoaded()
        return _extensions.value.orEmpty()
            .filterIsInstance<LoadedExtension>()
            .firstNotNullOfOrNull { ext -> ext.sources.firstOrNull { it.id == sourceId } }
    }

    /** Looks again, for the refresh button in settings. */
    suspend fun refresh() {
        loading.withLock {
            registerReceiver()
            val previous = _extensions.value.orEmpty().filterIsInstance<LoadedExtension>().associateBy { it.packageName }
            _extensions.value = loader.load(previous)
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = scheduleReload()
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** An update arrives as a removal and an addition in a row, which is one reload. */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(RELOAD_DELAY)
            refresh()
        }
    }

    private companion object {
        val RELOAD_DELAY = 500.milliseconds
    }
}
