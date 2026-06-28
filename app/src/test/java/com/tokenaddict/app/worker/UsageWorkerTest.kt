package com.tokenaddict.app.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tokenaddict.app.TestUtils
import com.tokenaddict.app.data.ClaudeProvider
import com.tokenaddict.app.data.model.ApiException
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.time.OffsetDateTime

class UsageWorkerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockWorkerParams: WorkerParameters

    @Mock
    private lateinit var mockSessionManager: SessionManager

    @Mock
    private lateinit var mockApiClient: ClaudeProvider

    @Mock
    private lateinit var mockNotificationScheduler: NotificationScheduler

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockSharedPreferencesEditor: SharedPreferences.Editor

    private lateinit var worker: ClaudeUsageWorker

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockSharedPreferencesEditor)
        `when`(mockSharedPreferencesEditor.putFloat(anyString(), anyFloat())).thenReturn(mockSharedPreferencesEditor)
        `when`(mockSharedPreferencesEditor.putLong(anyString(), anyLong())).thenReturn(mockSharedPreferencesEditor)
        `when`(mockSharedPreferencesEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockSharedPreferencesEditor)
        worker = ClaudeUsageWorker(
            mockContext,
            mockWorkerParams,
            mockSessionManager,
            mockApiClient,
            mockNotificationScheduler
        )
    }

    private fun stubUsage(usageInfo: com.tokenaddict.app.data.model.UsageInfo) {
        runBlocking {
            `when`(mockApiClient.getUsage()).thenReturn(usageInfo)
        }
    }

    private fun stubUsageThrow(exception: Exception) {
        runBlocking {
            `when`(mockApiClient.getUsage()).thenThrow(exception)
        }
    }

    @Test
    fun `doWork returns success with valid data`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure when not logged in`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(false)

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        runBlocking { verify(mockApiClient, never()).getUsage() }
    }

    @Test
    fun `doWork returns failure and clears session on Unauthorized exception`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(ApiException.Unauthorized())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockSessionManager).clearSession()
        verify(mockNotificationScheduler).cancelResetNotification()
        verify(mockNotificationScheduler).showReloginNotification()
    }

    @Test
    fun `doWork returns failure and clears session on Forbidden exception`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(ApiException.Forbidden())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockSessionManager).clearSession()
        verify(mockNotificationScheduler).cancelResetNotification()
        verify(mockNotificationScheduler).showReloginNotification()
    }

    @Test
    fun `doWork returns retry on RateLimited exception without clearing session`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(ApiException.RateLimited())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(mockSessionManager, never()).clearSession()
        verify(mockNotificationScheduler, never()).showReloginNotification()
    }

    @Test
    fun `doWork returns retry on NetworkError exception without clearing session`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(ApiException.NetworkError("Connection timeout"))

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(mockSessionManager, never()).clearSession()
        verify(mockNotificationScheduler, never()).showReloginNotification()
    }

    @Test
    fun `doWork schedules notification when reset time is in future and limit reached`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 100.0,
            resetsAt = TestUtils.createFutureResetTime(2)
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).scheduleResetNotification(anyLong())
    }

    @Test
    fun `doWork does not schedule notification when reset time is in future but limit not reached`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 50.0,
            resetsAt = TestUtils.createFutureResetTime(2)
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler, never()).scheduleResetNotification(anyLong())
    }

    @Test
    fun `doWork schedules notification when weekly reset is in future and weekly limit reached`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            resetsAt = TestUtils.createFutureResetTime(2),
            weeklyUtilization = 100.0,
            weeklyResetsAt = TestUtils.createFutureResetTime(48)
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).scheduleResetNotification(anyLong())
    }

    @Test
    fun `doWork schedules notification at weekly reset when both limits reached and weekly resets later`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        val fiveHourResetsAt = TestUtils.createFutureResetTime(2)
        val weeklyResetsAt = TestUtils.createFutureResetTime(48)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 100.0,
            resetsAt = fiveHourResetsAt,
            weeklyUtilization = 100.0,
            weeklyResetsAt = weeklyResetsAt
        ))

        runBlocking { worker.executeWork() }

        val captor = ArgumentCaptor.forClass(Long::class.java)
        verify(mockNotificationScheduler).scheduleResetNotification(captor.capture())
        assertEquals(OffsetDateTime.parse(weeklyResetsAt).toInstant().toEpochMilli(), captor.value)
    }

    @Test
    fun `doWork schedules notification at 5h reset when both limits reached and 5h resets later`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        val fiveHourResetsAt = TestUtils.createFutureResetTime(48)
        val weeklyResetsAt = TestUtils.createFutureResetTime(2)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 100.0,
            resetsAt = fiveHourResetsAt,
            weeklyUtilization = 100.0,
            weeklyResetsAt = weeklyResetsAt
        ))

        runBlocking { worker.executeWork() }

        val captor = ArgumentCaptor.forClass(Long::class.java)
        verify(mockNotificationScheduler).scheduleResetNotification(captor.capture())
        assertEquals(OffsetDateTime.parse(fiveHourResetsAt).toInstant().toEpochMilli(), captor.value)
    }

    @Test
    fun `doWork schedules notification at weekly reset when only weekly limit reached`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        val weeklyResetsAt = TestUtils.createFutureResetTime(48)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 50.0,
            resetsAt = TestUtils.createFutureResetTime(2),
            weeklyUtilization = 100.0,
            weeklyResetsAt = weeklyResetsAt
        ))

        runBlocking { worker.executeWork() }

        val captor = ArgumentCaptor.forClass(Long::class.java)
        verify(mockNotificationScheduler).scheduleResetNotification(captor.capture())
        assertEquals(OffsetDateTime.parse(weeklyResetsAt).toInstant().toEpochMilli(), captor.value)
    }

    @Test
    fun `doWork cancels notification when both reset times have passed`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 100.0,
            resetsAt = TestUtils.createExpiredResetTime(),
            weeklyUtilization = 100.0,
            weeklyResetsAt = TestUtils.createExpiredResetTime(),
            isReset = true,
            weeklyIsReset = true
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).cancelResetNotification()
    }

    @Test
    fun `doWork schedules notification at weekly reset when 5h reset passed but weekly limit still blocking`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        val weeklyResetsAt = TestUtils.createFutureResetTime(48)
        stubUsage(TestUtils.createMockUsageInfo(
            utilization = 100.0,
            resetsAt = TestUtils.createExpiredResetTime(),
            weeklyUtilization = 100.0,
            weeklyResetsAt = weeklyResetsAt,
            isReset = true,
            weeklyIsReset = false
        ))

        runBlocking { worker.executeWork() }

        val captor = ArgumentCaptor.forClass(Long::class.java)
        verify(mockNotificationScheduler).scheduleResetNotification(captor.capture())
        assertEquals(OffsetDateTime.parse(weeklyResetsAt).toInstant().toEpochMilli(), captor.value)
    }

    @Test
    fun `doWork cancels notification when reset time has passed`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            resetsAt = TestUtils.createExpiredResetTime(),
            isReset = true
        ))

        runBlocking { worker.executeWork() }

        verify(mockNotificationScheduler).cancelResetNotification()
    }

    @Test
    fun `doWork returns failure when no organization found`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(ApiException.NetworkError("No organization found"))

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns failure on general exception`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(RuntimeException("Network error"))

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork persists usage data to SharedPreferences`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
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
    fun `doWork persists is_reset as true when reset time has passed`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            resetsAt = TestUtils.createExpiredResetTime(),
            isReset = true
        ))

        runBlocking { worker.executeWork() }

        verify(mockSharedPreferencesEditor).putBoolean(eq("is_reset"), eq(true))
    }

    @Test
    fun `doWork persists is_reset as false when reset time is in future`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            resetsAt = TestUtils.createFutureResetTime(2)
        ))

        runBlocking { worker.executeWork() }

        verify(mockSharedPreferencesEditor).putBoolean(eq("is_reset"), eq(false))
    }

    @Test
    fun `doWork persists weekly is_reset as false`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo(
            weeklyResetsAt = TestUtils.createFutureResetTime(48),
            weeklyIsReset = false
        ))

        runBlocking { worker.executeWork() }

        verify(mockSharedPreferencesEditor).putBoolean(eq("weekly_is_reset"), eq(false))
    }

    @Test
    fun `doWork persists service changed flag and cancels reset notification on ServiceChanged`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsageThrow(ApiException.ServiceChanged("Claude returned HTML"))

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockNotificationScheduler).cancelResetNotification()
        verify(mockSharedPreferencesEditor).putBoolean(eq("claude_service_changed"), eq(true))
        verify(mockSessionManager, never()).clearSession()
        verify(mockNotificationScheduler, never()).showReloginNotification()
    }

    @Test
    fun `doWork clears service changed flag on success`() {
        `when`(mockSessionManager.isLoggedIn()).thenReturn(true)
        stubUsage(TestUtils.createMockUsageInfo())

        val result = runBlocking { worker.executeWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockSharedPreferencesEditor).putBoolean(eq("claude_service_changed"), eq(false))
    }
}
