package com.tokenaddict.app.data

import android.content.Context
import android.util.Log
import com.tokenaddict.app.R
import kotlin.random.Random

class NotificationMessageProvider internal constructor(
    private val context: Context,
    private val random: Random = Random.Default
) {
    companion object {
        private const val TAG = "NotifMsgProvider"
        private const val PREFS_NAME = "notification_message_provider"
        private const val KEY_LAST_MESSAGE_INDEX = "last_message_index"
        private const val AGENT_PLACEHOLDER = "[agent]"
    }

    private val displayNameMap = mapOf(
        "claude" to "Claude",
        "kimi" to "Kimi"
    )

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getResetMessage(providerId: String): String {
        val messages = context.resources.getStringArray(R.array.notification_reset_messages)

        if (messages.size <= 1) {
            Log.d(TAG, "Pool too small (${messages.size}), using fallback")
            return getFallbackMessage(providerId)
        }

        val displayName = displayNameMap[providerId]
        if (displayName == null) {
            Log.d(TAG, "Unknown provider '$providerId', using fallback")
            return getFallbackMessage(providerId)
        }

        val lastIndex = prefs.getInt(KEY_LAST_MESSAGE_INDEX, -1)
        val index = pickRandomIndex(messages.size, lastIndex)

        prefs.edit().putInt(KEY_LAST_MESSAGE_INDEX, index).apply()

        Log.d(TAG, "Selected message $index of ${messages.size} for $providerId")

        return messages[index].replace(AGENT_PLACEHOLDER, displayName)
    }

    private fun pickRandomIndex(poolSize: Int, lastIndex: Int): Int {
        if (lastIndex < 0 || lastIndex >= poolSize) {
            return random.nextInt(poolSize)
        }

        var newIndex: Int
        do {
            newIndex = random.nextInt(poolSize)
        } while (newIndex == lastIndex)

        return newIndex
    }

    private fun getFallbackMessage(providerId: String): String {
        return when (providerId) {
            "kimi" -> context.getString(R.string.notification_reset_message_kimi)
            else -> context.getString(R.string.notification_reset_message)
        }
    }
}
