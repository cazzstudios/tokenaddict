package com.tokenaddict.app.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tokenaddict.app.TestUtils
import com.tokenaddict.app.data.KimiProvider
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.model.ApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class KimiUsageWorkerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockWorkerParams: WorkerParameters

    @Mock
    private lateinit var mockKimiProvider: KimiProvider

    @Mock
    private lateinit var mockTokenManager: KimiTokenManager

    @Mock
    private lateinit var mockNotificationScheduler: NotificationScheduler

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockSharedPreferencesEditor: SharedPreferences.Editor

    private lateinit var worker: KimiUsageWorker

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockSharedPreferencesEditor)
        `when`(mockSharedPreferencesEditor.putFloat(anyString(), anyFloat())).thenReturn(mockSharedPreferencesEditor)
        `when`(mockSharedPreferencesEditor.putLong(anyString(), anyLong())).thenReturn(mockSharedPreferencesEditor)
        `when`(mockSharedPreferencesEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockSharedPreferencesEditor)
        worker = KimiUsageWorker(
            mockContext,
            mockWorkerParams,
            mockKimiProvider,
            mockTokenManager,
            mockNotificationScheduler
        )
    }

    private fun stubLoggedIn(loggedIn: Boolean) {
        runBlocking {
            `when`(mockKimiProvider.isLoggedIn()).thenReturn(loggedIn)
        }
    }

    private fun stubUsage(usageInfo: com.tokenaddict.app.data.model.UsageInfo) {
        runBlocking {
            `when`(mockKimiProvider.getUsage()).thenReturn(usageInfo)
        }
    }

    private fun stubUsageThrow(exception: Exception) {
        runBlocking {
            `when`(mockKimiProvider.getUsage()).thenThrow(exception)
        }
    }

    @Test
    fun `doWork returns success with valid data`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure when not logged in`() {
        stubLoggedIn(false)

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        runBlocking { verify(mockKimiProvider, never()).getUsage() }
    }

    @Test
    fun `doWork returns failure and clears tokens on Unauthorized exception`() {
        stubLoggedIn(true)
        stubUsageThrow(ApiException.Unauthorized())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockTokenManager).clearTokens()
        verify(mockNotificationScheduler).cancelResetNotification()
        verify(mockNotificationScheduler).showReloginNotification()
    }

    @Test
    fun `doWork returns failure and clears tokens on Forbidden exception`() {
        stubLoggedIn(true)
        stubUsageThrow(ApiException.Forbidden())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockTokenManager).clearTokens()
        verify(mockNotificationScheduler).cancelResetNotification()
        verify(mockNotificationScheduler).showReloginNotification()
    }

    @Test
    fun `doWork returns retry on RateLimited exception`() {
        stubLoggedIn(true)
        stubUsageThrow(ApiException.RateLimited())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns retry on NetworkError exception`() {
        stubLoggedIn(true)
        stubUsageThrow(ApiException.NetworkError("Connection timeout"))

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns failure on general exception`() {
        stubLoggedIn(true)
        stubUsageThrow(RuntimeException("Unexpected error"))

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork schedules notification when reset time is in future and limit reached`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 100.0,
            resetsAt = TestUtils.createFutureResetTime(2)
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).scheduleResetNotification(anyLong())
    }

    @Test
    fun `doWork does not schedule notification when reset time is in future but limit not reached`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 50.0,
            resetsAt = TestUtils.createFutureResetTime(2)
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler, never()).scheduleResetNotification(anyLong())
    }

    @Test
    fun `doWork schedules notification when weekly reset is in future and weekly limit reached`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            resetsAt = TestUtils.createFutureResetTime(2),
            weeklyUtilization = 100.0,
            weeklyResetsAt = TestUtils.createFutureResetTime(48)
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).scheduleResetNotification(anyLong())
    }

    @Test
    fun `doWork cancels notification when reset time has passed`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            resetsAt = TestUtils.createExpiredResetTime(),
            isReset = true
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).cancelResetNotification()
    }

    @Test
    fun `doWork persists usage data to SharedPreferences`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo())

        runBlocking { worker.executeWork() }

        verify(mockSharedPreferences).edit()
        verify(mockSharedPreferencesEditor).putFloat(eq("utilization"), anyFloat())
        verify(mockSharedPreferencesEditor).putLong(eq("last_checked"), anyLong())
        verify(mockSharedPreferencesEditor).putFloat(eq("weekly_utilization"), anyFloat())
        verify(mockSharedPreferencesEditor).putLong(eq("weekly_resets_at"), anyLong())
        verify(mockSharedPreferencesEditor).putBoolean(eq("weekly_is_reset"), anyBoolean())
        verify(mockSharedPreferencesEditor).apply()
    }

    @Test
    fun `doWork refreshes token before API call`() {
        stubLoggedIn(true)
        stubUsage(TestUtils.createMockUsageInfo())

        runBlocking { worker.executeWork() }

        runBlocking { verify(mockTokenManager).refreshTokenIfNeeded() }
    }
}
