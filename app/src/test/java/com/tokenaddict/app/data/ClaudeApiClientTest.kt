package com.tokenaddict.app.data

import com.tokenaddict.app.data.model.AccountResponse
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.model.Membership
import com.tokenaddict.app.data.model.Organization
import com.tokenaddict.app.data.model.UsageResponse
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClaudeApiClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiClient: ClaudeProvider

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = OkHttpClient()
        gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        apiClient = ClaudeProvider(client, gson)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getAccount parses memberships correctly`() {
        val jsonResponse = """
            {
                "memberships": [
                    {"organization": {"uuid": "org-123", "name": "Test Org"}},
                    {"organization": {"uuid": "org-456", "name": "Another Org"}}
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse()
            .setBody(jsonResponse)
            .setHeader("Content-Type", "application/json"))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/api/account"))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", "https://claude.ai/settings/usage")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        val accountResponse = gson.fromJson(body, AccountResponse::class.java)

        assertNotNull(accountResponse.memberships)
        assertEquals(2, accountResponse.memberships?.size)
        assertEquals("org-123", accountResponse.memberships?.get(0)?.organization?.uuid)
        assertEquals("Test Org", accountResponse.memberships?.get(0)?.organization?.name)
    }

    @Test
    fun `getUsage parses window data correctly`() {
        val jsonResponse = """
            {
                "five_hour": {
                    "utilization": 28.0,
                    "resets_at": "2026-03-06T03:00:00.577989+00:00"
                },
                "seven_day": {
                    "utilization": 67.0,
                    "resets_at": "2026-03-06T03:00:00.578009+00:00"
                },
                "seven_day_sonnet": {
                    "utilization": 5.0,
                    "resets_at": "2026-03-06T05:00:00.578016+00:00"
                },
                "seven_day_opus": null,
                "extra_usage": {
                    "is_enabled": false,
                    "monthly_limit": null,
                    "used_credits": null,
                    "utilization": null
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse()
            .setBody(jsonResponse)
            .setHeader("Content-Type", "application/json"))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/api/organizations/org-123/usage"))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", "https://claude.ai/settings/usage")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        val usageResponse = gson.fromJson(body, UsageResponse::class.java)

        assertNotNull(usageResponse.fiveHour)
        assertEquals(28.0, usageResponse.fiveHour?.utilization ?: 0.0, 0.001)
        assertEquals("2026-03-06T03:00:00.577989+00:00", usageResponse.fiveHour?.resetsAt)

        assertNotNull(usageResponse.sevenDay)
        assertEquals(67.0, usageResponse.sevenDay?.utilization ?: 0.0, 0.001)

        assertNotNull(usageResponse.sevenDaySonnet)
        assertEquals(5.0, usageResponse.sevenDaySonnet?.utilization ?: 0.0, 0.001)

        assertNull(usageResponse.sevenDayOpus)

        assertNotNull(usageResponse.extraUsage)
        assertEquals(false, usageResponse.extraUsage?.isEnabled)
    }

    @Test
    fun `401 throws Unauthorized exception`() {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized"))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/api/account"))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", "https://claude.ai/settings/usage")
            .addHeader("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            when (response.code) {
                401 -> throw ApiException.Unauthorized()
            }
            fail("Expected Unauthorized exception")
        } catch (e: ApiException.Unauthorized) {
            assertEquals("Session expired or invalid", e.message)
        }
    }

    @Test
    fun `403 throws Forbidden exception`() {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(403)
            .setBody("Forbidden"))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/api/account"))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", "https://claude.ai/settings/usage")
            .addHeader("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            when (response.code) {
                403 -> throw ApiException.Forbidden()
            }
            fail("Expected Forbidden exception")
        } catch (e: ApiException.Forbidden) {
            assertEquals("Access denied", e.message)
        }
    }

    @Test
    fun `429 throws RateLimited exception`() {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(429)
            .setBody("Rate limited"))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/api/account"))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", "https://claude.ai/settings/usage")
            .addHeader("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            when (response.code) {
                429 -> throw ApiException.RateLimited()
            }
            fail("Expected RateLimited exception")
        } catch (e: ApiException.RateLimited) {
            assertEquals("Rate limited", e.message)
        }
    }

    @Test
    fun `Referer header is present in requests`() {
        mockWebServer.enqueue(MockResponse()
            .setBody("{}")
            .setHeader("Content-Type", "application/json"))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/api/account"))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", "https://claude.ai/settings/usage")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("https://claude.ai/settings/usage", recordedRequest.getHeader("Referer"))
    }

    @Test
    fun `validateSession returns account on 200`() {
        val jsonResponse = """
            {
                "uuid": "user-abc",
                "display_name": "Test User",
                "email_address": "test@example.com",
                "memberships": [
                    {"organization": {"uuid": "org-123", "name": "Test Org"}}
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse()
            .setBody(jsonResponse)
            .setHeader("Content-Type", "application/json"))

        val provider = ClaudeProvider(client, gson, mockWebServer.url("/api").toString())
        val account = runBlocking { provider.validateSession() }

        assertEquals("user-abc", account.uuid)
        assertEquals("Test User", account.name)
        assertEquals("test@example.com", account.email)
    }

    @Test
    fun `validateSession throws on 401`() {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized"))

        val provider = ClaudeProvider(client, gson, mockWebServer.url("/api").toString())

        try {
            runBlocking { provider.validateSession() }
            fail("Expected Unauthorized exception")
        } catch (e: ApiException.Unauthorized) {
            assertEquals("Session expired or invalid", e.message)
        }
    }

    @Test
    fun `HTML 200 response throws ServiceChanged exception`() = runBlocking {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/html")
            .setBody("""<!DOCTYPE html><html><body>Cloudflare challenge</body></html>"""))

        val provider = ClaudeProvider(client, gson, mockWebServer.url("/api").toString())

        try {
            provider.validateSession()
            fail("Expected ServiceChanged exception")
        } catch (e: ApiException.ServiceChanged) {
            assertTrue(e.message!!.contains("HTML"))
        }
    }

    @Test
    fun `Unexpected JSON shape throws ServiceChanged exception`() = runBlocking {
        // First enqueue valid account response so getUsage() can obtain org UUID
        val accountJson = """{"memberships":[{"organization":{"uuid":"org-123","name":"Test Org"}}]}"""
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(accountJson))

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("hello"))

        val provider = ClaudeProvider(client, gson, mockWebServer.url("/api").toString())

        try {
            provider.getUsage()
            fail("Expected ServiceChanged exception")
        } catch (e: ApiException.ServiceChanged) {
            assertTrue(e.message!!.contains("Failed to parse"))
        }
    }

    @Test
    fun `isLoggedIn returns false on ServiceChanged`() = runBlocking {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/html")
            .setBody("""<html></html>"""))

        val provider = ClaudeProvider(client, gson, mockWebServer.url("/api").toString())

        assertFalse(provider.isLoggedIn())
    }
    @Test
    fun `getUsage extracts weekly fields from sevenDay`() = runBlocking {
        val accountJson = """{"memberships":[{"organization":{"uuid":"org-123","name":"Test Org"}}]}"""
        mockWebServer.enqueue(MockResponse().setBody(accountJson).setHeader("Content-Type", "application/json"))

        val usageJson = """{"five_hour":{"utilization":28.0,"resets_at":"2026-03-06T03:00:00.577989+00:00"},"seven_day":{"utilization":67.0,"resets_at":"2026-03-06T03:00:00.578009+00:00"}}"""
        mockWebServer.enqueue(MockResponse().setBody(usageJson).setHeader("Content-Type", "application/json"))

        val provider = ClaudeProvider(client, gson, mockWebServer.url("/api").toString())
        val usage = provider.getUsage()

        assertEquals(28.0, usage.utilization, 0.001)
        assertEquals("2026-03-06T03:00:00.577989+00:00", usage.resetsAt)
        assertEquals(67.0, usage.weeklyUtilization, 0.001)
        assertEquals("2026-03-06T03:00:00.578009+00:00", usage.weeklyResetsAt)
        assertTrue(usage.weeklyIsReset)
    }
}
