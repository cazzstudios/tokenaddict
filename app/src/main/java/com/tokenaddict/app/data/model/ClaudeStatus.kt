package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class ClaudeStatusResponse(
    val page: ClaudeStatusPage,
    val status: ClaudeStatusIndicator
)

data class ClaudeStatusPage(
    val id: String,
    val name: String,
    val url: String
)

data class ClaudeStatusIndicator(
    val indicator: String,
    val description: String
)

enum class ClaudeStatusLevel(val indicator: String, val displayName: String) {
    @SerializedName("none")
    NONE("none", "Operational"),
    @SerializedName("minor")
    MINOR("minor", "Degraded"),
    @SerializedName("major")
    MAJOR("major", "Outage"),
    @SerializedName("critical")
    CRITICAL("critical", "Major Outage");

    companion object {
        fun fromIndicator(indicator: String): ClaudeStatusLevel {
            return entries.find { it.indicator == indicator } ?: NONE
        }
    }

    val isOperational: Boolean get() = this == NONE
    val isOutage: Boolean get() = this == MAJOR || this == CRITICAL
}
