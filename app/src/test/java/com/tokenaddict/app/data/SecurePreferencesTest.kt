package com.tokenaddict.app.data

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class SecurePreferencesTest {

    @Mock
    private lateinit var mockPrefs: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var securePrefs: SecurePreferences

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(mockEditor)
        `when`(mockEditor.remove(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockEditor)
        `when`(mockEditor.clear()).thenReturn(mockEditor)
        securePrefs = SecurePreferences.create(mockPrefs)
    }

    @Test
    fun `putString and getString round-trip`() {
        `when`(mockPrefs.getString("token", null)).thenReturn("secret-value")

        securePrefs.putString("token", "secret-value")

        verify(mockEditor).putString("token", "secret-value")
        verify(mockEditor).commit()
        assertEquals("secret-value", securePrefs.getString("token"))
    }

    @Test
    fun `getString returns default when key absent`() {
        `when`(mockPrefs.getString("missing", null)).thenReturn(null)

        assertNull(securePrefs.getString("missing"))
        assertNull(securePrefs.getString("missing", "fallback"))
    }

    @Test
    fun `remove deletes key`() {
        securePrefs.remove("token")

        verify(mockEditor).remove("token")
        verify(mockEditor).apply()
    }

    @Test
    fun `clear clears all entries`() {
        securePrefs.clear()

        verify(mockEditor).clear()
        verify(mockEditor).apply()
    }

    @Test(expected = SecureStorageException::class)
    fun `getString throws SecureStorageException when prefs throws`() {
        `when`(mockPrefs.getString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenThrow(RuntimeException("disk error"))

        securePrefs.getString("token")
    }

    @Test(expected = SecureStorageException::class)
    fun `putString throws SecureStorageException when edit throws`() {
        `when`(mockPrefs.edit()).thenThrow(RuntimeException("edit failed"))

        securePrefs.putString("token", "value")
    }

    @Test(expected = SecureStorageException::class)
    fun `remove throws SecureStorageException when edit throws`() {
        `when`(mockPrefs.edit()).thenThrow(RuntimeException("edit failed"))

        securePrefs.remove("token")
    }

    @Test(expected = SecureStorageException::class)
    fun `clear throws SecureStorageException when edit throws`() {
        `when`(mockPrefs.edit()).thenThrow(RuntimeException("edit failed"))

        securePrefs.clear()
    }

    @Test(expected = SecureStorageException::class)
    fun `factory test seam throws SecureStorageException when factory throws`() {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        SecurePreferences.create(mockContext, "test_prefs") { _, _ -> throw RuntimeException("init failed") }
    }
}
