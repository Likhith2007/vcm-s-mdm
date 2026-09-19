# Capability & command registries (v1)

Open string registries. Adding a row is an additive (MINOR) change. Keep this file in sync
with the agent's capability advertisement and the server's command catalog.

## Capability keys

### policy
| key | meaning | min SDK | notes |
|-----|---------|---------|-------|
| `wifi` | toggle/lock Wi-Fi | 24 | |
| `bluetooth` | toggle/lock Bluetooth | 24 | BLUETOOTH_SCAN auto-grant fails if target app targetSdk<=30 |
| `gps` | location toggle | 24 | |
| `mobileData` | mobile data toggle | 24 | |
| `usbStorage` | block USB mass storage | 24 | |
| `camera` | disable camera | 24 | |
| `screenshots` | disable screenshots | 24 | |
| `kioskLockTask` | COSU lock-task kiosk | 24 | setLockTaskPackages/Features |
| `passwordComplexity` | password policy | 31 | setRequiredPasswordComplexity (setPasswordQuality deprecated @26) |
| `systemUpdatePolicy` | OS update windows | 24 | |
| `factoryResetProtection` | FRP policy | 30 | setFactoryResetProtectionPolicy |
| `usbDebug` | disable USB debugging | 18 | DISALLOW_DEBUGGING_FEATURES |
| `factoryReset` | prevent factory reset | 18 | DISALLOW_FACTORY_RESET |
| `unknownSources` | block unknown app sources | 28 | DISALLOW_INSTALL_UNKNOWN_SOURCES |
| `appBlock` | block app launch (per package) | 24 | setApplicationHidden (complex payload) |
| `appHide` | hide app icon (per package) | 24 | setApplicationHidden (complex payload) |
| `adminRemoval` | detect admin removal | 24 | telemetry-based, isAdminActive check |
| `internetSchedule` | time-based internet access | 24 | WorkManager scheduling |

### appManagement
| key | meaning | notes |
|-----|---------|-------|
| `silentInstall` | PackageInstaller silent install | Device Owner only |
| `silentUninstall` | silent uninstall | Device Owner only |
| `splitApk` | split/.xapk install | |
| `fdroidCatalog` | can pull from an F-Droid repo | for the APK browser/catalog feature |

### remoteControl
Advertised as an object (`tier`, `screenCapture`, `inputInjection`, `transport`). See README.

### oem
`vendor`, `knox` (parked tier).

## Command types

| type | requiresCapability | payload (sketch) |
|------|--------------------|------------------|
| `config.sync` | — | none (triggers a full reconcile) |
| `policy.apply` | the specific policy key | `{ policy: "wifi", value: false }` |
| `app.install` | `silentInstall` | `{ url, packageName, versionCode, sha256, runAfterInstall }` |
| `app.uninstall` | `silentUninstall` | `{ packageName }` |
| `app.approveInstall` | — | `{ packageName }` — un-suspends a package the agent auto-suspended pending approval |
| `app.launch` | — | `{ packageName, activity? }` |
| `remote.startSession` | `remoteControl.tier>=view` | `{ sessionId, signaling: {...}, mode: "view"|"control" }` |
| `remote.stopSession` | — | `{ sessionId }` |
| `device.reboot` | — | none (DO) |
| `device.lock` | — | none |

Per-type payload JSON Schemas go in `proto/payloads/` as the registry grows.

## Events (agent -> server, buffered and flushed on check-in)

`appInstallPending` — the agent detected a non-MDM-initiated install (Play Store or
sideload), immediately suspended the package (`DevicePolicyManager.setPackagesSuspended`),
and is reporting it for admin approval. `detail` is the package name. The server upserts a
pending-approval row on receipt; the admin's Approve/Deny queues `app.approveInstall` /
`app.uninstall` respectively. Installs the console itself pushes via `app.install` are never
gated — see `SelfInitiatedInstalls` in `agent-android/core`.
