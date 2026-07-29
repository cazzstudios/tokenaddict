package com.tokenaddict.app.data

import android.util.Log
import com.google.gson.Gson
import com.tokenaddict.app.data.model.ClaudeStatusLevel
import com.tokenaddict.app.data.model.ClaudeStatusResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ClaudeStatusChecker(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "ClaudeStatusChecker"
        private const val STATUS_URL = "https://status.claude.com/api/v2/status.json"
    }

    data class StatusResult(
        val level: ClaudeStatusLevel,
        val description: String
    )

    fun checkStatus(): StatusResult {
        val request = Request.Builder()
            .url(STATUS_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "TokenAddict/1.0")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Status check failed with code ${response.code}")
        }

        val body = response.body?.string() ?: throw IOException("Empty response body")
        val statusResponse = gson.fromJson(body, ClaudeStatusResponse::class.java)

        val level = ClaudeStatusLevel.fromIndicator(statusResponse.status.indicator)
        val description = statusResponse.status.description

        Log.d(TAG, "Status check: ${level.name} - $description")

        return StatusResult(level, description)
    }
}
