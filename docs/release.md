# 发布流程（T7.1）

## 1. 发布前提

- [ ] `main` 上 CI 通过；最近一次模拟器测试（Actions → CI → Run workflow）通过
- [ ] 真机矩阵 P0 用例全部通过（`docs/test-cases.md`）
- [ ] 安全检查表全部勾选并签字（`docs/security-checklist.md`）
- [ ] `CHANGELOG.md` 对应版本的“未发布”改为发布日期

## 2. 生成签名密钥（只做一次）

```sh
keytool -genkeypair -v \
  -keystore secureauth-release.jks -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias secureauth
```

- PKCS12 格式的密钥库口令与密钥口令相同，按提示输入一个强口令
- **离线备份至少两份**（例如两块不同的加密 U 盘），口令存入密码管理器。密钥丢失后无法发布可覆盖安装的更新，只能卸载重装，也就意味着数据全部丢失（计划 §5.1）
- 记录证书指纹，以后每个版本都要与它一致：

```sh
keytool -list -v -keystore secureauth-release.jks -alias secureauth | grep SHA256
```

密钥文件和口令**永远不进仓库**（`.gitignore` 已排除 `*.jks`、`*.keystore`、`keystore.properties`）。

## 3. 选择签名方式

### 方式 A：CI 签名（推送 tag 自动发布）

在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加：

| Secret | 值 |
|---|---|
| `SECUREAUTH_KEYSTORE_BASE64` | `base64 -w0 secureauth-release.jks` 的输出 |
| `SECUREAUTH_KEYSTORE_PASSWORD` | 密钥库口令 |
| `SECUREAUTH_KEY_ALIAS` | `secureauth` |
| `SECUREAUTH_KEY_PASSWORD` | 密钥口令（PKCS12 下与密钥库口令相同） |

代价：签名密钥会存放在 GitHub 上（加密保存，工作流结束即删除临时文件）。GitHub 账号一旦被盗，攻击者就可能发布“合法签名”的恶意更新。GitHub 账号务必开启两步验证（不要只靠本 App）。

### 方式 B：本地签名（密钥不离开本机）

在仓库根目录创建 `keystore.properties`（已被 git 忽略）：

```properties
storeFile=/绝对路径/secureauth-release.jks
storePassword=...
keyAlias=secureauth
keyPassword=...
```

然后：

```sh
./gradlew :core:test :core:koverVerify detektMain :app:lintRelease :app:assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs "$apk"          # 核对证书指纹
scripts/check-apk.sh "$apk"                    # 权限、导出组件、备份、大小 < 15 MB
cp "$apk" secureauth-0.1.0.apk && sha256sum secureauth-0.1.0.apk > secureauth-0.1.0.apk.sha256
```

再在 GitHub 上手动创建 Release，上传 APK 与 `.sha256`，说明中写入 `CHANGELOG.md` 对应段落和证书指纹。

## 4. 发布（方式 A）

```sh
git switch main && git pull
# 确认 app/build.gradle.kts 的 versionName 与 tag 一致，CHANGELOG 已写发布日期
git tag -a v0.1.0 -m "SecureAuth 0.1.0"
git push origin v0.1.0
```

`Release` 工作流会：

1. 检查 tag 与 `versionName` 一致，`CHANGELOG.md` 有对应段落
2. 运行单元测试、覆盖率、detekt、Lint
3. 用 Secrets 签名构建 release APK，随即删除临时密钥文件
4. `apksigner` 校验签名，`check-apk.sh` 检查权限、导出组件、备份设置、非 debuggable、大小 < 15 MB
5. 生成 `secureauth-<版本>.apk` 与 `.sha256`，创建 GitHub Release，说明中附 SHA-256 与签名证书指纹

## 5. 发布后验证

```sh
sha256sum -c secureauth-0.1.0.apk.sha256
apksigner verify --print-certs secureauth-0.1.0.apk   # 证书指纹与第 2 步记录的一致
```

- 在**飞行模式**下的真机上安装并跑一遍 P0 冒烟：首次启动、扫码添加、TOTP、HOTP、复制、上锁 / 解锁、重启后数据仍在
- debug 包与 release 包签名不同，不能互相覆盖安装：先卸载 debug 包（其中的测试数据会丢失）

## 6. 以后的版本

- `versionCode` 加 1，`versionName` 改为新版本号，`CHANGELOG.md` 新增段落
- **必须使用同一个签名密钥**，否则用户无法覆盖安装
