package com.mdmesh.policy.app

import com.mdmesh.policy.ComplexPolicy
import com.mdmesh.policy.PolicyOutcome
import kotlinx.serialization.json.JsonObject

/**
 * Capability-abstracted app blocking (runtime prevention).
 *
 * `value=true` blocks the app (hides it, prevents launch);
 * `value=false` allows the app (unhides it).
 *
 * Payload must include `packageName` (the app to block) and `value` (boolean).
 * The concrete strategy is selected by [AppBlockPolicyFactory].
 */
interface AppBlockPolicy : ComplexPolicy {

    override suspend fun apply(payload: JsonObject): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "appBlock"
    }
}
