#!/bin/zsh
set -euo pipefail

: "${APK_PATH:?APK_PATH is required}"
: "${APP_COMPONENT:?APP_COMPONENT is required}"

PREFER_EMULATOR="${PREFER_EMULATOR:-false}"
AUTO_START_EMULATOR="${AUTO_START_EMULATOR:-false}"
CREATE_AVD_IF_MISSING="${CREATE_AVD_IF_MISSING:-false}"
ANDROID_AVD_NAME="${ANDROID_AVD_NAME:-}"
ANDROID_AVD_SYSTEM_IMAGE="${ANDROID_AVD_SYSTEM_IMAGE:-system-images;android-34;google_apis;x86_64}"
ANDROID_AVD_DEVICE="${ANDROID_AVD_DEVICE:-tv_1080p}"

adb_executable_name() {
  if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
    echo "adb.exe"
  else
    echo "adb"
  fi
}

resolve_adb() {
  if [[ -n "${ADB:-}" ]]; then
    echo "$ADB"
    return
  fi

  local adb_name
  adb_name="$(adb_executable_name)"

  local -a candidates
  candidates=()

  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    candidates+=("$ANDROID_SDK_ROOT/platform-tools/$adb_name")
  fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    candidates+=("$ANDROID_HOME/platform-tools/$adb_name")
  fi
  if [[ -n "${ANDROID_SDK_DIR_HINT:-}" ]]; then
    candidates+=("$ANDROID_SDK_DIR_HINT/platform-tools/$adb_name")
  fi

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done

  echo "$adb_name"
}

resolve_sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    echo "$ANDROID_SDK_ROOT"
    return
  fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    echo "$ANDROID_HOME"
    return
  fi
  if [[ -n "${ANDROID_SDK_DIR_HINT:-}" ]]; then
    echo "$ANDROID_SDK_DIR_HINT"
    return
  fi
  echo ""
}

resolve_emulator_bin() {
  local sdk_root="$1"
  if [[ -n "$sdk_root" && -x "$sdk_root/emulator/emulator" ]]; then
    echo "$sdk_root/emulator/emulator"
    return
  fi
  if command -v emulator >/dev/null 2>&1; then
    command -v emulator
    return
  fi
  echo ""
}

resolve_cmdline_tool() {
  local sdk_root="$1"
  local tool_name="$2"
  if [[ -n "$sdk_root" && -x "$sdk_root/cmdline-tools/latest/bin/$tool_name" ]]; then
    echo "$sdk_root/cmdline-tools/latest/bin/$tool_name"
    return
  fi
  if [[ -n "$sdk_root" ]]; then
    local tool
    tool="$(find "$sdk_root/cmdline-tools" -type f -name "$tool_name" 2>/dev/null | head -n 1 || true)"
    if [[ -n "$tool" ]]; then
      echo "$tool"
      return
    fi
  fi
  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return
  fi
  echo ""
}

create_avd_if_needed() {
  local sdk_root="$1"
  local emulator_bin="$2"
  local avd_name="$3"
  if [[ "$CREATE_AVD_IF_MISSING" != "true" ]]; then
    return 1
  fi

  local sdkmanager_bin avdmanager_bin
  sdkmanager_bin="$(resolve_cmdline_tool "$sdk_root" sdkmanager)"
  avdmanager_bin="$(resolve_cmdline_tool "$sdk_root" avdmanager)"

  if [[ -z "$sdkmanager_bin" || -z "$avdmanager_bin" ]]; then
    echo "Cannot create AVD '$avd_name': sdkmanager/avdmanager not found." >&2
    return 1
  fi

  echo "Creating AVD '$avd_name' using $ANDROID_AVD_SYSTEM_IMAGE..."
  yes | "$sdkmanager_bin" --install "$ANDROID_AVD_SYSTEM_IMAGE" >/dev/null
  echo "no" | "$avdmanager_bin" create avd -n "$avd_name" -k "$ANDROID_AVD_SYSTEM_IMAGE" --device "$ANDROID_AVD_DEVICE" --force >/dev/null

  if ! "$emulator_bin" -list-avds | grep -Fxq "$avd_name"; then
    echo "Failed to create AVD '$avd_name'." >&2
    return 1
  fi
  return 0
}

maybe_start_emulator() {
  local adb="$1"
  if [[ "$AUTO_START_EMULATOR" != "true" ]]; then
    return
  fi

  local sdk_root emulator_bin
  sdk_root="$(resolve_sdk_root)"
  emulator_bin="$(resolve_emulator_bin "$sdk_root")"
  if [[ -z "$emulator_bin" ]]; then
    echo "Auto-start requested but emulator binary was not found." >&2
    return
  fi

  local avd_name="$ANDROID_AVD_NAME"
  if [[ -z "$avd_name" ]]; then
    avd_name="$("$emulator_bin" -list-avds | head -n 1)"
  fi

  if [[ -z "$avd_name" ]]; then
    echo "No AVD available to launch." >&2
    return
  fi

  if ! "$emulator_bin" -list-avds | grep -Fxq "$avd_name"; then
    if ! create_avd_if_needed "$sdk_root" "$emulator_bin" "$avd_name"; then
      echo "AVD '$avd_name' not found and could not be created." >&2
      return
    fi
  fi

  echo "Starting emulator AVD: $avd_name"
  nohup "$emulator_bin" -avd "$avd_name" -no-snapshot-load -netdelay none -netspeed full >/tmp/volumestream-emulator.log 2>&1 &

  local serial booted
  for _ in {1..180}; do
    serial="$("$adb" devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1; exit}')"
    if [[ -n "$serial" ]]; then
      booted="$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
      if [[ "$booted" == "1" ]]; then
        echo "Emulator is booted: $serial"
        return
      fi
    fi
    sleep 1
  done

  echo "Timed out waiting for emulator boot completion." >&2
}

list_online_devices() {
  local adb="$1"
  # Standard USB:     <serial>   device
  # Wireless/mDNS:    <serial> (N)._adb-tls-connect._tcp   device   product:…
  # We print "<serial> device" whenever "device" appears in the line (any field).
  "$adb" devices | awk 'NR>1 && NF>=2 && /\bdevice\b/ {print $1 " device"}'
}

resolve_target_serial() {
  local adb="$1"
  local prefer_emulator="$2"
  local configured_serial="${ANDROID_SERIAL:-}"
  local devices_output
  devices_output="$(list_online_devices "$adb")"

  if [[ -n "$configured_serial" ]]; then
    local matched_line
    matched_line="$(echo "$devices_output" | awk -v s="$configured_serial" '$1==s {print $0}' | head -n 1)"
    if [[ -z "$matched_line" ]]; then
      local known
      known="$(echo "$devices_output" | awk '{printf "%s(%s) ", $1, $2}')"
      if [[ -z "$known" ]]; then known="none"; fi
      echo "ANDROID_SERIAL '$configured_serial' not found in adb devices. Known: $known" >&2
      return 21
    fi
    local state
    state="$(echo "$matched_line" | awk '{print $2}')"
    if [[ "$state" != "device" ]]; then
      echo "ANDROID_SERIAL '$configured_serial' is '$state'. Ensure it is online/authorized, then re-run." >&2
      return 22
    fi
    echo "$configured_serial"
    return
  fi

  local -a online_serials
  online_serials=()
  while read -r serial state; do
    [[ -z "$serial" ]] && continue
    if [[ "$state" == "device" ]]; then
      online_serials+=("$serial")
    fi
  done <<< "$devices_output"

  if (( ${#online_serials[@]} == 0 )); then
    maybe_start_emulator "$adb"

    devices_output="$(list_online_devices "$adb")"
    online_serials=()
    while read -r serial state; do
      [[ -z "$serial" ]] && continue
      if [[ "$state" == "device" ]]; then
        online_serials+=("$serial")
      fi
    done <<< "$devices_output"

    if (( ${#online_serials[@]} == 0 )); then
    echo "No Android device/emulator found for adb. Start a device in Android Studio Device Manager (or connect a physical device) and re-run." >&2
    return 23
    fi
  fi

  if (( ${#online_serials[@]} == 1 )); then
    echo "${online_serials[1]}"
    return
  fi

  if [[ "$prefer_emulator" == "true" ]]; then
    local -a emulators
    emulators=()
    local s
    for s in "${online_serials[@]}"; do
      [[ "$s" == emulator-* ]] && emulators+=("$s")
    done
    if (( ${#emulators[@]} == 1 )); then
      echo "${emulators[1]}"
      return
    fi
  fi

  echo "Multiple adb targets are online: ${online_serials[*]}. Set ANDROID_SERIAL to choose one." >&2
  return 24
}

ADB_BIN="$(resolve_adb)"
TARGET_SERIAL="$(resolve_target_serial "$ADB_BIN" "$PREFER_EMULATOR")"

echo "Using adb target: $TARGET_SERIAL"
"$ADB_BIN" -s "$TARGET_SERIAL" install -r "$APK_PATH"
"$ADB_BIN" -s "$TARGET_SERIAL" shell am start -W -n "$APP_COMPONENT"

