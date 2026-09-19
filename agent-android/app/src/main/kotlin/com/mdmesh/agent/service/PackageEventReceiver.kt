package com.mdmesh.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mdmesh.core.install.PendingInstallGate
import com.mdmesh.core.sync.CheckInWorker
import com.mdmesh.core.telemetry.EventLog
import com.mdmesh.proto.EventType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Records app install/uninstall events into the telemetry [EventLog]. A fresh install this
 * device's own user started (Play Store / sideload) — never one the agent itself pushed via
 * `app.install` — gets gated via [PendingInstallGate], and an out-of-band check-in is forced so
 * the pending approval reaches the console within seconds instead of waiting for the next
 * periodic cycle.
 *
 * Best-effort only: this broadcast can be silently dropped by OEM background-execution
 * restrictions when the app isn't exempt from battery optimization (confirmed on a real
 * Realme/ColorOS device — logcat showed `dumpsys activity broadcasts` recording "skipped by
 * policy at enqueue: Background execution not allowed"). [com.mdmesh.core.install.
 * InstalledPackagePoller], run every check-in, is the reliable fallback that doesn't depend on
 * this broadcast firing at all.
 */
@AndroidEntryPoint
class PackageEventReceiver : BroadcastReceiver() {

    @Inject lateinit var eventLog: EventLog
    @Inject lateinit var gate: PendingInstallGate

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        // ACTION_PACKAGE_ADDED fires on update too; EXTRA_REPLACING distinguishes a fresh install.
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (replacing) return
                runCatching { eventLog.record(EventType.APP_INSTALLED, pkg) }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        gate.handleDetectedInstall(pkg)
                        CheckInWorker.scheduleNow(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "gate handling failed for $pkg", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_PACKAGE_REMOVED ->
                if (!replacing) runCatching { eventLog.record(EventType.APP_UNINSTALLED, pkg) }
        }
    }

    private companion object {
        const val TAG = "PackageEventReceiver"
    }
}
