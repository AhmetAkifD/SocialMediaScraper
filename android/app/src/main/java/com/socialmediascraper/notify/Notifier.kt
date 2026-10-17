package com.socialmediascraper.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.socialmediascraper.MainActivity
import com.socialmediascraper.R
import com.socialmediascraper.data.Post

object Notifier {
    private const val CHANNEL_ID = "new_posts"
    private const val GROUP_KEY = "com.socialmediascraper.NEW_POSTS"
    private const val SUMMARY_ID = 0
    private const val MAX_INDIVIDUAL = 5

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Yeni gönderiler", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Takip edilen hesaplar yeni bir gönderi paylaştığında"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showNewPosts(context: Context, posts: List<Post>) {
        if (!canNotify(context)) return
        val nm = NotificationManagerCompat.from(context)

        posts.take(MAX_INDIVIDUAL).forEach { post ->
            val openPost = PendingIntent.getActivity(
                context,
                post.id.hashCode(),
                Intent(Intent.ACTION_VIEW, Uri.parse(post.url)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${post.authorName} (@${post.authorHandle})")
                .setContentText(post.text.ifBlank { "Yeni gönderi" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(post.text.ifBlank { "Yeni gönderi" }))
                .setWhen(post.createdAt)
                .setShowWhen(true)
                .setContentIntent(openPost)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .build()
            notify(nm, post.id.hashCode(), notification)
        }

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val accounts = posts.map { "@${it.authorHandle}" }.distinct().joinToString(", ")
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${posts.size} yeni gönderi")
            .setContentText(accounts)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .build()
        notify(nm, SUMMARY_ID, summary)
    }

    @Suppress("MissingPermission") // canNotify() ile kontrol ediliyor
    private fun notify(nm: NotificationManagerCompat, id: Int, notification: android.app.Notification) {
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
