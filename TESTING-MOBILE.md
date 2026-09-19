# Phase 1: Mobile Device Testing Guide

Testing the MDMesh agent on a real Android device is essential to verify policies actually work.

---

## Prerequisites

### Device Requirements
- **Android 7.0+** (API 24+) minimum
- **Device Owner mode enabled** (required for all DPM restrictions to work)
- USB debugging enabled
- ADB (Android Debug Bridge) accessible
- Internet connection (to reach MDMesh server)

### Your Machine Needs
- *i did*JDK 17+**
- **Android SDK** with platform tools (adb, aapt)
- **Gradle 8.10+** (via wrapper)
- **USB cable** to connect device

### MDMesh Server
- **Running**: http://localhost:8080 (or remote URL)
- **Device enrolled** with the target device already

---

## Step 1: Prepare the Device

### Option A: Physical Device (Manual Device Owner Setup)

⚠️ **Warning**: Setting Device Owner requires factory reset. Do this on a test device only.

```bash
# 1. Factory reset the device (Settings > System > Reset options > Erase all data)
# 2. Connect via USB
# 3. Enable USB debugging (Settings > Developer Options > USB Debugging)
# 4. Verify ADB connection
adb devices

# 5. Install and set as Device Owner
# First, build and install the agent APK (see Step 2)
# Then:
adb shell dpm set-device-owner com.mdmesh.agent/.receiver.AdminReceiver

# 6. Verify Device Owner is set
adb shell dpm get-device-owner
# Should output: com.mdmesh.agent/.receiver.AdminReceiver
```

### Option B: Android Emulator (Easier for Testing)

1. Create an emulator with **API 28** (Android 9.0 or higher)
   - Android Studio > Virtual Device Manager > Create Device
   - Choose "Pixel 4" or similar
   - Select API 28+
   - Click Create

2. Start emulator:
   ```bash
   emulator -avd Pixel_4_API_28 &
   ```

3. Set as Device Owner:
   ```bash
   adb shell dpm set-device-owner com.mdmesh.agent/.receiver.AdminReceiver
   ```

4. Verify:
   ```bash
   adb shell dpm get-device-owner
   ```

---

## Step 2: Build & Deploy APK

### Build the Debug APK
```bash
cd agent-android
./gradlew :app:assembleDebug
```

APK location: `agent-android/app/build/outputs/apk/debug/app-debug.apk`

### Install on Device
```bash
adb install agent-android/app/build/outputs/apk/debug/app-debug.apk
```

Or, if already installed, reinstall:
```bash
adb install -r agent-android/app/build/outputs/apk/debug/app-debug.apk
```

### Verify Installation
```bash
adb shell pm list packages | grep mdmesh
# Should show: package:com.mdmesh.agent

# Check if Device Owner is still set
adb shell dpm get-device-owner
```

---

## Step 3: Enroll Device in MDMesh Console

1. Open **MDMesh Console**: http://localhost:8080
2. Login (default: admin / admin)
3. Go to **Devices > New Enrollment**
4. Select **Android**
5. Scan QR code or note enrollment ID
6. On device: Open MDMesh Agent app
7. Enter enrollment ID and MDMesh server URL
8. Complete enrollment

### Verify Enrollment
- Console shows device as "Active"
- Device check-in shows capability matrix with:
  - `usbDebug`
  - `factoryReset`
  - `unknownSources` (API 28+ only)
  - `appBlock`
  - `appHide`
  - `adminRemoval`

---

## Step 4: Test Each Policy

### 1. USB Debug Policy

**Disable USB Debug:**
```bash
# 1. Get device ID from console or:
adb devices
# Note the device ID

# 2. Queue policy via console:
#    Console > Devices > Select device > Commands > Add
#    Template: "Disable USB Debugging"
#    This sends: { "policy": "usbDebug", "value": false }

# 3. Verify on device (from your machine):
adb shell settings get global adb_enabled
# After policy: 0 (disabled)
# Before policy: 1 (enabled)
```

**Expected**:
- ✓ USB debugging disabled (Settings > Developer Options > USB Debugging is OFF)
- ✓ ADB connection breaks (adb: offline)
- ✓ Device restrictions set: `no_debugging_features`

**Check in logcat**:
```bash
adb logcat | grep -i "usbDebug\|debugging_features"
# Should see: "addUserRestriction(no_debugging_features)"
```

**Re-enable USB Debug:**
```bash
# Queue: { "policy": "usbDebug", "value": true }
# After applying, ADB should reconnect
adb devices
# Should show: device
```

---

### 2. Factory Reset Policy

**Disable Factory Reset:**
```bash
# 1. Queue via console: "Disable Factory Reset"
#    This sends: { "policy": "factoryReset", "value": false }

# 2. Verify on device (manually or via adb):
adb shell settings get global master_reset_available
# After policy: 0 (disabled)

# 3. Check manually:
#    Settings > System > Reset options > Factory Reset
#    Should be greyed out / disabled
```

**Expected**:
- ✓ "Erase all data" button is disabled/hidden
- ✓ Device restrictions set: `no_factory_reset`

**Check in logcat**:
```bash
adb logcat | grep -i "factoryReset\|no_factory_reset"
# Should see: "addUserRestriction(no_factory_reset)"
```

**Re-enable Factory Reset:**
```bash
# Queue: { "policy": "factoryReset", "value": true }
# Button should re-appear
```

---

### 3. Unknown Sources Policy (API 28+ Only)

⚠️ **Only available on API 28 (Android 9) and higher**

**Disable Unknown Sources:**
```bash
# 1. Queue via console: "Disable Unknown Sources"
#    This sends: { "policy": "unknownSources", "value": false }

# 2. Verify on device:
adb shell settings get secure install_non_market_apps
# After policy: 0 (disabled)

# 3. Check manually:
#    Settings > Apps & Notifications > Special app access > Install unknown apps
#    Should be disabled for all sources
```

**Expected**:
- ✓ Cannot install APKs from unknown sources (sideloading blocked)
- ✓ Device restrictions set: `no_install_unknown_sources`

**Test sideloading**:
```bash
# Try to install any APK not from Play Store:
adb install some-app.apk
# Should be blocked with permission error
```

---

### 4. App Block Policy

**Block Google Maps (or any app):**
```bash
# 1. Queue via console: Custom command
#    Type: policy.apply
#    Payload: 
#    {
#      "policy": "appBlock",
#      "packageName": "com.google.android.apps.maps",
#      "value": true
#    }

# 2. Verify on device:
adb shell pm get-app-hidden com.google.android.apps.maps
# After policy: true (hidden)

# 3. Check manually:
#    App drawer - Maps should be gone / hidden
#    Launching maps via: adb shell am start com.google.android.apps.maps
#    Should fail or not launch
```

**Expected**:
- ✓ App icon disappears from launcher
- ✓ App cannot be launched
- ✓ Logcat shows: `setApplicationHidden(com.google.android.apps.maps, true)`

**Test with multiple apps:**
```bash
# Block Gmail
{
  "policy": "appBlock",
  "packageName": "com.google.android.gm",
  "value": true
}

# Block Chrome
{
  "policy": "appBlock",
  "packageName": "com.android.chrome",
  "value": true
}
```

**Unblock an app:**
```bash
# Queue with value=false
{
  "policy": "appBlock",
  "packageName": "com.google.android.apps.maps",
  "value": false
}
# Maps should reappear in app drawer
```

---

### 5. App Hide Policy (Identical to AppBlock)

**Hide Chrome:**
```bash
# Queue:
{
  "policy": "appHide",
  "packageName": "com.android.chrome",
  "value": true
}

# Result: Chrome icon hidden (same as block)
```

**Unhide Chrome:**
```bash
# Queue with value=false
{
  "policy": "appHide",
  "packageName": "com.android.chrome",
  "value": false
}
```

---

### 6. Admin Removal Safeguard

⚠️ **This policy is mostly telemetry-based in Phase 1**

**Test detection:**
```bash
# 1. Verify admin is active:
adb shell dpm get-device-owner
# Should show: com.mdmesh.agent/.receiver.AdminReceiver

# 2. Try to remove admin (should fail if safeguard working):
adb shell dpm remove-active-admin com.mdmesh.agent/.receiver.AdminReceiver
# Should fail with permission denied

# 3. Check device telemetry on next check-in:
#    Console > Devices > Select device > Telemetry
#    Should show admin still active
```

**Expected**:
- ✓ Cannot remove admin (restricted by Device Owner)
- ✓ Telemetry reports admin still active

---

## Step 5: Verify Logs

### Logcat (Real-time)
```bash
# Start logcat in one terminal
adb logcat -v threadtime MDMesh:* *:E

# Apply a policy in console
# You should see:
# - Policy command received
# - DPM call (e.g., addUserRestriction)
# - Policy outcome (Applied / Failed)
```

### Server Logs
```bash
# Check MDMesh server logs
docker compose -f docker-compose.dev.yml logs -f server | grep -i "policy\|command"

# Should show:
# - Command queued
# - Device received command
# - Command result (DONE / FAILED)
```

### Check Device Restrictions
```bash
# See all restrictions applied
adb shell dumpsys device_policy

# Look for: User restrictions and Admin restrictions
# Should include: no_debugging_features, no_factory_reset, no_install_unknown_sources
```

---

## Troubleshooting

### Device Not Showing in Console After Enrollment
- [ ] Check device internet connection
- [ ] Verify MDMesh server is running (`docker compose ps`)
- [ ] Check device has MDMesh app installed
- [ ] Review server logs for enrollment errors

### Device Owner Not Set
```bash
# Verify
adb shell dpm get-device-owner

# If not set or wrong:
# 1. Uninstall app: adb uninstall com.mdmesh.agent
# 2. Reinstall: adb install app-debug.apk
# 3. Set again: adb shell dpm set-device-owner com.mdmesh.agent/.receiver.AdminReceiver
```

### Policy Command Shows "UNSUPPORTED"
- [ ] Device API level is too low for this policy
- [ ] Device is not Device Owner
- [ ] Policy key doesn't match capability name

Verify:
```bash
# Check device API
adb shell getprop ro.build.version.release
# or
adb shell getprop ro.build.version.sdk

# Check Device Owner
adb shell dpm get-device-owner

# Check capabilities in console check-in
```

### USB Debug Policy Breaks ADB
```bash
# If you disabled USB debug and can't reconnect:
# 1. Re-enable via Device Owner (undo the policy)
# 2. Or use WiFi ADB:
adb connect <device-ip>:5555

# 3. Or use adb over TCP from emulator:
adb tcpip 5555
adb connect localhost:5555
```

### App Hide Doesn't Work
- [ ] App is system app (some system apps can't be hidden)
- [ ] Device not Device Owner
- [ ] API is too old (need API 24+)

Check if app can be hidden:
```bash
# Try manually
adb shell pm hide com.example.app

# If it fails, the app can't be hidden on this device
```

### Policy Keeps Failing
```bash
# Check error message in device telemetry
# Common issues:
# - Device Owner lost (check: adb shell dpm get-device-owner)
# - DPM API changed on new Android (check API level)
# - Malformed JSON payload (check console logs)

# Re-verify admin:
adb shell dpm get-device-owner
```

---

## Testing Checklist

### Basic Setup
- [ ] Device/emulator ready (Android 7.0+)
- [ ] USB debugging enabled
- [ ] Device Owner set to com.mdmesh.agent/.receiver.AdminReceiver
- [ ] MDMesh server running
- [ ] Device enrolled in console

### Policy Tests (API 24+)
- [ ] USB Debug: Disable → ADB breaks → Re-enable → ADB works
- [ ] Factory Reset: Disable → Button hidden → Re-enable → Button shows
- [ ] App Block: Block Maps → Disappears → Unblock → Reappears
- [ ] App Hide: Hide Chrome → Disappears → Unhide → Reappears

### Policy Tests (API 28+ Only)
- [ ] Unknown Sources: Disable → Can't sideload → Re-enable → Can sideload

### Integration
- [ ] Console shows all policies in capability matrix
- [ ] Device receives commands successfully
- [ ] Device telemetry shows policy outcome (Applied/Failed)
- [ ] Server logs show no errors

---

## Cross-API Testing (Recommended)

Test on multiple API levels if possible:

| API | Code | Device | USB Debug | Factory Reset | Unknown Sources | App Block | Status |
|-----|------|--------|-----------|---------------|-----------------|-----------|--------|
| 24  | 7.0  | Emulator | ✓ | ✓ | ✗ | ✓ | Test |
| 28  | 9.0  | Emulator | ✓ | ✓ | ✓ | ✓ | Test |
| 30  | 11   | Device | ✓ | ✓ | ✓ | ✓ | Test |
| 33  | 13   | Emulator | ✓ | ✓ | ✓ | ✓ | Test |
| 35  | 15   | Emulator | ✓ | ✓ | ✓ | ✓ | Test |

---

## When Tests Pass ✅

If all policies work correctly:

1. ✅ All Phase 1 features validated
2. ✅ Ready to proceed to Phase 2 (Internet Scheduling)
3. ✅ Can test Phase 2 on same device

---

## Next: Phase 2 Implementation

Once Phase 1 is validated on mobile:
- [ ] Implement `InternetSchedulePolicy`
- [ ] Build `ScheduleWorker` (Wi-Fi/data toggling)
- [ ] Test internet blocking on schedule
- [ ] Server-side schedule validation
- [ ] Console schedule builder UI
