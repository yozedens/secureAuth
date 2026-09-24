# SecureAuth

个人离线 TOTP/HOTP 认证器（Android）。设计与计划见：

- [`docs/SecureAuth-v0.1-design.md`](docs/SecureAuth-v0.1-design.md)
- [`docs/SecureAuth-v0.1-plan.md`](docs/SecureAuth-v0.1-plan.md)

## 模块

| 模块 | 类型 | 说明 |
|---|---|---|
| `:core` | 纯 Kotlin/JVM | OTP、Base32、otpauth 解析、加密抽象；不能依赖 Android |
| `:app` | Android 应用 | UI、存储、安全 |

## 构建

需要 JDK 17+ 与 Android SDK（`platforms;android-35`）。

```bash
./gradlew :core:test            # core 单元测试
./gradlew detektMain            # 静态检查（含类型解析）
./gradlew :app:lintRelease      # Android Lint
./gradlew :app:assembleRelease  # release APK（未签名）
scripts/check-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

`scripts/check-apk.sh` 检查最终 APK：无 INTERNET / ACCESS_NETWORK_STATE 权限、`allowBackup=false`、声明了 `dataExtractionRules`、非 debuggable。

## 安全约定

- 禁止 `android.util.Log`、`println`、`printStackTrace`（detekt 强制；release 构建由 R8 移除 Log 调用）
- Secret 不得进入日志、UI State、保存状态、导航参数、Intent（设计 §9、§55）
- 仓库中只允许出现测试密钥
