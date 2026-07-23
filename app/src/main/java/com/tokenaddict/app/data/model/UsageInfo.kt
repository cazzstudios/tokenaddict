package com.tokenaddict.app.data.model

data class UsageInfo(
    val utilization: Double,
    val resetsAt: String?,
    val isReset: Boolean,
    val providerId: String,
    val weeklyUtilization: Double = 0.0,
    val weeklyResetsAt: String? = null,
    val weeklyIsReset: Boolean = false,
    val fableUtilization: Double = 0.0,
    val fableResetsAt: String? = null,
    val fableIsReset: Boolean = false
)
