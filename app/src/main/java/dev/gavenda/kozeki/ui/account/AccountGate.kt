package dev.gavenda.kozeki.ui.account

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.account.SignInFailure
import dev.gavenda.kozeki.data.sync.SyncFailure
import dev.gavenda.kozeki.data.sync.SyncStatus
import dev.gavenda.kozeki.ui.accountGateViewModel
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * Shows [content], the library, only to someone signed in whose library has been through its first
 * sync. Until then it asks for a sign-in, or to copy the library to the account.
 */
@Composable
fun AccountGate(content: @Composable () -> Unit) {
    val viewModel = koinViewModel { accountGateViewModel() }
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    when (val current = gate) {
        Gate.Open -> content()
        else -> Surface(Modifier.fillMaxSize()) {
            GateScreen(
                gate = current,
                onSignIn = { activity?.let(viewModel::signIn) },
                onCopy = viewModel::copy,
            )
        }
    }
}

@Composable
private fun GateScreen(gate: Gate, onSignIn: () -> Unit, onCopy: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        when (gate) {
            Gate.Loading, Gate.Open -> LoadingIndicator()
            is Gate.SignIn -> SignInPrompt(gate, onSignIn)
            is Gate.FirstSync -> FirstSyncPrompt(gate, onCopy)
        }
    }
}

@Composable
private fun SignInPrompt(gate: Gate.SignIn, onSignIn: () -> Unit) {
    EmptyState(
        expression = if (gate.failure == null) Expression.NEUTRAL else Expression.WORRIED,
        icon = painterResource(R.drawable.login_48px),
        title = stringResource(R.string.account_closed_title),
        message = when (gate.failure) {
            SignInFailure.OFFLINE -> stringResource(R.string.account_sign_in_offline)
            SignInFailure.REJECTED -> stringResource(R.string.account_sign_in_rejected)
            SignInFailure.CANCELLED, null -> stringResource(R.string.account_closed_message)
        },
        action = {
            Button(onClick = onSignIn, enabled = !gate.working) { Text(stringResource(R.string.account_sign_in)) }
        },
    )
}

@Composable
private fun FirstSyncPrompt(gate: Gate.FirstSync, onCopy: () -> Unit) {
    val failed = gate.status as? SyncStatus.Failed
    val failure = failed?.reason
    val running = gate.status as? SyncStatus.Running
    EmptyState(
        expression = when {
            failure != null -> Expression.WORRIED
            gate.started -> Expression.STUDIOUS
            else -> Expression.CURIOUS
        },
        icon = painterResource(R.drawable.cloud_upload_48px),
        title = stringResource(if (gate.books == 0) R.string.migration_fetching_title else R.string.migration_title),
        message = when {
            failed != null -> syncFailureText(failed)
            else -> if (gate.books == 0) {
                stringResource(R.string.migration_fetching_message)
            } else {
                pluralStringResource(R.plurals.migration_message, gate.books, gate.books)
            }
        },
        action = {
            when {
                failure != null -> Button(onClick = onCopy) { Text(stringResource(R.string.action_retry)) }
                !gate.started -> Button(onClick = onCopy) { Text(stringResource(R.string.migration_copy)) }
                else -> Column(
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val total = running?.total ?: 0
                    if (total > 0) {
                        LinearWavyProgressIndicator(progress = { running!!.sent.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.migration_progress, running!!.sent, total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

/** What went wrong with a sync, said plainly: no face is shown with it in Settings. */
@Composable
internal fun syncFailureText(failed: SyncStatus.Failed): String = when {
    failed.reason == SyncFailure.OFFLINE -> stringResource(R.string.sync_error_offline)
    failed.code != null -> stringResource(R.string.sync_error_refused, failed.code.toString())
    else -> stringResource(R.string.sync_error_unreadable)
}

@PreviewLightDark
@Composable
private fun SignInPreview() {
    AppTheme { Surface { GateScreen(Gate.SignIn(working = false, failure = null), {}, {}) } }
}

@PreviewLightDark
@Composable
private fun FirstSyncPreview() {
    AppTheme { Surface { GateScreen(Gate.FirstSync(books = 42, status = SyncStatus.Running(120, 480), started = true), {}, {}) } }
}

@PreviewLightDark
@Composable
private fun FirstSyncAskPreview() {
    AppTheme { Surface { GateScreen(Gate.FirstSync(books = 42, status = SyncStatus.Idle, started = false), {}, {}) } }
}
