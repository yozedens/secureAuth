#!/usr/bin/env bash
# Builds, signs and checks a release APK on this computer (docs/guide-build-release.md).
# Needs keystore.properties (or the SECUREAUTH_* environment variables) and ANDROID_HOME.
# Output: dist/secureauth-<version>.apk, .sha256 and release-notes.md.
# Runs on macOS, Linux and Git Bash on Windows.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "错误：$*" >&2; exit 1; }

[[ -f keystore.properties || -n "${SECUREAUTH_KEYSTORE:-}" ]] \
    || fail "找不到 keystore.properties（见 docs/guide-build-release.md 第 4 节）"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" ]] || fail "未设置 ANDROID_HOME（见 docs/guide-dev-environment.md 第 4.1 节）"
apksigner="$(ls -d "$sdk"/build-tools/*/apksigner* 2>/dev/null | sort -V | tail -n 1)"
[[ -n "$apksigner" ]] || fail "SDK 中没有 build-tools（在 SDK Manager 中安装 Android SDK Build-Tools）"

version="$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' app/build.gradle.kts)"
[[ -n "$version" ]] || fail "无法从 app/build.gradle.kts 读取 versionName"
grep -q "^## \[$version\]" CHANGELOG.md || fail "CHANGELOG.md 中没有 [$version] 段落"
echo "==> 版本 $version"

gradlew=./gradlew
[[ "${OS:-}" == "Windows_NT" ]] && gradlew=./gradlew.bat

echo "==> 测试与检查（几分钟）"
"$gradlew" :core:test :core:koverVerify detektMain :app:lintRelease

echo "==> 构建签名的 release APK"
rm -f app/build/outputs/apk/release/app-release.apk
"$gradlew" :app:assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
[[ -f "$apk" ]] || fail "没有生成已签名的 APK，请检查 keystore.properties 中的路径与口令"

mkdir -p dist
echo "==> 校验签名"
"$apksigner" verify --verbose --print-certs "$apk" | tee dist/signing.txt
grep -qE "Verified using v[23] scheme .*: true" dist/signing.txt || fail "签名校验失败"

echo "==> 安全基线与大小检查"
scripts/check-apk.sh "$apk"

out="secureauth-$version.apk"
cp "$apk" "dist/$out"
if command -v sha256sum > /dev/null; then
    (cd dist && sha256sum "$out" > "$out.sha256")
else
    (cd dist && shasum -a 256 "$out" > "$out.sha256")
fi
cert="$(sed -nE 's/^Signer #1 certificate SHA-256 digest: //p' dist/signing.txt)"
{
    awk -v v="$version" '$0 ~ "^## \\[" v "\\]" {p=1; next} /^## \[/ {p=0} p' CHANGELOG.md
    echo
    echo "### 校验"
    echo
    echo '```text'
    cat "dist/$out.sha256"
    echo "签名证书 SHA-256: $cert"
    echo '```'
    echo
    echo "安装前请核对 APK 的 SHA-256；以后的版本必须使用同一签名证书，否则无法覆盖安装。"
} > dist/release-notes.md

echo
echo "完成："
echo "  dist/$out"
echo "  dist/$out.sha256"
echo "  dist/release-notes.md（发布说明，可直接粘贴到 GitHub Release）"
echo "签名证书 SHA-256: $cert"
echo "请确认它与你生成密钥时记录的指纹一致。"
