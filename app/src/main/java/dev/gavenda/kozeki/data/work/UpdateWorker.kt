package dev.gavenda.kozeki.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.gavenda.kozeki.MainActivity
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.ComicChapterRepository
import dev.gavenda.kozeki.data.comic.UpdateCheckProgress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Asks the sources of the comics being followed for new chapters, so the library can show which
 * have some. It only runs with a connection. Once some comic is due a check, it goes into the
 * foreground with a notification of its progress, so a long library is not cut off part way.
 */
class UpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val chapters: ComicChapterRepository by inject()
    private var canPromote = true

    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        val progress = MutableStateFlow<UpdateCheckProgress?>(null)
        val complete = coroutineScope {
            val notifier = launch {
                progress.filterNotNull().collect { if (it.total > 0) promote(it) }
            }
            chapters.checkForUpdates(onProgress = { progress.value = it }).also { notifier.cancel() }
        }
        // What could not be reached waits for the next period, unless the run got nowhere at all.
        return if (!complete && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    /**
     * Shows [progress] in the foreground notification. The system can refuse a foreground start
     * from the background; the check then goes on as plain background work.
     */
    private suspend fun promote(progress: UpdateCheckProgress) {
        if (!canPromote) return
        try {
            setForeground(foregroundInfo(progress))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not run the update check in the foreground", e)
            canPromote = false
        }
    }

    private fun foregroundInfo(progress: UpdateCheckProgress): ForegroundInfo {
        val context = applicationContext
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_refresh)
            .setContentTitle(context.getString(R.string.update_check_title))
            .setContentText(progress.current)
            .setSubText(
                context.resources.getQuantityString(R.plurals.update_check_count, progress.total, progress.checked, progress.total),
            )
            .setProgress(progress.total, progress.checked, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val TAG = "UpdateWorker"
        private const val UNIQUE_NAME = "check-comic-updates"
        private const val CHANNEL_ID = "chapter-updates"

        /** Apart from [DownloadWorker]'s, so both can show at once. */
        private const val NOTIFICATION_ID = 2

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
    
        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
