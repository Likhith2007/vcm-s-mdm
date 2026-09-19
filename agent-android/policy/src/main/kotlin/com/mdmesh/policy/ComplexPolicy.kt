package com.mdmesh.policy

import kotlinx.serialization.json.JsonObject

/**
 * A policy with a complex payload that carries context (e.g., package name, time schedule).
 *
 * Complex policies are routed by [com.mdmesh.core.command.handlers.ComplexPolicyHandler]:
 * the handler decodes the full payload and calls [apply] with the parsed JsonObject.
 * This differs from [TogglePolicy], which only carries a boolean value and is routed
 * generically by [com.mdmesh.core.command.handlers.PolicyApplyHandler].
 *
 * Example: `{ "policy": "appBlock", "packageName": "com.example.x", "value": true }`
 */
interface ComplexPolicy : PolicyStrategy {

    /**
     * Apply the policy with the given payload. The payload is a pre-validated JsonObject;
     * the handler has already ensured "policy" and "value" fields exist. Returns a [PolicyOutcome];
     * never throws.
     */
    suspend fun apply(payload: JsonObject): PolicyOutcome
}
