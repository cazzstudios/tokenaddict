package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class UsageResponse(
    @SerializedName("five_hour") val fiveHour: Window?,
    @SerializedName("seven_day") val sevenDay: Window?,
    @SerializedName("seven_day_sonnet") val sevenDaySonnet: Window?,
    @SerializedName("seven_day_opus") val sevenDayOpus: Window?,
    @SerializedName("seven_day_oauth_apps") val sevenDayOauthApps: Window?,
    @SerializedName("seven_day_cowork") val sevenDayCowork: Window?,
    val extraUsage: ExtraUsage?
)
