package com.tokenaddict.app.security

import android.content.Context
import android.os.Build
import android.os.Debug
import com.tokenaddict.app.BuildConfig
import java.io.File

sealed class SecurityStatus {
    object Safe : SecurityStatus()
    data class Risky(val reasons: List<String>) : SecurityStatus()
}

internal interface SecurityEnvironment {
    val isDebugBuild: Boolean
    val board: String
    val manufacturer: String
    val hardware: String
    val fingerprint: String
    val tags: String
    val isDebuggerConnected: Boolean
    fun fileExists(path: String): Boolean
}

internal object DefaultSecurityEnvironment : SecurityEnvironment {
    override val isDebugBuild: Boolean get() = BuildConfig.DEBUG
    override val board: String get() = Build.BOARD.orEmpty()
    override val manufacturer: String get() = Build.MANUFACTURER.orEmpty()
    override val hardware: String get() = Build.HARDWARE.orEmpty()
    override val fingerprint: String get() = Build.FINGERPRINT.orEmpty()
    override val tags: String get() = Build.TAGS.orEmpty()
    override val isDebuggerConnected: Boolean get() = Debug.isDebuggerConnected()
    override fun fileExists(path: String): Boolean = File(path).exists()
}

object SecurityChecker {
    private const val TAG = "SecurityChecker"

    private val EMULATOR_BOARDS = setOf("goldfish", "ranchu")
    private val EMULATOR_MANUFACTURERS = setOf(
        "Genymotion", "generic", "Android SDK", "google", "unknown"
    )
    private val EMULATOR_HARDWARE = setOf("goldfish", "ranchu", "generic")
    private val ROOT_BINARIES = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"
    )

    @Volatile
    internal var environment: SecurityEnvironment = DefaultSecurityEnvironment

    fun checkEnvironment(@Suppress("UNUSED_PARAMETER") context: Context): SecurityStatus {
        val env = environment
        val reasons = mutableListOf<String>()

        if (env.isDebugBuild) {
            reasons += "Debug build"
        }

        val emuSignals = buildList {
            if (env.board.lowercase() in EMULATOR_BOARDS) add("board=${env.board}")
            if (env.manufacturer in EMULATOR_MANUFACTURERS) add("manufacturer=${env.manufacturer}")
            if (env.hardware.lowercase() in EMULATOR_HARDWARE) add("hardware=${env.hardware}")
            if (env.fingerprint.contains("generic", ignoreCase = true) ||
                env.fingerprint.contains("unknown", ignoreCase = true)
            ) add("fingerprint=${env.fingerprint}")
        }
        if (emuSignals.size >= 2) {
            reasons += "Emulator (${emuSignals.joinToString()})"
        }

        if (env.tags.contains("test-keys", ignoreCase = true)) {
            reasons += "Root (test-keys tag)"
        }
        val suPath = ROOT_BINARIES.firstOrNull { env.fileExists(it) }
        if (suPath != null) {
            reasons += "Root (su binary at $suPath)"
        }

        if (env.isDebuggerConnected) {
            reasons += "Debugger attached"
        }

        return if (reasons.isEmpty()) SecurityStatus.Safe else SecurityStatus.Risky(reasons)
    }
}
