package dev.gavenda.kozeki.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.gavenda.kozeki.data.comic.ComicChapterRepository
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Asks the sources of the comics being followed for new chapters, so the library can show which
 * have some. It only runs with a connection.
 */
class UpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val chapters: ComicChapterRepository by inject()

    override suspend fun doWork(): Result {
        val complete = chapters.checkForUpdates()
        // What could not be reached waits for the next period, unless the run got nowhere at all.
        return if (!complete && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "check-comic-updates"

        /** The name the check was scheduled under before comics were called comics, cancelled so it does not run twice. */
        private const val LEGACY_UNIQUE_NAME = "check-manga-updates"
        private const val MAX_ATTEMPTS = 3

        /** Checks every [hours], or stops checking for 0. A change of period keeps the one running. */
        fun schedule(context: Context, hours: Int) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(LEGACY_UNIQUE_NAME)
            if (hours <= 0) {
                work.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
