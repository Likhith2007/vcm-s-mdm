#!/bin/bash
# Phase 1 Validation Script
# Run this on a machine with JDK 17 + Android SDK to validate Phase 1 implementation

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=== Phase 1 MDMesh Validation ==="
echo ""

# Check prerequisites
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Install JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | grep -oE 'version "[^"]*"' | cut -d' ' -f2 | cut -d'.' -f1)
if [[ $JAVA_VERSION -lt 17 ]]; then
    echo "❌ Java version $JAVA_VERSION detected. Require JDK 17+"
    exit 1
fi

echo "✓ Java $(java -version 2>&1 | head -1)"
echo ""

# Build Android agent
echo "=== Building Android Agent ==="
cd agent-android
./gradlew clean

# Compile all modules (without running tests yet)
echo "Compiling policy module..."
./gradlew policy:compileDebugKotlin

echo "Compiling core module..."
./gradlew core:compileDebugKotlin

echo "Compiling app module..."
./gradlew app:compileDebugKotlin

echo "✓ All modules compile successfully"
echo ""

# Run unit tests
echo "=== Running Unit Tests ==="
echo ""

echo "Testing policy module (security + app policies)..."
./gradlew policy:test --info

echo ""
echo "Testing core module (handlers)..."
./gradlew core:test --info

echo ""
echo "Testing all modules..."
./gradlew allTests --info

echo ""
echo "=== Test Summary ==="
TEST_RESULTS=$(./gradlew -q testReport 2>&1 || echo "")

if [[ $? -eq 0 ]]; then
    echo "✓ All tests passed!"
else
    echo "⚠ Some tests failed. Review output above."
    exit 1
fi

echo ""
echo "=== Build APK ==="
./gradlew :app:assembleDebug
echo "✓ APK built: agent-android/app/build/outputs/apk/debug/app-debug.apk"

echo ""
echo "=== Phase 1 Validation Complete ==="
echo ""
echo "Next steps:"
echo "1. Deploy APK to test device (API 24-35)"
echo "2. Enroll device and verify capabilities are advertised"
echo "3. Test each policy via console:"
echo "   - USB Debug disable/enable"
echo "   - Factory Reset disable/enable"
echo "   - Unknown Sources disable (API 28+ only)"
echo "   - App Block/Hide with package name"
echo "4. Verify device restrictions take effect"
echo ""
echo "If all tests pass, proceed to Phase 2: Internet Scheduling"
