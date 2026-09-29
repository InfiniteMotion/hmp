# CLAUDE.md

@AGENTS.md

**本文件不再是内容真源。** AI 协作者须知（硬约束、常用命令、模块拓扑、跨平台机制、三端引擎差异、Agent 子系统、已知地雷、版本与分支策略）全部在 **[AGENTS.md](AGENTS.md)**，上面已按 Claude Code 的 `@` 导入语法引入。

改这些内容请只改 `AGENTS.md`。往本文件抄一份，就是再造一个漂移点 —— 版本号那件事刚在 v7.2.2 上演过一次（9 处人抄 → 1 处手改 + 脚本同步），文档叙述层别再重演。

---

## 本文件只放 Claude Code 特有的差异

### 项目 Skill

- **`release-prep`**（`skills/release-prep/SKILL.md`）—— 发版前半程：定版本号 → 写 `release.toml` → 同步派生点 → 点检 → 本机 preflight → 备好 PR。用户说"准备发版 / 发 X.Y.Z / bump 版本"时走它。**该 Skill 不碰 push / PR / 合并**，那几步按它的 ⑧ 步交回用户。

### 本地权限配置

`.claude/settings.local.json` 是本机的工具许可缓存（`permissions.allow` 里记着历史放行的 Bash 命令与 `WebSearch`），属个人环境而非项目事实源：

- 里面的 `./gradlew :desktop:feature-ui:compileKotlinDesktop` 指向**当前构建图里不存在的模块**（`settings.gradle.kts` 只 include `:desktop:app` 与 `:desktop:core-player`），是缓存下来的历史命令，别照抄；
- 允许了 `git add *` / `git stash *` / `git pull *` —— 这是放行范围，不是操作授权。合并 `release/*` 与 push 仍需用户明确同意（见 `AGENTS.md` §一 第 4 条）。

不需要提交本文件的改动来"修正"它；它只影响你这台机器上的提示频率。

### 历史说明

本文件原为唯一的项目速查（约 370 行）。2026-09-29 迁至 `AGENTS.md` 以覆盖跨工具场景（Codex / Cursor / Qoder 等按 `agents.md` 规范只读 `AGENTS.md`，读不到 `CLAUDE.md`）。迁移同时修正了原文里已核实的失真：最新版本号（v7.2.1 → **v7.2.2**）、`compileSdk`/`targetSdk`（36 → **37**）、shared-ui 单测任务（不存在的 `testDebugUnitTest` → **`testAndroidHostTest`**）、shared-ui 测试源集实况、Swift 文件数（17 → **22**）。其余仍待订正项见 `TODO.md` 的 **R54 / R36** 与 `docs/7_3/review-7.3-architecture.md` §四。
