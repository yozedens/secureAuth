#!/usr/bin/env bash
# Verifies the security baseline of a built APK (design §34, §35).
# Usage: scripts/check-apk.sh path/to/app.apk
#
# Uses aapt2: legacy aapt (used by apkanalyzer) cannot parse release APKs whose
# resource table was optimized by AGP.
set -euo pipefail

apk="${1:?usage: $0 <apk>}"
if [[ -z "${AAPT2:-}" ]]; then
    sdk="${ANDROID_HOME:?ANDROID_HOME not set}"
    AAPT2="$(ls -d "$sdk"/build-tools/*/aapt2 | sort -V | tail -n 1)"
fi
echo "Using $AAPT2"

perms="$("$AAPT2" dump permissions "$apk")"
echo "$perms"

manifest="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$apk")"

fail=0
for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
    if grep -q "'$forbidden'" <<<"$perms"; then
        echo "FAIL: forbidden permission $forbidden"
        fail=1
    fi
done

backup_line="$(grep -E ':allowBackup\(' <<<"$manifest" || true)"
echo "allowBackup: ${backup_line:-<missing>}"
if [[ -z "$backup_line" ]] || ! grep -qE '=(false|0x0+)$' <<<"$(sed 's/[[:space:]]*$//' <<<"$backup_line")"; then
    echo "FAIL: android:allowBackup is not false"
    fail=1
fi

if ! grep -qE ':dataExtractionRules\(' <<<"$manifest"; then
    echo "FAIL: android:dataExtractionRules missing"
    fail=1
fi

if grep -E ':debuggable\(' <<<"$manifest" | grep -qE '=(true|0xffffffff)'; then
    echo "FAIL: APK is debuggable"
    fail=1
fi

if [[ $fail -ne 0 ]]; then
    exit 1
fi
echo "OK: APK security baseline passed"
