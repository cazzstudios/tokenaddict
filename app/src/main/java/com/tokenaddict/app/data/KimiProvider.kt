package com.tokenaddict.app.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.tokenaddict.app.data.model.AccountInfo
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.model.KimiRateLimit
import com.tokenaddict.app.data.model.KimiUsageResponse
import com.tokenaddict.app.data.model.UsageInfo
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime

open class KimiProvider(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val tokenManager: KimiTokenManager
) : AiProvider {

    override val id: String = "kimi"
    override val displayName: String = "Kimi"
    override val baseUrl: String = BASE_URL
    override val loginUrl: String = "https://auth.kimi.com"

    companion object {
        private const val BASE_URL = "https://api.kimi.com/coding/v1"
        private const val TAG = "KimiProvider"
    }

    override suspend fun getAccount(): AccountInfo {
        val response = fetchUsageResponse()
        val membershipLevel = response.user?.membership?.level
        return AccountInfo(
            uuid = "kimi-${tokenManager.getDeviceId()}",
            name = "Kimi ${membershipLevel ?: "user"}",
            email = null
        )
    }

    override suspend fun getUsage(): UsageInfo {
        val response = fetchUsageResponse()
        val usage = response.usage

        // --- Weekly fields from response.usage ---
        val weeklyUtilization = parseUtilization(usage?.limit, usage?.remaining, usage?.used)
        val weeklyResetsAt = usage?.resetTime ?: response.resetTime
        var weeklyIsReset = false
        if (weeklyResetsAt != null) {
            try {
                val resetTime = OffsetDateTime.parse(weeklyResetsAt).toInstant()
                if (resetTime.isBefore(java.time.Instant.now())) {
                    weeklyIsReset = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse weekly resetTime: $weeklyResetsAt", e)
            }
        }

        // --- 5-hour fields from limits[] entry with duration==300, timeUnit contains "MINUTE" ---
        val fiveHourLimit = findFiveHourLimit(response.limits)
        val fiveHourDetail = fiveHourLimit?.detail

        val utilization: Double
        val resetsAt: String?
        var isReset = false

        if (fiveHourDetail != null) {
            utilization = parseUtilization(fiveHourDetail.limit, fiveHourDetail.remaining, fiveHourDetail.used)
            resetsAt = fiveHourDetail.resetTime
        } else if (fiveHourLimit != null) {
            // Fallback to flat fields on KimiRateLimit (Int?)
            val flatLimit = fiveHourLimit.limit?.toLong()
            val flatRemaining = fiveHourLimit.remaining?.toLong()
            utilization = if (flatLimit != null && flatRemaining != null && flatLimit > 0) {
                ((flatLimit - flatRemaining).toDouble() / flatLimit.toDouble()) * 100.0
            } else 0.0
            resetsAt = null
        } else {
            utilization = 0.0
            resetsAt = null
        }

        if (resetsAt != null) {
            try {
                val resetTime = OffsetDateTime.parse(resetsAt).toInstant()
                if (resetTime.isBefore(java.time.Instant.now())) {
                    isReset = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse 5-hour resetTime: $resetsAt", e)
            }
        }

        return UsageInfo(
            utilization = utilization,
            resetsAt = resetsAt,
            isReset = isReset,
            providerId = "kimi",
            weeklyUtilization = weeklyUtilization,
            weeklyResetsAt = weeklyResetsAt,
            weeklyIsReset = weeklyIsReset
        )
    }

    private fun findFiveHourLimit(limits: List<KimiRateLimit>?): KimiRateLimit? {
        return limits?.firstOrNull { rateLimit ->
            val duration = rateLimit.duration ?: rateLimit.window?.duration
            val timeUnit = rateLimit.timeUnit ?: rateLimit.window?.timeUnit
            duration == 300 && timeUnit?.contains("MINUTE", ignoreCase = true) == true
        }
    }

    private fun parseUtilization(limitStr: String?, remainingStr: String?, usedStr: String? = null): Double {
        val limit = limitStr?.toLongOrNull()
        val remaining = remainingStr?.toLongOrNull()
        if (limit != null && remaining != null && limit > 0) {
            return ((limit - remaining).toDouble() / limit.toDouble()) * 100.0
        }
        val used = usedStr?.toLongOrNull()
        if (limit != null && used != null && limit > 0) {
            return (used.toDouble() / limit.toDouble()) * 100.0
        }
        return 0.0
    }

    override suspend fun isLoggedIn(): Boolean {
        return tokenManager.getAccessToken() != null && tokenManager.isAccessTokenValid()
    }

    override fun logout() {
        tokenManager.clearTokens()
    }

    @VisibleForTesting
    internal open fun fetchUsageResponse(): KimiUsageResponse {
        tokenManager.refreshTokenIfNeeded()

        val accessToken = tokenManager.getAccessToken()
            ?: throw ApiException.Unauthorized("No access token")

        val request = Request.Builder()
            .url("$BASE_URL/usages")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw ApiException.ParseError("Empty response body")
        Log.d(TAG, "fetchUsageResponse: HTTP ${response.code}, body=$body")

        if (response.code != 200) {
            when (response.code) {
                401 -> throw ApiException.Unauthorized()
                403 -> throw ApiException.Forbidden()
                429 -> throw ApiException.RateLimited()
                else -> throw ApiException.NetworkError("HTTP ${response.code}")
            }
        }

        return try {
            gson.fromJson(body, KimiUsageResponse::class.java)
        } catch (e: Exception) {
            throw ApiException.ParseError(e.message ?: "Parse error")
        }
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw ApiException.ParseError("Empty response body")

            when (response.code) {
                200 -> {
                    return try {
                        gson.fromJson(body, T::class.java)
                    } catch (e: Exception) {
                        throw ApiException.ParseError(e.message ?: "Parse error")
                    }
                }
                401 -> throw ApiException.Unauthorized()
                403 -> throw ApiException.Forbidden()
                429 -> {
                    Log.w(TAG, "Rate limited. Response body: $body")
                    throw ApiException.RateLimited()
                }
                else -> throw ApiException.NetworkError("HTTP ${response.code}")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "executeRequest: API exception ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } catch (e: java.io.IOException) {
            Log.e(TAG, "executeRequest: IOException: ${e.message}", e)
            throw ApiException.NetworkError(e.message ?: "Unknown network error")
        }
    }
}
