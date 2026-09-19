package com.mdmesh.policy.app

/**
 * Lets an app-visibility policy flag a package as "this change was MDM-initiated, not the user
 * installing something" right before making a DPM call that will provoke a fresh
 * `ACTION_PACKAGE_ADDED` broadcast for it.
 *
 * `DevicePolicyManager.setApplicationHidden(admin, pkg, false)` (un-hiding a previously-hidden
 * app) is documented Android platform behavior to re-fire `ACTION_PACKAGE_ADDED` for that package
 * — the same broadcast a genuine fresh install/sideload produces. Without this marker,
 * `PackageEventReceiver`'s non-MDM-install gate (see `core`'s `PendingInstallGate`) would
 * misinterpret a Group Policy schedule's automatic "block window ended, re-enable the app" as a
 * suspicious new install and immediately re-suspend it — exactly undoing the automatic
 * re-enable and dumping it back into the pending-approvals queue.
 *
 * `policy` has no dependency on `core` (wrong direction for this module graph), so this interface
 * — not `core`'s concrete `SelfInitiatedInstalls` — is what `AppBlockHidePolicy`/`AppHiddenPolicy`
 * depend on; `:app`'s DI wiring supplies the real implementation.
 */
fun interface SelfInitiatedTracker {
    fun markSelfInitiated(packageName: String)
}
