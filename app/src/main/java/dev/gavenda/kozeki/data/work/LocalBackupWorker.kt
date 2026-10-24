package dev.gavenda.kozeki.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.gavenda.kozeki.data.backup.local.LocalBackup
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Writes the automatic backup to the folder the user picked. It needs no connection. */
class LocalBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val backup: LocalBackup by inject()

    override suspend fun doWork(): Result {
        val written = backup.backUpOnSchedule()
        // A folder the app has lost stays lost, so trying again the same period does no good past a few times.
        return if (!written && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "local-backup"
        private const val MAX_ATTEMPTS = 3

        /** Backs up every [hours], or stops for 0. A change of period keeps the one running. */
        fun schedule(context: Context, hours: Int) {
            val work = WorkManager.getInstance(context)
            if (hours <= 0) {
                work.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
