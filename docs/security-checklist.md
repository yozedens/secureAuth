# SecureAuth v0.1 安全检查清单（T6.3）

对应计划 §5.2 与设计 §60“安全”。每项标明验证方式；“自动”项在 CI 或模拟器测试中执行，
发布前仍需在真机矩阵的至少一台设备上抽查“手工”项。

| # | 检查项 | 方式 | 依据 / 方法 | 结果 |
|---|---|---|---|---|
| 1 | 最终 APK 无 `INTERNET` / `ACCESS_NETWORK_STATE` | 自动 | `scripts/check-apk.sh`（每次推送） | |
| 2 | 权限只含白名单：`CAMERA`、`USE_BIOMETRIC`、`USE_FINGERPRINT`、`*.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 自动 | 同上；新增权限必须评审后改白名单 | |
| 3 | 无意外导出组件（只有启动 Activity；其余导出组件必须有权限保护） | 自动 | 同上 | |
| 4 | `allowBackup=false`，`dataExtractionRules` 存在 | 自动 | 同上 | |
| 5 | release 非 debuggable | 自动 | 同上 | |
| 6 | Vault 文件落盘为密文 | 自动 | `DataStoreVaultRepositoryTest` | |
| 7 | 数据目录所有文件无 Secret / issuer / 账号名明文 | 自动 | `AccountFlowTest` → `SecretLeakCheck.assertNoPlaintextOnDisk`（UTF-8 / UTF-16） | |
| 8 | Logcat 无 Secret | 自动 + 手工 | `SecretLeakCheck.assertNotInLogcat`；真机：`adb logcat -d \| grep -i <测试密钥>` 全流程后无结果 | |
| 9 | 源码无 `Log` / `println` / `printStackTrace` | 自动 | detekt `ForbiddenImport` / `ForbiddenMethodCall` | |
| 10 | Secret 不进入 UI State、saved state、导航参数、Intent | 评审 + 自动 | 页面导航只在内存中且最多带账号 ID；表单状态 `toString` 脱敏；无 `rememberSaveable` 保存 Secret（`grep -rn rememberSaveable app/src/main` 应无结果） | |
| 11 | AES-256-GCM 篡改检测 | 自动 | `:core` 加解密与 VaultEnvelope 测试 | |
| 12 | 密钥在 Android Keystore；优先 StrongBox | 自动 + 手工 | `KeystoreAeadCipherTest`；真机记录 StrongBox 是否可用 | |
| 13 | 密钥缺失时进入 `Unreadable`，不覆盖数据 | 自动 + 手工 | `:core` Vault 测试；真机用例 S-03 | |
| 14 | PIN 退避重启后仍生效 | 自动 + 手工 | `:core` PinManager 测试；真机用例 L-04 | |
| 15 | 进程死亡恢复后必须解锁 | 手工 | 用例 L-09、L-10 | |
| 16 | 后台超时自动上锁 | 手工 | 用例 L-07、L-08 | |
| 17 | 截图、录屏、最近任务为黑屏（FLAG_SECURE） | 手工 | 用例 S-02 | |
| 18 | 系统备份与厂商换机不迁移数据 | 手工 | 用例 S-03、S-04，含华为“手机克隆”“备份与恢复” | |
| 19 | 剪贴板只含验证码，30 秒后清除，标记为敏感 | 自动 + 手工 | `AccountFlowTest` 校验复制内容；用例 U-02 | |
| 20 | APK 中无测试密钥以外的 Secret | 手工 | `unzip -p app-release.apk classes*.dex \| strings \| grep -E '^[A-Z2-7]{16,}$'` 结果只含测试向量 | |

## 发布前签字

```text
版本：v0.1.0  commit：
检查人：        日期：
全部通过：是 / 否（未通过项及处理：）
```
