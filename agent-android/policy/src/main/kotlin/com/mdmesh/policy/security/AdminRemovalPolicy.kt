package com.mdmesh.policy.security

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy

/**
 * Capability-abstracted admin removal safeguard (detection-based).
 *
 * `setEnabled(true)` baseline (admin is active, no restriction);
 * `setEnabled(false)` is not a perfect prevention (DO can't prevent admin uninstall directly).
 *
 * Primary mechanism: telemetry collection (AdminRemovalCollector) reports admin status
 * on every check-in; server detects uninstall via telemetry change and can respond (wipe, lock, alert).
 *
 * The concrete strategy is selected by [AdminRemovalPolicyFactory].
 */
interface AdminRemovalPolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "adminRemoval"
    }
}
