package com.mdmesh.core.sync

/**
 * Runs the reliable, check-in-driven fallback for detecting non-MDM app installs (see
 * [com.mdmesh.core.install.InstalledPackagePoller]). Android-free by design (a `fun interface`) so
 * [CheckInCoordinator] stays testable — mirrors [HardwareIdSource]'s pattern.
 */
fun interface PendingInstallScanner {
    fun scan()
}
