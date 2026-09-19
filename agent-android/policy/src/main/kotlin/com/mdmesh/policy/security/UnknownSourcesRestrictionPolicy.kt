package com.mdmesh.policy.security

import android.os.Build
import android.os.UserManager
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Unknown sources (side-loading) restriction strategy for API 28+ (PIE).
 * Uses the user restriction [UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES].
 *
 * `setEnabled(false)` adds the restriction (blocking installation from unknown sources);
 * `setEnabled(true)` clears it (allowing installation from unknown sources).
 *
 * Note: On Android 12+, this is install-time permission; pre-gated by Play Protect.
 */
internal class UnknownSourcesRestrictionPolicy(
    private val handle: DpmHandle,
) : UnknownSourcesPolicy {

    override val capabilityKey: String = UnknownSourcesPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        // Feature ON (enabled=true) => restriction cleared; OFF => restriction added.
        if (enabled) {
            handle.dpm.clearUserRestriction(handle.admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        } else {
            handle.dpm.addUserRestriction(handle.admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        }
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "unknownSources setEnabled failed") }
}
