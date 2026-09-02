package com.tokenaddict.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tokenaddict.app.security.SecurityChecker
import com.tokenaddict.app.security.SecurityStatus
import com.tokenaddict.app.worker.ChatGPTUsageWorker
import com.tokenaddict.app.worker.KimiUsageWorker
import com.tokenaddict.app.worker.ClaudeUsageWorker
import com.tokenaddict.app.worker.ClaudeStatusWorker
import com.tokenaddict.app.worker.ChatGPTStatusWorker
import java.util.concurrent.TimeUnit

class TokenAddictApplication : Application() {

    companion object {
        const val CHANNEL_ID_RESET = "reset_channel"
        const val CHANNEL_ID_LIMIT = "limit_channel"
        const val CHANNEL_ID_STATUS = "status_channel"
        const val PREF_KEY_POLLING_INTERVAL = "polling_interval"
        const val DEFAULT_POLLING_INTERVAL_MINUTES = 30L
        const val STATUS_POLLING_INTERVAL_MINUTES = 30L
        private const val TAG = "GlobalHandler"
    }

    override fun onCreate() {
        super.onCreate()
        setupGlobalErrorHandler()
        createNotificationChannels()
        schedulePeriodicWork()
        scheduleKimiPeriodicWork()
        scheduleChatGPTPeriodicWork()
        scheduleClaudeStatusWork()
        scheduleChatGPTStatusWork()
        checkSecurityEnvironment()
    }

    private fun setupGlobalErrorHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun checkSecurityEnvironment() {
        when (val status = SecurityChecker.checkEnvironment(this)) {
            is SecurityStatus.Safe -> Log.d(TAG, "Security check: environment is safe")
            is SecurityStatus.Risky -> Log.w(TAG, "Security check: ${status.reasons.joinToString("; ")}")
        }
    }

    private fun createNotificationChannels() {
        val resetChannel = NotificationChannel(
            CHANNEL_ID_RESET,
            getString(R.string.channel_reset_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_reset_description)
        }

        val limitChannel = NotificationChannel(
            CHANNEL_ID_LIMIT,
            getString(R.string.channel_limit_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_limit_description)
        }

        val statusChannel = NotificationChannel(
            CHANNEL_ID_STATUS,
            getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_status_description)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(resetChannel)
        notificationManager.createNotificationChannel(limitChannel)
        notificationManager.createNotificationChannel(statusChannel)
    }

    private fun getPollingIntervalMinutes(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getInt(PREF_KEY_POLLING_INTERVAL, DEFAULT_POLLING_INTERVAL_MINUTES.toInt()).toLong()
            .coerceAtLeast(5)
    }

    private fun schedulePeriodicWork() {
        val intervalMinutes = getPollingIntervalMinutes()
        val workRequest = PeriodicWorkRequestBuilder<ClaudeUsageWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleKimiPeriodicWork() {
        val intervalMinutes = getPollingIntervalMinutes()
        val workRequest = PeriodicWorkRequestBuilder<KimiUsageWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kimi_usage_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleChatGPTPeriodicWork() {
        val intervalMinutes = getPollingIntervalMinutes()
        val workRequest = PeriodicWorkRequestBuilder<ChatGPTUsageWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "chatgpt_usage_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleClaudeStatusWork() {
        val workRequest = PeriodicWorkRequestBuilder<ClaudeStatusWorker>(
            STATUS_POLLING_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ClaudeStatusWorker.NORMAL_POLLING_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleChatGPTStatusWork() {
        val workRequest = PeriodicWorkRequestBuilder<ChatGPTStatusWorker>(
            STATUS_POLLING_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ChatGPTStatusWorker.NORMAL_POLLING_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
