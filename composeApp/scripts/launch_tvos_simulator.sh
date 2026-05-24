#!/usr/bin/env bash
# launch_tvos_simulator.sh
# Builds VolumeStreamShared + tvOSApp, then installs and launches on a tvOS simulator.
# Usage:
#   TVOS_SIMULATOR="tvOS Simulator" ./launch_tvos_simulator.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOFTWARE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

SIMULATOR="${TVOS_SIMULATOR:-tvOS Simulator}"
SCHEME="${TVOS_SCHEME:-tvOSApp}"
PROJECT_PATH="$SOFTWARE_DIR/tvOSApp/tvOSApp.xcodeproj"
DERIVED_DATA="$SOFTWARE_DIR/build/tvos_derived/script"
APP_PATH="$DERIVED_DATA/Build/Products/Debug-appletvsimulator/$SCHEME.app"

# ── Resolve simulator UDID ────────────────────────────────────────────────────
SIMCTL_JSON="$(xcrun simctl list devices available -j)"
SIMULATOR_UDID="$(SIMCTL_JSON="$SIMCTL_JSON" python3 - "$SIMULATOR" <<'PY'
import json, os, sys
requested = (sys.argv[1] if len(sys.argv) > 1 else "").strip()
data = json.loads(os.environ["SIMCTL_JSON"])
tv_devices = []
for runtime, devices in data.get("devices", {}).items():
    if "tvOS" not in runtime:
        continue
    for d in devices:
        if d.get("isAvailable"):
            tv_devices.append(d)
picked = next((d for d in tv_devices if d.get("udid","").lower() == requested.lower()), None)
if picked is None:
    picked = next((d for d in tv_devices if d.get("name","").lower() == requested.lower()), None)
if picked is None:
    picked = next((d for d in tv_devices if d.get("state") == "Booted"), None)
if picked is None and tv_devices:
    picked = tv_devices[0]
if picked is None:
    sys.stderr.write("No available tvOS simulators found.\n"); sys.exit(2)
print(picked["udid"])
PY
)"

if [[ -z "$SIMULATOR_UDID" ]]; then
    echo "Failed to resolve a tvOS simulator UDID." >&2; exit 2
fi
echo "Using tvOS simulator: $SIMULATOR (UDID=$SIMULATOR_UDID)"

# ── Boot simulator ────────────────────────────────────────────────────────────
xcrun simctl boot "$SIMULATOR_UDID" >/dev/null 2>&1 || true
xcrun simctl bootstatus "$SIMULATOR_UDID" -b
open -a Simulator

# ── Build (xcodebuild calls Gradle Compile Kotlin phase automatically) ────────
echo "▶ Building tvOSApp..."
xcodebuild \
  -project "$PROJECT_PATH" \
  -scheme "$SCHEME" \
  -destination "platform=tvOS Simulator,id=$SIMULATOR_UDID" \
  -configuration Debug \
  -derivedDataPath "$DERIVED_DATA" \
  build

# ── Install + Launch ──────────────────────────────────────────────────────────
EFFECTIVE_APP_PATH="$APP_PATH"
if [[ ! -d "$EFFECTIVE_APP_PATH" ]]; then
    EFFECTIVE_APP_PATH="$(find "$DERIVED_DATA/Build/Products/Debug-appletvsimulator" -maxdepth 1 -type d -name '*.app' | head -n1)"
fi
if [[ -z "$EFFECTIVE_APP_PATH" || ! -d "$EFFECTIVE_APP_PATH" ]]; then
    echo "Built .app not found under $DERIVED_DATA/Build/Products/Debug-appletvsimulator" >&2; exit 3
fi

EFFECTIVE_BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print:CFBundleIdentifier' "$EFFECTIVE_APP_PATH/Info.plist" 2>/dev/null || true)"
if [[ -z "$EFFECTIVE_BUNDLE_ID" ]]; then EFFECTIVE_BUNDLE_ID="${TVOS_BUNDLE_ID:-com.vdigital.volumestream.tvos}"; fi

echo "▶ Installing $EFFECTIVE_APP_PATH..."
xcrun simctl install "$SIMULATOR_UDID" "$EFFECTIVE_APP_PATH"
echo "▶ Launching $EFFECTIVE_BUNDLE_ID..."
xcrun simctl launch "$SIMULATOR_UDID" "$EFFECTIVE_BUNDLE_ID"
echo "✅ tvOSApp launched on $SIMULATOR"

