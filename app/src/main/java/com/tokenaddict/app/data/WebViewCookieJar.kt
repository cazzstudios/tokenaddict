package com.tokenaddict.app.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val cookieString = cookie.toString()
            cookieManager.setCookie(url.toString(), cookieString)
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return parseCookies(url, cookieString)
    }

    private fun parseCookies(url: HttpUrl, cookieString: String): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        val cookiePairs = cookieString.split("; ")

        for (pair in cookiePairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotEmpty()) {
                    val cookie = Cookie.Builder()
                        .domain(url.host)
                        .path(url.encodedPath)
                        .name(name)
                        .value(value)
                        .build()
                    cookies.add(cookie)
                }
            }
        }

        return cookies
    }
}
