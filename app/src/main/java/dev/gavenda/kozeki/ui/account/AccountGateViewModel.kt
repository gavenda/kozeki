package dev.gavenda.kozeki.ui.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.account.AccountAuth
import dev.gavenda.kozeki.data.account.AccountState
import dev.gavenda.kozeki.data.account.SignInFailure
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
    private val books = MutableStateFlow<Int?>(null)
    private val started = MutableStateFlow(false)

    val gate: StateFlow<Gate> = combine(
        auth.state, store.owner, store.firstSyncDone, signIn,
        combine(books, started, engine.status, ::Triple),
    ) { state, owner, done, signIn, (books, started, status) ->
        when (state) {
            AccountState.Loading -> Gate.Loading
            AccountState.SignedOut -> signIn
            is AccountState.SignedIn -> when {
                owner != state.account.id -> Gate.Loading
                done -> Gate.Open
                books == null -> Gate.Loading
                else -> Gate.FirstSync(books, status, started)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.Loading)

    init {
        viewModelScope.launch {
            combine(auth.state, store.owner, store.firstSyncDone, ::Triple).collect { (state, owner, done) ->
                val account = (state as? AccountState.SignedIn)?.account
                if (account == null) {
                    // Whoever signs in next may have another library, so its count is asked again.
                    books.value = null
                    started.value = false
                    return@collect
                }
                if (owner != account.id) {
                    engine.adopt(account)
                } else if (!done && books.value == null) {
                    val count = engine.booksToCopy()
                    books.value = count
                    // Nothing to copy, so nothing to ask: the library just comes down.
                    if (count == 0) copy()
                }
            }
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
}
