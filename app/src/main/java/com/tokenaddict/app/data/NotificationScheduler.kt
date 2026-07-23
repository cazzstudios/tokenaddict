package com.tokenaddict.app.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.tokenaddict.app.R
import com.tokenaddict.app.TokenAddictApplication
import com.tokenaddict.app.receiver.ResetAlarmReceiver
import com.tokenaddict.app.ui.KimiLoginActivity
import com.tokenaddict.app.ui.LoginActivity

class NotificationScheduler(private val context: Context, private val providerId: String = "claude") {

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val NOTIFICATION_ID_CLAUDE = 1001
        private const val NOTIFICATION_ID_KIMI = 1002
        private const val NOTIFICATION_ID_RELOGIN_CLAUDE = 2001
        private const val NOTIFICATION_ID_RELOGIN_KIMI = 2002
        private const val CHANNEL_ID_RELOGIN = "relogin_channel"
        private const val KEY_SCHEDULED_RESET_TIME = "scheduled_reset_time"
        const val PREF_KEY_NOTIFICATION_ENABLED_CLAUDE = "notification_enabled_claude"
        const val PREF_KEY_NOTIFICATION_ENABLED_KIMI = "notification_enabled_kimi"
    }

    private fun getPrefsName(): String = "notification_scheduler_$providerId"
    private fun getAction(): String = "com.tokenaddict.app.RESET_ALARM_${providerId.uppercase()}"
    private fun getNotificationId(): Int = if (providerId == "kimi") NOTIFICATION_ID_KIMI else NOTIFICATION_ID_CLAUDE
    private fun getRequestCode(): Int = getNotificationId()

    private fun getReloginNotificationId(): Int =
        if (providerId == "kimi") NOTIFICATION_ID_RELOGIN_KIMI else NOTIFICATION_ID_RELOGIN_CLAUDE

    private fun getLoginActivityClass(): Class<*> =
        if (providerId == "kimi") KimiLoginActivity::class.java else LoginActivity::class.java

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val prefs = context.getSharedPreferences(getPrefsName(), Context.MODE_PRIVATE)

    private fun isEnabled(): Boolean {
        val key = if (providerId == "kimi") PREF_KEY_NOTIFICATION_ENABLED_KIMI else PREF_KEY_NOTIFICATION_ENABLED_CLAUDE
        val defaultPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return defaultPrefs.getBoolean(key, true)
    }

    fun scheduleResetNotification(resetTimeMillis: Long) {
        if (!isEnabled()) {
            cancelResetNotification()
            return
        }

        val intent = Intent(context, ResetAlarmReceiver::class.java).apply {
            action = getAction()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "scheduleResetNotification: missing SCHEDULE_EXACT_ALARM — using non-exact fallback")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                resetTimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                resetTimeMillis,
                pendingIntent
            )
        }

        prefs.edit().putLong(KEY_SCHEDULED_RESET_TIME, resetTimeMillis).apply()
    }

    fun cancelResetNotification() {
        val intent = Intent(context, ResetAlarmReceiver::class.java).apply {
            action = getAction()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        prefs.edit().remove(KEY_SCHEDULED_RESET_TIME).apply()
    }

    fun hasScheduledNotification(): Boolean {
        val intent = Intent(context, ResetAlarmReceiver::class.java).apply {
            action = getAction()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent != null
    }

    fun getScheduledResetTime(): Long? {
        val time = prefs.getLong(KEY_SCHEDULED_RESET_TIME, -1L)
        return if (time != -1L) time else null
    }

    fun showReloginNotification() {
        if (!isEnabled()) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(CHANNEL_ID_RELOGIN) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_RELOGIN,
                context.getString(R.string.session_expired_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.session_expired_message)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LoginActivity.EXTRA_PROVIDER_ID, providerId)
        }
        when (providerId) {
            "kimi" -> intent.setClass(context, KimiLoginActivity::class.java)
            else -> intent.setClass(context, LoginActivity::class.java)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            getReloginNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RELOGIN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.session_expired_title))
            .setContentText(context.getString(R.string.session_expired_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val managerCompat = NotificationManagerCompat.from(context)
        if (managerCompat.areNotificationsEnabled()) {
            managerCompat.notify(getReloginNotificationId(), notification)
        }
    }
}
