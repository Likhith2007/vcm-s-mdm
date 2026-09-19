package com.mdmesh.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Requests the "ignore battery optimizations" system dialog once, right at enrollment — before
 * kiosk mode can ever be active (kiosk is a remote-pushed command, only reachable after enrollment
 * completes), so it can never interrupt a running kiosk session. Without this exemption, some OEMs
 * (confirmed: Realme/ColorOS) silently drop `PACKAGE_ADDED` broadcast delivery under background-
 * execution restrictions — see [com.mdmesh.agent.service.PackageEventReceiver]. A no-op if already
 * granted, so calling this on every enrollment attempt is safe.
 */
object BatteryOptimizationExemption {
    fun requestIfNeeded(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
