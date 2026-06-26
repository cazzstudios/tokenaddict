package com.tokenaddict.app.data.model

sealed class SessionState {
    object LoggedOut : SessionState()
    data class LoggedIn(val email: String? = null) : SessionState()
    object Expired : SessionState()
}
