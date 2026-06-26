package com.tokenaddict.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import com.tokenaddict.app.data.model.SessionState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SessionManager(private val context: Context, private val providerId: String) {

    companion object {
        private const val TAG = "SessionManager"
        private const val PLAINTEXT_PREFS_NAME = "session_prefs"
        internal const val ENCRYPTED_PREFS_NAME = "encrypted_session_prefs"
        private const val OLD_KEY_COOKIES = "cookies_claude_ai"
        private const val OLD_KEY_COOKIES_PREFIX = "cookies_"

        private val SESSION_MARKERS = listOf(
            "sk-ant-sid01",
            "sessionKey=",
            "sessionKey-ant"
        )

        private val gson = Gson()
        private val stringListType = object : TypeToken<List<String>>() {}.type

        /**
         * Test-only override for secure prefs creation. Null in production.
         * The factory receives (context, prefsName) and must return a [SecurePreferences].
         * Throws [SecureStorageException] if it cannot create one.
         */
        @JvmStatic
        internal var encryptedPrefsFactory: ((Context, String) -> SecurePreferences)? = null
    }

    private val keyCookiesV2: String get() = "cookies_v2_${providerId}"
    private val keyCookiesV1: String get() = "${OLD_KEY_COOKIES_PREFIX}${providerId}"

    /** Plaintext prefs used only for reading legacy data during migration. */
    private val plaintextPrefs: SharedPreferences =
        context.getSharedPreferences(PLAINTEXT_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Primary session storage backed by [SecurePreferences].
     *
     * If encryption initialization fails, [SecureStorageException] propagates —
     * no plaintext fallback.
     */
    val securePreferences: SecurePreferences = createSecurePrefs()

    private fun createSecurePrefs(): SecurePreferences {
        val factory = encryptedPrefsFactory
        if (factory != null) {
            return factory(context, ENCRYPTED_PREFS_NAME)
        }
        return SecurePreferences.create(context, ENCRYPTED_PREFS_NAME)
    }

    init {
        // Step 1: Migrate legacy flat cookies → JSON format (within plaintext prefs)
        if (providerId == "claude") {
            val oldCookies = plaintextPrefs.getString(OLD_KEY_COOKIES, null)
            if (oldCookies != null && !plaintextPrefs.contains(keyCookiesV2)) {
                plaintextPrefs.edit()
                    .putString(keyCookiesV2, flatCookiesToJson(oldCookies))
                    .remove(OLD_KEY_COOKIES)
                    .apply()
            }
            val v1Cookies = plaintextPrefs.getString(keyCookiesV1, null)
            if (v1Cookies != null && !plaintextPrefs.contains(keyCookiesV2)) {
                plaintextPrefs.edit()
                    .putString(keyCookiesV2, flatCookiesToJson(v1Cookies))
                    .remove(keyCookiesV1)
                    .apply()
            }
        }

        // Step 2: Migrate plaintext cookies → encrypted SecurePreferences
        // Only copy; never delete plaintext before encrypted copy succeeds.
        val keysToMigrate = listOf(keyCookiesV2, keyCookiesV1, OLD_KEY_COOKIES)
        for (key in keysToMigrate) {
            val plaintextValue = plaintextPrefs.getString(key, null) ?: continue
            val alreadyEncrypted = securePreferences.getString(key) != null
            if (!alreadyEncrypted) {
                securePreferences.putString(key, plaintextValue)
            }
        }
        // Safe to delete plaintext entries now — encrypted copy succeeded.
        plaintextPrefs.edit()
            .remove(keyCookiesV2)
            .remove(keyCookiesV1)
            .remove(OLD_KEY_COOKIES)
            .apply()
    }

    private fun flatCookiesToJson(flatCookies: String): String {
        val cookies = flatCookies.split("; ")
            .filter { it.isNotBlank() }
        return gson.toJson(cookies)
    }

    private fun jsonToCookies(jsonString: String): List<String> {
        return try {
            gson.fromJson(jsonString, stringListType) ?: emptyList()
        } catch (e: Exception) {
            jsonString.split("; ").filter { it.isNotBlank() }
        }
    }

    private val cookieManager = CookieManager.getInstance()

    fun saveSession(url: String) {
        val cookieString = cookieManager.getCookie(url) ?: return
        val cookies = cookieString.split("; ").filter { it.isNotBlank() }
        securePreferences.putString(keyCookiesV2, gson.toJson(cookies))
    }

    fun restoreSession(url: String) {
        val jsonString = securePreferences.getString(keyCookiesV2) ?: return
        val cookies = jsonToCookies(jsonString)
        for (cookie in cookies) {
            cookieManager.setCookie(url, cookie)
        }
        try { cookieManager.flush() } catch (_: NoSuchMethodError) { /* Robolectric shadow */ }
    }

    fun clearSession() {
        cookieManager.removeAllCookies(null)
        try { cookieManager.flush() } catch (_: NoSuchMethodError) { /* Robolectric shadow */ }
        securePreferences.remove(keyCookiesV2)
        securePreferences.remove(keyCookiesV1)
        securePreferences.remove(OLD_KEY_COOKIES)
    }

    fun isLoggedIn(): Boolean {
        val jsonString = securePreferences.getString(keyCookiesV2) ?: return false
        if (providerId == "claude") {
            if (jsonString.isBlank() || jsonString == "[]") return false
            return SESSION_MARKERS.any { marker -> jsonString.contains(marker) }
                    || jsonString.contains("claude.ai")
                    || jsonString.contains("sessionKey")
                    || jsonString.contains("__cf")
                    || jsonString.contains("anthropic")
        }
        return jsonString.isNotEmpty() && jsonString != "[]"
    }

    /**
     * Check live CookieManager for session markers (used during WebView browsing
     * before cookies are persisted to SecurePreferences).
     */
    fun hasSessionCookies(url: String): Boolean {
        val cookies = cookieManager.getCookie(url) ?: return false
        return SESSION_MARKERS.any { marker -> cookies.contains(marker) }
    }

    fun getSessionState(): SessionState {
        if (providerId == "claude") {
            restoreSession("https://claude.ai")
            val cookies = cookieManager.getCookie("https://claude.ai")
            if (cookies.isNullOrBlank()) return SessionState.LoggedOut
            val hasMarker = SESSION_MARKERS.any { cookies.contains(it) }
                    || cookies.contains("sessionKey")
                    || cookies.contains("anthropic")
                    || cookies.contains("__cf")
            return if (hasMarker || cookies.length > 50) SessionState.LoggedIn() else SessionState.LoggedOut
        }
        return if (isLoggedIn()) SessionState.LoggedIn() else SessionState.LoggedOut
    }
}
