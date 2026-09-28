package com.mdmesh.policy.security

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Blocks the device user from uninstalling ANY currently-installed app, via
 * [android.app.admin.DevicePolicyManager.setUninstallBlocked] applied across every package on
 * the device — not just one, unlike [com.mdmesh.policy.app.AppBlockPolicy] which targets a
 * single `packageName` from its payload.
 *
 * `setEnabled(true)` locks every app down; `setEnabled(false)` releases them all.
 */
interface PreventUninstallPolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "preventUninstall"

        /**
         * Whether this toggle is currently active, derived from the OS's own persisted flag on
         * the agent's own package (the [PreventUninstallToggle.setEnabled] sweep always includes
         * it) — no separate store to keep in sync. Public (unlike the `internal` implementation
         * class) so callers outside `:policy`, like `PackageEventReceiver`, can check it too —
         * needed so a package installed AFTER the toggle was switched on still inherits it.
         */
        fun isCurrentlyEnabled(handle: DpmHandle): Boolean = runCatching {
            handle.dpm.isUninstallBlocked(handle.admin, handle.context.packageName)
        }.getOrDefault(false)
    }
}
