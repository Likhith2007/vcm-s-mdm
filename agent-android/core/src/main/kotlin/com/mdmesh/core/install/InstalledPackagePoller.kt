package com.mdmesh.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reliable fallback for detecting non-MDM installs: diffs the currently-installed package set
 * against [KnownPackagesStore]'s baseline on every check-in, since the broadcast-based
 * [com.mdmesh.agent.service.PackageEventReceiver] path alone isn't guaranteed — this project
 * confirmed on a real device that some OEMs (ColorOS) silently drop `PACKAGE_ADDED` delivery to a
 * manifest receiver under background-execution restrictions unless the app holds a battery-
 * optimization exemption. Detection here is as fast as the next check-in (near-instant when the
 * app is foregrounded or the exemption is granted; otherwise bounded by the periodic cycle).
 */
@Singleton
class InstalledPackagePoller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knownPackages: KnownPackagesStore,
    private val gate: PendingInstallGate,
) {

    fun poll() {
        val current = installedPackageNames()
        val known = knownPackages.get()
        if (known == null) {
            // First run: seed the baseline without retroactively flagging pre-existing apps.
            knownPackages.set(current)
            return
        }
        for (pkg in current - known) {
            gate.handleDetectedInstall(pkg)
        }
        knownPackages.set(current)
    }

    private fun installedPackageNames(): Set<String> =
        context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
}
