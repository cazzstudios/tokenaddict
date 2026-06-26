package com.tokenaddict.app.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tokenaddict.app.TestUtils
import com.tokenaddict.app.data.ClaudeProvider
import com.tokenaddict.app.data.KimiProvider
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class MultiProviderIntegrationTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockWorkerParams: WorkerParameters

    @Mock
    private lateinit var mockSessionManager: SessionManager

    @Mock
    private lateinit var mockClaudeProvider: ClaudeProvider

    @Mock
    private lateinit var mockClaudeNotificationScheduler: NotificationScheduler

    @Mock
    private lateinit var mockClaudeSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockClaudeSharedPreferencesEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockKimiProvider: KimiProvider

    @Mock
    private lateinit var mockKimiTokenManager: KimiTokenManager

    @Mock
    private lateinit var mockKimiNotificationScheduler: NotificationScheduler

    @Mock
    private lateinit var mockKimiSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockKimiSharedPreferencesEditor: SharedPreferences.Editor

    private lateinit var claudeWorker: ClaudeUsageWorker
    private lateinit var kimiWorker: KimiUsageWorker

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        `when`(mockContext.getSharedPreferences(eq("usage_prefs"), anyInt())).thenReturn(mockClaudeSharedPreferences)
        `when`(mockClaudeSharedPreferences.edit()).thenReturn(mockClaudeSharedPreferencesEditor)
        `when`(mockClaudeSharedPreferencesEditor.putFloat(anyString(), anyFloat())).thenReturn(mockClaudeSharedPreferencesEditor)
        `when`(mockClaudeSharedPreferencesEditor.putLong(anyString(), anyLong())).thenReturn(mockClaudeSharedPreferencesEditor)
        `when`(mockClaudeSharedPreferencesEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockClaudeSharedPreferencesEditor)

        `when`(mockContext.getSharedPreferences(eq("usage_prefs_kimi"), anyInt())).thenReturn(mockKimiSharedPreferences)
        `when`(mockKimiSharedPreferences.edit()).thenReturn(mockKimiSharedPreferencesEditor)
        `when`(mockKimiSharedPreferencesEditor.putFloat(anyString(), anyFloat())).thenReturn(mockKimiSharedPreferencesEditor)
        `when`(mockKimiSharedPreferencesEditor.putLong(anyString(), anyLong())).thenReturn(mockKimiSharedPreferencesEditor)
        `when`(mockKimiSharedPreferencesEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockKimiSharedPreferencesEditor)

        claudeWorker = ClaudeUsageWorker(
            mockContext,
            mockWorkerParams,
            mockSessionManager,
            mockClaudeProvider,
            mockClaudeNotificationScheduler
        )

        kimiWorker = KimiUsageWorker(
            mockContext,
            mockWorkerParams,
            mockKimiProvider,
            mockKimiTokenManager,
            mockKimiNotificationScheduler
        )
    }

    private fun stubClaudeLoggedIn(loggedIn: Boolean) {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(loggedIn)
    }

    private fun stubKimiLoggedIn(loggedIn: Boolean) {
        runBlocking {
            `when`(mockKimiProvider.isLoggedIn()).thenReturn(loggedIn)
        }
    }

    private fun stubClaudeUsage(usageInfo: com.tokenaddict.app.data.model.UsageInfo) {
        runBlocking {
            `when`(mockClaudeProvider.getUsage()).thenReturn(usageInfo)
        }
    }

    private fun stubKimiUsage(usageInfo: com.tokenaddict.app.data.model.UsageInfo) {
        runBlocking {
            `when`(mockKimiProvider.getUsage()).thenReturn(usageInfo)
        }
    }

    @Test
    fun `both workers return success when logged in`() {
        stubClaudeLoggedIn(true)
        stubKimiLoggedIn(true)
        stubClaudeUsage(TestUtils.createMockUsageInfo())
        stubKimiUsage(TestUtils.createMockUsageInfo())

        val claudeResult = runBlocking { claudeWorker.executeWork() }
        val kimiResult = runBlocking { kimiWorker.executeWork() }

        assertEquals(ListenableWorker.Result.success(), claudeResult)
        assertEquals(ListenableWorker.Result.success(), kimiResult)
    }

    @Test
    fun `both workers handle not-logged-in state independently`() {
        stubClaudeLoggedIn(false)
        stubKimiLoggedIn(false)

        val claudeResult = runBlocking { claudeWorker.executeWork() }
        val kimiResult = runBlocking { kimiWorker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), claudeResult)
        assertEquals(ListenableWorker.Result.failure(), kimiResult)

        runBlocking { verify(mockClaudeProvider, never()).getUsage() }
        runBlocking { verify(mockKimiProvider, never()).getUsage() }
    }

    @Test
    fun `Claude worker uses different SharedPreferences than Kimi worker`() {
        stubClaudeLoggedIn(true)
        stubKimiLoggedIn(true)
        stubClaudeUsage(TestUtils.createMockUsageInfo())
        stubKimiUsage(TestUtils.createMockUsageInfo())

        runBlocking { claudeWorker.executeWork() }
        runBlocking { kimiWorker.executeWork() }

        verify(mockContext).getSharedPreferences(eq("usage_prefs"), anyInt())
        verify(mockContext).getSharedPreferences(eq("usage_prefs_kimi"), anyInt())
    }

    @Test
    fun `notification scheduling is independent for each worker`() {
        stubClaudeLoggedIn(true)
        stubKimiLoggedIn(true)
        stubClaudeUsage(TestUtils.createMockUsageInfo(utilization = 100.0, resetsAt = TestUtils.createFutureResetTime(2)))
        stubKimiUsage(TestUtils.createMockUsageInfo(resetsAt = TestUtils.createExpiredResetTime(), isReset = true))

        runBlocking { claudeWorker.executeWork() }
        runBlocking { kimiWorker.executeWork() }

        verify(mockClaudeNotificationScheduler).scheduleResetNotification(anyLong())
        verify(mockKimiNotificationScheduler).cancelResetNotification()
    }

    @Test
    fun `Claude worker failure does not affect Kimi worker`() {
        stubClaudeLoggedIn(false)
        stubKimiLoggedIn(true)
        stubKimiUsage(TestUtils.createMockUsageInfo())

        val claudeResult = runBlocking { claudeWorker.executeWork() }
        val kimiResult = runBlocking { kimiWorker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), claudeResult)
        assertEquals(ListenableWorker.Result.success(), kimiResult)
    }

    @Test
    fun `Kimi worker failure does not affect Claude worker`() {
        stubClaudeLoggedIn(true)
        stubKimiLoggedIn(false)
        stubClaudeUsage(TestUtils.createMockUsageInfo())

        val claudeResult = runBlocking { claudeWorker.executeWork() }
        val kimiResult = runBlocking { kimiWorker.executeWork() }

        assertEquals(ListenableWorker.Result.success(), claudeResult)
        assertEquals(ListenableWorker.Result.failure(), kimiResult)
    }
}
