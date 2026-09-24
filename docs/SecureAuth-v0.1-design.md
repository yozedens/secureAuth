# SecureAuth v0.1 — 个人离线 TOTP/HOTP Authenticator

## 1. 项目概述

**项目名称：** SecureAuth  
**版本：** v0.1 MVP  
**定位：** Local-first、Offline-first、安全优先的 Android TOTP/HOTP 验证器  
**目标：** 作为个人使用的轻量级本地认证器，用于管理 GitHub、Google、Facebook、企业内部系统等服务的 TOTP/HOTP 二次验证码。

### 1.1 核心原则

1. **本地优先**
   - 所有账号数据仅存储在 Android 本地。
   - v0.1 不提供云同步。
   - v0.1 不需要服务器。

2. **离线运行**
   - OTP 生成完全在本地完成。
   - 不声明 `INTERNET` 权限。
   - 不接入联网分析、广告或遥测 SDK。

3. **安全优先**
   - OTP Secret 使用 Android Keystore 保护的 AES-256-GCM 加密。
   - 支持生物识别和 PIN 应用锁。
   - 敏感页面启用 `FLAG_SECURE`。
   - 不在日志、SharedPreferences、普通 UI 状态中保存明文 Secret。

4. **标准优先**
   - TOTP 遵循 RFC 6238。
   - HOTP 遵循 RFC 4226。
   - 支持标准 `otpauth://` URI。

---

# 2. v0.1 目标

v0.1 是一个可以真正日常使用的个人版 MVP，而不是完整商业产品。

用户可以：

- 扫描网站提供的 2FA QR Code。
- 手工添加 TOTP/HOTP 账号。
- 管理多个认证账号。
- 查看当前 OTP。
- 查看 TOTP 倒计时。
- 一键复制 OTP。
- 编辑账号。
- 删除账号。
- 关闭并重新打开 App 后仍能保留账号。
- 使用生物识别或 PIN 解锁 App。
- 在完全离线状态下正常生成验证码。

---

# 3. v0.1 功能范围

## 3.1 P0 功能

| 编号 | 功能 | 优先级 | 状态 |
|---|---|---|---|
| F01 | TOTP | P0 | 必须 |
| F02 | HOTP | P0 | 必须 |
| F03 | QR Code 扫描 | P0 | 必须 |
| F04 | 手工添加账号 | P0 | 必须 |
| F05 | 多账号管理 | P0 | 必须 |
| F06 | OTP 展示 | P0 | 必须 |
| F07 | TOTP 倒计时 | P0 | 必须 |
| F08 | 一键复制 OTP | P0 | 必须 |
| F09 | 编辑账号 | P0 | 必须 |
| F10 | 删除账号 | P0 | 必须 |
| F11 | 本地持久化 | P0 | 必须 |
| F12 | Android Keystore | P0 | 必须 |
| F13 | AES-256-GCM 加密 | P0 | 必须 |
| F14 | 生物识别解锁 | P0 | 必须 |
| F15 | PIN 解锁 | P0 | 必须 |
| F16 | `FLAG_SECURE` | P0 | 必须 |
| F17 | 无网络运行 | P0 | 必须 |

---

# 4. 明确不属于 v0.1 的功能

以下功能暂时不实现：

- 云同步
- 多设备同步
- 在线账号系统
- 用户注册/登录
- 云端 Secret 存储
- Google Drive / iCloud 等云备份
- Authy 云同步
- Google Authenticator 云同步
- 从其他认证器直接导入
- Passkey / FIDO2 / WebAuthn
- Push Authentication
- 桌面端
- Web 端
- 企业后台
- 多用户权限系统
- 复杂分组
- 标签
- 拖拽排序
- 自定义图标
- 服务 Logo 在线获取
- 广告
- 数据分析
- Crash/Analytics 在线上报

---

# 5. 技术栈

## 5.1 Android

- Kotlin
- JDK 17
- Android Jetpack
- Jetpack Compose
- Material 3
- Coroutines
- StateFlow
- DataStore
- Android Keystore
- BiometricPrompt
- CameraX
- QR Code Decoder

建议：

- `minSdk = 26`
- `targetSdk = 35`

v0.1 不引入 Hilt/Koin，采用手工依赖注入。

---

# 6. 整体架构

采用：

**Clean-ish Architecture + MVVM + Repository + StateFlow**

整体结构：

```text
┌─────────────────────────────────────┐
│              Compose UI             │
│                                     │
│ Lock / Account / Add / Scan / Edit │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│           ViewModel / StateFlow      │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│             Repository              │
└──────────────┬───────────────┬──────┘
               │               │
               ▼               ▼
       ┌──────────────┐ ┌──────────────┐
       │ OTP / Parser │ │ SecureStorage│
       └──────────────┘ └──────┬───────┘
                               │
                               ▼
                       ┌──────────────┐
                       │ Android      │
                       │ Keystore     │
                       └──────────────┘
```

---

# 7. 项目目录

建议：

```text
secureauth/
├── app/
│   ├── MainActivity.kt
│   ├── SecureAuthApplication.kt
│   └── AppContainer.kt
│
├── core/
│   ├── model/
│   │   ├── OtpAccount.kt
│   │   ├── OtpType.kt
│   │   └── Algorithm.kt
│   │
│   ├── otp/
│   │   ├── OtpGenerator.kt
│   │   ├── HotpGenerator.kt
│   │   └── TotpGenerator.kt
│   │
│   ├── base32/
│   │   └── Base32Decoder.kt
│   │
│   └── otpauth/
│       ├── OtpUri.kt
│       └── OtpUriParser.kt
│
├── data/
│   ├── repository/
│   │   ├── TokenRepository.kt
│   │   └── TokenRepositoryImpl.kt
│   │
│   └── storage/
│       ├── SecureStorage.kt
│       └── DataStoreSecureStorage.kt
│
├── security/
│   ├── keystore/
│   │   └── KeystoreManager.kt
│   │
│   ├── biometric/
│   │   └── BiometricAuthenticator.kt
│   │
│   └── pin/
│       └── PinManager.kt
│
└── feature/
    ├── accounts/
    │   ├── AccountListScreen.kt
    │   └── AccountListViewModel.kt
    │
    ├── addaccount/
    │   ├── AddAccountScreen.kt
    │   └── AddAccountViewModel.kt
    │
    ├── scanner/
    │   ├── ScanScreen.kt
    │   └── ScanViewModel.kt
    │
    └── lock/
        ├── LockScreen.kt
        └── LockViewModel.kt
```

---

# 8. 核心数据模型

## 8.1 OTP 类型

```kotlin
enum class OtpType {
    TOTP,
    HOTP
}
```

## 8.2 算法

```kotlin
enum class Algorithm {
    SHA1,
    SHA256,
    SHA512
}
```

虽然 v0.1 UI 默认只暴露 SHA1，但底层模型应预留 SHA256/SHA512。

## 8.3 OtpAccount

```kotlin
data class OtpAccount(
    val id: String,
    val type: OtpType,
    val issuer: String?,
    val accountName: String,
    val secret: Secret,
    val algorithm: Algorithm,
    val digits: Int,
    val periodSeconds: Int?,
    val counter: Long?
)
```

其中：

- TOTP 使用 `periodSeconds`
- HOTP 使用 `counter`
- Secret 不应该以普通 String 长期存在于 UI 层

---

# 9. Secret 设计

建议不要让业务层直接到处使用 String Secret。

可以定义：

```kotlin
class Secret(
    private val value: ByteArray
) {
    fun bytes(): ByteArray {
        return value.copyOf()
    }
}
```

后续可以进一步封装敏感数据生命周期。

原则：

- 不写入 Logcat
- 不进入普通 UI State
- 不写入 SharedPreferences
- 不写入明文数据库
- 不出现在异常 message
- 不参与普通埋点
- 不放在 Intent extra
- 不通过 Clipboard 复制 Secret

---

# 10. OTP 核心算法

## 10.1 HOTP

HOTP 遵循 RFC 4226。

核心公式：

```text
HOTP(K, C) = Truncate(HMAC-SHA-1(K, C)) mod 10^Digits
```

其中：

- `K` = Secret
- `C` = Counter
- `Digits` = OTP 位数

核心接口：

```kotlin
interface OtpGenerator {

    fun generate(
        secret: ByteArray,
        counter: Long,
        algorithm: Algorithm,
        digits: Int
    ): String
}
```

---

# 11. TOTP

TOTP 遵循 RFC 6238。

TOTP 本质上是在 HOTP 基础上使用时间计算 Counter：

```text
T = floor((Current Unix Time - T0) / X)
```

然后：

```text
TOTP = HOTP(Secret, T)
```

默认：

```text
Algorithm = SHA1
Digits    = 6
Period    = 30 seconds
```

因此：

```kotlin
counter = currentUnixTime / periodSeconds
```

再调用 HOTP。

---

# 12. OTP 参数

底层支持：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| Algorithm | SHA1 | SHA1/SHA256/SHA512 |
| Digits | 6 | 6/8 |
| Period | 30 | TOTP 秒数 |
| Counter | 0 | HOTP Counter |

v0.1 UI 默认：

```text
TOTP:
SHA1 / 6 digits / 30 seconds

HOTP:
SHA1 / 6 digits / Counter
```

---

# 13. Base32

QR Code 中通常通过：

```text
secret=JBSWY3DPEHPK3PXP
```

传递 Base32 Secret。

需要实现独立的 Base32 Decoder。

应支持：

- 大写
- 小写
- 有 `=`
- 无 `=`
- 前后空格
- 合法字符校验

应拒绝：

- 非法字符
- 空 Secret
- 无法解码的数据

测试重点：

```text
JBSWY3DPEHPK3PXP
jbswy3dpehpk3pxp
JBSWY3DPEHPK3PXP====
```

---

# 14. `otpauth://` URI

标准格式：

```text
otpauth://totp/Issuer:account@example.com?secret=XXX&issuer=Issuer
```

HOTP：

```text
otpauth://hotp/Issuer:account@example.com?secret=XXX&issuer=Issuer&counter=0
```

解析模型：

```kotlin
data class OtpUri(
    val type: OtpType,
    val issuer: String?,
    val accountName: String,
    val secret: String,
    val algorithm: Algorithm,
    val digits: Int,
    val periodSeconds: Int?,
    val counter: Long?
)
```

---

# 15. URI Parser 规则

解析：

1. Scheme
2. OTP 类型
3. Label
4. Issuer
5. Account
6. Secret
7. Algorithm
8. Digits
9. Period
10. Counter

需要处理 URL Encoding。

例如：

```text
otpauth://totp/Example%20Service:user%40example.com
```

最终应得到：

```text
Issuer = Example Service
Account = user@example.com
```

---

# 16. QR Code 扫描流程

不要扫描后直接保存。

正确流程：

```text
Camera
   │
   ▼
QR Decoder
   │
   ▼
otpauth:// URI
   │
   ▼
OtpUriParser
   │
   ▼
确认页面
   │
   ├── Issuer
   ├── Account
   ├── Type
   ├── Algorithm
   ├── Digits
   └── Period/Counter
   │
   ▼
用户确认
   │
   ▼
TokenRepository
   │
   ▼
Encrypted Storage
```

---

# 17. 手工添加账号

页面：

```text
添加账号

类型
○ TOTP
○ HOTP

服务名称
[ GitHub              ]

账号
[ user@example.com    ]

Secret
[ JBSWY3DPEHPK3PXP    ]

算法
[ SHA1 ▼ ]

位数
[ 6 ▼ ]

周期
[ 30 秒 ]

        保存
```

HOTP 页面增加：

```text
Counter
[ 0 ]
```

---

# 18. 账号列表

主页面：

```text
SecureAuth

GitHub
user@example.com

        123 456
        28s

Google
user@gmail.com

        482 912
        17s

企业系统
admin

        821 304
        09s
```

每个账号：

- Issuer
- Account
- OTP
- TOTP 倒计时
- 复制按钮
- 编辑入口
- 删除入口

---

# 19. TOTP 倒计时设计

不要给每个账号创建独立 Timer。

建议使用全局时间流：

```kotlin
val ticker: StateFlow<Long>
```

例如每秒：

```text
14:30:01
14:30:02
14:30:03
...
```

所有 TOTP 根据当前 Unix Time 计算：

```text
remaining = period - (timestamp % period)
```

这样可以：

- 减少协程数量
- 避免多个 Timer 漂移
- 统一刷新
- 多账号更容易管理

---

# 20. HOTP Counter 设计

HOTP 与 TOTP 不同，它依赖持久化 Counter。

推荐 v0.1 行为：

```text
用户点击生成/复制
        │
        ▼
使用当前 Counter 生成 OTP
        │
        ▼
Counter + 1
        │
        ▼
持久化
```

例如：

```text
Counter = 0
OTP = 755224

使用后：

Counter = 1
```

需要特别注意：

HOTP Counter 必须与服务器端 Counter 保持同步。

因此 v0.1 UI 应允许用户编辑/重置 Counter。

---

# 21. Repository

接口：

```kotlin
interface TokenRepository {

    suspend fun getAll(): List<OtpAccount>

    suspend fun get(id: String): OtpAccount?

    suspend fun add(account: OtpAccount)

    suspend fun update(account: OtpAccount)

    suspend fun delete(id: String)
}
```

Repository 负责：

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
SecureStorage
 ↓
AES-GCM
 ↓
DataStore
```

---

# 22. 为什么 v0.1 使用 DataStore

当前个人版账号数量预计较少。

例如：

```text
5 ~ 100 个账号
```

因此 v0.1 不需要立即引入 Room。

DataStore 的优点：

- 简单
- 依赖少
- 本地文件
- 易于整体加密
- 适合 MVP

未来如果加入：

- 搜索
- 分组
- 标签
- 排序
- 大量账号
- 复杂查询

可以迁移到：

```text
Room + encrypted storage
```

---

# 23. 数据存储安全架构

核心原则：

```text
DataStore
   │
   │ encrypted payload
   ▼
AES-256-GCM
   │
   │ key
   ▼
Android Keystore
```

DataStore 中只能出现：

```text
version
iv
ciphertext
```

不能出现：

```text
secret=JBSWY3DPEHPK3PXP
```

---

# 24. Android Keystore

建议：

```text
Alias:
secureauth_master_key
```

使用：

```text
AES
GCM
NoPadding
256 bit
```

Keystore 保存：

```text
AES-256 Key
```

DataStore 保存：

```text
Encrypted JSON
```

---

# 25. AES-GCM 数据格式

建议逻辑结构：

```json
{
  "version": 1,
  "iv": "Base64...",
  "ciphertext": "Base64..."
}
```

其中：

- `version` 用于未来格式升级
- `iv` 每次加密随机生成
- `ciphertext` 包含 GCM Authentication Tag

不要重复使用相同 IV + Key。

---

# 26. 加密流程

写入：

```text
OtpAccount
    │
    ▼
Serialize
    │
    ▼
JSON
    │
    ▼
AES-256-GCM Encrypt
    │
    ▼
Base64
    │
    ▼
DataStore
```

读取：

```text
DataStore
    │
    ▼
Base64 Decode
    │
    ▼
AES-256-GCM Decrypt
    │
    ▼
JSON
    │
    ▼
OtpAccount
```

---

# 27. 应用锁

支持：

1. Biometric
2. PIN

启动：

```text
App
 │
 ▼
LockScreen
 │
 ├── Biometric
 │
 └── PIN
 │
 ▼
AccountList
```

---

# 28. PIN 安全

绝对不能：

```text
SharedPreferences:
pin = "123456"
```

也不能：

```text
DataStore:
pin = "123456"
```

建议：

```text
PIN
 ↓
随机 Salt
 ↓
安全 KDF / 密码哈希
 ↓
保存 hash + salt
```

验证：

```text
输入 PIN
 ↓
相同 KDF
 ↓
比较 hash
```

如果使用 Android Keystore + 用户认证保护密钥，还可以进一步减少 PIN 本地验证逻辑。

---

# 29. Biometric

使用 Android：

```text
BiometricPrompt
```

流程：

```text
打开 App
    │
    ▼
检测设备生物识别能力
    │
    ├── 支持 → BiometricPrompt
    │
    └── 不支持 → PIN
```

用户也可以通过 PIN 作为 fallback。

---

# 30. `FLAG_SECURE`

敏感页面启用：

```kotlin
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

目的：

- 防止截图
- 防止录屏
- 防止最近任务缩略图泄漏

---

# 31. Clipboard 安全

复制 OTP 时：

```text
OTP
 ↓
Clipboard
```

建议：

- 仅复制 OTP，不复制 Secret
- 标记为敏感内容
- 30 秒后自动清除
- 页面返回后台时可以考虑清除
- 不在日志记录 Clipboard 内容

---

# 32. UI State 安全

UI State 中允许：

```kotlin
data class AccountUiModel(
    val id: String,
    val issuer: String?,
    val accountName: String,
    val otp: String,
    val remainingSeconds: Int
)
```

不要放：

```kotlin
secret
```

也不要：

```kotlin
data class AccountUiModel(
    ...
    val secret: String
)
```

---

# 33. Secret 内存生命周期

无法做到：

```text
Secret 永远不存在于内存
```

因为生成 OTP 时必须读取 Secret。

正确目标是：

- 尽量缩短 Secret 在内存中的生命周期
- 不长期缓存
- 不进入 UI State
- 不写日志
- 不持久化明文
- 使用后尽可能清理临时 ByteArray

---

# 34. 网络安全策略

v0.1：

**不声明 INTERNET 权限。**

Manifest 不添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

这样可以从架构层面约束：

```text
App
 │
 ├── OTP
 ├── Storage
 ├── Camera
 └── Biometric
```

不存在：

```text
Network
```

---

# 35. Camera 权限

QR 扫描需要：

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

只有用户进入扫描页面时才请求 Camera 权限。

不要启动 App 时立即请求。

---

# 36. 页面设计

v0.1 页面：

```text
LockScreen
     │
     ▼
AccountListScreen
     │
     ├── AddAccountScreen
     │
     ├── ScanScreen
     │      │
     │      ▼
     │   ConfirmAccountScreen
     │
     └── EditAccountScreen
```

---

# 37. LockScreen

内容：

```text
SecureAuth

🔐

使用生物识别解锁

[ 使用 PIN ]
```

PIN：

```text
请输入 PIN

● ● ● ● ● ●

[ 解锁 ]
```

---

# 38. AccountListScreen

顶部：

```text
SecureAuth                 ＋
```

账号卡片：

```text
GitHub
user@example.com

123 456

剩余 27 秒

[复制]
```

HOTP：

```text
GitHub
user@example.com

755 224

Counter: 12

[生成] [复制]
```

---

# 39. AddAccountScreen

支持：

```text
扫码添加
```

和：

```text
手工添加
```

入口。

推荐：

```text
添加账号

┌─────────────────┐
│    扫描二维码    │
└─────────────────┘

┌─────────────────┐
│    手工添加      │
└─────────────────┘
```

---

# 40. ConfirmAccountScreen

QR 扫描成功后：

```text
确认添加

服务
GitHub

账号
user@example.com

类型
TOTP

算法
SHA1

位数
6

周期
30 秒

[取消]       [添加]
```

必须用户确认后才写入存储。

---

# 41. EditAccountScreen

允许修改：

- Issuer
- Account
- Algorithm
- Digits
- Period
- Counter

Secret 修改时应要求重新输入或重新扫描。

---

# 42. 删除账号

删除必须二次确认：

```text
删除账号？

GitHub
user@example.com

删除后 Secret 将无法恢复。

[取消] [删除]
```

因为 v0.1 不提供备份，所以删除应明确提示不可恢复。

---

# 43. ViewModel

例如：

```kotlin
class AccountListViewModel(
    private val repository: TokenRepository,
    private val totpGenerator: TotpGenerator
) : ViewModel() {

    val uiState: StateFlow<AccountListUiState>
}
```

ViewModel 负责：

- 加载账号
- 生成 OTP
- 处理复制
- 删除
- 编辑
- 计算 UI 状态

Repository 不负责 UI 行为。

---

# 44. AppContainer

不使用 Hilt/Koin。

可以：

```kotlin
class AppContainer(
    context: Context
) {

    val keystoreManager = KeystoreManager()

    val secureStorage =
        DataStoreSecureStorage(
            context = context,
            keystoreManager = keystoreManager
        )

    val tokenRepository =
        TokenRepositoryImpl(
            storage = secureStorage
        )

    val hotpGenerator =
        HotpGenerator()

    val totpGenerator =
        TotpGenerator(
            hotpGenerator = hotpGenerator
        )
}
```

然后在：

```kotlin
Application
```

中创建。

---

# 45. 依赖关系原则

核心层不能依赖 Android：

```text
core/model
core/otp
core/base32
core/otpauth
```

应该能够直接使用：

```text
JUnit
```

测试。

例如：

```text
core/otp
     │
     └── java.security
```

而不是：

```text
core/otp
     │
     └── Android Context
```

---

# 46. 测试策略

## 46.1 HOTP

必须使用 RFC 4226 官方测试向量。

重点测试：

```text
Counter 0
Counter 1
Counter 2
...
Counter 9
```

---

# 47. TOTP

必须使用 RFC 6238 测试向量。

同时测试：

```text
SHA1
SHA256
SHA512
```

即使 v0.1 UI 默认只使用 SHA1。

---

# 48. Base32 测试

测试：

```text
大写
小写
带 padding
无 padding
空格
非法字符
空字符串
```

---

# 49. URI Parser 测试

至少测试：

```text
TOTP
HOTP
issuer
account
URL encoding
secret
algorithm
digits
period
counter
```

异常：

```text
scheme 错误
type 错误
secret 缺失
HOTP counter 缺失
非法 digits
非法 algorithm
```

---

# 50. Crypto 测试

必须测试：

```text
encrypt -> decrypt
```

以及：

```text
错误 Key
错误 IV
篡改 ciphertext
错误 tag
空数据
大数据
```

重点验证：

```text
篡改后不能成功解密
```

---

# 51. Repository 测试

测试：

```text
add
get
getAll
update
delete
```

以及：

```text
App restart
    ↓
DataStore
    ↓
Repository
    ↓
数据仍然存在
```

---

# 52. UI 测试

至少覆盖：

```text
启动
 ↓
解锁
 ↓
账号列表
 ↓
添加
 ↓
查看 OTP
 ↓
复制
 ↓
编辑
 ↓
删除
```

QR：

```text
扫描
 ↓
解析
 ↓
确认
 ↓
保存
```

---

# 53. 测试覆盖率

目标：

```text
关键核心代码 ≥ 80%
```

重点：

- OTP
- Base32
- URI Parser
- Crypto
- Repository
- Security

覆盖率不是唯一质量指标，标准测试向量和异常场景同样重要。

---

# 54. 性能目标

## OTP

目标：

```text
单次 OTP 生成 < 100ms
```

实际正常设备上应明显低于该目标。

## UI

目标：

```text
正常账号列表 ≥ 55 FPS
```

对于个人使用场景：

```text
5 ~ 100 个账号
```

应保持流畅。

---

# 55. APK 大小

目标：

```text
APK < 15 MB
```

主要通过：

- 避免大型 SDK
- 避免不必要第三方库
- 不加入网络 SDK
- 不加入分析 SDK
- 不加入大型 UI 框架
- 使用 Android/Jetpack 原生能力

---

# 56. 数据流

## TOTP

```text
System Clock
     │
     ▼
Unix Timestamp
     │
     ▼
Time Counter
     │
     ▼
Secret + Counter
     │
     ▼
HMAC
     │
     ▼
Dynamic Truncation
     │
     ▼
Modulo
     │
     ▼
6 Digit OTP
```

---

# 57. QR 添加完整流程

```text
用户打开添加页面
        │
        ▼
点击“扫描二维码”
        │
        ▼
请求 Camera 权限
        │
        ▼
Camera Preview
        │
        ▼
扫描 QR
        │
        ▼
获得 otpauth:// URI
        │
        ▼
OtpUriParser
        │
        ▼
OtpUri
        │
        ▼
确认页面
        │
        ▼
用户确认
        │
        ▼
Base32 Decode
        │
        ▼
创建 OtpAccount
        │
        ▼
Repository
        │
        ▼
AES-GCM Encrypt
        │
        ▼
DataStore
```

---

# 58. 安全边界

## 绝对禁止

```text
Log.d("OTP", secret)
```

```text
Log.d("OTP", account.secret)
```

```text
println(secret)
```

```text
SharedPreferences.putString("secret", secret)
```

```text
Intent.putExtra("secret", secret)
```

```text
Crashlytics.setCustomKey("secret", secret)
```

---

# 59. 错误处理

错误类型建议：

```kotlin
sealed class OtpError {
    data object InvalidSecret : OtpError()
    data object InvalidAlgorithm : OtpError()
    data object InvalidDigits : OtpError()
    data object InvalidPeriod : OtpError()
    data object InvalidCounter : OtpError()
    data object InvalidUri : OtpError()
}
```

UI 显示用户可理解的信息。

例如：

```text
二维码不是有效的认证器二维码
```

而不是：

```text
IllegalArgumentException: Base32 decode failed...
```

同时避免把 Secret 放入异常 message。

---

# 60. 时间处理

TOTP 必须依赖 Unix 时间。

建议：

```kotlin
interface Clock {
    fun currentTimeMillis(): Long
}
```

生产环境：

```kotlin
System.currentTimeMillis()
```

测试：

```kotlin
FakeClock
```

这样可以精确测试 RFC 6238。

---

# 61. 时间异常处理

如果用户手机时间不准确，TOTP 可能无法通过服务器验证。

v0.1 可以提供简单提示：

```text
验证码无效时，请检查手机的日期和时间是否设置为自动。
```

不需要在客户端实现服务器时间同步。

原因：

- v0.1 无网络
- Authenticator 不负责服务器验证
- 自动时间同步属于 Android 系统能力

---

# 62. 安全威胁模型

v0.1 重点防御：

| 威胁 | 防护 |
|---|---|
| 本地文件泄漏 | AES-GCM |
| Android 文件读取 | Keystore |
| 截图 | FLAG_SECURE |
| 最近任务缩略图 | FLAG_SECURE |
| Log 泄漏 | 禁止日志记录 Secret |
| 明文 DataStore | 禁止 |
| Secret 进入 UI | 架构隔离 |
| 网络泄漏 | 不声明 INTERNET |
| 未授权打开 App | Biometric/PIN |
| 数据篡改 | AES-GCM Authentication Tag |

不承诺防御：

- 已 Root 且攻击者完全控制设备
- 内核级恶意软件
- 已被完全攻陷的设备
- 用户主动泄露 PIN
- 用户主动泄露 Secret

---

# 63. v0.1 验收标准

## 功能

- [ ] 可以添加 TOTP
- [ ] 可以添加 HOTP
- [ ] 可以扫描 `otpauth://`
- [ ] 可以手工输入 Secret
- [ ] 可以管理多个账号
- [ ] OTP 正确生成
- [ ] TOTP 倒计时正常
- [ ] OTP 可以复制
- [ ] 可以编辑账号
- [ ] 可以删除账号
- [ ] App 重启后数据仍存在

## 安全

- [ ] Secret 不以明文写入 DataStore
- [ ] Secret 不进入普通 UI State
- [ ] Secret 不进入 Logcat
- [ ] AES-256-GCM 正常工作
- [ ] AES Key 存在 Android Keystore
- [ ] 支持 Biometric
- [ ] 支持 PIN
- [ ] 启用 FLAG_SECURE
- [ ] Manifest 不声明 INTERNET

## 标准

- [ ] RFC 4226 HOTP 测试向量通过
- [ ] RFC 6238 TOTP 测试向量通过
- [ ] Base32 测试通过
- [ ] otpauth URI 测试通过

---

# 64. 后续版本路线

## v0.2

可以考虑：

- 搜索
- 分组
- 标签
- 自定义排序
- 深色主题
- AMOLED 黑色主题
- 更丰富的账号编辑
- 服务图标

## v0.3

可以考虑：

- 加密备份
- 加密恢复
- 文件导入/导出
- 更完善的数据迁移机制

## v0.4+

可以考虑：

- 从其他认证器导入
- 更完整的标准兼容
- 多设备迁移
- 更丰富的安全策略

---

# 65. 备份设计原则

备份不应直接导出：

```json
{
  "secret": "JBSWY3DPEHPK3PXP"
}
```

未来备份应该：

```text
Backup Password
       │
       ▼
KDF
       │
       ▼
Encryption Key
       │
       ▼
AES-256-GCM
       │
       ▼
Encrypted Backup
```

原因：

Android Keystore 中的 Key 无法简单复制到另一台设备。

因此：

**设备内加密和跨设备备份加密应该是两套机制。**

---

# 66. 设计决策总结

## 决策 1：DataStore，而不是 Room

原因：

- v0.1 数据量小
- 实现简单
- 依赖少
- 足够满足个人使用

未来复杂查询时再引入 Room。

## 决策 2：自己实现 OTP 核心

原因：

- RFC 4226/6238 算法本身并不复杂
- 完全掌握实现
- 降低第三方运行时依赖
- 方便测试标准向量
- 核心逻辑可以保持 Android 无关

## 决策 3：QR 必须属于 v0.1

原因：

实际网站，例如 GitHub，在配置 2FA 时通常会提供二维码。

用户可以：

```text
网站二维码
     ↓
SecureAuth 扫描
     ↓
自动解析 Secret
     ↓
直接生成验证码
```

因此 QR 不是后续增强功能，而是核心使用流程。

## 决策 4：TOTP + HOTP 都进入 v0.1

HOTP 算法并不复杂。

TOTP 本质上是：

```text
HOTP + Time Counter
```

因此同时支持两者不会显著增加核心算法复杂度。

---

# 67. 最终 v0.1 架构

```text
                     SecureAuth
                          │
          ┌───────────────┴────────────────┐
          │                                │
       Compose UI                     Security
          │                                │
          │                    ┌───────────┴──────────┐
          │                    │                      │
          │                Biometric                 PIN
          │
          ▼
      ViewModel
          │
          ▼
      Repository
          │
    ┌─────┴───────────────┐
    │                     │
    ▼                     ▼
 OTP Core             SecureStorage
    │                     │
    ├── HOTP              ▼
    ├── TOTP          AES-256-GCM
    ├── Base32             │
    └── otpauth            ▼
                      Android Keystore
                            │
                            ▼
                        DataStore
```

---

# 68. 第一版开发顺序

建议按照下面顺序实现：

```text
1. 创建 Android 项目
        ↓
2. 建立 core/model
        ↓
3. 实现 Base32
        ↓
4. 实现 HOTP
        ↓
5. 实现 TOTP
        ↓
6. 编写 RFC 测试
        ↓
7. 实现 otpauth Parser
        ↓
8. 实现 AES-GCM
        ↓
9. 实现 Android Keystore
        ↓
10. 实现 DataStore
        ↓
11. 实现 Repository
        ↓
12. 实现账号列表
        ↓
13. 实现手工添加
        ↓
14. 实现 QR Scanner
        ↓
15. 实现 QR 确认页面
        ↓
16. 实现编辑/删除
        ↓
17. 实现 Clipboard
        ↓
18. 实现 Biometric
        ↓
19. 实现 PIN
        ↓
20. 加入 FLAG_SECURE
        ↓
21. 完成 UI 测试
        ↓
22. 真机测试
        ↓
23. 安全检查
        ↓
24. v0.1 Release
```

---

# 69. v0.1 Definition of Done

当以下条件全部满足时，认为 SecureAuth v0.1 完成：

```text
✓ TOTP 可正常生成
✓ HOTP 可正常生成
✓ QR 可扫描
✓ otpauth URI 可解析
✓ 手工添加可用
✓ 多账号可用
✓ OTP 可复制
✓ TOTP 倒计时正常
✓ 编辑可用
✓ 删除可用
✓ 数据重启后仍存在
✓ AES-GCM 加密
✓ Android Keystore
✓ Biometric
✓ PIN
✓ FLAG_SECURE
✓ 无 INTERNET 权限
✓ RFC 4226 测试通过
✓ RFC 6238 测试通过
✓ Base32 测试通过
✓ URI Parser 测试通过
✓ 真机离线环境正常运行
```

这将构成 SecureAuth 的第一个真正可用版本。
