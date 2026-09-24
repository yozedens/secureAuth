# ADR 0001：应用锁与密钥模型

- **状态：** 已接受（2026-09-24）
- **范围：** v0.1
- **关联：** 设计文档 §22–§31、§35、§66（D1、D2、D6）；计划 T3.1

## 背景

v0.1 需要在编写加密与存储代码（M2）之前确定：应用锁与加密的关系、密钥分层、Keystore 参数、PIN 校验方式、锁的生命周期以及存储清单。这些决定影响存储格式，事后修改需要数据迁移。

## 决策

### 1. 锁模型：界面门禁 + 加固（D1）

- Keystore 主密钥**不要求用户认证**（不设置 `setUserAuthenticationRequired`）。
- 应用锁控制的是界面访问和 `VaultSession` 的开启，不是密码学绑定。
- 加固措施：锁包在整个导航外层；`locked` 只存在内存、进程启动默认为 `true`、不写入保存状态；后台超时自动上锁；上锁时丢弃已解密数据；PIN 在线限速。
- v0.2 与加密导出一起升级为 key slot 模型（随机数据密钥分别由 PIN 派生密钥和生物识别认证的 Keystore 密钥包裹）。

**理由：** 威胁模型已排除 root 设备；在未 root 设备上，密码学绑定的增量收益主要是防界面层漏洞；key slot 与加密导出共用口令派生机制，合并实现成本最低。

### 2. 密钥分层

```text
Android Keystore
 └── secureauth_master_key_v1（AES-256-GCM，不可导出）
      ├── 加密 vault.pb    （全部账号，整库一个密文）
      └── 加密 security.pb （PIN 校验值、失败计数、退避截止时间）
settings.pb：明文，只含非敏感设置
```

v0.1 只有一把 Keystore 密钥，没有独立的数据密钥。envelope 中预留 `keyId` 字段，v0.2 升级时据此迁移。

### 3. Keystore 参数

| 参数 | 取值 |
|---|---|
| 算法 | AES / GCM / NoPadding，256 位 |
| 用途 | `PURPOSE_ENCRYPT or PURPOSE_DECRYPT` |
| 用户认证 | 不要求 |
| IV | 由 Keystore 随机生成（`setRandomizedEncryptionRequired(true)`，默认值），调用方不得传入 |
| StrongBox | **优先使用**（`setIsStrongBoxBacked(true)`）；抛出 `StrongBoxUnavailableException` 时回退到普通 Keystore |
| `setUnlockedDeviceRequired` | **v0.1 不开启**：部分机型兼容性风险高，出错时表现为库不可读；App 无后台工作，收益有限。M6 真机测试后再评估 |
| 生成时机 | 仅在首次初始化流程中生成；读取时发现密钥缺失进入 `Unreadable`，**绝不**自动重新生成 |

### 4. 加密格式与位置（D6）

- 加解密放在 Repository 层（`VaultStorage` / `VaultSession`）；DataStore 只存密文 envelope，其 `Serializer` 只做二进制编解码。
- envelope：`magic "SAV1" | version 1 | keyId | ivLen | iv | ciphertext+tag`，头部作为 AAD。
- 明文 Vault 为 JSON，带 `schemaVersion`。

### 5. PIN（D2）

- 6–12 位数字。
- 校验值：`PBKDF2WithHmacSHA256`，16 字节随机 salt，32 字节输出；迭代次数在目标机型上标定到约 300–500 ms；算法名与迭代次数随校验值保存；比较用 `MessageDigest.isEqual`。
- 校验值存在 `security.pb` 中，由主密钥加密，文件被带离设备也无法离线穷举。
- 在线限速：失败 5 次起等待 30 秒、1 分钟、5 分钟，之后每次 15 分钟；计数持久化，重启不清零；不做"失败 N 次清空数据"。

### 6. 锁生命周期

- 自动上锁默认 **1 分钟**，设置中可选"立即 / 30 秒 / 1 分钟 / 5 分钟"。
- 计时基于 `SystemClock.elapsedRealtime()`（不受修改系统时间影响）；进程死亡或重启后必然上锁。

### 7. 数据不可读

- `VaultState.Unreadable(reason)`：不写入、不删除密文、不重建密钥；提供"重试"和需二次确认的"清除全部数据并重新开始"。

## 后果

- 正面：实现简单；无需处理生物识别变更导致的密钥失效；忘记 PIN 时仍可用生物识别进入并修改 PIN。
- 负面：应用锁可被界面层漏洞绕过（已通过根部门禁与测试缓解，并在威胁模型中写明）。
- v0.2 升级需要一次数据迁移：用旧主密钥解密，生成数据密钥，建立 key slot，重新加密。
