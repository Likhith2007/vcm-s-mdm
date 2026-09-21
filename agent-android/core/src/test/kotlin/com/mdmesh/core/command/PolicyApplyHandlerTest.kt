package com.mdmesh.core.command

import com.mdmesh.core.command.handlers.PolicyApplyHandler
import com.mdmesh.policy.ComplexPolicy
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers both routes [PolicyApplyHandler] dispatches: simple toggles (wifi, camera, …) and
 * complex, context-aware policies (appBlock, appHide, …). These used to be two separate
 * CommandHandlers that both claimed type = "policy.apply" — a real bug, since the dispatcher
 * keys handlers by type in a plain map, so only one of them ever actually ran. Merged into one
 * handler/test file so that regression can't reappear silently.
 */
class PolicyApplyHandlerTest {

    private class FakeToggle(
        override val capabilityKey: String,
        private val outcome: PolicyOutcome,
    ) : TogglePolicy {
        var lastEnabled: Boolean? = null
        override fun isSupported(): Boolean = true
        override fun setEnabled(enabled: Boolean): PolicyOutcome {
            lastEnabled = enabled
            return outcome
        }
    }

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

    private fun handler(
        toggles: Map<String, TogglePolicy> = emptyMap(),
        complexPolicies: Map<String, ComplexPolicy> = emptyMap(),
    ) = PolicyApplyHandler(toggles, complexPolicies)

    private fun command(payload: JsonObject?) = CommandEnvelope(
        commandId = "c1",
        issuedAt = "2026-01-01T00:00:00Z",
        type = "policy.apply",
        payload = payload,
    )

    private fun togglePayload(policy: String, value: Boolean) = buildJsonObject {
        put("policy", policy)
        put("value", value)
    }

    private fun appBlockPayload(packageName: String, block: Boolean) = buildJsonObject {
        put("policy", "appBlock")
        put("packageName", packageName)
        put("value", block)
    }

    // --- toggle policies ---

    @Test
    fun `applies a known toggle policy and reports done`() = runTest {
        val wifi = FakeToggle("wifi", PolicyOutcome.Applied)
        val result = handler(toggles = mapOf("wifi" to wifi)).handle(command(togglePayload("wifi", false)))

        assertEquals(CommandStatus.DONE, result.status)
        assertEquals(false, wifi.lastEnabled)
    }

    @Test
    fun `reports unsupported for a toggle policy with no registered strategy`() = runTest {
        val result = handler().handle(command(togglePayload("camera", true)))
        assertEquals(CommandStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun `surfaces a toggle strategy failure as failed`() = runTest {
        val wifi = FakeToggle("wifi", PolicyOutcome.Failed("dpm blew up"))
        val result = handler(toggles = mapOf("wifi" to wifi)).handle(command(togglePayload("wifi", true)))
        assertEquals(CommandStatus.FAILED, result.status)
    }

    // --- complex policies ---

    @Test
    fun `routes to correct complex policy and reports done`() = runTest {
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Applied)
        val payload = appBlockPayload("com.example.app", true)

        val result = handler(complexPolicies = mapOf("appBlock" to appBlock)).handle(command(payload))

        assertEquals(CommandStatus.DONE, result.status)
        assertEquals(payload, appBlock.lastPayload)
    }

    @Test
    fun `surfaces a complex policy failure as failed`() = runTest {
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Failed("DPM error"))
        val payload = appBlockPayload("com.example.app", true)

        val result = handler(complexPolicies = mapOf("appBlock" to appBlock)).handle(command(payload))

        assertEquals(CommandStatus.FAILED, result.status)
    }

    @Test
    fun `passes full payload to complex policy for context-aware processing`() = runTest {
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Applied)
        val payload = buildJsonObject {
            put("policy", "appBlock")
            put("packageName", "com.example.app")
            put("value", true)
            put("description", "Block this app")
        }

        handler(complexPolicies = mapOf("appBlock" to appBlock)).handle(command(payload))

        assertEquals(payload, appBlock.lastPayload)
    }

    @Test
    fun `a complex policy key never falls through to the toggle registry`() = runTest {
        // Regression guard for the original bug: even if "appBlock" somehow also existed in the
        // toggle map, the complex registry must win (checked first) since only it understands
        // packageName-scoped payloads.
        val wifi = FakeToggle("appBlock", PolicyOutcome.Applied)
        val appBlock = FakeComplexPolicy("appBlock", PolicyOutcome.Applied)
        val payload = appBlockPayload("com.example.app", true)

        handler(toggles = mapOf("appBlock" to wifi), complexPolicies = mapOf("appBlock" to appBlock))
            .handle(command(payload))

        assertEquals(payload, appBlock.lastPayload)
        assertEquals(null, wifi.lastEnabled)
    }

    // --- shared error paths ---

    @Test
    fun `reports unsupported for a policy with no registered strategy anywhere`() = runTest {
        val result = handler().handle(command(appBlockPayload("com.example.app", true)))
        assertEquals(CommandStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun `reports failed when the payload is missing`() = runTest {
        val result = handler().handle(command(payload = null))
        assertEquals(CommandStatus.FAILED, result.status)
    }

    @Test
    fun `reports failed when policy key is missing from payload`() = runTest {
        val payload = buildJsonObject { put("packageName", "com.example.app") }
        val result = handler().handle(command(payload))
        assertEquals(CommandStatus.FAILED, result.status)
    }
}
