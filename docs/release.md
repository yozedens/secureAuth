# 发布流程总览（T7.1）

详细的图文式操作步骤（面向初次接触 Android 的读者）见 [`guide-build-release.md`](guide-build-release.md)。本文是速查版。

## 发布前提

- [ ] `main` 上 CI 通过；最近一次模拟器测试（Actions → CI → Run workflow）通过
- [ ] 真机矩阵 P0 用例全部通过（[`test-cases.md`](test-cases.md)）
- [ ] 安全检查表全部勾选并签字（[`security-checklist.md`](security-checklist.md)）
- [ ] `CHANGELOG.md` 对应版本的“未发布”改为发布日期，`versionCode` / `versionName` 已更新

## 签名密钥

生成、记录指纹、离线双备份：见指南第 3 节。密钥文件和口令**永远不进仓库**（`.gitignore` 已排除 `*.jks`、`*.keystore`、`keystore.properties`、`dist/`）。

## 方式 A：本地签名（推荐，密钥不离开本机）

```sh
# 项目根目录已有 keystore.properties（指南第 4 节）
scripts/release-local.sh        # 测试 + 签名构建 + 校验，产物在 dist/
```

然后在 GitHub 网页创建 Release（tag `vX.Y.Z`，目标 `main`），粘贴 `dist/release-notes.md`，上传 APK 与 `.sha256`（指南第 8 节）。

## 方式 B：CI 签名（推送 tag 自动发布）

1. 仓库 **Settings → Secrets and variables → Actions → Secrets** 添加：

   | Secret | 值 |
   |---|---|
   | `SECUREAUTH_KEYSTORE_BASE64` | `base64 -w0 secureauth-release.jks` 的输出（macOS：`base64 -i secureauth-release.jks`） |
   | `SECUREAUTH_KEYSTORE_PASSWORD` | 密钥库口令 |
   | `SECUREAUTH_KEY_ALIAS` | `secureauth` |
   | `SECUREAUTH_KEY_PASSWORD` | 密钥口令（PKCS12 下与密钥库口令相同） |

2. 同一页面 **Variables** 标签中添加变量 `SECUREAUTH_CI_SIGNING`，值为 `true`（不设置时 Release 工作流不运行，以免与本地签名方式冲突）
3. 发布：

   ```sh
   git switch main && git pull
   git tag -a v0.1.0 -m "SecureAuth 0.1.0"
   git push origin v0.1.0
   ```

`Release` 工作流会：检查 tag 与 `versionName`、`CHANGELOG.md` 一致 → 测试与静态检查 → 用 Secrets 签名构建（结束即删除临时密钥文件）→ `apksigner` 校验与安全基线、体积检查 → 创建 GitHub Release，附 APK、`.sha256`、签名证书指纹。

代价：签名密钥存放在 GitHub 上。GitHub 账号一旦被盗，攻击者可能发布“合法签名”的恶意更新，因此 GitHub 账号务必开启两步验证（不要只依赖本 App）。

## 发布后

- `sha256sum -c secureauth-X.Y.Z.apk.sha256`；`apksigner verify --print-certs` 的证书指纹与记录一致
- 飞行模式真机冒烟：首次启动、扫码添加、TOTP、HOTP、复制、上锁 / 解锁、重启后数据仍在
- debug 包与 release 包签名不同，不能互相覆盖安装

## 以后的版本

`versionCode` 加 1、`versionName` 更新、`CHANGELOG.md` 新增段落；**必须使用同一个签名密钥**；发布前先在测试机上验证覆盖升级后数据仍在（指南第 10 节）。
