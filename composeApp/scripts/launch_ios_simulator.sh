#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOFTWARE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Supports both new IOS_* env vars and legacy names from older IDE tasks.
SIMULATOR="${IOS_SIMULATOR:-${SIMULATOR:-iPhone 16e}}"
SCHEME="${IOS_SCHEME:-${SCHEME:-iosApp}}"
BUNDLE_ID="${IOS_BUNDLE_ID:-${BUNDLE_ID:-com.vdigital.volumestream.VolumeStream}}"
PROJECT_PATH="${IOS_PROJECT_PATH:-${PROJECT_PATH:-$SOFTWARE_DIR/iosApp/iosApp.xcodeproj}}"
DERIVED_DATA="${IOS_DERIVED_DATA:-${DERIVED_DATA:-$SOFTWARE_DIR/build/ios_derived/script}}"
APP_PATH="${IOS_APP_PATH:-${APP_PATH:-$DERIVED_DATA/Build/Products/Debug-iphonesimulator/$SCHEME.app}}"

if [[ ! -d "$PROJECT_PATH" ]]; then
    echo "iOS Xcode project not found at: $PROJECT_PATH" >&2
    exit 2
fi

SIMCTL_JSON="$(xcrun simctl list devices available -j)"
SIMULATOR_UDID="$(SIMCTL_JSON="$SIMCTL_JSON" /usr/bin/python3 - "$SIMULATOR" <<'PY'
import json
import os
import sys

requested = (sys.argv[1] if len(sys.argv) > 1 else "").strip()
data = json.loads(os.environ["SIMCTL_JSON"])

ios_devices = []
for runtime, devices in data.get("devices", {}).items():
    if "iOS" not in runtime:
        continue
    for d in devices:
        if d.get("isAvailable"):
            ios_devices.append(d)

def pick_requested():
    if not requested:
        return None
    req_lower = requested.lower()
    for d in ios_devices:
        if d.get("udid", "").lower() == req_lower:
            return d
    for d in ios_devices:
        if d.get("name", "").lower() == req_lower:
            return d
    return None

picked = pick_requested()
if picked is None:
    booted = [d for d in ios_devices if d.get("state") == "Booted"]
    if booted:
        picked = booted[0]
if picked is None and ios_devices:
    picked = ios_devices[0]

if picked is None:
    sys.stderr.write("No available iOS simulators found.\n")
    sys.exit(2)

print(picked["udid"])
PY
)"

if [[ -z "$SIMULATOR_UDID" ]]; then
    echo "Failed to resolve an iOS simulator UDID." >&2
    exit 2
fi

echo "Using iOS simulator: $SIMULATOR (UDID=$SIMULATOR_UDID)"
xcrun simctl boot "$SIMULATOR_UDID" >/dev/null 2>&1 || true
xcrun simctl bootstatus "$SIMULATOR_UDID" -b
open -a Simulator

echo "▶ Building $SCHEME..."
xcodebuild \
  -project "$PROJECT_PATH" \
  -scheme "$SCHEME" \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
  -derivedDataPath "$DERIVED_DATA" \
  build

EFFECTIVE_APP_PATH="$APP_PATH"
if [[ ! -d "$EFFECTIVE_APP_PATH" ]]; then
    EFFECTIVE_APP_PATH="$(find "$DERIVED_DATA/Build/Products/Debug-iphonesimulator" -maxdepth 1 -type d -name '*.app' | head -n 1)"
fi
if [[ -z "$EFFECTIVE_APP_PATH" || ! -d "$EFFECTIVE_APP_PATH" ]]; then
    echo "Built .app was not found under $DERIVED_DATA/Build/Products/Debug-iphonesimulator" >&2
    exit 3
fi


EFFECTIVE_BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print:CFBundleIdentifier' "$EFFECTIVE_APP_PATH/Info.plist" 2>/dev/null || true)"
if [[ -z "$EFFECTIVE_BUNDLE_ID" ]]; then
    EFFECTIVE_BUNDLE_ID="$BUNDLE_ID"
fi

echo "▶ Installing $EFFECTIVE_APP_PATH..."
xcrun simctl install "$SIMULATOR_UDID" "$EFFECTIVE_APP_PATH"
echo "▶ Launching $EFFECTIVE_BUNDLE_ID..."
xcrun simctl launch "$SIMULATOR_UDID" "$EFFECTIVE_BUNDLE_ID"
echo "✅ iOS app launched on $SIMULATOR"

