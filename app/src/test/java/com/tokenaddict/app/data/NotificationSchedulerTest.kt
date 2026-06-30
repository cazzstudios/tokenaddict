package com.tokenaddict.app.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.tokenaddict.app.ui.KimiLoginActivity
import com.tokenaddict.app.ui.LoginActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class NotificationSchedulerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockAlarmManager: AlarmManager

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockDefaultSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var mockedPendingIntentStatic: MockedStatic<PendingIntent>
    private lateinit var mockedIntentConstruction: MockedConstruction<Intent>
    private lateinit var mockedPreferenceManagerStatic: MockedStatic<PreferenceManager>
    private lateinit var notificationScheduler: NotificationScheduler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        `when`(mockContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(mockAlarmManager)
        // Grant exact alarm permission by default so scheduleResetNotification tests pass.
        // Individual tests can override this if they need to test the denied path.
        `when`(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)

        `when`(mockDefaultSharedPreferences.getBoolean(anyString(), anyBoolean()))
            .thenReturn(true)
        mockedPreferenceManagerStatic = mockStatic(PreferenceManager::class.java)
        mockedPreferenceManagerStatic.`when`<SharedPreferences> {
            PreferenceManager.getDefaultSharedPreferences(any())
        }.thenReturn(mockDefaultSharedPreferences)

        mockedPendingIntentStatic = mockStatic(PendingIntent::class.java)
        val mockPendingIntent = mock(PendingIntent::class.java)
        mockedPendingIntentStatic.`when`<PendingIntent> {
            PendingIntent.getBroadcast(
                any(),
                anyInt(),
                any(),
                anyInt()
            )
        }.thenReturn(mockPendingIntent)

        mockedIntentConstruction = mockConstruction(Intent::class.java)

        notificationScheduler = NotificationScheduler(mockContext)
    }

    @After
    fun tearDown() {
        mockedPendingIntentStatic.close()
        mockedIntentConstruction.close()
        mockedPreferenceManagerStatic.close()
    }

    @Test
    fun `scheduleResetNotification sets exact alarm`() {
        val resetTimeMillis = System.currentTimeMillis() + 3_600_000L

        notificationScheduler.scheduleResetNotification(resetTimeMillis)

        verify(mockAlarmManager).setExactAndAllowWhileIdle(
            eq(AlarmManager.RTC_WAKEUP),
            eq(resetTimeMillis),
            any()
        )
    }

    @Test
    fun `scheduleResetNotification saves time to SharedPreferences`() {
        val resetTimeMillis = System.currentTimeMillis() + 3_600_000L

        notificationScheduler.scheduleResetNotification(resetTimeMillis)

        verify(mockEditor).putLong("scheduled_reset_time", resetTimeMillis)
        verify(mockEditor).apply()
    }

    @Test
    fun `cancelResetNotification cancels the alarm`() {
        notificationScheduler.cancelResetNotification()

        verify(mockAlarmManager).cancel(any<PendingIntent>())
    }

    @Test
    fun `cancelResetNotification removes time from SharedPreferences`() {
        notificationScheduler.cancelResetNotification()

        verify(mockEditor).remove("scheduled_reset_time")
        verify(mockEditor).apply()
    }

    @Test
    fun `getScheduledResetTime returns stored time when set`() {
        val expectedTime = 1234567890L
        `when`(mockSharedPreferences.getLong("scheduled_reset_time", -1L))
            .thenReturn(expectedTime)

        val result = notificationScheduler.getScheduledResetTime()

        assertEquals(expectedTime, result)
    }

    @Test
    fun `getScheduledResetTime returns null when not set`() {
        `when`(mockSharedPreferences.getLong("scheduled_reset_time", -1L))
            .thenReturn(-1L)

        val result = notificationScheduler.getScheduledResetTime()

        assertNull(result)
    }

    @Test
    fun `scheduleResetNotification does nothing when notifications disabled`() {
        `when`(mockDefaultSharedPreferences.getBoolean(NotificationScheduler.PREF_KEY_NOTIFICATION_ENABLED_CLAUDE, true))
            .thenReturn(false)

        val resetTimeMillis = System.currentTimeMillis() + 3_600_000L
        notificationScheduler.scheduleResetNotification(resetTimeMillis)

        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any())
        verify(mockEditor).remove("scheduled_reset_time")
    }

    @Test
    fun `showReloginNotification uses LoginActivity for Claude provider`() {
        val realContext = RuntimeEnvironment.getApplication()
        val scheduler = NotificationScheduler(realContext)

        scheduler.showReloginNotification()

        val capturedIntents = mockedIntentConstruction.constructed()
        assertTrue(capturedIntents.isNotEmpty())
        val intent = capturedIntents.first()
        verify(intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        verify(intent).putExtra(LoginActivity.EXTRA_PROVIDER_ID, "claude")
        verify(intent).setClass(eq(realContext), eq(LoginActivity::class.java))
    }

    @Test
    fun `showReloginNotification uses KimiLoginActivity for Kimi provider`() {
        val realContext = RuntimeEnvironment.getApplication()
        val kimiScheduler = NotificationScheduler(realContext, "kimi")

        kimiScheduler.showReloginNotification()

        val capturedIntents = mockedIntentConstruction.constructed()
        assertTrue(capturedIntents.isNotEmpty())
        val intent = capturedIntents.first()
        verify(intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        verify(intent).putExtra(LoginActivity.EXTRA_PROVIDER_ID, "kimi")
        verify(intent).setClass(eq(realContext), eq(KimiLoginActivity::class.java))
    }

    @Test
    fun `showReloginNotification does nothing when notifications disabled`() {
        `when`(mockDefaultSharedPreferences.getBoolean(NotificationScheduler.PREF_KEY_NOTIFICATION_ENABLED_CLAUDE, true))
            .thenReturn(false)

        notificationScheduler.showReloginNotification()

        verify(mockContext, never()).getSystemService(Context.NOTIFICATION_SERVICE)
    }
}
