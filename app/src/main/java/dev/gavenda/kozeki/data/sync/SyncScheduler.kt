package dev.gavenda.kozeki.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.gavenda.kozeki.data.account.AccountAuth
import dev.gavenda.kozeki.data.account.AccountState
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides when to sync, once the first sync has gone through: shortly after the library changes,
 * whenever the app comes to the front (for what changed on the web meanwhile), and through
 * [SyncWorker] whenever one of those could not get through.
 */
@OptIn(FlowPreview::class)
class SyncScheduler(
    private val engine: SyncEngine,
    private val db: KozekiDatabase,
    private val store: SyncStore,
    private val settings: SettingsRepository,
    private val auth: AccountAuth,
    private val scope: CoroutineScope,
    private val retryLater: () -> Unit,
) {
    private val active = combine(auth.state, store.firstSyncDone) { state, done -> state is AccountState.SignedIn && done }
        .distinctUntilChanged()

    fun start() {
        scope.launch {
            active.collectLatest { on ->
                if (!on) return@collectLatest
                requestSync()
                merge(
                    db.invalidationTracker.createFlow(*SYNCED_TABLES, emitInitialState = false).map { },
                    settings.dailyGoalMinutes.drop(1).map { },
                    settings.lastCurrency.drop(1).map { },
                )
                    .debounce(EDIT_DEBOUNCE_MS)
                    .collect { if (engine.hasUnsent()) requestSync() }
            }
        }
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { if (activeNow()) requestSync() }
                }
            })
        }
    }

    private suspend fun activeNow(): Boolean = active.first()

    /** Syncs now, leaving it to [SyncWorker] if that does not get through. */
    fun requestSync() {
        scope.launch { if (!engine.sync()) retryLater() }
    }

    private companion object {
        const val EDIT_DEBOUNCE_MS = 3_000L
        val SYNCED_TABLES = arrayOf(
            "books", "read_throughs", "reading_sessions", "physical_readings", "notes", "yearly_goals", "shelves", "shelf_books",
        )
    }
}
