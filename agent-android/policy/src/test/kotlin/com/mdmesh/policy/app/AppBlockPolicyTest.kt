package com.mdmesh.policy.app

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class AppBlockPolicyTest {

    @Test
    fun `has appBlock capability key`() {
        assertEquals("appBlock", AppBlockPolicy.CAPABILITY_KEY)
    }

    @Test
    fun `payload contains package name and value`() {
        val payload = buildJsonObject {
            put("packageName", "com.example.app")
            put("value", true)
        }

        assertEquals("com.example.app", payload["packageName"]?.toString()?.trim('"'))
        assertEquals("true", payload["value"]?.toString())
    }
}
