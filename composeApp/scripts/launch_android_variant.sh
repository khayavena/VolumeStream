#!/bin/zsh
set -euo pipefail

: "${APK_PATH:?APK_PATH is required}"
: "${APP_COMPONENT:?APP_COMPONENT is required}"

PREFER_EMULATOR="${PREFER_EMULATOR:-false}"

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

list_online_devices() {
  local adb="$1"
  "$adb" devices | awk 'NR>1 && NF>=2 {print $1 " " $2}'
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
    echo "No Android device/emulator found for adb. Start a device in Android Studio Device Manager (or connect a physical device) and re-run." >&2
    return 23
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

