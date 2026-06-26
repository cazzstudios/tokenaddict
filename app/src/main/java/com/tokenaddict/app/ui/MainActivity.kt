package com.tokenaddict.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private companion object {
        const val FULL_ALPHA = 1.0f
        const val DIMMED_ALPHA = 0.35f
    }

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

    private lateinit var claudeWorkingRobot: ImageView
    private lateinit var claudeRestingRobot: ImageView
    private lateinit var kimiWorkingRobot: ImageView
    private lateinit var kimiRestingRobot: ImageView

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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> { }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
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

        claudeWorkingRobot = findViewById(R.id.claudeWorkingRobot)
        claudeRestingRobot = findViewById(R.id.claudeRestingRobot)
        kimiWorkingRobot = findViewById(R.id.kimiWorkingRobot)
        kimiRestingRobot = findViewById(R.id.kimiRestingRobot)
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

        setRobotIconEvidence(claudeWorkingRobot, claudeRestingRobot, data.hasReachedLimit)

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

        setRobotIconEvidence(kimiWorkingRobot, kimiRestingRobot, data.hasReachedLimit)

        kimiLastCheckedText.text = getString(R.string.last_checked, data.lastChecked)
    }

    private fun setRobotIconEvidence(workingRobot: ImageView, restingRobot: ImageView, hasReachedLimit: Boolean) {
        workingRobot.alpha = if (hasReachedLimit) DIMMED_ALPHA else FULL_ALPHA
        restingRobot.alpha = if (hasReachedLimit) FULL_ALPHA else DIMMED_ALPHA
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
}
