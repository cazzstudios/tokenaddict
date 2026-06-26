package com.tokenaddict.app.security

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class SecurityCheckerTest {

    private lateinit var fake: FakeEnvironment
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        fake = FakeEnvironment()
        SecurityChecker.environment = fake
    }

    @After
    fun tearDown() {
        SecurityChecker.environment = DefaultSecurityEnvironment
    }

    @Test
    fun `returns Safe on clean production environment`() {
        fake.isDebugBuild = false
        fake.board = "samsungexynos990"
        fake.manufacturer = "samsung"
        fake.hardware = "samsungexynos990"
        fake.fingerprint = "samsung/r0qeur/r0q:13/TP1A.220624.014/G991BXXSGFWK5:user/release-keys"
        fake.tags = "release-keys"
        fake.isDebuggerConnected = false

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Safe)
    }

    @Test
    fun `detects debug build`() {
        fake.isDebugBuild = false
        configureCleanDevice()

        val resultSafe = SecurityChecker.checkEnvironment(context)
        assertTrue(resultSafe is SecurityStatus.Safe)

        fake.isDebugBuild = true
        val resultRisky = SecurityChecker.checkEnvironment(context)

        assertTrue(resultRisky is SecurityStatus.Risky)
        assertEquals("Debug build", (resultRisky as SecurityStatus.Risky).reasons.single())
    }

    @Test
    fun `detects emulator via goldfish board and generic manufacturer`() {
        fake.isDebugBuild = false
        fake.board = "goldfish"
        fake.manufacturer = "generic"
        fake.hardware = "goldfish"
        fake.fingerprint = "generic/sdk_gphone64_x86_64/emu64xa:13/TP1A.220624.014/9351448:userdebug/dev-keys"
        fake.tags = "dev-keys"

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Risky)
        val reasons = (result as SecurityStatus.Risky).reasons
        assertTrue(reasons.any { it.startsWith("Emulator") })
    }

    @Test
    fun `detects emulator via ranchu hardware and unknown fingerprint`() {
        fake.isDebugBuild = false
        fake.board = "ranchu"
        fake.manufacturer = "Google"
        fake.hardware = "ranchu"
        fake.fingerprint = "unknown"
        fake.tags = "test-keys"

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Risky)
        val reasons = (result as SecurityStatus.Risky).reasons
        assertTrue(reasons.any { it.startsWith("Emulator") })
    }

    @Test
    fun `does not false-positive on real device with unusual fingerprint`() {
        fake.isDebugBuild = false
        fake.board = "kalama"
        fake.manufacturer = "OnePlus"
        fake.hardware = "qcom"
        fake.fingerprint = "OnePlus/CPH2583/CPH2583:14/UP1A.231005.007/1700000000:user/release-keys"
        fake.tags = "release-keys"

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Safe)
    }

    @Test
    fun `detects root via test-keys tag`() {
        fake.isDebugBuild = false
        configureCleanDevice()
        fake.tags = "test-keys"

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Risky)
        val reasons = (result as SecurityStatus.Risky).reasons
        assertTrue(reasons.any { it.contains("test-keys") })
    }

    @Test
    fun `detects root via su binary`() {
        fake.isDebugBuild = false
        configureCleanDevice()
        fake.knownFiles = setOf("/system/bin/su")

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Risky)
        val reasons = (result as SecurityStatus.Risky).reasons
        assertTrue(reasons.any { it.contains("su binary") })
    }

    @Test
    fun `detects debugger attached`() {
        fake.isDebugBuild = false
        configureCleanDevice()
        fake.isDebuggerConnected = true

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Risky)
        assertEquals("Debugger attached", (result as SecurityStatus.Risky).reasons.single())
    }

    @Test
    fun `reports multiple risks simultaneously`() {
        fake.isDebugBuild = true
        fake.board = "goldfish"
        fake.manufacturer = "generic"
        fake.hardware = "generic"
        fake.fingerprint = "generic"
        fake.tags = "test-keys"
        fake.isDebuggerConnected = true
        fake.knownFiles = setOf("/system/xbin/su")

        val result = SecurityChecker.checkEnvironment(context)

        assertTrue(result is SecurityStatus.Risky)
        val reasons = (result as SecurityStatus.Risky).reasons
        assertTrue(reasons.size >= 4)
        assertTrue(reasons.any { it == "Debug build" })
        assertTrue(reasons.any { it.startsWith("Emulator") })
        assertTrue(reasons.any { it.contains("test-keys") })
        assertTrue(reasons.any { it.contains("su binary") })
        assertTrue(reasons.any { it == "Debugger attached" })
    }

    private fun configureCleanDevice() {
        fake.board = "samsungexynos990"
        fake.manufacturer = "samsung"
        fake.hardware = "samsungexynos990"
        fake.fingerprint = "samsung/r0qeur/r0q:13/TP1A.220624.014/user/release-keys"
        fake.tags = "release-keys"
        fake.isDebuggerConnected = false
    }
}

private class FakeEnvironment : SecurityEnvironment {
    override var isDebugBuild: Boolean = false
    override var board: String = ""
    override var manufacturer: String = ""
    override var hardware: String = ""
    override var fingerprint: String = ""
    override var tags: String = ""
    override var isDebuggerConnected: Boolean = false
    var knownFiles: Set<String> = emptySet()

    override fun fileExists(path: String): Boolean = path in knownFiles
}
