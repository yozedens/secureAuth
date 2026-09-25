# 开发与测试环境搭建指南

本文面向**从没开发过 Android 应用**的读者，一步步带你完成：

1. 安装开发工具（Git、Android Studio、Android SDK）
2. 下载并打开 SecureAuth 项目，在电脑上编译
3. 创建模拟器（电脑上的虚拟手机），在模拟器上运行 App 和自动化测试
4. 连接真实手机，安装 App 并按测试用例测试

生成可发布的安装包（签名、发布）见另一篇：[`guide-build-release.md`](guide-build-release.md)。

> 读法建议：按顺序做，每节末尾都有“检查点”，确认通过再往下走。遇到问题先看第 10 节“常见问题”。

---

## 0. 先认识几个名词

| 名词 | 是什么 |
|---|---|
| **Android Studio** | Google 官方的 Android 开发软件（IDE），写代码、编译、运行、调试都在里面 |
| **Android SDK** | 编译 Android 应用所需的工具和库，Android Studio 会帮你下载 |
| **JDK** | Java 开发工具包。编译需要它，Android Studio 自带一份（叫 JBR），不用单独装 |
| **Gradle** | 编译工具。项目里的 `gradlew`（Windows 上是 `gradlew.bat`）会自动下载正确版本，不用单独装 |
| **模拟器（Emulator / AVD）** | 在电脑上运行的虚拟 Android 手机 |
| **adb** | 电脑与手机（或模拟器）通信的命令行工具：安装 App、看日志等 |
| **APK** | Android 安装包文件，相当于 Windows 的 `.exe` 安装程序 |
| **debug 包 / release 包** | debug 包用于开发测试，可调试；release 包是正式发布版，经过优化并用你自己的密钥签名 |

本项目用到的版本（已写在项目配置里，**不需要你手动选**，这里只是让你知道）：

| 项 | 版本 |
|---|---|
| 编译用 Android 版本（compileSdk / targetSdk） | Android 15（API 35） |
| 最低支持（minSdk） | Android 8.0（API 26） |
| JDK | 17 或更高（用 Android Studio 自带的即可） |
| Gradle | 8.14.3（由 `gradlew` 自动下载） |

---

## 1. 电脑要求

| 项 | 最低 | 推荐 |
|---|---|---|
| 系统 | Windows 10/11 64 位、macOS 13+、64 位 Linux（如 Ubuntu 22.04+） | — |
| 内存 | 16 GB | 32 GB |
| 硬盘 | 80 GB 可用空间（最好是 SSD） | 150 GB |
| CPU | 支持虚拟化的 64 位 CPU（Intel VT-x / AMD-V）或 Apple Silicon（M1/M2/M3…） | 8 核以上 |

**确认虚拟化已开启**（模拟器需要）：

- **Windows**：按 `Ctrl+Shift+Esc` 打开任务管理器 →“性能”→“CPU”，右下角“虚拟化”应为“已启用”。如果是“已禁用”，需要进电脑 BIOS 开启（通常叫 Intel Virtualization Technology / SVM Mode，不同品牌电脑进入 BIOS 的按键不同，可搜索“电脑型号 + 开启 VT”）
- **macOS**：无需操作
- **Linux**：终端执行 `egrep -c '(vmx|svm)' /proc/cpuinfo`，结果大于 0 即支持

---

## 2. 安装 Git 并下载项目代码

Git 是代码版本管理工具，用来下载（克隆）和提交代码。

### 2.1 安装 Git

- **Windows**：打开 <https://git-scm.com/download/win> 下载安装包，一路“Next”即可。安装后会多出一个 **Git Bash**（一个命令行窗口，后文部分脚本需要在它里面运行）
- **macOS**：打开“终端”，输入 `git --version`。如果没装，系统会弹窗提示安装“命令行开发者工具”，点“安装”
- **Linux（Ubuntu）**：`sudo apt update && sudo apt install -y git`

**检查点**：命令行输入 `git --version`，能显示版本号。

### 2.2 下载项目

本仓库是 GitHub 仓库 `yozedens/secureAuth`。如果仓库是私有的，需要先用有权限的 GitHub 账号登录：

- 最简单：安装 **GitHub Desktop**（<https://desktop.github.com/>），登录后 File → Clone repository → 选 `yozedens/secureAuth` → 选择保存目录 → Clone
- 或者命令行（会提示登录；密码处需要填 GitHub 的 Personal Access Token，不是登录密码）：

```sh
git clone https://github.com/yozedens/secureAuth.git
cd secureAuth
```

> 路径建议：放在**不含中文和空格**的目录，例如 Windows 上的 `D:\code\secureAuth`、macOS/Linux 上的 `~/code/secureAuth`。含中文的路径偶尔会让编译工具出错。

**检查点**：目录里能看到 `app`、`core`、`docs`、`gradlew` 等文件。

---

## 3. 安装 Android Studio

### 3.1 下载

打开官网下载页：

- 国际站：<https://developer.android.com/studio>
- 国内镜像站（访问国际站慢时用）：<https://developer.android.google.cn/studio>

点击页面上的下载按钮（会自动识别你的系统），勾选同意协议后下载。安装包约 1 GB。

### 3.2 安装

- **Windows**：双击 `.exe`，一路“Next”。“Choose Components”页保持勾选 **Android Virtual Device**（这是模拟器）
- **macOS**：打开 `.dmg`，把 Android Studio 图标拖到“应用程序（Applications）”文件夹。注意下载与你芯片匹配的版本（Apple 芯片选 “Mac with Apple chip”）
- **Linux**：解压 `.tar.gz` 到例如 `~/android-studio`，运行 `~/android-studio/bin/studio.sh`。Ubuntu 上还需要：`sudo apt install -y libc6 libncurses6 libstdc++6 lib32z1 libbz2-1.0`（部分发行版包名略有不同）

### 3.3 首次启动向导（Setup Wizard）

1. 如果问“是否导入之前的设置”，选 **Do not import settings**
2. 如果问是否发送使用数据，按个人意愿选择
3. 安装类型选 **Standard（标准）**
4. 选择界面主题（随意）
5. **License Agreement（许可协议）**页：左侧列表里**每一项**都要点一下，然后选右下的 **Accept**，全部接受后“Finish”才能点
6. 等待下载 SDK 组件（几百 MB 到 1 GB 以上，视网速需要 5–30 分钟）

**检查点**：出现 “Welcome to Android Studio” 欢迎页。

---

## 4. 安装本项目需要的 SDK 组件

向导只装了默认组件，还需补装几项。

1. 欢迎页点 **More Actions → SDK Manager**（如果已经打开了某个项目：菜单 **Tools → SDK Manager**）
2. 顶部 **Android SDK Location** 显示了 SDK 的安装位置，**记下这个路径**，后面要用。默认位置：
   - Windows：`C:\Users\你的用户名\AppData\Local\Android\Sdk`
   - macOS：`/Users/你的用户名/Library/Android/sdk`
   - Linux：`/home/你的用户名/Android/Sdk`
3. **SDK Platforms** 标签页：勾选 **Android 15.0（"VanillaIceCream"）/ API Level 35**
4. **SDK Tools** 标签页，勾选：
   - Android SDK Build-Tools（选最新版即可）
   - Android SDK Command-line Tools (latest)
   - Android Emulator
   - Android SDK Platform-Tools
5. 点 **Apply**，确认后等待下载完成，点 **OK**

**检查点**：SDK Manager 中上述各项显示为 “Installed”。

### 4.1 设置环境变量（命令行用）

在 Android Studio 里点按钮编译不需要这一步；但后文的命令行操作（adb、打包脚本、签名校验）需要。把下面的 `SDK路径` 换成第 4 步记下的路径。

**Windows**（PowerShell 中执行一次，之后新开的窗口生效）：

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
$old = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$old;$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
```

**macOS**（编辑 `~/.zshrc`，末尾加上，保存后执行 `source ~/.zshrc`）：

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

**Linux**（编辑 `~/.bashrc`，末尾加上，保存后执行 `source ~/.bashrc`；`JAVA_HOME` 按你的 Android Studio 解压位置修改）：

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export JAVA_HOME="$HOME/android-studio/jbr"
```

**检查点**：**新开**一个命令行窗口，输入 `adb version` 能显示版本号；输入 `java -version` 显示 17 或更高（Windows 若提示找不到 java，把 `%JAVA_HOME%\bin` 也加入 Path）。

---

## 5. 打开项目并编译

1. 欢迎页点 **Open**，选择第 2 步下载的 `secureAuth` 文件夹（选文件夹本身，不是里面的某个文件）
2. 如果弹出 “Trust and Open Project?”，选 **Trust Project**
3. 右下角会显示 **Gradle Sync**（同步）进度。**第一次同步需要下载 Gradle 和所有依赖库，可能需要 10–30 分钟**，请耐心等待，不要中途关闭
4. 同步完成后，左侧项目树能看到 `app` 和 `core` 两个模块

> Android Studio 会自动在项目根目录生成 `local.properties`（记录 SDK 路径）。它只属于你的电脑，已被 git 忽略，不要提交。

### 5.1 确认使用正确的 JDK

菜单 **File → Settings**（macOS：**Android Studio → Settings**）→ **Build, Execution, Deployment → Build Tools → Gradle** → **Gradle JDK** 选 **jbr-17** 或 **jbr-21**（Android Studio 自带的 JDK，17 及以上均可）。

### 5.2 在命令行编译并运行单元测试

在项目根目录打开命令行（Android Studio 底部的 **Terminal** 标签也可以）：

macOS / Linux / Git Bash：

```sh
./gradlew :core:test            # 核心逻辑的单元测试（OTP 算法、解析、加密等）
./gradlew :app:assembleDebug    # 编译 debug 安装包
```

Windows PowerShell / 命令提示符：

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :app:assembleDebug
```

最后显示 **BUILD SUCCESSFUL** 即成功。编译出的安装包在 `app/build/outputs/apk/debug/app-debug.apk`。

**检查点**：两条命令都显示 BUILD SUCCESSFUL。

其他常用检查（与 CI 相同，提交代码前可以跑一遍）：

```sh
./gradlew :core:koverVerify     # 核心模块测试覆盖率 ≥ 80%
./gradlew detektMain            # 代码规范检查
./gradlew :app:lintRelease      # Android 代码检查
```

---

## 6. 创建模拟器

我们需要两台虚拟手机：**Android 15（API 35）**是主力测试机，**Android 8.0（API 26）**是最低支持版本。

1. 菜单 **Tools → Device Manager**（或右侧边栏的手机图标）
2. 点 **+**（Create Virtual Device）
3. **选择硬件**：Phone 分类下选 **Pixel 8**（或任意 Pixel）→ Next
4. **选择系统镜像**：
   - 找 **API 35**，选择带 **Google APIs** 字样的镜像
   - CPU 类型：Intel/AMD 电脑选 **x86_64**；Apple 芯片的 Mac 选 **arm64-v8a**
   - 名称旁有下载图标说明还没下载，点它下载（约 1–2 GB）
   - 选中后 Next
5. 名称随意（如 `Pixel8_API35`），Finish
6. 重复以上步骤，再建一台 **API 26**（Android 8.0，同样选 Google APIs 镜像）

### 6.1 启动模拟器并运行 App

1. 在 Device Manager 中点模拟器右侧的 ▶ 启动，第一次启动需要 1–3 分钟
2. Android Studio 顶部工具栏：左边的下拉框选 **app**，右边的设备下拉框选刚启动的模拟器
3. 点绿色 ▶（Run 'app'），App 会编译、安装并自动打开

**检查点**：模拟器中出现“欢迎使用 SecureAuth”页面。

> 截图全黑是正常的：App 禁止截图和录屏（安全设计）。

### 6.2 在模拟器上测试生物识别（指纹）

1. 模拟器中打开系统 **设置 → 安全（Security）**，先设置一个锁屏 PIN
2. 再添加指纹：系统提示“触摸传感器”时，点击模拟器窗口侧边工具栏的 **⋯（Extended controls）→ Fingerprint → Touch Sensor**
3. 回到 SecureAuth 的设置页打开“生物识别解锁”，需要验证指纹时，再次在 Extended controls 中点 **Touch Sensor**

### 6.3 在模拟器上测试扫码和“从图片识别”

- **从图片识别**（推荐）：把测试二维码图片放进模拟器相册，App 中选“从图片识别”选中它。放图方法任选：
  - 把 `testdata/qr/01_totp_basic.png` 文件直接**拖到模拟器窗口**上（会存到“下载/Download”）
  - 或命令行：`adb push testdata/qr/01_totp_basic.png /sdcard/Pictures/`，之后在模拟器相册中可见（若没出现，重启模拟器）
- **相机扫码**：模拟器的后置摄像头默认是一个虚拟 3D 房间，很难对准二维码。扫码建议用真机测试（第 8 节）

---

## 7. 在模拟器上运行自动化测试

项目有两类自动化测试：

| 类型 | 在哪运行 | 命令 |
|---|---|---|
| 单元测试（`core` 模块） | 电脑本机，无需模拟器 | `./gradlew :core:test` |
| 设备测试 / 界面测试（`app/src/androidTest`） | 模拟器或真机 | `./gradlew :app:connectedDebugAndroidTest` |

> **警告**：设备测试会**清空 debug 版 App 的全部数据和密钥**。不要在装有自用 SecureAuth 数据的真机上运行。模拟器随便用。

步骤：

1. 启动一台模拟器（第 6.1 节），**只开一台**，避免测试跑到别的设备上
2. 项目根目录执行：

   ```sh
   ./gradlew :app:connectedDebugAndroidTest
   ```

   （Windows：`.\gradlew.bat :app:connectedDebugAndroidTest`）
3. 测试期间模拟器上会自动操作 App（引导、设 PIN、添加账号、复制、编辑、删除……），不要手动点它
4. 完成后显示 BUILD SUCCESSFUL 即全部通过。详细报告用浏览器打开：
   `app/build/reports/androidTests/connected/debug/index.html`

在 Android Studio 里也可以：左侧项目树找到 `app/src/androidTest/kotlin`，右键 → **Run 'Tests in ...'**。

建议在 API 26 和 API 35 两台模拟器上各跑一次。GitHub 上的 CI 也能跑同样的测试：仓库页面 **Actions → CI → Run workflow**。

---

## 8. 真机测试

### 8.1 打开手机的“开发者选项”和“USB 调试”

1. 进入 **设置 → 关于手机**，找到 **版本号**（部分品牌叫“OS 版本”“MIUI 版本”“HyperOS 版本”），**连续点击 7 次**，直到提示“您已处于开发者模式”（可能要求输入锁屏密码）
2. 返回设置，找到 **开发者选项**（位置因品牌而异，找不到时在设置顶部搜索框搜“开发者选项”）
3. 打开 **USB 调试**

常见品牌的额外设置（菜单名称可能因系统版本略有不同，找不到时用设置里的搜索）：

| 品牌 | 额外需要 |
|---|---|
| 小米 / 红米（HyperOS、MIUI） | 开发者选项里同时打开 **USB 安装** 和 **USB 调试（安全设置）**；这两项可能要求插 SIM 卡并登录小米账号 |
| 华为（HarmonyOS 4.2） | 先到 **设置 → 系统和更新 → 纯净模式** 关闭纯净模式，否则无法安装本 App；开发者选项里可打开“仅充电模式下允许 ADB 调试” |
| 荣耀、OPPO、vivo | 部分机型开发者选项中有“禁止权限监控”“USB 安装”等开关，安装失败时检查这些选项 |

> 测试完成后建议关闭 USB 调试和开发者选项（安全考虑）。

### 8.2 连接电脑

1. 用**支持数据传输的** USB 线连接手机和电脑（有些线只能充电）
2. 手机上若弹出“USB 用途”，选 **文件传输** 或 **传输文件（MTP）**
3. 手机弹出“允许 USB 调试吗？”，勾选“始终允许使用这台计算机进行调试”，点 **允许**
4. 电脑命令行执行 `adb devices`，应看到类似：

   ```text
   List of devices attached
   ABCD1234    device
   ```

   - 显示 `unauthorized`：手机上没点“允许”，拔插一次重新授权
   - 什么都不显示：换线、换 USB 口；**Windows** 可能需要安装手机厂商的 USB 驱动（在厂商官网搜索“USB 驱动”，Pixel 手机用 SDK Manager 中的 “Google USB Driver”）

### 8.3 安装 App 到手机

任选一种方式：

- **Android Studio**：设备下拉框选你的手机，点绿色 ▶ 运行
- **命令行**：

  ```sh
  ./gradlew :app:installDebug                                  # 编译并安装 debug 包
  adb install -r app/build/outputs/apk/debug/app-debug.apk     # 或直接安装已编译好的 APK
  ```

- **不用电脑编译，直接用 CI 生成的安装包**：
  1. 打开 GitHub 仓库 → **Actions** → 选最新一次成功（绿色 ✓）的 **CI** 运行
  2. 页面底部 **Artifacts** 中下载 `secureauth-debug-<提交号>`，解压得到 `app-debug.apk`
  3. `adb install -r app-debug.apk`；或者把 APK 发到手机上，点击安装（需要在手机上允许“安装未知来源应用”）

> debug 包和正式版（release）的签名不同，不能互相覆盖安装。换装时需先卸载，**卸载会删除 App 里的所有账号**。

### 8.4 按用例执行真机测试

测试用例在 [`docs/test-cases.md`](test-cases.md)，逐条执行并记录结果。需要的准备：

1. **全程开启飞行模式**（验证完全离线可用）
2. **测试二维码**：用第二台设备或电脑屏幕打开 `testdata/qr/` 中的图片，让手机扫描；每张图的预期结果见 [`testdata/qr/README.md`](../testdata/qr/README.md)
3. **核对验证码是否正确**：用电脑独立计算验证码，与 App 显示的对比
   - Linux：`sudo apt install oathtool`；macOS：`brew install oath-toolkit`
   - 然后：`oathtool --totp -b JBSWY3DPEHPK3PXP`
   - 没有 oathtool（例如 Windows）可以用 Python：

     ```sh
     pip install pyotp
     python -c "import pyotp; print(pyotp.TOTP('JBSWY3DPEHPK3PXP').now())"
     ```

   - 注意电脑和手机的时间都要准确（开启自动同步时间）
4. **检查日志中没有泄露密钥**（安全检查项）：跑完一遍流程后执行

   ```sh
   adb logcat -d | grep -i JBSWY3DP             # macOS / Linux / Git Bash
   adb logcat -d | findstr /i JBSWY3DP          # Windows 命令提示符
   ```

   应**没有任何输出**
5. 安全检查项逐条对照 [`docs/security-checklist.md`](security-checklist.md)；华为鸿蒙 4.2 设备额外执行计划文档 §4.2.1 的专项用例

### 8.5 无线调试（可选，Android 11+）

手机和电脑在同一 Wi-Fi 下时可以不插线：开发者选项 → **无线调试** → 打开 →“使用配对码配对设备”，然后在 Android Studio 设备下拉框中选 **Pair Devices Using Wi-Fi**，按提示输入配对码。注意：做飞行模式相关测试时需要改用 USB 线。

---

## 9. 日常开发流程速查

```sh
git pull                                   # 拉取最新代码
./gradlew :core:test detektMain            # 改完代码先自测
./gradlew :app:installDebug                # 装到已连接的手机 / 模拟器
./gradlew :app:connectedDebugAndroidTest   # 设备测试（会清空 debug 版数据）
git add -A && git commit -m "..." && git push
```

推送后到 GitHub 的 Actions 页面确认 CI 通过（绿色 ✓）。

---

## 10. 常见问题

| 现象 | 原因与解决 |
|---|---|
| Gradle Sync 很久不动或报下载失败 | 网络访问 Google 服务器慢。可在 Android Studio **Settings → Appearance & Behavior → System Settings → HTTP Proxy** 配置代理；命令行编译的代理写在**用户目录**的 `~/.gradle/gradle.properties`（Windows：`C:\Users\你的用户名\.gradle\gradle.properties`）中，例如 `systemProp.https.proxyHost=127.0.0.1` 和 `systemProp.https.proxyPort=7890`。**不要**把代理配置写进项目里的 `gradle.properties` |
| `SDK location not found` | 没有 `local.properties` 或没设 `ANDROID_HOME`。用 Android Studio 打开一次项目会自动生成；或手动创建 `local.properties`，内容为 `sdk.dir=SDK路径`（Windows 路径的 `\` 写成 `\\`） |
| `Failed to install the following Android SDK packages as some licences have not been accepted` | 执行 `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses`（Windows：`sdkmanager.bat --licenses`），全部输入 `y` |
| `Unsupported class file major version` / 提示 JDK 版本不对 | 按 5.1 节把 Gradle JDK 设为 17 或以上；命令行检查 `JAVA_HOME` |
| 模拟器启动失败或非常卡 | 检查第 1 节的虚拟化是否开启；Windows 上确认已启用 “Windows 虚拟机监控程序平台（Windows Hypervisor Platform）”（控制面板 → 程序 → 启用或关闭 Windows 功能）；关闭其他占内存的程序 |
| `adb devices` 显示 `unauthorized` | 手机上重新点“允许 USB 调试” |
| 安装失败 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 手机上已有签名不同的版本（例如 debug 与 release 混装）。先卸载旧版（会丢失其中数据） |
| 安装失败 `INSTALL_FAILED_USER_RESTRICTED`（常见于小米） | 开发者选项中打开“USB 安装”，安装时手机上会弹窗，点“继续安装” |
| 华为手机无法安装 | 关闭“纯净模式”（第 8.1 节） |
| 截图、录屏是黑的 | 正常，App 故意禁止截图（安全设计） |
| 扫码页提示“此设备没有可用的相机” | 模拟器创建时没有相机或设备无相机，改用“从图片识别”或真机 |
| 设备测试失败：`Timed out waiting for ...` | 报错会写明在等哪一步，并附上当时的界面结构；先确认只连了一台设备、模拟器没有被手动操作 |
