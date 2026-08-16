#!/usr/bin/env bash
set -euo pipefail

if command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  ADB_BIN="$ANDROID_HOME/platform-tools/adb"
else
  echo "adb was not found. Add Android platform-tools to PATH or set ANDROID_HOME." >&2
  exit 1
fi
SERIAL="${1:-emulator-5554}"
FIXTURE="$(cd "$(dirname "$0")" && pwd)/fixtures/minklauncher-test-contacts.vcf"
DEVICE_FILE="/sdcard/Download/minklauncher-test-contacts.vcf"

if ! "$ADB_BIN" -s "$SERIAL" get-state >/dev/null 2>&1; then
  echo "No connected Android device found with serial: $SERIAL" >&2
  exit 1
fi

# This fixture must never be offered to a physical device. The Android emulator
# reports ro.kernel.qemu=1; a serial-name check alone is not sufficient.
if [[ "$("$ADB_BIN" -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "Refusing to import test contacts: $SERIAL is not an Android emulator." >&2
  exit 1
fi

if "$ADB_BIN" -s "$SERIAL" shell test -f "$DEVICE_FILE"; then
  echo "The MinkLauncher contact fixture is already staged on $SERIAL; nothing was changed."
  exit 0
fi

if [[ ! -f "$FIXTURE" ]]; then
  ruby "$(dirname "$0")/generate_test_contacts.rb"
fi

"$ADB_BIN" -s "$SERIAL" push "$FIXTURE" "$DEVICE_FILE"
"$ADB_BIN" -s "$SERIAL" shell am broadcast \
  -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://$DEVICE_FILE" >/dev/null

MEDIA_ROW="$(
  "$ADB_BIN" -s "$SERIAL" shell content query \
    --uri content://media/external/file \
    --projection _id:_display_name:mime_type |
    grep "_display_name=$(basename "$DEVICE_FILE")" |
    tail -n 1
)"
MEDIA_ID="$(printf '%s\n' "$MEDIA_ROW" | sed -n 's/.*_id=\([0-9][0-9]*\).*/\1/p')"

if [[ -z "$MEDIA_ID" ]]; then
  echo "The VCF was pushed, but its MediaStore content URI could not be resolved." >&2
  exit 1
fi

"$ADB_BIN" -s "$SERIAL" shell am start \
  -a android.intent.action.VIEW \
  -t text/x-vcard \
  -d "content://media/external/file/$MEDIA_ID" \
  -f 0x10000001

echo "The emulator Contacts app should now show its one-time import confirmation."
