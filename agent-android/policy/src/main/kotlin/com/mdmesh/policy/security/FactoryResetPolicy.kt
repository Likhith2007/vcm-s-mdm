package com.mdmesh.policy.security

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy

/**
 * Capability-abstracted factory reset protection control.
 *
 * `setEnabled(true)` allows factory reset; `setEnabled(false)` prevents it.
 * Backed by the user restriction [android.os.UserManager.DISALLOW_FACTORY_RESET].
 * The concrete strategy is selected by [FactoryResetPolicyFactory].
 */
interface FactoryResetPolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "factoryReset"
    }
}
