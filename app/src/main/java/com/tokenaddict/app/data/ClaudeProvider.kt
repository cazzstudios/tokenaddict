package com.tokenaddict.app.data

import android.util.Log
import com.tokenaddict.app.data.model.AccountInfo
import com.tokenaddict.app.data.model.AccountResponse
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.model.UsageInfo
import com.tokenaddict.app.data.model.UsageResponse
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime

class ClaudeProvider(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val apiBaseUrl: String = BASE_URL
) : AiProvider {

    override val id: String = "claude"
    override val displayName: String = "Claude"
    override val baseUrl: String = apiBaseUrl
    override val loginUrl: String = "https://claude.ai/"

    companion object {
        private const val BASE_URL = "https://claude.ai/api"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val REFERER = "https://claude.ai/settings/usage"
        private const val ACCEPT = "application/json"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val SEC_FETCH_DEST = "empty"
        private const val SEC_FETCH_MODE = "cors"
        private const val SEC_FETCH_SITE = "same-origin"
        private const val X_REQUESTED_WITH = "XMLHttpRequest"
        private const val TAG = "ClaudeProvider"
    }

    private fun Request.Builder.addCommonHeaders(): Request.Builder {
        return this
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", REFERER)
            .addHeader("Accept", ACCEPT)
            .addHeader("Accept-Language", ACCEPT_LANGUAGE)
            .addHeader("Sec-Fetch-Dest", SEC_FETCH_DEST)
            .addHeader("Sec-Fetch-Mode", SEC_FETCH_MODE)
            .addHeader("Sec-Fetch-Site", SEC_FETCH_SITE)
            .addHeader("X-Requested-With", X_REQUESTED_WITH)
    }

    private fun fetchAccountResponse(): AccountResponse {
        val request = Request.Builder()
            .url("$apiBaseUrl/account")
            .addCommonHeaders()
            .build()
        return executeRequest<AccountResponse>(request)
    }

    suspend fun validateSession(): AccountInfo {
        val accountResponse = fetchAccountResponse()
        return AccountInfo(
            uuid = accountResponse.uuid ?: "",
            name = accountResponse.display_name ?: accountResponse.full_name ?: "",
            email = accountResponse.email_address
        )
    }

    override suspend fun getAccount(): AccountInfo = validateSession()

    override suspend fun getUsage(): UsageInfo {
        val accountResponse = fetchAccountResponse()
        val orgUuid = accountResponse.memberships?.firstOrNull()?.organization?.uuid
            ?: throw ApiException.NetworkError("No organization found")

        val request = Request.Builder()
            .url("$apiBaseUrl/organizations/$orgUuid/usage")
            .addCommonHeaders()
            .build()

        val usageResponse = executeRequest<UsageResponse>(request)
        val utilization = usageResponse.fiveHour?.utilization ?: 0.0
        val resetsAtStr = usageResponse.fiveHour?.resetsAt

        var isReset = false
        if (resetsAtStr != null) {
            val resetTime = OffsetDateTime.parse(resetsAtStr).toInstant()
            if (resetTime.isAfter(java.time.Instant.now())) {
            } else {
                isReset = true
            }
        }

        val weeklyUtilization = usageResponse.sevenDay?.utilization ?: 0.0
        val weeklyResetsAtStr = usageResponse.sevenDay?.resetsAt

        var weeklyIsReset = false
        if (weeklyResetsAtStr != null) {
            val weeklyResetTime = OffsetDateTime.parse(weeklyResetsAtStr).toInstant()
            if (weeklyResetTime.isAfter(java.time.Instant.now())) {
            } else {
                weeklyIsReset = true
            }
        }

        return UsageInfo(
            utilization = utilization,
            resetsAt = resetsAtStr,
            isReset = isReset,
            weeklyUtilization = weeklyUtilization,
            weeklyResetsAt = weeklyResetsAtStr,
            weeklyIsReset = weeklyIsReset,
            providerId = "claude"
        )
    }

    private fun String.looksLikeHtml(): Boolean {
        val trimmed = trim()
        return trimmed.startsWith("<", ignoreCase = true) ||
                trimmed.contains("<html", ignoreCase = true) ||
                trimmed.contains("cloudflare", ignoreCase = true) ||
                trimmed.contains("<!doctype", ignoreCase = true)
    }

    override suspend fun isLoggedIn(): Boolean {
        return try {
            validateSession()
            true
        } catch (e: ApiException.Unauthorized) {
            false
        } catch (e: ApiException.Forbidden) {
            false
        } catch (e: ApiException.ServiceChanged) {
            false
        }
    }

    override fun logout() {
        // Delegated to SessionManager
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw ApiException.ParseError("Empty response body")

            when (response.code) {
                200 -> {
                    if (body.looksLikeHtml()) {
                        throw ApiException.ServiceChanged("Claude returned HTML instead of JSON")
                    }
                    return try {
                        gson.fromJson(body, T::class.java)
                    } catch (e: Exception) {
                        throw ApiException.ServiceChanged("Failed to parse Claude response: ${e.message}")
                    }
                }
                401 -> throw ApiException.Unauthorized()
                403 -> throw ApiException.Forbidden()
                429 -> throw ApiException.RateLimited()
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
