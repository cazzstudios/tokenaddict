package com.tokenaddict.app.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiProvider
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.HttpConfig
import com.tokenaddict.app.data.model.ApiException
import com.google.gson.Gson
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.time.OffsetDateTime

class KimiUsageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // Internal constructor for test injection
    internal constructor(
        context: Context,
        params: WorkerParameters,
        kimiProvider: KimiProvider,
        tokenManager: KimiTokenManager,
        notificationScheduler: NotificationScheduler
    ) : this(context, params) {
        this.kimiProvider = kimiProvider
        this.tokenManager = tokenManager
        this.notificationScheduler = notificationScheduler
    }

    private lateinit var kimiProvider: KimiProvider
    private lateinit var tokenManager: KimiTokenManager
    private lateinit var notificationScheduler: NotificationScheduler

    private fun getKimiProvider(): KimiProvider {
        if (!::kimiProvider.isInitialized) {
            val gson = Gson()
            val tokenMgr = getTokenManager()
            val client = OkHttpClient.Builder()
                .addInterceptor(tokenMgr.createMshInterceptor())
                .connectTimeout(HttpConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HttpConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HttpConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            kimiProvider = KimiProvider(client, gson, tokenMgr)
        }
        return kimiProvider
    }

    private fun getTokenManager(): KimiTokenManager {
        if (!::tokenManager.isInitialized) {
            val gson = Gson()
            val securePrefs = SecurePreferences.create(applicationContext, "kimi_tokens")
            tokenManager = KimiTokenManager(
                securePrefs,
                KimiOAuthManager(OkHttpClient(), gson),
                gson
            )
        }
        return tokenManager
    }

    private fun getNotificationScheduler(): NotificationScheduler {
        if (!::notificationScheduler.isInitialized) {
            notificationScheduler = NotificationScheduler(applicationContext, "kimi")
        }
        return notificationScheduler
    }

    override suspend fun doWork(): Result {
        return executeWork()
    }

    internal suspend fun executeWork(): Result {
        return try {
            getTokenManager().refreshTokenIfNeeded()

            val loggedIn = getKimiProvider().isLoggedIn()
            if (!loggedIn) {
                return Result.failure()
            }

            val usageInfo = getKimiProvider().getUsage()

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
                val prefs: SharedPreferences = applicationContext.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("utilization", utilization.toFloat())
                    .putLong("resets_at", resetTimeMillis)
                    .putBoolean("is_reset", isReset)
                    .putFloat("weekly_utilization", weeklyUtilization.toFloat())
                    .putLong("weekly_resets_at", weeklyResetTimeMillis)
                    .putBoolean("weekly_is_reset", weeklyIsReset)
                    .putLong("last_checked", System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) { Log.e(TAG, "Failed to write kimi usage prefs", e) }

            Result.success()
        } catch (e: ApiException.Unauthorized) {
            Log.e(TAG, "executeWork: Unauthorized – clearing session", e)
            getTokenManager().clearTokens()
            getNotificationScheduler().cancelResetNotification()
            getNotificationScheduler().showReloginNotification()
            Result.failure()
        } catch (e: ApiException.Forbidden) {
            Log.e(TAG, "executeWork: Forbidden – clearing session", e)
            getTokenManager().clearTokens()
            getNotificationScheduler().cancelResetNotification()
            getNotificationScheduler().showReloginNotification()
            Result.failure()
        } catch (e: ApiException.RateLimited) {
            Log.e(TAG, "executeWork: RateLimited", e)
            Result.retry()
        } catch (e: ApiException.NetworkError) {
            Log.e(TAG, "executeWork: NetworkError", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "executeWork: unexpected exception ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "KimiUsageWorker"
    }
}
