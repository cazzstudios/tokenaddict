package com.tokenaddict.app.data

import com.tokenaddict.app.data.model.ApiException
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class KimiOAuthManager(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val AUTH_BASE_URL = "https://auth.kimi.com/api/oauth"
        private const val CLIENT_ID = "17e5f671-d194-4dfb-9706-5516cb48c098"
        private const val TAG = "KimiOAuthManager"
    }

    // Response from device_authorization endpoint
    data class DeviceCodeResponse(
        @SerializedName("device_code") val deviceCode: String,
        @SerializedName("user_code") val userCode: String,
        @SerializedName("verification_uri") val verificationUri: String,
        @SerializedName("verification_uri_complete") val verificationUriComplete: String?,
        @SerializedName("expires_in") val expiresIn: Int
    )

    // Response from polling token endpoint
    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long?,
        @SerializedName("token_type") val tokenType: String?,
        val error: String?
    )

    /**
     * Step 1: Request a device code from the authorization server.
     */
    fun requestDeviceCode(): DeviceCodeResponse {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url("$AUTH_BASE_URL/device_authorization")
            .post(body)
            .addHeader("User-Agent", "KimiCLI/1.5")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw ApiException.NetworkError("Empty response from device authorization")

        if (!response.isSuccessful) {
            throw ApiException.NetworkError("Device auth HTTP ${response.code}: $responseBody")
        }

        return try {
            gson.fromJson(responseBody, DeviceCodeResponse::class.java)
        } catch (e: Exception) {
            throw ApiException.ParseError("Failed to parse device code response: ${e.message}")
        }
    }

    /**
     * Step 2: Poll the token endpoint until user approves or code expires.
     * POST https://auth.kimi.com/api/oauth/token
     * Body: grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=...
     *
     * Returns TokenResponse with access_token, refresh_token, expires_in on success.
     * The caller must handle these error responses:
     * - authorization_pending: continue polling at current interval
     * - slow_down: increase poll interval by 5 seconds
     * - expired_token: device code expired (return error)
     * - access_denied: user denied authorization (return error)
     */
    fun pollForToken(deviceCode: String): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", deviceCode)
            .add("client_id", CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url("$AUTH_BASE_URL/token")
            .post(body)
            .addHeader("User-Agent", "KimiCLI/1.5")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw ApiException.NetworkError("Empty response from token poll")

        return try {
            gson.fromJson(responseBody, TokenResponse::class.java)
        } catch (e: Exception) {
            throw ApiException.ParseError("Failed to parse token response: ${e.message}")
        }
    }

    /**
     * Step 3 (optional): Refresh an expired access token.
     * POST https://auth.kimi.com/api/oauth/token
     * Body: grant_type=refresh_token&refresh_token=...
     *
     * Returns: TokenResponse with new access_token, refresh_token, expires_in
     */
    fun refreshAccessToken(refreshToken: String): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url("$AUTH_BASE_URL/token")
            .post(body)
            .addHeader("User-Agent", "KimiCLI/1.5")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw ApiException.NetworkError("Empty response from token refresh")

        if (!response.isSuccessful) {
            when (response.code) {
                401 -> throw ApiException.Unauthorized("Token refresh HTTP 401: $responseBody")
                403 -> throw ApiException.Forbidden("Token refresh HTTP 403: $responseBody")
                else -> throw ApiException.NetworkError("Token refresh HTTP ${response.code}: $responseBody")
            }
        }

        return try {
            gson.fromJson(responseBody, TokenResponse::class.java)
        } catch (e: Exception) {
            throw ApiException.ParseError("Failed to parse refresh response: ${e.message}")
        }
    }
}
