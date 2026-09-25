# 测试二维码集

由 `scripts/gen-test-qr.py` 生成，只含测试密钥（计划 §4.3）。用于真机扫码与“从图片识别”
（`docs/test-cases.md`）。请勿加入任何真实账号的二维码。

| 文件 | 预期 |
|---|---|
| `01_totp_basic.png` | 接受；TOTP / SHA1 / 6 位 / 30 秒 |
| `02_hotp_counter.png` | 接受；HOTP，首次生成 755224，之后 287082（RFC 4226） |
| `03_url_encoded.png` | 接受；服务“Example Corp”，账号“alice@example.com” |
| `04_plus_in_account.png` | 接受；账号保留加号“alice+2fa@example.com” |
| `05_raw_spaces_utf8.png` | 接受；服务“示例 服务”，账号“张三” |
| `06_mixed_case.png` | 接受；scheme、类型、参数名、算法、密钥大小写不敏感 |
| `07_hotp_missing_counter.png` | 接受并提示“未包含计数器，已默认为 0” |
| `08_invalid_digits.png` | 拒绝；提示不是有效的认证器二维码 |
| `09_migration.png` | 拒绝；提示暂不支持从 Google Authenticator 导入 |
| `10_not_otpauth.png` | 拒绝；提示不是有效的认证器二维码 |
| `11_long_fields.png` | 接受；服务名与账号各截断为 256 个字符，界面不溢出 |
| `12_sha256_8digits.png` | 接受；与 oathtool --totp=sha256 -d 8 结果一致 |
| `13_sha512_60s.png` | 接受；60 秒周期，与 oathtool --totp=sha512 -s 60 结果一致 |

交叉核对（与 App 显示的验证码比较）：

```sh
oathtool --totp -b JBSWY3DPEHPK3PXP
oathtool --hotp -b GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ -c 0
oathtool --totp=sha256 -d 8 -b GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA
oathtool --totp=sha512 -s 60 -b GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA
```
