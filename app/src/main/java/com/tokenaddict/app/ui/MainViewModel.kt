package com.tokenaddict.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.SessionManager
import com.tokenaddict.app.data.HttpConfig
import com.tokenaddict.app.data.model.SessionState
import com.tokenaddict.app.worker.ClaudeUsageWorker
import com.tokenaddict.app.worker.KimiUsageWorker
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val injectedKimiTokenManager: KimiTokenManager? = null
) : AndroidViewModel(application) {

    sealed class UiState {
        object Loading : UiState()
        object LoggedOut : UiState()
        data class UsageData(
            val utilization: Double,
            val resetsAt: String,
            val timeRemaining: String,
            val isReset: Boolean,
            val lastChecked: String,
            val weeklyUtilization: Double = 0.0,
            val weeklyResetsIn: String = "",
            val weeklyIsReset: Boolean = false,
            val hasReachedLimit: Boolean = false,
            val serviceChanged: Boolean = false,
            val limitCountdownText: String = "",
            val countdownHasDays: Boolean = false
        ) : UiState()
    }

    private val _claudeState = MutableLiveData<UiState>(UiState.Loading)
    val claudeState: LiveData<UiState> = _claudeState

    private val _kimiState = MutableLiveData<UiState>(UiState.Loading)
    val kimiState: LiveData<UiState> = _kimiState

    // Backward-compatible alias — delegates to claudeState for existing MainActivity
    val uiState: LiveData<UiState> = _claudeState

    private val claudeSessionManager = SessionManager(application, "claude")

    private val kimiTokenManager: KimiTokenManager by lazy {
        injectedKimiTokenManager ?: run {
            val gson = Gson()
            val context = getApplication<Application>()
            KimiTokenManager(
                SecurePreferences.create(context, "kimi_tokens"),
                KimiOAuthManager(OkHttpClient.Builder()
                    .connectTimeout(HttpConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(HttpConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(HttpConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(), gson),
                gson
            )
        }
    }

    private val claudePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        viewModelScope.launch {
            loadUsageData("claude")
        }
    }
    private val kimiPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        viewModelScope.launch {
            loadUsageData("kimi")
        }
    }

    private var claudeResetsAtMillis: Long = 0L
    private var claudeWeeklyResetsAtMillis: Long = 0L
    private var kimiResetsAtMillis: Long = 0L
    private var kimiWeeklyResetsAtMillis: Long = 0L
    private var claudeCountdownJob: Job? = null
    private var kimiCountdownJob: Job? = null
    private var isForeground = true

    init {
        val app = getApplication<Application>()
        app.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(claudePrefsListener)
        app.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(kimiPrefsListener)

        checkSession("claude")
        checkSession("kimi")
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdownTimer("claude")
        stopCountdownTimer("kimi")
        val app = getApplication<Application>()
        app.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(claudePrefsListener)
        app.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(kimiPrefsListener)
    }

    fun checkSession(providerId: String = "claude") {
        val state = getStateLiveData(providerId) ?: return

        if (providerId == "claude") {
            when (claudeSessionManager.getSessionState()) {
                is SessionState.LoggedIn -> loadUsageData("claude")
                SessionState.LoggedOut -> state.value = UiState.LoggedOut
                else -> state.value = UiState.LoggedOut
            }
        } else if (providerId == "kimi") {
            val hasTokens = kimiTokenManager.getAccessToken() != null
            if (!hasTokens) {
                state.value = UiState.LoggedOut
            } else {
                loadUsageData("kimi")
            }
        }
    }

    fun refresh(providerId: String? = null) {
        if (providerId == null || providerId == "claude") {
            _claudeState.value = UiState.Loading
            val workRequest = OneTimeWorkRequestBuilder<ClaudeUsageWorker>().build()
            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueue(workRequest)
            val workLiveData = workManager.getWorkInfoByIdLiveData(workRequest.id)
            val observer = object : Observer<WorkInfo?> {
                override fun onChanged(workInfo: WorkInfo?) {
                    if (workInfo == null || workInfo.state.isFinished) {
                        checkSession("claude")
                        workLiveData.removeObserver(this)
                    }
                }
            }
            workLiveData.observeForever(observer)
        }
        if (providerId == null || providerId == "kimi") {
            _kimiState.value = UiState.Loading
            val workRequest = OneTimeWorkRequestBuilder<KimiUsageWorker>().build()
            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueue(workRequest)
            val workLiveData = workManager.getWorkInfoByIdLiveData(workRequest.id)
            val observer = object : Observer<WorkInfo?> {
                override fun onChanged(workInfo: WorkInfo?) {
                    if (workInfo == null || workInfo.state.isFinished) {
                        checkSession("kimi")
                        workLiveData.removeObserver(this)
                    }
                }
            }
            workLiveData.observeForever(observer)
        }
    }

    fun logout(providerId: String = "claude") {
        if (providerId == "claude") {
            claudeSessionManager.clearSession()
            NotificationScheduler(getApplication(), "claude").cancelResetNotification()
        } else if (providerId == "kimi") {
            kimiTokenManager.clearTokens()
        }
        getStateLiveData(providerId)?.value = UiState.LoggedOut
    }

    private fun formatTimeRemaining(resetsAtMillis: Long, isReset: Boolean): String {
        if (isReset || resetsAtMillis <= 0) return ""
        val remainingMillis = resetsAtMillis - System.currentTimeMillis()
        if (remainingMillis <= 0) return ""
        val days = remainingMillis / (1000 * 60 * 60 * 24)
        val hours = (remainingMillis / (1000 * 60 * 60)) % 24
        val minutes = (remainingMillis / (1000 * 60)) % 60
        return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
    }

    private fun formatLimitCountdown(resetsAtMillis: Long): String {
        if (resetsAtMillis <= 0) return ""
        val remaining = resetsAtMillis - System.currentTimeMillis()
        if (remaining <= 0) return ""
        val days = remaining / (1000 * 60 * 60 * 24)
        val hours = (remaining / (1000 * 60 * 60)) % 24
        val minutes = (remaining / (1000 * 60)) % 60
        val seconds = (remaining / 1000) % 60
        return if (days > 0) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", days, hours, minutes)
        } else {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    private fun hasDaysInLimitCountdown(resetsAtMillis: Long): Boolean {
        if (resetsAtMillis <= 0) return false
        val remaining = resetsAtMillis - System.currentTimeMillis()
        return remaining > 1000 * 60 * 60 * 24
    }

    private fun startCountdownTimer(providerId: String) {
        val jobRef = if (providerId == "claude") claudeCountdownJob else kimiCountdownJob
        jobRef?.cancel()
        val newJob = viewModelScope.launch {
            while (isActive) {
                if (!isForeground) break
                delay(1_000)
                updateCountdown(providerId)
            }
        }
        if (providerId == "claude") claudeCountdownJob = newJob
        else kimiCountdownJob = newJob
    }

    private fun updateCountdown(providerId: String) {
        val liveData = getStateLiveData(providerId) ?: return
        val state = liveData.value
        if (state !is UiState.UsageData || !state.hasReachedLimit) {
            stopCountdownTimer(providerId)
            return
        }
        val fiveHourMillis = if (providerId == "claude") claudeResetsAtMillis else kimiResetsAtMillis
        val weeklyMillis = if (providerId == "claude") claudeWeeklyResetsAtMillis else kimiWeeklyResetsAtMillis
        val fiveHourReached = state.utilization >= 100.0 && fiveHourMillis > System.currentTimeMillis()
        val weeklyReached = state.weeklyUtilization >= 100.0 && weeklyMillis > System.currentTimeMillis()
        val effectiveMillis = maxOf(
            if (fiveHourReached) fiveHourMillis else Long.MIN_VALUE,
            if (weeklyReached) weeklyMillis else Long.MIN_VALUE
        )
        val countdownText = formatLimitCountdown(effectiveMillis)
        val countdownHasDays = hasDaysInLimitCountdown(effectiveMillis)
        liveData.value = state.copy(limitCountdownText = countdownText, countdownHasDays = countdownHasDays)
    }

    private fun stopCountdownTimer(providerId: String) {
        val job = if (providerId == "claude") claudeCountdownJob else kimiCountdownJob
        job?.cancel()
        if (providerId == "claude") claudeCountdownJob = null
        else kimiCountdownJob = null
    }

    fun onForegroundChanged(isForeground: Boolean) {
        this.isForeground = isForeground
        if (isForeground) {
            listOf("claude", "kimi").forEach { providerId ->
                val liveData = getStateLiveData(providerId) ?: return@forEach
                val state = liveData.value
                when (state) {
                    is UiState.UsageData -> {
                        if (state.hasReachedLimit) {
                            startCountdownTimer(providerId)
                        }
                    }
                    is UiState.Loading -> {
                        // WorkManager worker may have been delayed (Doze/battery).
                        // Re-check SharedPreferences in case the worker wrote data
                        // while the app was paused, or re-trigger refresh if not.
                        loadUsageData(providerId)
                    }
                    else -> { /* LoggedOut or null — nothing to do */ }
                }
            }
        } else {
            stopCountdownTimer("claude")
            stopCountdownTimer("kimi")
        }
    }

    private fun loadUsageData(providerId: String) {
        val prefsName = if (providerId == "claude") "usage_prefs" else "usage_prefs_kimi"
        val prefs = getApplication<Application>()
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        val isReset = prefs.getBoolean("is_reset", false)
        val utilization = if (isReset) 0.0 else prefs.getFloat("utilization", 0f).toDouble()
        val resetsAtMillis = prefs.getLong("resets_at", 0L)
        val lastCheckedMillis = prefs.getLong("last_checked", 0L)

        val weeklyIsReset = prefs.getBoolean("weekly_is_reset", false)
        val weeklyUtilization = if (weeklyIsReset) 0.0 else prefs.getFloat("weekly_utilization", 0f).toDouble()
        val weeklyResetsAtMillis = prefs.getLong("weekly_resets_at", 0L)

        if (providerId == "claude") {
            claudeResetsAtMillis = resetsAtMillis
            claudeWeeklyResetsAtMillis = weeklyResetsAtMillis
        } else {
            kimiResetsAtMillis = resetsAtMillis
            kimiWeeklyResetsAtMillis = weeklyResetsAtMillis
        }

        val fiveHourReached = utilization >= 100.0 && resetsAtMillis > System.currentTimeMillis()
        val weeklyReached = weeklyUtilization >= 100.0 && weeklyResetsAtMillis > System.currentTimeMillis()
        val hasReachedLimit = fiveHourReached || weeklyReached

        val countdownHasDays: Boolean
        val limitCountdownText = if (hasReachedLimit) {
            val effectiveMillis = maxOf(
                if (fiveHourReached) resetsAtMillis else Long.MIN_VALUE,
                if (weeklyReached) weeklyResetsAtMillis else Long.MIN_VALUE
            )
            countdownHasDays = hasDaysInLimitCountdown(effectiveMillis)
            formatLimitCountdown(effectiveMillis)
        } else {
            countdownHasDays = false
            stopCountdownTimer(providerId)
            ""
        }

        if (hasReachedLimit) {
            startCountdownTimer(providerId)
        }

        val serviceChanged = if (providerId == "claude") {
            prefs.getBoolean("claude_service_changed", false)
        } else {
            false
        }

        val state = getStateLiveData(providerId) ?: return

        if (lastCheckedMillis == 0L) {
            state.value = UiState.Loading
            refresh(providerId)
            return
        }

        val dateFormatter = DateTimeFormatter
            .ofPattern("MMM dd, HH:mm")
            .withZone(ZoneId.systemDefault())

        val resetsAt = if (resetsAtMillis > 0L) {
            dateFormatter.format(Instant.ofEpochMilli(resetsAtMillis))
        } else {
            "N/A"
        }
        val lastChecked = dateFormatter.format(Instant.ofEpochMilli(lastCheckedMillis))

        val timeRemaining = formatTimeRemaining(resetsAtMillis, isReset)
        val weeklyResetsIn = formatTimeRemaining(weeklyResetsAtMillis, weeklyIsReset)

        state.value = UiState.UsageData(
            utilization = utilization,
            resetsAt = resetsAt,
            timeRemaining = timeRemaining,
            isReset = isReset,
            lastChecked = lastChecked,
            weeklyUtilization = weeklyUtilization,
            weeklyResetsIn = weeklyResetsIn,
            weeklyIsReset = weeklyIsReset,
            hasReachedLimit = hasReachedLimit,
            serviceChanged = serviceChanged,
            limitCountdownText = limitCountdownText,
            countdownHasDays = countdownHasDays
        )
    }

    private fun getStateLiveData(providerId: String): MutableLiveData<UiState>? {
        return when (providerId) {
            "claude" -> _claudeState
            "kimi" -> _kimiState
            else -> null
        }
    }

    internal fun getClaudeState(): MutableLiveData<UiState> = _claudeState
    internal fun getKimiState(): MutableLiveData<UiState> = _kimiState
}
