package com.tokenaddict.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tokenaddict.app.R
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.model.KimiOAuthTokens
import com.google.gson.Gson
import okhttp3.OkHttpClient

class KimiLoginActivity : AppCompatActivity() {

    private lateinit var retryButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var webViewContainer: FrameLayout
    private lateinit var webViewHelperText: TextView
    private lateinit var webView: WebView

    private lateinit var oauthManager: KimiOAuthManager
    private lateinit var tokenManager: KimiTokenManager
    private lateinit var gson: Gson

    private var deviceCode: String? = null
    private var userCode: String? = null
    private var verificationUri: String? = null
    private var verificationUriComplete: String? = null
    private var pollIntervalMs = BASE_POLL_INTERVAL_MS
    private var isPolling = false
    private val handler = Handler(Looper.getMainLooper())

    private fun configureCookiesForUrl(view: WebView?, url: String?) {
        val host = try { android.net.Uri.parse(url ?: "").host } catch (_: Exception) { null }
        val allowed = host != null && LOGIN_OAUTH_DOMAINS.any { host == it || host.endsWith(".$it") }
        view?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, allowed) }
    }

    companion object {
        private const val TAG = "KimiLoginActivity"
        private const val BASE_POLL_INTERVAL_MS = 5000L
        private const val SLOW_DOWN_INCREMENT_MS = 5000L

        @JvmStatic
        internal var securePrefsFactory: ((android.content.Context, String) -> SecurePreferences)? = null

        private val LOGIN_OAUTH_DOMAINS = listOf(
            "accounts.google.com",
            "auth.kimi.com",
            "kimi.com"
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kimi_login)

        gson = Gson()
        oauthManager = KimiOAuthManager(OkHttpClient(), gson)

        val securePrefs = securePrefsFactory?.invoke(this, SecurePreferences.PLAINTEXT_PREFS_NAME)
            ?: SecurePreferences.create(this, SecurePreferences.PLAINTEXT_PREFS_NAME)
        tokenManager = KimiTokenManager(securePrefs, oauthManager, gson)

        retryButton = findViewById(R.id.kimiRetryButton)
        progressBar = findViewById(R.id.kimiProgressBar)
        webViewContainer = findViewById(R.id.kimiWebViewContainer)
        webViewHelperText = findViewById(R.id.kimiWebViewHelperText)
        webView = findViewById(R.id.kimiWebView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }
        configureCookiesForUrl(webView, null)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                configureCookiesForUrl(view, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != "about:blank") {
                    showLoading(false)
                }
                view?.evaluateJavascript(
                    """
                    (function() {
                        var vp = document.querySelector('meta[name="viewport"]');
                        if (!vp) {
                            vp = document.createElement('meta');
                            vp.name = 'viewport';
                            vp.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                            document.head.appendChild(vp);
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val verifyUrl = verificationUriComplete ?: verificationUri
                if (verifyUrl != null && url.startsWith("https://www.kimi.com/google-callback")) {
                    view?.postDelayed({ view.loadUrl(verifyUrl) }, 3000)
                }
                return false
            }
        }

        retryButton.setOnClickListener { requestNewCode() }

        requestNewCode()
    }

    private fun requestNewCode() {
        showLoading(true)
        retryButton.visibility = View.GONE
        webViewContainer.visibility = View.GONE
        webViewHelperText.visibility = View.GONE

        Thread {
            try {
                val response = oauthManager.requestDeviceCode()
                deviceCode = response.deviceCode
                userCode = response.userCode
                verificationUri = response.verificationUri
                verificationUriComplete = response.verificationUriComplete
                pollIntervalMs = BASE_POLL_INTERVAL_MS

                runOnUiThread {
                    webViewContainer.visibility = View.VISIBLE
                    webViewHelperText.visibility = View.VISIBLE

                    clearSessionAndLoad(response.verificationUriComplete ?: response.verificationUri)
                    startPolling()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    retryButton.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun startPolling() {
        isPolling = true
        pollForToken()
    }

    private fun pollForToken() {
        if (!isPolling) return
        val code = deviceCode ?: return

        Thread {
            try {
                val tokenResponse = oauthManager.pollForToken(code)

                if (tokenResponse.accessToken != null) {
                    val oauthTokens = KimiOAuthTokens(
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken ?: "",
                        expiresAt = System.currentTimeMillis() + ((tokenResponse.expiresIn ?: 3600L) * 1000)
                    )
                    tokenManager.saveTokens(oauthTokens)

                    runOnUiThread {
                        isPolling = false
                        showLoading(false)
                        setResult(RESULT_OK)
                        finish()
                    }
                } else if (tokenResponse.error == "authorization_pending") {
                    runOnUiThread {
                        handler.postDelayed({ pollForToken() }, pollIntervalMs)
                    }
                } else if (tokenResponse.error == "slow_down") {
                    pollIntervalMs += SLOW_DOWN_INCREMENT_MS
                    runOnUiThread {
                        handler.postDelayed({ pollForToken() }, pollIntervalMs)
                    }
                } else if (tokenResponse.error == "expired_token") {
                    runOnUiThread {
                        isPolling = false
                        showLoading(false)
                        retryButton.visibility = View.VISIBLE
                    }
                } else if (tokenResponse.error == "access_denied") {
                    runOnUiThread {
                        isPolling = false
                        showLoading(false)
                        retryButton.visibility = View.VISIBLE
                    }
                } else {
                    runOnUiThread {
                        handler.postDelayed({ pollForToken() }, pollIntervalMs)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    handler.postDelayed({ pollForToken() }, pollIntervalMs * 2)
                }
            }
        }.start()
    }

    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
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
                handler.postDelayed({
                    webView.loadUrl(url)
                }, 1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isPolling = false
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    }
}
