#!/usr/bin/env bash
# Verifies the security baseline of a built APK (design §34, §35).
# Usage: scripts/check-apk.sh path/to/app.apk
set -euo pipefail

apk="${1:?usage: $0 <apk>}"
analyzer="${APKANALYZER:-${ANDROID_HOME:?ANDROID_HOME not set}/cmdline-tools/latest/bin/apkanalyzer}"

perms="$("$analyzer" manifest permissions "$apk")"
echo "Permissions in $apk:"
echo "${perms:-  (none)}"

fail=0
for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
    if grep -qx "$forbidden" <<<"$perms"; then
        echo "FAIL: forbidden permission $forbidden"
        fail=1
    fi
done

manifest="$("$analyzer" manifest print "$apk")"
if ! grep -q 'android:allowBackup="false"' <<<"$manifest"; then
    echo "FAIL: android:allowBackup is not false"
    fail=1
fi
if ! grep -q 'android:dataExtractionRules=' <<<"$manifest"; then
    echo "FAIL: android:dataExtractionRules missing"
    fail=1
fi
if grep -q 'android:debuggable="true"' <<<"$manifest"; then
    echo "FAIL: APK is debuggable"
    fail=1
fi

if [[ $fail -ne 0 ]]; then
    exit 1
fi
echo "OK: APK security baseline passed"
