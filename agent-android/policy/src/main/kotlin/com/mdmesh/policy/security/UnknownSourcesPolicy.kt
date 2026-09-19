package com.mdmesh.policy.security

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy

/**
 * Capability-abstracted unknown sources (side-loading) control.
 *
 * `setEnabled(true)` allows app installation from unknown sources; `setEnabled(false)` blocks it.
 * Backed by the user restriction [android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES].
 * The concrete strategy is selected by [UnknownSourcesPolicyFactory].
 */
interface UnknownSourcesPolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "unknownSources"
    }
}
