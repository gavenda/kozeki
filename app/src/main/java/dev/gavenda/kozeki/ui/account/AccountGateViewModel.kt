package dev.gavenda.kozeki.ui.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.account.AccountAuth
import dev.gavenda.kozeki.data.account.AccountState
import dev.gavenda.kozeki.data.account.SignInFailure
import dev.gavenda.kozeki.data.sync.FirstSyncPlan
import dev.gavenda.kozeki.data.sync.SyncEngine
import dev.gavenda.kozeki.data.sync.SyncStatus
import dev.gavenda.kozeki.data.sync.SyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What stands between opening the app and the library. */
sealed interface Gate {
    data object Loading : Gate

    data class SignIn(val working: Boolean, val failure: SignInFailure?) : Gate

    /** The account's library is being brought down, before anything is sent. */
    data class Fetching(val status: SyncStatus) : Gate

    /**
     * The account has books and so does this device, [matched] of them the same. Asks whether to
     * merge the two or keep the account's.
     */
    data class Choose(val accountBooks: Int, val deviceBooks: Int, val matched: Int) : Gate

    /**
     * The first sync with the account has not gone through. [books] is how many books it copies
     * there; with none it starts on its own, and with some it waits to be asked.
     */
    data class FirstSync(val books: Int, val status: SyncStatus, val started: Boolean) : Gate

    data object Open : Gate
}

class AccountGateViewModel(
    private val auth: AccountAuth,
    private val engine: SyncEngine,
    store: SyncStore,
) : ViewModel() {

    private val signIn = MutableStateFlow(Gate.SignIn(working = false, failure = null))
    private val plan = MutableStateFlow<FirstSyncPlan?>(null)
    private val preparing = MutableStateFlow(false)
    private val started = MutableStateFlow(false)
    // Books going up after the question was answered: the ones the account does not have.
    private val copying = MutableStateFlow<Int?>(null)

    val gate: StateFlow<Gate> = combine(
        auth.state, store.owner, store.firstSyncDone, signIn,
        combine(plan, started, copying, engine.status, ::Quad),
    ) { state, owner, done, signIn, (plan, started, copying, status) ->
        when (state) {
            AccountState.Loading -> Gate.Loading
            AccountState.SignedOut -> signIn
            is AccountState.SignedIn -> when {
                owner != state.account.id -> Gate.Loading
                done -> Gate.Open
                plan == null -> Gate.Fetching(status)
                plan.asks && !started -> Gate.Choose(plan.accountBooks, plan.deviceBooks, plan.matched)
                else -> Gate.FirstSync(copying ?: plan.deviceBooks, status, started)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.Loading)

    init {
        viewModelScope.launch {
            combine(auth.state, store.owner, store.firstSyncDone, ::Triple).collect { (state, owner, done) ->
                val account = (state as? AccountState.SignedIn)?.account
                if (account == null) {
                    // Whoever signs in next may have another library, so it is looked at again.
                    plan.value = null
                    started.value = false
                    copying.value = null
                    return@collect
                }
                if (owner != account.id) {
                    engine.adopt(account)
                } else if (!done && plan.value == null) {
                    fetch()
                }
            }
        }
    }

    /** Brings down the account's library, then goes on, or asks, by what both sides hold. */
    fun fetch() {
        if (preparing.value) return
        preparing.value = true
        viewModelScope.launch {
            val found = engine.prepareFirstSync()
            preparing.value = false
            plan.value = found
            // Nothing here, or a sync already under way: nothing to ask, the library just comes down.
            // Otherwise an empty account waits for "Copy books" and a full one for Gate.Choose.
            if (found != null && (found.deviceBooks == 0 || found.sending)) copy()
        }
    }

    fun signIn(activity: Activity) {
        signIn.value = Gate.SignIn(working = true, failure = null)
        viewModelScope.launch {
            val failure = auth.signIn(activity)
            // Closing the browser is not something to complain about.
            signIn.value = Gate.SignIn(working = false, failure = failure?.takeIf { it != SignInFailure.CANCELLED })
        }
    }

    /** Runs the first sync, which copies the library to the account and brings down what is there. */
    fun copy() {
        started.value = true
        viewModelScope.launch { engine.sync() }
    }

    /** This device's books join the account's, the ones it has already made one with its own. */
    fun merge() {
        val found = plan.value ?: return
        copying.value = found.deviceBooks - found.matched
        started.value = true
        viewModelScope.launch {
            engine.mergeWithAccount()
            engine.sync()
        }
    }

    /** The account's library is kept and this device's books give way to it. */
    fun keepAccount() {
        copying.value = 0
        started.value = true
        viewModelScope.launch {
            engine.keepAccountLibrary()
            engine.sync()
        }
    }
}

/** Whether the first sync has something to ask: both sides hold books, and nothing was sent yet. */
private val FirstSyncPlan.asks: Boolean get() = accountBooks > 0 && deviceBooks > 0 && !sending

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
