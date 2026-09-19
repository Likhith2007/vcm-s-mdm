package com.mdmesh.core.install

import com.mdmesh.core.telemetry.EventLog
import com.mdmesh.proto.EventType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared "a non-MDM install was detected" handling, called from both the (best-effort, can be
 * silently dropped by OEM background-execution restrictions — confirmed on a real Realme/ColorOS
 * device) [com.mdmesh.agent.service.PackageEventReceiver] broadcast path and the (reliable,
 * check-in-driven) [InstalledPackagePoller] fallback, so the two never diverge in behaviour.
 */
@Singleton
class PendingInstallGate @Inject constructor(
    private val installManager: InstallManager,
    private val eventLog: EventLog,
    private val selfInitiated: SelfInitiatedInstalls,
) {

    /** Suspends [packageName] and reports it for admin approval — unless MDM itself is mid-installing it. */
    fun handleDetectedInstall(packageName: String) {
        if (selfInitiated.consume(packageName)) return // MDM pushed this itself — never gate it.
        installManager.setSuspended(packageName, true)
        runCatching { eventLog.record(EventType.APP_INSTALL_PENDING, packageName) }
    }
}
