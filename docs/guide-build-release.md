# 本地打包、签名与发布指南

本文面向**没有 Android 发布经验**的读者，说明如何：

1. 在自己电脑上生成 APK 安装包（debug 版与正式版）
2. 生成并保管签名密钥
3. 用自己的密钥给正式版签名（**本地签名**，密钥不离开你的电脑）
4. 校验安装包，在 GitHub 上发布，并安装到手机
5. 以后如何发布新版本

开始前请先按 [`guide-dev-environment.md`](guide-dev-environment.md) 装好开发环境（至少完成第 2–5 节，并设置第 4.1 节的环境变量）。

---

## 0. 先理解几个概念

### debug 包和 release 包

| | debug 包 | release 包（正式版） |
|---|---|---|
| 用途 | 开发、测试 | 自己日常使用、发布 |
| 签名 | Android Studio 自动生成的调试密钥（每台电脑不同） | **你自己生成并保管的正式密钥** |
| 性能与体积 | 未优化，体积较大 | 代码经过压缩优化，体积小 |
| 可调试 | 是（电脑可以读取它的内部状态） | 否（更安全） |
| 文件 | `app-debug.apk` | `app-release.apk` |

**日常存放真实两步验证账号，请只使用 release 包。**

### 什么是“签名”，为什么密钥这么重要

Android 要求每个安装包都用开发者的密钥签名。系统靠签名判断“这个更新是不是同一个开发者发的”：

- 新版本**必须用同一个密钥签名**，才能覆盖安装旧版本并**保留数据**
- 签名不同的安装包无法覆盖安装，只能先卸载旧版，而**卸载会删除 App 内的所有账号**（SecureAuth 不联网、不备份，数据只在手机里）

所以：

- **密钥丢了**：以后永远无法发布能覆盖安装的更新，只能卸载重装并重新绑定所有网站的两步验证
- **密钥泄露**：别人可以做一个“签名合法”的恶意版本冒充更新

### 版本号

在 `app/build.gradle.kts` 中：

- `versionCode`：整数，**每次发布必须比上一次大**（1、2、3……），系统用它判断新旧
- `versionName`：给人看的版本号，如 `0.1.0`

---

## 1. 生成 debug 安装包（测试用）

适合：自己测试新功能、给测试手机安装。

**方式 A：Android Studio**

菜单 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**。完成后右下角弹出提示，点 **locate** 打开文件所在目录。

**方式 B：命令行**（在项目根目录）

```sh
./gradlew :app:assembleDebug          # macOS / Linux / Git Bash
.\gradlew.bat :app:assembleDebug      # Windows PowerShell
```

生成的文件：`app/build/outputs/apk/debug/app-debug.apk`

安装到已连接的手机：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

> 不想自己编译时，也可以从 GitHub Actions 下载 CI 生成的 debug 包，见开发环境指南第 8.3 节。

---

## 2. 生成未签名的 release 包（仅用于检查）

没有配置签名密钥时，执行：

```sh
./gradlew :app:assembleRelease
```

得到 `app/build/outputs/apk/release/app-release-unsigned.apk`。**未签名的包无法安装**，它只用于检查体积和安全配置（CI 每次推送都会这样检查）：

```sh
scripts/check-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

> `scripts/` 下的脚本是 bash 脚本：macOS / Linux 直接在终端运行；Windows 请在 **Git Bash** 中运行（安装 Git 时自带）。

---

## 3. 生成签名密钥（整个项目只做一次）

### 3.1 找到 keytool

`keytool` 是 JDK 自带的密钥工具，Android Studio 里就有：

| 系统 | keytool 位置 |
|---|---|
| Windows | `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe` |
| macOS | `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool` |
| Linux | `~/android-studio/jbr/bin/keytool`（按你的解压位置） |

如果已按开发环境指南设置了 `JAVA_HOME` 并把 `bin` 加入 PATH，直接输入 `keytool` 即可。

### 3.2 选一个安全的存放目录

密钥文件**不能放在项目目录里**（防止误提交）。例如：

- Windows：`D:\keys\`
- macOS / Linux：`~/keys/`

### 3.3 生成

macOS / Linux / Git Bash：

```sh
mkdir -p ~/keys && cd ~/keys
keytool -genkeypair -v \
  -keystore secureauth-release.jks -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias secureauth
```

Windows PowerShell（一行）：

```powershell
New-Item -ItemType Directory -Force D:\keys; cd D:\keys
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore secureauth-release.jks -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias secureauth
```

参数含义：密钥文件名 `secureauth-release.jks`，别名（alias）`secureauth`，RSA 4096 位，有效期 10000 天（约 27 年）。

接下来会依次提问：

1. **输入密钥库口令**：设一个强口令，**建议只用字母和数字、16 位以上**（含 `\`、`#`、`!` 等符号会给后面的配置文件带来麻烦）。输入时屏幕不显示字符，属于正常现象
2. **再次输入新口令**
3. **您的名字与姓氏是什么？**、组织单位、组织、城市、省份、国家代码：这些信息会写进证书，个人使用可随意填（例如名字填 `SecureAuth`，国家代码填 `CN`），也可以直接回车跳过
4. **是否正确？** 输入 `y`（中文提示时输入“是”）回车

PKCS12 格式的“密钥口令”与“密钥库口令”相同，不会再单独询问。

### 3.4 记录证书指纹

```sh
keytool -list -v -keystore secureauth-release.jks -alias secureauth
```

输入口令后，找到 `SHA256:` 开头的一行，例如 `SHA256: 3A:7F:...:C2`。**把这串指纹记到密码管理器或纸上**：以后每个版本发布前都要核对它，一致才说明用的是同一个密钥。

### 3.5 备份（非常重要）

- 把 `secureauth-release.jks` 复制到**至少两个离线位置**，例如两个不同的 U 盘（最好是加密 U 盘），分开存放
- 口令存进密码管理器（如 Bitwarden、KeePassXC），或写在纸上与 U 盘分开保管
- **不要**把密钥文件放进网盘同步目录、聊天软件、邮件，**不要**提交到 Git 仓库
- 每年检查一次备份能否正常读取（用 3.4 节的命令能列出证书即可）

---

## 4. 配置本地签名

在**项目根目录**创建一个名为 `keystore.properties` 的文本文件（与 `gradlew` 同一层）。它已被 `.gitignore` 排除，不会被提交。内容：

```properties
storeFile=/Users/你的用户名/keys/secureauth-release.jks
storePassword=你的口令
keyAlias=secureauth
keyPassword=你的口令
```

注意：

- `storeFile` 写**绝对路径**。Windows 路径**用正斜杠**，例如 `storeFile=D:/keys/secureauth-release.jks`（写反斜杠 `\` 会出错）
- `storePassword` 与 `keyPassword` 填同一个口令（PKCS12 格式）
- 等号两边不要加空格和引号

确认它不会被提交：

```sh
git check-ignore -v keystore.properties
```

输出中包含 `.gitignore` 字样即表示已被忽略。`git status` 中也不应出现这个文件。

> 这个文件含明文口令，只放在你自己的电脑上。不用时可以删除，需要打包时再创建。

---

## 5. 生成签名的正式版 APK

### 方式 A：一键脚本（推荐）

在项目根目录（Windows 用 Git Bash）执行：

```sh
scripts/release-local.sh
```

脚本会依次：

1. 检查 `keystore.properties`、`ANDROID_HOME`、版本号与 `CHANGELOG.md` 是否就绪
2. 运行单元测试、覆盖率、代码规范检查、Android Lint（几分钟）
3. 编译并签名正式版
4. 用 `apksigner` 校验签名
5. 运行安全基线检查：无联网权限、权限白名单、无多余导出组件、禁止备份、不可调试、体积小于 15 MB
6. 在 `dist/` 目录生成：
   - `secureauth-<版本>.apk`：安装包
   - `secureauth-<版本>.apk.sha256`：校验值
   - `release-notes.md`：发布说明（更新日志、SHA-256、签名证书指纹）

最后会打印**签名证书 SHA-256**，请与第 3.4 节记录的指纹核对，必须一致。（格式可能不同：一个带冒号、大写，一个不带冒号、小写，忽略这些差别比较即可。）

`dist/` 目录已被 git 忽略。

### 方式 B：只编译不检查

```sh
./gradlew :app:assembleRelease
```

有 `keystore.properties` 时，生成的是已签名的 `app/build/outputs/apk/release/app-release.apk`（文件名中没有 `unsigned`）。发布前仍建议用方式 A 跑完全部检查。

> Android Studio 菜单里的 **Build → Generate Signed App Bundle / APK** 向导也能签名，但它的输出目录和检查步骤与本项目流程不同，且可能提示你“记住口令”。建议统一使用上面的方式，避免混乱。

---

## 6. 手动校验安装包（可选）

脚本已经做过校验。如需手动确认（例如检查别人发给你的 APK）：

**SHA-256 校验值**

```sh
cd dist
sha256sum -c secureauth-0.1.0.apk.sha256       # Linux / Git Bash，显示 OK 即一致
shasum -a 256 secureauth-0.1.0.apk             # macOS，与 .sha256 文件中的值比对
```

Windows PowerShell：

```powershell
Get-FileHash dist\secureauth-0.1.0.apk -Algorithm SHA256
```

**签名证书**

```sh
$ANDROID_HOME/build-tools/<版本号>/apksigner verify --print-certs dist/secureauth-0.1.0.apk
```

（Windows：`%ANDROID_HOME%\build-tools\<版本号>\apksigner.bat verify --print-certs dist\secureauth-0.1.0.apk`；`<版本号>` 是 build-tools 下的文件夹名，如 `35.0.0`。）输出中 `certificate SHA-256 digest` 应与第 3.4 节记录的指纹一致。

---

## 7. 发布前检查清单

- [ ] 真机测试 P0 用例全部通过（[`test-cases.md`](test-cases.md)）
- [ ] 安全检查表全部勾选（[`security-checklist.md`](security-checklist.md)）
- [ ] `CHANGELOG.md` 对应版本的“未发布”改成发布日期（例如 `## [0.1.0] - 2026-10-01`），提交并推送到 `main`，GitHub 上 CI 通过
- [ ] 本地代码与 `main` 一致：`git switch main && git pull`，`git status` 显示没有未提交的修改
- [ ] 第 5 节脚本执行成功，证书指纹与记录一致

---

## 8. 在 GitHub 上发布（本地签名方式）

### 方式 A：网页操作

1. 打开 GitHub 仓库页面，右侧 **Releases** → **Draft a new release**（或 **Create a new release**）
2. **Choose a tag**：输入 `v0.1.0`，点 **Create new tag: v0.1.0 on publish**
3. **Target**：选 `main`
4. **Release title**：`SecureAuth 0.1.0`
5. **描述框**：打开 `dist/release-notes.md`，全部复制粘贴进去
6. **附件**：把 `dist/secureauth-0.1.0.apk` 和 `dist/secureauth-0.1.0.apk.sha256` 拖进页面下方的上传区域，等待上传完成
7. 点 **Publish release**

### 方式 B：命令行（需要安装 GitHub CLI：<https://cli.github.com/>，并执行过 `gh auth login`）

```sh
gh release create v0.1.0 \
  dist/secureauth-0.1.0.apk dist/secureauth-0.1.0.apk.sha256 \
  --target main --title "SecureAuth 0.1.0" --notes-file dist/release-notes.md
```

> 仓库中还有一个 CI 自动签名发布的工作流（`.github/workflows/release.yml`）。它**默认不启用**，只有在仓库变量 `SECUREAUTH_CI_SIGNING` 设为 `true` 时才运行，所以按本节手动发布不会触发它。CI 签名方式见 [`release.md`](release.md)。

---

## 9. 安装正式版到手机

1. **如果手机上装过 debug 包，先卸载它**（debug 与 release 签名不同，不能覆盖安装；卸载会删除其中的测试数据）
2. 安装：
   - 用数据线：`adb install dist/secureauth-0.1.0.apk`
   - 或把 APK 复制到手机（数据线、蓝牙等），在手机文件管理器中点击安装；系统可能要求允许“安装未知来源应用”，允许一次即可
3. **打开飞行模式**，做一遍冒烟测试：首次引导 → 设 PIN → 扫码添加 → 核对验证码 → HOTP 生成 → 复制 → 上锁 / 解锁 → 重启手机后数据仍在
4. 之后正式添加真实账号。**每个网站开启两步验证时，务必另外保存它提供的恢复码**

---

## 10. 以后发布新版本

1. 修改 `app/build.gradle.kts`：`versionCode` 加 1（例如 1 → 2），`versionName` 改为新版本号（例如 `0.1.1`）
2. `CHANGELOG.md` 顶部新增一段 `## [0.1.1] - 日期`，写明改动
3. 提交并推送到 `main`，等 CI 通过
4. 按第 5 节打包，**确认证书指纹与第一次记录的一致**
5. **先在测试手机上验证升级**：装着旧版本并有测试数据时，执行 `adb install -r dist/secureauth-0.1.1.apk` 覆盖安装，确认账号数据仍在、验证码正确
6. 按第 8 节发布，第 9 节安装到自己的手机（覆盖安装即可，**不要先卸载**）

---

## 11. 意外情况

| 情况 | 怎么办 |
|---|---|
| 找不到密钥文件，但有备份 | 从备份复制回来，用 3.4 节命令核对指纹 |
| 密钥和备份都丢了 | 无法再发布可覆盖安装的更新。只能生成新密钥，在手机上**先逐个网站关闭或迁移两步验证**（或确认恢复码在手），再卸载旧版、安装新版并重新添加 |
| 怀疑密钥或口令泄露 | 不要再用它发布。同上生成新密钥并迁移；同时检查手机上的 App 是否来自你自己的安装包（用第 6 节方法核对签名指纹） |
| 覆盖安装时报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 新包签名与手机上的不同。**不要卸载**，先确认是否误用了 debug 包或其他密钥 |
| 覆盖安装时报 `INSTALL_FAILED_VERSION_DOWNGRADE` | 新包的 `versionCode` 没有变大，按第 10 节修改 |
| 脚本提示“没有生成已签名的 APK” | 检查 `keystore.properties`：路径是否正确（Windows 用正斜杠）、口令是否正确、文件是否在项目根目录 |
| `Keystore was tampered with, or password was incorrect` | 口令错误 |
| 脚本提示 `CHANGELOG.md 中没有 [x.y.z] 段落` | 按第 10 节第 2 步补上对应版本的更新日志 |
