# SecureAuth v0.1 — 个人离线 TOTP/HOTP Authenticator

> **文档版本：** v0.1 设计 · 修订 2
>
> **修订 2：** 明确支持平台，新增鸿蒙 4.x（HarmonyOS 4.2 及以下）兼容说明与安装说明（§5.3、§35、§66 D8）。
>
> **修订说明：** 本修订在初稿基础上吸收了设计评审意见，主要变化：
>
> - 补齐"数据离开设备"的非网络通道：系统备份 / 设备迁移、依赖合并权限、Compose 保存状态（§34、§35、§36）
> - 明确应用锁模型为"UI 门禁 + 加固"，定义锁生命周期、PIN 防暴力破解与完整流程（§27–§31）
> - 新增"金库不可读"状态与数据丢失风险提示（§26、§32）
> - 存储明确为整库加密，加解密在 Repository 层，DataStore 只存密文（§22–§25）
> - Secret 边界下移到 Repository 之下，ViewModel 不接触 Secret（§9、§21、§47）
> - 明确 HOTP 按钮语义、剪贴板策略、otpauth / Base32 的真实世界边界规则（§13–§15、§20、§33）
> - QR 解码库定为 ZXing core；`:core` 为纯 JVM Gradle 模块
> - 补充威胁模型、测试策略，调整优先级与开发顺序；新增决策记录（§66）

## 1. 项目概述

**项目名称：** SecureAuth  
**版本：** v0.1 MVP  
**定位：** Local-first、Offline-first、安全优先的 Android TOTP/HOTP 验证器  
**目标：** 作为个人使用的轻量级本地认证器，用于管理 GitHub、Google、Facebook、企业内部系统等服务的 TOTP/HOTP 二次验证码。

### 1.1 核心原则

1. **本地优先**
   - 所有账号数据仅存储在本机 Android 应用私有目录。
   - v0.1 不提供云同步，不需要服务器。
   - **不参与任何系统级备份或设备迁移**（见 §35）。

2. **离线运行**
   - OTP 生成完全在本地完成。
   - **最终 APK** 不含 `INTERNET` 权限（包括依赖库合并进来的权限，见 §34）。
   - 不接入联网分析、广告或遥测 SDK。
   - 不依赖 Google Play 服务（GMS），在无 GMS 的设备上完整可用。

3. **安全优先**
   - 账号数据使用 Android Keystore 保护的 AES-256-GCM 整库加密。
   - 支持生物识别和 PIN 应用锁，后台超时自动上锁。
   - 整个应用窗口启用 `FLAG_SECURE`。
   - Secret 不出现在日志、SharedPreferences、UI State、保存状态（saved state）、导航参数中。

4. **标准优先**
   - TOTP 遵循 RFC 6238。
   - HOTP 遵循 RFC 4226。
   - 支持标准 `otpauth://` URI，并兼容常见的非标准写法。

---

# 2. v0.1 目标

v0.1 是一个可以真正日常使用的个人版 MVP，而不是完整商业产品。

用户可以：

- 扫描网站提供的 2FA QR Code。
- 手工添加 TOTP/HOTP 账号。
- 管理多个认证账号。
- 查看当前 OTP 与 TOTP 倒计时。
- 一键复制 OTP。
- 编辑、删除账号。
- 关闭并重新打开 App 后仍能保留账号。
- 使用生物识别或 PIN 解锁 App。
- 在完全离线状态下正常生成验证码。

---

# 3. v0.1 功能范围

## 3.1 P0 功能（必须）

| 编号 | 功能 | 说明 |
|---|---|---|
| F01 | TOTP | RFC 6238 |
| F02 | HOTP | RFC 4226，语义见 §20 |
| F03 | QR Code 扫描 | CameraX + ZXing |
| F04 | 手工添加账号 | |
| F05 | 多账号管理 | 默认按 issuer 字母序排列 |
| F06 | OTP 展示 | |
| F07 | TOTP 倒计时 | 全局 ticker |
| F08 | 一键复制 OTP | 剪贴板策略见 §33 |
| F09 | 编辑账号 | |
| F10 | 删除账号 | 二次确认 |
| F11 | 本地持久化 | 整库加密，DataStore |
| F12 | Android Keystore | |
| F13 | AES-256-GCM 加密 | |
| F14 | 生物识别解锁 | |
| F15 | PIN 解锁 | 含设置 / 修改 / 尝试次数限制 |
| F16 | `FLAG_SECURE` | 全局开启 |
| F17 | 无网络运行 | 最终 APK 无 INTERNET |
| F18 | 首次启动引导 | 设置 PIN + 数据丢失风险提示（§32） |
| F19 | 自动上锁 | 后台超时重新上锁（§29） |
| F20 | 禁止系统备份 / 设备迁移 | §35 |
| F21 | 金库不可读处理 | §26 |
| F22 | 设置页 | 生物识别开关、修改 PIN、自动上锁时长 |

## 3.2 P1 功能（成本低，建议 v0.1 内完成）

| 编号 | 功能 | 说明 |
|---|---|---|
| P1-01 | 从图片识别二维码 | 在本机上开通 2FA 时二维码就显示在本机屏幕上，摄像头扫不到；系统照片选择器（无需存储权限）+ ZXing |
| P1-02 | 简单搜索 | 按 issuer / 账号名过滤 |
| P1-03 | 重复账号检测 | 同一二维码扫描两次时提示 |
| P1-04 | 自动时间检测 | 读取 `Settings.Global.AUTO_TIME`，关闭时提示（无需权限） |
| P1-05 | 深色主题 | 跟随系统，Compose Material 3 几乎零成本 |

---

# 4. 明确不属于 v0.1 的功能

- HarmonyOS NEXT（5.0 及以上）支持
- 应用市场上架（含华为应用市场、Google Play）；v0.1 以签名 APK 侧载，个人自用
- 云同步、多设备同步
- 在线账号系统、用户注册/登录
- 云端 Secret 存储
- Google Drive / iCloud 等云备份
- Authy / Google Authenticator 云同步
- 从其他认证器直接导入（扫描到 `otpauth-migration://` 时给出明确提示，见 §15）
- 加密导出 / 备份（计划 v0.2，见 §61）
- Passkey / FIDO2 / WebAuthn
- Push Authentication
- 桌面端、Web 端
- 企业后台、多用户权限系统
- 复杂分组、标签、拖拽排序
- 自定义图标、服务 Logo 在线获取
- 广告、数据分析、Crash/Analytics 在线上报
- `otpauth://` 外部链接打开（新增导出入口，暂不做）

---

# 5. 技术栈

## 5.1 Android

- Kotlin，JDK 17
- Android Jetpack、Jetpack Compose、Material 3
- Coroutines、StateFlow
- DataStore（只存密文，见 §22）
- kotlinx.serialization（不使用 `org.json`，它在本地 JVM 单测中是 Stub）
- Android Keystore
- androidx.biometric（BiometricPrompt）
- CameraX（`ImageAnalysis`）
- **ZXing core**（Apache-2.0，纯 Java，无网络、无 GMS、无 native 库）作为 QR 解码器

**不使用 ML Kit：** 闭源 Google SDK，其依赖链可能合并 INTERNET 权限并上报使用指标；非捆绑版依赖 GMS 下载模型。

建议：

- `minSdk = 26`：`PBKDF2WithHmacSHA256`、`java.util.Base64`、`java.time` 均从 API 26 起原生可用，core 层零第三方依赖。
- `targetSdk = 35`：强制 edge-to-edge，Compose 需正确处理 WindowInsets。若计划上架 Google Play，以当时的 target API 要求为准（按往年节奏，2026-08-31 后通常要求 API 36）。

v0.1 不引入 Hilt/Koin，采用手工依赖注入。

## 5.3 支持平台

| 平台 | 支持情况 | 说明 |
|---|---|---|
| Android 8.0（API 26）– Android 15（API 35） | 支持 | `minSdk 26` / `targetSdk 35` |
| Android 16 及以上 | 支持 | 按 targetSdk 35 的兼容行为运行 |
| 鸿蒙 4.x（含 HarmonyOS 4.2）及以下 | 支持（兼容运行） | 通过其 Android 兼容层运行 APK，兼容层 API 级别以 `Build.VERSION.SDK_INT` 实测为准（通常约为 Android 12），在支持范围内；无需修改代码 |
| HarmonyOS NEXT（5.0 及以上） | 不支持 | 不再运行 APK，v0.1 不涉及 |

鸿蒙 4.x 设备的特点与对应处理（均无需额外代码，已由现有设计覆盖，需真机验证）：

| 项 | 情况 | 处理 |
|---|---|---|
| 无 Google Play 服务 | 华为设备不带 GMS | 设计本身不依赖 GMS（ZXing、无 Google SDK） |
| 从图片识别二维码 | 无 GMS 时系统照片选择器回退到系统文件选择器（`ACTION_OPEN_DOCUMENT`） | 真机验证可选图并识别 |
| Keystore | 可用；多数机型无 StrongBox | StrongBox 失败时回退（§24） |
| 生物识别 | 指纹通常可用；2D 人脸一般不对第三方应用开放 | 不可用时回落 PIN（§31） |
| 华为"手机克隆" / "备份与恢复" | 是否遵守 `allowBackup=false` 需实测 | 真机验证；迁移后由 `Unreadable` 状态兜底（§26、§35） |
| 后台杀进程较激进 | 剪贴板 30 秒清除定时器可能不执行 | 已接受（§33） |

**安装说明（个人自用，侧载 APK）：** 鸿蒙 4.x 默认开启"纯净模式"，会拦截外部 APK 安装。安装前需在"设置 → 系统和更新 → 纯净模式"中关闭，或在安装提示中选择继续安装；安装完成后可重新开启。不上架华为应用市场。

## 5.2 Gradle 模块

| 模块 | 类型 | 内容 |
|---|---|---|
| `:core` | 纯 Kotlin/JVM（`kotlin("jvm")`） | model、otp、base32、otpauth、crypto 抽象 |
| `:app` | Android application | data、security、feature、DI |

`:core` 必须是独立的纯 JVM 模块，"核心层不依赖 Android"才能由编译器强制保证。

---

# 6. 整体架构

**Clean-ish Architecture + MVVM + Repository + StateFlow**

```text
┌──────────────────────────────────────────┐
│ LockGate（根部门禁：locked ? Lock : Nav） │
└──────────────────┬───────────────────────┘
                   ▼
┌──────────────────────────────────────────┐
│ Compose UI：Accounts / Add / Scan /       │
│             Confirm / Edit / Settings     │
└──────────────────┬───────────────────────┘
                   ▼
┌──────────────────────────────────────────┐
│ ViewModel / StateFlow（不接触 Secret）     │
└──────────────────┬───────────────────────┘
                   ▼
┌──────────────────────────────────────────┐
│ OtpCodeService / TokenRepository          │
│ VaultSession（解锁期间持有明文）  ← Secret 边界
└───────────┬──────────────────┬───────────┘
            ▼                  ▼
   ┌────────────────┐  ┌──────────────────┐
   │ OTP / Parser   │  │ VaultStorage      │
   │ （:core）       │  │ AES-GCM 加解密     │
   └────────────────┘  └────┬─────────┬────┘
                            │ 密钥     │ 密文
                            ▼         ▼
                  ┌──────────────┐ ┌──────────┐
                  │ Android      │ │ DataStore│
                  │ Keystore     │ │ （密文）  │
                  └──────────────┘ └──────────┘
```

Keystore 只提供密钥，密文直接写入 DataStore，数据不"流经"Keystore。

---

# 7. 项目目录

```text
secureauth/
├── core/                          # :core，纯 JVM 模块
│   └── src/main/kotlin/…/core/
│       ├── model/
│       │   ├── OtpAccount.kt
│       │   ├── OtpKind.kt
│       │   ├── Algorithm.kt
│       │   └── Secret.kt
│       ├── otp/
│       │   ├── Hotp.kt
│       │   └── Totp.kt
│       ├── base32/
│       │   └── Base32.kt
│       ├── otpauth/
│       │   ├── OtpUri.kt
│       │   └── OtpUriParser.kt
│       ├── crypto/
│       │   ├── AeadCipher.kt          # 接口 + 纯 JVM 实现（测试用）
│       │   └── VaultEnvelope.kt
│       └── error/
│           └── Errors.kt
│
└── app/                           # :app，Android 模块
    └── src/main/kotlin/…/
        ├── MainActivity.kt           # FragmentActivity
        ├── SecureAuthApplication.kt
        ├── AppContainer.kt
        ├── data/
        │   ├── repository/
        │   │   ├── TokenRepository.kt
        │   │   └── TokenRepositoryImpl.kt
        │   ├── vault/
        │   │   ├── VaultSession.kt
        │   │   ├── VaultStorage.kt
        │   │   └── KeystoreAeadCipher.kt
        │   └── otp/
        │       └── OtpCodeService.kt
        ├── security/
        │   ├── keystore/KeystoreManager.kt
        │   ├── biometric/BiometricAuthenticator.kt
        │   ├── pin/PinManager.kt
        │   ├── lock/AppLockController.kt
        │   └── clipboard/SecureClipboard.kt
        └── feature/
            ├── lock/            # LockScreen, LockViewModel
            ├── onboarding/      # SetupPinScreen, OnboardingViewModel
            ├── accounts/        # AccountListScreen, AccountListViewModel
            ├── addaccount/      # AddAccountScreen, ManualEntryScreen, AddAccountViewModel
            ├── scanner/         # ScanScreen, ScanViewModel
            ├── confirm/         # ConfirmAccountScreen
            ├── edit/            # EditAccountScreen, EditAccountViewModel
            └── settings/        # SettingsScreen, SettingsViewModel
```

---

# 8. 核心数据模型

## 8.1 OTP 类型：用 sealed 类型让非法状态无法表示

```kotlin
sealed interface OtpKind {
    data class Totp(val periodSeconds: Int = 30) : OtpKind
    data class Hotp(val counter: Long = 0) : OtpKind
}
```

不再使用 `type` + 可空 `periodSeconds` / `counter` 的组合，避免出现"TOTP 带 counter""HOTP 没有 counter"。

## 8.2 算法

```kotlin
enum class Algorithm { SHA1, SHA256, SHA512 }
```

v0.1 UI 默认 SHA1；算法、位数、周期放在手工添加页的折叠"高级选项"中。底层完整支持 SHA256/SHA512。

## 8.3 OtpAccount 与 AccountMeta

```kotlin
data class AccountMeta(
    val id: String,              // UUID
    val issuer: String?,
    val accountName: String,
    val algorithm: Algorithm,
    val digits: Int,             // 6..8
    val kind: OtpKind,
    val createdAt: Long
)

class OtpAccount(
    val meta: AccountMeta,
    val secret: Secret
)
```

- `AccountMeta` 不含 Secret，可以交给 ViewModel。
- `OtpAccount` 只存在于数据层（`VaultSession` / Repository 内部）。

---

# 9. Secret 设计

```kotlin
class Secret(value: ByteArray) {
    private val value: ByteArray = value.copyOf()   // 构造时复制，避免调用方清零导致别名问题

    inline fun <R> use(block: (ByteArray) -> R): R {
        val copy = bytesCopy()
        try { return block(copy) } finally { copy.fill(0) }
    }

    fun bytesCopy(): ByteArray = value.copyOf()

    fun wipe() = value.fill(0)

    override fun equals(other: Any?): Boolean =
        other is Secret && MessageDigest.isEqual(value, other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "Secret(***)"
}
```

要点：

- 构造时复制输入数组。
- `toString()` 永远不输出内容。
- `equals` 按内容常量时间比较，保证 Repository "写入后读出再比较"的测试成立、支持重复检测。

原则——Secret 不得出现在：

- Logcat、`println`、异常 message
- SharedPreferences、明文文件或数据库
- UI State、ViewModel
- `savedInstanceState`、`SavedStateHandle`、`rememberSaveable`、导航路由参数
- Intent extra
- 剪贴板
- 任何 `data class` 的自动 `toString()` 中

---

# 10. OTP 核心算法

## 10.1 HOTP（RFC 4226）

```text
HOTP(K, C) = Truncate(HMAC-SHA-x(K, C)) mod 10^Digits
```

- `K` = Secret，`C` = 8 字节大端 Counter，`Digits` = 6..8
- 动态截断的 offset 取 HMAC 结果**最后一个字节**的低 4 位（对 SHA256/SHA512 同样适用）

核心实现为纯函数，不需要 `OtpGenerator` 接口：

```kotlin
object Hotp {
    fun generate(key: ByteArray, counter: Long, algorithm: Algorithm, digits: Int): String
}
```

输出使用 `code.toString().padStart(digits, '0')`，**不要**使用 `String.format("%06d")`：后者跟随默认 Locale，在阿拉伯语、波斯语等环境下会输出非 ASCII 数字（Lint `DefaultLocale`）。

---

# 11. TOTP（RFC 6238）

```text
T = floor((Current Unix Time - T0) / X)，T0 = 0
TOTP = HOTP(Secret, T)
```

```kotlin
object Totp {
    fun generate(key: ByteArray, epochSeconds: Long, periodSeconds: Int,
                 algorithm: Algorithm, digits: Int): String =
        Hotp.generate(key, Math.floorDiv(epochSeconds, periodSeconds.toLong()), algorithm, digits)
}
```

默认：SHA1 / 6 位 / 30 秒。Counter 使用 `Long`，保证 64 位时间步。

---

# 12. OTP 参数

| 参数 | 默认值 | 合法范围 |
|---|---:|---|
| Algorithm | SHA1 | SHA1 / SHA256 / SHA512 |
| Digits | 6 | 6–8 |
| Period | 30 | 1–300 秒 |
| Counter | 0 | ≥ 0 |

---

# 13. Base32

QR Code 通常通过 `secret=JBSWY3DPEHPK3PXP` 传递 Base32 Secret。需要独立实现 Base32 Decoder（RFC 4648 字母表）。

**规范化（解码前）：**

- 去掉**所有**空白字符（Google 等服务展示的手工密钥每 4 位用空格分组），以及 `-`
- 转为大写
- 去掉末尾 `=`（`=` 只允许出现在末尾）

**拒绝：**

- 规范化后为空
- 非法字符（字母表不含 `0` `1` `8` `9`；错误提示可提醒"是否把字母 O/I/B 输成了数字"，但**不自动纠错**）
- 非法长度：去 padding 后长度 mod 8 ∈ {1, 3, 6}

**不拒绝短密钥：** 文档示例 `JBSWY3DPEHPK3PXP` 只有 10 字节（80 位），低于 RFC 4226 要求的 128 位，但现实中仍有服务在用。

错误信息中不得包含输入内容。

---

# 14. `otpauth://` URI

```text
otpauth://totp/Issuer:account@example.com?secret=XXX&issuer=Issuer
otpauth://hotp/Issuer:account@example.com?secret=XXX&issuer=Issuer&counter=0
```

解析结果：

```kotlin
class OtpUri(                   // 不是 data class：避免自动 toString() 输出 Secret
    val issuer: String?,
    val accountName: String,
    val secret: Secret,         // 解析阶段即完成 Base32 解码与校验
    val algorithm: Algorithm,
    val digits: Int,
    val kind: OtpKind,
    val warnings: List<ParseWarning>   // 如 HOTP 缺少 counter
)
```

---

# 15. URI Parser 规则

使用**手写的宽松解析器**，不使用 `java.net.URI`（遇到未编码空格等字符会抛 `URISyntaxException`）。

解析步骤：Scheme → 类型 → Label（Issuer / Account）→ Query 参数 → 校验。

| 情况 | 处理 |
|---|---|
| 大小写：`OTPAUTH://`、`TOTP`、`Secret=`、`sha256` | 均不区分大小写 |
| Label URL 编码，如 `Example%20Service:user%40example.com` | 百分号解码 → Issuer = `Example Service`，Account = `user@example.com` |
| Label 中的 `+`（如 `user+2fa@gmail.com`） | Label **只做百分号解码**，不把 `+` 转成空格 |
| Query 值中的 `+` | 按表单编码视为空格（仅对 issuer 有实际影响） |
| Label 分隔符 | 字面 `:` 或 `%3A`，按第一个冒号拆分，Account 去除前导空格 |
| `issuer` 参数与 Label 前缀同时存在且不一致 | 以 `issuer` 参数为准，不报错 |
| Account 为空 | 允许，在确认页由用户补填 |
| 未知参数（`image`、`color` 等） | 忽略 |
| 重复的 `secret` 参数 | 拒绝 |
| `digits` 不在 6–8 / `period` 不在 1–300 | 拒绝 |
| HOTP 缺少 `counter` | 默认 0，并在确认页提示"请确认计数器与服务端一致" |
| TOTP 带 `counter` / HOTP 带 `period` | 忽略 |
| `otpauth-migration://offline?data=…` | 返回专门错误，提示"暂不支持从 Google Authenticator 导入" |
| 超长字段、控制字符、RTL 覆盖字符 | issuer / account 限制长度（如 256 字符）并剔除控制字符 |

---

# 16. QR Code 扫描流程

不要扫描后直接保存。

```text
Camera（CameraX ImageAnalysis，STRATEGY_KEEP_ONLY_LATEST）
   │  Y 平面 → PlanarYUVLuminanceSource
   ▼
ZXing QRCodeReader
   │  识别到第一个有效结果后停止分析
   ▼
otpauth:// 字符串
   ▼
OtpUriParser（含 Base32 解码与校验）
   │  失败 → 友好提示，继续扫描
   ▼
PendingAccountHolder（添加流程范围内的共享 ViewModel，内存）
   ▼
确认页面：Issuer / Account / Type / Algorithm / Digits / Period 或 Counter / 警告 / 重复提示
   ▼
用户确认 → TokenRepository → 加密写入
   ▼
清空 PendingAccountHolder（取消时同样清空）
```

**禁止**把 URI 或 Secret 作为导航路由参数、放入 `SavedStateHandle` 或 `rememberSaveable`：它们都会进入 `savedInstanceState` Bundle，离开 App 进程由 system_server 持有，并在进程重建后恢复。

---

# 17. 手工添加账号

```text
添加账号

类型          ○ TOTP  ○ HOTP
服务名称      [ GitHub              ]
账号          [ user@example.com    ]
Secret        [ JBSW Y3DP EHPK 3PXP ]   ← 允许带空格粘贴

▸ 高级选项（默认折叠）
  算法        [ SHA1 ▼ ]
  位数        [ 6 ▼ ]
  周期        [ 30 秒 ]      （TOTP）
  Counter     [ 0 ]          （HOTP）

        保存
```

Secret 输入框：

- 状态保存在 `AddAccountViewModel` 中（扛过屏幕旋转，不进入 saved state），**不使用 `rememberSaveable`**
- 键盘类型为 Password、关闭自动更正，避免输入法学习词库
- 真机验证不会弹出自动填充服务的"保存密码"提示
- 保存或离开页面后清空

---

# 18. 账号列表

```text
SecureAuth                      🔍  ＋  ⚙

GitHub
user@example.com
        123 456          ◔ 28s      [复制]

Google
user@gmail.com
        482 912          ◔ 17s      [复制]

内部系统（HOTP）
admin
        --- ---     Counter: 12   [生成]
```

- 默认按 issuer 字母序（其次账号名）排列
- 点击卡片或复制按钮复制 OTP，显示"已复制"提示（Android 13+ 系统已有提示时不重复弹出）
- 长按 / 菜单进入编辑、删除
- `LazyColumn` 使用 `key = id`，UI 模型不可变

---

# 19. TOTP 倒计时设计

不给每个账号创建独立 Timer，使用全局时间流：

```kotlin
val ticker: StateFlow<Long>   // epochMillis，每秒对齐到秒边界发射
```

- 用 `delay(1000 - now % 1000)` 对齐秒边界，避免漂移
- `stateIn(scope, SharingStarted.WhileSubscribed(5_000), …)` + `collectAsStateWithLifecycle()`：App 在后台或上锁时停止计算
- `remaining = period - (epochSeconds % period)`；各账号可有不同 period
- OTP 只在时间步变化时重新计算（按 `(id, timeStep)` 缓存）

---

# 20. HOTP Counter 设计

存储的 `counter` 表示**下一次要使用的**计数值。

| 操作 | 行为 |
|---|---|
| 列表加载 | **不显示码**（显示 `--- ---`），不改变 counter |
| 点击 [生成] | 在同一个事务中：读取 counter = N → 保存 N+1 → **保存成功后**显示 HOTP(K, N) |
| 点击 [复制] | 复制当前显示的码，**不递增** |
| 编辑 Counter | 允许手动修改，用于与服务端重新同步 |

原因：按 RFC 4226 §7.4，服务端通过前向窗口（look-ahead）重新同步——客户端**超前**可以恢复，**落后**则被拒绝。因此必须先持久化、再显示，宁可多加不能少加。

- [生成] 按钮防抖，避免连点
- 递增使用 `DataStore.updateData` 原子完成（`OtpCodeService.nextHotp(id)`）
- 例：Counter = 0 → 生成后显示 `755224`，存储的 Counter 变为 1
- HOTP 码在被使用前一直有效，剪贴板风险高于 TOTP（见 §33）

---

# 21. Repository 与 OtpCodeService

```kotlin
interface TokenRepository {
    val accounts: Flow<List<AccountMeta>>        // 不含 Secret
    suspend fun get(id: String): AccountMeta?
    suspend fun add(meta: AccountMeta, secret: Secret): AddResult   // 含重复检测
    suspend fun updateMeta(meta: AccountMeta)
    suspend fun replaceSecret(id: String, secret: Secret)
    suspend fun delete(id: String)
}

interface OtpCodeService {
    fun totpAt(id: String, epochMillis: Long): OtpCode
    suspend fun nextHotp(id: String): OtpCode    // 原子地 counter+1 并持久化
}
```

- Secret 只进不出：Repository 对上层只暴露 `AccountMeta` 与算好的 `OtpCode`。
- 实现：两个接口的逻辑合并在 `:core` 的 `VaultRepository` 中（可在 JVM 上完整测试）；`:app` 只提供 `VaultStore`（DataStore 实现，只存密文）与 `KeystoreAeadCipher`。
- Repository 不负责 UI 行为。

数据路径：

```text
ViewModel
 ↓ AccountMeta / OtpCode
TokenRepository / OtpCodeService
 ↓
VaultSession（解锁期间持有明文 Vault）
 ↓ encrypt / decrypt
VaultStorage（AES-256-GCM，密钥来自 Keystore）
 ↓ 密文
DataStore
```

---

# 22. 为什么 v0.1 使用 DataStore

个人版账号数量预计 5–100 个，v0.1 不需要 Room。DataStore 提供：

- 原子写入（临时文件 + rename）
- `updateData` 串行化的读-改-写事务
- `Flow` 观察
- 依赖少，适合 MVP

**使用方式：** `DataStore<VaultEnvelope>`，自定义 `Serializer` 只做 envelope 的二进制编解码，**不做加解密**。

原因：DataStore 会把当前值缓存在内存中直到进程结束。若在 Serializer 中解密，明文整库会常驻内存、上锁后也无法清除；且将来若改用每次使用都需认证的密钥（`CryptoObject`），Serializer 无法参与认证流程。

修改统一走：

```kotlin
dataStore.updateData { env -> encrypt(transform(decrypt(env))) }
```

未来如果加入搜索、分组、标签、排序、大量账号、复杂查询，可迁移到 Room + 加密存储。

---

# 23. 数据存储安全架构

**整库加密：** 所有账号（包括 issuer、账号名等元数据——账号名常为邮箱，同样敏感）序列化为一个明文 Vault，整体加密为一个密文 blob。

```text
Vault (明文 JSON，仅在 VaultSession 内存中)
   │ kotlinx.serialization
   ▼
AES-256-GCM（AAD = envelope 头）
   │ key：Keystore
   ▼
VaultEnvelope → DataStore
```

## 23.1 存储清单

| 文件 / 存储 | 内容 | 保护 |
|---|---|---|
| `vault.pb`（DataStore） | 全部账号 | AES-GCM，Keystore 主密钥 |
| `security.pb`（DataStore） | PIN 校验值（算法、迭代次数、salt、hash）、失败次数、退避截止时间 | AES-GCM，Keystore 主密钥 |
| `settings.pb`（DataStore） | 生物识别开关、自动上锁时长、是否完成引导 | 明文（不含敏感数据） |

DataStore 中**不得出现** `secret=JBSWY3DPEHPK3PXP`、issuer 或账号名的明文。

---

# 24. Android Keystore

```text
Alias:   secureauth_master_key_v1
Algorithm: AES-256 / GCM / NoPadding
```

```kotlin
KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(BLOCK_MODE_GCM)
    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    // v0.1 不设置 setUserAuthenticationRequired（锁模型见 §27）
```

要点：

- 默认 `setRandomizedEncryptionRequired(true)`：加密时**不能自己传 IV**（会抛异常），应 `cipher.init(ENCRYPT_MODE, key)` 后读取 `cipher.iv`。
- 优先使用 StrongBox：`setIsStrongBoxBacked(true)`，捕获 `StrongBoxUnavailableException` 后回退到普通 Keystore（ADR 0001）。
- v0.1 **不开启** `setUnlockedDeviceRequired`：部分机型兼容性风险高，M6 真机测试后再评估（ADR 0001）。
- 生成 / 获取密钥在后台线程，不在 `Application.onCreate` 中同步执行。
- **只在"首次初始化"流程中生成密钥**；读取时发现密钥缺失，进入 `Unreadable` 状态（§26），**绝不**自动生成新密钥覆盖。

---

# 25. AES-GCM 数据格式

二进制 envelope：

```text
magic      4 bytes   "SAV1"
version    1 byte    1
keyId      1 byte    1          → 对应 Keystore alias；预留未来 key slot
ivLen      1 byte    12
iv         12 bytes
ciphertext N bytes   含 128-bit GCM tag
```

- `magic | version | keyId | ivLen` 作为 **AAD** 传入 GCM，防止头部被篡改或降级。
- IV 每次加密由 Keystore 随机生成，同一密钥下不重复使用。
- `version` 管加密格式；明文 Vault 内另有 `schemaVersion` 管数据模型迁移。

明文 Vault：

```json
{
  "schemaVersion": 1,
  "accounts": [
    {
      "id": "…", "issuer": "GitHub", "accountName": "user@example.com",
      "secret": "<Base64>", "algorithm": "SHA1", "digits": 6,
      "kind": { "type": "totp", "periodSeconds": 30 },
      "createdAt": 1790000000000
    }
  ]
}
```

反序列化使用 `ignoreUnknownKeys = true` 与字段默认值，保证向前兼容。

---

# 26. 加解密流程与"金库不可读"状态

写入：

```text
Vault → JSON → AES-256-GCM Encrypt（AAD=header）→ VaultEnvelope → DataStore
```

读取：

```text
DataStore → VaultEnvelope → 校验 magic/version → AES-256-GCM Decrypt → JSON → Vault
```

## 26.1 Vault 状态

```kotlin
sealed interface VaultState {
    data object Uninitialized : VaultState        // 首次启动，无密文、无密钥
    data object Locked : VaultState
    data class Unlocked(val session: VaultSession) : VaultState
    data class Unreadable(val reason: VaultError) : VaultState
}
```

`VaultError` 分类：

| 错误 | 典型原因 |
|---|---|
| `KeyMissing` | 从备份 / 换机恢复、部分机型 OTA 后 Keystore 异常 |
| `AuthenticationFailed` | `AEADBadTagException`：密文被篡改或密钥不匹配 |
| `Corrupted` | 不是 envelope 格式或被截断 |
| `UnsupportedVersion` | 由更高版本 App 写入 |
| `CryptoUnavailable` | Keystore 暂时性错误（可重试） |
| `Io` | 读写失败（可重试） |

`Unreadable` 状态下：

- **不写入、不重建密钥、不删除密文**（可能只是暂时性错误）
- 向用户清楚说明原因，提供"重试"
- 只提供一个需二次确认的"清除全部数据并重新开始"操作，并提示数据将永久丢失

---

# 27. 应用锁模型

**v0.1 采用"UI 门禁 + 加固"模型（决策 D1，见 §66）：**

- Keystore 主密钥不要求用户认证；
- 解锁（PIN / 生物识别）控制的是 UI 访问与 `VaultSession` 的开启；
- 威胁模型如实声明：应用锁是 **UI 层的访问控制**，不是密码学绑定。

为了未来升级到 key slot 模型（随机数据密钥分别由"PIN 派生密钥"和"生物识别认证的 Keystore 密钥"包裹），envelope 已预留 `keyId` 字段（§25），可在 v0.2 实现加密导出时一并迁移——两者共用"口令派生密钥 + 包裹"机制。

---

# 28. 锁门禁（LockGate）

锁是**根部门禁**，不是导航目的地：

```kotlin
setContent {
    val locked by lockController.locked.collectAsStateWithLifecycle()
    if (locked) LockScreen(...) else AppNavHost(...)
}
```

- `locked` 只存在内存（`AppLockController`，Application 级单例），**进程启动时默认为 `true`，绝不写入 saved state**。
- 因此进程被杀后从最近任务恢复，无论导航栈恢复到哪一页，都会先显示锁屏。
- 上锁时丢弃 `VaultSession`（清零其中的 Secret），停止 ticker。
- 首次启动（`VaultState.Uninitialized`）进入引导页（§32）。

---

# 29. 自动上锁

- 使用 `ProcessLifecycleOwner` 监听前后台：
  - `ON_STOP`：记录进入后台时间（`SystemClock.elapsedRealtime()`）
  - `ON_START`：若超过阈值则上锁
- 阈值可在设置页配置：立即 / 30 秒 / 1 分钟（默认）/ 5 分钟
- 设备重启、进程死亡后必然上锁（见 §28）

---

# 30. PIN 安全

绝对不能明文保存 PIN（SharedPreferences、DataStore 都不行）。

## 30.1 PIN 形式

- 6–12 位数字（决策 D2）

## 30.2 校验值

```text
PIN
 ↓ 随机 16 字节 salt
PBKDF2WithHmacSHA256（API 26+ 原生）
 ↓ 迭代次数在目标机型上标定到约 300–500 ms
hash（32 字节）
 ↓ 与 algorithm、iterations、salt 一起
写入 security.pb（整体由 Keystore 主密钥 AES-GCM 加密）
```

- 校验值被 Keystore 加密：即使文件被带离设备，也无法离线穷举。
- 比较使用 `MessageDigest.isEqual`。
- KDF 在 `Dispatchers.Default` 执行，不阻塞主线程。
- 保存 algorithm 与 iterations，便于未来升级参数（下次成功解锁时透明重算）。

## 30.3 尝试次数限制

6 位 PIN 只有 10⁶ 种组合，KDF 本身挡不住穷举，必须在线限速：

| 连续失败次数 | 等待时间 |
|---|---|
| 1–4 | 无 |
| 5 | 30 秒 |
| 6 | 1 分钟 |
| 7 | 5 分钟 |
| 8+ | 15 分钟（每次） |

- 失败次数与退避截止时间持久化在 `security.pb`，**重启不清零**
- 成功解锁后清零
- 不做"失败 N 次清空数据"（v0.1 无备份，风险过高）

## 30.4 流程

| 流程 | 说明 |
|---|---|
| 首次设置 | 输入两次确认；在引导页完成（§32） |
| 修改 PIN | 需先验证旧 PIN |
| 忘记 PIN | 可用生物识别解锁后在设置页修改；若生物识别也不可用，只能"清除全部数据并重新开始"（引导页提前告知） |

---

# 31. Biometric

- `MainActivity` 继承 **`FragmentActivity`**（`BiometricPrompt` 的要求；Compose 模板默认的 `ComponentActivity` 不满足）。
- 使用 `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)` 检测：

| 结果 | 处理 |
|---|---|
| `BIOMETRIC_SUCCESS` | 可开启 |
| `ERROR_NONE_ENROLLED` | 提示去系统设置录入 |
| `ERROR_NO_HARDWARE` / `ERROR_HW_UNAVAILABLE` | 隐藏开关 / 暂不可用，回落 PIN |
| `ERROR_SECURITY_UPDATE_REQUIRED` | 提示更新系统，回落 PIN |

- 必须先设置 PIN，才能开启生物识别；生物识别失败或取消时回落 PIN。
- Prompt 的否定按钮为"使用 PIN"。
- v0.1 不使用 `CryptoObject`（与 §27 一致）。

流程：

```text
打开 App → LockGate
    ├── 已开启且可用 → BiometricPrompt ─┬─ 成功 → 解锁
    │                                    └─ 失败/取消 → PIN
    └── 否则 → PIN
```

---

# 32. 首次启动引导与数据丢失风险

v0.1 无备份，且密钥绑定本设备。**卸载 App、清除数据、恢复出厂设置、换手机，都会导致全部 2FA 永久丢失。** 这是认证器最主要的现实风险。

首次启动引导：

1. 说明：数据只保存在本机，不会备份，不会随换机迁移
2. **强烈建议**在各网站开启 2FA 时保存恢复码（recovery codes）
3. 设置 PIN（两次确认）
4. 可选开启生物识别
5. 生成 Keystore 主密钥，写入空 Vault

用户需勾选"我已了解"后才能继续。设置页常驻同样的提示。

---

# 33. Clipboard 安全

复制 OTP：

- 仅复制 OTP，不复制 Secret
- 标记为敏感内容：API 33+ 使用 `ClipDescription.EXTRA_IS_SENSITIVE`，更低版本使用字符串键 `"android.content.extra.IS_SENSITIVE"`（隐藏系统剪贴板预览）
- **30 秒后无条件清除**（决策 D4）：定时器运行在 Application 级协程作用域中（ViewModel 作用域随页面销毁会被取消）；API 28+ 使用 `clearPrimaryClip()`，更低版本写入空内容。若用户在此期间复制了其他内容，也会被清除——这是有意的取舍（Android 10+ 后台无法读取剪贴板，无法判断内容是否仍是我们的码）
- **不在 App 进入后台时清除**：复制后切到浏览器粘贴是核心使用路径
- 进程被系统杀死时定时器不会执行，可接受
- 不在日志中记录剪贴板内容
- 输入法剪贴板历史 / 云剪贴板同步不受 App 控制，列入威胁模型

---

# 34. 网络安全策略

v0.1 **最终 APK** 不含 INTERNET 权限。

Manifest 显式剔除，防止依赖库在合并时带入：

```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
```

CI 对 release APK 执行检查：

```bash
apkanalyzer manifest permissions app-release.apk | grep -q INTERNET && exit 1
```

架构上，App 只包含：

```text
App
 ├── OTP
 ├── Storage
 ├── Camera
 └── Biometric
```

不存在 Network。

---

# 35. 系统备份与设备迁移

"不声明 INTERNET" 不等于"数据不离开设备"：自动备份与设备间迁移（D2D）由系统进程执行。若不关闭：

- 加密后的 Vault 与 PIN 校验值可能被上传到 Google Drive，或经换机工具、厂商备份功能迁出
- Keystore 密钥不会随之迁移，恢复后得到无法解密的库

Android 12 官方文档说明：在部分厂商设备上，`allowBackup="false"` 只关闭云备份，**不关闭 D2D**；旧的 `fullBackupContent` 规则对 D2D 无效。因此：

```xml
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ... >
```

`res/xml/data_extraction_rules.xml`：

```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
```

需在真机上验证，包括国产 ROM 的换机 / 本地备份工具（如华为"手机克隆""备份与恢复"）。即使如此，§26 的 `Unreadable` 处理仍然必须实现——总有无法拦截的迁移路径。

---

# 36. UI State 与保存状态安全

UI State 中允许：

```kotlin
@Immutable
data class AccountUiModel(
    val id: String,
    val issuer: String?,
    val accountName: String,
    val otp: String?,            // HOTP 未生成时为 null
    val remainingSeconds: Int?,  // 仅 TOTP
    val periodSeconds: Int?,
    val counter: Long?           // 仅 HOTP
)
```

不允许出现 `secret` 字段。

以下机制会把数据写入 `savedInstanceState`，**不得承载 Secret 或 otpauth URI**：

- Navigation 路由参数（包括 type-safe route 对象的字段）
- `SavedStateHandle`
- `rememberSaveable` / 可保存的 `TextFieldState`

唯一例外是手工添加页的 Secret 输入框本身，其状态保存在 ViewModel 中（§17）。

---

# 37. Secret 内存生命周期

无法做到"Secret 永远不存在于内存"，生成 OTP 时必须读取 Secret。

目标：

- 明文 Vault 只存在于 `VaultSession`，**上锁时整体丢弃**
- ViewModel 与 UI 层不持有 Secret
- 使用 `Secret.use {}`，用完即清零副本
- 不写日志，不持久化明文

现实预期：JSON 解析产生的 `String` 不可变、`Mac` / `SecretKeySpec` 会在内部复制密钥，ART 上无法做到彻底清零。真正有效的控制是上锁丢弃、不进 UI State、不写日志。

---

# 38. FLAG_SECURE

在 `MainActivity.onCreate` 中、`setContent` 之前**全局开启**：

```kotlin
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

- 单 Activity 应用中 FLAG_SECURE 按窗口生效，全局开启比按页面切换更简单也更安全（切换时序容易漏掉最近任务缩略图）
- Compose `Dialog` / `Popup` 是独立窗口，默认 `SecureFlagPolicy.Inherit` 会继承，**不要覆盖为 Off**
- 作用：防止截图、录屏、投屏、最近任务缩略图泄漏
- 局限：不能阻止无障碍服务读屏；API 34+ 对验证码视图设置 `accessibilityDataSensitive`

---

# 39. Camera 权限

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

- 只在用户进入扫描页面时请求，不在启动时请求
- 被拒绝时说明用途；"不再询问"时引导到系统设置
- 始终提供"改用手工添加 / 从图片识别"入口
- 无摄像头设备也可安装

---

# 40. 页面设计

```text
LockGate
 ├── [Uninitialized] OnboardingScreen → SetupPinScreen
 ├── [Locked]        LockScreen
 ├── [Unreadable]    VaultErrorScreen
 └── [Unlocked]      AppNavHost
                       └── AccountListScreen
                             ├── AddAccountScreen
                             │     ├── ScanScreen ──┐
                             │     ├── 从图片识别 ──┼──→ ConfirmAccountScreen
                             │     └── ManualEntryScreen
                             ├── EditAccountScreen
                             └── SettingsScreen
```

---

# 41. LockScreen

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

（输错 5 次后："请 30 秒后再试"）
```

---

# 42. AccountListScreen

TOTP 卡片：

```text
GitHub
user@example.com

123 456

剩余 27 秒

[复制]
```

HOTP 卡片：

```text
内部系统
admin

--- ---            （点击生成后显示 755 224）

Counter: 12

[生成] [复制]
```

---

# 43. AddAccountScreen

```text
添加账号

┌─────────────────┐
│    扫描二维码    │
└─────────────────┘

┌─────────────────┐
│    从图片识别    │
└─────────────────┘

┌─────────────────┐
│    手工添加      │
└─────────────────┘
```

---

# 44. ConfirmAccountScreen

```text
确认添加

服务      GitHub
账号      user@example.com      [可编辑，为空时必填]
类型      TOTP
算法      SHA1
位数      6
周期      30 秒

⚠ 该账号可能已存在（同一 Secret）            ← 重复检测
⚠ 二维码未包含计数器，已默认 0               ← HOTP 警告

[取消]       [添加]
```

必须用户确认后才写入存储；取消或完成后清空暂存。

---

# 45. EditAccountScreen

允许修改：Issuer、Account、Algorithm、Digits、Period、Counter。

- 修改 Algorithm / Digits / Period 时提示："修改这些参数会导致验证码与服务端不一致，仅用于修正录入错误"
- Secret 不可查看；修改 Secret 需重新输入或重新扫描

---

# 46. 删除账号

删除必须二次确认：

```text
删除账号？

GitHub
user@example.com

删除后 Secret 将无法恢复。
请确认已在该网站关闭两步验证或保存了恢复码。

[取消] [删除]
```

---

# 47. ViewModel

```kotlin
class AccountListViewModel(
    private val repository: TokenRepository,
    private val codeService: OtpCodeService,
    private val ticker: StateFlow<Long>,
    private val clipboard: SecureClipboard
) : ViewModel() {
    val uiState: StateFlow<AccountListUiState>
}
```

ViewModel 负责：组合账号元数据与验证码、处理复制 / HOTP 生成 / 删除 / 搜索、计算 UI 状态。ViewModel **不接触 Secret**。

ViewModel 通过 `viewModelFactory { initializer { … } }` 从 `AppContainer` 获取依赖。

---

# 48. AppContainer

不使用 Hilt/Koin。依赖均为 `lazy`，Keystore 与 DataStore 的 IO 不在主线程、不在 `Application.onCreate` 中同步执行。

```kotlin
open class AppContainer(private val context: Context) {
    open val clock: Clock by lazy { Clock.systemUTC() }                 // java.time.Clock
    open val keystoreManager by lazy { KeystoreManager() }
    open val vaultStorage by lazy { VaultStorage(context, keystoreManager) }
    open val vaultSession by lazy { VaultSessionManager(vaultStorage) }
    open val tokenRepository: TokenRepository by lazy { TokenRepositoryImpl(vaultSession) }
    open val otpCodeService: OtpCodeService by lazy { OtpCodeServiceImpl(vaultSession) }
    open val pinManager by lazy { PinManager(context, keystoreManager) }
    open val biometricAuthenticator: BiometricAuthenticator by lazy { AndroidBiometricAuthenticator() }
    open val lockController by lazy { AppLockController(clock, pinManager, vaultSession) }
    open val clipboard by lazy { SecureClipboard(context, applicationScope) }
    open val ticker by lazy { Ticker(clock, applicationScope) }
}
```

`open` 以便 UI 测试替换为 fake（生物识别、相机、时钟）。在 `SecureAuthApplication` 中创建。

---

# 49. 依赖关系原则

`:core`（model、otp、base32、otpauth、crypto 抽象）不能依赖 Android，由 Gradle 模块类型强制：

```text
:core ──→ java.security / javax.crypto / java.time
:app  ──→ :core + Android
```

`:core` 直接使用 JUnit 在 JVM 上测试。

---

# 50. 测试策略

## 50.1 HOTP

RFC 4226 附录 D 官方向量，Secret = ASCII `"12345678901234567890"`，6 位：

| Counter | OTP |
|---|---|
| 0 | 755224 |
| 1 | 287082 |
| 2 | 359152 |
| 3 | 969429 |
| 4 | 338314 |
| 5 | 254676 |
| 6 | 287922 |
| 7 | 162583 |
| 8 | 399871 |
| 9 | 520489 |

## 50.2 TOTP

RFC 6238 附录 B 向量，**8 位**，period = 30。注意三种算法的种子**长度不同**：

| 算法 | 种子（ASCII） |
|---|---|
| SHA1 | `12345678901234567890`（20 字节） |
| SHA256 | `12345678901234567890123456789012`（32 字节） |
| SHA512 | `1234567890123456789012345678901234567890123456789012345678901234`（64 字节） |

| Unix Time | SHA1 | SHA256 | SHA512 |
|---|---|---|---|
| 59 | 94287082 | 46119246 | 90693936 |
| 1111111109 | 07081804 | 68084774 | 25091201 |
| 1111111111 | 14050471 | 67062674 | 99943326 |
| 1234567890 | 89005924 | 91819424 | 93441116 |
| 2000000000 | 69279037 | 90698825 | 38618901 |
| 20000000000 | 65353130 | 77737706 | 47863826 |

误用 20 字节种子时，SHA256 @59 会得到 `32247374`——这是常见错误。`20000000000` 一行同时验证 64 位时间步。时间通过 `java.time.Clock.fixed(...)` 注入。

## 50.3 Base32

大写、小写、带 padding、无 padding、中间空格（`JBSW Y3DP EHPK 3PXP`）、`-` 分隔、非法字符（含 0/1/8/9）、非法长度（mod 8 ∈ {1,3,6}）、`=` 出现在中间、空字符串、80 位短密钥。

```text
JBSWY3DPEHPK3PXP
jbswy3dpehpk3pxp
JBSWY3DPEHPK3PXP====
JBSW Y3DP EHPK 3PXP
```

均应解码为 `Hello!` + `DE AD BE EF`（10 字节）。

## 50.4 URI Parser

正常：TOTP、HOTP、issuer、account、URL 编码、secret、algorithm、digits、period、counter，以及 §15 表中的每一行。

异常：scheme 错误、type 错误、secret 缺失、secret 重复、非法 digits、非法 period、非法 algorithm、`otpauth-migration://`。

## 50.5 Crypto

`AeadCipher` 接口有两个实现：

- **JVM 实现**（原始 AES 密钥）：在 `:core` 单测中覆盖 encrypt → decrypt、错误 Key、错误 IV、篡改 ciphertext、篡改 tag、篡改 AAD / 头部、空数据、大数据（如 1 MB）
- **Keystore 实现**：Keystore 无法在 JVM / Robolectric 中运行，使用设备上的 instrumented test 覆盖 encrypt → decrypt 与篡改检测

重点：篡改后不能成功解密。

## 50.6 Repository / Vault

- add、get、accounts、updateMeta、replaceSecret、delete、重复检测
- HOTP `nextHotp` 并发调用时 counter 严格递增
- **重启后数据仍存在**：取消第一个 DataStore 实例的 scope 后，在同一文件上新建实例读取（同一进程内同一文件不能同时存在两个活动实例）
- **明文不落盘**：写入已知 secret 与 issuer，读取原始文件字节，断言不包含 Base32 串、原始 secret 字节、issuer、账号名
- 密钥缺失 / 密文篡改 → `Unreadable`，且原文件未被修改

## 50.7 Security / Lock

- PIN 正确 / 错误、退避时间、重启后失败计数保留
- 自动上锁阈值
- **进程死亡：** `adb shell am kill <package>` 后从最近任务恢复，必须显示锁屏
- 剪贴板：敏感标记、30 秒清除

## 50.8 UI 测试

生物识别、相机、时钟通过 `AppContainer` 注入 fake。

```text
首次启动 → 引导 → 设置 PIN → 账号列表 → 手工添加 → 查看 OTP → 复制 → 编辑 → 删除
解锁 → 扫描（fake 帧）→ 解析 → 确认 → 保存
HOTP：生成 → counter+1 → 复制不递增
```

## 50.9 随机输入测试

对 URI Parser 与 Base32 做随机 / 变异输入测试：任何输入都只能返回结构化错误，不能抛出未处理异常。

## 50.10 静态检查

- release 构建 R8：`-assumenosideeffects class android.util.Log { *; }`
- detekt `ForbiddenMethodCall`：禁止 `println`、`android.util.Log`
- Android Lint：`DefaultLocale` 等检查开启为 error
- CI：最终 APK 权限检查（§34）

---

# 51. 测试覆盖率

目标：`:core` 与安全相关代码 ≥ 80%（Kover 或 JaCoCo）。重点：OTP、Base32、URI Parser、Crypto、Repository、Lock / PIN。

覆盖率不是唯一质量指标，标准测试向量和异常场景同样重要。

---

# 52. 性能目标

| 指标 | 目标 |
|---|---|
| 冷启动到锁屏可交互 | < 1 秒 |
| PIN 校验（KDF） | 300–500 ms（有意为之） |
| 解锁到账号列表可见 | < 500 ms（100 个账号） |
| 单次 OTP 生成 | 微秒级，无需专门优化 |
| 账号列表滚动 | ≥ 55 FPS（5–100 个账号） |

KDF 与 Keystore 操作在后台线程执行。

---

# 53. APK 大小

目标 APK < 15 MB（预计 3–6 MB）。

- release 开启 R8 与资源压缩
- 不引入网络、分析、大型 UI 框架 SDK
- 避免 `material-icons-extended`，按需复制所需图标
- 优先纯 Java/Kotlin 依赖，不引入 native 库

---

# 54. 数据流

## 54.1 TOTP

```text
java.time.Clock → Unix Timestamp → floorDiv(T, period)
   → VaultSession 中的 Secret + Counter → HMAC → Dynamic Truncation
   → mod 10^digits → padStart → OTP
```

## 54.2 QR 添加完整流程

```text
添加页 → 点击"扫描二维码" → 请求 Camera 权限 → CameraX Preview
   → ZXing 解码 → otpauth:// 字符串
   → OtpUriParser（含 Base32 解码与校验）→ OtpUri
   → PendingAccountHolder（内存）
   → 确认页（含重复检测与警告）→ 用户确认
   → TokenRepository.add → VaultSession → AES-GCM Encrypt → DataStore
   → 清空 PendingAccountHolder
```

---

# 55. 安全边界

绝对禁止：

```kotlin
Log.d("OTP", secret)
Log.d("OTP", account.toString())
println(secret)
SharedPreferences.putString("secret", secret)
intent.putExtra("secret", secret)
navController.navigate("confirm/$otpauthUri")
savedStateHandle["secret"] = secret
rememberSaveable { mutableStateOf(secretInput) }
data class Foo(val secret: String)          // 自动 toString() 泄漏
throw IllegalArgumentException("bad secret: $input")
```

---

# 56. 错误处理

按层定义结构化错误：

```kotlin
sealed interface ParseError {
    data object InvalidUri : ParseError                  // 超长（> 4096 字符）等结构性问题
    data object InvalidScheme : ParseError
    data object UnsupportedMigrationFormat : ParseError   // otpauth-migration://
    data object InvalidType : ParseError
    data object MissingSecret : ParseError
    data object DuplicateSecret : ParseError
    data class InvalidSecret(val problem: SecretProblem) : ParseError  // 不含输入内容
    data object InvalidAlgorithm : ParseError
    data object InvalidDigits : ParseError
    data object InvalidPeriod : ParseError
    data object InvalidCounter : ParseError
}

enum class SecretProblem { EMPTY, INVALID_CHARACTER, LOOKALIKE_DIGIT, MISPLACED_PADDING, INVALID_LENGTH, TOO_LONG }

// 以下为简写，成员形式同 ParseError
sealed interface VaultError { KeyMissing; AuthenticationFailed; Corrupted; UnsupportedVersion; CryptoUnavailable; Io }
sealed interface AuthError  { WrongPin; RateLimited(untilMillis); BiometricUnavailable; Cancelled }
sealed interface CameraError { PermissionDenied; PermissionPermanentlyDenied; Unavailable }
```

UI 显示用户能理解的信息，例如：

```text
二维码不是有效的认证器二维码
暂不支持从 Google Authenticator 导入
Secret 包含无效字符，请检查是否把字母 O/I/B 输成了数字 0/1/8
```

而不是 `IllegalArgumentException: Base32 decode failed...`。错误与异常 message 中不得包含 Secret 或用户输入。

---

# 57. 时间处理

TOTP 依赖 Unix 时间。直接使用 `java.time.Clock`（API 26+ 原生）：

- 生产：`Clock.systemUTC()`
- 测试：`Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC)`

---

# 58. 时间异常处理

手机时间不准确时，TOTP 无法通过服务器验证。

- 读取 `Settings.Global.AUTO_TIME`（无需权限），若关闭，在列表顶部显示提示："系统时间未设为自动，验证码可能无效"
- 帮助文案："验证码无效时，请检查手机的日期和时间是否设置为自动。"

不在客户端实现服务器时间同步：v0.1 无网络，自动时间同步属于 Android 系统能力。

---

# 59. 安全威胁模型

## 59.1 重点防御

| 威胁 | 防护 |
|---|---|
| 本地文件泄漏（带离设备） | AES-GCM 整库加密，密钥在 Keystore，不可导出 |
| 系统 / 厂商备份、设备迁移 | `allowBackup=false` + `dataExtractionRules` 全排除（§35） |
| 依赖库引入网络能力 | `tools:node="remove"` + CI 检查最终 APK（§34） |
| PIN 离线穷举 | PIN 校验值由 Keystore 加密（§30.2） |
| PIN 在线穷举 | 持久化失败计数 + 指数退避（§30.3） |
| 截图 / 录屏 / 最近任务缩略图 | 全局 FLAG_SECURE |
| Log 泄漏 | 禁止记录；R8 剥离 Log；detekt 规则 |
| Secret 进入 UI / 保存状态 | Secret 边界在 Repository 之下；禁止 saved state 承载（§36） |
| 进程重建绕过锁 | 根部门禁，`locked` 默认 true 且不保存（§28） |
| 未授权打开已解锁的 App | 自动上锁（§29） |
| 数据篡改 | AES-GCM Authentication Tag + AAD |

## 59.2 已知局限（写明，不承诺防御）

| 项 | 说明 |
|---|---|
| 应用锁非密码学绑定 | v0.1 锁为 UI 层访问控制（§27）；UI 漏洞可能绕过 |
| 数据丢失（可用性） | 卸载、清除数据、恢复出厂、换机、Keystore 异常 → 全部 2FA 丢失；缓解：引导提示保存恢复码，v0.2 加密导出 |
| 无障碍服务读屏 | FLAG_SECURE 无效；API 34+ 使用 `accessibilityDataSensitive` 部分缓解 |
| 输入法剪贴板历史 / 云剪贴板 | 不受 App 控制；OTP 敏感标记 + 30 秒清除 |
| 回滚 | GCM 不防"用旧的合法密文替换当前文件"（如 HOTP counter 回退）；需要文件写权限，属已排除能力 |

## 59.3 不承诺防御

- 已 Root 且攻击者完全控制设备
- 内核级恶意软件
- 已被完全攻陷的设备
- 用户主动泄露 PIN 或 Secret

---

# 60. v0.1 验收标准

## 功能

- [ ] 首次启动引导（风险提示 + 设置 PIN）
- [ ] 可以添加 TOTP / HOTP
- [ ] 可以扫描 `otpauth://`，识别 `otpauth-migration://` 并给出提示
- [ ] 可以手工输入 Secret（含空格分组格式）
- [ ] 可以管理多个账号，按 issuer 排序
- [ ] OTP 正确生成，TOTP 倒计时正常
- [ ] HOTP 生成先持久化后显示，复制不递增
- [ ] OTP 可以复制，30 秒后清除
- [ ] 可以编辑、删除账号
- [ ] App 重启后数据仍存在
- [ ] 设置页：生物识别开关、修改 PIN、自动上锁时长

## 安全

- [ ] Secret、issuer、账号名不以明文写入任何文件（自动化测试）
- [ ] Secret 不进入 UI State、ViewModel、saved state、导航参数
- [ ] Secret 不进入 Logcat
- [ ] AES-256-GCM 正常工作，篡改检测生效
- [ ] AES Key 存在 Android Keystore
- [ ] 支持 Biometric 与 PIN；PIN 尝试次数限制生效且重启不清零
- [ ] 进程被杀后从最近任务恢复，必须先解锁
- [ ] 后台超时自动上锁
- [ ] 全局启用 FLAG_SECURE
- [ ] **最终 APK** 不含 INTERNET 权限（CI 检查）
- [ ] 系统备份与设备迁移已排除（真机验证）
- [ ] 密钥缺失时进入 `Unreadable`，不覆盖数据

## 标准

- [ ] RFC 4226 HOTP 测试向量通过
- [ ] RFC 6238 TOTP 测试向量通过（SHA1 / SHA256 / SHA512，8 位，正确种子长度）
- [ ] Base32 测试通过
- [ ] otpauth URI 测试通过（含 §15 边界规则）

---

# 61. 后续版本路线

## v0.2

- **加密导出 / 导入**（提前自原 v0.3，缓解数据丢失风险）
- 升级锁模型为 key slot（与加密导出共用口令派生密钥机制，见 §27）
- 分组、标签、自定义排序
- AMOLED 黑色主题
- 服务图标（本地内置，不联网）

## v0.3

- 更完善的数据迁移机制
- `otpauth://` 外部链接打开

## v0.4+

- 从其他认证器导入（如 `otpauth-migration://`）
- 更完整的标准兼容
- 多设备迁移
- 更丰富的安全策略

---

# 62. 备份设计原则

备份不应直接导出：

```json
{ "secret": "JBSWY3DPEHPK3PXP" }
```

未来备份：

```text
Backup Password → KDF（带 salt，参数随文件保存）→ Encryption Key → AES-256-GCM → Encrypted Backup
```

Android Keystore 中的 Key 无法复制到另一台设备，因此：

**设备内加密和跨设备备份加密是两套机制。**

---

# 63. 设计决策总结

## 决策 1：DataStore，而不是 Room

v0.1 数据量小、实现简单、依赖少，足够个人使用。DataStore 只存密文，加解密在 Repository 层（§22）。复杂查询时再引入 Room。

## 决策 2：自己实现 OTP 核心

RFC 4226/6238 算法不复杂；完全掌握实现；降低第三方运行时依赖；方便测试标准向量；核心逻辑保持 Android 无关。

## 决策 3：QR 必须属于 v0.1

实际网站（如 GitHub）配置 2FA 时通常提供二维码，QR 是核心使用流程。另提供"从图片识别"，覆盖在本机上开通 2FA 的场景。

## 决策 4：TOTP + HOTP 都进入 v0.1

TOTP 本质是 HOTP + Time Counter，算法成本很低。但 HOTP 的真实成本在 counter 状态管理、按钮语义和失步处理（§20），估时按"有状态功能"计算。

## 决策 5：ZXing，而不是 ML Kit

纯 Java、无网络、无 GMS、无 native 库，与"离线、无 INTERNET、无 GMS"原则一致。

---

# 64. 最终 v0.1 架构

```text
                           SecureAuth
                               │
                          LockGate（根部门禁）
               ┌───────────────┴────────────────┐
               │                                │
          Compose UI                        Security
               │                    ┌──────────┼──────────┐
               ▼                Biometric     PIN     AutoLock
           ViewModel
               │  AccountMeta / OtpCode（无 Secret）
               ▼
   TokenRepository / OtpCodeService
               │
          VaultSession  ←──── Secret 边界
        ┌──────┴──────────────┐
        ▼                     ▼
   OTP Core（:core）      VaultStorage
    ├── HOTP                  │ AES-256-GCM
    ├── TOTP            ┌─────┴──────┐
    ├── Base32          ▼ 密钥       ▼ 密文
    └── otpauth   Android Keystore  DataStore
```

---

# 65. 第一版开发顺序

锁模型决定密钥层级与存储格式，锁门禁决定整个导航结构，因此安全基础设施前置。

```text
1. 工程骨架
   :core（纯 JVM）+ :app；MainActivity 继承 FragmentActivity
   Manifest 加固：tools:node="remove"、allowBackup=false、dataExtractionRules
   全局 FLAG_SECURE；R8 日志剥离；detekt / Lint
   CI：单测 + 最终 APK 权限检查
        ↓
2. core：model / Secret → Base32 → HOTP → TOTP → RFC 向量测试
        ↓
3. core：otpauth Parser（含 §15 边界规则与随机输入测试）
        ↓
4. core：AeadCipher 接口 + JVM 实现 + VaultEnvelope + Crypto 测试
        ↓
5. Keystore 实现 + instrumented 测试
        ↓
6. VaultStorage / VaultSession / Repository / OtpCodeService
   （重启测试、明文不落盘测试、Unreadable 测试）
        ↓
7. LockGate + 引导 + PIN（设置 / 解锁 / 限速）+ 自动上锁
        ↓
8. Biometric + 设置页
        ↓
9. 账号列表 + ticker + 复制 / 剪贴板
        ↓
10. 手工添加
        ↓
11. QR Scanner（CameraX + ZXing）+ 从图片识别
        ↓
12. 确认页（重复检测、警告）
        ↓
13. 编辑 / 删除
        ↓
14. UI 测试
        ↓
15. 真机测试：国产 ROM、鸿蒙 4.2、无 GMS 设备、飞行模式、进程死亡、换机工具
        ↓
16. 安全检查（§60 安全项逐条验证）
        ↓
17. v0.1 Release
```

---

# 66. 决策记录

| 编号 | 决策 | 结论 | 理由 |
|---|---|---|---|
| D1 | 应用锁是否与加密绑定 | v0.1：UI 门禁 + 加固；v0.2：key slot | 威胁模型已排除 root；未 root 设备上绑定的增量收益主要是防 UI 漏洞；key slot 与加密导出共用口令派生机制，合并实现最省；envelope 已预留 `keyId` |
| D2 | PIN 形式 | 6–12 位数字 | 兼顾易用；安全性依赖 Keystore 加密校验值 + 在线限速 |
| D3 | HOTP 缺少 counter | 默认 0 并在确认页提示 | 兼容不规范的二维码，由用户确认 |
| D4 | 剪贴板清除 | 30 秒后无条件清除 | 后台无法读取剪贴板判断内容；OTP 短时有效，误清代价小于残留风险 |
| D5 | QR 解码库 | ZXing core | 见 §63 决策 5 |
| D6 | 存储加密位置 | Repository 层，DataStore 只存密文 | 避免明文常驻 DataStore 缓存；兼容未来 CryptoObject |
| D7 | 模块划分 | `:core` 纯 JVM + `:app` | 编译期强制核心层不依赖 Android |
| D8 | 鸿蒙支持范围 | 兼容鸿蒙 4.x（含 4.2）；不支持 HarmonyOS NEXT；侧载安装，不上架 | 个人自用；4.x 通过 Android 兼容层运行，现有设计已不依赖 GMS，无需额外代码 |
| D9 | Keystore 参数与自动上锁 | 优先 StrongBox 并回退；不开启 `setUnlockedDeviceRequired`；自动上锁默认 1 分钟 | 见 `docs/adr/0001-lock-and-key-model.md` |

参考：开源的 Aegis Authenticator 目标与本方案接近，其 `docs/vault.md` 的金库格式值得参考。Aegis 为 GPLv3，本项目为 MIT——只借鉴设计，不复制代码。

---

# 67. v0.1 Definition of Done

```text
✓ 首次启动引导（风险提示 + PIN）
✓ TOTP / HOTP 可正常生成
✓ QR 可扫描，可从图片识别
✓ otpauth URI 可解析（含边界规则）
✓ 手工添加可用
✓ 多账号可用
✓ OTP 可复制，30 秒清除
✓ TOTP 倒计时正常
✓ HOTP 先持久化后显示
✓ 编辑 / 删除可用
✓ 数据重启后仍存在
✓ AES-GCM 整库加密，明文不落盘
✓ Android Keystore
✓ Biometric + PIN（含限速）
✓ 自动上锁；进程重建必须解锁
✓ 全局 FLAG_SECURE
✓ 最终 APK 无 INTERNET 权限
✓ 系统备份 / 设备迁移已排除
✓ 金库不可读时不覆盖数据
✓ RFC 4226 测试通过
✓ RFC 6238 测试通过
✓ Base32 测试通过
✓ URI Parser 测试通过
✓ 真机离线环境正常运行（含无 GMS 设备）
```

这将构成 SecureAuth 的第一个真正可用版本。
