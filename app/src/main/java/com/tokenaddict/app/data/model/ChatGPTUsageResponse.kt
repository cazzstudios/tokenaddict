package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class ChatGPTUsageResponse(
    @SerializedName("user_id") val userId: String?,
    @SerializedName("account_id") val accountId: String?,
    val email: String?,
    @SerializedName("plan_type") val planType: String?,
    @SerializedName("rate_limit") val rateLimit: ChatGPRateLimit?,
    @SerializedName("additional_rate_limits") val additionalRateLimits: List<ChatGPTAdditionalRateLimit>?
)

data class ChatGPRateLimit(
    val allowed: Boolean?,
    @SerializedName("limit_reached") val limitReached: Boolean?,
    @SerializedName("primary_window") val primaryWindow: ChatGPTWindow?,
    @SerializedName("secondary_window") val secondaryWindow: ChatGPTWindow?
)

data class ChatGPTWindow(
    @SerializedName("used_percent") val usedPercent: Double?,
    @SerializedName("limit_window_seconds") val limitWindowSeconds: Long?,
    @SerializedName("reset_after_seconds") val resetAfterSeconds: Long?,
    @SerializedName("reset_at") val resetAt: Long?
)

data class ChatGPTAdditionalRateLimit(
    val id: String?,
    val title: String?,
    @SerializedName("primary_window") val primaryWindow: ChatGPTWindow?,
    @SerializedName("secondary_window") val secondaryWindow: ChatGPTWindow?
)
