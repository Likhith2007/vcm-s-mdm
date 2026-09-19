package com.mdmesh.policy.security

import android.os.Build
import android.os.UserManager
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Factory reset restriction strategy for API 18+ (JELLY_BEAN_MR2).
 * Uses the user restriction [UserManager.DISALLOW_FACTORY_RESET].
 *
 * `setEnabled(false)` adds the restriction (preventing factory reset);
 * `setEnabled(true)` clears it (allowing factory reset).
 */
internal class FactoryResetRestrictionPolicy(
    private val handle: DpmHandle,
) : FactoryResetPolicy {

    override val capabilityKey: String = FactoryResetPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        // Feature ON (enabled=true) => restriction cleared; OFF => restriction added.
        if (enabled) {
            handle.dpm.clearUserRestriction(handle.admin, UserManager.DISALLOW_FACTORY_RESET)
        } else {
            handle.dpm.addUserRestriction(handle.admin, UserManager.DISALLOW_FACTORY_RESET)
        }
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "factoryReset setEnabled failed") }
}
