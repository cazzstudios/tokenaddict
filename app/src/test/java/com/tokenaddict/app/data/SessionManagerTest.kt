package com.tokenaddict.app.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import com.tokenaddict.app.data.model.SessionState
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class SessionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockCookieManager: CookieManager

    private lateinit var mockedCookieManagerStatic: MockedStatic<CookieManager>

    private val gson = Gson()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        mockedCookieManagerStatic = mockStatic(CookieManager::class.java)
        mockedCookieManagerStatic.`when`<CookieManager> { CookieManager.getInstance() }
            .thenReturn(mockCookieManager)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)

        SessionManager.encryptedPrefsFactory = { _, _ -> SecurePreferences.create(mockSharedPreferences) }
    }

    @After
    fun tearDown() {
        SessionManager.encryptedPrefsFactory = null
        mockedCookieManagerStatic.close()
    }

    private fun createSessionManager(providerId: String = "claude"): SessionManager {
        return SessionManager(mockContext, providerId)
    }

    @Test
    fun `saveSession saves cookies to SecurePreferences as JSON array`() {
        val sessionManager = createSessionManager()
        val url = "https://claude.ai"
        val cookies = "sessionKey=sk-ant-sid01abc123; other=value"
        `when`(mockCookieManager.getCookie(url)).thenReturn(cookies)

        sessionManager.saveSession(url)

        val expectedJson = gson.toJson(listOf("sessionKey=sk-ant-sid01abc123", "other=value"))
        verify(mockEditor).putString("cookies_v2_claude", expectedJson)
        verify(mockEditor, atLeastOnce()).apply()
    }

    @Test
    fun `restoreSession restores cookies from JSON array to CookieManager`() {
        val sessionManager = createSessionManager()
        val url = "https://claude.ai"
        val cookiesList = listOf("sessionKey=sk-ant-sid01abc123", "other=value")
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(cookiesList))

        sessionManager.restoreSession(url)

        verify(mockCookieManager).setCookie(url, "sessionKey=sk-ant-sid01abc123")
        verify(mockCookieManager).setCookie(url, "other=value")
        verify(mockCookieManager).flush()
    }

    @Test
    fun `restoreSession does nothing when no stored cookies`() {
        val sessionManager = createSessionManager()
        val url = "https://claude.ai"
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null)).thenReturn(null)

        sessionManager.restoreSession(url)

        verify(mockCookieManager, never()).setCookie(anyString(), anyString())
    }

    @Test
    fun `clearSession removes all cookies and SecurePreferences keys`() {
        val sessionManager = createSessionManager()

        sessionManager.clearSession()

        verify(mockCookieManager).removeAllCookies(null)
        verify(mockCookieManager).flush()
        verify(mockEditor, atLeast(2)).remove("cookies_v2_claude")
        verify(mockEditor, atLeastOnce()).apply()
    }

    @Test
    fun `isLoggedIn returns true when sessionKey cookie exists`() {
        val sessionManager = createSessionManager()
        val cookiesList = listOf("sessionKey=sk-ant-sid01abc123", "other=value")
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(cookiesList))

        assertTrue(sessionManager.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns false when no cookies`() {
        val sessionManager = createSessionManager()
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null)).thenReturn(null)

        assertFalse(sessionManager.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns false when no session markers`() {
        val sessionManager = createSessionManager()
        val cookiesList = listOf("other-cookie=other-value", "another=thing")
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(cookiesList))

        assertFalse(sessionManager.isLoggedIn())
    }

    @Test
    fun `getSessionState returns LoggedIn when sessionKey exists`() {
        val sessionManager = createSessionManager()
        val url = "https://claude.ai"
        val cookies = "sessionKey=sk-ant-sid01abc123; other=value"
        val cookiesList = listOf("sessionKey=sk-ant-sid01abc123", "other=value")
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(cookiesList))
        `when`(mockCookieManager.getCookie(url)).thenReturn(cookies)

        val state = sessionManager.getSessionState()
        assertTrue(state is SessionState.LoggedIn)
    }

    @Test
    fun `getSessionState returns LoggedOut when no cookies`() {
        val sessionManager = createSessionManager()
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null)).thenReturn(null)
        `when`(mockCookieManager.getCookie("https://claude.ai")).thenReturn(null)

        val state = sessionManager.getSessionState()
        assertTrue(state is SessionState.LoggedOut)
    }

    @Test
    fun `getSessionState returns LoggedIn after process death recovery`() {
        val sessionManager = createSessionManager()
        val url = "https://claude.ai"
        val cookiesList = listOf("sessionKey=sk-ant-sid01abc123", "other=value")
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(cookiesList))

        var cookiesRestored = false
        `when`(mockCookieManager.getCookie(url)).thenAnswer {
            if (cookiesRestored) "sessionKey=sk-ant-sid01abc123; other=value" else null
        }
        doAnswer {
            cookiesRestored = true
            null
        }.`when`(mockCookieManager).setCookie(eq(url), anyString())

        val state = sessionManager.getSessionState()

        assertTrue(state is SessionState.LoggedIn)
        verify(mockCookieManager).setCookie(eq(url), eq("sessionKey=sk-ant-sid01abc123"))
        verify(mockCookieManager).setCookie(eq(url), eq("other=value"))
    }

    @Test
    fun `restoreSession preserves cookie attributes`() {
        val sessionManager = createSessionManager()
        val url = "https://claude.ai"
        val cookieWithAttributes = "sessionKey=sk-ant-sid01abc123; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/; HttpOnly; Secure"
        val cookiesList = listOf(cookieWithAttributes)
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(cookiesList))

        sessionManager.restoreSession(url)

        verify(mockCookieManager).setCookie(url, cookieWithAttributes)
        verify(mockCookieManager, times(1)).setCookie(eq(url), anyString())
    }

    @Test
    fun `migrates cookies from plaintext to encrypted prefs on first launch`() {
        val mockEncryptedPrefs = mock(SharedPreferences::class.java)
        val mockEncryptedEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockEncryptedPrefs.edit()).thenReturn(mockEncryptedEditor)
        `when`(mockEncryptedEditor.putString(anyString(), anyString())).thenReturn(mockEncryptedEditor)
        `when`(mockEncryptedEditor.remove(anyString())).thenReturn(mockEncryptedEditor)

        val securePrefs = SecurePreferences.create(mockEncryptedPrefs)

        val cookiesJson = gson.toJson(listOf("sessionKey=sk-ant-sid01abc123"))
        `when`(mockSharedPreferences.getString("cookies_v2_claude", null)).thenReturn(cookiesJson)

        SessionManager.encryptedPrefsFactory = { _, _ -> securePrefs }

        createSessionManager()

        verify(mockEncryptedEditor).putString("cookies_v2_claude", cookiesJson)
        verify(mockEditor, atLeastOnce()).remove("cookies_v2_claude")
    }

    @Test(expected = SecureStorageException::class)
    fun `throws SecureStorageException when encryption init fails`() {
        SessionManager.encryptedPrefsFactory = { _, _ -> throw SecureStorageException("init failed") }

        createSessionManager()
    }

    @Test
    fun `does not delete plaintext data when encryption init fails`() {
        val freshMockPrefs = mock(SharedPreferences::class.java)
        val freshMockEditor = mock(SharedPreferences.Editor::class.java)
        `when`(freshMockPrefs.edit()).thenReturn(freshMockEditor)
        `when`(freshMockEditor.putString(anyString(), anyString())).thenReturn(freshMockEditor)
        `when`(freshMockEditor.remove(anyString())).thenReturn(freshMockEditor)

        val freshContext = mock(Context::class.java)
        `when`(freshContext.getSharedPreferences(anyString(), anyInt())).thenReturn(freshMockPrefs)

        `when`(freshMockPrefs.getString("cookies_v2_claude", null))
            .thenReturn(gson.toJson(listOf("sessionKey=sk-ant-sid01abc123")))

        try {
            SessionManager.encryptedPrefsFactory = { _, _ -> throw SecureStorageException("init failed") }
            SessionManager(freshContext, "claude")
            fail("Expected SecureStorageException")
        } catch (_: SecureStorageException) {
        }

        verify(freshMockEditor, never()).remove("cookies_v2_claude")
    }

    @Test
    fun `saveSession writes to SecurePreferences`() {
        val mockEncryptedPrefs = mock(SharedPreferences::class.java)
        val mockEncryptedEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockEncryptedPrefs.edit()).thenReturn(mockEncryptedEditor)
        `when`(mockEncryptedEditor.putString(anyString(), anyString())).thenReturn(mockEncryptedEditor)

        val securePrefs = SecurePreferences.create(mockEncryptedPrefs)

        val url = "https://claude.ai"
        val cookies = "sessionKey=sk-ant-sid01abc123; other=value"
        `when`(mockCookieManager.getCookie(url)).thenReturn(cookies)

        SessionManager.encryptedPrefsFactory = { _, _ -> securePrefs }

        val sm = createSessionManager()
        sm.saveSession(url)

        val expectedJson = gson.toJson(listOf("sessionKey=sk-ant-sid01abc123", "other=value"))
        verify(mockEncryptedEditor).putString("cookies_v2_claude", expectedJson)
    }
}
