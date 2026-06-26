package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class KimiUsageResponse(
    @SerializedName("usage") val usage: KimiUsageWindow? = null,
    @SerializedName("limits") val limits: List<KimiRateLimit>? = null,
    @SerializedName("resetTime") val resetTime: String? = null,
    @SerializedName("user") val user: KimiUser? = null
)

data class KimiUsageWindow(
    @SerializedName("limit") val limit: String? = null,
    @SerializedName("used") val used: String? = null,
    @SerializedName("remaining") val remaining: String? = null,
    @SerializedName("resetTime") val resetTime: String? = null
)

data class KimiRateLimit(
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("timeUnit") val timeUnit: String? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("remaining") val remaining: Int? = null,
    @SerializedName("window") val window: KimiRateLimitWindow? = null,
    @SerializedName("detail") val detail: KimiRateLimitDetail? = null
)

data class KimiRateLimitWindow(
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("timeUnit") val timeUnit: String? = null
)

data class KimiRateLimitDetail(
    @SerializedName("limit") val limit: String? = null,
    @SerializedName("used") val used: String? = null,
    @SerializedName("remaining") val remaining: String? = null,
    @SerializedName("resetTime") val resetTime: String? = null
)

data class KimiUser(
    @SerializedName("membership") val membership: KimiMembership? = null
)

data class KimiMembership(
    @SerializedName("level") val level: String? = null
)
