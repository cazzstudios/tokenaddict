package com.tokenaddict.app.data.model

data class KimiOAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)
