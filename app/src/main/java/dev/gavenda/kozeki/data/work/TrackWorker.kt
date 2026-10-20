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
import dev.gavenda.kozeki.data.tracking.TrackRepository
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Tells AniList the chapters read while it could not be reached. It only runs with a connection. */
class TrackWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val tracker: TrackRepository by inject()

    override suspend fun doWork(): Result {
        val waiting = tracker.retryPending()
        return if (waiting && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "push-tracked-comics"
        private const val MAX_ATTEMPTS = 6

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrackWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            // One run at a time is enough: it sends everything that waits.
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
