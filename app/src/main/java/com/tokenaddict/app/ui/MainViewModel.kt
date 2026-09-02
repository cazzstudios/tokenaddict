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
import com.tokenaddict.app.data.ChatGPTSessionManager
import com.tokenaddict.app.data.KimiOAuthManager
import com.tokenaddict.app.data.KimiTokenManager
import com.tokenaddict.app.data.NotificationScheduler
import com.tokenaddict.app.data.SecurePreferences
import com.tokenaddict.app.data.SessionManager
import com.tokenaddict.app.data.HttpConfig
import com.tokenaddict.app.data.model.SessionState
import com.tokenaddict.app.worker.ClaudeUsageWorker
import com.tokenaddict.app.worker.ChatGPTUsageWorker
import com.tokenaddict.app.worker.KimiUsageWorker
import com.tokenaddict.app.worker.ClaudeStatusWorker
import com.tokenaddict.app.worker.ChatGPTStatusWorker
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
            val fableUtilization: Double = 0.0,
            val fableResetsIn: String = "",
            val fableIsReset: Boolean = false,
            val fableResetsAtMillis: Long = 0L,
            val hasReachedLimit: Boolean = false,
            val serviceChanged: Boolean = false,
            val limitCountdownText: String = "",
            val countdownHasDays: Boolean = false,
            val resetsAtMillis: Long = 0L,
            val weeklyResetsAtMillis: Long = 0L
        ) : UiState()
    }

    data class ServiceStatus(
        val indicator: String,
        val description: String,
        val lastChecked: Long
    )

    private val _claudeState = MutableLiveData<UiState>(UiState.Loading)
    val claudeState: LiveData<UiState> = _claudeState

    private val _kimiState = MutableLiveData<UiState>(UiState.Loading)
    val kimiState: LiveData<UiState> = _kimiState

    private val _chatgptState = MutableLiveData<UiState>(UiState.Loading)
    val chatgptState: LiveData<UiState> = _chatgptState

    // Backward-compatible alias — delegates to claudeState for existing MainActivity
    val uiState: LiveData<UiState> = _claudeState

    private val _claudeServiceStatus = MutableLiveData<ServiceStatus?>()
    val claudeServiceStatus: LiveData<ServiceStatus?> = _claudeServiceStatus

    private val _chatgptServiceStatus = MutableLiveData<ServiceStatus?>()
    val chatgptServiceStatus: LiveData<ServiceStatus?> = _chatgptServiceStatus

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

    private val chatgptSessionManager = ChatGPTSessionManager(application)

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
    private val chatgptPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        viewModelScope.launch {
            loadUsageData("chatgpt")
        }
    }
    private val statusPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        viewModelScope.launch {
            loadServiceStatus()
        }
    }
    private val chatgptStatusPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        viewModelScope.launch {
            loadChatGPTServiceStatus()
        }
    }

    private var claudeResetsAtMillis: Long = 0L
    private var claudeWeeklyResetsAtMillis: Long = 0L
    private var kimiResetsAtMillis: Long = 0L
    private var kimiWeeklyResetsAtMillis: Long = 0L
    private var chatgptResetsAtMillis: Long = 0L
    private var chatgptWeeklyResetsAtMillis: Long = 0L
    private var claudeCountdownJob: Job? = null
    private var kimiCountdownJob: Job? = null
    private var chatgptCountdownJob: Job? = null
    private var isForeground = true

    init {
        val app = getApplication<Application>()
        app.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(claudePrefsListener)
        app.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(kimiPrefsListener)
        app.getSharedPreferences("usage_prefs_chatgpt", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(chatgptPrefsListener)
        app.getSharedPreferences(ClaudeStatusWorker.STATUS_PREFS, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(statusPrefsListener)
        app.getSharedPreferences(ChatGPTStatusWorker.STATUS_PREFS, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(chatgptStatusPrefsListener)

        checkSession("claude")
        checkSession("kimi")
        checkSession("chatgpt")
        loadServiceStatus()
        loadChatGPTServiceStatus()
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdownTimer("claude")
        stopCountdownTimer("kimi")
        stopCountdownTimer("chatgpt")
        val app = getApplication<Application>()
        app.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(claudePrefsListener)
        app.getSharedPreferences("usage_prefs_kimi", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(kimiPrefsListener)
        app.getSharedPreferences("usage_prefs_chatgpt", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(chatgptPrefsListener)
        app.getSharedPreferences(ClaudeStatusWorker.STATUS_PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(statusPrefsListener)
        app.getSharedPreferences(ChatGPTStatusWorker.STATUS_PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(chatgptStatusPrefsListener)
    }

    fun checkSession(providerId: String = "claude") {
        val state = getStateLiveData(providerId) ?: return

        if (providerId == "claude") {
            val sessionState = claudeSessionManager.getSessionState()
            val loggedIn = claudeSessionManager.isLoggedIn()
            if (sessionState is SessionState.LoggedIn && !loggedIn) {
                claudeSessionManager.clearSession()
                state.value = UiState.LoggedOut
            } else when (sessionState) {
                is SessionState.LoggedIn -> loadUsageData("claude")
                SessionState.LoggedOut -> state.value = UiState.LoggedOut
                else -> state.value = UiState.LoggedOut
            }
        } else if (providerId == "kimi") {
            val hasTokens = kimiTokenManager.getAccessToken() != null && kimiTokenManager.isAccessTokenValid()
            if (!hasTokens) {
                state.value = UiState.LoggedOut
            } else {
                loadUsageData("kimi")
            }
        } else if (providerId == "chatgpt") {
            if (!chatgptSessionManager.isLoggedIn()) {
                state.value = UiState.LoggedOut
            } else {
                loadUsageData("chatgpt")
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
        if (providerId == null || providerId == "chatgpt") {
            _chatgptState.value = UiState.Loading
            val workRequest = OneTimeWorkRequestBuilder<ChatGPTUsageWorker>().build()
            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueue(workRequest)
            val workLiveData = workManager.getWorkInfoByIdLiveData(workRequest.id)
            val observer = object : Observer<WorkInfo?> {
                override fun onChanged(workInfo: WorkInfo?) {
                    if (workInfo == null || workInfo.state.isFinished) {
                        checkSession("chatgpt")
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
        } else if (providerId == "chatgpt") {
            chatgptSessionManager.clearSession()
            NotificationScheduler(getApplication(), "chatgpt").cancelResetNotification()
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
        val jobRef = when (providerId) {
            "claude" -> claudeCountdownJob
            "kimi" -> kimiCountdownJob
            "chatgpt" -> chatgptCountdownJob
            else -> null
        }
        jobRef?.cancel()
        val newJob = viewModelScope.launch {
            while (isActive) {
                if (!isForeground) break
                delay(1_000)
                updateCountdown(providerId)
            }
        }
        when (providerId) {
            "claude" -> claudeCountdownJob = newJob
            "kimi" -> kimiCountdownJob = newJob
            "chatgpt" -> chatgptCountdownJob = newJob
        }
    }

    private fun updateCountdown(providerId: String) {
        val liveData = getStateLiveData(providerId) ?: return
        val state = liveData.value
        if (state !is UiState.UsageData || !state.hasReachedLimit) {
            stopCountdownTimer(providerId)
            return
        }
        val fiveHourMillis = when (providerId) {
            "claude" -> claudeResetsAtMillis
            "kimi" -> kimiResetsAtMillis
            "chatgpt" -> chatgptResetsAtMillis
            else -> 0L
        }
        val weeklyMillis = when (providerId) {
            "claude" -> claudeWeeklyResetsAtMillis
            "kimi" -> kimiWeeklyResetsAtMillis
            "chatgpt" -> chatgptWeeklyResetsAtMillis
            else -> 0L
        }
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
        val job = when (providerId) {
            "claude" -> claudeCountdownJob
            "kimi" -> kimiCountdownJob
            "chatgpt" -> chatgptCountdownJob
            else -> null
        }
        job?.cancel()
        when (providerId) {
            "claude" -> claudeCountdownJob = null
            "kimi" -> kimiCountdownJob = null
            "chatgpt" -> chatgptCountdownJob = null
        }
    }

    fun onForegroundChanged(isForeground: Boolean) {
        this.isForeground = isForeground
        if (isForeground) {
            listOf("claude", "kimi", "chatgpt").forEach { providerId ->
                val liveData = getStateLiveData(providerId) ?: return@forEach
                val state = liveData.value
                when (state) {
                    is UiState.UsageData -> {
                        if (state.hasReachedLimit) {
                            startCountdownTimer(providerId)
                        }
                    }
                    is UiState.Loading -> {
                        loadUsageData(providerId)
                    }
                    else -> { /* LoggedOut or null — nothing to do */ }
                }
            }
        } else {
            stopCountdownTimer("claude")
            stopCountdownTimer("kimi")
            stopCountdownTimer("chatgpt")
        }
    }

    private fun loadUsageData(providerId: String) {
        val prefsName = when (providerId) {
            "claude" -> "usage_prefs"
            "kimi" -> "usage_prefs_kimi"
            "chatgpt" -> "usage_prefs_chatgpt"
            else -> "usage_prefs"
        }
        val prefs = getApplication<Application>()
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        val isReset = prefs.getBoolean("is_reset", false)
        val utilization = if (isReset) 0.0 else prefs.getFloat("utilization", 0f).toDouble()
        val resetsAtMillis = prefs.getLong("resets_at", 0L)
        val lastCheckedMillis = prefs.getLong("last_checked", 0L)

        val weeklyIsReset = prefs.getBoolean("weekly_is_reset", false)
        val weeklyUtilization = if (weeklyIsReset) 0.0 else prefs.getFloat("weekly_utilization", 0f).toDouble()
        val weeklyResetsAtMillis = prefs.getLong("weekly_resets_at", 0L)

        val fableIsReset = prefs.getBoolean("fable_is_reset", false)
        val fableUtilization = if (fableIsReset) 0.0 else prefs.getFloat("fable_utilization", 0f).toDouble()
        val fableResetsAtMillis = prefs.getLong("fable_resets_at", 0L)

        if (providerId == "claude") {
            claudeResetsAtMillis = resetsAtMillis
            claudeWeeklyResetsAtMillis = weeklyResetsAtMillis
        } else if (providerId == "kimi") {
            kimiResetsAtMillis = resetsAtMillis
            kimiWeeklyResetsAtMillis = weeklyResetsAtMillis
        } else {
            chatgptResetsAtMillis = resetsAtMillis
            chatgptWeeklyResetsAtMillis = weeklyResetsAtMillis
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
        val fableResetsIn = formatTimeRemaining(fableResetsAtMillis, fableIsReset)

        state.value = UiState.UsageData(
            utilization = utilization,
            resetsAt = resetsAt,
            timeRemaining = timeRemaining,
            isReset = isReset,
            lastChecked = lastChecked,
            weeklyUtilization = weeklyUtilization,
            weeklyResetsIn = weeklyResetsIn,
            weeklyIsReset = weeklyIsReset,
            fableUtilization = fableUtilization,
            fableResetsIn = fableResetsIn,
            fableIsReset = fableIsReset,
            fableResetsAtMillis = fableResetsAtMillis,
            hasReachedLimit = hasReachedLimit,
            serviceChanged = serviceChanged,
            limitCountdownText = limitCountdownText,
            countdownHasDays = countdownHasDays,
            resetsAtMillis = resetsAtMillis,
            weeklyResetsAtMillis = weeklyResetsAtMillis
        )
    }

    private fun getStateLiveData(providerId: String): MutableLiveData<UiState>? {
        return when (providerId) {
            "claude" -> _claudeState
            "kimi" -> _kimiState
            "chatgpt" -> _chatgptState
            else -> null
        }
    }

    private fun loadServiceStatus() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(ClaudeStatusWorker.STATUS_PREFS, Context.MODE_PRIVATE)

        val indicator = prefs.getString(ClaudeStatusWorker.KEY_STATUS_INDICATOR, null)
        val description = prefs.getString(ClaudeStatusWorker.KEY_STATUS_DESCRIPTION, null)
        val lastChecked = prefs.getLong(ClaudeStatusWorker.KEY_STATUS_LAST_CHECKED, 0L)

        if (indicator != null && description != null) {
            _claudeServiceStatus.value = ServiceStatus(indicator, description, lastChecked)
        } else {
            _claudeServiceStatus.value = null
        }
    }

    private fun loadChatGPTServiceStatus() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(ChatGPTStatusWorker.STATUS_PREFS, Context.MODE_PRIVATE)

        val indicator = prefs.getString(ChatGPTStatusWorker.KEY_STATUS_INDICATOR, null)
        val description = prefs.getString(ChatGPTStatusWorker.KEY_STATUS_DESCRIPTION, null)
        val lastChecked = prefs.getLong(ChatGPTStatusWorker.KEY_STATUS_LAST_CHECKED, 0L)

        if (indicator != null && description != null) {
            _chatgptServiceStatus.value = ServiceStatus(indicator, description, lastChecked)
        } else {
            _chatgptServiceStatus.value = null
        }
    }

    internal fun getClaudeState(): MutableLiveData<UiState> = _claudeState
    internal fun getKimiState(): MutableLiveData<UiState> = _kimiState
    internal fun getChatGPTState(): MutableLiveData<UiState> = _chatgptState
}
