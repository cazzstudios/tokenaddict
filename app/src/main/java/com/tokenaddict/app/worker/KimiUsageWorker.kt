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
import com.tokenaddict.app.data.TimeUtils
import com.tokenaddict.app.data.WorkerUtils
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.writeUsagePrefs
import com.google.gson.Gson
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

            val blockingState = WorkerUtils.computeBlockingState(usageInfo)
            WorkerUtils.scheduleOrCancelNotification(blockingState, getNotificationScheduler())

            val (resetTimeMillis, _) = TimeUtils.computeResetState(usageInfo.resetsAt, TAG)
            val (weeklyResetTimeMillis, _) = TimeUtils.computeResetState(usageInfo.weeklyResetsAt, TAG)

            try {
                val prefs: SharedPreferences = applicationContext.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
                prefs.edit()
                    .writeUsagePrefs(usageInfo, resetTimeMillis, weeklyResetTimeMillis)
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
