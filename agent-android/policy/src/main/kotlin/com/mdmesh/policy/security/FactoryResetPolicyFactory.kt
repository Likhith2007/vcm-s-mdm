package com.mdmesh.policy.security

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [FactoryResetPolicy] strategy for the current device. Currently there is one
 * implementation (API 18+), but the factory shape is kept for uniformity.
 *
 * Returns `null` when no strategy is supported (e.g. not Device Owner), in which case
 * the `factoryReset` capability is never advertised.
 */
object FactoryResetPolicyFactory {

    fun create(handle: DpmHandle): FactoryResetPolicy? =
        listOf(FactoryResetRestrictionPolicy(handle)).firstOrNull { it.isSupported() }
}
