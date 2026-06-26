package com.tokenaddict.app.data.model

data class AccountResponse(
    val tagged_id: String?,
    val uuid: String?,
    val email_address: String?,
    val full_name: String?,
    val display_name: String?,
    val memberships: List<Membership>?
)
