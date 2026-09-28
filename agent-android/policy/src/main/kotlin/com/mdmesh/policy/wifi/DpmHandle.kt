package com.mdmesh.policy.wifi

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Thin holder bundling the [DevicePolicyManager] with this agent's admin
 * [ComponentName]. Passed to every strategy so that the raw DPM surface stays
 * confined to the strategy implementations and never leaks into feature code.
 *
 * [context] is here only for strategies that need broader package-manager access than DPM
 * itself provides (e.g. enumerating every installed package for a device-wide policy) — most
 * strategies only ever touch [dpm]/[admin].
 */
data class DpmHandle(
    val dpm: DevicePolicyManager,
    val admin: ComponentName,
    val context: Context,
)
