package com.tokenaddict.app.receiver

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.tokenaddict.app.R
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ResetAlarmReceiverTest {

    private lateinit var context: Application
    private lateinit var receiver: ResetAlarmReceiver
    private lateinit var mockedNotificationManagerCompatStatic: MockedStatic<NotificationManagerCompat>
    private lateinit var mockNotificationManager: NotificationManagerCompat

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        receiver = ResetAlarmReceiver()

        // Initialize WorkManager for testing.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        // Enable notifications in default preferences.
        context.getSharedPreferences("androidx.preference.PreferenceManager.KEY", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("notification_enabled_claude", true)
            .putBoolean("notification_enabled_kimi", true)
            .apply()

        mockNotificationManager = mock(NotificationManagerCompat::class.java)
        `when`(mockNotificationManager.areNotificationsEnabled()).thenReturn(true)

        mockedNotificationManagerCompatStatic = mockStatic(NotificationManagerCompat::class.java)
        mockedNotificationManagerCompatStatic.`when`<NotificationManagerCompat> {
            NotificationManagerCompat.from(context)
        }.thenReturn(mockNotificationManager)
    }

    @After
    fun tearDown() {
        mockedNotificationManagerCompatStatic.close()
    }

    @Test
    fun onReceive_claude_postsQuirkyClaudeNotification() {
        val intent = Intent("com.tokenaddict.app.RESET_ALARM_CLAUDE")
        receiver.onReceive(context, intent)

        val notificationCaptor = ArgumentCaptor.forClass(android.app.Notification::class.java)
        verify(mockNotificationManager).notify(eq(1001), notificationCaptor.capture())

        val extras = notificationCaptor.value.extras
        val title = extras.getString(NotificationCompat.EXTRA_TITLE)
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString() ?: ""

        assertTrue("Expected title 'Usage Limit Reset' but got: $title",
            title == context.getString(R.string.notification_reset_title))
        assertTrue("Expected 'Claude' in message but got: $text", text.contains("Claude"))
        assertFalse("Message should not contain literal [agent] but got: $text",
            text.contains("[agent]"))
        assertFalse("Message should not be the old static fallback but got: $text",
            text == context.getString(R.string.notification_reset_message))
    }

    @Test
    fun onReceive_kimi_postsQuirkyKimiNotification() {
        val intent = Intent("com.tokenaddict.app.RESET_ALARM_KIMI")
        receiver.onReceive(context, intent)

        val notificationCaptor = ArgumentCaptor.forClass(android.app.Notification::class.java)
        verify(mockNotificationManager).notify(eq(1002), notificationCaptor.capture())

        val extras = notificationCaptor.value.extras
        val title = extras.getString(NotificationCompat.EXTRA_TITLE)
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString() ?: ""

        assertTrue("Expected title 'Usage Limit Reset' but got: $title",
            title == context.getString(R.string.notification_reset_title))
        assertTrue("Expected 'Kimi' in message but got: $text", text.contains("Kimi"))
        assertFalse("Message should not contain literal [agent] but got: $text",
            text.contains("[agent]"))
        assertFalse("Message should not be the old static fallback but got: $text",
            text == context.getString(R.string.notification_reset_message_kimi))
    }

    @Test
    fun onReceive_executesWithoutCrash() {
        // Smoke test: ensure onReceive runs without throwing for both providers
        val claudeIntent = Intent("com.tokenaddict.app.RESET_ALARM_CLAUDE")
        receiver.onReceive(context, claudeIntent)

        val kimiIntent = Intent("com.tokenaddict.app.RESET_ALARM_KIMI")
        receiver.onReceive(context, kimiIntent)
    }

    @Test
    fun onReceive_doesNotPostNotification_whenWeeklyResetStillInFuture() {
        val now = System.currentTimeMillis()
        context.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("utilization", 0f)
            .putLong("resets_at", now - 1000)
            .putFloat("weekly_utilization", 100f)
            .putLong("weekly_resets_at", now + 3600_000)
            .apply()

        val intent = Intent("com.tokenaddict.app.RESET_ALARM_CLAUDE")
        receiver.onReceive(context, intent)

        verify(mockNotificationManager, never()).notify(anyInt(), any(android.app.Notification::class.java))
    }

    @Test
    fun onReceive_doesNotPostNotification_when5hResetStillInFuture() {
        val now = System.currentTimeMillis()
        context.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("utilization", 100f)
            .putLong("resets_at", now + 3600_000)
            .putFloat("weekly_utilization", 0f)
            .putLong("weekly_resets_at", now - 1000)
            .apply()

        val intent = Intent("com.tokenaddict.app.RESET_ALARM_CLAUDE")
        receiver.onReceive(context, intent)

        verify(mockNotificationManager, never()).notify(anyInt(), any(android.app.Notification::class.java))
    }

    @Test
    fun onReceive_postsNotification_whenOnly5hLimitReachedAndWeeklyResetStillInFuture() {
        val now = System.currentTimeMillis()
        context.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("utilization", 100f)
            .putLong("resets_at", now - 1000)
            .putFloat("weekly_utilization", 50f)
            .putLong("weekly_resets_at", now + 3600_000)
            .apply()

        val intent = Intent("com.tokenaddict.app.RESET_ALARM_CLAUDE")
        receiver.onReceive(context, intent)

        verify(mockNotificationManager).notify(eq(1001), any(android.app.Notification::class.java))
    }

    @Test
    fun shouldNotify_returnsTrue_whenBothResetTimesHavePassed() {
        assertTrue(ResetAlarmReceiver.shouldNotify(100L, 200L, 300L, 100f, 100f))
    }

    @Test
    fun shouldNotify_returnsTrue_whenResetTimesAreUnset() {
        assertTrue(ResetAlarmReceiver.shouldNotify(0L, 0L, 300L, 100f, 100f))
    }

    @Test
    fun shouldNotify_returnsTrue_whenOnly5hLimitReachedAndWeeklyResetStillInFuture() {
        assertTrue(ResetAlarmReceiver.shouldNotify(100L, 400L, 300L, 100f, 50f))
    }

    @Test
    fun shouldNotify_returnsFalse_whenWeeklyResetStillInFuture() {
        assertFalse(ResetAlarmReceiver.shouldNotify(100L, 400L, 300L, 100f, 100f))
    }

    @Test
    fun shouldNotify_returnsFalse_when5hResetStillInFuture() {
        assertFalse(ResetAlarmReceiver.shouldNotify(400L, 100L, 300L, 100f, 100f))
    }

    @Test
    fun shouldNotify_returnsFalse_whenBothResetsStillInFuture() {
        assertFalse(ResetAlarmReceiver.shouldNotify(400L, 500L, 300L, 100f, 100f))
    }
}
