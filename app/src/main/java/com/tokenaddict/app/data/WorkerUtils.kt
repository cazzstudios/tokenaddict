package com.tokenaddict.app.data

import android.content.SharedPreferences
import com.tokenaddict.app.data.TimeUtils.parseResetTime
import com.tokenaddict.app.data.model.UsageInfo
import java.time.Instant

data class ResetBlockState(
    val fiveHourBlocking: Boolean,
    val weeklyBlocking: Boolean,
    val effectiveResetMillis: Long
)

object WorkerUtils {
    /** Compute reset-blocking state from usage info + current time. */
    fun computeBlockingState(
        usageInfo: UsageInfo,
        now: Instant = Instant.now()
    ): ResetBlockState {
        val fiveHourInstant = usageInfo.resetsAt.parseResetTime()
        val weeklyInstant = usageInfo.weeklyResetsAt.parseResetTime()

        val isFiveHourLimitReached = usageInfo.utilization >= 100.0
        val isWeeklyLimitReached = usageInfo.weeklyUtilization >= 100.0

        val fiveHourBlocking = isFiveHourLimitReached && fiveHourInstant?.isAfter(now) == true
        val weeklyBlocking = isWeeklyLimitReached && weeklyInstant?.isAfter(now) == true

        val effectiveResetMillis = maxOf(
            if (fiveHourBlocking) fiveHourInstant!!.toEpochMilli() else Long.MIN_VALUE,
            if (weeklyBlocking) weeklyInstant!!.toEpochMilli() else Long.MIN_VALUE
        )

        return ResetBlockState(fiveHourBlocking, weeklyBlocking, effectiveResetMillis)
    }

    /** Schedule or cancel notification based on blocking state. */
    fun scheduleOrCancelNotification(
        blockingState: ResetBlockState,
        notificationScheduler: NotificationScheduler
    ) {
        if (blockingState.fiveHourBlocking || blockingState.weeklyBlocking) {
            notificationScheduler.scheduleResetNotification(blockingState.effectiveResetMillis)
        } else {
            notificationScheduler.cancelResetNotification()
        }
    }
}

/** Write usage data to SharedPreferences. */
fun SharedPreferences.Editor.writeUsagePrefs(
    usageInfo: UsageInfo,
    resetTimeMillis: Long,
    weeklyResetTimeMillis: Long,
    fableResetTimeMillis: Long = 0L
): SharedPreferences.Editor {
    return this
        .putFloat("utilization", usageInfo.utilization.toFloat())
        .putLong("resets_at", resetTimeMillis)
        .putBoolean("is_reset", usageInfo.isReset)
        .putFloat("weekly_utilization", usageInfo.weeklyUtilization.toFloat())
        .putLong("weekly_resets_at", weeklyResetTimeMillis)
        .putBoolean("weekly_is_reset", usageInfo.weeklyIsReset)
        .putFloat("fable_utilization", usageInfo.fableUtilization.toFloat())
        .putLong("fable_resets_at", fableResetTimeMillis)
        .putBoolean("fable_is_reset", usageInfo.fableIsReset)
        .putLong("last_checked", System.currentTimeMillis())
}
