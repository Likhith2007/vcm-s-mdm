@echo off
REM Phase 1 Validation Script (Windows)
REM Run this on a machine with JDK 17 + Android SDK to validate Phase 1 implementation

setlocal enabledelayedexpansion

echo ===================================
echo Phase 1 MDMesh Validation (Windows)
echo ===================================
echo.

REM Check prerequisites
java -version >nul 2>&1
if errorlevel 1 (
    echo Error: Java not found. Install JDK 17+
    exit /b 1
)

echo OK: Java installed
echo.

REM Navigate to agent-android
cd agent-android

echo ===================================
echo Building Android Agent
echo ===================================
echo.

echo Compiling policy module...
call gradlew.bat policy:compileDebugKotlin
if errorlevel 1 (
    echo Error: policy module compilation failed
    exit /b 1
)

echo Compiling core module...
call gradlew.bat core:compileDebugKotlin
if errorlevel 1 (
    echo Error: core module compilation failed
    exit /b 1
)

echo Compiling app module...
call gradlew.bat app:compileDebugKotlin
if errorlevel 1 (
    echo Error: app module compilation failed
    exit /b 1
)

echo OK: All modules compile successfully
echo.

echo ===================================
echo Running Unit Tests
echo ===================================
echo.

echo Testing policy module (security + app policies)...
call gradlew.bat policy:test
if errorlevel 1 (
    echo Warning: Some policy tests failed
)

echo.
echo Testing core module (handlers)...
call gradlew.bat core:test
if errorlevel 1 (
    echo Warning: Some core tests failed
)

echo.
echo Testing complete
echo.

echo ===================================
echo Building APK
echo ===================================
echo.

call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo Error: APK build failed
    exit /b 1
)

echo OK: APK built successfully
echo Location: agent-android\app\build\outputs\apk\debug\app-debug.apk
echo.

echo ===================================
echo Phase 1 Validation Complete
echo ===================================
echo.
echo Next steps:
echo 1. Deploy APK to test device (API 24-35)
echo 2. Enroll device and verify capabilities are advertised
echo 3. Test each policy via console:
echo    - USB Debug disable/enable
echo    - Factory Reset disable/enable
echo    - Unknown Sources disable (API 28+ only)
echo    - App Block/Hide with package name
echo 4. Verify device restrictions take effect
echo.
echo If all tests pass, proceed to Phase 2: Internet Scheduling
echo.

pause
