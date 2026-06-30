package com.tokenaddict.app.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.tokenaddict.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private lateinit var claudeLoadingContainer: LinearLayout
    private lateinit var claudeLoggedOutContainer: LinearLayout
    private lateinit var claudeUsageContainer: LinearLayout
    private lateinit var claudeFiveHourProgress: ProgressBar
    private lateinit var claudeFiveHourResetsIn: TextView
    private lateinit var claudeWeeklyProgress: ProgressBar
    private lateinit var claudeWeeklyResetsIn: TextView
    private lateinit var claudeLastCheckedText: TextView

    private lateinit var claudeServiceChangedBanner: TextView

    private lateinit var kimiLoadingContainer: LinearLayout
    private lateinit var kimiLoggedOutContainer: LinearLayout
    private lateinit var kimiUsageContainer: LinearLayout
    private lateinit var kimiFiveHourProgress: ProgressBar
    private lateinit var kimiFiveHourResetsIn: TextView
    private lateinit var kimiWeeklyProgress: ProgressBar
    private lateinit var kimiWeeklyResetsIn: TextView
    private lateinit var kimiLastCheckedText: TextView

    private lateinit var claudeRobotIcon: ImageView
    private lateinit var claudeUsageDetails: LinearLayout
    private lateinit var kimiRobotIcon: ImageView
    private lateinit var kimiUsageDetails: LinearLayout

    // Claude countdown containers
    private lateinit var claudeCountdownShortContainer: LinearLayout
    private lateinit var claudeCountdownLongContainer: LinearLayout

    // Claude short digits (HH:MM:SS)
    private lateinit var claudeShortHours: CountdownDigitView
    private lateinit var claudeShortMinutes: CountdownDigitView
    private lateinit var claudeShortSeconds: CountdownDigitView

    // Claude long digits (DD:HH:MM)
    private lateinit var claudeLongDays: CountdownDigitView
    private lateinit var claudeLongHours: CountdownDigitView
    private lateinit var claudeLongMinutes: CountdownDigitView

    // Kimi countdown containers
    private lateinit var kimiCountdownShortContainer: LinearLayout
    private lateinit var kimiCountdownLongContainer: LinearLayout

    // Kimi short digits (HH:MM:SS)
    private lateinit var kimiShortHours: CountdownDigitView
    private lateinit var kimiShortMinutes: CountdownDigitView
    private lateinit var kimiShortSeconds: CountdownDigitView

    // Kimi long digits (DD:HH:MM)
    private lateinit var kimiLongDays: CountdownDigitView
    private lateinit var kimiLongHours: CountdownDigitView
    private lateinit var kimiLongMinutes: CountdownDigitView

    private val claudeLoginLauncher = registerForActivityResult(LoginResultContract()) { success ->
        if (success) {
            viewModel.checkSession("claude")
        }
    }

    private val kimiLoginLauncher = registerForActivityResult(KimiLoginResultContract()) { success ->
        if (success) {
            viewModel.checkSession("kimi")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        initViews()
        setupListeners()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onForegroundChanged(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onForegroundChanged(false)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> { }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationPermissionRationaleDialog()
                }

                hasRequestedNotificationPermissionBefore() -> {
                    showNotificationPermissionSettingsDialog()
                }

                else -> {
                    markNotificationPermissionRequested()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun showNotificationPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_rationale)
            .setPositiveButton(R.string.notification_permission_open_settings) { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.notification_permission_cancel, null)
            .show()
    }

    private fun showNotificationPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.notification_permission_open_settings) { _, _ ->
                openAppNotificationSettings()
            }
            .setNegativeButton(R.string.notification_permission_cancel, null)
            .show()
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun getNotificationPermissionPrefs(): SharedPreferences {
        return getSharedPreferences(NOTIFICATION_PERMISSION_PREFS, MODE_PRIVATE)
    }

    private fun hasRequestedNotificationPermissionBefore(): Boolean {
        return getNotificationPermissionPrefs().getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
    }

    private fun markNotificationPermissionRequested() {
        getNotificationPermissionPrefs().edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
            .apply()
    }

    private fun initViews() {
        claudeLoadingContainer = findViewById(R.id.claudeLoadingContainer)
        claudeLoggedOutContainer = findViewById(R.id.claudeLoggedOutContainer)
        claudeUsageContainer = findViewById(R.id.claudeUsageContainer)
        claudeFiveHourProgress = findViewById(R.id.claudeFiveHourProgress)
        claudeFiveHourResetsIn = findViewById(R.id.claudeFiveHourResetsIn)
        claudeWeeklyProgress = findViewById(R.id.claudeWeeklyProgress)
        claudeWeeklyResetsIn = findViewById(R.id.claudeWeeklyResetsIn)
        claudeLastCheckedText = findViewById(R.id.claudeLastCheckedText)
        claudeServiceChangedBanner = findViewById(R.id.claude_service_changed_banner)

        kimiLoadingContainer = findViewById(R.id.kimiLoadingContainer)
        kimiLoggedOutContainer = findViewById(R.id.kimiLoggedOutContainer)
        kimiUsageContainer = findViewById(R.id.kimiUsageContainer)
        kimiFiveHourProgress = findViewById(R.id.kimiFiveHourProgress)
        kimiFiveHourResetsIn = findViewById(R.id.kimiFiveHourResetsIn)
        kimiWeeklyProgress = findViewById(R.id.kimiWeeklyProgress)
        kimiWeeklyResetsIn = findViewById(R.id.kimiWeeklyResetsIn)
        kimiLastCheckedText = findViewById(R.id.kimiLastCheckedText)

        claudeRobotIcon = findViewById(R.id.claudeRobotIcon)
        claudeUsageDetails = findViewById(R.id.claudeUsageDetails)

        claudeCountdownShortContainer = findViewById(R.id.claudeCountdownShortContainer)
        claudeCountdownLongContainer = findViewById(R.id.claudeCountdownLongContainer)
        claudeShortHours = findViewById(R.id.claudeShortHours)
        claudeShortMinutes = findViewById(R.id.claudeShortMinutes)
        claudeShortSeconds = findViewById(R.id.claudeShortSeconds)
        claudeLongDays = findViewById(R.id.claudeLongDays)
        claudeLongHours = findViewById(R.id.claudeLongHours)
        claudeLongMinutes = findViewById(R.id.claudeLongMinutes)

        kimiRobotIcon = findViewById(R.id.kimiRobotIcon)
        kimiUsageDetails = findViewById(R.id.kimiUsageDetails)

        kimiCountdownShortContainer = findViewById(R.id.kimiCountdownShortContainer)
        kimiCountdownLongContainer = findViewById(R.id.kimiCountdownLongContainer)
        kimiShortHours = findViewById(R.id.kimiShortHours)
        kimiShortMinutes = findViewById(R.id.kimiShortMinutes)
        kimiShortSeconds = findViewById(R.id.kimiShortSeconds)
        kimiLongDays = findViewById(R.id.kimiLongDays)
        kimiLongHours = findViewById(R.id.kimiLongHours)
        kimiLongMinutes = findViewById(R.id.kimiLongMinutes)
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.claudeLoginButton).setOnClickListener {
            claudeLoginLauncher.launch(Unit)
        }
        findViewById<MaterialButton>(R.id.claudeRefreshButton).setOnClickListener {
            viewModel.refresh("claude")
        }
        findViewById<MaterialButton>(R.id.claudeLogoutButton).setOnClickListener {
            viewModel.logout("claude")
        }

        findViewById<MaterialButton>(R.id.kimiLoginButton).setOnClickListener {
            kimiLoginLauncher.launch(Unit)
        }
        findViewById<MaterialButton>(R.id.kimiRefreshButton).setOnClickListener {
            viewModel.refresh("kimi")
        }
        findViewById<MaterialButton>(R.id.kimiLogoutButton).setOnClickListener {
            viewModel.logout("kimi")
        }
    }

    private fun observeUiState() {
        viewModel.claudeState.observe(this) { state ->
            when (state) {
                is MainViewModel.UiState.Loading -> showClaudeLoading()
                is MainViewModel.UiState.LoggedOut -> showClaudeLoggedOut()
                is MainViewModel.UiState.UsageData -> showClaudeUsageData(state)
            }
        }

        viewModel.kimiState.observe(this) { state ->
            when (state) {
                is MainViewModel.UiState.Loading -> showKimiLoading()
                is MainViewModel.UiState.LoggedOut -> showKimiLoggedOut()
                is MainViewModel.UiState.UsageData -> showKimiUsageData(state)
            }
        }
    }

    private fun showClaudeLoading() {
        claudeLoadingContainer.visibility = View.VISIBLE
        claudeLoggedOutContainer.visibility = View.GONE
        claudeUsageContainer.visibility = View.GONE
        claudeServiceChangedBanner.visibility = View.GONE
    }

    private fun showClaudeLoggedOut() {
        claudeLoadingContainer.visibility = View.GONE
        claudeLoggedOutContainer.visibility = View.VISIBLE
        claudeUsageContainer.visibility = View.GONE
        claudeServiceChangedBanner.visibility = View.GONE
    }

    private fun showClaudeUsageData(data: MainViewModel.UiState.UsageData) {
        claudeLoadingContainer.visibility = View.GONE
        claudeLoggedOutContainer.visibility = View.GONE
        claudeUsageContainer.visibility = View.VISIBLE
        claudeServiceChangedBanner.visibility = if (data.serviceChanged) View.VISIBLE else View.GONE

        val fiveHourPercent = data.utilization.toInt()
        claudeFiveHourProgress.progress = fiveHourPercent

        claudeFiveHourResetsIn.text = if (data.timeRemaining.isNotEmpty() && data.timeRemaining != getString(R.string.n_a)) {
            buildResetLabel("$fiveHourPercent%", data.timeRemaining)
        } else {
            "$fiveHourPercent%"
        }

        val weeklyPercent = data.weeklyUtilization.toInt()
        claudeWeeklyProgress.progress = weeklyPercent

        claudeWeeklyResetsIn.text = if (data.weeklyResetsIn.isNotEmpty() && data.weeklyResetsIn != getString(R.string.n_a)) {
            buildResetLabel("$weeklyPercent%", data.weeklyResetsIn)
        } else {
            "$weeklyPercent%"
        }

        updateCountdownDisplay(
            robotIcon = claudeRobotIcon,
            usageDetails = claudeUsageDetails,
            countdownShortContainer = claudeCountdownShortContainer,
            countdownLongContainer = claudeCountdownLongContainer,
            shortHours = claudeShortHours,
            shortMinutes = claudeShortMinutes,
            shortSeconds = claudeShortSeconds,
            longDays = claudeLongDays,
            longHours = claudeLongHours,
            longMinutes = claudeLongMinutes,
            hasReachedLimit = data.hasReachedLimit,
            countdownText = data.limitCountdownText
        )

        claudeLastCheckedText.text = getString(R.string.last_checked, data.lastChecked)
    }

    private fun showKimiLoading() {
        kimiLoadingContainer.visibility = View.VISIBLE
        kimiLoggedOutContainer.visibility = View.GONE
        kimiUsageContainer.visibility = View.GONE
    }

    private fun showKimiLoggedOut() {
        kimiLoadingContainer.visibility = View.GONE
        kimiLoggedOutContainer.visibility = View.VISIBLE
        kimiUsageContainer.visibility = View.GONE
    }

    private fun showKimiUsageData(data: MainViewModel.UiState.UsageData) {
        kimiLoadingContainer.visibility = View.GONE
        kimiLoggedOutContainer.visibility = View.GONE
        kimiUsageContainer.visibility = View.VISIBLE

        val fiveHourPercent = data.utilization.toInt()
        kimiFiveHourProgress.progress = fiveHourPercent

        kimiFiveHourResetsIn.text = if (data.timeRemaining.isNotEmpty() && data.timeRemaining != getString(R.string.n_a)) {
            buildResetLabel("$fiveHourPercent%", data.timeRemaining)
        } else {
            "$fiveHourPercent%"
        }

        val weeklyPercent = data.weeklyUtilization.toInt()
        kimiWeeklyProgress.progress = weeklyPercent

        kimiWeeklyResetsIn.text = if (data.weeklyResetsIn.isNotEmpty() && data.weeklyResetsIn != getString(R.string.n_a)) {
            buildResetLabel("$weeklyPercent%", data.weeklyResetsIn)
        } else {
            "$weeklyPercent%"
        }

        updateCountdownDisplay(
            robotIcon = kimiRobotIcon,
            usageDetails = kimiUsageDetails,
            countdownShortContainer = kimiCountdownShortContainer,
            countdownLongContainer = kimiCountdownLongContainer,
            shortHours = kimiShortHours,
            shortMinutes = kimiShortMinutes,
            shortSeconds = kimiShortSeconds,
            longDays = kimiLongDays,
            longHours = kimiLongHours,
            longMinutes = kimiLongMinutes,
            hasReachedLimit = data.hasReachedLimit,
            countdownText = data.limitCountdownText
        )

        kimiLastCheckedText.text = getString(R.string.last_checked, data.lastChecked)
    }

    private fun updateCountdownDisplay(
        robotIcon: ImageView,
        usageDetails: LinearLayout,
        countdownShortContainer: LinearLayout,
        countdownLongContainer: LinearLayout,
        shortHours: CountdownDigitView,
        shortMinutes: CountdownDigitView,
        shortSeconds: CountdownDigitView,
        longDays: CountdownDigitView,
        longHours: CountdownDigitView,
        longMinutes: CountdownDigitView,
        hasReachedLimit: Boolean,
        countdownText: String
    ) {
        if (!hasReachedLimit) {
            robotIcon.setImageResource(R.drawable.working)
            countdownShortContainer.visibility = View.GONE
            countdownLongContainer.visibility = View.GONE
            usageDetails.visibility = View.VISIBLE
            usageDetails.alpha = 1f
            return
        }

        robotIcon.setImageResource(R.drawable.resting)

        val parts = countdownText.split(":")
        val useLongFormat = parts.size == 4

        if (useLongFormat) {
            longDays.setDigitValue(parts[0].toIntOrNull() ?: 0, animate = false)
            longHours.setDigitValue(parts[1].toIntOrNull() ?: 0, animate = false)
            longMinutes.setDigitValue(parts[2].toIntOrNull() ?: 0, animate = false)
            if (countdownLongContainer.visibility != View.VISIBLE) {
                countdownShortContainer.visibility = View.GONE
                crossfade(usageDetails, countdownLongContainer)
            } else {
                usageDetails.visibility = View.GONE
                countdownShortContainer.visibility = View.GONE
            }
        } else {
            shortHours.setDigitValue(parts[0].toIntOrNull() ?: 0, animate = false)
            shortMinutes.setDigitValue(parts[1].toIntOrNull() ?: 0, animate = false)
            shortSeconds.setDigitValue(parts[2].toIntOrNull() ?: 0, animate = false)
            if (countdownShortContainer.visibility != View.VISIBLE) {
                countdownLongContainer.visibility = View.GONE
                crossfade(usageDetails, countdownShortContainer)
            } else {
                usageDetails.visibility = View.GONE
                countdownLongContainer.visibility = View.GONE
            }
        }
    }

    private fun crossfade(from: View, to: View, duration: Long = 300L) {
        if (from.visibility == View.GONE && to.visibility == View.VISIBLE && to.alpha == 1f) {
            return
        }

        from.animate().cancel()
        to.animate().cancel()

        if (from.visibility == View.GONE) {
            to.alpha = 0f
            to.visibility = View.VISIBLE
            to.animate()
                .alpha(1f)
                .setDuration(duration)
                .start()
            return
        }

        from.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                from.visibility = View.GONE
                from.alpha = 1f
                to.alpha = 0f
                to.visibility = View.VISIBLE
                to.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .start()
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllAnimations()
    }

    private fun cancelAllAnimations() {
        claudeShortHours.animate().cancel()
        claudeShortMinutes.animate().cancel()
        claudeShortSeconds.animate().cancel()
        claudeLongDays.animate().cancel()
        claudeLongHours.animate().cancel()
        claudeLongMinutes.animate().cancel()
        kimiShortHours.animate().cancel()
        kimiShortMinutes.animate().cancel()
        kimiShortSeconds.animate().cancel()
        kimiLongDays.animate().cancel()
        kimiLongHours.animate().cancel()
        kimiLongMinutes.animate().cancel()
        claudeUsageDetails.animate().cancel()
        kimiUsageDetails.animate().cancel()
        claudeCountdownShortContainer.animate().cancel()
        claudeCountdownLongContainer.animate().cancel()
        kimiCountdownShortContainer.animate().cancel()
        kimiCountdownLongContainer.animate().cancel()
    }

    private fun buildResetLabel(percentage: String, timeRemaining: String): SpannableString {
        val prefix = getString(R.string.format_with_reset, percentage, getString(R.string.resets_in, "")).trimEnd(',', ' ')
        val fullText = "$prefix $timeRemaining"
        val spannable = SpannableString(fullText)

        val defaultColor = claudeFiveHourResetsIn.currentTextColor
        spannable.setSpan(
            ForegroundColorSpan(defaultColor),
            0,
            fullText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val dayColor = ContextCompat.getColor(this, R.color.reset_time_days)
        val hourColor = ContextCompat.getColor(this, R.color.reset_time_hours)
        val minuteColor = ContextCompat.getColor(this, R.color.reset_time_minutes)

        val regex = """(\d+d)|(\d+h)|(\d+m)""".toRegex()
        regex.findAll(timeRemaining).forEach { matchResult ->
            val component = matchResult.value
            val start = prefix.length + 1 + matchResult.range.first
            val end = start + component.length
            val color = when {
                component.endsWith("d") -> dayColor
                component.endsWith("h") -> hourColor
                component.endsWith("m") -> minuteColor
                else -> defaultColor
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_PREFS = "notification_permission_prefs"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
