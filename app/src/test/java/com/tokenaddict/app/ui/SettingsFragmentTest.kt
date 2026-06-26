package com.tokenaddict.app.ui

import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.tokenaddict.app.TestTokenAddictApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTokenAddictApplication::class)
class SettingsFragmentTest {

    private lateinit var context: android.app.Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()
        )
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putInt("polling_interval", 30)
            .apply()
    }

    @Test
    fun `reschedulePeriodicWork updates WorkManager interval for both workers`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        val fragment = SettingsFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()

        val newInterval = 60L
        fragment.reschedulePeriodicWork(newInterval)

        val workManager = WorkManager.getInstance(context)
        val claudeWorkInfo = workManager.getWorkInfosForUniqueWork("usage_check").get()
        val kimiWorkInfo = workManager.getWorkInfosForUniqueWork("kimi_usage_check").get()

        assertEquals(1, claudeWorkInfo.size)
        assertEquals(1, kimiWorkInfo.size)
        assertEquals(TimeUnit.MINUTES.toMillis(newInterval), claudeWorkInfo[0].periodicityInfo?.repeatIntervalMillis)
        assertEquals(TimeUnit.MINUTES.toMillis(newInterval), kimiWorkInfo[0].periodicityInfo?.repeatIntervalMillis)
    }
}
