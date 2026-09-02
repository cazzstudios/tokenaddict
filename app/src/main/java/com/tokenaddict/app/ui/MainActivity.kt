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
import com.tokenaddict.app.data.model.ClaudeStatusLevel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private lateinit var claudeLoadingContainer: LinearLayout
    private lateinit var claudeLoggedOutContainer: LinearLayout
    private lateinit var claudeUsageContainer: LinearLayout
    private lateinit var claudeFiveHourProgress: ProgressWithMarkerView
    private lateinit var claudeFiveHourResetsIn: TextView
    private lateinit var claudeWeeklyProgress: ProgressWithMarkerView
    private lateinit var claudeWeeklyResetsIn: TextView
    private lateinit var claudeFableProgress: ProgressWithMarkerView
    private lateinit var claudeLastCheckedText: TextView

    private lateinit var claudeServiceChangedBanner: TextView

    private lateinit var claudeStatusContainer: LinearLayout
    private lateinit var claudeStatusDot: View
    private lateinit var claudeStatusText: TextView

    private lateinit var chatgptStatusContainer: LinearLayout
    private lateinit var chatgptStatusDot: View
    private lateinit var chatgptStatusText: TextView

    private lateinit var kimiLoadingContainer: LinearLayout
    private lateinit var kimiLoggedOutContainer: LinearLayout
    private lateinit var kimiUsageContainer: LinearLayout
    private lateinit var kimiFiveHourProgress: ProgressWithMarkerView
    private lateinit var kimiFiveHourResetsIn: TextView
    private lateinit var kimiWeeklyProgress: ProgressWithMarkerView
    private lateinit var kimiWeeklyResetsIn: TextView
    private lateinit var kimiLastCheckedText: TextView

    private lateinit var claudeRobotIcon: ImageView
    private lateinit var claudeUsageDetails: LinearLayout
    private lateinit var kimiRobotIcon: ImageView
    private lateinit var kimiUsageDetails: LinearLayout

    private lateinit var chatgptLoadingContainer: LinearLayout
    private lateinit var chatgptLoggedOutContainer: LinearLayout
    private lateinit var chatgptUsageContainer: LinearLayout
    private lateinit var chatgptFiveHourProgress: ProgressWithMarkerView
    private lateinit var chatgptFiveHourResetsIn: TextView
    private lateinit var chatgptWeeklyProgress: ProgressWithMarkerView
    private lateinit var chatgptWeeklyResetsIn: TextView
    private lateinit var chatgptLastCheckedText: TextView
    private lateinit var chatgptRobotIcon: ImageView
    private lateinit var chatgptUsageDetails: LinearLayout

    private lateinit var claudeCard: MaterialCardView
    private lateinit var kimiCard: MaterialCardView
    private lateinit var chatgptCard: MaterialCardView
    private lateinit var fabConnect: FloatingActionButton
    private lateinit var emptyStateText: android.widget.TextView

    private var claudeUiState: MainViewModel.UiState = MainViewModel.UiState.Loading
    private var kimiUiState: MainViewModel.UiState = MainViewModel.UiState.Loading
    private var chatgptUiState: MainViewModel.UiState = MainViewModel.UiState.Loading

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

    // ChatGPT countdown containers
    private lateinit var chatgptCountdownShortContainer: LinearLayout
    private lateinit var chatgptCountdownLongContainer: LinearLayout

    // ChatGPT short digits (HH:MM:SS)
    private lateinit var chatgptShortHours: CountdownDigitView
    private lateinit var chatgptShortMinutes: CountdownDigitView
    private lateinit var chatgptShortSeconds: CountdownDigitView

    // ChatGPT long digits (DD:HH:MM:SS)
    private lateinit var chatgptLongDays: CountdownDigitView
    private lateinit var chatgptLongHours: CountdownDigitView
    private lateinit var chatgptLongMinutes: CountdownDigitView
    private lateinit var chatgptLongSeconds: CountdownDigitView

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

    private val chatgptLoginLauncher = registerForActivityResult(ChatGPTLoginResultContract()) { success ->
        if (success) {
            viewModel.checkSession("chatgpt")
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

        claudeStatusContainer = findViewById(R.id.claudeStatusContainer)
        claudeStatusDot = findViewById(R.id.claudeStatusDot)
        claudeStatusText = findViewById(R.id.claudeStatusText)

        chatgptStatusContainer = findViewById(R.id.chatgptStatusContainer)
        chatgptStatusDot = findViewById(R.id.chatgptStatusDot)
        chatgptStatusText = findViewById(R.id.chatgptStatusText)

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

        chatgptLoadingContainer = findViewById(R.id.chatgptLoadingContainer)
        chatgptLoggedOutContainer = findViewById(R.id.chatgptLoggedOutContainer)
        chatgptUsageContainer = findViewById(R.id.chatgptUsageContainer)
        chatgptFiveHourProgress = findViewById(R.id.chatgptFiveHourProgress)
        chatgptFiveHourResetsIn = findViewById(R.id.chatgptFiveHourResetsIn)
        chatgptWeeklyProgress = findViewById(R.id.chatgptWeeklyProgress)
        chatgptWeeklyResetsIn = findViewById(R.id.chatgptWeeklyResetsIn)
        chatgptLastCheckedText = findViewById(R.id.chatgptLastCheckedText)
        chatgptRobotIcon = findViewById(R.id.chatgptRobotIcon)
        chatgptUsageDetails = findViewById(R.id.chatgptUsageDetails)

        // ChatGPT short countdown (include root)
        val chatgptShortInclude = findViewById<LinearLayout>(R.id.chatgpt_countdown_short)
        chatgptCountdownShortContainer = chatgptShortInclude
        chatgptShortHours = chatgptShortInclude.findViewById(R.id.hours_digit)
        chatgptShortMinutes = chatgptShortInclude.findViewById(R.id.minutes_digit)
        chatgptShortSeconds = chatgptShortInclude.findViewById(R.id.seconds_digit)

        // ChatGPT long countdown (include root)
        val chatgptLongInclude = findViewById<LinearLayout>(R.id.chatgpt_countdown_long)
        chatgptCountdownLongContainer = chatgptLongInclude
        chatgptLongDays = chatgptLongInclude.findViewById(R.id.days_digit)
        chatgptLongHours = chatgptLongInclude.findViewById(R.id.hours_digit)
        chatgptLongMinutes = chatgptLongInclude.findViewById(R.id.minutes_digit)
        chatgptLongSeconds = chatgptLongInclude.findViewById(R.id.seconds_digit)

        claudeCard = findViewById(R.id.claudeCard)
        kimiCard = findViewById(R.id.kimiCard)
        chatgptCard = findViewById(R.id.chatgptCard)
        fabConnect = findViewById(R.id.fabConnect)
        emptyStateText = findViewById(R.id.emptyStateText)

        fabConnect.setOnClickListener { showConnectDialog() }
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

        findViewById<MaterialButton>(R.id.chatgptLoginButton).setOnClickListener {
            chatgptLoginLauncher.launch(Unit)
        }
        findViewById<MaterialButton>(R.id.chatgptRefreshButton).setOnClickListener {
            viewModel.refresh("chatgpt")
        }
        findViewById<MaterialButton>(R.id.chatgptLogoutButton).setOnClickListener {
            viewModel.logout("chatgpt")
        }
    }

    private fun observeUiState() {
        viewModel.claudeState.observe(this) { state ->
            claudeUiState = state
            when (state) {
                is MainViewModel.UiState.Loading -> showClaudeLoading()
                is MainViewModel.UiState.LoggedOut -> showClaudeLoggedOut()
                is MainViewModel.UiState.UsageData -> showClaudeUsageData(state)
            }
            updateCardVisibilities()
        }

        viewModel.kimiState.observe(this) { state ->
            kimiUiState = state
            when (state) {
                is MainViewModel.UiState.Loading -> showKimiLoading()
                is MainViewModel.UiState.LoggedOut -> showKimiLoggedOut()
                is MainViewModel.UiState.UsageData -> showKimiUsageData(state)
            }
            updateCardVisibilities()
        }

        viewModel.chatgptState.observe(this) { state ->
            chatgptUiState = state
            when (state) {
                is MainViewModel.UiState.Loading -> showChatGPTLoading()
                is MainViewModel.UiState.LoggedOut -> showChatGPTLoggedOut()
                is MainViewModel.UiState.UsageData -> showChatGPTUsageData(state)
            }
            updateCardVisibilities()
        }

        viewModel.claudeServiceStatus.observe(this) { status ->
            updateServiceStatusUI(status)
        }

        viewModel.chatgptServiceStatus.observe(this) { status ->
            updateChatGPTServiceStatusUI(status)
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
        claudeFiveHourProgress.markerPosition = computeMarkerPosition(
            data.resetsAtMillis, FIVE_HOUR_DURATION_MS, data.isReset
        )

        claudeFiveHourResetsIn.text = if (data.timeRemaining.isNotEmpty() && data.timeRemaining != getString(R.string.n_a)) {
            buildResetLabel("$fiveHourPercent%", data.timeRemaining)
        } else {
            "$fiveHourPercent%"
        }

        val weeklyPercent = data.weeklyUtilization.toInt()
        claudeWeeklyProgress.progress = weeklyPercent
        claudeWeeklyProgress.markerPosition = computeMarkerPosition(
            data.weeklyResetsAtMillis, WEEKLY_DURATION_MS, data.weeklyIsReset
        )

        claudeWeeklyResetsIn.text = if (data.weeklyResetsIn.isNotEmpty() && data.weeklyResetsIn != getString(R.string.n_a)) {
            buildResetLabel("$weeklyPercent%", data.weeklyResetsIn)
        } else {
            "$weeklyPercent%"
        }

        val fablePercent = data.fableUtilization.toInt()
        claudeFableProgress.progress = fablePercent
        claudeFableProgress.markerPosition = computeMarkerPosition(
            data.fableResetsAtMillis, WEEKLY_DURATION_MS, data.fableIsReset
        )

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
        kimiFiveHourProgress.markerPosition = computeMarkerPosition(
            data.resetsAtMillis, FIVE_HOUR_DURATION_MS, data.isReset
        )

        kimiFiveHourResetsIn.text = if (data.timeRemaining.isNotEmpty() && data.timeRemaining != getString(R.string.n_a)) {
            buildResetLabel("$fiveHourPercent%", data.timeRemaining)
        } else {
            "$fiveHourPercent%"
        }

        val weeklyPercent = data.weeklyUtilization.toInt()
        kimiWeeklyProgress.progress = weeklyPercent
        kimiWeeklyProgress.markerPosition = computeMarkerPosition(
            data.weeklyResetsAtMillis, WEEKLY_DURATION_MS, data.weeklyIsReset
        )

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

    private fun showChatGPTLoading() {
        chatgptLoadingContainer.visibility = View.VISIBLE
        chatgptLoggedOutContainer.visibility = View.GONE
        chatgptUsageContainer.visibility = View.GONE
    }

    private fun showChatGPTLoggedOut() {
        chatgptLoadingContainer.visibility = View.GONE
        chatgptLoggedOutContainer.visibility = View.VISIBLE
        chatgptUsageContainer.visibility = View.GONE
    }

    private fun showChatGPTUsageData(data: MainViewModel.UiState.UsageData) {
        chatgptLoadingContainer.visibility = View.GONE
        chatgptLoggedOutContainer.visibility = View.GONE
        chatgptUsageContainer.visibility = View.VISIBLE

        val fiveHourPercent = data.utilization.toInt()
        chatgptFiveHourProgress.progress = fiveHourPercent
        chatgptFiveHourProgress.markerPosition = computeMarkerPosition(
            data.resetsAtMillis, FIVE_HOUR_DURATION_MS, data.isReset
        )

        chatgptFiveHourResetsIn.text = if (data.timeRemaining.isNotEmpty() && data.timeRemaining != getString(R.string.n_a)) {
            buildResetLabel("$fiveHourPercent%", data.timeRemaining)
        } else {
            "$fiveHourPercent%"
        }

        val weeklyPercent = data.weeklyUtilization.toInt()
        chatgptWeeklyProgress.progress = weeklyPercent
        chatgptWeeklyProgress.markerPosition = computeMarkerPosition(
            data.weeklyResetsAtMillis, WEEKLY_DURATION_MS, data.weeklyIsReset
        )

        chatgptWeeklyResetsIn.text = if (data.weeklyResetsIn.isNotEmpty() && data.weeklyResetsIn != getString(R.string.n_a)) {
            buildResetLabel("$weeklyPercent%", data.weeklyResetsIn)
        } else {
            "$weeklyPercent%"
        }

        updateCountdownDisplay(
            robotIcon = chatgptRobotIcon,
            usageDetails = chatgptUsageDetails,
            countdownShortContainer = chatgptCountdownShortContainer,
            countdownLongContainer = chatgptCountdownLongContainer,
            shortHours = chatgptShortHours,
            shortMinutes = chatgptShortMinutes,
            shortSeconds = chatgptShortSeconds,
            longDays = chatgptLongDays,
            longHours = chatgptLongHours,
            longMinutes = chatgptLongMinutes,
            longSeconds = chatgptLongSeconds,
            hasReachedLimit = data.hasReachedLimit,
            countdownText = data.limitCountdownText,
            hasDays = data.countdownHasDays
        )

        chatgptLastCheckedText.text = getString(R.string.last_checked, data.lastChecked)
    }

    private fun updateCardVisibilities() {
        val claudeHidden = claudeUiState is MainViewModel.UiState.LoggedOut
        val kimiHidden = kimiUiState is MainViewModel.UiState.LoggedOut
        val chatgptHidden = chatgptUiState is MainViewModel.UiState.LoggedOut

        claudeCard.visibility = if (claudeHidden) View.GONE else View.VISIBLE
        kimiCard.visibility = if (kimiHidden) View.GONE else View.VISIBLE
        chatgptCard.visibility = if (chatgptHidden) View.GONE else View.VISIBLE

        val allHidden = claudeHidden && kimiHidden && chatgptHidden
        emptyStateText.visibility = if (allHidden) View.VISIBLE else View.GONE

        val anyLoggedOut = claudeHidden || kimiHidden || chatgptHidden
        fabConnect.visibility = if (anyLoggedOut) View.VISIBLE else View.GONE
    }

    private fun showConnectDialog() {
        val providers = mutableListOf<Pair<String, Int>>()
        if (claudeUiState is MainViewModel.UiState.LoggedOut) {
            providers.add("claude" to R.string.connect_claude)
        }
        if (kimiUiState is MainViewModel.UiState.LoggedOut) {
            providers.add("kimi" to R.string.connect_kimi)
        }
        if (chatgptUiState is MainViewModel.UiState.LoggedOut) {
            providers.add("chatgpt" to R.string.connect_chatgpt)
        }

        if (providers.isEmpty()) return

        val labels = providers.map { getString(it.second) }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connect_provider_title)
            .setItems(labels) { _, which ->
                val providerId = providers[which].first
                when (providerId) {
                    "claude" -> claudeLoginLauncher.launch(Unit)
                    "kimi" -> kimiLoginLauncher.launch(Unit)
                    "chatgpt" -> chatgptLoginLauncher.launch(Unit)
                }
            }
            .show()
    }

    private fun updateServiceStatusUI(status: MainViewModel.ServiceStatus?) {
        if (status == null) {
            claudeStatusContainer.visibility = View.GONE
            return
        }

        claudeStatusContainer.visibility = View.VISIBLE

        val level = ClaudeStatusLevel.fromIndicator(status.indicator)
        val statusText: String
        val dotDrawable: Int

        when (level) {
            ClaudeStatusLevel.NONE -> {
                statusText = getString(R.string.status_operational)
                dotDrawable = R.drawable.status_dot_operational
            }
            ClaudeStatusLevel.MINOR -> {
                statusText = getString(R.string.status_degraded)
                dotDrawable = R.drawable.status_dot_degraded
            }
            ClaudeStatusLevel.MAJOR -> {
                statusText = getString(R.string.status_outage)
                dotDrawable = R.drawable.status_dot_outage
            }
            ClaudeStatusLevel.CRITICAL -> {
                statusText = getString(R.string.status_major_outage)
                dotDrawable = R.drawable.status_dot_outage
            }
        }

        claudeStatusText.text = statusText
        claudeStatusDot.setBackgroundResource(dotDrawable)
    }

    private fun updateChatGPTServiceStatusUI(status: MainViewModel.ServiceStatus?) {
        if (status == null) {
            chatgptStatusContainer.visibility = View.GONE
            return
        }

        chatgptStatusContainer.visibility = View.VISIBLE

        val level = ClaudeStatusLevel.fromIndicator(status.indicator)
        val statusText: String
        val dotDrawable: Int

        when (level) {
            ClaudeStatusLevel.NONE -> {
                statusText = getString(R.string.status_operational)
                dotDrawable = R.drawable.status_dot_operational
            }
            ClaudeStatusLevel.MINOR -> {
                statusText = getString(R.string.status_degraded)
                dotDrawable = R.drawable.status_dot_degraded
            }
            ClaudeStatusLevel.MAJOR -> {
                statusText = getString(R.string.status_outage)
                dotDrawable = R.drawable.status_dot_outage
            }
            ClaudeStatusLevel.CRITICAL -> {
                statusText = getString(R.string.status_major_outage)
                dotDrawable = R.drawable.status_dot_outage
            }
        }

        chatgptStatusText.text = statusText
        chatgptStatusDot.setBackgroundResource(dotDrawable)
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
        chatgptShortHours.animate().cancel()
        chatgptShortMinutes.animate().cancel()
        chatgptShortSeconds.animate().cancel()
        chatgptLongDays.animate().cancel()
        chatgptLongHours.animate().cancel()
        chatgptLongMinutes.animate().cancel()
        chatgptLongSeconds.animate().cancel()
        claudeUsageDetails.animate().cancel()
        kimiUsageDetails.animate().cancel()
        chatgptUsageDetails.animate().cancel()
        claudeCountdownShortContainer.animate().cancel()
        claudeCountdownLongContainer.animate().cancel()
        kimiCountdownShortContainer.animate().cancel()
        kimiCountdownLongContainer.animate().cancel()
        chatgptCountdownShortContainer.animate().cancel()
        chatgptCountdownLongContainer.animate().cancel()
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
        private const val FIVE_HOUR_DURATION_MS = 5L * 60 * 60 * 1000
        private const val WEEKLY_DURATION_MS = 7L * 24 * 60 * 60 * 1000
    }

    /**
     * Compute where the pace marker should appear (0–100 %) given the reset
     * timestamp and fixed window duration.  Returns -1 (hidden) when the
     * window has already reset or the data is unavailable.
     */
    private fun computeMarkerPosition(
        resetsAtMillis: Long,
        windowDurationMs: Long,
        isReset: Boolean
    ): Float {
        if (isReset || resetsAtMillis <= 0L) return -1f
        val now = System.currentTimeMillis()
        if (now >= resetsAtMillis) return -1f
        val windowStart = resetsAtMillis - windowDurationMs
        val elapsed = (now - windowStart).coerceIn(0L, windowDurationMs)
        return (elapsed * 100f / windowDurationMs).coerceIn(0f, 100f)
    }
}
