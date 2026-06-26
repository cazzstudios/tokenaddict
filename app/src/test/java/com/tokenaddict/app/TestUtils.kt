package com.tokenaddict.app

import com.tokenaddict.app.data.model.AccountResponse
import com.tokenaddict.app.data.model.ExtraUsage
import com.tokenaddict.app.data.model.Membership
import com.tokenaddict.app.data.model.Organization
import com.tokenaddict.app.data.model.UsageInfo
import com.tokenaddict.app.data.model.UsageResponse
import com.tokenaddict.app.data.model.Window
import java.time.Instant
import java.time.format.DateTimeFormatter

object TestUtils {

    val sampleUsageJson = """
        {
            "five_hour": {
                "utilization": 28.0,
                "resets_at": "${DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(3600))}"
            },
            "seven_day": {
                "utilization": 67.0,
                "resets_at": "${DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(86400))}"
            },
            "seven_day_sonnet": {
                "utilization": 5.0,
                "resets_at": "${DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(86400))}"
            },
            "seven_day_opus": null,
            "seven_day_oauth_apps": null,
            "seven_day_cowork": null,
            "extra_usage": {
                "is_enabled": false,
                "monthly_limit": null,
                "used_credits": null,
                "utilization": null
            }
        }
    """.trimIndent()

    val sampleAccountJson = """
        {
            "memberships": [
                {"organization": {"uuid": "org-abc-123", "name": "Test Organization"}}
            ]
        }
    """.trimIndent()

    fun createMockUsageResponse(
        fiveHourUtilization: Double = 28.0,
        fiveHourResetsAt: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(3600)),
        sevenDayUtilization: Double = 67.0
    ): UsageResponse {
        return UsageResponse(
            fiveHour = Window(
                utilization = fiveHourUtilization,
                resetsAt = fiveHourResetsAt
            ),
            sevenDay = Window(
                utilization = sevenDayUtilization,
                resetsAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(86400))
            ),
            sevenDaySonnet = Window(
                utilization = 5.0,
                resetsAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(86400))
            ),
            sevenDayOpus = null,
            sevenDayOauthApps = null,
            sevenDayCowork = null,
            extraUsage = ExtraUsage(
                isEnabled = false,
                monthlyLimit = null,
                usedCredits = null,
                utilization = null
            )
        )
    }

    fun createMockAccountResponse(
        orgUuid: String = "org-abc-123",
        orgName: String = "Test Organization"
    ): AccountResponse {
        return AccountResponse(
            tagged_id = null,
            uuid = null,
            email_address = null,
            full_name = null,
            display_name = null,
            memberships = listOf(
                Membership(organization = Organization(uuid = orgUuid, name = orgName))
            )
        )
    }

    fun createExpiredResetTime(): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().minusSeconds(3600))
    }

    fun createFutureResetTime(hoursFromNow: Int = 1): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(hoursFromNow * 3600L))
    }

    fun createMockUsageInfo(
        utilization: Double = 28.0,
        resetsAt: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(3600)),
        isReset: Boolean = false,
        weeklyUtilization: Double = 0.0,
        weeklyResetsAt: String? = null,
        weeklyIsReset: Boolean = false,
        providerId: String = "claude"
    ): UsageInfo {
        return UsageInfo(
            utilization = utilization,
            resetsAt = resetsAt,
            isReset = isReset,
            weeklyUtilization = weeklyUtilization,
            weeklyResetsAt = weeklyResetsAt,
            weeklyIsReset = weeklyIsReset,
            providerId = providerId
        )
    }
}
