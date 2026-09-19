package com.mdmesh.policy.app

import com.mdmesh.policy.ComplexPolicy
import com.mdmesh.policy.PolicyOutcome
import kotlinx.serialization.json.JsonObject

/**
 * Capability-abstracted app hiding (UI suppression).
 *
 * `value=true` hides the app icon (but doesn't prevent launch if already running);
 * `value=false` shows the app icon.
 *
 * Payload must include `packageName` (the app to hide) and `value` (boolean).
 * The concrete strategy is selected by [AppHidePolicyFactory].
 *
 * Note: This is separate from [AppBlockPolicy] for API clarity, though both use setApplicationHidden.
 */
interface AppHidePolicy : ComplexPolicy {

    override suspend fun apply(payload: JsonObject): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "appHide"
    }
}
