package com.mdmesh.policy.app

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [AppHidePolicy] strategy for the current device. Currently there is one
 * implementation (API 24+), but the factory shape is kept for uniformity.
 *
 * Returns `null` when no strategy is supported (e.g. not Device Owner or API < 24), in which case
 * the `appHide` capability is never advertised.
 */
object AppHidePolicyFactory {

    fun create(handle: DpmHandle, selfInitiatedTracker: SelfInitiatedTracker = SelfInitiatedTracker {}): AppHidePolicy? =
        listOf(AppHiddenPolicy(handle, selfInitiatedTracker)).firstOrNull { it.isSupported() }
}
