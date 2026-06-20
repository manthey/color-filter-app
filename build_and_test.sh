#!/bin/bash

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Set timeouts for slow operations (no KVM means slower emulation)
TIMEOUT_LONG=900         # 15 minutes
TIMEOUT_MEDIUM=600       # 10 minutes
TIMEOUT_SHORT=300        # 5 minutes

echo "=== Building Code ==="
echo ""

# Ensure gradlew exists
if [ ! -f gradlew ]; then
    echo "Error: gradlew not found!"
    exit 1
fi

# Function to download SDK command-line tools if not available
download_sdk_tools() {
    local sdk_dir="$1"
    local tools_path="$sdk_dir/cmdline-tools/latest/bin/sdkmanager"

    # Check if we already have the tools in the expected location
    if [ -f "$tools_path" ]; then
        echo "Android SDK tools already downloaded at $sdk_dir"
        return 0
    fi

    echo "Downloading Android SDK command-line tools..."

    local download_dest="/tmp/android-cmdline-tools.zip"
    rm -f "$download_dest"

    # Download the latest command line tools from Google
    if ! wget --quiet https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O "$download_dest" 2>&1; then
        echo "Failed to download Android SDK tools"
        return 1
    fi

    # Create the cmdline-tools directory with latest subdirectory
    rm -rf "$sdk_dir/cmdline-tools"
    mkdir -p "$sdk_dir/cmdline-tools/latest/bin"

    # Extract to a temporary location
    local extract_dir="$sdk_dir/.extract_temp"
    rm -rf "$extract_dir"
    mkdir -p "$extract_dir"

    unzip -q "$download_dest" -d "$extract_dir"

    # The extracted structure is cmdline-tools/bin/sdkmanager (no latest/ in download)
    if [ -f "$extract_dir/cmdline-tools/bin/sdkmanager" ]; then
        # Move all content from extracted cmdline-tools to sdk's cmdline-tools/latest/
        cp -r "$extract_dir/cmdline-tools/"* "$sdk_dir/cmdline-tools/latest/"
    else
        echo "Error: Unexpected SDK structure after extraction"
        rm -rf "$extract_dir"
        rm -f "$download_dest"
        return 1
    fi

    rm -rf "$extract_dir"
    rm -f "$download_dest"

    # Verify sdkmanager is in the expected location
    if [ ! -f "$tools_path" ]; then
        echo "Error: SDK tools not found after extraction at $tools_path"
        return 1
    fi

    echo "Android SDK tools downloaded and installed"
}

# Function to accept all licenses non-interactively
accept_licenses() {
    local sdk_path="$1"

    # Set ANDROID_HOME for this function
    export ANDROID_HOME="$sdk_path"

    local sdkmanager=""
    if [ -f "$sdk_path/cmdline-tools/latest/bin/sdkmanager" ]; then
        sdkmanager="$sdk_path/cmdline-tools/latest/bin/sdkmanager"
    elif [ -f "$sdk_path/cmdline-tools/bin/sdkmanager" ]; then
        sdkmanager="$sdk_path/cmdline-tools/bin/sdkmanager"
    fi

    if [ ! -f "$sdkmanager" ]; then
        return 0  # Skip license acceptance if no sdkmanager
    fi

    mkdir -p "$sdk_path/licenses"

    # Check for unaccepted licenses
    local license_files
    license_files=$(ls "$sdk_path/licenses/"* 2>/dev/null | wc -l)

    if [ "$license_files" -eq 0 ] || grep -q "UNACCEPTED" "$sdk_path/licenses/*" 2>/dev/null; then
        echo "Accepting SDK licenses..."

        # Use yes to pipe 'y' for all license prompts, with timeout
        Timeout $TIMEOUT_MEDIUM bash -c "(yes y 2>/dev/null || true) | $sdkmanager --licenses" 2>&1 || {
            echo "Warning: Some licenses may not have been accepted"
        }
    fi
}

# Function to install required SDK packages
install_sdk_packages() {
    local sdk_path="$1"

    # Set ANDROID_HOME temporarily for this function
    export ANDROID_HOME="$sdk_path"
    export ANDROID_SDK_ROOT="$sdk_path"

    # Use sdkmanager to install (check both possible locations)
    local sdkmanager=""
    if [ -f "$sdk_path/cmdline-tools/latest/bin/sdkmanager" ]; then
        sdkmanager="$sdk_path/cmdline-tools/latest/bin/sdkmanager"
    elif [ -f "$sdk_path/cmdline-tools/bin/sdkmanager" ]; then
        sdkmanager="$sdk_path/cmdline-tools/bin/sdkmanager"
    fi

    if [ ! -f "$sdkmanager" ]; then
        echo "Error: sdkmanager not found in SDK at $sdk_path"
        return 1
    fi

    # Accept licenses first
    accept_licenses "$sdk_path"

    # Install platform-tools if needed (it's already installed but make sure)
    if [ ! -f "$sdk_path/platform-tools/adb" ]; then
        echo "Installing Android SDK Platform-Tools..."
        timeout $TIMEOUT_MEDIUM bash -c "(yes y 2>/dev/null || true) | $sdkmanager platform-tools" 2>&1 || {
            echo "Warning: Could not install platform-tools via sdkmanager"
        }
    fi

    # Install build tools (need as per the project)
    local build_tools_version="36.0.0"
    if [ ! -d "$sdk_path/build-tools/$build_tools_version" ]; then
        echo "Installing Android build tools $build_tools_version..."
        timeout $TIMEOUT_MEDIUM bash -c "(yes y 2>/dev/null || true) | $sdkmanager 'build-tools;$build_tools_version'" 2>&1 || {
            echo "Warning: Could not install build-tools via sdkmanager"
        }
    fi

    # Install platform android-36 (compileSdk)
    if [ ! -d "$sdk_path/platforms/android-36" ]; then
        echo "Installing Android API level 36..."
        timeout $TIMEOUT_MEDIUM bash -c "(yes y 2>/dev/null || true) | $sdkmanager 'platforms;android-36'" 2>&1 || {
            echo "Warning: Could not install platforms android-36 via sdkmanager"
        }
    fi

    # Install platform android-30 (minSdk)
    if [ ! -d "$sdk_path/platforms/android-30" ]; then
        echo "Installing Android API level 30..."
        timeout $TIMEOUT_MEDIUM bash -c "(yes y 2>/dev/null || true) | $sdkmanager 'platforms;android-30'" 2>&1 || {
            echo "Warning: Could not install platforms android-30 via sdkmanager"
        }
    fi

    # Install system images for emulator (x86_64 for faster emulation without KVM)
    local system_image="system-images;android-30;default;x86_64"
    if [ ! -d "$sdk_path/system-images/android-30/default/x86_64" ]; then
        echo "Installing Android x86_64 system image for API 30..."
        timeout $TIMEOUT_LONG bash -c "(yes y 2>/dev/null || true) | $sdkmanager '$system_image'" 2>&1 || {
            echo "Warning: Could not install system image via sdkmanager"
        }
    fi

    # Install emulator if not present
    if [ ! -f "$sdk_path/emulator/emulator" ]; then
        echo "Installing Android Emulator..."
        timeout $TIMEOUT_LONG bash -c "(yes y 2>/dev/null || true) | $sdkmanager emulator" 2>&1 || {
            echo "Warning: Could not install emulator via sdkmanager"
        }
    fi

    return 0
}



# Function to create an AVD
create_avd() {
    local sdk_path="$1"
    local avd_name="test_device"
    local target_api=30

    # Set ANDROID_HOME for emulator tools
    export ANDROID_HOME="$sdk_path"

    # Ensure system image is installed before creating AVD
    if [ ! -d "$sdk_path/system-images/android-$target_api/default/x86_64" ]; then
        echo "System image not found, installing..."
        install_sdk_packages "$sdk_path"
    fi

    local avdmanager="$sdk_path/cmdline-tools/latest/bin/avdmanager"
    if [ ! -f "$avdmanager" ]; then
        avdmanager="$sdk_path/cmdline-tools/bin/avdmanager"
    fi

    if [ ! -f "$avdmanager" ]; then
        echo "Error: avdmanager not found in SDK"
        return 1
    fi

    # Check if AVD already exists and is valid
    if [ -d "$ANDROID_HOME/avd/$avd_name.avd" ] && [ -f "$sdk_path/system-images/android-$target_api/default/x86_64/package.xml" ]; then
        echo "AVD $avd_name already exists"
        return 0
    fi

    # Remove old AVD if it exists but is invalid
    rm -rf "$ANDROID_HOME/avd/$avd_name.avd"

    echo "Creating Android Virtual Device: $avd_name"

    # Create AVD with timeout and non-interactive input
    local avd_output
    avd_output=$(timeout $TIMEOUT_MEDIUM bash -c "(yes y 2>/dev/null || true) | \"$avdmanager\" create avd -n \"$avd_name\" -k \"system-images;android-$target_api;default;x86_64\" -d pixel_4 --force" 2>&1) || {
        local exit_code=$?
        echo "Warning: Could not create AVD with avdmanager (exit code: $exit_code)"
        echo "$avd_output"

        # Try manual AVD creation
        mkdir -p "$ANDROID_HOME/avd"

        cat > "$ANDROID_HOME/avd/$avd_name.avd/config.ini" <<AVDCONF
abi.type=x86_64
avd.ini.displayname=$avd_name
avd.ini.encoding=UTF-8
disk.dataPartition.size=800M
hw.accelerometer=yes
hw.audioInput=yes
hw.camera.back=emulated
hw.camera.front=none
hw.cpu.arch=x86_64
hw.gpu.mode=swiftshader_indirect
hw.lcd.density=480
hw.lcd.height=1920
hw.lcd.width=1080
hw.ramSize=1536
image.sysdir.legacy=system-images/android-$target_api/default/x86_64/
screen.touch=yes
vm.heapSize=256
AVDCONF

        cat > "$ANDROID_HOME/avd/$avd_name.avd/hardwareProfile.ini" <<HWCONF
[Screen]
screen.display.name=pixel_4

[Hardware]
hw.device.manufacturer = Google
hw.device.name = pixel_4
hw.cpu.model = generic
hw.cpu.ncore = 2
hw.ramSize = 1536
HWCONF

        # Create the parent directory AVD descriptor file
        cat > "$ANDROID_HOME/avd/$avd_name.ini" <<AVDINI
path=$ANDROID_HOME/avd/$avd_name.avd
path.relative=$avd_name.avd
pw_hash=
AVDINI
    }

    # Verify all required files exist
    if [ ! -f "$ANDROID_HOME/avd/$avd_name.ini" ] || [ ! -f "$ANDROID_HOME/avd/$avd_name.avd/config.ini" ] || [ ! -f "$ANDROID_HOME/avd/$avd_name.avd/hardwareProfile.ini" ]; then
        echo "Error: Failed to create AVD"
        return 1
    fi

    return 0
}

# Function to start the emulator
start_emulator() {
    local sdk_path="$1"
    local avd_name="test_device"

    # Set ANDROID_HOME for emulator
    export ANDROID_HOME="$sdk_path"

    local emulator="$sdk_path/emulator/emulator"
    if [ ! -f "$emulator" ]; then
        echo "Error: emulator binary not found at $emulator"
        return 1
    fi

    # Ensure adb is in PATH using full path
    local adb_path="$sdk_path/platform-tools/adb"

    echo "Starting Android Emulator for AVD: $avd_name..."

    # Start emulator with NO ACCELERATION (no KVM support required)
    # Use swiftshader for GPU and disable snapshot to avoid state issues
    "$emulator" "@$avd_name" \
        -no-window \
        -gpu swiftshader_indirect \
        -no-audio \
        -no-boot-anim \
        -memory 1536 \
        -no-accel \
        -no-snapshot \
        -scale 0.75 &

    EMULATOR_PID=$!

    echo "Emulator started with PID: $EMULATOR_PID"

    # Wait for emulator to fully boot
    echo "Waiting for emulator to boot..."
    local max_wait=600  # 10 minutes (very slow without KVM acceleration)
    local waited=0

    while ! "$adb_path" devices | grep -qE "(device|emulator)$" && [ $waited -lt $max_wait ]; do
        sleep 5
        waited=$((waited + 5))
        echo "Waited ${waited}s for emulator..."

        if ! kill -0 $EMULATOR_PID 2>/dev/null; then
            echo "Error: Emulator process died"
            return 1
        fi
    done

    if [ $waited -ge $max_wait ]; then
        echo "Warning: Emulator did not boot within timeout, continuing anyway..."
    fi

    # Ensure device is properly connected via ADB (handles offline state)
    echo "Waiting for ADB connection to fully establish..."
    "$adb_path" wait-for-device 2>/dev/null || true

    echo "Device ready, waiting for system services..."

    # Wait for package service to be available (can take time with no-accel)
    local service_wait=300
    local serv_waited=0
    while [ $serv_waited -lt $service_wait ]; do
        sleep 5
        serv_waited=$((serv_waited + 5))

        # Try a simple command to see if device is responsive
        if "$adb_path" shell echo test_device_ready 2>/dev/null | grep -q "test_device_ready"; then
            break
        fi

        if [ $((serv_waited % 30)) -eq 0 ]; then
            echo "Waited ${serv_waited}s for system services..."
        fi
    done

    echo "System services ready (waited ${serv_waited}s)"
    echo "System services ready (waited ${serv_waited}s)"

    # Unlock the emulator screen (sometimes needed for tests)
    "$adb_path" shell input keyevent 82 2>/dev/null || true
    sleep 3

    return 0
}

# Determine Android SDK path
SDK_PATH=""

# Check if ANDROID_HOME is set and valid with required components
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    # Check if it has sdkmanager or the old tools structure
    if [ -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ] || [ -f "$ANDROID_HOME/cmdline-tools/bin/sdkmanager" ]; then
        SDK_PATH="$ANDROID_HOME"
        echo "Using ANDROID_HOME with cmdline tools: $SDK_PATH"
    elif [ -d "$ANDROID_HOME/tools" ] && [ -d "$ANDROID_HOME/platform-tools" ]; then
        # Old SDK structure - need to add cmdline tools, but only if we can write to it
        if [ -w "$ANDROID_HOME" ]; then
            SDK_PATH="$ANDROID_HOME"
            echo "Using existing SDK at $SDK_PATH (will enhance with cmdline tools)"
        else
            echo "Warning: Cannot write to ANDROID_HOME, will create SDK in project directory"
            SDK_PATH=""
        fi
    else
        echo "Warning: ANDROID_HOME set but doesn't have required components"
    fi
fi

# If we still don't have SDK path, check local.properties
if [ -z "$SDK_PATH" ]; then
    if grep -q "^sdk.dir" local.properties 2>/dev/null; then
        # Extract sdk.dir from local.properties and convert Windows paths if needed
        temp_path=$(grep "^sdk.dir" local.properties | cut -d'=' -f2- | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//' | tr -d '\r')

        # Convert Windows path separators (C:\path -> /mnt/c/path for WSL)
        temp_path=$(echo "$temp_path" | sed 's/\\/\//g' | sed 's/^\/\([A-Za-z]\):\/\(.*\)$/\/mnt\/\L\1\E\/\2/')

        if [ ! -d "$temp_path" ]; then
            echo "Warning: Android SDK not found at path from local.properties"
        else
            # Verify it has the minimum required components and is writable
            if [ -f "$temp_path/cmdline-tools/latest/bin/sdkmanager" ] || [ -f "$temp_path/cmdline-tools/bin/sdkmanager" ] || [ -d "$temp_path/tools" ]; then
                if [ -w "$temp_path" ]; then
                    SDK_PATH="$temp_path"
                    echo "Using sdk.dir from local.properties: $SDK_PATH"
                else
                    echo "Warning: Cannot write to SDK path from local.properties"
                fi
            fi
        fi
    fi
fi

# If still no SDK, try to find a system-wide install that is writable
if [ -z "$SDK_PATH" ] && [ -d "/usr/lib/android-sdk" ]; then
    if [ -f "/usr/lib/android-sdk/cmdline-tools/latest/bin/sdkmanager" ] || [ -f "/usr/lib/android-sdk/cmdline-tools/bin/sdkmanager" ]; then
        if [ -w "/usr/lib/android-sdk" ]; then
            SDK_PATH="/usr/lib/android-sdk"
            echo "Found system Android SDK with cmdline tools at: $SDK_PATH"
        else
            echo "Warning: Cannot write to /usr/lib/android-sdk, will create SDK in project directory"
        fi
    elif [ -d "/usr/lib/android-sdk/tools" ] || [ -d "/usr/lib/android-sdk/platform-tools" ]; then
        if [ -w "/usr/lib/android-sdk" ]; then
            SDK_PATH="/usr/lib/android-sdk"
            echo "Found system Android SDK (will enhance) at: $SDK_PATH"
        else
            echo "Warning: Cannot write to /usr/lib/android-sdk, will create SDK in project directory"
        fi
    fi
fi

# If no SDK found or incomplete/writable, download and set up our own
if [ -z "$SDK_PATH" ]; then
    # Use .android-sdk in project directory as cache
    SDK_PATH="$SCRIPT_DIR/.android-sdk"

    echo "No writable Android SDK found. Setting up Android SDK at: $SDK_PATH"

    # Create the sdk directory
    mkdir -p "$SDK_PATH"

    # Download and install command-line tools
    if ! download_sdk_tools "$SDK_PATH"; then
        echo "Error: Failed to download Android SDK tools"
        exit 1
    fi

    # Install required packages (this will be done interactively for licenses)
    install_sdk_packages "$SDK_PATH" || {
        echo "Warning: Some SDK packages could not be installed automatically."
    }
elif [ ! -f "$SDK_PATH/cmdline-tools/latest/bin/sdkmanager" ] && [ ! -f "$SDK_PATH/cmdline-tools/bin/sdkmanager" ]; then
    # Existing SDK but missing cmdline tools - need to add them (only if writable)
    if [ ! -w "$SDK_PATH" ]; then
        echo "Error: Cannot write to existing SDK at $SDK_PATH"
        exit 1
    fi

    if [ -d "$SDK_PATH/tools" ] && [ -z "$(ls -A "$SDK_PATH/cmdline-tools" 2>/dev/null)" ]; then
        echo "Enhancing existing SDK with Android command-line tools..."

        # Download and install command-line tools
        if ! download_sdk_tools "$SDK_PATH"; then
            echo "Error: Failed to download Android SDK tools"
            exit 1
        fi

        # Install required packages
        install_sdk_packages "$SDK_PATH" || {
            echo "Warning: Some SDK packages could not be installed automatically."
        }
    fi
fi

# Final verification
echo "Using Android SDK: $SDK_PATH"

if [ ! -d "$SDK_PATH" ]; then
    echo "Error: Android SDK directory not found at: $SDK_PATH"
    exit 1
fi

if [ ! -f "$SDK_PATH/cmdline-tools/latest/bin/sdkmanager" ] && [ ! -f "$SDK_PATH/cmdline-tools/bin/sdkmanager" ] && [ ! -d "$SDK_PATH/tools" ]; then
    echo "Error: sdkmanager or tools not found in SDK"
    exit 1
fi

# Ensure required SDK components exist (they may need to be installed)
if [ ! -d "$SDK_PATH/platforms/android-30" ] || [ ! -d "$SDK_PATH/platforms/android-36" ]; then
    echo "Installing missing Android API levels..."
    install_sdk_packages "$SDK_PATH"
fi

# Verify final state - platform 30 should exist for minSdk
if [ ! -d "$SDK_PATH/platforms/android-30" ]; then
    echo "Error: Android API level 30 not found after installation attempt"
    exit 1
fi

echo ""
echo "=== Syncing Dependencies ==="

# Export ANDROID_HOME so gradle can find it
export ANDROID_HOME="$SDK_PATH"
export ANDROID_SDK_ROOT="$SDK_PATH"

# Also create/update local.properties with the correct path
cat > /tmp/sdk_path_update.sh << 'PATHEOF'
#!/bin/bash
SCRIPT_DIR="$SCRIPT_DIR"
SDK_PATH="$1"
local_props_file="local.properties"

# Convert WSL path back to Windows style if needed
if [[ "$SDK_PATH" == /mnt/* ]]; then
    drive_letter=$(echo "$SDK_PATH" | sed 's|^/mnt/||' | cut -c1)
    rest_of_path=$(echo "$SDK_PATH" | sed 's|^/mnt/[a-zA-Z]||')
    sdk_line="sdk.dir=${drive_letter}:\\\\${rest_of_path//\//\\\\}"
else
    sdk_line="sdk.dir=$SDK_PATH"
fi

if grep -q "^sdk.dir" "$local_props_file"; then
    sed -i "s|^sdk.dir=.*|$sdk_line|" "$local_props_file"
else
    echo "$sdk_line" >> "$local_props_file"
fi
PATHEOF

bash /tmp/sdk_path_update.sh "$SDK_PATH"

# Gradle will automatically download and sync all required dependencies during build
./gradlew --refresh-dependencies 2>&1 || true

# Export ANDROID_HOME for subsequent commands
export ANDROID_HOME="$SDK_PATH"
export ADB_TRACE=1  # Enable adb tracing for debugging
export ANDROID_SDK_ROOT="$SDK_PATH"

# Ensure adb is available in PATH using full path
if ! command -v adb &> /dev/null; then
    if [ -f "$SDK_PATH/platform-tools/adb" ]; then
        export PATH="$PATH:$SDK_PATH/platform-tools"
        echo "Added platform-tools to PATH"
    fi
fi

echo ""
echo "=== Building project (skip tests) ==="
./gradlew --warning-mode all build -x test --parallel --daemon || {
    echo "Warning: Build had issues but continuing..."
}

# Ensure device is connected or emulator is running
echo ""
echo "=== Starting Android Emulator (CPU emulation mode, no KVM) ==="

# Set environment for emulator tools
export PATH="$SDK_PATH/cmdline-tools/latest/bin:$PATH"
export PATH="$SDK_PATH/platform-tools:$PATH"

# Kill any existing emulators first
pkill -f "emulator.*@test_device" 2>/dev/null || true
sleep 5

# Kill and restart ADB server to clear any stale connections
adb kill-server 2>/dev/null || true
sleep 3
adb start-server 2>/dev/null || true
sleep 5
# Create AVD if needed (only if no devices are connected)
device_list=$(adb devices 2>/dev/null || echo "")
if ! echo "$device_list" | grep -qE "^.*\s(device|emulator)$"; then
    create_avd "$SDK_PATH" || {
        echo "Warning: Could not create AVD, continuing anyway..."
    }

    # If still no device, start emulator
    device_list=$(adb devices 2>/dev/null || echo "")
    if ! echo "$device_list" | grep -qE "^.*\s(device|emulator)$"; then
        start_emulator "$SDK_PATH" || {
            echo "Error: Failed to start emulator"
            exit 1
        }
    fi
fi

# Final check for device and handle offline state
echo ""
echo "=== Checking for Android devices ==="
adb devices 2>&1 | head -5

# Restart ADB if we see offline devices
if adb devices 2>/dev/null | grep -q "offline"; then
    echo "Devices in offline state, reconnecting..."
    adb kill-server 2>/dev/null || true
    sleep 3
    adb start-server 2>/dev/null || true
    sleep 5
fi

# Start logcat in background for capturing output during tests
echo "=== Starting logcat in background ==="
adb logcat -c 2>/dev/null || true
adb logcat *:V > logcat.txt &
LOGCAT_PID=$!

# Wait before running tests to ensure everything is settled
adb wait-for-device 2>/dev/null || true
sleep 5

# Run instrumented tests and generate coverage report
echo "=== Running instrumented tests with coverage ==="
./gradlew --warning-mode all connectedDebugAndroidTest createDebugCoverageReport || {
    echo "Warning: Instrumented tests had failures, but continuing to collect coverage"
}

# Stop logcat process
kill $LOGCAT_PID 2>/dev/null || true

# Print code coverage summary (similar to GitHub workflow)
echo ""
echo "=== Code Coverage Summary ==="
COVERAGE_FILE=$(find app/build/reports -name "report.xml" | head -1)
if [ -f "$COVERAGE_FILE" ]; then
    covered=$(grep -oP 'covered="\K[0-9]+' "$COVERAGE_FILE" | awk '{s+=$1} END {print s}')
    missed=$(grep -oP 'missed="\K[0-9]+' "$COVERAGE_FILE" | awk '{s+=$1} END {print s}')
    total=$((covered + missed))
    if [ $total -gt 0 ]; then
        percentage=$(awk "BEGIN {printf \"%.2f\", 100*$covered/$total}")
        echo "$covered of $total statements covered ($percentage%)"
    else
        echo "No coverage data available."
    fi
else
    echo "Jacoco coverage file not found."
fi

# Show relevant logcat output (filtered for colorfilter)
echo ""
echo "=== Logcat Output (filtered for colorfilter) ==="
if [ -f "logcat.txt" ]; then
    grep "colorfilter" logcat.txt | tail -20 || echo "No colorfilter-related logcat entries found"
else
    echo "Logcat file not created."
fi

echo ""
echo "=== Build and Test Complete ==="
