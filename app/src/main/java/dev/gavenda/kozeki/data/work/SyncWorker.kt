package dev.gavenda.kozeki.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.gavenda.kozeki.data.account.AccountAuth
import dev.gavenda.kozeki.data.account.AccountState
import dev.gavenda.kozeki.data.sync.SyncEngine
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Syncs once there is a connection, trying again later while it does not get through. */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters), KoinComponent {

    private val engine: SyncEngine by inject()
    private val auth: AccountAuth by inject()

    // Signed out, there is nothing to sync until a sign-in, which syncs on its own.
    override suspend fun doWork(): Result = when {
        auth.state.value !is AccountState.SignedIn -> Result.success()
        engine.sync() -> Result.success()
        else -> Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "sync"

        /** Queues a sync for when the device is online. One already queued is kept. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
