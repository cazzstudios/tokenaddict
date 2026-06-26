package com.tokenaddict.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tokenaddict.app.R
import com.tokenaddict.app.TokenAddictApplication
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.worker.ClaudeUsageWorker
import com.tokenaddict.app.worker.KimiUsageWorker
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        setupPollingIntervalPreference()
        setupNotificationTogglePreference("notification_enabled_claude", "claude")
        setupNotificationTogglePreference("notification_enabled_kimi", "kimi")
        setupBatteryOptimizationPreference()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
    }

    private fun setupBatteryOptimizationPreference() {
        findPreference<Preference>("battery_optimization")?.apply {
            setOnPreferenceClickListener {
                openBatteryOptimizationSettings()
                true
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        val context = requireContext()
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startActivity(intent)
        }
    }

    private fun setupPollingIntervalPreference() {
        findPreference<SeekBarPreference>("polling_interval")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val intervalMinutes = (newValue as Int).toLong()
                reschedulePeriodicWork(intervalMinutes)
                true
            }
        }
    }

    private fun setupNotificationTogglePreference(preferenceKey: String, providerId: String) {
        findPreference<SwitchPreferenceCompat>(preferenceKey)?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val scheduler = NotificationScheduler(requireContext(), providerId)
                if (enabled) {
                    val resetTime = scheduler.getScheduledResetTime()
                    if (resetTime != null && resetTime > System.currentTimeMillis()) {
                        scheduler.scheduleResetNotification(resetTime)
                    }
                } else {
                    scheduler.cancelResetNotification()
                }
                true
            }
        }
    }

    internal fun reschedulePeriodicWork(intervalMinutes: Long) {
        val workManager = WorkManager.getInstance(requireContext())

        val claudeWorkRequest = PeriodicWorkRequestBuilder<ClaudeUsageWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
        ).build()
        workManager.enqueueUniquePeriodicWork(
            "usage_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            claudeWorkRequest
        )

        val kimiWorkRequest = PeriodicWorkRequestBuilder<KimiUsageWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
        ).build()
        workManager.enqueueUniquePeriodicWork(
            "kimi_usage_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            kimiWorkRequest
        )

        arrayOf("claude", "kimi").forEach { providerId ->
            val scheduler = NotificationScheduler(requireContext(), providerId)
            if (scheduler.hasScheduledNotification()) {
                val resetTime = scheduler.getScheduledResetTime()
                if (resetTime != null && resetTime > System.currentTimeMillis()) {
                    scheduler.scheduleResetNotification(resetTime)
                }
            }
        }
    }
}
