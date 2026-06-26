package com.tokenaddict.app.receiver

import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tokenaddict.app.TokenAddictApplication
import com.tokenaddict.app.R
import com.tokenaddict.app.data.NotificationMessageProvider
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.ui.MainActivity
import com.tokenaddict.app.worker.ClaudeUsageWorker
import com.tokenaddict.app.worker.KimiUsageWorker

import androidx.preference.PreferenceManager

class ResetAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val NOTIFICATION_ID_CLAUDE = 1001
        private const val NOTIFICATION_ID_KIMI = 1002
        private const val ACTION_SUFFIX_CLAUDE = "RESET_ALARM_CLAUDE"
        private const val ACTION_SUFFIX_KIMI = "RESET_ALARM_KIMI"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: ""
        val providerId = when {
            action.endsWith(ACTION_SUFFIX_CLAUDE) -> "claude"
            action.endsWith(ACTION_SUFFIX_KIMI) -> "kimi"
            else -> "claude"
        }

        val enabledKey = if (providerId == "kimi") {
            NotificationScheduler.PREF_KEY_NOTIFICATION_ENABLED_KIMI
        } else {
            NotificationScheduler.PREF_KEY_NOTIFICATION_ENABLED_CLAUDE
        }
        val notificationsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(enabledKey, true)

        val notificationId = if (providerId == "kimi") NOTIFICATION_ID_KIMI else NOTIFICATION_ID_CLAUDE

        if (notificationsEnabled) {
            val title = context.getString(R.string.notification_reset_title)
            val message = NotificationMessageProvider(context).getResetMessage(providerId)

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val notification = NotificationCompat.Builder(
                context,
                TokenAddictApplication.CHANNEL_ID_RESET
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(notificationId, notification)
            }
        }

        val workRequest = if (providerId == "kimi") {
            OneTimeWorkRequestBuilder<KimiUsageWorker>().build()
        } else {
            OneTimeWorkRequestBuilder<ClaudeUsageWorker>().build()
        }
        WorkManager.getInstance(context).enqueue(workRequest)

        val prefsName = if (providerId == "kimi") "usage_prefs_kimi" else "usage_prefs"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val resetsAt = prefs.getLong("resets_at", 0L)
        val weeklyResetsAt = prefs.getLong("weekly_resets_at", 0L)
        prefs.edit().apply {
            if (resetsAt in 1..now) {
                putBoolean("is_reset", true)
                putFloat("utilization", 0f)
            }
            if (weeklyResetsAt in 1..now) {
                putBoolean("weekly_is_reset", true)
                putFloat("weekly_utilization", 0f)
            }
            apply()
        }
    }
}
