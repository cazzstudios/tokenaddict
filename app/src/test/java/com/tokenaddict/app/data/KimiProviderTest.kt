package com.tokenaddict.app.data

import com.tokenaddict.app.TestUtils
import com.tokenaddict.app.data.model.KimiRateLimit
import com.tokenaddict.app.data.model.KimiRateLimitDetail
import com.tokenaddict.app.data.model.KimiRateLimitWindow
import com.tokenaddict.app.data.model.KimiUsageResponse
import com.tokenaddict.app.data.model.KimiUsageWindow
import com.google.gson.Gson
import okhttp3.OkHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KimiProviderTest {

    @Mock
    private lateinit var mockTokenManager: KimiTokenManager

    private lateinit var provider: KimiProvider
    private lateinit var gson: Gson

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        gson = Gson()

        val client = OkHttpClient.Builder().build()
        provider = KimiProvider(client, gson, mockTokenManager)
    }

    @Test
    fun `isLoggedIn returns true when token exists and not expired`() = runBlocking {
        `when`(mockTokenManager.getAccessToken()).thenReturn("valid_token")
        `when`(mockTokenManager.isAccessTokenValid()).thenReturn(true)

        assertTrue(provider.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns false when no token`() = runBlocking {
        `when`(mockTokenManager.getAccessToken()).thenReturn(null)
        `when`(mockTokenManager.isAccessTokenValid()).thenReturn(false)

        assertFalse(provider.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns false when token expired`() = runBlocking {
        `when`(mockTokenManager.getAccessToken()).thenReturn("expired_token")
        `when`(mockTokenManager.isAccessTokenValid()).thenReturn(false)

        assertFalse(provider.isLoggedIn())
    }

    @Test
    fun `logout clears tokens`() {
        provider.logout()

        verify(mockTokenManager).clearTokens()
    }

    @Test
    fun `provider has correct id and displayName`() {
        assertEquals("kimi", provider.id)
        assertEquals("Kimi", provider.displayName)
        assertEquals("https://api.kimi.com/coding/v1", provider.baseUrl)
        assertEquals("https://auth.kimi.com", provider.loginUrl)
    }

    private inner class TestableKimiProvider(
        private val response: KimiUsageResponse
    ) : KimiProvider(OkHttpClient.Builder().build(), Gson(), mockTokenManager) {
        override fun fetchUsageResponse(): KimiUsageResponse = response
    }

    @Test
    fun `getUsage maps usage to weekly and limits 300min to fiveHour`() = runBlocking {
        val response = KimiUsageResponse(
            usage = KimiUsageWindow(
                limit = "100",
                remaining = "30",
                resetTime = TestUtils.createFutureResetTime(168) // 1 week from now
            ),
            limits = listOf(
                KimiRateLimit(
                    duration = 300,
                    timeUnit = "TIME_UNIT_MINUTE",
                    window = KimiRateLimitWindow(duration = 300, timeUnit = "TIME_UNIT_MINUTE"),
                    detail = KimiRateLimitDetail(
                        limit = "100",
                        remaining = "60",
                        resetTime = TestUtils.createFutureResetTime(2) // 2 hours from now
                    )
                )
            )
        )
        val testProvider = TestableKimiProvider(response)
        val result = testProvider.getUsage()

        // weekly = (100 - 30) / 100 * 100 = 70%
        assertEquals(70.0, result.weeklyUtilization, 0.01)
        // 5-hour = (100 - 60) / 100 * 100 = 40%
        assertEquals(40.0, result.utilization, 0.01)
        assertNotNull(result.weeklyResetsAt)
        assertNotNull(result.resetsAt)
        assertEquals("kimi", result.providerId)
        assertFalse(result.isReset)
        assertFalse(result.weeklyIsReset)
    }

    @Test
    fun `getUsage uses legacy flat fields when detail is null`() = runBlocking {
        val response = KimiUsageResponse(
            usage = KimiUsageWindow(
                limit = "50",
                remaining = "25",
                resetTime = TestUtils.createFutureResetTime(168)
            ),
            limits = listOf(
                KimiRateLimit(
                    duration = 300,
                    timeUnit = "TIME_UNIT_MINUTE",
                    limit = 100,
                    remaining = 20  // flat Int fields, no detail
                )
            )
        )
        val testProvider = TestableKimiProvider(response)
        val result = testProvider.getUsage()

        // weekly = (50 - 25) / 50 * 100 = 50%
        assertEquals(50.0, result.weeklyUtilization, 0.01)
        // 5-hour = (100 - 20) / 100 * 100 = 80%
        assertEquals(80.0, result.utilization, 0.01)
        // Legacy flat fallback has no resetTime
        assertNull(result.resetsAt)
        assertFalse(result.isReset)
    }

    @Test
    fun `getUsage returns zero fiveHour when no 300min limit entry`() = runBlocking {
        val response = KimiUsageResponse(
            usage = KimiUsageWindow(
                limit = "100",
                remaining = "30",
                resetTime = TestUtils.createFutureResetTime(168)
            ),
            limits = emptyList()
        )
        val testProvider = TestableKimiProvider(response)
        val result = testProvider.getUsage()

        // weekly = 70%
        assertEquals(70.0, result.weeklyUtilization, 0.01)
        // No 5-hour entry → utilization defaults to 0
        assertEquals(0.0, result.utilization, 0.01)
        assertNull(result.resetsAt)
        assertFalse(result.isReset)
    }

    @Test
    fun `getUsage returns zero fiveHour when limits is null`() = runBlocking {
        val response = KimiUsageResponse(
            usage = KimiUsageWindow(
                limit = "100",
                remaining = "30",
                resetTime = TestUtils.createFutureResetTime(168)
            ),
            limits = null
        )
        val testProvider = TestableKimiProvider(response)
        val result = testProvider.getUsage()

        // weekly = 70%
        assertEquals(70.0, result.weeklyUtilization, 0.01)
        // null limits → utilization defaults to 0
        assertEquals(0.0, result.utilization, 0.01)
        assertNull(result.resetsAt)
        assertFalse(result.isReset)
    }
}
