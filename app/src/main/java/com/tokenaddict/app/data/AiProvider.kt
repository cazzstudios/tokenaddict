package com.tokenaddict.app.data

import com.tokenaddict.app.data.model.AccountInfo
import com.tokenaddict.app.data.model.UsageInfo

interface AiProvider {
    val id: String
    val displayName: String
    val baseUrl: String
    val loginUrl: String

    suspend fun getAccount(): AccountInfo
    suspend fun getUsage(): UsageInfo
    suspend fun isLoggedIn(): Boolean
    fun logout()
}
