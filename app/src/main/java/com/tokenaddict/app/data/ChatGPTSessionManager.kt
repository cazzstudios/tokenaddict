package com.tokenaddict.app.data

import android.content.Context
import android.content.SharedPreferences

class ChatGPTSessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCredentials(accessToken: String, accountId: String?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_ACCOUNT_ID, accountId)
            .putLong(KEY_LAST_AUTH_CHECK, System.currentTimeMillis())
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getAccountId(): String? = prefs.getString(KEY_ACCOUNT_ID, null)

    fun isLoggedIn(): Boolean {
        val token = getAccessToken()
        return !token.isNullOrBlank()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun getLastAuthCheck(): Long = prefs.getLong(KEY_LAST_AUTH_CHECK, 0L)

    companion object {
        private const val PREFS_NAME = "chatgpt_session_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_LAST_AUTH_CHECK = "last_auth_check"
    }
}
