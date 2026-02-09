#!/bin/bash
set -e

# 1. Compile Go Core
echo "Compiling Go Core..."
cd core
gomobile bind -v -target=android -androidapi 21 -o ../android/app/libs/core.aar . >/dev/null || {
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

echo "Monitoring logs..."
timeout 5 adb logcat -s Snirect:* GoLog:*
echo "Monitoring finished."
