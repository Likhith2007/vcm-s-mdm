package com.mdmesh.policy.app

import android.os.Build
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * App blocking strategy for API 24+.
 * Uses [android.app.admin.DevicePolicyManager.setApplicationHidden] to hide/block apps, falling
 * back to [android.app.admin.DevicePolicyManager.setPackagesSuspended] when the OS refuses to
 * hide a package — some OEMs protect their own bundled system apps (an app store, dialer, etc.)
 * from being hidden even by the active Device Owner, but still allow suspending them (a separate
 * enforcement: the app stays visible/installed but can't be opened — tapping it shows a system
 * "this app is suspended" dialog instead). Trying both and succeeding if either works maximizes
 * how many packages this can actually block, which is the whole point of "block any app".
 *
 * Payload expected: `{ "policy": "appBlock", "packageName": "com.example.x", "value": true/false }`
 *
 * `value=true` → hide/suspend app (block)
 * `value=false` → unhide/unsuspend app (allow)
 */
internal class AppBlockHidePolicy(
    private val handle: DpmHandle,
    private val selfInitiatedTracker: SelfInitiatedTracker = SelfInitiatedTracker {},
) : AppBlockPolicy {

    override val capabilityKey: String = AppBlockPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override suspend fun apply(payload: JsonObject): PolicyOutcome = runCatching {
        val packageName = payload["packageName"]?.jsonPrimitive?.content
            ?: return PolicyOutcome.Failed("appBlock: missing packageName")
        val block = payload["value"]?.jsonPrimitive?.content?.toBoolean()
            ?: return PolicyOutcome.Failed("appBlock: missing value")

        // Un-hiding re-fires ACTION_PACKAGE_ADDED for the package (Android platform behavior) —
        // mark it first so the non-MDM-install gate doesn't mistake this for a fresh sideload and
        // immediately re-suspend the very app we just re-enabled. See SelfInitiatedTracker.
        if (!block) {
            selfInitiatedTracker.markSelfInitiated(packageName)
        }

        // Both return value(s), not exceptions, when the OS refuses -- neither can be assumed to
        // succeed. Try both; either one actually blocking the app is a win.
        val hidden = handle.dpm.setApplicationHidden(handle.admin, packageName, block)
        val notSuspended = runCatching {
            handle.dpm.setPackagesSuspended(handle.admin, arrayOf(packageName), block)
        }.getOrElse { arrayOf(packageName) } // treat a thrown exception as "failed to suspend"
        val suspended = packageName !in notSuspended

        if (!hidden && !suspended) {
            return PolicyOutcome.Failed(
                "Both hide and suspend refused for $packageName (protected system app?)",
            )
        }
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "appBlock apply failed") }
}
