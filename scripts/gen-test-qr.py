#!/usr/bin/env python3
"""Generates the offline test QR set in testdata/qr/ (plan §4.3).

Only test keys: RFC 4226 / RFC 6238 seeds and the well-known JBSWY3DPEHPK3PXP.
Requires: pip install qrcode pypng
Usage: scripts/gen-test-qr.py
"""
import base64
import pathlib

import qrcode
import qrcode.image.pure

OUT = pathlib.Path(__file__).resolve().parent.parent / "testdata" / "qr"


def b32(seed: str) -> str:
    return base64.b32encode(seed.encode()).decode().rstrip("=")


SHA1 = b32("12345678901234567890")
SHA256 = b32("12345678901234567890123456789012")
SHA512 = b32("1234567890123456789012345678901234567890123456789012345678901234")
DEMO = "JBSWY3DPEHPK3PXP"

# (file name, content, expected result)
CASES = [
    ("01_totp_basic", f"otpauth://totp/ExampleCorp:alice@example.com?secret={DEMO}&issuer=ExampleCorp",
     "接受；TOTP / SHA1 / 6 位 / 30 秒"),
    ("02_hotp_counter", f"otpauth://hotp/ExampleCorp:hotp-user?secret={SHA1}&issuer=ExampleCorp&counter=0",
     "接受；HOTP，首次生成 755224，之后 287082（RFC 4226）"),
    ("03_url_encoded", f"otpauth://totp/Example%20Corp:alice%40example.com?secret={DEMO}&issuer=Example%20Corp",
     "接受；服务“Example Corp”，账号“alice@example.com”"),
    ("04_plus_in_account", f"otpauth://totp/ExampleCorp:alice+2fa@example.com?secret={DEMO}&issuer=ExampleCorp",
     "接受；账号保留加号“alice+2fa@example.com”"),
    ("05_raw_spaces_utf8", f"otpauth://totp/示例 服务:张三?secret={DEMO}&issuer=示例 服务",
     "接受；服务“示例 服务”，账号“张三”"),
    ("06_mixed_case", f"OTPAUTH://TOTP/ExampleCorp:mixed?SECRET={DEMO.lower()}&Issuer=ExampleCorp&ALGORITHM=sha1",
     "接受；scheme、类型、参数名、算法、密钥大小写不敏感"),
    ("07_hotp_missing_counter", f"otpauth://hotp/ExampleCorp:no-counter?secret={SHA1}&issuer=ExampleCorp",
     "接受并提示“未包含计数器，已默认为 0”"),
    ("08_invalid_digits", f"otpauth://totp/ExampleCorp:bad-digits?secret={DEMO}&issuer=ExampleCorp&digits=5",
     "拒绝；提示不是有效的认证器二维码"),
    ("09_migration", "otpauth-migration://offline?data=CgA%3D",
     "拒绝；提示暂不支持从 Google Authenticator 导入"),
    ("10_not_otpauth", "https://example.com/",
     "拒绝；提示不是有效的认证器二维码"),
    ("11_long_fields", f"otpauth://totp/{'I' * 300}:{'a' * 300}?secret={DEMO}&issuer={'I' * 300}",
     "接受；服务名与账号各截断为 256 个字符，界面不溢出"),
    ("12_sha256_8digits", f"otpauth://totp/ExampleCorp:sha256?secret={SHA256}&issuer=ExampleCorp&algorithm=SHA256&digits=8",
     "接受；与 oathtool --totp=sha256 -d 8 结果一致"),
    ("13_sha512_60s", f"otpauth://totp/ExampleCorp:sha512?secret={SHA512}&issuer=ExampleCorp&algorithm=SHA512&period=60",
     "接受；60 秒周期，与 oathtool --totp=sha512 -s 60 结果一致"),
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, content, expected in CASES:
        img = qrcode.make(content, image_factory=qrcode.image.pure.PyPNGImage, box_size=8, border=4)
        with open(OUT / f"{name}.png", "wb") as f:
            img.save(f)
        rows.append(f"| `{name}.png` | {expected} |")
    readme = [
        "# 测试二维码集",
        "",
        "由 `scripts/gen-test-qr.py` 生成，只含测试密钥（计划 §4.3）。用于真机扫码与“从图片识别”",
        "（`docs/test-cases.md`）。请勿加入任何真实账号的二维码。",
        "",
        "| 文件 | 预期 |",
        "|---|---|",
        *rows,
        "",
        "交叉核对（与 App 显示的验证码比较）：",
        "",
        "```sh",
        f"oathtool --totp -b {DEMO}",
        f"oathtool --hotp -b {SHA1} -c 0",
        f"oathtool --totp=sha256 -d 8 -b {SHA256}",
        f"oathtool --totp=sha512 -s 60 -b {SHA512}",
        "```",
        "",
    ]
    (OUT / "README.md").write_text("\n".join(readme), encoding="utf-8")


if __name__ == "__main__":
    main()
