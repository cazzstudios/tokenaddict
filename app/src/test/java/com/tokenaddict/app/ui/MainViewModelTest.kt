package com.tokenaddict.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.SessionManager
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var application: Application
    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences
    private lateinit var sessionPrefs: SharedPreferences
    private lateinit var kimiTokenManager: KimiTokenManager

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        prefs = application.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        sessionPrefs = application.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        sessionPrefs.edit().clear().commit()

        val testPrefs = application.getSharedPreferences("test_kimi_tokens", Context.MODE_PRIVATE)
        testPrefs.edit().clear().commit()
        val securePrefs = SecurePreferences.create(testPrefs)
        val testGson = Gson()
        val testOAuthManager = KimiOAuthManager(OkHttpClient(), testGson)
        kimiTokenManager = KimiTokenManager(securePrefs, testOAuthManager, testGson)

        SessionManager.encryptedPrefsFactory = { ctx, prefsName ->
            val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            SecurePreferences.create(prefs)
        }

        viewModel = MainViewModel(application, kimiTokenManager)
    }

    @After
    fun tearDown() {
        SessionManager.encryptedPrefsFactory = null
    }

    private fun callLoadUsageData(providerId: String) {
        val method = MainViewModel::class.java.getDeclaredMethod("loadUsageData", String::class.java)
        method.isAccessible = true
        method.invoke(viewModel, providerId)
    }

    @Test
    fun resetAvailable_timeRemainingIsEmpty() {
        val futureMillis = System.currentTimeMillis() + 3_600_000L
        prefs.edit()
            .putFloat("utilization", 0.75f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", true)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertEquals("", state.timeRemaining)
        assertTrue(state.isReset)
    }

    @Test
    fun futureReset_timeRemainingShowsHoursAndMinutes() {
        val futureMillis = System.currentTimeMillis() + 3 * 3_600_000L
        prefs.edit()
            .putFloat("utilization", 50.0f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected 'Xh Ym' pattern, got: ${state.timeRemaining}",
            state.timeRemaining.matches(Regex("\\d+h \\d+m")))
    }

    @Test
    fun expiredReset_timeRemainingIsEmpty() {
        val pastMillis = System.currentTimeMillis() - 3_600_000L
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", pastMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertEquals("", state.timeRemaining)
        assertFalse(state.isReset)
    }

    @Test
    fun checkSession_claude_withPersistedSession_emitsUsageData() {
        val sessionCookies = """["sk-ant-sid01=test-session-key; path=/; domain=.claude.ai"]"""
        sessionPrefs.edit().putString("cookies_v2_claude", sessionCookies).commit()

        val futureMillis = System.currentTimeMillis() + 3_600_000L
        prefs.edit()
            .putFloat("utilization", 0.85f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        val freshViewModel = MainViewModel(application, kimiTokenManager)

        val state = freshViewModel.claudeState.value
        assertTrue("Expected UsageData but got ${state?.let { it::class.simpleName }}",
            state is MainViewModel.UiState.UsageData)
    }

    @Test
    fun checkSession_claude_noSession_emitsLoggedOut() {
        sessionPrefs.edit().clear().commit()

        val freshViewModel = MainViewModel(application, kimiTokenManager)

        val state = freshViewModel.claudeState.value
        assertTrue("Expected LoggedOut but got ${state?.let { it::class.simpleName }}",
            state is MainViewModel.UiState.LoggedOut)
    }

    @Test
    fun weeklyResetInFuture_timeRemainingShowsDaysHoursMinutes() {
        val futureMillis = System.currentTimeMillis() + 50 * 3_600_000L
        prefs.edit()
            .putFloat("utilization", 50.0f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.8f)
            .putLong("weekly_resets_at", System.currentTimeMillis() + 100 * 3_600_000L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected 'Xd Xh Ym' pattern, got: ${state.weeklyResetsIn}",
            state.weeklyResetsIn.matches(Regex("\\d+d \\d+h \\d+m")))
    }

    @Test
    fun missingFiveHourData_showsNAResetsAt() {
        prefs.edit()
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertEquals("N/A", state.resetsAt)
    }

    @Test
    fun kimiState_loadsWeeklyKeys() {
        val kimiPrefs = application.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
        val futureMillis = System.currentTimeMillis() + 3_600_000L
        kimiPrefs.edit()
            .putFloat("utilization", 0.6f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.45f)
            .putLong("weekly_resets_at", futureMillis)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("kimi")

        val state = viewModel.kimiState.value as MainViewModel.UiState.UsageData
        assertEquals(0.45, state.weeklyUtilization, 0.01)
        assertTrue("Expected weeklyResetsIn to be non-empty", state.weeklyResetsIn.isNotEmpty())
    }

    @Test
    fun kimiWeeklyResetOver24h_showsDaysInFormat() {
        val kimiPrefs = application.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
        val futureMillis = System.currentTimeMillis() + 3_600_000L
        kimiPrefs.edit()
            .putFloat("utilization", 0.6f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.8f)
            .putLong("weekly_resets_at", System.currentTimeMillis() + 100 * 3_600_000L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("kimi")

        val state = viewModel.kimiState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected 'Xd Xh Ym' pattern, got: ${state.weeklyResetsIn}",
            state.weeklyResetsIn.matches(Regex("\\d+d \\d+h \\d+m")))
    }

    @Test
    fun loadUsageData_setsServiceChangedTrue_whenPrefFlagIsTrue() {
        val futureMillis = System.currentTimeMillis() + 3_600_000L
        prefs.edit()
            .putFloat("utilization", 50.0f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .putBoolean("claude_service_changed", true)
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected serviceChanged true", state.serviceChanged)
    }

    @Test
    fun loadUsageData_setsServiceChangedFalse_forKimi() {
        val kimiPrefs = application.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
        val futureMillis = System.currentTimeMillis() + 3_600_000L
        kimiPrefs.edit()
            .putFloat("utilization", 0.6f)
            .putLong("resets_at", futureMillis)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.45f)
            .putLong("weekly_resets_at", futureMillis)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .putBoolean("claude_service_changed", true)
            .commit()

        callLoadUsageData("kimi")

        val state = viewModel.kimiState.value as MainViewModel.UiState.UsageData
        assertFalse("Expected serviceChanged false for Kimi", state.serviceChanged)
    }

    @Test
    fun formatLimitCountdown_withDays_returnsDdHhMmFormat() {
        val threeDaysFromNow = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", threeDaysFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected dd:hh:mm format, got: ${state.limitCountdownText}",
            state.limitCountdownText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
        assertEquals("Countdown should have 3 parts (DD:HH:MM), not 4",
            3, state.limitCountdownText.split(":").size)
        assertTrue("countdownHasDays should be true when days > 0", state.countdownHasDays)
    }

    @Test
    fun formatLimitCountdown_withDays_returnsHhMmFormat() {
        val threeDaysFromNow = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", threeDaysFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected dd:hh:mm format (DD:HH:MM), got: ${state.limitCountdownText}",
            state.limitCountdownText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun formatLimitCountdown_lessThanOneDay_returnsHhMmSsFormat() {
        val tenHoursFromNow = System.currentTimeMillis() + 10L * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", tenHoursFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected hh:mm:ss format for less than 1 day, got: ${state.limitCountdownText}",
            state.limitCountdownText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
        assertFalse("Should not show days when less than 1 day", state.limitCountdownText.contains("days"))
    }

    @Test
    fun formatLimitCountdown_notAtLimit_returnsEmpty() {
        val threeHoursFromNow = System.currentTimeMillis() + 3L * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 50.0f)
            .putLong("resets_at", threeHoursFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertEquals("", state.limitCountdownText)
    }

    @Test
    fun formatLimitCountdown_lessThanOneDay_showsHhMmSs_only() {
        val tenHoursFromNow = System.currentTimeMillis() + 10L * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", tenHoursFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected hh:mm:ss format for less than 1 day, got: ${state.limitCountdownText}",
            state.limitCountdownText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun formatLimitCountdown_bothLimitsHit_usesLaterReset() {
        val twoHoursFromNow = System.currentTimeMillis() + 2L * 60 * 60 * 1000
        val fiveDaysFromNow = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", twoHoursFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 100.0f)
            .putLong("weekly_resets_at", fiveDaysFromNow)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected dd:hh:mm format using later reset, got: ${state.limitCountdownText}",
            state.limitCountdownText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun formatLimitCountdown_onlyFiveHourHit_usesFiveHourReset() {
        val threeHoursFromNow = System.currentTimeMillis() + 3L * 60 * 60 * 1000
        val fiveDaysFromNow = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", threeHoursFromNow)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 50.0f)
            .putLong("weekly_resets_at", fiveDaysFromNow)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertTrue("Expected hh:mm:ss (not dd:hh:mm:ss) for 5-hour-only limit, got: ${state.limitCountdownText}",
            state.limitCountdownText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
        assertEquals("Countdown should have 3 parts (HH:MM:SS), not 4 (DD:HH:MM:SS)",
            3, state.limitCountdownText.split(":").size)
    }

    @Test
    fun formatLimitCountdown_expiredReset_returnsEmpty() {
        val oneHourAgo = System.currentTimeMillis() - 1L * 60 * 60 * 1000
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", oneHourAgo)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertEquals("", state.limitCountdownText)
    }

    @Test
    fun formatLimitCountdown_zeroResetsAt_returnsEmpty() {
        prefs.edit()
            .putFloat("utilization", 100.0f)
            .putLong("resets_at", 0L)
            .putBoolean("is_reset", false)
            .putFloat("weekly_utilization", 0.0f)
            .putLong("weekly_resets_at", 0L)
            .putBoolean("weekly_is_reset", false)
            .putLong("last_checked", System.currentTimeMillis())
            .commit()

        callLoadUsageData("claude")

        val state = viewModel.claudeState.value as MainViewModel.UiState.UsageData
        assertEquals("", state.limitCountdownText)
    }
}
