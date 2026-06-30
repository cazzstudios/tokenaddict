package com.tokenaddict.app.ui

import android.Manifest
import android.content.Context
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tokenaddict.app.R
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.SessionManager
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var masterKeyBuilderMock: MockedConstruction<MasterKey.Builder>
    private lateinit var encryptedPrefsMock: MockedStatic<EncryptedSharedPreferences>

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val masterKey = Mockito.mock(MasterKey::class.java)
        masterKeyBuilderMock = Mockito.mockConstruction(MasterKey.Builder::class.java) { mock, _ ->
            Mockito.`when`(mock.setKeyScheme(Mockito.any())).thenReturn(mock)
            Mockito.`when`(mock.build()).thenReturn(masterKey)
        }

        encryptedPrefsMock = Mockito.mockStatic(EncryptedSharedPreferences::class.java)
        encryptedPrefsMock.`when`<android.content.SharedPreferences> {
            EncryptedSharedPreferences.create(
                Mockito.any(Context::class.java),
                Mockito.anyString(),
                Mockito.any(MasterKey::class.java),
                Mockito.any(EncryptedSharedPreferences.PrefKeyEncryptionScheme::class.java),
                Mockito.any(EncryptedSharedPreferences.PrefValueEncryptionScheme::class.java)
            )
        }.thenAnswer { invocation ->
            val context = invocation.getArgument<Context>(0)
            val prefsName = invocation.getArgument<String>(1)
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }

        SessionManager.encryptedPrefsFactory = { ctx, prefsName ->
            val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            SecurePreferences.create(prefs)
        }
    }

    @After
    fun tearDown() {
        encryptedPrefsMock.close()
        masterKeyBuilderMock.close()
        SessionManager.encryptedPrefsFactory = null
    }

    private fun launchActivity(): MainActivity {
        return Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
    }

    private fun setClaudeState(
        activity: MainActivity,
        hasReachedLimit: Boolean,
        limitCountdownText: String = ""
    ) {
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        val field = viewModel.javaClass.getDeclaredField("_claudeState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val liveData = field.get(viewModel) as MutableLiveData<MainViewModel.UiState>
        liveData.postValue(
            MainViewModel.UiState.UsageData(
                utilization = 100.0,
                resetsAt = "N/A",
                timeRemaining = "",
                isReset = false,
                lastChecked = "Jan 01, 00:00",
                hasReachedLimit = hasReachedLimit,
                limitCountdownText = limitCountdownText
            )
        )
        ShadowLooper.idleMainLooper()
        ShadowLooper.shadowMainLooper().idleFor(java.time.Duration.ofMillis(1000))
    }

    @Test
    fun notLimited_showsUsageDetails() {
        val activity = launchActivity()
        setClaudeState(activity, hasReachedLimit = false)

        val usageDetails = activity.findViewById<View>(R.id.claudeUsageDetails)
        assertEquals(View.VISIBLE, usageDetails.visibility)

        val shortContainer = activity.findViewById<View>(R.id.claudeCountdownShortContainer)
        assertEquals(View.GONE, shortContainer.visibility)

        val longContainer = activity.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.GONE, longContainer.visibility)
    }

    @Test
    fun shortCountdown_showsShortContainer() {
        val activity = launchActivity()
        setClaudeState(activity, hasReachedLimit = true, limitCountdownText = "00:05:00")

        val shortContainer = activity.findViewById<View>(R.id.claudeCountdownShortContainer)
        assertEquals(View.VISIBLE, shortContainer.visibility)
    }

    @Test
    fun longCountdown_showsLongContainer() {
        val activity = launchActivity()
        setClaudeState(activity, hasReachedLimit = true, limitCountdownText = "01:02:02:00")

        val longContainer = activity.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.VISIBLE, longContainer.visibility)
    }

    @Test
    fun robotIcon_showsRestingWhenLimited() {
        val activity = launchActivity()
        setClaudeState(activity, hasReachedLimit = true, limitCountdownText = "05:00:00")

        val imageView = activity.findViewById<ImageView>(R.id.claudeRobotIcon)
        val drawable = ResourcesCompat.getDrawable(activity.resources, R.drawable.resting, null)
        assertEquals(drawable?.constantState, imageView.drawable?.constantState)
    }

    @Test
    fun configurationChange_preservesCountdownFormatAndVisibility() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume()
        val activity = controller.get()
        setClaudeState(activity, hasReachedLimit = true, limitCountdownText = "00:05:00")

        val shortContainerBefore = activity.findViewById<View>(R.id.claudeCountdownShortContainer)
        assertEquals(View.VISIBLE, shortContainerBefore.visibility)
        val longContainerBefore = activity.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.GONE, longContainerBefore.visibility)

        controller.recreate()
        val recreated = controller.get()
        ShadowLooper.idleMainLooper()

        val shortContainerAfter = recreated.findViewById<View>(R.id.claudeCountdownShortContainer)
        assertEquals(View.VISIBLE, shortContainerAfter.visibility)
        val longContainerAfter = recreated.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.GONE, longContainerAfter.visibility)
        val usageContainerAfter = recreated.findViewById<View>(R.id.claudeUsageContainer)
        assertEquals(View.VISIBLE, usageContainerAfter.visibility)
    }

    @Test
    fun configurationChange_preservesLongCountdownFormat() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume()
        val activity = controller.get()
        setClaudeState(activity, hasReachedLimit = true, limitCountdownText = "01:02:02:00")

        val longContainerBefore = activity.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.VISIBLE, longContainerBefore.visibility)

        controller.recreate()
        val recreated = controller.get()
        ShadowLooper.idleMainLooper()

        val longContainerAfter = recreated.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.VISIBLE, longContainerAfter.visibility)
        val shortContainerAfter = recreated.findViewById<View>(R.id.claudeCountdownShortContainer)
        assertEquals(View.GONE, shortContainerAfter.visibility)
    }

    @Test
    fun configurationChange_preservesNotLimitedState() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume()
        val activity = controller.get()
        setClaudeState(activity, hasReachedLimit = false)

        val usageDetailsBefore = activity.findViewById<View>(R.id.claudeUsageDetails)
        assertEquals(View.VISIBLE, usageDetailsBefore.visibility)

        controller.recreate()
        val recreated = controller.get()
        ShadowLooper.idleMainLooper()

        val usageDetailsAfter = recreated.findViewById<View>(R.id.claudeUsageDetails)
        assertEquals(View.VISIBLE, usageDetailsAfter.visibility)
        val shortContainerAfter = recreated.findViewById<View>(R.id.claudeCountdownShortContainer)
        assertEquals(View.GONE, shortContainerAfter.visibility)
        val longContainerAfter = recreated.findViewById<View>(R.id.claudeCountdownLongContainer)
        assertEquals(View.GONE, longContainerAfter.visibility)
    }

    @Test
    fun notificationPermissionGranted_noDialogShown() {
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        launchActivity()
        ShadowLooper.idleMainLooper()

        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun notificationPermissionRationaleShown_showsRationaleDialog() {
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Shadows.shadowOf(app.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)

        val activity = launchActivity()
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        assertTrue(dialog!!.isShowing)
        assertEquals(activity.getString(R.string.notification_permission_rationale), dialog.findViewById<android.widget.TextView>(android.R.id.message)?.text)
    }

    @Test
    fun notificationPermissionPermanentlyDenied_showsSettingsDialog() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notification_permission_requested", true)
            .commit()

        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Shadows.shadowOf(app.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)

        val activity = launchActivity()
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        assertTrue(dialog!!.isShowing)
        assertEquals(activity.getString(R.string.notification_permission_message), dialog.findViewById<android.widget.TextView>(android.R.id.message)?.text)
    }

    @Test
    fun notificationPermissionSettingsDialog_openSettingsStartsSettingsIntent() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notification_permission_requested", true)
            .commit()

        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Shadows.shadowOf(app.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)

        val activity = launchActivity()
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)

        dialog!!.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()

        val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, startedIntent!!.action)
    }
}
