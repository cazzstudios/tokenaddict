package com.tokenaddict.app.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationMessageProviderTest {

    private lateinit var appContext: Application

    @Before
    fun setUp() {
        appContext = RuntimeEnvironment.getApplication()
        appContext.getSharedPreferences("notification_message_provider", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun getResetMessage_claude_containsDisplayName() {
        val provider = NotificationMessageProvider(appContext, Random(42))
        val message = provider.getResetMessage("claude")

        assertTrue("Expected 'Claude' in message but got: $message", message.contains("Claude"))
        assertFalse("Expected no literal [agent] but got: $message", message.contains("[agent]"))
    }

    @Test
    fun getResetMessage_kimi_containsDisplayName() {
        val provider = NotificationMessageProvider(appContext, Random(42))
        val message = provider.getResetMessage("kimi")

        assertTrue("Expected 'Kimi' in message but got: $message", message.contains("Kimi"))
        assertFalse("Expected no literal [agent] but got: $message", message.contains("[agent]"))
    }

    @Test
    fun consecutiveCalls_returnDifferentMessages() {
        val provider = NotificationMessageProvider(appContext, Random(42))
        val msg1 = provider.getResetMessage("claude")
        val msg2 = provider.getResetMessage("claude")

        assertNotEquals("Consecutive calls should return different messages", msg1, msg2)
    }

    @Test
    fun persistence_acrossProviderInstances_returnsDifferentMessages() {
        val provider1 = NotificationMessageProvider(appContext, Random(42))
        val msg1 = provider1.getResetMessage("claude")

        val provider2 = NotificationMessageProvider(appContext, Random(42))
        val msg2 = provider2.getResetMessage("claude")

        assertNotEquals("New provider instance should avoid previously shown message", msg1, msg2)
    }

    @Test
    fun singleItemPool_alwaysReturnsSameMessage() {
        val mockContext = mock(Context::class.java)
        val mockResources = mock(Resources::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.getStringArray(anyInt())).thenReturn(arrayOf("Only one [agent] message"))
        `when`(mockContext.getString(anyInt())).thenReturn("Fallback for [agent]")
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.getInt(anyString(), anyInt())).thenReturn(-1)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)

        val provider = NotificationMessageProvider(mockContext, Random(42))
        val msg1 = provider.getResetMessage("claude")
        val msg2 = provider.getResetMessage("claude")

        assertEquals("Single-item pool should always return same message", msg1, msg2)
    }

    @Test
    fun emptyPool_returnsFallbackContainingProviderName() {
        val mockContext = mock(Context::class.java)
        val mockResources = mock(Resources::class.java)

        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.getStringArray(anyInt())).thenReturn(emptyArray())
        `when`(mockContext.getString(anyInt()))
            .thenReturn("Your Claude usage limit has been reset. Tap to check.")

        val provider = NotificationMessageProvider(mockContext, Random(42))
        val message = provider.getResetMessage("claude")

        assertTrue("Fallback should contain provider name 'Claude' but got: $message",
            message.contains("Claude"))
    }

    @Test
    fun unknownProvider_returnsFallback() {
        val mockContext = mock(Context::class.java)
        val mockResources = mock(Resources::class.java)

        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.getStringArray(anyInt())).thenReturn(arrayOf("msg one", "msg two"))
        `when`(mockContext.getString(anyInt())).thenReturn("Fallback message")

        val provider = NotificationMessageProvider(mockContext, Random(42))
        val message = provider.getResetMessage("totally_unknown_provider")

        assertEquals("Fallback message", message)
    }
}
