package com.socialmediascraper.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.socialmediascraper.data.AppSettings
import com.socialmediascraper.data.Repository
import com.socialmediascraper.notify.Notifier
import java.util.concurrent.TimeUnit

/** Arka planda periyodik olarak tüm hesapları tarar ve yeni gönderiler için bildirim gösterir. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        if (repo.accounts.value.isEmpty()) return Result.success()
        val newPosts = repo.refreshAll()
        if (repo.settings.value.notificationsEnabled && newPosts.isNotEmpty()) {
            Notifier.showNewPosts(applicationContext, newPosts)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "refresh_posts"

        /** [replace] false ise mevcut zamanlama korunur (uygulama her açıldığında sıfırlanmaz). */
        fun schedule(context: Context, settings: AppSettings, replace: Boolean = true) {
            val wm = WorkManager.getInstance(context)
            if (!settings.backgroundEnabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                settings.intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES,
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            val policy = if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            wm.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }
}
