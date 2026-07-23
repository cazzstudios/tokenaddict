package com.tokenaddict.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
    private lateinit var claudeFableProgress: ProgressBar
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

    // Claude long digits (DD:HH:MM:SS)
    private lateinit var claudeLongDays: CountdownDigitView
    private lateinit var claudeLongHours: CountdownDigitView
    private lateinit var claudeLongMinutes: CountdownDigitView
    private lateinit var claudeLongSeconds: CountdownDigitView

    // Kimi countdown containers
    private lateinit var kimiCountdownShortContainer: LinearLayout
    private lateinit var kimiCountdownLongContainer: LinearLayout

    // Kimi short digits (HH:MM:SS)
    private lateinit var kimiShortHours: CountdownDigitView
    private lateinit var kimiShortMinutes: CountdownDigitView
    private lateinit var kimiShortSeconds: CountdownDigitView

    // Kimi long digits (DD:HH:MM:SS)
    private lateinit var kimiLongDays: CountdownDigitView
    private lateinit var kimiLongHours: CountdownDigitView
    private lateinit var kimiLongMinutes: CountdownDigitView
    private lateinit var kimiLongSeconds: CountdownDigitView

    private val claudeLoginLauncher = registerForActivityResult(LoginResultContract()) { success ->
        if (success) {
            viewModel.checkSession("claude")
            checkPermissionsAfterLogin()
        }
    }

    private val kimiLoginLauncher = registerForActivityResult(KimiLoginResultContract()) { success ->
        if (success) {
            viewModel.checkSession("kimi")
            checkPermissionsAfterLogin()
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

    private fun checkPermissionsAfterLogin() {
        if (hasShownPostLoginPermissionDialog()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showPermissionDialog()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showPermissionDialog()
                return
            }
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            showPermissionDialog()
            return
        }
    }

    private fun showPermissionDialog() {
        markPostLoginPermissionDialogShown()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(R.string.permission_dialog_message)
            .setPositiveButton(R.string.permission_dialog_open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.permission_dialog_not_now, null)
            .show()
    }

    private fun getPermissionPrefs(): SharedPreferences {
        return getSharedPreferences(PERMISSION_DIALOG_PREFS, MODE_PRIVATE)
    }

    private fun hasShownPostLoginPermissionDialog(): Boolean {
        return getPermissionPrefs().getBoolean(KEY_PERMISSION_DIALOG_SHOWN, false)
    }

    private fun markPostLoginPermissionDialogShown() {
        getPermissionPrefs().edit()
            .putBoolean(KEY_PERMISSION_DIALOG_SHOWN, true)
            .apply()
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
        claudeFableProgress = findViewById(R.id.claudeFableProgress)
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

        // Claude short countdown (include root)
        val claudeShortInclude = findViewById<LinearLayout>(R.id.claude_countdown_short)
        claudeCountdownShortContainer = claudeShortInclude
        claudeShortHours = claudeShortInclude.findViewById(R.id.hours_digit)
        claudeShortMinutes = claudeShortInclude.findViewById(R.id.minutes_digit)
        claudeShortSeconds = claudeShortInclude.findViewById(R.id.seconds_digit)

        // Claude long countdown (include root)
        val claudeLongInclude = findViewById<LinearLayout>(R.id.claude_countdown_long)
        claudeCountdownLongContainer = claudeLongInclude
        claudeLongDays = claudeLongInclude.findViewById(R.id.days_digit)
        claudeLongHours = claudeLongInclude.findViewById(R.id.hours_digit)
        claudeLongMinutes = claudeLongInclude.findViewById(R.id.minutes_digit)
        claudeLongSeconds = claudeLongInclude.findViewById(R.id.seconds_digit)

        kimiRobotIcon = findViewById(R.id.kimiRobotIcon)
        kimiUsageDetails = findViewById(R.id.kimiUsageDetails)

        // Kimi short countdown (include root)
        val kimiShortInclude = findViewById<LinearLayout>(R.id.kimi_countdown_short)
        kimiCountdownShortContainer = kimiShortInclude
        kimiShortHours = kimiShortInclude.findViewById(R.id.hours_digit)
        kimiShortMinutes = kimiShortInclude.findViewById(R.id.minutes_digit)
        kimiShortSeconds = kimiShortInclude.findViewById(R.id.seconds_digit)

        // Kimi long countdown (include root)
        val kimiLongInclude = findViewById<LinearLayout>(R.id.kimi_countdown_long)
        kimiCountdownLongContainer = kimiLongInclude
        kimiLongDays = kimiLongInclude.findViewById(R.id.days_digit)
        kimiLongHours = kimiLongInclude.findViewById(R.id.hours_digit)
        kimiLongMinutes = kimiLongInclude.findViewById(R.id.minutes_digit)
        kimiLongSeconds = kimiLongInclude.findViewById(R.id.seconds_digit)
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

        val fablePercent = data.fableUtilization.toInt()
        claudeFableProgress.progress = fablePercent

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
            longSeconds = claudeLongSeconds,
            hasReachedLimit = data.hasReachedLimit,
            countdownText = data.limitCountdownText,
            hasDays = data.countdownHasDays
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
            longSeconds = kimiLongSeconds,
            hasReachedLimit = data.hasReachedLimit,
            countdownText = data.limitCountdownText,
            hasDays = data.countdownHasDays
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
        longSeconds: CountdownDigitView,
        hasReachedLimit: Boolean,
        countdownText: String,
        hasDays: Boolean
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

        if (hasDays) {
            // DD:HH:MM format — use long container, hide seconds
            longDays.setDigitValue(parts[0].toIntOrNull() ?: 0, animate = false)
            longHours.setDigitValue(parts[1].toIntOrNull() ?: 0, animate = false)
            longMinutes.setDigitValue(parts[2].toIntOrNull() ?: 0, animate = false)
            countdownLongContainer.findViewById<View>(R.id.days_column).visibility = View.VISIBLE
            countdownLongContainer.findViewById<View>(R.id.days_colon).visibility = View.VISIBLE
            countdownLongContainer.findViewById<View>(R.id.seconds_column).visibility = View.GONE
            countdownLongContainer.findViewById<View>(R.id.seconds_colon).visibility = View.GONE
            if (countdownLongContainer.visibility != View.VISIBLE) {
                countdownShortContainer.visibility = View.GONE
                crossfade(usageDetails, countdownLongContainer)
            } else {
                usageDetails.visibility = View.GONE
                countdownShortContainer.visibility = View.GONE
            }
        } else {
            // HH:MM:SS format — use short container
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
        claudeLongSeconds.animate().cancel()
        kimiShortHours.animate().cancel()
        kimiShortMinutes.animate().cancel()
        kimiShortSeconds.animate().cancel()
        kimiLongDays.animate().cancel()
        kimiLongHours.animate().cancel()
        kimiLongMinutes.animate().cancel()
        kimiLongSeconds.animate().cancel()
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
        private const val PERMISSION_DIALOG_PREFS = "permission_dialog_prefs"
        private const val KEY_PERMISSION_DIALOG_SHOWN = "permission_dialog_shown"
    }
}
