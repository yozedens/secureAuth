# SecureAuth v0.1 开发计划与环境要求

> 依据：`docs/SecureAuth-v0.1-design.md`（修订 1）。文中 `§n` 指设计文档章节。
>
> 估时单位为**理想人天**（1 名熟悉 Kotlin/Compose 的开发者，专注工作 6 小时 / 天），已按任务拆分，**另加 20% 缓冲**。

---

# 1. 总览

## 1.1 里程碑

| 里程碑 | 内容 | 估时（人天） | 退出标准 |
|---|---|---:|---|
| M0 | 工程骨架与安全基线 | 3 | CI 绿；最终 APK 无 INTERNET；空壳 App 全局 FLAG_SECURE |
| M1 | core：算法与解析 | 4 | RFC 4226/6238 向量、Base32、URI 解析测试全部通过；`:core` 覆盖率 ≥ 80% |
| M2 | 加密与存储 | 5 | 重启持久化、明文不落盘、篡改检测、`Unreadable` 测试通过（含真机 / 模拟器） |
| M3 | 应用锁与安全 | 5 | 引导、PIN、限速、生物识别、自动上锁可用；进程死亡后必须解锁 |
| M4 | 账号列表与复制 | 4 | TOTP 倒计时、HOTP 生成语义、剪贴板策略可用 |
| M5 | 添加 / 扫码 / 编辑 / 删除 | 5.5 | 手工添加、扫码、从图片识别、确认页、编辑、删除全流程可用 |
| M6 | 测试与加固 | 5.5 | UI 测试通过；真机矩阵（含鸿蒙 4.2）通过；§60 安全项逐条验证 |
| M7 | 发布 | 1 | 签名 release APK；v0.1 tag |
| | **小计** | **33** | |
| | **含 20% 缓冲** | **≈ 40** | |

## 1.2 关键路径

```text
M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7
          │
          └─ M1 的 URI 解析（T1.5）可与 M2 并行
M3 的 ADR（T3.1）应在 M2 开始前完成（决定密钥层级与存储格式）
```

---

# 2. 任务拆解（WBS）

每个任务单独提交 / PR，PR 内包含对应测试。"验收"一栏即该任务的 Definition of Done。

## M0 工程骨架与安全基线（3 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T0.1 | Gradle 多模块与版本目录 | `settings.gradle.kts`、`:core`（`kotlin("jvm")`）、`:app`、`gradle/libs.versions.toml`、Gradle Wrapper | — | 1 | `./gradlew build` 通过；`:core` 中 `import android.*` 编译失败 |
| T0.2 | Manifest 与 Activity 基线（§34、§35、§38） | `tools:node="remove"` 剔除 INTERNET / ACCESS_NETWORK_STATE；`allowBackup=false`；`data_extraction_rules.xml`；`MainActivity : FragmentActivity`；全局 FLAG_SECURE；`enableEdgeToEdge()` | T0.1 | 0.5 | 合并后 Manifest 符合要求；截图为黑屏 |
| T0.3 | 静态检查与 R8（§50.10） | detekt（`ForbiddenMethodCall`：`println`、`android.util.Log`）；Lint `DefaultLocale` 设为 error；release R8 + `-assumenosideeffects` Log | T0.1 | 0.5 | 故意违规时构建失败 |
| T0.4 | CI | GitHub Actions：`:core` 单测 + detekt + lint + `assembleRelease` + `apkanalyzer` 权限检查；独立的模拟器 instrumented 任务（手动 / 夜间触发） | T0.2、T0.3 | 1 | PR 上 CI 绿；加入含 INTERNET 的依赖时 CI 失败 |

## M1 core：算法与解析（4 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T1.1 | 模型（§8、§9） | `OtpKind`、`Algorithm`、`AccountMeta`、`OtpAccount`、`Secret`、错误类型（§56） | T0.1 | 0.5 | `Secret` 的 toString / equals / 构造复制测试 |
| T1.2 | Base32（§13） | `Base32.decode` | T1.1 | 1 | §50.3 全部用例；错误信息不含输入 |
| T1.3 | HOTP（§10） | `Hotp.generate` | T1.1 | 0.5 | RFC 4226 附录 D 10 组向量 |
| T1.4 | TOTP（§11） | `Totp.generate`，使用 `java.time.Clock` | T1.3 | 0.5 | RFC 6238 附录 B 18 组向量（正确种子长度，8 位） |
| T1.5 | otpauth 解析器（§14、§15） | `OtpUriParser` | T1.2 | 1.5 | §15 表格逐行用例 + §50.4 异常用例 + 随机输入测试（只返回结构化错误） |

## M2 加密与存储（5 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T2.1 | AEAD 抽象与 envelope（§25） | `AeadCipher` 接口、JVM 实现、`VaultEnvelope` 编解码（AAD = 头部） | T1.1、T3.1 | 1 | §50.5 JVM 用例：错 key / IV / tag / AAD、篡改、空数据、1 MB |
| T2.2 | Keystore 实现（§24） | `KeystoreManager`、`KeystoreAeadCipher`；可选 StrongBox 回退 | T2.1 | 1 | instrumented：加解密、篡改检测、IV 由 Keystore 生成、密钥缺失返回 `KeyMissing` |
| T2.3 | VaultStorage / VaultSession（§22、§23、§26） | `DataStore<VaultEnvelope>`（Serializer 不解密）、Vault JSON（kotlinx.serialization，`schemaVersion`）、`VaultState` 状态机 | T2.2 | 1.5 | 重启后数据仍在；明文不落盘；`Unreadable` 时原文件不变 |
| T2.4 | Repository / OtpCodeService（§21） | `TokenRepository`、`OtpCodeService`（`totpAt`、原子 `nextHotp`）、重复检测 | T2.3、T1.4 | 1.5 | §50.6 全部用例；并发 `nextHotp` 严格递增 |

## M3 应用锁与安全（5 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T3.1 | 安全 ADR | `docs/adr/0001-lock-and-key-model.md`：锁模型、密钥层级、PIN 校验、锁生命周期、存储清单（固化 §66 的 D1–D7） | — | 0.5 | 评审通过后才开始 T2.1 |
| T3.2 | 锁控制与门禁（§28、§29） | `AppLockController`（内存、默认 locked）、`LockGate`、`ProcessLifecycleOwner` 自动上锁、上锁时丢弃 `VaultSession` | T2.3 | 1 | `am kill` 后恢复显示锁屏；超时上锁测试 |
| T3.3 | PIN（§30） | `PinManager`：PBKDF2 标定、校验值加密存储、失败计数与退避、修改 PIN | T2.2 | 1.5 | 正确 / 错误 / 退避 / 重启保留计数；KDF 不在主线程 |
| T3.4 | 引导与 PIN 设置（§32） | `OnboardingScreen`、`SetupPinScreen`、首次初始化生成密钥与空 Vault | T3.2、T3.3 | 1 | 首次启动流程完整；未勾选"我已了解"不能继续 |
| T3.5 | 生物识别与设置页（§31） | `BiometricAuthenticator`、`LockScreen`、`SettingsScreen`（生物识别开关、修改 PIN、自动上锁时长） | T3.4 | 1 | `canAuthenticate` 各分支；失败回落 PIN |

## M4 账号列表与复制（4 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T4.1 | 主题与导航骨架 | M3 主题（跟随系统深色）、`AppNavHost`、ViewModel 工厂（`AppContainer`，`open` 以便替换 fake） | T3.2 | 0.5 | 导航参数中无敏感数据（代码审查项） |
| T4.2 | 全局 ticker（§19） | 秒边界对齐、`WhileSubscribed` | T1.4 | 0.5 | 虚拟时间测试：不漂移、后台停止 |
| T4.3 | 账号列表（§18、§42、§47） | `AccountListViewModel`、`AccountUiModel`、TOTP 卡片与倒计时、HOTP 生成语义（§20）、排序 | T2.4、T4.2 | 1.5 | HOTP 先持久化后显示、复制不递增；ViewModel 不引用 `Secret` |
| T4.4 | 剪贴板（§33） | `SecureClipboard`：敏感标记、应用级 30 秒清除 | T4.3 | 0.5 | API 33+ 与更低版本行为；后台不清除 |
| T4.5 | 搜索与时间提示（P1-02、P1-04） | 过滤；`AUTO_TIME` 横幅 | T4.3 | 1 | 关闭自动时间时显示提示 |

## M5 添加 / 扫码 / 编辑 / 删除（5.5 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T5.1 | 添加入口与手工添加（§17、§43） | `AddAccountScreen`、`ManualEntryScreen`（高级选项折叠、Password 键盘、状态在 ViewModel） | T4.1、T2.4 | 1.5 | 带空格粘贴可解析；旋转屏幕输入不丢；无 `rememberSaveable` |
| T5.2 | 扫码（§16、§39） | CameraX `ImageAnalysis` + ZXing；权限请求 / 拒绝 / 永久拒绝；首个有效结果后停止 | T1.5 | 1.5 | 测试二维码集（§4.3）全部识别正确 |
| T5.3 | 从图片识别（P1-01） | Photo Picker + ZXing 位图解码 | T5.2 | 0.5 | 截图中的二维码可识别 |
| T5.4 | 确认页（§44） | `PendingAccountHolder`（流程级共享 ViewModel）、重复检测、HOTP 缺 counter 警告、账号为空时必填 | T5.2、T2.4 | 1 | 取消 / 完成后暂存清空；进程重建后暂存不恢复 |
| T5.5 | 编辑与删除（§45、§46） | `EditAccountScreen`、参数修改警告、替换 Secret、删除二次确认 | T4.3 | 1 | 编辑后验证码正确；删除后不可恢复 |

## M6 测试与加固（5.5 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T6.1 | UI 测试（§50.8） | Compose UI 测试：fake 生物识别 / 相机帧 / 时钟 | M5 | 2 | §50.8 三条路径通过 |
| T6.2 | 真机矩阵测试 | 按 §4.2 设备逐台执行测试用例表；鸿蒙 4.2 设备额外执行 §4.2.1 专项 | M5 | 2.5 | 所有 P0 用例通过，问题入 issue |
| T6.3 | 安全检查 | 逐条验证 §60 安全项；检查合并 Manifest 的导出组件；`strings`/grep 检查 APK 与数据文件 | T6.2 | 1 | 安全检查表全部勾选 |

## M7 发布（1 人天）

| ID | 任务 | 产出 | 依赖 | 估时 | 验收 |
|---|---|---|---|---:|---|
| T7.1 | 发布 | release 签名、APK 大小检查（< 15 MB）、`CHANGELOG`、`v0.1.0` tag、APK SHA-256 | M6 | 1 | 离线真机安装运行正常 |

---

# 3. 开发环境

> 逐步安装与使用说明见 [`guide-dev-environment.md`](guide-dev-environment.md)（开发 / 测试环境）与 [`guide-build-release.md`](guide-build-release.md)（打包、签名、发布）。

## 3.1 硬件

| 项 | 最低 | 推荐 | 说明 |
|---|---|---|---|
| CPU | 4 核 64 位，支持硬件虚拟化（Intel VT-x / AMD-V）或 Apple Silicon | 8 核以上 | 模拟器依赖硬件加速 |
| 内存 | 16 GB | 32 GB | Android Studio + Gradle 守护进程 + 1–2 个模拟器 |
| 磁盘 | 80 GB 可用 SSD | 150 GB 可用 NVMe | SDK、多个 API 级别系统镜像、Gradle 缓存 |
| 显示 | 1920×1080 | 2 块屏 | 其中一块用于显示测试二维码 |
| USB | 1 根支持数据传输的线 | 每台测试机一根 | 真机调试 |

## 3.2 软件

| 项 | 要求 |
|---|---|
| 操作系统 | macOS 13+ / Windows 10/11 64 位 / 主流 64 位 Linux |
| JDK | 17（与设计文档一致，AGP 要求 17+）；用 Gradle toolchain 固定 |
| Android Studio | 当前稳定版 |
| Android SDK | Platform `android-35`（compileSdk / targetSdk）、Build-Tools、Platform-Tools（adb）、Emulator、Command-line Tools（含 `apkanalyzer`） |
| 系统镜像 | API 26、30、34、35（Google APIs 版），可选 AOSP 无 GMS 版 |
| Gradle | 使用仓库内 Gradle Wrapper，不依赖本机安装 |
| Kotlin / Compose / AndroidX | 版本在 T0.1 统一锁定于 `libs.versions.toml` |
| Git | 2.30+；启用提交签名（可选） |
| 其他 | Python 3.10+（测试辅助脚本）；`qrencode`；`oathtool`（oath-toolkit） |

版本在 T0.1 选定后写入 `libs.versions.toml`，此后升级需单独 PR。

## 3.3 主要依赖（全部为无网络、无 GMS 依赖）

| 依赖 | 用途 |
|---|---|
| Jetpack Compose BOM、Material 3 | UI |
| androidx.activity / lifecycle（含 `lifecycle-process`）/ navigation-compose | 生命周期、导航 |
| androidx.fragment | `FragmentActivity`（BiometricPrompt） |
| androidx.biometric | 生物识别 |
| androidx.datastore（core，非 preferences） | 密文存储 |
| androidx.camera（camera2、lifecycle、view） | 扫码 |
| com.google.zxing:core | QR 解码 |
| kotlinx-serialization-json、kotlinx-coroutines | 序列化、并发 |
| 测试：JUnit、kotlinx-coroutines-test、Turbine、Compose UI Test、AndroidX Test、Kover | 测试 |
| 质量：detekt、Android Lint | 静态检查 |

新增任何依赖都必须通过 T0.4 的权限检查。

## 3.4 仓库与流程

- 默认分支 `main` 开启保护：必须 PR、必须 CI 通过
- 分支命名：`feat/T1.2-base32` 等，按任务 ID
- 提交粒度：一个任务一个 PR，PR 描述引用任务 ID 与设计文档章节
- 设计变更先改文档再改代码

## 3.5 Claude Code 云端会话（如需让 Claude 参与编码）

当前云端环境的实测情况：

| 项 | 状态 |
|---|---|
| JDK 21、Gradle 8.14 | 已安装 |
| Maven Central、Gradle Plugin Portal、services.gradle.org | 可访问 |
| Google Maven（`dl.google.com` / `maven.google.com` 的实际下载地址） | **被网络策略拦截** |
| Android SDK | 未安装 |
| 资源 | 4 核 / 15 GB 内存 |

因此：

- `:core`（纯 JVM）可在云端直接构建与测试
- `:app` 需要：在环境网络策略中放行 `dl.google.com`（以及 `maven.google.com`），并在环境安装脚本中安装 Android command-line tools 与 SDK `platforms;android-35`、`build-tools`
- 模拟器与真机测试无法在云端进行，只能在本地或 CI 执行

---

# 4. 测试环境

## 4.1 模拟器（本地与 CI）

| AVD | API | 镜像 | 用途 |
|---|---|---|---|
| Min | 26 | Google APIs | 最低版本：PBKDF2、剪贴板旧键、无 `clearPrimaryClip` |
| Legacy backup | 30 | Google APIs | API ≤ 30 的备份行为、旧剪贴板行为 |
| Current | 34 | Google APIs | `accessibilityDataSensitive`、Photo Picker |
| Target | 35 | Google APIs | edge-to-edge、目标版本行为 |
| No-GMS | 34 | AOSP（无 Google APIs） | 验证不依赖 GMS |

模拟器可覆盖：Keystore（软件实现）、指纹（`adb -e emu finger touch 1`）、相机（虚拟场景或主机摄像头）、进程死亡（`adb shell am kill`）、时间修改。

模拟器**不能**覆盖：StrongBox、硬件级 Keystore 行为、真实生物识别等级、厂商 ROM 行为、换机工具。

## 4.2 真机矩阵

| 优先级 | 设备类型 | 验证重点 |
|---|---|---|
| 必需 | 近两年的 Pixel（有 GMS，Android 15/16，指纹） | 基准设备；StrongBox；Google 备份排除验证 |
| 必需 | 国行手机，无 GMS（小米 HyperOS / OPPO ColorOS / vivo OriginOS / 荣耀 MagicOS 任一） | 无 GMS 运行；厂商换机 / 本地备份工具；后台杀进程；生物识别等级 |
| 建议 | 另一品牌国行手机 | 厂商差异 |
| 建议 | Android 8–10 旧手机 | 最低版本真机行为、性能下限（KDF 标定） |
| 可选 | 仅人脸解锁或无生物识别的设备 | 回落 PIN 流程 |
| 可选 | 平板或折叠屏 | 布局与配置变更 |

| 必需 | 华为鸿蒙 4.2 设备（无 GMS） | 鸿蒙兼容层运行；见 §4.2.1 |

HarmonyOS NEXT（5.0 及以上）不运行 APK，不在支持与测试范围内。

### 4.2.1 鸿蒙 4.2 专项用例

| 用例 | 预期 |
|---|---|
| 记录 `Build.VERSION.SDK_INT` | ≥ 26，写入测试报告 |
| 关闭纯净模式后侧载安装 APK | 安装成功；重新开启纯净模式后 App 仍可正常使用 |
| 首次启动、生成 Keystore 密钥、写入 / 读取 Vault | 正常；记录 StrongBox 是否可用 |
| 指纹解锁；仅人脸时的表现 | 指纹可用；人脸不可用时回落 PIN |
| 扫码（CameraX + ZXing） | 识别测试二维码集 |
| 从图片识别 | 回退到系统文件选择器，可选图并识别 |
| FLAG_SECURE | 截图、录屏、最近任务为黑屏 |
| 华为"手机克隆" / "备份与恢复" | 不迁移 App 数据；若迁移，新设备进入 `Unreadable` 且不覆盖 |
| 进程被杀 / 后台清理后恢复 | 必须重新解锁 |
| 飞行模式全流程 | 正常 |

## 4.3 测试辅助资源

| 资源 | 用途 |
|---|---|
| `oathtool` / Python `pyotp` | 独立计算 TOTP/HOTP，与 App 结果交叉核对 |
| `qrencode` 或 Python `qrcode` | 离线生成测试二维码 |
| 测试二维码集（纳入仓库 `testdata/qr/`，只含测试密钥） | 标准 TOTP / HOTP、URL 编码、`+` 号账号、原始空格、大小写混合、缺 counter、非法 digits、`otpauth-migration://`、非 otpauth 二维码、超长字段 |
| 第二块屏幕或另一台设备 | 显示二维码供扫描 |
| 专用测试账号（如 GitHub、Google 测试号） | 端到端开通 / 验证 2FA；**不使用个人主账号** |
| 本地 HOTP 验证脚本（pyotp，带 look-ahead 窗口） | 验证 HOTP counter 同步与重新同步 |

测试数据约定：仓库内只允许出现测试密钥（如 RFC 向量、`JBSWY3DPEHPK3PXP`），禁止提交任何真实账号的 Secret。

## 4.4 离线条件

- 真机测试全程开启飞行模式（扫码、添加、生成、复制、重启）
- 验证 APK 在从未联网的新设备上首次启动可用

## 4.5 CI 环境

| 任务 | Runner | 触发 |
|---|---|---|
| `:core` 单测、detekt、lint、`assembleRelease`、权限检查 | GitHub 托管 `ubuntu-latest` | 每个 PR |
| instrumented 测试（模拟器，API 26 与 35） | `ubuntu-latest` + KVM，`reactivecircus/android-emulator-runner` | 手动 / 夜间 |

私有仓库注意 GitHub Actions 分钟数额度，模拟器任务耗时较长，故不在每个 PR 上运行。

---

# 5. 其他要求

## 5.1 签名密钥

- 生成 release keystore，**离线保存并至少备份两份**（丢失后无法发布更新，已安装用户只能卸载重装——也就意味着数据丢失）
- keystore 与密码不进入仓库；CI 如需签名，使用加密的 Secrets
- 发布时附 APK 的 SHA-256，便于校验
- 具体步骤见 `docs/release.md`

## 5.2 安全检查清单（T6.3 使用）

逐项的验证方式与记录表见 `docs/security-checklist.md`。

- [ ] 合并后 Manifest：无 INTERNET、无意外导出组件、`allowBackup=false`、`dataExtractionRules` 生效
- [ ] APK 中无测试密钥以外的 Secret（`strings` / grep）
- [ ] 数据目录文件无 secret / issuer / 账号名明文
- [ ] Logcat 全流程无 Secret
- [ ] 截图、录屏、最近任务均为黑屏
- [ ] 进程死亡恢复后必须解锁
- [ ] PIN 退避在重启后仍生效
- [ ] 厂商换机 / 本地备份工具（含华为"手机克隆""备份与恢复"）不迁移数据，或迁移后进入 `Unreadable` 且不覆盖
- [ ] release 为非 debuggable

## 5.3 文档

- 设计变更：更新 `docs/SecureAuth-v0.1-design.md` 并记录在 §66
- 架构决策：`docs/adr/`
- 测试用例表：`docs/test-cases.md`（T6.2 前完成，供真机矩阵逐项执行）
- 安全检查表：`docs/security-checklist.md`（T6.3）
- 测试二维码集：`testdata/qr/`，由 `scripts/gen-test-qr.py` 生成

## 5.4 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 厂商 ROM 的 Keystore / 生物识别行为差异 | 解锁失败、数据不可读 | 尽早在 M2 就上国行真机跑 instrumented 测试 |
| 厂商备份工具无视 `allowBackup` | 数据迁出或恢复后不可读 | `Unreadable` 状态兜底；真机验证并在文档中记录 |
| 鸿蒙 4.2 兼容层行为与原生 Android 有差异 | 个别功能异常 | §4.2.1 专项用例；问题优先在该设备上复现与修复 |
| KDF 在低端机过慢 | 解锁体验差 | 旧机上标定迭代次数；参数随校验值存储，可调整 |
| 依赖更新带入网络权限 | 违反 F17 | CI 权限检查阻断 |
| 单人开发时间波动 | 延期 | 按里程碑交付，P1 项（T4.5、T5.3）可延后 |
