package dev.gavenda.kozeki.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.gavenda.kozeki.MainActivity
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.ChapterDownload
import dev.gavenda.kozeki.data.comic.ComicDownloads
import dev.gavenda.kozeki.data.comic.QueuedDownload
import dev.gavenda.kozeki.data.db.KozekiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the app in the foreground, with a notification of the chapter coming in, while the
 * download queue has chapters waiting or coming in. The chapters are fetched by [ComicDownloads];
 * this only stops the system from ending the app in the middle of a long queue.
 */
class DownloadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val downloads: ComicDownloads by inject()
    private val db: KozekiDatabase by inject()
    private val titles = mutableMapOf<String, String>()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createChannel(applicationContext)
        return foregroundInfo(downloads.queued.first())
    }

    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        downloads.queued
            .takeWhile { queue -> queue.any { it.isActive } }
            .collect { setForeground(foregroundInfo(it)) }
        return Result.success()
    }

    private suspend fun foregroundInfo(queue: List<QueuedDownload>): ForegroundInfo {
        val context = applicationContext
        val current = queue.firstOrNull { it.state is ChapterDownload.Downloading }
            ?: queue.firstOrNull { it.state is ChapterDownload.Queued }
        val left = queue.count { it.isActive }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openQueue(context))
            .setSubText(context.resources.getQuantityString(R.plurals.downloads_notification_left, left, left))
        if (current == null) {
            builder.setContentTitle(context.getString(R.string.comic_download_running)).setProgress(0, 0, true)
        } else {
            builder.setContentTitle(title(current.bookId)).setContentText(current.chapter.name)
            val state = current.state as? ChapterDownload.Downloading
            val total = state?.total
            if (state != null && total != null && total > 0) {
                builder.setProgress(total, state.done, false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }
        return ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun title(bookId: String): String =
        titles[bookId] ?: (db.bookDao().get(bookId)?.title ?: "").also { titles[bookId] = it }

    private val QueuedDownload.isActive: Boolean
        get() = state is ChapterDownload.Queued || state is ChapterDownload.Downloading

    companion object {
        private const val UNIQUE_NAME = "comic-downloads"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        /** The action of the intent that opens the download queue. */
        const val ACTION_OPEN_QUEUE = "dev.gavenda.kozeki.action.OPEN_DOWNLOAD_QUEUE"

        /**
         * Starts the worker, unless one is running already. One that is just finishing has this one
         * follow it, so a chapter queued as the last one ended still gets a notification.
         */
        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun openQueue(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_QUEUE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
