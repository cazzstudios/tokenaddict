package com.tokenaddict.app.security

import com.tokenaddict.app.R
import com.tokenaddict.app.TestTokenAddictApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTokenAddictApplication::class)
class NetworkSecurityConfigTest {

    private lateinit var parser: XmlResourceParser

    private val expectedDomains = setOf("claude.ai", "anthropic.com", "auth.kimi.com", "api.kimi.com")

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        parser = context.resources.getXml(R.xml.network_security_config)
    }

    @Test
    fun `base-config declares cleartextTrafficPermitted false`() {
        val baseConfigCleartext = parseBaseConfigCleartext()
        assertEquals(
            "Global cleartext traffic must be disabled",
            "false",
            baseConfigCleartext
        )
    }

    @Test
    fun `contains all 4 pinned domains`() {
        val domains = parseAllDomains()
        assertEquals("Expected 4 pinned domains", 4, domains.size)
        for (expected in expectedDomains) {
            assertTrue(
                "Missing pinned domain: $expected",
                domains.contains(expected)
            )
        }
    }

    @Test
    fun `each domain has at least 2 certificate pins`() {
        val domainPinCounts = parseDomainPinCounts()
        for (domain in expectedDomains) {
            val count = domainPinCounts[domain]
                ?: error("Domain $domain not found in config")
            assertTrue(
                "Domain $domain should have at least 2 pins, found $count",
                count >= 2
            )
        }
    }

    @Test
    fun `all domain-config blocks disable cleartext traffic`() {
        val domainCleartextMap = parseDomainCleartextFlags()
        for (domain in expectedDomains) {
            val permitted = domainCleartextMap[domain]
                ?: error("Domain $domain not found in config")
            assertEquals(
                "Domain $domain must have cleartextTrafficPermitted=false",
                "false",
                permitted
            )
        }
    }

    private data class ParsedConfig(
        val baseConfigCleartext: String?,
        val domainConfigs: List<DomainEntry>
    )

    private data class DomainEntry(
        val domain: String,
        val pinCount: Int,
        val cleartextPermitted: String?
    )

    private fun parseConfig(): ParsedConfig {
        var baseConfigCleartext: String? = null
        val domainConfigs = mutableListOf<DomainEntry>()
        var inBaseConfig = false
        var inDomainConfig = false
        var currentDomain: String? = null
        var currentCleartext: String? = null
        var pinCount = 0

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "base-config" -> {
                                inBaseConfig = true
                                baseConfigCleartext = parser.getAttributeValue(null, "cleartextTrafficPermitted")
                            }
                            "domain-config" -> {
                                inDomainConfig = true
                                currentCleartext = parser.getAttributeValue(null, "cleartextTrafficPermitted")
                            }
                            "domain" -> {
                                if (inDomainConfig) {
                                    currentDomain = parser.nextText().trim()
                                }
                            }
                            "pin" -> {
                                if (inDomainConfig) {
                                    pinCount++
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "domain-config" -> {
                                if (inDomainConfig && currentDomain != null) {
                                    domainConfigs.add(
                                        DomainEntry(
                                            domain = currentDomain!!,
                                            pinCount = pinCount,
                                            cleartextPermitted = currentCleartext
                                        )
                                    )
                                }
                                inDomainConfig = false
                                currentDomain = null
                                currentCleartext = null
                                pinCount = 0
                            }
                            "base-config" -> {
                                inBaseConfig = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }

        return ParsedConfig(baseConfigCleartext, domainConfigs)
    }

    private fun parseBaseConfigCleartext(): String? = parseConfig().baseConfigCleartext

    private fun parseAllDomains(): Set<String> =
        parseConfig().domainConfigs.map { it.domain }.toSet()

    private fun parseDomainPinCounts(): Map<String, Int> =
        parseConfig().domainConfigs.associate { it.domain to it.pinCount }

    private fun parseDomainCleartextFlags(): Map<String, String?> =
        parseConfig().domainConfigs.associate { it.domain to it.cleartextPermitted }
}
