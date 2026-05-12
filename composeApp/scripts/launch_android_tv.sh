#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VARIANT_SCRIPT="$SCRIPT_DIR/launch_android_variant.sh"

if [[ ! -f "$VARIANT_SCRIPT" ]]; then
  echo "Android variant launch script not found: $VARIANT_SCRIPT" >&2
  exit 10
fi

# TV defaults; can still be overridden by env if needed.
export APP_COMPONENT="${APP_COMPONENT:-com.vdigital.volumestream.tv/com.vdigital.volumestream.platform.activity.TvMainActivity}"
export PREFER_EMULATOR="${PREFER_EMULATOR:-true}"

exec /bin/zsh "$VARIANT_SCRIPT"

