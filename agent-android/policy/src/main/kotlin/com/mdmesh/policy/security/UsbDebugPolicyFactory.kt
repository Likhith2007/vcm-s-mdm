package com.mdmesh.policy.security

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [UsbDebugPolicy] strategy for the current device. Currently there is one
 * implementation (API 18+), but the factory shape is kept for uniformity.
 *
 * Returns `null` when no strategy is supported (e.g. not Device Owner), in which case
 * the `usbDebug` capability is never advertised.
 */
object UsbDebugPolicyFactory {

    fun create(handle: DpmHandle): UsbDebugPolicy? =
        listOf(UsbDebugRestrictionPolicy(handle)).firstOrNull { it.isSupported() }
}
