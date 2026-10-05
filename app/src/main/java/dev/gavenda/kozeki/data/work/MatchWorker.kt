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
import dev.gavenda.kozeki.data.metadata.MatchService
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Looks up metadata for books imported while offline. It only runs with a connection, which is
 * what lets importing itself stay fully offline.
 */
class MatchWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters),
    KoinComponent {

    private val matchService: MatchService by inject()

    override suspend fun doWork(): Result {
        val retry = matchService.matchPending()
        return if (retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "match-pending-books"
        private const val MAX_ATTEMPTS = 6

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<MatchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            // Appending means a book imported while a run is in flight still gets its own run.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
