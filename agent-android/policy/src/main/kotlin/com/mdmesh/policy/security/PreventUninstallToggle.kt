package com.mdmesh.policy.security

import android.os.Build
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Applies [android.app.admin.DevicePolicyManager.setUninstallBlocked] across every
 * currently-installed package. A handful of protected system packages refusing (the same class
 * of OS-level restriction [com.mdmesh.policy.app.AppBlockHidePolicy]'s `setApplicationHidden`
 * hits) doesn't invalidate the whole operation — only report [PolicyOutcome.Failed] if not one
 * package could be locked.
 *
 * `PackageEventReceiver` (agent module) applies the SAME restriction to any newly-installed
 * package while this is active, by checking [PreventUninstallPolicy.isCurrentlyEnabled] — the
 * agent's own package's uninstall-blocked flag, which DPM already persists, rather than a
 * separately maintained store that could drift out of sync with it.
 */
internal class PreventUninstallToggle(
    private val handle: DpmHandle,
) : PreventUninstallPolicy {

    override val capabilityKey: String = PreventUninstallPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        val packages = handle.context.packageManager.getInstalledApplications(0).map { it.packageName }
        var succeeded = 0
        for (pkg in packages) {
            runCatching { handle.dpm.setUninstallBlocked(handle.admin, pkg, enabled) }
                .onSuccess { succeeded++ }
        }
        if (succeeded == 0 && packages.isNotEmpty()) {
            return PolicyOutcome.Failed("setUninstallBlocked failed for every installed package")
        }
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "preventUninstall setEnabled failed") }
}
