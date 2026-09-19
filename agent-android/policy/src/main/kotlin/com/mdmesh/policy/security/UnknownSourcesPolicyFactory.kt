package com.mdmesh.policy.security

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [UnknownSourcesPolicy] strategy for the current device. Currently there is one
 * implementation (API 28+), but the factory shape is kept for uniformity.
 *
 * Returns `null` when no strategy is supported (e.g. not Device Owner or API < 28), in which case
 * the `unknownSources` capability is never advertised.
 */
object UnknownSourcesPolicyFactory {

    fun create(handle: DpmHandle): UnknownSourcesPolicy? =
        listOf(UnknownSourcesRestrictionPolicy(handle)).firstOrNull { it.isSupported() }
}
