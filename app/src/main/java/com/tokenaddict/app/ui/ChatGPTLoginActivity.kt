package com.tokenaddict.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tokenaddict.app.R
import com.tokenaddict.app.data.ChatGPTSessionManager

class ChatGPTLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var sessionManager: ChatGPTSessionManager

    private var loginSuccessCalled = false
    private var pendingAsyncResult: String? = null

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            checkLoginState()
            webView.postDelayed(this, PERIODIC_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = ChatGPTSessionManager(this)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorText = findViewById(R.id.errorText)
        retryButton = findViewById(R.id.retryButton)

        retryButton.setOnClickListener {
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            clearSessionAndLoad(LOGIN_URL)
        }

        setupWebView()
        clearSessionAndLoad(LOGIN_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = USER_AGENT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "onPageFinished: $url")
                progressBar.visibility = View.GONE
                pendingAsyncResult = null
                checkLoginState()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showError("Failed to load page. Please check your connection and try again.")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                Log.d(TAG, "shouldOverrideUrlLoading: $url")
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress >= 100) {
                    checkLoginState()
                }
            }
        }
    }

    private fun clearSessionAndLoad(url: String) {
        CookieManager.getInstance().removeAllCookies { removed ->
            Log.d(TAG, "Cookies cleared: $removed")
            runOnUiThread {
                webView.clearCache(true)
                webView.loadUrl(url)
                webView.postDelayed(periodicCheckRunnable, PERIODIC_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkLoginState() {
        if (loginSuccessCalled) return

        val currentUrl = webView.url
        Log.d(TAG, "checkLoginState url=$currentUrl")

        checkPendingAsyncResult()
        fireAsyncSessionFetch()
        checkCookieBasedLogin()
    }

    private fun fireAsyncSessionFetch() {
        val fireJs = """
        (function() {
            if (window._gptPending) return;
            window._gptPending = true;
            window._gptResult = null;
            fetch('/api/auth/session', { credentials: 'include' })
              .then(function(r) { return r.text(); })
              .then(function(text) {
                  window._gptResult = text;
                  window._gptPending = false;
              })
              .catch(function() {
                  window._gptResult = 'not_logged_in';
                  window._gptPending = false;
              });
        })()
        """.trimIndent()

        webView.evaluateJavascript(fireJs, null)
    }

    private fun checkPendingAsyncResult() {
        val readJs = """
        (function() {
            if (window._gptResult) {
                var r = window._gptResult;
                window._gptResult = null;
                return r;
            }
            return 'not_ready';
        })()
        """.trimIndent()

        webView.evaluateJavascript(readJs) { value ->
            val cleanValue = unescapeJsValue(value) ?: return@evaluateJavascript
            if (cleanValue.isBlank() || cleanValue == "not_ready" || cleanValue == "not_logged_in") return@evaluateJavascript

            parseSessionResponse(cleanValue, "async-fetch")
        }
    }

    private fun checkCookieBasedLogin() {
        val chatgptCookies = CookieManager.getInstance().getCookie("https://chatgpt.com") ?: ""
        val openaiCookies = CookieManager.getInstance().getCookie("https://chat.openai.com") ?: ""
        Log.d(TAG, "Cookie check chatgpt=${chatgptCookies.take(100)} openai=${openaiCookies.take(100)}")

        val allCookies = "$chatgptCookies;$openaiCookies"
        val hasSessionCookie = allCookies.contains("session-token", ignoreCase = true) ||
                allCookies.contains("__Secure-next-auth", ignoreCase = true)

        if (hasSessionCookie) {
            Log.d(TAG, "Session cookie found, trying cookie-based auth")
            val cookieJs = """
            (function() {
                var cookies = document.cookie.split(';');
                for (var i = 0; i < cookies.length; i++) {
                    var c = cookies[i].trim();
                    if (c.indexOf('session-token') !== -1 || c.indexOf('__Secure-next-auth') !== -1) {
                        var eq = c.indexOf('=');
                        if (eq !== -1) return c.substring(eq + 1);
                    }
                }
                return 'no_token';
            })()
            """.trimIndent()

            webView.evaluateJavascript(cookieJs) { value ->
                Log.d(TAG, "Cookie token result: ${value?.take(200)}")
                val clean = unescapeJsValue(value) ?: return@evaluateJavascript
                if (clean.isBlank() || clean == "no_token" || clean == "null") {
                    attemptDirectSessionFetch()
                    return@evaluateJavascript
                }

                sessionManager.saveCredentials(clean, null)
                saveSessionAndFinish()
            }
        }
    }

    private fun attemptDirectSessionFetch() {
        if (loginSuccessCalled) return

        val js = """
        (function() {
            if (window._gptDirectPending) return;
            window._gptDirectPending = true;
            window._gptDirectResult = null;
            fetch('https://chatgpt.com/api/auth/session', {
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
            })
            .then(function(r) {
                if (r.ok) return r.text();
                return fetch('https://chat.openai.com/api/auth/session', {
                    credentials: 'include',
                    headers: { 'Accept': 'application/json' }
                }).then(function(r2) { return r2.ok ? r2.text() : 'not_logged_in'; });
            })
            .then(function(text) {
                window._gptDirectResult = text;
                window._gptDirectPending = false;
            })
            .catch(function() {
                window._gptDirectResult = 'not_logged_in';
                window._gptDirectPending = false;
            });
        })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)

        webView.postDelayed({
            val readJs = """
            (function() {
                if (window._gptDirectResult) {
                    var r = window._gptDirectResult;
                    window._gptDirectResult = null;
                    return r;
                }
                return 'not_ready';
            })()
            """.trimIndent()

            webView.evaluateJavascript(readJs) { value ->
                val cleanValue = unescapeJsValue(value) ?: return@evaluateJavascript
                if (cleanValue.isBlank() || cleanValue == "not_ready" || cleanValue == "not_logged_in") return@evaluateJavascript
                parseSessionResponse(cleanValue, "direct-fetch")
            }
        }, 3000L)
    }

    private fun parseSessionResponse(json: String, source: String) {
        if (loginSuccessCalled) return
        try {
            val stripped = json.replace("\uFEFF", "")
            val firstBrace = stripped.indexOf('{')
            val lastBrace = stripped.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
                Log.w(TAG, "No JSON object found in response from $source: ${stripped.take(200)}")
                return
            }
            val jsonString = stripped.substring(firstBrace, lastBrace + 1)

            val gson = com.google.gson.Gson()
            val authResponse = gson.fromJson(jsonString, Map::class.java)
            val accessToken = authResponse?.get("accessToken") as? String

            if (!accessToken.isNullOrBlank()) {
                val accountId = authResponse["account_id"] as? String
                Log.d(TAG, "Login detected via $source, accountId=$accountId")
                sessionManager.saveCredentials(accessToken, accountId)
                saveSessionAndFinish()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse auth response from $source: ${json.take(200)}", e)
        }
    }

    private fun saveSessionAndFinish() {
        if (loginSuccessCalled) return
        loginSuccessCalled = true
        webView.removeCallbacks(periodicCheckRunnable)
        sessionManager.saveCredentials(
            sessionManager.getAccessToken() ?: return,
            sessionManager.getAccountId()
        )
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showError(message: String) {
        webView.visibility = View.GONE
        progressBar.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun unescapeJsValue(value: String?): String? {
        if (value.isNullOrBlank() || value == "null") return null
        if (!value.startsWith("\"") || !value.endsWith("\"")) return value
        val inner = value.substring(1, value.length - 1)
        return inner
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.removeCallbacks(periodicCheckRunnable)
    }

    companion object {
        private const val TAG = "ChatGPTLoginActivity"
        private const val LOGIN_URL = "https://chat.openai.com"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val PERIODIC_CHECK_INTERVAL_MS = 2000L
    }
}
