# SecureAuth

个人离线 TOTP/HOTP 认证器（Android）。设计与计划见：

- [`docs/SecureAuth-v0.1-design.md`](docs/SecureAuth-v0.1-design.md)
- [`docs/SecureAuth-v0.1-plan.md`](docs/SecureAuth-v0.1-plan.md)

## 模块

| 模块 | 类型 | 说明 |
|---|---|---|
| `:core` | 纯 Kotlin/JVM | OTP、Base32、otpauth 解析、加密抽象；不能依赖 Android |
| `:app` | Android 应用 | UI、存储、安全 |

## 上手文档

- 开发与测试环境搭建（安装 Android Studio / SDK、模拟器、真机测试）：[`docs/guide-dev-environment.md`](docs/guide-dev-environment.md)
- 本地打包、签名与发布：[`docs/guide-build-release.md`](docs/guide-build-release.md)

## 构建

需要 JDK 17+ 与 Android SDK（`platforms;android-35`），安装步骤见上方文档。

```bash
./gradlew :core:test            # core 单元测试
./gradlew :core:koverVerify     # core 覆盖率 ≥ 80%
./gradlew detektMain            # 静态检查（含类型解析）
./gradlew :app:lintRelease      # Android Lint
./gradlew :app:assembleRelease  # release APK（未签名）
scripts/check-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

`scripts/check-apk.sh`（基于 `aapt2`）检查最终 APK：无 INTERNET / ACCESS_NETWORK_STATE 权限、权限只含白名单、除启动 Activity 外无未受保护的导出组件、`allowBackup=false`、声明了 `dataExtractionRules`、非 debuggable。

## 测试

```bash
./gradlew :app:connectedDebugAndroidTest   # 设备 / 模拟器上的集成与界面测试
```

- 界面测试（设计 §50.8）通过 `AppContainer` 注入假时钟、假生物识别和假相机帧，使用真实 Keystore 与 DataStore；**运行时会清除 debug 包的数据和密钥**，不要在装有自用数据的手机上运行
- CI 在手动触发（Actions → CI → Run workflow）或每晚定时时，于 API 26 / 35 模拟器上运行
- 真机测试：`docs/test-cases.md`；安全检查：`docs/security-checklist.md`
- 测试二维码：`testdata/qr/`（由 `scripts/gen-test-qr.py` 生成，只含测试密钥）

## 下载测试包

每次推送到 `main` 后，CI 会上传 debug APK：在 GitHub 仓库的 Actions 页面打开对应的 CI 运行，在页面底部 Artifacts 中下载 `secureauth-debug-<提交号>`（保留 14 天）。

debug 包仅用于手动测试：它可调试、使用调试签名，与将来的 release 包签名不同，不能覆盖安装。

## 发布

推荐本地签名：`scripts/release-local.sh` 完成测试、签名构建与校验，产物在 `dist/`，再到 GitHub 手动发布。也可以启用 CI 签名（仓库变量 `SECUREAUTH_CI_SIGNING=true`），推送 `vX.Y.Z` tag 后由 `Release` 工作流自动发布。详见 [`docs/guide-build-release.md`](docs/guide-build-release.md) 与 [`docs/release.md`](docs/release.md)；变更记录见 `CHANGELOG.md`。

## 安全约定

- 禁止 `android.util.Log`、`println`、`printStackTrace`（detekt 强制；release 构建由 R8 移除 Log 调用）
- Secret 不得进入日志、UI State、保存状态、导航参数、Intent（设计 §9、§55）
- 仓库中只允许出现测试密钥
