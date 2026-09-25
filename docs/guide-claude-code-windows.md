# 用本机 Claude Code 搭建环境与打包（Windows）

本文说明如何在 Windows 电脑上让 **Claude Code（命令行版）** 替你完成大部分环境安装、编译、测试工作，以及哪些步骤必须你亲自做。

相关文档：手动安装步骤见 [`guide-dev-environment.md`](guide-dev-environment.md)，签名与发布见 [`guide-build-release.md`](guide-build-release.md)。

---

## 1. 分工一览

| 步骤 | 谁来做 | 说明 |
|---|---|---|
| 安装 Git、Claude Code、下载代码 | **你** | 第 2 节，一次性 |
| 安装 JDK、Android SDK、模拟器，设置环境变量 | Claude Code | 运行 `scripts\setup-android-sdk.ps1`，不需要 Android Studio |
| 同意 SDK 许可协议 | **你** | Claude 会先问你，你同意后它才会接受 |
| 开启 CPU 虚拟化 / Windows 虚拟机监控程序平台、重启 | **你** | 需要管理员权限，Claude 只给出步骤 |
| 编译、单元测试、代码检查、debug 打包 | Claude Code | |
| 启动模拟器、运行界面自动化测试 | Claude Code | 只在模拟器上跑（会清空 debug 包数据） |
| 手机开启开发者选项、USB 调试、点“允许” | **你** | |
| 把 debug 包装到手机 | Claude Code | |
| 真机手工测试（扫码、指纹、截图黑屏、换机工具……） | **你** | Claude 可以帮你准备二维码、核对验证码、检查日志 |
| **生成签名密钥、备份、写 `keystore.properties`** | **你** | 口令不能出现在与 Claude 的对话里 |
| **签名打包 `scripts/release-local.sh`** | **你**（推荐） | 也可以明确要求 Claude 代为运行 |
| 在 GitHub 上发布 | 你，或明确要求 Claude 用 `gh` 发布 | |

这些规则写在仓库的 `CLAUDE.md` 里，Claude Code 每次启动都会读取并遵守；`.claude/settings.json` 还额外禁止它读取密钥文件、运行 `keytool`。

---

## 2. 你需要先做的准备（一次性）

### 2.1 安装 Git

Claude Code 在 Windows 上依赖 Git 自带的 Git Bash。打开 **PowerShell**（开始菜单搜索“PowerShell”），执行：

```powershell
winget install --id Git.Git -e
```

没有 `winget` 时，到 <https://git-scm.com/download/win> 下载安装包，一路“Next”。

### 2.2 安装 Claude Code

按官方文档安装（安装方式可能更新，以官方为准）：<https://code.claude.com/docs>。常见方式是在 PowerShell 中执行官方给出的安装命令，装好后**新开**一个终端，输入 `claude --version` 能显示版本号即可。首次运行 `claude` 会引导你登录账号。

### 2.3 下载代码

放在**纯英文、无空格**的路径下（例如 `D:\code`）：

```powershell
mkdir D:\code; cd D:\code
git clone https://github.com/yozedens/secureAuth.git
cd secureAuth
```

仓库是私有的话，Git 会弹出 GitHub 登录窗口，按提示登录。

---

## 3. 让 Claude Code 安装环境

在项目目录中启动：

```powershell
cd D:\code\secureAuth
claude
```

首次在该目录启动会询问是否信任此文件夹，选信任。然后直接用中文告诉它，例如：

> 按 CLAUDE.md 的约定，在这台 Windows 电脑上装好 SecureAuth 的开发环境，然后验证能编译。

接下来会发生：

1. **Claude 运行安装脚本**，每条命令执行前会请你确认。正常的安装、编译命令可以同意。
2. **许可协议**：脚本发现 SDK 许可未接受时会停下，Claude 会问你是否同意。你可以：
   - 自己在另一个终端运行它给出的 `sdkmanager --licenses` 命令，阅读后逐条输入 `y`；或
   - 回复“同意”，它会加 `-AcceptLicenses` 重新运行（代表你接受 Android SDK 许可；如果需要安装 JDK / Git，也代表你接受 winget 的相关协议）
3. **下载**：首次下载 1–5 GB，视网速需要十几分钟到一个小时。下载失败时，可以告诉它你的代理地址（例如“用代理 127.0.0.1:7890”），脚本支持 `-ProxyHost` / `-ProxyPort`。
4. **中文用户名**：如果你的 Windows 用户名是中文，脚本会提示改用纯英文的 SDK 路径（例如 `D:\Android\Sdk`），Claude 会帮你带参数重跑。
5. **硬件加速**：如果脚本最后提示“硬件加速不可用”，需要**你**来做：
   - 确认 BIOS 中开启了 CPU 虚拟化（任务管理器 → 性能 → CPU →“虚拟化：已启用”）
   - 右键开始菜单 → “终端（管理员）”或“Windows PowerShell（管理员）”，执行：

     ```powershell
     Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All
     ```

   - 重启电脑
6. **重启终端**：脚本写入的环境变量要新开终端才生效。安装结束后，退出 Claude Code（输入 `/exit`），关闭终端，重新打开终端并在项目目录再次运行 `claude`。

然后可以让它验证：

> 运行单元测试并打一个 debug 包。

> 启动 API 35 模拟器，跑一遍设备测试。

---

## 4. 真机调试

1. **你**：按 [`guide-dev-environment.md`](guide-dev-environment.md) 第 8.1–8.2 节打开手机的开发者选项和 USB 调试，用数据线连接电脑，在手机上点“允许 USB 调试”
2. 告诉 Claude：

   > 手机已经连上，把 debug 包装到手机上。

3. 真机测试按 [`test-cases.md`](test-cases.md) 由**你**执行。可以请 Claude 帮忙，例如：
   - “用 oathtool 或 Python 算一下 JBSWY3DPEHPK3PXP 现在的验证码”
   - “我刚跑完一遍流程，检查手机日志里有没有测试密钥”

> **不要**让 Claude 在装有你真实账号数据的手机上运行设备测试（`connectedDebugAndroidTest`），它会清空 debug 包的数据。`CLAUDE.md` 已要求它运行前确认只连着模拟器。

---

## 5. 签名与打包（你来做）

### 5.1 生成密钥（只做一次）

**另开一个 PowerShell 窗口**（不要在 Claude Code 里操作），执行：

```powershell
New-Item -ItemType Directory -Force D:\keys; cd D:\keys
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore secureauth-release.jks -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias secureauth
```

提示输入口令时输入你的强口令（屏幕不显示字符属正常），其余问题按 [`guide-build-release.md`](guide-build-release.md) 第 3.3 节回答。然后：

- 按第 3.4 节记录证书指纹
- 按第 3.5 节把 `secureauth-release.jks` 备份到两个 U 盘，口令存进密码管理器

### 5.2 配置本地签名

用记事本在项目根目录（`D:\code\secureAuth`）新建 `keystore.properties`，内容：

```properties
storeFile=D:/keys/secureauth-release.jks
storePassword=你的口令
keyAlias=secureauth
keyPassword=你的口令
```

注意路径用**正斜杠** `/`。保存时如果记事本自动加了 `.txt` 后缀，要改回 `keystore.properties`（资源管理器 → 查看 → 勾选“文件扩展名”后重命名）。这个文件已被 git 忽略，Claude Code 也被禁止读取它。

### 5.3 打包

发布前的准备（改 `CHANGELOG.md` 日期、提交推送、确认 CI 通过）可以交给 Claude：

> 准备发布 0.1.0：把 CHANGELOG 的“未发布”改成今天的日期，提交推送，确认 CI 通过。

然后**你**在开始菜单打开 **Git Bash**，执行：

```sh
cd /d/code/secureAuth
git pull
scripts/release-local.sh
```

完成后核对脚本最后打印的**签名证书 SHA-256**与你记录的指纹一致。产物在 `dist\` 目录。

> 如果你希望由 Claude 代为运行打包脚本，需要明确对它说“运行 scripts/release-local.sh”。它只会看脚本输出，不会查看密钥文件。

### 5.4 发布与安装

- 发布到 GitHub：按 [`guide-build-release.md`](guide-build-release.md) 第 8 节在网页上操作；或者对 Claude 说“用 dist 里的文件创建 v0.1.0 的 GitHub Release”（需要先安装 GitHub CLI 并执行 `gh auth login`）
- 安装到手机：先卸载 debug 包（其中的测试数据会丢失），再让 Claude 或你自己执行 `adb install dist\secureauth-0.1.0.apk`，然后按第 9 节在飞行模式下做冒烟测试

---

## 6. 常见问题

| 现象 | 处理 |
|---|---|
| 运行 `.ps1` 提示“禁止运行脚本” | 用 `powershell -ExecutionPolicy Bypass -File scripts\setup-android-sdk.ps1` 运行（只对这一次生效，不改系统设置） |
| 下载命令行工具失败，提示链接失效 | 到 <https://developer.android.com/studio#command-line-tools-only> 复制最新的 Windows 版下载链接，让 Claude 用 `-CmdlineToolsUrl <链接>` 重跑 |
| `sdkmanager` 下载很慢或失败 | 告诉 Claude 代理地址，用 `-ProxyHost` / `-ProxyPort` 重跑 |
| 装完后 `adb`、`java` 找不到 | 环境变量要新开终端才生效：退出 Claude Code、关闭终端后重新打开 |
| 模拟器启动失败 | 看第 3 节第 5 步的硬件加速处理；只想编译、暂时不用模拟器时，可让 Claude 用 `-SkipEmulator` 重跑以节省空间 |
| Claude 请求运行 `keytool` 或读取 `keystore.properties` | 拒绝。这违反 `CLAUDE.md` 的签名红线，可以提醒它遵守 |
| 不小心把口令发给了 Claude | 用 `keytool -storepasswd` 修改密钥库口令（PKCS12 下密钥口令随之一致），并同步更新 `keystore.properties` 和密码管理器 |
