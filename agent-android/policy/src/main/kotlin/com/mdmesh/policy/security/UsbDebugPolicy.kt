package com.mdmesh.policy.security

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy

/**
 * Capability-abstracted USB debugging control.
 *
 * `setEnabled(true)` re-enables USB debugging; `setEnabled(false)` disables it.
 * Backed by the user restriction [android.os.UserManager.DISALLOW_DEBUGGING_FEATURES].
 * The concrete strategy is selected by [UsbDebugPolicyFactory].
 */
interface UsbDebugPolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "usbDebug"
    }
}
