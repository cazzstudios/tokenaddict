package com.tokenaddict.app.data

import android.os.Build
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.model.KimiOAuthTokens
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

class KimiTokenManager(
    private val securePrefs: SecurePreferences,
    private val oauthManager: KimiOAuthManager,
    private val gson: Gson,
    private val migratedDeviceId: String? = null
) {
    companion object {
        private const val KEY_TOKENS = "oauth_tokens"
        private const val KEY_DEVICE_ID = "kimi_device_id"
        private const val TAG = "KimiTokenManager"
        // Proactive refresh buffer: refresh if token expires within this many ms (300s = 5 min)
        private const val EXPIRY_BUFFER_MS = 300_000L
    }

    // ===== Token Storage =====

    fun saveTokens(tokens: KimiOAuthTokens) {
        val json = gson.toJson(tokens)
        securePrefs.putString(KEY_TOKENS, json)
    }

    fun getAccessToken(): String? {
        val tokens = readTokens() ?: return null
        return tokens.accessToken
    }

    fun getRefreshToken(): String? {
        val tokens = readTokens() ?: return null
        return tokens.refreshToken
    }

    fun isTokenExpired(): Boolean {
        val tokens = readTokens() ?: return true
        return System.currentTimeMillis() + EXPIRY_BUFFER_MS >= tokens.expiresAt
    }

    fun isAccessTokenValid(): Boolean {
        val tokens = readTokens() ?: return false
        return System.currentTimeMillis() < tokens.expiresAt
    }

    fun refreshTokenIfNeeded() {
        if (!isTokenExpired()) return

        val refreshToken = getRefreshToken() ?: return

        try {
            val response = oauthManager.refreshAccessToken(refreshToken)
            val accessToken = response.accessToken ?: return
            val newRefreshToken = response.refreshToken ?: refreshToken
            val expiresIn = response.expiresIn ?: 3600L
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

            saveTokens(KimiOAuthTokens(
                accessToken = accessToken,
                refreshToken = newRefreshToken,
                expiresAt = expiresAt
            ))
        } catch (e: ApiException.Unauthorized) {
            throw e
        } catch (e: ApiException.Forbidden) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Token refresh failed: ${e.message}", e)
        }
    }

    fun clearTokens() {
        securePrefs.remove(KEY_TOKENS)
    }

    private fun readTokens(): KimiOAuthTokens? {
        val json = securePrefs.getString(KEY_TOKENS) ?: return null
        return try {
            gson.fromJson(json, KimiOAuthTokens::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ===== Device UUID =====

    fun getDeviceId(): String {
        var deviceId = securePrefs.getString(KEY_DEVICE_ID)
        if (deviceId == null && migratedDeviceId != null) {
            deviceId = migratedDeviceId
            securePrefs.putString(KEY_DEVICE_ID, deviceId)
        }
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            securePrefs.putString(KEY_DEVICE_ID, deviceId)
        }
        return deviceId
    }

    // ===== X-Msh Headers Interceptor =====

    fun createMshInterceptor(): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("X-Msh-Platform", "android")
                .header("X-Msh-Version", "1.0")
                .header("X-Msh-Device-Name", Build.MODEL)
                .header("X-Msh-Device-Model", Build.MODEL)
                .header("X-Msh-Os-Version", Build.VERSION.RELEASE)
                .header("X-Msh-Device-Id", getDeviceId())
                .header("User-Agent", "KimiCLI/1.5")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
    }
}
