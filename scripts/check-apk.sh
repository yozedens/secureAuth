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
    # aapt2.exe on Windows (Git Bash).
    AAPT2="$(ls -d "$sdk"/build-tools/*/aapt2* | sort -V | tail -n 1)"
fi
echo "Using $AAPT2"

perms="$("$AAPT2" dump permissions "$apk")"
echo "$perms"

manifest="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$apk")"

# Prints "<tag> <name>" for each component that is exported and has no android:permission.
exported_components() {
    awk '
        function flush() {
            if (comp != "" && exported && !perm) print comp " " name
            comp = ""
        }
        /^ *E: / {
            match($0, /^ */); ind = RLENGTH
            if (comp != "" && ind <= cind) flush()
            if ($2 ~ /^(activity|activity-alias|service|receiver|provider)$/) {
                comp = $2; cind = ind; name = ""; exported = 0; perm = 0
            }
            next
        }
        /^ *A: / && comp != "" {
            match($0, /^ */)
            if (RLENGTH != cind + 2) next
            if ($0 ~ /:name\(/) { n = $0; sub(/.*:name\([^)]*\)="/, "", n); sub(/".*/, "", n); name = n }
            if ($0 ~ /:exported\(/ && $0 ~ /=(true|0xffffffff)/) exported = 1
            if ($0 ~ /:permission\(/) perm = 1
        }
        END { flush() }
    '
}

fail=0
for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
    if grep -q "'$forbidden'" <<<"$perms"; then
        echo "FAIL: forbidden permission $forbidden"
        fail=1
    fi
done

# Allowlist: any new permission (e.g. pulled in by a dependency) must be reviewed and added
# here deliberately (plan T0.4, design §34).
allowed_perms='^(android\.permission\.(CAMERA|USE_BIOMETRIC|USE_FINGERPRINT)|.*\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$'
while read -r perm; do
    if ! grep -qE "$allowed_perms" <<<"$perm"; then
        echo "FAIL: permission not in allowlist: $perm"
        fail=1
    fi
done < <(sed -nE "s/^uses-permission: name='([^']+)'.*/\1/p" <<<"$perms")

# Exported components without a protecting permission (plan §5.2). Only the launcher
# activity may be reachable by other apps.
exported="$(exported_components <<<"$manifest")"
echo "Exported without permission: ${exported:-<none>}"
while read -r component; do
    [[ -z "$component" ]] && continue
    if [[ "$component" != "activity io.github.yozedens.secureauth.MainActivity" ]]; then
        echo "FAIL: unexpected exported component: $component"
        fail=1
    fi
done <<<"$exported"

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

# Size budget (design §53): < 15 MB.
max_bytes=$((15 * 1024 * 1024))
size="$(wc -c < "$apk" | tr -d ' ')"
echo "APK size: $size bytes (limit $max_bytes)"
if (( size >= max_bytes )); then
    echo "FAIL: APK is larger than 15 MB"
    fail=1
fi

if [[ $fail -ne 0 ]]; then
    exit 1
fi
echo "OK: APK security baseline passed"
