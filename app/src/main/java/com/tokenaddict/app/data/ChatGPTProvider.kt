package com.tokenaddict.app.data

import android.util.Log
import com.tokenaddict.app.data.model.AccountInfo
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.model.ChatGPTAuthResponse
import com.tokenaddict.app.data.model.ChatGPTUsageResponse
import com.tokenaddict.app.data.model.UsageInfo
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

class ChatGPTProvider(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val sessionManager: ChatGPTSessionManager
) : AiProvider {

    override val id: String = "chatgpt"
    override val displayName: String = "ChatGPT"
    override val baseUrl: String = BASE_URL
    override val loginUrl: String = "https://chat.openai.com"

    companion object {
        private const val BASE_URL = "https://chatgpt.com/backend-api"
        private const val AUTH_URL = "https://chat.openai.com/api/auth/session"
        private const val TAG = "ChatGPTProvider"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val FIVE_HOUR_SECONDS = 18000L
    }

    private fun buildAuthRequest(): Request {
        val accessToken = sessionManager.getAccessToken()
            ?: throw ApiException.Unauthorized("No access token")
        return Request.Builder()
            .url(AUTH_URL)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .build()
    }

    override suspend fun getAccount(): AccountInfo {
        val accessToken = sessionManager.getAccessToken()
            ?: throw ApiException.Unauthorized("No access token")

        val request = buildAuthRequest()
        val authResponse = executeRequest<ChatGPTAuthResponse>(client, gson, request, TAG,
            onParseError = { msg -> ApiException.ServiceChanged("Failed to parse ChatGPT auth response: $msg") }
        )

        val token = authResponse.accessToken
        val accountId = authResponse.accountId
        if (token.isNullOrBlank()) {
            throw ApiException.Unauthorized("No access token in auth response")
        }

        sessionManager.saveCredentials(token, accountId)

        return AccountInfo(
            uuid = accountId ?: "chatgpt-user",
            name = authResponse.email ?: "ChatGPT User",
            email = authResponse.email
        )
    }

    override suspend fun getUsage(): UsageInfo {
        val accessToken = sessionManager.getAccessToken()
            ?: throw ApiException.Unauthorized("No access token")
        val accountId = sessionManager.getAccountId()

        val requestBuilder = Request.Builder()
            .url("$BASE_URL/wham/usage")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")

        if (!accountId.isNullOrBlank()) {
            requestBuilder.addHeader("ChatGPT-Account-Id", accountId)
        }

        val request = requestBuilder.build()

        val usageResponse = executeRequest<ChatGPTUsageResponse>(client, gson, request, TAG,
            onResponse200 = { body ->
                if (body.trimStart().startsWith("<")) {
                    throw ApiException.ServiceChanged("ChatGPT returned HTML instead of JSON")
                }
            },
            onParseError = { msg -> ApiException.ServiceChanged("Failed to parse ChatGPT usage response: $msg") }
        )

        val rateLimit = usageResponse.rateLimit
            ?: throw ApiException.ServiceChanged("No rate_limit in response")

        val primaryWindow = rateLimit.primaryWindow
        val secondaryWindow = rateLimit.secondaryWindow

        val primaryUsedPercent = primaryWindow?.usedPercent ?: 0.0
        val primaryWindowSeconds = primaryWindow?.limitWindowSeconds ?: 0L
        val primaryResetAt = primaryWindow?.resetAt?.let { it * 1000 } ?: 0L

        val secondaryUsedPercent = secondaryWindow?.usedPercent ?: 0.0
        val secondaryWindowSeconds = secondaryWindow?.limitWindowSeconds ?: 0L
        val secondaryResetAt = secondaryWindow?.resetAt?.let { it * 1000 } ?: 0L

        val fiveHourWindow: Double
        val fiveHourResetsAt: String?
        val fiveHourIsReset: Boolean

        val weeklyWindow: Double
        val weeklyResetsAt: String?
        val weeklyIsReset: Boolean

        if (primaryWindowSeconds == FIVE_HOUR_SECONDS) {
            fiveHourWindow = primaryUsedPercent
            fiveHourResetsAt = primaryResetAt.let { formatResetTime(it) }
            fiveHourIsReset = primaryUsedPercent <= 0.0

            weeklyWindow = secondaryUsedPercent
            weeklyResetsAt = secondaryResetAt.let { formatResetTime(it) }
            weeklyIsReset = secondaryUsedPercent <= 0.0
        } else {
            fiveHourWindow = secondaryUsedPercent
            fiveHourResetsAt = secondaryResetAt.let { formatResetTime(it) }
            fiveHourIsReset = secondaryUsedPercent <= 0.0

            weeklyWindow = primaryUsedPercent
            weeklyResetsAt = primaryResetAt.let { formatResetTime(it) }
            weeklyIsReset = primaryUsedPercent <= 0.0
        }

        val isReset = fiveHourIsReset && weeklyIsReset

        return UsageInfo(
            utilization = fiveHourWindow,
            resetsAt = fiveHourResetsAt,
            isReset = isReset,
            providerId = "chatgpt",
            weeklyUtilization = weeklyWindow,
            weeklyResetsAt = weeklyResetsAt,
            weeklyIsReset = weeklyIsReset
        )
    }

    private fun formatResetTime(millis: Long): String? {
        if (millis <= 0) return null
        return java.time.Instant.ofEpochMilli(millis).toString()
    }

    override suspend fun isLoggedIn(): Boolean {
        val token = sessionManager.getAccessToken() ?: return false
        return try {
            val request = buildAuthRequest()
            val authResponse = executeRequest<ChatGPTAuthResponse>(client, gson, request, TAG)
            val newToken = authResponse.accessToken
            if (!newToken.isNullOrBlank()) {
                sessionManager.saveCredentials(newToken, authResponse.accountId)
                true
            } else {
                false
            }
        } catch (e: ApiException.Unauthorized) {
            false
        } catch (e: ApiException.Forbidden) {
            false
        } catch (e: ApiException.ServiceChanged) {
            false
        }
    }

    override fun logout() {
        sessionManager.clearSession()
    }

    private fun String.looksLikeHtml(): Boolean {
        val trimmed = trim()
        return trimmed.startsWith("<", ignoreCase = true) ||
                trimmed.contains("<html", ignoreCase = true) ||
                trimmed.contains("cloudflare", ignoreCase = true) ||
                trimmed.contains("<!doctype", ignoreCase = true)
    }
}
