package com.tokenaddict.app.data

import android.util.Log
import com.tokenaddict.app.data.model.ApiException
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Shared inline HTTP executor. Handles status code → exception mapping,
 * JSON parsing, and IOException wrapping. Configurable via lambda parameters
 * for provider-specific behavior.
 *
 * @param onResponse200 Optional callback invoked on 200 responses (before JSON parse).
 *                      Used by ClaudeProvider to inject looksLikeHtml() check.
 *                      If it throws ServiceChanged, it propagates.
 * @param onParseError Factory for parse error exceptions.
 *                      ClaudeProvider: `{ msg -> ApiException.ServiceChanged(...) }`
 *                      KimiProvider: uses default `{ msg -> ApiException.ParseError(msg) }`
 * @param onRateLimited Optional callback invoked when 429 is received.
 *                      KimiProvider uses: `{ body -> Log.w(TAG, "Rate limited. Response body: $body") }`
 */
inline fun <reified T> executeRequest(
    client: OkHttpClient,
    gson: Gson,
    request: Request,
    tag: String,
    noinline onResponse200: ((String) -> Unit)? = null,
    onParseError: (String) -> ApiException = { msg -> ApiException.ParseError(msg) },
    noinline onRateLimited: ((String) -> Unit)? = null
): T {
    try {
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw ApiException.ParseError("Empty response body")
        Log.d(tag, "HTTP ${response.code}, body=$body")

        when (response.code) {
            200 -> {
                onResponse200?.invoke(body)
                return try {
                    gson.fromJson(body, T::class.java)
                } catch (e: Exception) {
                    throw onParseError(e.message ?: "Parse error")
                }
            }
            401 -> throw ApiException.Unauthorized()
            403 -> throw ApiException.Forbidden()
            429 -> {
                onRateLimited?.invoke(body)
                throw ApiException.RateLimited()
            }
            else -> throw ApiException.NetworkError("HTTP ${response.code}")
        }
    } catch (e: ApiException) {
        Log.e(tag, "API exception ${e.javaClass.simpleName}: ${e.message}", e)
        throw e
    } catch (e: IOException) {
        Log.e(tag, "IOException: ${e.message}", e)
        throw ApiException.NetworkError(e.message ?: "Unknown network error")
    }
}
