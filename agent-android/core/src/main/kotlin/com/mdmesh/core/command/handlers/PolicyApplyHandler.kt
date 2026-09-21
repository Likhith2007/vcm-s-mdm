package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.policy.ComplexPolicy
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `policy.apply` — applies a policy, either a simple on/off toggle (`{ "policy": "wifi",
 * "value": false }`) or a complex, context-aware one (`{ "policy": "appBlock",
 * "packageName": "com.example.x", "value": true }`; see `proto/registry.md`).
 *
 * MUST be the only [CommandHandler] registered for `type = "policy.apply"`: the dispatcher
 * keys handlers by `type` in a plain map (`handlers.associateBy { it.type }`), so a second
 * handler sharing this type doesn't run alongside this one — it silently replaces it. This
 * used to be split into two handlers (toggle vs. complex) that both claimed `"policy.apply"`;
 * whichever won that map collision (empirically, this one) permanently shadowed the other, so
 * every complex policy — appBlock included — reported "not supported" no matter what the
 * device actually had registered. Found live: a Group Policy's scheduled app block never
 * applied on-device despite the command being delivered and the device advertising the
 * `appBlock` capability.
 *
 * Routing is fully data-driven: the policy key is looked up in [complexPolicies] first (a
 * toggle key like "wifi" is never present there, so this never shadows a real toggle), then
 * [toggles]. No per-policy `when` — adding a policy means registering its strategy, not
 * editing here. Unknown / unsupported keys degrade to `unsupported`, mirroring the
 * open-registry contract.
 */
class PolicyApplyHandler(
    private val toggles: Map<String, TogglePolicy>,
    private val complexPolicies: Map<String, ComplexPolicy>,
) : CommandHandler {

    override val type: String = "policy.apply"

    @Serializable
    private data class Payload(
        val policy: String,
        @SerialName("value") val enabled: Boolean,
    )

    override suspend fun handle(command: CommandEnvelope): CommandResult {
        val payload = command.payload
            ?: return CommandResults.failed(command, "policy.apply requires a payload")
        val jsonPayload = payload as? JsonObject
            ?: return CommandResults.failed(command, "policy.apply requires a JSON object payload")
        val policyKey = jsonPayload["policy"]?.let { it.toString().trim('"') }
            ?: return CommandResults.failed(command, "policy.apply: missing policy key")

        complexPolicies[policyKey]?.let { complex ->
            return when (val outcome = complex.apply(jsonPayload)) {
                PolicyOutcome.Applied -> CommandResults.done(command)
                PolicyOutcome.Unsupported -> CommandResults.unsupported(command)
                is PolicyOutcome.Failed -> CommandResults.failed(command, outcome.reason)
            }
        }

        val toggle = toggles[policyKey]
            ?: return CommandResults.unsupported(command, "policy not supported: $policyKey")
        val parsed = runCatching {
            ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), payload)
        }.getOrElse { return CommandResults.failed(command, "bad payload: ${it.message}") }

        return when (val outcome = toggle.setEnabled(parsed.enabled)) {
            PolicyOutcome.Applied -> CommandResults.done(command)
            PolicyOutcome.Unsupported -> CommandResults.unsupported(command)
            is PolicyOutcome.Failed -> CommandResults.failed(command, outcome.reason)
        }
    }
}
