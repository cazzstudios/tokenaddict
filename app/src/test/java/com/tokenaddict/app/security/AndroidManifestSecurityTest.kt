package com.tokenaddict.app.security

import com.tokenaddict.app.TestTokenAddictApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTokenAddictApplication::class)
class AndroidManifestSecurityTest {

    private fun readManifestXml(): String {
        val manifest = File("src/test/resources/AndroidManifest.xml")
        assertTrue(
            "AndroidManifest.xml must exist in test resources",
            manifest.exists()
        )
        return manifest.readText()
    }

    @Test
    fun `manifest declares networkSecurityConfig attribute`() {
        val xml = readManifestXml()
        assertTrue(
            "AndroidManifest must declare android:networkSecurityConfig",
            xml.contains("android:networkSecurityConfig")
        )
        assertTrue(
            "networkSecurityConfig must reference @xml/network_security_config",
            xml.contains("@xml/network_security_config")
        )
    }

    @Test
    fun `manifest declares usesCleartextTraffic false`() {
        val xml = readManifestXml()
        assertTrue(
            "AndroidManifest must declare android:usesCleartextTraffic=\"false\"",
            xml.contains("android:usesCleartextTraffic=\"false\"")
        )
    }

    @Test
    fun `manifest declares allowBackup false`() {
        val xml = readManifestXml()
        assertTrue(
            "AndroidManifest must declare android:allowBackup=\"false\"",
            xml.contains("android:allowBackup=\"false\"")
        )
    }
}
