package com.tokenaddict.app.data

import java.time.Instant
import java.time.OffsetDateTime

object TimeUtils {
    /** Parses ISO-8601 timestamp, returns Instant or null on parse failure (lenient). */
    fun String?.parseResetTime(): Instant? {
        if (this == null) return null
        return try {
            OffsetDateTime.parse(this).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    /** Parses ISO-8601 timestamp, throws on parse failure (strict — for Claude-style propagation). */
    fun String?.parseResetTimeStrict(): Instant {
        return OffsetDateTime.parse(this).toInstant()
    }

    /** Returns true if the instant is before the current time (i.e., has already reset). */
    fun Instant.isReset(): Boolean = this.isBefore(Instant.now())

    /** Combined: parses reset time (lenient) and returns (millis, isReset). Falls back to (0L, false). */
    fun computeResetState(resetsAt: String?, tag: String): Pair<Long, Boolean> {
        val instant = resetsAt.parseResetTime()
        if (instant == null) return 0L to false
        return instant.toEpochMilli() to instant.isReset()
    }
}
