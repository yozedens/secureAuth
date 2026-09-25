# SecureAuth 项目约定

## 沟通语言

- **始终使用中文回复**，包括说明、总结、提问与 PR 描述。
- 代码、标识符、提交信息保持英文。

## Git 流程

- 直接提交并推送到 `main`，不需要每次开 PR（仓库所有者已同意）。
- 推送后检查 `main` 上的 CI，失败时立即修复。

## 参考文档

- 设计：`docs/SecureAuth-v0.1-design.md`
- 计划与环境：`docs/SecureAuth-v0.1-plan.md`

## 开发约定

- `:core` 为纯 Kotlin/JVM 模块，不得依赖 Android。
- 禁止 `android.util.Log`、`println`、`printStackTrace`；Secret 不得进入日志、UI State、保存状态、导航参数、Intent。
- 仓库中只允许出现测试密钥。
- 设计变更先改文档，再改代码。

## 本机环境（Windows 为主）

- 装环境：在项目根目录运行 `powershell -ExecutionPolicy Bypass -File scripts\setup-android-sdk.ps1`（可重复运行，已完成的步骤会跳过）。详见 `docs/guide-claude-code-windows.md`。
- **接受许可前必须先问用户**：Android SDK 许可、winget 安装 JDK / Git 的协议都需要用户明确同意后，才可以加 `-AcceptLicenses`。
- 需要管理员权限、重启、BIOS 设置、手机上点击的步骤：停下来，把具体操作告诉用户，不要尝试绕过或提权。
- 环境变量写入后要新开终端才生效；当前会话中可临时设置 `ANDROID_HOME`、`JAVA_HOME` 继续执行。
- Windows 上用 `.\gradlew.bat`；`scripts/*.sh` 在 Git Bash 中运行。
- 设备测试 `:app:connectedDebugAndroidTest` 会清空 debug 包的数据和密钥：运行前用 `adb devices` 确认只连接了模拟器（`emulator-xxxx`）；要在真机上运行必须先得到用户明确同意。

## 签名与发布红线

- **不得**生成、复制、移动、备份或删除签名密钥（`*.jks`、`*.keystore`），不得运行 `keytool`。
- **不得**创建、读取、修改、打印 `keystore.properties`，不得读取或打印 `SECUREAUTH_*` 口令类环境变量；命令和对话中不得出现签名口令。用户若把口令发给你，提醒其更换口令，不要使用。
- 签名打包 `scripts/release-local.sh` 默认由用户在自己的终端执行；用户明确要求时可以代为运行，但只看脚本输出，不查看任何密钥文件。
- 不得推送 `v*` tag、创建 GitHub Release、修改仓库 Secrets / Variables，除非用户明确要求。
- `.claude/settings.json` 中的拒绝规则只是额外保护，不代表其余方式可以读取这些文件。
