package com.tokenaddict.app.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tokenaddict.app.data.ClaudeProvider
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.SessionManager
import com.tokenaddict.app.data.HttpConfig
import com.tokenaddict.app.data.WebViewCookieJar
import okhttp3.OkHttpClient
import com.google.gson.Gson
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.time.OffsetDateTime

class ClaudeUsageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    internal constructor(
        context: Context,
        params: WorkerParameters,
        sessionManager: SessionManager,
        apiClient: ClaudeProvider,
        notificationScheduler: NotificationScheduler
    ) : this(context, params) {
        this.sessionManager = sessionManager
        this.apiClient = apiClient
        this.notificationScheduler = notificationScheduler
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ClaudeProvider
    private lateinit var notificationScheduler: NotificationScheduler

    private fun getSessionManager(): SessionManager {
        if (!::sessionManager.isInitialized) {
            sessionManager = SessionManager(applicationContext, "claude")
        }
        return sessionManager
    }

    private fun getApiClient(): ClaudeProvider {
        if (!::apiClient.isInitialized) {
            val client = OkHttpClient.Builder()
                .cookieJar(WebViewCookieJar())
                .connectTimeout(HttpConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HttpConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HttpConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            apiClient = ClaudeProvider(client, Gson())
        }
        return apiClient
    }

    private fun getNotificationScheduler(): NotificationScheduler {
        if (!::notificationScheduler.isInitialized) {
            notificationScheduler = NotificationScheduler(applicationContext, "claude")
        }
        return notificationScheduler
    }

    override suspend fun doWork(): Result {
        getSessionManager().restoreSession("https://claude.ai")
        return executeWork()
    }

    internal suspend fun executeWork(): Result {
        val loggedIn = getSessionManager().isLoggedIn()
        if (!loggedIn) {
            return Result.failure()
        }

        return try {
            val usageInfo = getApiClient().getUsage()

            val utilization = usageInfo.utilization
            val resetTimeStr = usageInfo.resetsAt
            val isReset = usageInfo.isReset

            val weeklyUtilization = usageInfo.weeklyUtilization
            val weeklyResetsAtStr = usageInfo.weeklyResetsAt
            val weeklyIsReset = usageInfo.weeklyIsReset

            val now = Instant.now()

            var resetTimeMillis = 0L
            val fiveHourResetInstant = resetTimeStr?.let {
                try {
                    OffsetDateTime.parse(it).toInstant().also { instant ->
                        resetTimeMillis = instant.toEpochMilli()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse reset time: $it", e)
                    null
                }
            }

            var weeklyResetTimeMillis = 0L
            val weeklyResetInstant = weeklyResetsAtStr?.let {
                try {
                    OffsetDateTime.parse(it).toInstant().also { instant ->
                        weeklyResetTimeMillis = instant.toEpochMilli()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse weekly reset time: $weeklyResetsAtStr", e)
                    null
                }
            }

            val isFiveHourLimitReached = utilization >= 100.0
            val isWeeklyLimitReached = weeklyUtilization >= 100.0

            val fiveHourBlocking = isFiveHourLimitReached && fiveHourResetInstant?.isAfter(now) == true
            val weeklyBlocking = isWeeklyLimitReached && weeklyResetInstant?.isAfter(now) == true

            if (fiveHourBlocking || weeklyBlocking) {
                val effectiveResetMillis = maxOf(
                    if (fiveHourBlocking) fiveHourResetInstant!!.toEpochMilli() else Long.MIN_VALUE,
                    if (weeklyBlocking) weeklyResetInstant!!.toEpochMilli() else Long.MIN_VALUE
                )
                getNotificationScheduler().scheduleResetNotification(effectiveResetMillis)
            } else {
                getNotificationScheduler().cancelResetNotification()
            }

            try {
                val prefs: SharedPreferences = applicationContext.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("utilization", utilization.toFloat())
                    .putLong("resets_at", resetTimeMillis)
                    .putBoolean("is_reset", isReset)
                    .putFloat("weekly_utilization", weeklyUtilization.toFloat())
                    .putLong("weekly_resets_at", weeklyResetTimeMillis)
                    .putBoolean("weekly_is_reset", weeklyIsReset)
                    .putLong("last_checked", System.currentTimeMillis())
                    .putBoolean("claude_service_changed", false)
                    .apply()
            } catch (e: Exception) { Log.e(TAG, "Failed to write usage prefs", e) }

            Result.success()
        } catch (e: ApiException.Unauthorized) {
            Log.e(TAG, "executeWork: Unauthorized – clearing session", e)
            getSessionManager().clearSession()
            getNotificationScheduler().cancelResetNotification()
            getNotificationScheduler().showReloginNotification()
            try {
                applicationContext.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("claude_service_changed", false).apply()
            } catch (e: Exception) { Log.w(TAG, "Failed to set claude_service_changed flag after 401", e) }
            Result.failure()
        } catch (e: ApiException.Forbidden) {
            Log.e(TAG, "executeWork: Forbidden – clearing session", e)
            getSessionManager().clearSession()
            getNotificationScheduler().cancelResetNotification()
            getNotificationScheduler().showReloginNotification()
            try {
                applicationContext.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("claude_service_changed", false).apply()
            } catch (e: Exception) { Log.w(TAG, "Failed to set claude_service_changed flag after 403", e) }
            Result.failure()
        } catch (e: ApiException.ServiceChanged) {
            Log.e(TAG, "executeWork: ServiceChanged – Claude API may have changed", e)
            getNotificationScheduler().cancelResetNotification()
            try {
                applicationContext.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("claude_service_changed", true).apply()
            } catch (e: Exception) { Log.w(TAG, "Failed to set claude_service_changed flag after ServiceChanged", e) }
            Result.failure()
        } catch (e: ApiException.NetworkError) {
            Log.e(TAG, "executeWork: NetworkError", e)
            Result.retry()
        } catch (e: ApiException.RateLimited) {
            Log.e(TAG, "executeWork: RateLimited", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "executeWork: unexpected exception ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ClaudeUsageWorker"
    }
}
