package com.tokenaddict.app.ui

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiOAuthManager.DeviceCodeResponse
import com.tokenaddict.app.data.KimiOAuthManager.TokenResponse
import com.tokenaddict.app.data.SecurePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class KimiLoginActivityTest {

    private lateinit var activity: KimiLoginActivity
    private lateinit var mockOAuthManager: KimiOAuthManager

    @Before
    fun setUp() {
        mockOAuthManager = mock()
        whenever(mockOAuthManager.requestDeviceCode()).thenThrow(RuntimeException("Mock: no network"))
        KimiLoginActivity.oauthManagerFactory = { _, _ -> mockOAuthManager }

        KimiLoginActivity.securePrefsFactory = { ctx, prefsName ->
            val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            SecurePreferences.create(prefs)
        }

        val intent = Intent(RuntimeEnvironment.getApplication(), KimiLoginActivity::class.java)
        activity = Robolectric.buildActivity(KimiLoginActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()
    }

    @After
    fun tearDown() {
        KimiLoginActivity.securePrefsFactory = null
        KimiLoginActivity.oauthManagerFactory = null
    }

    private fun getWebView(): android.webkit.WebView =
        activity.findViewById(
            activity.resources.getIdentifier("kimiWebView", "id", activity.packageName)
        )

    private fun awaitAsync() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ---- Existing WebView config tests ----

    @Test
    fun webview_mixedContentModeIsNeverAllow() {
        assertEquals(
            WebSettings.MIXED_CONTENT_NEVER_ALLOW,
            getWebView().settings.mixedContentMode
        )
    }

    @Test
    fun webview_allowFileAccessIsFalse() {
        assertFalse(getWebView().settings.allowFileAccess)
    }

    @Test
    fun webview_javaScriptCanOpenWindowsAutomaticallyIsFalse() {
        assertFalse(getWebView().settings.javaScriptCanOpenWindowsAutomatically)
    }

    @Test
    fun webview_javascriptEnabledForOAuth() {
        assertTrue(getWebView().settings.javaScriptEnabled)
    }

    // ---- Coroutine-based OAuth flow tests ----

    @Test
    fun `requestNewCode calls deviceCode and updates UI on success`() {
        val mockOAuth = mock<KimiOAuthManager>()
        whenever(mockOAuth.requestDeviceCode()).thenReturn(
            DeviceCodeResponse(
                deviceCode = "test_device",
                userCode = "ABC-123",
                verificationUri = "https://auth.kimi.com/activate",
                verificationUriComplete = "https://auth.kimi.com/activate?code=ABC-123",
                expiresIn = 600
            )
        )
        whenever(mockOAuth.pollForToken(any())).thenReturn(
            TokenResponse(null, null, null, null, "expired_token")
        )
        KimiLoginActivity.oauthManagerFactory = { _, _ -> mockOAuth }

        val testActivity = Robolectric.buildActivity(
            KimiLoginActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), KimiLoginActivity::class.java)
        ).create().start().resume().get()

        awaitAsync()

        val webViewContainer = testActivity.findViewById<View>(
            testActivity.resources.getIdentifier("kimiWebViewContainer", "id", testActivity.packageName)
        )
        assertEquals(View.VISIBLE, webViewContainer.visibility)

        val progressBar = testActivity.findViewById<View>(
            testActivity.resources.getIdentifier("kimiProgressBar", "id", testActivity.packageName)
        )
        assertEquals(View.GONE, progressBar.visibility)
    }

    @Test
    fun `requestNewCode shows retry button on network error`() {
        val mockOAuth = mock<KimiOAuthManager>()
        whenever(mockOAuth.requestDeviceCode()).thenThrow(RuntimeException("Network error"))
        KimiLoginActivity.oauthManagerFactory = { _, _ -> mockOAuth }

        val testActivity = Robolectric.buildActivity(
            KimiLoginActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), KimiLoginActivity::class.java)
        ).create().start().resume().get()

        awaitAsync()

        val retryButton = testActivity.findViewById<View>(
            testActivity.resources.getIdentifier("kimiRetryButton", "id", testActivity.packageName)
        )
        assertEquals(View.VISIBLE, retryButton.visibility)

        val progressBar = testActivity.findViewById<View>(
            testActivity.resources.getIdentifier("kimiProgressBar", "id", testActivity.packageName)
        )
        assertEquals(View.GONE, progressBar.visibility)
    }

    @Test
    fun `pollForToken completes on success`() {
        val mockOAuth = mock<KimiOAuthManager>()
        whenever(mockOAuth.requestDeviceCode()).thenReturn(
            DeviceCodeResponse("test_device", "ABC-123", "https://auth.kimi.com/activate", null, 600)
        )
        whenever(mockOAuth.pollForToken(any())).thenReturn(
            TokenResponse("access_token_123", "refresh_token_456", 3600L, "Bearer", null)
        )
        KimiLoginActivity.oauthManagerFactory = { _, _ -> mockOAuth }

        val testActivity = Robolectric.buildActivity(
            KimiLoginActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), KimiLoginActivity::class.java)
        ).create().start().resume().get()

        awaitAsync()

        assertTrue(testActivity.isFinishing)
    }

    @Test
    fun `pollForToken retries on authorization_pending`() {
        val mockOAuth = mock<KimiOAuthManager>()
        whenever(mockOAuth.requestDeviceCode()).thenReturn(
            DeviceCodeResponse("test_device", "ABC-123", "https://auth.kimi.com/activate", null, 600)
        )
        whenever(mockOAuth.pollForToken(any())).thenReturn(
            TokenResponse(null, null, null, null, "authorization_pending"),
            TokenResponse("access_token_123", "refresh_token_456", 3600L, "Bearer", null)
        )
        KimiLoginActivity.oauthManagerFactory = { _, _ -> mockOAuth }

        val testActivity = Robolectric.buildActivity(
            KimiLoginActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), KimiLoginActivity::class.java)
        ).create().start().resume().get()

        awaitAsync()

        // Advance the foreground scheduler past the authorization_pending delay(5000)
        Robolectric.getForegroundThreadScheduler().advanceBy(6000)

        // Wait for the retry: IO -> pollForToken -> Main -> finish
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(testActivity.isFinishing)
    }

    @Test
    fun `pollForToken handles expired_token`() {
        val mockOAuth = mock<KimiOAuthManager>()
        whenever(mockOAuth.requestDeviceCode()).thenReturn(
            DeviceCodeResponse("test_device", "ABC-123", "https://auth.kimi.com/activate", null, 600)
        )
        whenever(mockOAuth.pollForToken(any())).thenReturn(
            TokenResponse(null, null, null, null, "expired_token")
        )
        KimiLoginActivity.oauthManagerFactory = { _, _ -> mockOAuth }

        val testActivity = Robolectric.buildActivity(
            KimiLoginActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), KimiLoginActivity::class.java)
        ).create().start().resume().get()

        awaitAsync()

        val retryButton = testActivity.findViewById<View>(
            testActivity.resources.getIdentifier("kimiRetryButton", "id", testActivity.packageName)
        )
        assertEquals(View.VISIBLE, retryButton.visibility)

        val progressBar = testActivity.findViewById<View>(
            testActivity.resources.getIdentifier("kimiProgressBar", "id", testActivity.packageName)
        )
        assertEquals(View.GONE, progressBar.visibility)
    }
}
