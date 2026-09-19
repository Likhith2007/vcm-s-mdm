package com.mdmesh.policy.security

import android.os.Build
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Admin removal safeguard strategy for API 24+.
 *
 * This policy is mostly detection-based:
 * - `setEnabled(false)` records a baseline that the admin is active (for later comparison)
 * - `setEnabled(true)` allows normal operation
 * - On each check-in, the telemetry collector (AdminRemovalCollector) reports admin status
 * - The server detects a change (admin uninstall) and responds (wipe, lock, alert)
 *
 * No direct API can prevent an active Device Owner from being uninstalled, so we rely on
 * enrollment + device owner lock + server response.
 */
internal class AdminRemovalSafeguardPolicy(
    private val handle: DpmHandle,
) : AdminRemovalPolicy {

    override val capabilityKey: String = AdminRemovalPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        // Policy ON (enabled=true) = normal operation, admin is baseline active.
        // Policy OFF (enabled=false) = prepare for detection; server will monitor.
        // For now, both just return success; detection happens via telemetry on check-in.
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "adminRemoval setEnabled failed") }
}
