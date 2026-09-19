package com.mdmesh.policy.app

import android.os.Build
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * App hiding strategy for API 24+.
 * Uses [android.app.admin.DevicePolicyManager.setApplicationHidden] to hide/show app icons.
 *
 * Payload expected: `{ "policy": "appHide", "packageName": "com.example.x", "value": true/false }`
 *
 * `value=true` → hide icon
 * `value=false` → show icon
 *
 * Note: Hiding does not prevent launch if the app is already running; use appBlock for full prevention.
 */
internal class AppHiddenPolicy(
    private val handle: DpmHandle,
    private val selfInitiatedTracker: SelfInitiatedTracker = SelfInitiatedTracker {},
) : AppHidePolicy {

    override val capabilityKey: String = AppHidePolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override suspend fun apply(payload: JsonObject): PolicyOutcome = runCatching {
        val packageName = payload["packageName"]?.jsonPrimitive?.content
            ?: return PolicyOutcome.Failed("appHide: missing packageName")
        val hide = payload["value"]?.jsonPrimitive?.content?.toBoolean()
            ?: return PolicyOutcome.Failed("appHide: missing value")

        // Un-hiding re-fires ACTION_PACKAGE_ADDED for the package — see SelfInitiatedTracker.
        if (!hide) {
            selfInitiatedTracker.markSelfInitiated(packageName)
        }
        // value=true => hide; value=false => show.
        handle.dpm.setApplicationHidden(handle.admin, packageName, hide)
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "appHide apply failed") }
}
