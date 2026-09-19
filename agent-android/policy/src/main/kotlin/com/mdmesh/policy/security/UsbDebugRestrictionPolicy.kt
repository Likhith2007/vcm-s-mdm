package com.mdmesh.policy.security

import android.os.Build
import android.os.UserManager
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * USB debugging restriction strategy for API 18+ (JELLY_BEAN_MR2).
 * Uses the user restriction [UserManager.DISALLOW_DEBUGGING_FEATURES].
 *
 * `setEnabled(false)` adds the restriction (blocking USB debugging);
 * `setEnabled(true)` clears it (allowing USB debugging).
 */
internal class UsbDebugRestrictionPolicy(
    private val handle: DpmHandle,
) : UsbDebugPolicy {

    override val capabilityKey: String = UsbDebugPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        // Feature ON (enabled=true) => restriction cleared; OFF => restriction added.
        if (enabled) {
            handle.dpm.clearUserRestriction(handle.admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        } else {
            handle.dpm.addUserRestriction(handle.admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "usbDebug setEnabled failed") }
}
