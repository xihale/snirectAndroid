#!/bin/bash
set -e

# 1. Compile Go Core
echo "Compiling Go Core..."
cd core
gomobile bind -v -ldflags="-s -w" -trimpath -target=android/arm64 -androidapi 21 -o ../android/app/libs/core.aar . >/dev/null || {
  echo "Go compilation failed"
  exit 1
}
cd ..

# 2. Build and Install Android App
echo "Building and Installing Android App..."
cd android
./gradlew installDebug >/dev/null || {
  echo "Android build failed"
  exit 1
}
cd ..

# 3. Launch the app
echo "Launching Snirect (Restarting Process)..."
adb shell am force-stop com.xihale.snirect
adb shell am start -n com.xihale.snirect/com.xihale.snirect.MainActivity >/dev/null

echo "Waiting for VPN to establish..."
sleep 2

# 4. Verify Connectivity via ADB (Requires device connected)
echo "----------------------------------------"
echo "VERIFYING CONNECTIVITY via ADB shell curl"
echo "----------------------------------------"

# Helper function for curl check
check_url() {
    local url=$1
    local expected=$2
    echo "Checking $url (Expected: $expected)..."
    # Using -k because Android curl might not have updated CA store.
    # We are testing connectivity here.
    adb shell "curl -k -I -m 5 -s -o /dev/null -w '%{http_code}' '$url'" || echo "FAIL (Command Error)"
    echo ""
}

# Check Google (Should be 200 or 301/302)
check_url "https://www.google.com" "200/3xx"

# Check DuckDuckGo (Should be 200 or 301/302)
check_url "https://duckduckgo.com" "200/3xx"

# Check Pixiv (Should be 200 or 301/302)
check_url "https://www.pixiv.net" "200/3xx"

echo "----------------------------------------"
echo "Monitoring logs for 10 seconds..."
timeout 10 adb logcat -v time -s Snirect:* GoLog:*
echo "Monitoring finished."
