package com.tokenaddict.app.ui

import android.content.Context
import android.content.Intent
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.SessionManager
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
class LoginActivityTest {

    private lateinit var activity: LoginActivity
    private lateinit var shadowWebView: org.robolectric.shadows.ShadowWebView

    @After
    fun tearDown() {
        SessionManager.encryptedPrefsFactory = null
    }

    private fun makeValidJwt(): String {
        val header = android.util.Base64.encodeToString(
            """{"alg":"RS256","typ":"JWT"}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val payload = android.util.Base64.encodeToString(
            """{"sub":"1234567890","name":"Test User","iat":1516239022}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val sig = android.util.Base64.encodeToString(
            "fake-signature".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        return "$header.$payload.$sig"
    }

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        val plaintextPrefs = app.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        plaintextPrefs.edit().clear().commit()

        SessionManager.encryptedPrefsFactory = { ctx, prefsName ->
            val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            SecurePreferences.create(prefs)
        }

        val intent = Intent(app, LoginActivity::class.java)
        activity = Robolectric.buildActivity(LoginActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()
        val webView = activity.findViewById<android.webkit.WebView>(
            activity.resources.getIdentifier("webView", "id", activity.packageName)
        )
        shadowWebView = org.robolectric.Shadows.shadowOf(webView)
    }

    private fun callParseCredential(raw: String?): String {
        val method = LoginActivity::class.java.getDeclaredMethod("parseCredential", String::class.java)
        method.isAccessible = true
        return method.invoke(activity, raw) as String
    }

    private fun callInjectCredentialIntoMainView(credential: String) {
        val method = LoginActivity::class.java.getDeclaredMethod("injectCredentialIntoMainView", String::class.java)
        method.isAccessible = true
        method.invoke(activity, credential)
    }

    private fun callIsValidJwtStructure(token: String): Boolean {
        val method = LoginActivity::class.java.getDeclaredMethod("isValidJwtStructure", String::class.java)
        method.isAccessible = true
        return method.invoke(activity, token) as Boolean
    }

    @Test
    fun parseCredential_validJwt_accepted() {
        val jwt = makeValidJwt()
        val result = callParseCredential("\"$jwt\"")
        assertEquals(jwt, result)
    }

    @Test
    fun parseCredential_rejectsNonJwt() {
        val result = callParseCredential("this-is-not-a-jwt-at-all-and-is-long-enough")
        assertEquals("", result)
    }

    @Test
    fun parseCredential_rejectsTwoSegment() {
        val result = callParseCredential("abc.def")
        assertEquals("", result)
    }

    @Test
    fun parseCredential_rejectsFourSegment() {
        val result = callParseCredential("a.b.c.d")
        assertEquals("", result)
    }

    @Test
    fun parseCredential_rejectsSegmentWithInvalidChars() {
        val result = callParseCredential("abc.def.ghi jkl")
        assertEquals("", result)
    }

    @Test
    fun parseCredential_rejectsEmpty() {
        assertEquals("", callParseCredential(""))
        assertEquals("", callParseCredential(null))
    }

    @Test
    fun parseCredential_rejectsShortCredential() {
        assertEquals("", callParseCredential("short"))
    }

    @Test
    fun isValidJwtStructure_trueForValidJwt() {
        assertTrue(callIsValidJwtStructure(makeValidJwt()))
    }

    @Test
    fun isValidJwtStructure_falseForNonJwt() {
        assertFalse(callIsValidJwtStructure("not-a-jwt"))
        assertFalse(callIsValidJwtStructure("a.b"))
        assertFalse(callIsValidJwtStructure("a.b.c.d"))
        assertFalse(callIsValidJwtStructure("abc.def.ghi jkl"))
        assertFalse(callIsValidJwtStructure("abc.def.ghi!jkl"))
        assertFalse(callIsValidJwtStructure(""))
    }

    @Test
    fun injectCredential_validJwt_usesJsonNotStringInterpolation() {
        val jwt = makeValidJwt()
        callInjectCredentialIntoMainView(jwt)

        val js = shadowWebView.lastEvaluatedJavascript ?: error("No JS evaluated")
        assertTrue("Expected JSON.parse-safe object literal, got: $js",
            js.contains("\"credential\":\"$jwt\""))
        assertFalse("Should not contain string interpolation pattern",
            js.contains("credential: '$jwt'"))
        assertTrue("Origin must be accounts.google.com",
            js.contains("origin: 'https://accounts.google.com'"))
    }

    @Test
    fun injectCredential_rejectsOversizedCredential() {
        val oversized = "A".repeat(9 * 1024)
        callInjectCredentialIntoMainView(oversized)

        val js = shadowWebView.lastEvaluatedJavascript
        assertTrue("Oversized credential should not be evaluated", js == null || js.isEmpty())
    }

    @Test
    fun injectCredential_quotesInCredential_areJsonEscaped() {
        val malicious = makeValidJwt().dropLast(1) + '"'
        callInjectCredentialIntoMainView(malicious)

        val js = shadowWebView.lastEvaluatedJavascript ?: error("No JS evaluated")
        assertTrue("Quotes must be JSON-escaped",
            js.contains("\\\"") || js.contains("\\u0022"))
    }

    @Test
    fun injectCredential_newlinesInCredential_areJsonEscaped() {
        val malicious = makeValidJwt().dropLast(5) + "a\nb\nc"
        callInjectCredentialIntoMainView(malicious)

        val js = shadowWebView.lastEvaluatedJavascript ?: error("No JS evaluated")
        assertFalse("Newlines should not appear raw in JS",
            js.contains("credential: '") && js.contains("\n"))
        assertTrue("Payload should be valid JSON object",
            js.contains("\"credential\":"))
    }

    @Test
    fun injectCredential_payloadStructureIsValidJson() {
        val jwt = makeValidJwt()
        callInjectCredentialIntoMainView(jwt)

        val js = shadowWebView.lastEvaluatedJavascript ?: error("No JS evaluated")
        assertTrue("JS must contain var payload =", js.contains("var payload ="))
        assertTrue("Payload must include type field", js.contains("\"type\":\"id_token\""))
    }

    @Test
    fun injectCredential_emitsJsonEncodedCredential_notRawString() {
        val jwt = makeValidJwt()
        callInjectCredentialIntoMainView(jwt)

        val js = shadowWebView.lastEvaluatedJavascript ?: error("No JS evaluated")
        assertTrue("Credential must be JSON-encoded with double quotes",
            js.contains("\"credential\":\"$jwt\""))
        assertFalse("Must not use JS string interpolation for credential",
            js.contains("credential: '$jwt'"))
        assertTrue("Payload must be a JSON object literal",
            js.contains("var payload ="))
    }

    @Test
    fun parseCredential_rejectsCredentialWithEmbeddedQuotes() {
        // 3 dot-separated segments longer than 20 chars, but " embedded in a segment
        // fails isValidJwtStructure because " is not in [A-Za-z0-9_-]
        val malicious = "AAAAAAAAAA.BBBBBBBBB\"BBBBB.CCCCCCCCCC"
        assertEquals("", callParseCredential(malicious))
    }

    @Test
    fun parseCredential_rejectsCredentialWithEmbeddedNewlines() {
        // 3 dot-separated segments longer than 20 chars, but \n embedded in a segment
        // fails isValidJwtStructure because newline is not in [A-Za-z0-9_-]
        val malicious = "AAAAAAAAAA.BBBBBBBBB\nBBBBB.CCCCCCCCCC"
        assertEquals("", callParseCredential(malicious))
    }

    @Test
    fun webSettings_mixedContentModeIsNeverAllow() {
        val webView = activity.findViewById<android.webkit.WebView>(
            activity.resources.getIdentifier("webView", "id", activity.packageName)
        )
        assertEquals(
            android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW,
            webView.settings.mixedContentMode
        )
    }
}
