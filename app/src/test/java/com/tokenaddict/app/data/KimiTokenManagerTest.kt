package com.tokenaddict.app.data

import android.content.Context
import com.tokenaddict.app.data.model.KimiOAuthTokens
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KimiTokenManagerTest {

    private lateinit var tokenManager: KimiTokenManager
    private lateinit var oauthManager: KimiOAuthManager
    private lateinit var gson: Gson

    @Before
    fun setUp() {
        gson = Gson()
        oauthManager = KimiOAuthManager(OkHttpClient.Builder().build(), gson)
        val mockPrefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_kimi_tokens", Context.MODE_PRIVATE)
        mockPrefs.edit().clear().commit()
        val securePrefs = SecurePreferences.create(mockPrefs)
        tokenManager = KimiTokenManager(securePrefs, oauthManager, gson)
        tokenManager.clearTokens()
    }

    @Test
    fun `saveTokens stores and retrieves access token`() {
        val tokens = KimiOAuthTokens(
            accessToken = "test_access_token",
            refreshToken = "test_refresh_token",
            expiresAt = System.currentTimeMillis() + 3600_000L
        )
        tokenManager.saveTokens(tokens)

        assertEquals("test_access_token", tokenManager.getAccessToken())
        assertEquals("test_refresh_token", tokenManager.getRefreshToken())
    }

    @Test
    fun `getAccessToken returns null when no tokens saved`() {
        assertNull(tokenManager.getAccessToken())
    }

    @Test
    fun `getRefreshToken returns null when no tokens saved`() {
        assertNull(tokenManager.getRefreshToken())
    }

    @Test
    fun `isTokenExpired returns false when token is valid`() {
        val futureExpiry = System.currentTimeMillis() + 3600_000L
        val tokens = KimiOAuthTokens("tok", "ref", futureExpiry)
        tokenManager.saveTokens(tokens)

        assertFalse(tokenManager.isTokenExpired())
    }

    @Test
    fun `isTokenExpired returns true when token is past expiry`() {
        val pastExpiry = System.currentTimeMillis() - 1000L
        val tokens = KimiOAuthTokens("tok", "ref", pastExpiry)
        tokenManager.saveTokens(tokens)

        assertTrue(tokenManager.isTokenExpired())
    }

    @Test
    fun `isTokenExpired returns true when no tokens`() {
        assertTrue(tokenManager.isTokenExpired())
    }

    @Test
    fun `isTokenExpired returns true within 300s buffer`() {
        val nearExpiry = System.currentTimeMillis() + 200_000L
        val tokens = KimiOAuthTokens("tok", "ref", nearExpiry)
        tokenManager.saveTokens(tokens)

        assertTrue(tokenManager.isTokenExpired())
    }

    @Test
    fun `isTokenExpired returns false beyond 300s buffer`() {
        val beyondBuffer = System.currentTimeMillis() + 400_000L
        val tokens = KimiOAuthTokens("tok", "ref", beyondBuffer)
        tokenManager.saveTokens(tokens)

        assertFalse(tokenManager.isTokenExpired())
    }

    @Test
    fun `clearTokens removes all tokens`() {
        val tokens = KimiOAuthTokens("tok", "ref", System.currentTimeMillis() + 3600_000L)
        tokenManager.saveTokens(tokens)
        assertNotNull(tokenManager.getAccessToken())

        tokenManager.clearTokens()

        assertNull(tokenManager.getAccessToken())
        assertNull(tokenManager.getRefreshToken())
    }

    @Test
    fun `getDeviceId returns same UUID on repeated calls`() {
        val firstId = tokenManager.getDeviceId()
        val secondId = tokenManager.getDeviceId()

        assertEquals(firstId, secondId)
        assertNotNull(firstId)
    }

    @Test
    fun `getDeviceId returns valid UUID format`() {
        val deviceId = tokenManager.getDeviceId()
        assertNotNull(deviceId)
        assertTrue(deviceId.length >= 32)
    }

    @Test
    fun `getDeviceId persists across instances`() {
        val mockPrefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_persist", Context.MODE_PRIVATE)
        mockPrefs.edit().clear().commit()
        val securePrefs = SecurePreferences.create(mockPrefs)

        val firstManager = KimiTokenManager(securePrefs, oauthManager, gson)
        val firstId = firstManager.getDeviceId()

        val secondManager = KimiTokenManager(securePrefs, oauthManager, gson)
        val secondId = secondManager.getDeviceId()

        assertEquals(firstId, secondId)
    }

    @Test
    fun `getDeviceId migrates from plaintext fallback`() {
        val existingDeviceId = "existing-uuid-from-plaintext"
        val mockPrefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_migration", Context.MODE_PRIVATE)
        mockPrefs.edit().clear().commit()
        val securePrefs = SecurePreferences.create(mockPrefs)

        val manager = KimiTokenManager(securePrefs, oauthManager, gson, existingDeviceId)
        val deviceId = manager.getDeviceId()

        assertEquals(existingDeviceId, deviceId)

        val manager2 = KimiTokenManager(securePrefs, oauthManager, gson)
        assertEquals(existingDeviceId, manager2.getDeviceId())
    }

    @Test
    fun `refreshTokenIfNeeded does not refresh when token is valid`() {
        val futureExpiry = System.currentTimeMillis() + 3600_000L
        val tokens = KimiOAuthTokens("valid_token", "refresh_token", futureExpiry)
        tokenManager.saveTokens(tokens)

        tokenManager.refreshTokenIfNeeded()
        assertEquals("valid_token", tokenManager.getAccessToken())
    }

    @Test
    fun `saveTokens overwrites previous tokens`() {
        val tokens1 = KimiOAuthTokens("old_access", "old_refresh", System.currentTimeMillis() + 3600_000L)
        tokenManager.saveTokens(tokens1)
        assertEquals("old_access", tokenManager.getAccessToken())

        val tokens2 = KimiOAuthTokens("new_access", "new_refresh", System.currentTimeMillis() + 7200_000L)
        tokenManager.saveTokens(tokens2)
        assertEquals("new_access", tokenManager.getAccessToken())
        assertEquals("new_refresh", tokenManager.getRefreshToken())
    }
}
