package com.utility.toolbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.utility.toolbox.MainActivity
import com.utility.toolbox.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles notification forwarding and management for cloned apps.
 * Provides notification grouping and management within workspaces.
 */
@Singleton
class NotificationForwarder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CHANNEL_CLONE_PREFIX = "clone_"
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Create a notification channel for a cloned app.
     */
    fun createCloneNotificationChannel(clonePackage: String, appName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getChannelId(clonePackage)
            val channel = NotificationChannel(
                channelId,
                "$appName (Clone)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from cloned $appName"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Build a notification for a cloned app message.
     */
    fun buildCloneNotification(
        clonePackage: String,
        appName: String,
        title: String,
        message: String,
        notificationId: Int
    ): Notification {
        val channelId = getChannelId(clonePackage)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(clonePackage)
            .build()
    }

    /**
     * Show a notification summary for a group of clone notifications.
     */
    fun showNotificationSummary(clonePackage: String, appName: String, count: Int) {
        val summary = NotificationCompat.Builder(context, getChannelId(clonePackage))
            .setContentTitle("$appName (Clone)")
            .setContentText("$count new notifications")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup(clonePackage)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(
            clonePackage.hashCode(),
            summary
        )
    }

    private fun getChannelId(clonePackage: String): String =
        "$CHANNEL_CLONE_PREFIX$clonePackage"

    /**
     * Delete notification channels when a clone is removed.
     */
    fun removeCloneChannels(clonePackage: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(getChannelId(clonePackage))
        }
    }
}
