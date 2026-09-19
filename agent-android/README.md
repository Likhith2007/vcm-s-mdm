# MDM Agent (Android)

A from-scratch, self-hosted **custom DPC** (Device Owner) Android MDM agent.
Modern Kotlin: coroutines/Flow, Hilt, Room, DataStore, WorkManager,
kotlinx.serialization + Retrofit.

- `applicationId` / base namespace: `com.mdmesh.agent`
- `minSdk 24`, `targetSdk 35`, `compileSdk 35`
- Identity is a **server-issued device id** (DataStore). The agent never uses
  IMEI/IMSI/serial as identity (restricted post-Android 10).

> This scaffold will not build on a box without the Android SDK/Gradle. It is
> structured to compile cleanly in CI or a provisioned dev box once an SDK is
> present.

## Module map

| Module | Type | Responsibility |
|--------|------|----------------|
| `:proto` | Kotlin/JVM lib | `@Serializable` wire contract mirroring `../proto/` (`CapabilityMatrix`, `CommandEnvelope`, `CommandResult`, `ProtocolJson`). No Android deps. |
| `:policy` | android-lib | **Capability-abstraction layer.** `DeviceControl`, `PolicyStrategy`, SDK-gated `WifiPolicy` (modern/legacy strategies + factory), `CapabilityRegistry`. All `DevicePolicyManager` calls stay behind interfaces. |
| `:core` | android-lib | Sync/check-in: Retrofit `MdmApi`, `CapabilityCollector`, `CommandDispatcher` (+ handlers), `DeviceIdStore` (DataStore), `CheckInCoordinator`/`CheckInWorker`. Base URL via `BuildConfig`. |
| `:kiosk` | android-lib | COSU skeleton: `KioskController` (+ stub), `CrashLoopGuard`. |
| `:remote` | android-lib | Remote view/control skeleton: `RemoteControlSession`, `RemoteControlTierDetector`, and the **only** Accessibility surface (`InputInjectionService`). |
| `:oem` | android-lib | `OemAdapter` + `GenericOemAdapter` (no-op) + `KnoxAdapter` (PARKED, no Knox dep). |
| `:app` | android-app | Hilt `Application`, `AdminReceiver`, provisioning activities, `MainActivity` launcher/home stub, `CheckInService`, manifest with the minimal permission set. |

### The capability-abstraction intent

Android is a permanent version treadmill — every OS release adds, removes, or
restricts a policy API. Rather than scatter `Build.VERSION.SDK_INT` branches through
feature code, each policy area defines an interface (`WifiPolicy`) with multiple
`PolicyStrategy` implementations; a factory picks the supported one **once**. The
`CapabilityRegistry` reports only the keys that have a working strategy, and that
set becomes the `capabilities` advertised in the `CapabilityMatrix`. The server is
contractually forbidden from sending a command whose `requiresCapability` isn't
advertised, so a 3-year-old agent on Android 9 and a fresh agent on Android 16 talk
to the same server without special-casing. Unknown command types degrade to
`status=unsupported` instead of crashing.

### Permission minimization (Play Protect)

- Base `:app` manifest has **no** `READ_SMS` and **no** `QUERY_ALL_PACKAGES`;
  package visibility is scoped via `<queries>`.
- **Accessibility / input injection lives only in `:remote`** (and merges in only
  for builds that include it). Its `AccessibilityService` ships
  `android:enabled="false"` and is toggled on only for an authorised control session.
- No phone-state / device-identifier permissions — identity is server-issued.

## Build

```bash
# One-time: generate the binary wrapper jar (cannot be committed from this box).
gradle wrapper --gradle-version 8.10.2

# Then the usual:
./gradlew assembleDebug
./gradlew test          # :proto + :core JVM unit tests
```

> The repo ships `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.properties`,
> but **not** `gradle/wrapper/gradle-wrapper.jar` (a binary). Run `gradle wrapper`
> once on a machine that has a system Gradle to materialise it.

Release signing reads from env vars (`MDM_RELEASE_STORE_FILE`,
`MDM_RELEASE_STORE_PASSWORD`, `MDM_RELEASE_KEY_ALIAS`, `MDM_RELEASE_KEY_PASSWORD`);
see the `// TODO(keystore custody)` note in `app/build.gradle.kts`. The DO binding
is tied to the signing certificate — re-signing a deployed DPC with a different key
forces a factory reset of every enrolled device, so guard the release key carefully.

## ADB Device-Owner dev enrollment loop

Device Owner can only be set on a device/emulator with **no accounts** (fresh or
factory-reset). Then:

```bash
# 1. Install the agent.
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Bind it as Device Owner (note the .debug applicationIdSuffix on debug builds).
adb shell dpm set-device-owner com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver
#   release build would be:
#   adb shell dpm set-device-owner com.mdmesh.agent/com.mdmesh.agent.admin.AdminReceiver

# 3. Confirm.
adb shell dumpsys device_policy | grep -i "Device Owner"
```

If `set-device-owner` fails with "Not allowed to set the device owner because
there are already some accounts" — remove all accounts, or factory reset.

### Getting OFF Device Owner (dev cycle)

A Device Owner cannot simply be uninstalled. To clear it:

```bash
# Clears the DO binding (works on debug/userdebug builds).
adb shell dpm remove-active-admin com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver

# If that is blocked, factory reset:
adb shell am broadcast -a android.intent.action.MASTER_CLEAR   # or wipe via Settings / recovery
```

On an emulator, the fastest reset is **Wipe Data** (cold boot) from the AVD
manager, then re-run the enrollment loop.

## Real provisioning (production)

Production enrollment is via QR / NFC / zero-touch using the
`GET_PROVISIONING_MODE` + `ADMIN_POLICY_COMPLIANCE` activities (already wired). The
ADB loop above is for the dev inner loop only.

## Complete Windows mobile-testing guide

This section is the end-to-end local workflow for testing the debug agent on a
real Android phone with the MDMesh server running in Docker.

### 1. Required tools

Install or confirm these tools on Windows:

- Android Studio with Android SDK Platform 35 and Platform-Tools
- JDK 17+
- Docker Desktop with the Linux engine running
- A USB cable and an Android test phone

The debug APK uses this package and admin component:

```text
Package:  com.mdmesh.agent.debug
Admin:    com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver
```

Never test Device Owner behavior on a personal phone unless it can be erased.
Device Owner provisioning can require a factory reset.

### 2. Open PowerShell and fix ADB PATH

Every new PowerShell window may not know the `adb` command. Run this whenever
PowerShell says `adb is not recognized`:

```powershell
$env:Path += ";C:\Users\ilikh\AppData\Local\Android\Sdk\platform-tools"
```

Confirm ADB:

```powershell
adb version
```

To permanently add Platform-Tools to your user PATH:

```powershell
$sdkTools = "C:\Users\ilikh\AppData\Local\Android\Sdk\platform-tools"
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$sdkTools*") {
    [Environment]::SetEnvironmentVariable("Path", "$userPath;$sdkTools", "User")
}
```

Close and reopen PowerShell after changing the permanent PATH.

### 3. Prepare and authorize a new phone

On the phone:

1. Open Settings and enable Developer options.
2. Enable USB debugging.
3. Connect the USB cable.
4. Unlock the phone.
5. Accept **Allow USB debugging** and select **Always allow from this computer**.

From PowerShell:

```powershell
adb kill-server
adb start-server
adb devices
```

Expected:

```text
List of devices attached
DEVICE_SERIAL    device
```

If it says `unauthorized`, unlock the phone and accept the dialog. If no dialog
appears, use **Developer options > Revoke USB debugging authorizations**, unplug
and reconnect the cable, then repeat the commands.

If it says no devices, try another cable/USB port and confirm the phone's USB
mode allows data transfer.

### 4. Build and install the debug APK

From the repository:

```powershell
cd C:\Users\ilikh\Downloads\mdm\MDMesh\agent-android
.\gradlew.bat --no-daemon :app:assembleDebug
```

Install it:

```powershell
adb install -r "C:\Users\ilikh\Downloads\mdm\MDMesh\agent-android\app\build\outputs\apk\debug\app-debug.apk"
```

Expected:

```text
Success
```

Launch the app manually when needed:

```powershell
adb shell am start -n com.mdmesh.agent.debug/com.mdmesh.agent.MainActivity
```

### 5. Set Device Owner for ADB testing

Check current owners:

```powershell
adb shell dpm list-owners
```

For a fresh test phone, do not add Google/Samsung accounts before setting
Device Owner. Install the APK, then run:

```powershell
adb shell dpm set-active-admin com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver
adb shell dpm set-device-owner com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver
adb shell dpm list-owners
```

Expected output contains:

```text
DeviceOwner
com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver
```

Common errors:

- **Already an admin, but invalid component**: use the exact debug component
  above. The class is in `com.mdmesh.agent.admin`, not `com.mdmesh.agent.receiver`.
- **Not active admin**: run `set-active-admin` first.
- **Unknown admin**: reinstall the APK and use the exact debug package/component.
- **Accounts already exist**: remove all accounts or factory-reset the test phone.
- **No owners**: the app may be installed, but Device Owner setup did not finish.

### 6. Start the local MDMesh server

Open a second PowerShell window. Start Docker Desktop first and wait until its
Linux engine says it is running. Then:

```powershell
cd C:\Users\ilikh\Downloads\mdm\MDMesh
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

Both services should be running:

```text
mdmesh-postgres-1   ... healthy
mdmesh-server-1     ... 0.0.0.0:8080->8080/tcp
```

If Docker reports `dockerDesktopLinuxEngine` is missing, open Docker Desktop
and wait for the engine to start. If the server exits with `BASE_URL is
required`, create `.env` in the repository root with local values:

```text
DB_NAME=mdmesh
DB_USER=mdmesh
DB_PASSWORD=mdmesh
BASE_URL=http://192.168.62.153:8080
HASH_SECRET=mdmesh-local-dev-secret
SECURE_ENROLLMENT=0
```

Replace `192.168.62.153` with the PC's Wi-Fi IPv4 address from:

```powershell
ipconfig
```

The phone and PC must be on the same Wi-Fi network. Check the API from the PC:

```powershell
Invoke-WebRequest http://192.168.62.153:8080/rest/public/auth/options -UseBasicParsing
```

An HTTP `200` confirms the API is reachable. A browser `404` at
`http://192.168.62.153:8080/` is normal because port 8080 is the backend API,
not the web console.

### 7. Start the web console

Open a third PowerShell window. The console is a Vite development server and
must proxy API calls to Docker:

```powershell
$env:VITE_DEV_PROXY_TARGET = "http://localhost:8080"
$env:VITE_DEVICE_SERVER_URL = "http://192.168.62.153:8080"
$env:VITE_AGENT_APK_URL = "http://192.168.62.153:8000/app-debug.apk"
npm --prefix "C:\Users\ilikh\Downloads\mdm\MDMesh\web" install
npm --prefix "C:\Users\ilikh\Downloads\mdm\MDMesh\web" run dev -- --host 0.0.0.0
```

Open the console on the PC:

```text
http://localhost:5173
```

From the phone or another computer on the same Wi-Fi:

```text
http://192.168.62.153:5173
```

Default local login:

```text
Login:    admin
Password: admin
```

Port meanings:

| Port | Purpose | Phone uses it? |
|------|---------|----------------|
| `5173` | React/Vite admin console | No, except to open the console remotely |
| `8080` | Java/Tomcat REST API | Yes, for enrollment and check-in |
| `8000` | Temporary APK download server | Only QR provisioning needs it |

### 8. Make the debug APK available for QR provisioning

Open a fourth PowerShell window:

```powershell
python -m http.server 8000 --directory "C:\Users\ilikh\Downloads\mdm\MDMesh\agent-android\app\build\outputs\apk\debug"
```

The phone should be able to open this URL in its browser:

```text
http://192.168.62.153:8000/app-debug.apk
```

If it cannot, allow Python through Windows Firewall or add an inbound TCP rule
for port 8000. Keep this terminal running during QR provisioning.

### 9. Enroll using the in-app debug flow

This is the easiest method when the phone is already Device Owner.

1. In the web console open **Enroll**.
2. Select a real device configuration in the configuration dropdown.
3. Select **Token** or **Scan QR**.
4. Generate a new token/QR. Tokens are single-use.
5. Open the MDMesh app and scroll to **DEBUG ENROLLMENT**.
6. For manual enrollment, enter exactly:

```text
Server URL: http://192.168.62.153:8080
```

7. Paste the new token and tap **Enroll with token**.

For in-app QR enrollment, tap **Scan enrollment QR**, allow camera access, and
scan the QR from the console. The QR must contain an MDMesh server URL and a
one-time token.

The app shows an immediate success or failure dialog. On success it displays a
server-issued Device ID and the phone appears under **Devices**.

`error.agent.enrollment.disabled` means the token has no configuration. Return
to **Enroll**, select a configuration, generate a new token, and try again.

Use `http://192.168.62.153:8080` for local HTTP testing. Do not use:

```text
http://localhost:8080
http://localhost:5173
https://mdm.example.com
https://mdmesh.server_url
```

The debug APK allows local HTTP. Release builds require HTTPS.

### 10. Observe server and phone logs

Server logs:

```powershell
cd C:\Users\ilikh\Downloads\mdm\MDMesh
docker compose -f docker-compose.dev.yml logs -f server
```

Useful filtered server logs:

```powershell
docker compose -f docker-compose.dev.yml logs --since 10m server |
  Select-String "enroll|Agent|ERROR|Exception|token"
```

Phone logs:

```powershell
adb logcat -c
adb logcat | Select-String "OkHttpClient|CheckInWorker|Enrollment|enroll|UnknownHost|SSL"
```

A successful request looks like:

```text
--> POST http://192.168.62.153:8080/rest/public/agent/v1/enroll
<-- 200
```

Common phone errors:

- `UnknownHostException`: wrong hostname or phone/PC are not on the same network.
- `Unable to parse TLS packet header`: entered `https://` for the local HTTP server.
- `CLEARTEXT communication ... not permitted`: install the latest debug APK;
  local HTTP is enabled only in the debug manifest.
- `error.agent.enrollment.disabled`: token was generated without a device
  configuration.
- `error.agent.token.used`: generate a new token.
- `error.agent.token.expired`: generate a new token.
- `Connection refused`: Docker server is stopped, port 8080 is blocked, or the
  IP address changed.

The Tomcat `404` at the root URL and `405 Method Not Allowed` when opening the
enrollment endpoint in a browser are expected: the backend endpoint requires a
`POST` request, not a browser `GET` request.

### 11. Test Phase 1 policies

After the phone appears under **Devices**:

1. Open the device details page.
2. Use the command/policy controls.
3. Start with app blocking because it does not disconnect ADB.
4. Test block and unblock with a package such as Maps or Chrome.
5. Test factory reset and unknown sources restrictions.
6. Test USB debugging last; disabling it disconnects ADB.

To inspect Device Owner state:

```powershell
adb shell dpm list-owners
adb shell dumpsys device_policy
```

### 12. Stop the local services

Stop Docker services:

```powershell
cd C:\Users\ilikh\Downloads\mdm\MDMesh
docker compose -f docker-compose.dev.yml down
```

Stop Vite and the temporary APK server with `Ctrl+C` in their terminals.

### 13. Reset the development phone

To remove Device Owner during development:

```powershell
adb shell dpm remove-active-admin com.mdmesh.agent.debug/com.mdmesh.agent.admin.AdminReceiver
```

If Android refuses, factory-reset the test phone. A factory reset erases the
phone and is required when Android will not allow another Device Owner.
