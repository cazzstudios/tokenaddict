package com.tokenaddict.app.ui

import android.content.Context
import android.content.Intent
import android.webkit.WebSettings
import com.tokenaddict.app.data.SecurePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class KimiLoginActivityTest {

    private lateinit var activity: KimiLoginActivity

    @Before
    fun setUp() {
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
    }

    private fun getWebView(): android.webkit.WebView =
        activity.findViewById(
            activity.resources.getIdentifier("kimiWebView", "id", activity.packageName)
        )

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
}
