package com.tokenaddict.app.ui

import android.annotation.SuppressLint
import org.json.JSONObject
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tokenaddict.app.R
import com.tokenaddict.app.data.SessionManager

/**
 * Loads Claude's web UI in a WebView.  Google Identity Services (GIS) opens a
 * popup via `window.open` that authenticates the user, lands on
 * `accounts.google.com/gsi/transform`, and calls
 * `window.opener.postMessage(credential)` to hand the ID token back.
 *
 * We enable multiple-window support so the popup becomes a child WebView
 * (via [WebChromeClient.onCreateWindow] + [WebViewTransport]) instead of
 * navigating the main page away.  The child keeps the opener relationship,
 * so `postMessage` succeeds and Claude's JS receives the credential.
 *
 * A fallback path handles the case where the main WebView itself lands on the
 * transform page (redirect-mode GIS or disabled multi-window).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var sessionManager: SessionManager
    private lateinit var loginUrl: String
    private lateinit var baseUrl: String
    private var loginSuccessCalled = false
    private var popupWebView: WebView? = null

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            checkCookiesForSession()
            webView.let { checkLoginState(it, it.url) }
            webView.postDelayed(this, PERIODIC_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID) ?: DEFAULT_PROVIDER_ID
        loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL) ?: DEFAULT_LOGIN_URL
        baseUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: DEFAULT_BASE_URL
        sessionManager = SessionManager(this, providerId)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorText = findViewById(R.id.errorText)
        retryButton = findViewById(R.id.retryButton)

        retryButton.setOnClickListener {
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(loginUrl)
        }

        setupWebView()
        clearSessionAndLoad(loginUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = USER_AGENT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Must be true so GIS popups are intercepted by onCreateWindow
            // instead of navigating the main page away.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        // Third-party cookies are managed per-URL via configureCookiesForUrl()
        // to allow only during OAuth flows on known provider domains.
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = createMainWebViewClient()
        webView.webChromeClient = createMainWebChromeClient()
    }

    private fun createMainWebViewClient() = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "onPageStarted: $url")
            configureCookiesForUrl(view, url)
            progressBar.visibility = View.VISIBLE
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "onPageFinished: $url")
            progressBar.visibility = View.GONE

            if (url != null && isLoggedInUrl(url)) {
                onLoginSuccess()
                return
            }

            if (url != null && url.contains("accounts.google.com/gsi/transform")) {
                Log.d(TAG, "Main WebView on gsi/transform – fallback relay")
                handleGoogleIdentityTransform(view)
                return
            }

            checkLoginState(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString()
            Log.d(TAG, "shouldOverrideUrlLoading: $url")
            if (url != null && isLoggedInUrl(url)) {
                onLoginSuccess()
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.e(TAG, "WebView error: ${error?.description} for ${request?.url}")
            if (request?.isForMainFrame == true) {
                progressBar.visibility = View.GONE
                webView.visibility = View.GONE
                errorText.text = getString(R.string.webview_error)
                errorView.visibility = View.VISIBLE
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.e(TAG, "HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            Log.e(TAG, "SSL error: $error")
            handler?.cancel()
        }
    }

    private fun createMainWebChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (newProgress >= 100) {
                checkLoginState(view, view?.url)
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.d(
                TAG,
                "Console [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}] ${consoleMessage?.message()}"
            )
            return true
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            Log.d(TAG, "onCreateWindow: isDialog=$isDialog isUserGesture=$isUserGesture")
            return handleCreateWindow(resultMsg)
        }
    }

    /**
     * Creates a child WebView for the popup requested by `window.open`.
     * The [WebViewTransport] mechanism pairs child with parent so that
     * `window.opener` in the child points back to the parent, making
     * `window.opener.postMessage()` in the GIS transform page work.
     *
     * The popup is added to the activity's view hierarchy as a fullscreen overlay
     * so the user can actually see and interact with the Google sign-in flow.
     */
    private fun handleCreateWindow(resultMsg: Message?): Boolean {
        cleanupPopup()

        val popup = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = this@LoginActivity.webView.settings.userAgentString
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
        }
        configureCookiesForUrl(popup, popup.url)
        (webView.parent as? android.widget.FrameLayout)?.addView(popup)

        popup.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "Popup onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Popup onPageFinished: $url")
                if (url == null) return

                if (url.contains("accounts.google.com/gsi/transform")) {
                    Log.d(TAG, "Popup on gsi/transform – waiting for postMessage relay")
                    schedulePopupTransformFallback(popup)
                    return
                }

                if (url.startsWith("https://claude.ai")) {
                    Log.d(TAG, "Popup redirected to Claude: $url")
                    this@LoginActivity.webView.loadUrl(url)
                    cleanupPopup()
                    return
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                Log.d(TAG, "Popup shouldOverrideUrlLoading: $url")
                return false
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.e(TAG, "Popup SSL error: $error")
                handler?.cancel()
            }
        }

        popup.webChromeClient = WebChromeClient()

        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        transport.webView = popup
        resultMsg.sendToTarget()

        popupWebView = popup
        Log.d(TAG, "Popup WebView wired via transport")
        return true
    }

    /**
     * When the transport doesn't propagate `window.opener` (older WebView),
     * we wait, then extract the credential from the transform page HTML and
     * inject a synthetic MessageEvent into the main WebView.
     */
    private fun schedulePopupTransformFallback(popupView: WebView) {
        popupView.postDelayed({
            if (popupWebView == null) return@postDelayed

            popupView.evaluateJavascript(
                """(${buildExtractCredentialJs()})()"""
            ) { result ->
                val credential = parseCredential(result)
                if (credential.isNotEmpty()) {
                    Log.d(TAG, "Popup fallback: extracted credential (${credential.length} chars)")
                    injectCredentialIntoMainView(credential)
                } else {
                    Log.w(TAG, "Popup fallback: no credential found")
                }
                cleanupPopup()
            }
        }, TRANSFORM_FALLBACK_DELAY_MS)
    }

    /**
     * Main WebView fallback: extracts credential from gsi/transform, navigates
     * back to Claude, and injects a synthetic MessageEvent.
     */
    private fun handleGoogleIdentityTransform(view: WebView?) {
        view?.evaluateJavascript(
            """(${buildExtractCredentialJs()})()"""
        ) { result ->
            val credential = parseCredential(result)
            Log.d(TAG, "Main-WebView gsi/transform credential length=${credential.length}")

            if (credential.isNotEmpty()) {
                if (webView.canGoBack()) {
                    webView.goBack()
                    webView.postDelayed({ injectCredentialIntoMainView(credential) }, 2000)
                } else {
                    webView.loadUrl("https://claude.ai/")
                    webView.postDelayed({ injectCredentialIntoMainView(credential) }, 3000)
                }
            } else {
                Log.w(TAG, "Could not extract credential – returning to Claude")
                if (webView.canGoBack()) webView.goBack() else webView.loadUrl("https://claude.ai/")
            }
        }
    }

    /**
     * JS that searches page HTML for a Google ID-token credential.
     * Patterns: `"credential": "eyJ..."`, `var credential = 'eyJ...'`,
     * `id_token: 'eyJ...'`, URL hash `#credential=...`.
     */
    private fun buildExtractCredentialJs(): String = """
        function() {
            try {
                var html = document.documentElement ? document.documentElement.innerHTML : '';
                var patterns = [
                    /"credential"\s*:\s*"([^"]{20,})"/,
                    /var\s+credential\s*=\s*['"]([^'"]{20,})['"]/,
                    /id_token['"]*\s*:\s*['"]([^'"]{20,})['"]/
                ];
                for (var i = 0; i < patterns.length; i++) {
                    var m = html.match(patterns[i]);
                    if (m && m[1]) return m[1];
                }
                var hash = window.location.hash;
                if (hash) {
                    var hm = hash.match(/credential=([^&]+)/);
                    if (hm && hm[1].length > 20) return decodeURIComponent(hm[1]);
                }
            } catch(e) {}
            return '';
        }
    """.trimIndent()

    private fun parseCredential(raw: String?): String {
        if (raw == null) return ""
        var s = raw.trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            s = s.substring(1, s.length - 1)
        }
        s = s.replace("\\\"", "\"").replace("\\\\", "\\")
        if (s.length <= 20) return ""
        // JWT structure check: three dot-separated base64url segments
        if (!isValidJwtStructure(s)) return ""
        return s
    }

    // JWT structure check: three dot-separated base64url segments
    private fun isValidJwtStructure(token: String): Boolean {
        val parts = token.split('.')
        if (parts.size != 3) return false
        return parts.all { part ->
            part.isNotEmpty() && part.all { c ->
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
            }
        }
    }

    /**
     * Dispatches a synthetic MessageEvent into the main WebView.  We target
     * both `window` and `document` because Android WebView historically
     * dispatches `message` events on `document` instead of `window`.
     */
    private fun injectCredentialIntoMainView(credential: String) {
        if (credential.toByteArray().size > MAX_CREDENTIAL_BYTES) {
            Log.w(TAG, "Credential exceeds ${MAX_CREDENTIAL_BYTES} bytes, rejecting (${credential.toByteArray().size} bytes)")
            return
        }

        val payload = JSONObject().apply {
            put("type", "id_token")
            put("credential", credential)
        }
        val jsonPayload = payload.toString()

        // JSON is valid JS object literal syntax, so we embed the serialized
        // JSON directly — no string interpolation of untrusted content.
        val js = """
            (function() {
                try {
                    var payload = $jsonPayload;
                    var evt = new MessageEvent('message', {
                        data: payload,
                        origin: 'https://accounts.google.com'
                    });
                    window.dispatchEvent(evt);
                    document.dispatchEvent(evt);
                    true;
                } catch(e) {
                    'inject_error:' + e.message;
                }
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "Credential injection result: $result")
            checkCookiesForSession()
            checkLoginState(webView, webView.url)
        }
    }

    private fun clearSessionAndLoad(url: String) {
        val cookieManager = CookieManager.getInstance()
        WebStorage.getInstance().deleteAllData()

        cookieManager.removeAllCookies { _ ->
            runOnUiThread {
                cookieManager.flush()
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
                webView.loadUrl("about:blank")
                webView.postDelayed({
                    webView.loadUrl(url)
                    webView.postDelayed(periodicCheckRunnable, PERIODIC_CHECK_INTERVAL_MS)
                }, 300)
            }
        }
    }

    private fun checkCookiesForSession() {
        if (sessionManager.hasSessionCookies(baseUrl)) {
            Log.d(TAG, "checkCookiesForSession: found session markers")
            onLoginSuccess()
        }
    }

    private fun isLoggedInUrl(url: String): Boolean {
        if (url == "https://claude.ai/" || url == "https://claude.ai") return false
        return url.contains("/chat") || url.contains("/new")
    }

    private fun checkLoginState(wv: WebView?, currentUrl: String?) {
        Log.d(TAG, "checkLoginState: $currentUrl")
        if (currentUrl == null) return

        if (isLoggedInUrl(currentUrl)) {
            Log.d(TAG, "checkLoginState: detected logged-in URL")
            onLoginSuccess()
            return
        }

        if (!currentUrl.startsWith(baseUrl, ignoreCase = true)) return

        wv?.evaluateJavascript(
            """
            (async function() {
                try {
                    var resp = await fetch('https://claude.ai/api/account', {credentials: 'include'});
                    if (resp.ok) {
                        var data = await resp.json();
                        if (data && data.uuid) return 'logged_in';
                    }
                    return 'not_logged_in';
                } catch(e) {
                    return 'not_logged_in';
                }
            })()
            """.trimIndent()
        ) { result ->
            Log.d(TAG, "checkLoginState result: $result")
            if (result?.trim('"') == "logged_in") {
                onLoginSuccess()
            }
        }
    }

    private fun onLoginSuccess() {
        if (loginSuccessCalled) return
        loginSuccessCalled = true
        webView.removeCallbacks(periodicCheckRunnable)
        setThirdPartyCookies(webView, false)
        saveSessionWithRetry(0)
    }

    private fun saveSessionWithRetry(attempt: Int) {
        val cookieString = CookieManager.getInstance().getCookie(baseUrl)
        Log.d(TAG, "saveSessionWithRetry attempt=$attempt cookies=${cookieString?.take(200)}")
        if (!cookieString.isNullOrBlank() || attempt >= 10) {
            sessionManager.saveSession(baseUrl)
            setResult(RESULT_OK)
            finish()
        } else {
            webView.postDelayed({ saveSessionWithRetry(attempt + 1) }, 500)
        }
    }

    @Deprecated("Use OnBackPressedCallback instead")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.removeCallbacks(periodicCheckRunnable)
        cleanupPopup()
    }

    private fun cleanupPopup() {
        popupWebView?.let {
            try {
                setThirdPartyCookies(it, false)
                it.loadUrl("about:blank")
                (webView.parent as? android.widget.FrameLayout)?.removeView(it)
                it.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up popup: ${e.message}")
            }
        }
        popupWebView = null
    }

    /**
     * Enables third-party cookies only when the URL belongs to a known OAuth
     * provider domain.  Disables them otherwise to limit cross-site tracking.
     *
     * Allowed domains:
     * - accounts.google.com: Google Identity Services (GIS) OAuth popup flow
     * - claude.ai: main application domain (session cookies)
     * - auth.kimi.com / kimi.com: Kimi device-code OAuth flow
     */
    private fun configureCookiesForUrl(view: WebView?, url: String?) {
        val host = try { android.net.Uri.parse(url ?: "").host } catch (_: Exception) { null }
        val allowed = host != null && LOGIN_OAUTH_DOMAINS.any { host == it || host.endsWith(".$it") }
        setThirdPartyCookies(view, allowed)
    }

    private fun setThirdPartyCookies(view: WebView?, enabled: Boolean) {
        view?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, enabled) }
    }

    companion object {
        private const val TAG = "LoginActivity"

        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_LOGIN_URL = "login_url"
        const val EXTRA_BASE_URL = "base_url"

        private const val DEFAULT_PROVIDER_ID = "claude"
        private const val DEFAULT_LOGIN_URL = "https://claude.ai/"
        private const val DEFAULT_BASE_URL = "https://claude.ai"
        private const val PERIODIC_CHECK_INTERVAL_MS = 2000L
        private const val TRANSFORM_FALLBACK_DELAY_MS = 2500L

        private val LOGIN_OAUTH_DOMAINS = listOf(
            "accounts.google.com",
            "claude.ai",
            "auth.kimi.com",
            "kimi.com"
        )

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private const val MAX_CREDENTIAL_BYTES = 8 * 1024
    }
}
