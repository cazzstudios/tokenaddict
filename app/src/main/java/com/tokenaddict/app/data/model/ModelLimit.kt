package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class ModelLimit(
    val kind: String?,
    val group: String?,
    val percent: Double?,
    val severity: String?,
    @SerializedName("resets_at") val resetsAt: String?,
    val scope: ModelScope?,
    @SerializedName("is_active") val isActive: Boolean?
)

data class ModelScope(
    val model: ModelInfo?,
    val surface: String?
)

data class ModelInfo(
    val id: String?,
    @SerializedName("display_name") val displayName: String?
)