package com.tokenaddict.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tokenaddict.app.data.NotificationScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            rescheduleAlarms(context)
        }
    }

    private fun rescheduleAlarms(context: Context) {
        for (providerId in listOf("claude", "kimi", "chatgpt")) {
            val scheduler = NotificationScheduler(context, providerId)
            if (scheduler.hasScheduledNotification()) {
                val resetTime = scheduler.getScheduledResetTime()
                if (resetTime != null && resetTime > System.currentTimeMillis()) {
                    scheduler.scheduleResetNotification(resetTime)
                }
            }
        }
    }
}
