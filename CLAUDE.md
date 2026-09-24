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
