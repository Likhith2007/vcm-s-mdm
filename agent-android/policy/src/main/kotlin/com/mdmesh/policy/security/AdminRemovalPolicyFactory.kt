package com.mdmesh.policy.security

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [AdminRemovalPolicy] strategy for the current device. Currently there is one
 * implementation (API 24+), but the factory shape is kept for uniformity.
 *
 * Returns `null` when no strategy is supported (e.g. not Device Owner), in which case
 * the `adminRemoval` capability is never advertised.
 */
object AdminRemovalPolicyFactory {

    fun create(handle: DpmHandle): AdminRemovalPolicy? =
        listOf(AdminRemovalSafeguardPolicy(handle)).firstOrNull { it.isSupported() }
}
