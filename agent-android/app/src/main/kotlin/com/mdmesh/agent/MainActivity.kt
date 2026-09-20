package com.mdmesh.agent

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.AlertDialog
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.integration.android.IntentIntegrator
import com.mdmesh.agent.admin.AdminReceiver
import com.mdmesh.agent.service.CheckInService
import com.mdmesh.core.config.ServerConfigStore
import com.mdmesh.core.store.DeviceIdStore
import com.mdmesh.core.store.EnrollTokenStore
import com.mdmesh.core.sync.CheckInWorker
import com.mdmesh.core.sync.EnrollmentException
import com.mdmesh.core.sync.EnrollmentManager
import com.mdmesh.core.sync.SyncStatus
import com.mdmesh.policy.wifi.DpmHandle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * MDMesh agent home / status screen. Doubles as the kiosk HOME surface
 * (`android:lockTaskMode="if_whitelisted"` + HOME intent filter), so when the agent's
 * package is lock-task-allowlisted the system auto-enters lock task here.
 *
 * Shows the real managed state — Device-Owner status, server-issued device id, kiosk state,
 * and the server URL — so a person looking at the device can see it's managed by MDMesh.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deviceIdStore: DeviceIdStore
    @Inject lateinit var dpmHandle: DpmHandle
    @Inject lateinit var serverConfig: ServerConfigStore
    @Inject lateinit var syncStatus: SyncStatus
    @Inject lateinit var enrollment: EnrollmentManager

    private val qrScanner = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val scan = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val raw = scan?.contents ?: return@registerForActivityResult
        handleScannedEnrollment(raw)
    }

    private val deviceOwnerProvisioning = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showEnrollmentResult(true, "Full management enabled — this device is now Device Owner.")
        } else {
            showEnrollmentResult(
                false,
                "Couldn't enable full management. This almost always means the phone still has an " +
                    "account on it (Google or otherwise) — remove every account under Settings > " +
                    "Accounts, then try again.",
            )
        }
        refresh()
    }

    private lateinit var deviceIdValue: TextView
    private lateinit var kioskValue: TextView
    private lateinit var managementValue: TextView
    private lateinit var manageButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        handleDebugEnrollmentIntent(intent)
        // Grant our own POST_NOTIFICATIONS as Device Owner BEFORE starting the foreground
        // service, so the "MDMesh active" notification is visible on Android 13+.
        grantSelfNotifications()
        // NOTE: the battery-optimization exemption dialog is requested once, at enrollment
        // (AdminPolicyComplianceActivity for real provisioning; enrollWithCredentials below for the
        // debug flow) — deliberately NOT here on every launch, since it used to sit on top and pause
        // the kiosk launcher (a kiosk.enter wouldn't take visible effect until dismissed). Enrollment
        // always happens before kiosk can ever be active, so requesting it there is interruption-free.
        // Without the exemption, some OEMs (confirmed: Realme/ColorOS) silently drop PACKAGE_ADDED
        // broadcast delivery — see PackageEventReceiver / InstalledPackagePoller for the fallback.
        // Start the near-real-time command channel (foreground poll loop). Starting from the
        // launcher activity keeps us in the foreground-start allowance on Android 12+.
        ContextCompat.startForegroundService(this, Intent(this, CheckInService::class.java))
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugEnrollmentIntent(intent)
    }

    /** Accepts ADB enrollment extras only in debuggable builds. */
    private fun handleDebugEnrollmentIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL)?.trim()
        val token = intent?.getStringExtra(EXTRA_ENROLLMENT_TOKEN)?.trim()
        if (serverUrl.isNullOrBlank() && token.isNullOrBlank()) return

        lifecycleScope.launch {
            if (!serverUrl.isNullOrBlank()) serverConfig.save(serverUrl)
            if (!token.isNullOrBlank()) EnrollTokenStore(applicationContext).save(token)
            CheckInWorker.scheduleNow(applicationContext)
            refresh()
        }
    }

    /** Self-grant POST_NOTIFICATIONS (Device Owner, API 33+) so our FGS notification shows. */
    private fun grantSelfNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val dpm = dpmHandle.dpm
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setPermissionGrantState(
                    dpmHandle.admin,
                    packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        kioskValue.text = if (isLocked()) "Locked (kiosk active)" else "Not locked"
        val owner = isDeviceOwner()
        managementValue.text = if (owner) "Device Owner — active" else "Not managed"
        managementValue.setTextColor(if (owner) OK else ALERT)
        manageButton.visibility = if (owner) android.view.View.GONE else android.view.View.VISIBLE
        lifecycleScope.launch {
            val id = deviceIdStore.current()
            deviceIdValue.text = if (id.isNullOrBlank()) enrollingLabel() else id
        }
    }

    /** "Enrolling…" plus the last sync error (if any), so a stuck enrollment is diagnosable on-device. */
    private fun enrollingLabel(): String {
        val failure = syncStatus.lastError.value ?: return "Enrolling…"
        val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(failure.atMillis))
        return "Enrolling… (last error: ${failure.message} at $at)"
    }

    private fun isLocked(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
    }

    private fun isDeviceOwner(): Boolean =
        dpmHandle.dpm.isDeviceOwnerApp(packageName)

    /**
     * Self-service Device Owner grant — an alternative to the fragile "tap the Welcome screen 6
     * times" QR-during-setup flow (not every OEM/build surfaces it the same way) and to running
     * `adb shell dpm set-device-owner` by hand. [DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE]
     * is a normal system intent any installed app can fire; the OS does its own account check
     * (this device must have none) and runs the exact same [GetProvisioningModeActivity] /
     * [AdminPolicyComplianceActivity] flow QR provisioning does. No admin-extras bundle is passed
     * here — this device is already enrolled with the server, so there's nothing new to persist.
     */
    private fun startDeviceOwnerProvisioning() {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                AdminReceiver.componentName(this@MainActivity),
            )
        }
        if (intent.resolveActivity(packageManager) == null) {
            showEnrollmentResult(false, "This device doesn't support Device Owner provisioning.")
            return
        }
        deviceOwnerProvisioning.launch(intent)
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(INK)
            setPadding(dp(28), dp(40), dp(28), dp(40))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
        root.addView(text("MDMesh", 30f, SIGNAL, bold = true))
        root.addView(text("Device agent", 14f, MUTED).apply { setPadding(0, dp(2), 0, dp(20)) })

        root.addView(label("MANAGEMENT"))
        managementValue = text("…", 16f, TEXT)
        root.addView(managementValue)
        manageButton = Button(this).apply {
            text = "Enable full management"
            setOnClickListener { startDeviceOwnerProvisioning() }
        }
        root.addView(manageButton)
        root.addView(spacer())

        root.addView(label("DEVICE ID"))
        deviceIdValue = text("…", 14f, TEXT, mono = true)
        root.addView(deviceIdValue)
        root.addView(spacer())

        root.addView(label("KIOSK"))
        kioskValue = text("…", 16f, TEXT)
        root.addView(kioskValue)
        root.addView(spacer())

        root.addView(label("AGENT VERSION"))
        root.addView(
            text(
                "${com.mdmesh.agent.BuildConfig.VERSION_NAME} (build ${com.mdmesh.agent.BuildConfig.VERSION_CODE})",
                15f,
                TEXT,
                mono = true,
            ),
        )
        root.addView(spacer())

        root.addView(label("SERVER"))
        root.addView(text(serverConfig.baseUrl(), 13f, MUTED, mono = true))
        root.addView(spacer())

        if (BuildConfig.DEBUG) addDebugEnrollmentForm(root)

        root.addView(
            text("Managed by MDMesh", 12f, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            },
        )

        return ScrollView(this).apply {
            setBackgroundColor(INK)
            addView(root)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
    }

    private fun addDebugEnrollmentForm(root: LinearLayout) {
        root.addView(label("DEBUG ENROLLMENT"))
        val serverInput = EditText(this).apply {
            hint = "Server URL, e.g. http://192.168.1.25:8080"
            setText(serverConfig.baseUrl())
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val tokenInput = EditText(this).apply {
            hint = "One-time enrollment token"
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val submit = Button(this).apply {
            text = "Enroll with token"
            setOnClickListener {
                val url = serverInput.text.toString().trim().trimEnd('/')
                val token = tokenInput.text.toString().trim()
                enrollWithCredentials(url, token, this)
            }
        }
        val scan = Button(this).apply {
            text = "Scan enrollment QR"
            setOnClickListener { startQrScanner() }
        }
        root.addView(serverInput)
        root.addView(tokenInput)
        root.addView(scan)
        root.addView(submit)
        root.addView(spacer())
    }

    private fun startQrScanner() {
        if (!BuildConfig.DEBUG) return
        val integrator = IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Scan the MDMesh enrollment QR")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrScanner.launch(integrator.createScanIntent())
    }

    private fun handleScannedEnrollment(raw: String) {
        runCatching {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            val extras = json["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"]
                ?.jsonObject
            val url = extras?.get(AdminReceiver.EXTRA_SERVER_URL)?.jsonPrimitive?.content
                ?: json["serverUrl"]?.jsonPrimitive?.content
            val token = extras?.get(AdminReceiver.EXTRA_ENROLL_TOKEN)?.jsonPrimitive?.content
                ?: json["enrollmentToken"]?.jsonPrimitive?.content
            require(!url.isNullOrBlank() && !token.isNullOrBlank()) {
                "This QR does not contain an MDMesh server URL and enrollment token."
            }
            enrollWithCredentials(url.trim().trimEnd('/'), token.trim(), null)
        }.onFailure {
            showEnrollmentResult(false, it.message ?: "Could not read this enrollment QR.")
        }
    }

    private fun enrollWithCredentials(url: String, token: String, button: Button?) {
        if (url.isBlank() || token.isBlank()) {
            showEnrollmentResult(false, "Enter both the server URL and the one-time token.")
            return
        }
        if (!isUsableServerUrl(url)) {
            showEnrollmentResult(
                false,
                "Use the real API URL, for example:\nhttp://192.168.62.153:8080\n\nDo not use mdm.example.com, mdmesh.server_url, localhost, or port 5173.",
            )
            return
        }
        button?.isEnabled = false
        lifecycleScope.launch {
            try {
                serverConfig.save(url)
                EnrollTokenStore(applicationContext).save(token)
                val deviceId = enrollment.ensureEnrolled()
                CheckInWorker.scheduleNow(applicationContext)
                BatteryOptimizationExemption.requestIfNeeded(applicationContext)
                showEnrollmentResult(true, "Enrollment succeeded.\n\nDevice ID: $deviceId")
                refresh()
            } catch (error: Throwable) {
                showEnrollmentResult(false, enrollmentError(error))
            } finally {
                button?.isEnabled = true
            }
        }
    }

    private fun isUsableServerUrl(url: String): Boolean = runCatching {
        val parsed = java.net.URI(url)
        parsed.scheme in setOf("http", "https") &&
            !parsed.host.isNullOrBlank() &&
            parsed.host !in setOf("mdm.example.com", "mdmesh.server_url", "localhost")
    }.getOrDefault(false)

    private fun enrollmentError(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return when {
            root is java.net.UnknownHostException -> "Cannot find the server host. Check that the phone and PC are on the same Wi-Fi and that the URL is correct.\n\n${root.message.orEmpty()}"
            root is java.net.ConnectException -> "Cannot connect to the server. Check Docker, Windows Firewall, and port 8080.\n\n${root.message.orEmpty()}"
            root is javax.net.ssl.SSLException -> "TLS/HTTPS failed. Your local Docker server uses HTTP, not HTTPS. Use http://192.168.62.153:8080.\n\n${root.message.orEmpty()}"
            error.message?.contains("error.agent.enrollment.disabled") == true -> "The server rejected enrollment because this token has no device configuration. In the console open Enroll, select a configuration in the configuration dropdown, generate a new token, then try again."
            error is EnrollmentException -> error.message ?: "The server rejected enrollment. Generate a new token."
            else -> error.message ?: root.message ?: "Enrollment failed. Generate a new token and try again."
        }
    }

    private fun showEnrollmentResult(success: Boolean, message: String) {
        AlertDialog.Builder(this)
            .setTitle(if (success) "Enrollment successful" else "Enrollment failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun label(s: String): TextView =
        text(s, 11f, FAINT).apply {
            letterSpacing = 0.10f
            setPadding(0, 0, 0, dp(4))
        }

    private fun spacer(): TextView = TextView(this).apply {
        height = dp(16)
    }

    private fun text(
        s: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        mono: Boolean = false,
    ): TextView = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (mono) typeface = Typeface.MONOSPACE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_ENROLLMENT_TOKEN = "enrollment_token"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val INK = Color.parseColor("#0E1117")
        val TEXT = Color.parseColor("#E8EEF4")
        val MUTED = Color.parseColor("#8693A4")
        val FAINT = Color.parseColor("#5C6675")
        val SIGNAL = Color.parseColor("#F4B942")
        val OK = Color.parseColor("#3FD08A")
        val ALERT = Color.parseColor("#F2545B")
    }
}
