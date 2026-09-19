package com.mdmesh.core.command.handlers

import com.mdmesh.policy.ComplexPolicy
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ComplexPolicyHandler]:
 * - Routes complex policy.apply commands to the correct policy
 * - Extracts JSON payload and policy key
 * - Reports UNSUPPORTED for unknown policies
 * - Reports FAILED when DPM operations fail
 * - Reports DONE when policy succeeds
 */
class ComplexPolicyHandlerTest {

    private class FakeComplexPolicy(
        override val capabilityKey: String,
        private val outcome: PolicyOutcome,
    ) : ComplexPolicy {
        var lastPayload: JsonObject? = null
        override fun isSupported(): Boolean = true

        override suspend fun apply(payload: JsonObject): PolicyOutcome {
            lastPayload = payload
            return outcome
        }
    }

    private fun command(payload: JsonObject?) = CommandEnvelope(
        commandId = "c1",
        issuedAt = "2026-01-01T00:00:00Z",
        type = "policy.apply",
        payload = payload,
    )

    private fun appBlockPayload(packageName: String, block: Boolean) = buildJsonObject {
        put("policy", "appBlock")
        put("packageName", packageName)
        put("value", block)
    }

    @Test
    fun `routes to correct complex policy and reports done`() = runTest {
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Applied)
        val handler = ComplexPolicyHandler(mapOf("appBlock" to appBlock))
        val payload = appBlockPayload("com.example.app", true)

        val result = handler.handle(command(payload))

        assertEquals(CommandStatus.DONE, result.status)
        assertEquals(payload, appBlock.lastPayload)
    }

    @Test
    fun `reports unsupported for unknown policy`() = runTest {
        val handler = ComplexPolicyHandler(emptyMap())
        val payload = appBlockPayload("com.example.app", true)

        val result = handler.handle(command(payload))

        assertEquals(CommandStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun `reports failed when payload is missing`() = runTest {
        val handler = ComplexPolicyHandler(emptyMap())
        val result = handler.handle(command(payload = null))

        assertEquals(CommandStatus.FAILED, result.status)
    }

    @Test
    fun `reports failed when policy key is missing from payload`() = runTest {
        val handler = ComplexPolicyHandler(emptyMap())
        val payload = buildJsonObject {
            put("packageName", "com.example.app")
        }

        val result = handler.handle(command(payload))

        assertEquals(CommandStatus.FAILED, result.status)
    }

    @Test
    fun `surfaces a complex policy failure as failed`() = runTest {
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Failed("DPM error"))
        val handler = ComplexPolicyHandler(mapOf("appBlock" to appBlock))
        val payload = appBlockPayload("com.example.app", true)

        val result = handler.handle(command(payload))

        assertEquals(CommandStatus.FAILED, result.status)
    }

    @Test
    fun `passes full payload to complex policy for context-aware processing`() = runTest {
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Applied)
        val handler = ComplexPolicyHandler(mapOf("appBlock" to appBlock))
        val payload = buildJsonObject {
            put("policy", "appBlock")
            put("packageName", "com.example.app")
            put("value", true)
            put("description", "Block this app")
        }

        handler.handle(command(payload))

        // Verify the entire payload was passed, not just extracted fields
        assertEquals(payload, appBlock.lastPayload)
    }
}
