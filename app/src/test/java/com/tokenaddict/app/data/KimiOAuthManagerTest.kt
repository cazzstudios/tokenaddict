package com.tokenaddict.app.data

import com.tokenaddict.app.data.model.ApiException
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KimiOAuthManagerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var oauthManager: KimiOAuthManager
    private lateinit var gson: Gson

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        gson = Gson()

        val redirectClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val path = original.url.encodedPath
                val newUrl = mockWebServer.url(path)
                chain.proceed(original.newBuilder().url(newUrl).build())
            }
            .build()

        oauthManager = KimiOAuthManager(redirectClient, gson)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `DeviceCodeResponse parses all fields correctly`() {
        val json = """
            {
                "device_code": "abc123device",
                "user_code": "DEF-GHI",
                "verification_uri": "https://auth.kimi.com/activate",
                "expires_in": 900
            }
        """.trimIndent()

        val response = gson.fromJson(json, KimiOAuthManager.DeviceCodeResponse::class.java)

        assertEquals("abc123device", response.deviceCode)
        assertEquals("DEF-GHI", response.userCode)
        assertEquals("https://auth.kimi.com/activate", response.verificationUri)
        assertEquals(900, response.expiresIn)
    }

    @Test
    fun `TokenResponse parses success fields correctly`() {
        val json = """
            {
                "access_token": "new_access_token",
                "refresh_token": "new_refresh_token",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
        """.trimIndent()

        val response = gson.fromJson(json, KimiOAuthManager.TokenResponse::class.java)

        assertEquals("new_access_token", response.accessToken)
        assertEquals("new_refresh_token", response.refreshToken)
        assertEquals(3600L, response.expiresIn)
        assertEquals("Bearer", response.tokenType)
        assertNull(response.error)
    }

    @Test
    fun `TokenResponse parses authorization_pending error`() {
        val json = """{"error":"authorization_pending"}"""

        val response = gson.fromJson(json, KimiOAuthManager.TokenResponse::class.java)

        assertNull(response.accessToken)
        assertNull(response.refreshToken)
        assertNull(response.expiresIn)
        assertEquals("authorization_pending", response.error)
    }

    @Test
    fun `TokenResponse parses slow_down error`() {
        val json = """{"error":"slow_down"}"""

        val response = gson.fromJson(json, KimiOAuthManager.TokenResponse::class.java)

        assertNull(response.accessToken)
        assertEquals("slow_down", response.error)
    }

    @Test
    fun `TokenResponse parses expired_token error`() {
        val json = """{"error":"expired_token"}"""

        val response = gson.fromJson(json, KimiOAuthManager.TokenResponse::class.java)

        assertNull(response.accessToken)
        assertEquals("expired_token", response.error)
    }

    @Test
    fun `TokenResponse parses access_denied error`() {
        val json = """{"error":"access_denied"}"""

        val response = gson.fromJson(json, KimiOAuthManager.TokenResponse::class.java)

        assertNull(response.accessToken)
        assertEquals("access_denied", response.error)
    }

    @Test
    fun `requestDeviceCode makes POST to device_authorization endpoint`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "device_code": "test_device_code",
                        "user_code": "ABC-XYZ",
                        "verification_uri": "https://auth.kimi.com/activate",
                        "expires_in": 600
                    }
                """.trimIndent())
        )

        val result = oauthManager.requestDeviceCode()

        assertEquals("test_device_code", result.deviceCode)
        assertEquals("ABC-XYZ", result.userCode)
        assertEquals("https://auth.kimi.com/activate", result.verificationUri)
        assertEquals(600, result.expiresIn)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/oauth/device_authorization", recordedRequest.path)
        assertEquals("KimiCLI/1.5", recordedRequest.getHeader("User-Agent"))
        assertEquals("application/json", recordedRequest.getHeader("Accept"))
        val deviceBody = recordedRequest.body.readUtf8()
        assertTrue(deviceBody.contains("client_id="))
    }

    @Test
    fun `requestDeviceCode sends client id`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "device_code": "test_device_code",
                        "user_code": "ABC-XYZ",
                        "verification_uri": "https://auth.kimi.com/activate",
                        "expires_in": 600
                    }
                """.trimIndent())
        )

        oauthManager.requestDeviceCode()

        val recordedRequest = mockWebServer.takeRequest()
        val deviceBody = recordedRequest.body.readUtf8()
        assertTrue(deviceBody.contains("client_id=17e5f671-d194-4dfb-9706-5516cb48c098"))
    }

    @Test
    fun `requestDeviceCode throws NetworkError on non-200 response`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        try {
            oauthManager.requestDeviceCode()
            fail("Expected ApiException.NetworkError")
        } catch (e: ApiException.NetworkError) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `requestDeviceCode throws ParseError on invalid JSON`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not valid json {{{")
        )

        try {
            oauthManager.requestDeviceCode()
            fail("Expected ApiException.ParseError")
        } catch (e: ApiException.ParseError) {
            assertTrue(e.message!!.contains("Failed to parse device code response"))
        }
    }

    @Test
    fun `pollForToken returns token on successful authorization`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "access_token": "poll_access_token",
                        "refresh_token": "poll_refresh_token",
                        "expires_in": 3600,
                        "token_type": "Bearer"
                    }
                """.trimIndent())
        )

        val result = oauthManager.pollForToken("device_code_123")

        assertEquals("poll_access_token", result.accessToken)
        assertEquals("poll_refresh_token", result.refreshToken)
        assertEquals(3600L, result.expiresIn)
        assertNull(result.error)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/oauth/token", recordedRequest.path)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("device_code_123"))
        assertTrue(body.contains("urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
        assertTrue(body.contains("client_id=17e5f671-d194-4dfb-9706-5516cb48c098"))
    }

    @Test
    fun `pollForToken sends client id`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "access_token": "poll_access_token",
                        "refresh_token": "poll_refresh_token",
                        "expires_in": 3600,
                        "token_type": "Bearer"
                    }
                """.trimIndent())
        )

        oauthManager.pollForToken("device_code_123")

        val recordedRequest = mockWebServer.takeRequest()
        val pollBody = recordedRequest.body.readUtf8()
        assertTrue(pollBody.contains("client_id=17e5f671-d194-4dfb-9706-5516cb48c098"))
    }

    @Test
    fun `pollForToken returns authorization_pending`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"authorization_pending"}""")
        )

        val result = oauthManager.pollForToken("device_code_456")

        assertNull(result.accessToken)
        assertEquals("authorization_pending", result.error)
    }

    @Test
    fun `pollForToken returns slow_down`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"slow_down"}""")
        )

        val result = oauthManager.pollForToken("device_code_789")

        assertNull(result.accessToken)
        assertEquals("slow_down", result.error)
    }

    @Test
    fun `pollForToken returns expired_token`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"expired_token"}""")
        )

        val result = oauthManager.pollForToken("device_code_expired")

        assertNull(result.accessToken)
        assertEquals("expired_token", result.error)
    }

    @Test
    fun `pollForToken returns access_denied`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"access_denied"}""")
        )

        val result = oauthManager.pollForToken("device_code_denied")

        assertNull(result.accessToken)
        assertEquals("access_denied", result.error)
    }

    @Test
    fun `pollForToken throws ParseError on invalid JSON`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("garbage{{{")
        )

        try {
            oauthManager.pollForToken("device_code_bad")
            fail("Expected ApiException.ParseError")
        } catch (e: ApiException.ParseError) {
            assertTrue(e.message!!.contains("Failed to parse token response"))
        }
    }

    @Test
    fun `refreshAccessToken returns new tokens`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "access_token": "refreshed_access",
                        "refresh_token": "refreshed_refresh",
                        "expires_in": 7200,
                        "token_type": "Bearer"
                    }
                """.trimIndent())
        )

        val result = oauthManager.refreshAccessToken("old_refresh_token")

        assertEquals("refreshed_access", result.accessToken)
        assertEquals("refreshed_refresh", result.refreshToken)
        assertEquals(7200L, result.expiresIn)
        assertNull(result.error)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/oauth/token", recordedRequest.path)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("old_refresh_token"))
        assertTrue(body.contains("client_id=17e5f671-d194-4dfb-9706-5516cb48c098"))
    }

    @Test
    fun `refreshAccessToken throws Unauthorized on 401`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        try {
            oauthManager.refreshAccessToken("bad_refresh_token")
            fail("Expected ApiException.Unauthorized")
        } catch (e: ApiException.Unauthorized) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `refreshAccessToken throws Forbidden on 403`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
        )

        try {
            oauthManager.refreshAccessToken("forbidden_refresh_token")
            fail("Expected ApiException.Forbidden")
        } catch (e: ApiException.Forbidden) {
            assertTrue(e.message!!.contains("403"))
        }
    }

    @Test
    fun `refreshAccessToken throws Unauthorized on 400 invalid_grant`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"invalid_grant","error_description":"The provided authorization grant is invalid"}""")
        )

        try {
            oauthManager.refreshAccessToken("expired_refresh_token")
            fail("Expected ApiException.Unauthorized")
        } catch (e: ApiException.Unauthorized) {
            assertTrue(e.message!!.contains("400"))
            assertTrue(e.message!!.contains("invalid_grant"))
        }
    }

    @Test
    fun `refreshAccessToken throws NetworkError on other non-200`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        try {
            oauthManager.refreshAccessToken("bad_refresh_token")
            fail("Expected ApiException.NetworkError")
        } catch (e: ApiException.NetworkError) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `refreshAccessToken throws ParseError on invalid JSON`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{broken json")
        )

        try {
            oauthManager.refreshAccessToken("refresh_token_parse")
            fail("Expected ApiException.ParseError")
        } catch (e: ApiException.ParseError) {
            assertTrue(e.message!!.contains("Failed to parse refresh response"))
        }
    }
}
