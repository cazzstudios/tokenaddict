package com.tokenaddict.app.data.model

import com.google.gson.annotations.SerializedName

data class ChatGPTAuthResponse(
    @SerializedName("accessToken") val accessToken: String?,
    @SerializedName("account_id") val accountId: String?,
    val email: String?
)
