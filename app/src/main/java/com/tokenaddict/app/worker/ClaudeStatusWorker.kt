package com.tokenaddict.app.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.google.gson.Gson
import com.tokenaddict.app.data.ClaudeStatusChecker
import com.tokenaddict.app.data.HttpConfig
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.model.ClaudeStatusLevel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ClaudeStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    internal constructor(
        context: Context,
        params: WorkerParameters,
        statusChecker: ClaudeStatusChecker,
        notificationScheduler: NotificationScheduler
    ) : this(context, params) {
        this.statusChecker = statusChecker
        this.notificationScheduler = notificationScheduler
    }

    private lateinit var statusChecker: ClaudeStatusChecker
    private lateinit var notificationScheduler: NotificationScheduler

    private fun getStatusChecker(): ClaudeStatusChecker {
        if (!::statusChecker.isInitialized) {
            val client = OkHttpClient.Builder()
                .connectTimeout(HttpConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HttpConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HttpConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            statusChecker = ClaudeStatusChecker(client, Gson())
        }
        return statusChecker
    }

    private fun getNotificationScheduler(): NotificationScheduler {
        if (!::notificationScheduler.isInitialized) {
            notificationScheduler = NotificationScheduler(applicationContext, "claude")
        }
        return notificationScheduler
    }

    private fun getStatusPrefs(): SharedPreferences {
        return applicationContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result {
        return try {
            val result = getStatusChecker().checkStatus()
            val previousLevel = getPreviousStatusLevel()
            val currentLevel = result.level

            Log.d(TAG, "Status check: ${currentLevel.name} - ${result.description} (previous: ${previousLevel.name})")

            // Save current status for UI
            saveStatus(result.level, result.description)

            // Handle status transitions
            when {
                // Outage just started
                previousLevel.isOperational && currentLevel.isOutage -> {
                    Log.w(TAG, "Outage detected: ${result.description}")
                    saveOutageStarted(true)
                    scheduleFastPolling()
                }
                // Outage just resolved
                previousLevel.isOutage && currentLevel.isOperational -> {
                    Log.i(TAG, "Outage resolved: ${result.description}")
                    saveOutageStarted(false)
                    cancelFastPolling()
                    getNotificationScheduler().showOutageResolvedNotification(result.description)
                }
                // Still in outage - continue fast polling
                currentLevel.isOutage -> {
                    Log.d(TAG, "Still in outage, continuing fast polling")
                }
                // Still operational - continue normal polling
                else -> {
                    Log.d(TAG, "Operational, normal polling")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Status check failed", e)
            Result.retry()
        }
    }

    private fun getPreviousStatusLevel(): ClaudeStatusLevel {
        val indicator = getStatusPrefs().getString(KEY_STATUS_INDICATOR, "none") ?: "none"
        return ClaudeStatusLevel.fromIndicator(indicator)
    }

    private fun saveStatus(level: ClaudeStatusLevel, description: String) {
        getStatusPrefs().edit()
            .putString(KEY_STATUS_INDICATOR, level.indicator)
            .putString(KEY_STATUS_DESCRIPTION, description)
            .putLong(KEY_STATUS_LAST_CHECKED, System.currentTimeMillis())
            .apply()
    }

    private fun saveOutageStarted(started: Boolean) {
        getStatusPrefs().edit()
            .putBoolean(KEY_OUTAGE_STARTED, started)
            .apply()
    }

    private fun scheduleFastPolling() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ClaudeStatusWorker>(
            FAST_POLLING_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            FAST_POLLING_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Fast polling scheduled: every $FAST_POLLING_INTERVAL_MINUTES minutes")
    }

    private fun cancelFastPolling() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(FAST_POLLING_WORK_NAME)
        Log.d(TAG, "Fast polling cancelled")
    }

    companion object {
        private const val TAG = "ClaudeStatusWorker"
        const val STATUS_PREFS = "claude_status_prefs"
        const val KEY_STATUS_INDICATOR = "status_indicator"
        const val KEY_STATUS_DESCRIPTION = "status_description"
        const val KEY_STATUS_LAST_CHECKED = "status_last_checked"
        const val KEY_OUTAGE_STARTED = "outage_started"
        const val FAST_POLLING_INTERVAL_MINUTES = 5L
        const val FAST_POLLING_WORK_NAME = "claude_status_fast_polling"
        const val NORMAL_POLLING_WORK_NAME = "claude_status_check"
    }
}
