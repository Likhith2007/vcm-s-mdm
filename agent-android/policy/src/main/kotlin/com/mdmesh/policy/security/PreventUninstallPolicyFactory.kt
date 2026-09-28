package com.mdmesh.policy.security

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [PreventUninstallPolicy] strategy for the current device. There is only one (the
 * `setUninstallBlocked` API has been stable since API 24); the factory shape is kept for
 * uniformity with every other policy surface.
 */
object PreventUninstallPolicyFactory {

    fun create(handle: DpmHandle): PreventUninstallPolicy? =
        listOf(PreventUninstallToggle(handle)).firstOrNull { it.isSupported() }
}
