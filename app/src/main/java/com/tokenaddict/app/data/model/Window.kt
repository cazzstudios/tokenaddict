package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class Window(
    val utilization: Double?,
    @SerializedName("resets_at") val resetsAt: String?  // ISO 8601 format
)
