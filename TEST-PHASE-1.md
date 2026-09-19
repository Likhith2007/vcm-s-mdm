# Phase 1 Testing Strategy

**Status**: Implementation complete; tests created; awaiting local/CI execution

---

## Test Files Created

### Security Policy Tests
1. **UsbDebugPolicyTest.kt** — Unit tests for USB debug policy
   - Disable USB debug: `setEnabled(false)` → `addUserRestriction(DISALLOW_DEBUGGING_FEATURES)`
   - Enable USB debug: `setEnabled(true)` → `clearUserRestriction(DISALLOW_DEBUGGING_FEATURES)`
   - API guard: SDK 18+

2. **SecurityPolicyFactoriesTest.kt** — Factory probe validation
   - USB Debug: API 18+ (JELLY_BEAN_MR2)
   - Factory Reset: API 18+
   - Unknown Sources: API 28+ (P) — strictest requirement
   - Admin Removal: API 24+ (N)

### App Management Tests
3. **AppBlockPolicyTest.kt** — Complex policy tests
   - Parse `packageName` from JSON payload
   - Parse `value` (boolean) from payload
   - Call `setApplicationHidden(admin, packageName, value)`
   - Reject malformed payloads
   - API guard: SDK 24+

### Handler & Registry Tests
4. **ComplexPolicyHandlerTest.kt** — Command routing validation
   - Route commands to correct complex policy via policy key
   - Extract full JSON payload (not just top-level fields)
   - Report UNSUPPORTED for unknown policies
   - Report FAILED on DPM exceptions

5. **CapabilityRegistryTest.kt** — Capability negotiation
   - Aggregate toggle + complex policy keys
   - Handle API-level filtering (factory returns null if unsupported)
   - No exceptions during capability detection

---

## Test Execution

### Prerequisites
```
✓ JDK 17
✓ Android SDK (minimum)
✓ Gradle 8.10 (via wrapper)
```

### Run All Policy Tests
```bash
cd agent-android
./gradlew policy:test               # All policy module tests
./gradlew core:test                 # All core module tests (includes ComplexPolicyHandlerTest)
./gradlew allTests                  # Entire agent-android test suite
```

### Run Specific Test Class
```bash
./gradlew policy:test --tests "com.mdmesh.policy.security.SecurityPolicyFactoriesTest"
./gradlew core:test --tests "com.mdmesh.core.command.handlers.ComplexPolicyHandlerTest"
```

### Generate Coverage Report
```bash
./gradlew policy:jacocoTestReport core:jacocoTestReport
# Reports at: agent-android/policy/build/reports/jacoco/test/html/index.html
```

---

## Integration Testing Checklist

### Device Enrollment & Check-in
- [ ] Enroll Android device (minSdk 24)
- [ ] Verify device check-in includes capability matrix
- [ ] Check that `CapabilityRegistry.supportedPolicyKeys()` includes:
  - `usbDebug`, `factoryReset`, `unknownSources` (all APIs 18+)
  - `appBlock`, `appHide` (API 24+)
  - `adminRemoval` (API 24+)

### Policy Application (Device Side)

#### USB Debug Policy
- [ ] **Disable**: Queue `policy.apply` with `{"policy":"usbDebug","value":false}`
  - Verify: USB debugging disabled on device (Settings > Developer Options)
  - Verify: Logcat shows `addUserRestriction(DISALLOW_DEBUGGING_FEATURES)`
- [ ] **Enable**: Queue `{"policy":"usbDebug","value":true}`
  - Verify: USB debugging re-enabled
  - Verify: `clearUserRestriction` called

#### Factory Reset Policy
- [ ] **Disable**: `{"policy":"factoryReset","value":false}`
  - Verify: Settings > About > Factory Reset button disabled/hidden
- [ ] **Enable**: `{"policy":"factoryReset","value":true}`
  - Verify: Factory Reset button re-enabled

#### Unknown Sources Policy (API 28+ only)
- [ ] On API 28+ device: `{"policy":"unknownSources","value":false}`
  - Verify: Cannot install APKs from unknown sources
- [ ] Skip test on API 24-27 devices (factory should return null → capability not advertised)

#### App Block Policy
- [ ] **Block app**: `{"policy":"appBlock","packageName":"com.google.android.apps.maps","value":true}`
  - Verify: Maps app hidden from launcher (or blocked)
  - Verify: `setApplicationHidden` called with `true`
  - Verify: Can unblock with `value":false`
- [ ] **Multiple apps**: Queue sequential policies for 3+ apps
  - Verify: All blocked correctly

#### App Hide Policy
- [ ] Same tests as AppBlock (uses same API)

### Server-Side Validation

#### Command Queueing
- [ ] Console sends `policy.apply` command with correct JSON structure
- [ ] Server validates payload before storing in AgentCommand queue
- [ ] Server rejects malformed payloads (400 Bad Request or async error)

#### Capability Matrix
- [ ] Device check-in returns capability matrix with Phase 1 keys
- [ ] Console/server only shows policy options device supports
- [ ] Attempting to apply unsupported policy → device returns UNSUPPORTED (not error)

### Error Cases
- [ ] Device not Device Owner → `isDeviceOwnerApp()` fails → `PolicyOutcome.Failed`
- [ ] DPM call throws exception → caught in `runCatching` → returns `Failed(message)`
- [ ] JSON parsing fails (malformed payload) → handler catches → returns FAILED status
- [ ] Factory returns null (API too old) → policy not in map → UNSUPPORTED response

---

## Cross-API Validation Matrix

Test on these API levels:
| API Level | Code | USB Debug | Factory Reset | Unknown Sources | App Block | Admin Removal |
|-----------|------|-----------|---------------|-----------------|-----------|---------------|
| 24 (N)    | 24   | ✓ support | ✓ support     | ✗ unsupported   | ✓ support | ✓ support     |
| 28 (P)    | 28   | ✓ support | ✓ support     | ✓ support       | ✓ support | ✓ support     |
| 30 (R)    | 30   | ✓ support | ✓ support     | ✓ support       | ✓ support | ✓ support     |
| 33 (T)    | 33   | ✓ support | ✓ support     | ✓ support       | ✓ support | ✓ support     |
| 35        | 35   | ✓ support | ✓ support     | ✓ support       | ✓ support | ✓ support     |

---

## Capability Matrix Example

Device check-in on API 28 device should include:
```json
{
  "capabilities": {
    "policies": [
      {
        "key": "usbDebug",
        "minSdk": 18,
        "supported": true
      },
      {
        "key": "factoryReset",
        "minSdk": 18,
        "supported": true
      },
      {
        "key": "unknownSources",
        "minSdk": 28,
        "supported": true
      },
      {
        "key": "appBlock",
        "minSdk": 24,
        "supported": true
      },
      {
        "key": "appHide",
        "minSdk": 24,
        "supported": true
      },
      {
        "key": "adminRemoval",
        "minSdk": 24,
        "supported": true
      }
    ]
  }
}
```

---

## Troubleshooting

### Test Fails with "Cannot instantiate DpmHandle"
→ Mock is not properly configured. Check `@Mock` annotation presence in test class.

### Factory Returns null Unexpectedly
→ Check `Build.VERSION.SDK_INT`. If running on API < required, factory correctly returns null (this is expected!).

### "addUserRestriction not called"
→ Verify `dpm.isDeviceOwnerApp()` is mocked to return `true`. User restrictions only work for Device Owner.

### ComplexPolicyHandler Routes to Wrong Policy
→ Check payload contains `"policy"` key. Verify key value matches registered policy map keys exactly.

---

## Performance Considerations

- **Policy application latency**: Should be <100ms (synchronous DPM calls)
- **Check-in with capability matrix**: Should not exceed 500ms overhead (registry build is fast)
- **Handler dispatch**: Negligible (<5ms) — just HashMap lookup + method call

---

## Next Steps After Validation

1. ✓ Phase 1 unit tests pass
2. ✓ Integration tests pass on API 24, 28, 33, 35 devices
3. → Proceed to Phase 2: Internet scheduling implementation
