package com.mdmesh.core.install

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [InstallRequest.apkParts] normalization — the contract the multi-file
 * install session relies on (single-APK requests behave as a one-part list; split bundles pass
 * their parts through in order, ignoring the single-URL fields).
 */
class InstallRequestPartsTest {

    @Test
    fun `single url normalizes to one part`() {
        val req = InstallRequest(url = "https://h/app.apk", packageName = "com.x", sha256 = "ab")
        val parts = req.apkParts
        assertEquals(1, parts.size)
        assertEquals("https://h/app.apk", parts[0].url)
        assertEquals("ab", parts[0].sha256)
    }

    @Test
    fun `local path normalizes to one part`() {
        val req = InstallRequest(localPath = "/data/app.apk", packageName = "com.x")
        val parts = req.apkParts
        assertEquals(1, parts.size)
        assertEquals("/data/app.apk", parts[0].localPath)
    }

    @Test
    fun `parts take precedence and preserve order`() {
        val req = InstallRequest(
            url = "https://h/ignored.apk",
            packageName = "com.x",
            parts = listOf(
                ApkPart(url = "https://h/base.apk", sha256 = "b0"),
                ApkPart(url = "https://h/split_arm64.apk", sha256 = "a1"),
                ApkPart(url = "https://h/split_en.apk"),
            ),
        )
        val parts = req.apkParts
        assertEquals(3, parts.size)
        assertEquals("https://h/base.apk", parts[0].url)
        assertEquals("https://h/split_arm64.apk", parts[1].url)
        assertEquals("https://h/split_en.apk", parts[2].url)
        assertEquals(null, parts[2].sha256)
    }
}
