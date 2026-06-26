package com.tokenaddict.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            val serviceChanged: Boolean = false
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
        loadUsageData("claude")
    }
    private val kimiPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadUsageData("kimi")
    }

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
            val workRequest = OneTimeWorkRequestBuilder<ClaudeUsageWorker>().build()
            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueue(workRequest)
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    if (workInfo != null && workInfo.state == WorkInfo.State.SUCCEEDED) {
                        loadUsageData("claude")
                    }
                }
        }
        if (providerId == null || providerId == "kimi") {
            val workRequest = OneTimeWorkRequestBuilder<KimiUsageWorker>().build()
            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueue(workRequest)
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    if (workInfo != null && workInfo.state == WorkInfo.State.SUCCEEDED) {
                        loadUsageData("kimi")
                    }
                }
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

        val hasReachedLimit = utilization >= 100.0 || weeklyUtilization >= 100.0

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
            serviceChanged = serviceChanged
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
