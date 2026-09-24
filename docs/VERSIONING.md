# Hearable Music Player — 版本命名与发布规范

本文档为项目**版本号格式、发版流程与分支策略**的正式约定。**自 v5.6.1 起施行**。

---

## 1. 版本号格式

采用 **三位版本号**：`MAJOR.MINOR.PATCH`（如 `6.0.0`、`6.11.1`）。

- 文档与对外表述可带前缀 `v`，如 **v6.11.1**。
- 构建产物使用纯数字：`versionName = "6.11.1"`，与 ROADMAP 中的版本一致。

## 2. 何时升级哪一位

| 类型 | 何时递增 | 示例 |
|------|----------|------|
| **MAJOR** | 不兼容的架构或产品方向大变更、重大破坏性改动 | 单体 → 模块化（v4 → v5） |
| **MINOR** | 新功能或明显体验/能力提升，保持向后兼容 | 新页面、新能力、较大重构 |
| **PATCH** | 仅 bug 修复、文案/样式小调整、文档/配置更新，无新功能 | 修崩溃、改文案、更新依赖说明 |

**原则**：

- 每次正式发布**只递增一位**：能 PATCH 就 PATCH，否则 MINOR，再否则 MAJOR。
- 避免跳号：从 `6.11.1` 下次应为 `6.11.2` 或 `6.12.0`，不直接出现未发布过的版本号。
- 发布后立即在 **ROADMAP.md** 中写入该版本、日期及变更，并更新「当前版本」。

## 3. 版本号与构建系统

版本号集中维护在 `gradle.properties`：

```properties
hmp.versionCode=71000
hmp.versionName=7.1.0
```

- **versionName**：与三位版本号一致。各模块通过 `project.findProperty("hmp.versionName")` 引用。
- **versionCode**：每次发布**严格递增**的整数，按 `MAJOR*10000 + MINOR*1000 + PATCH` 换算（如 `7.1.0` → `71000`）。

## 4. 分支策略

### 分支结构

```
master ─────────────────────────────── 已发布版本（保护分支）
  │
  ├── feature/<line> ────────────────── 长期开发线（一条线一个分支）
  │     └── feature/agent-build ─────── Agent 线（方向 B，已随 v7.2.0 合入 release）
  │     └── feature/site-sync ───────── 站点同步线
  └── release/X.Y.Z ────────────────── 发版集成分支
```

| 分支 | 用途 | 保护 |
|------|------|------|
| `master` | 已发布版本，仅通过 release 分支 PR 合入 | ✅ |
| `feature/<line>` | **长期开发线**：一条工作流一个分支，直接在其上按阶段族提交 | — |
| `release/X.Y.Z` | 发版集成分支，从 `master` 拉出，PR 到 `master` | — |

> ⚠️ **历史注记**：本规范曾记载按平台拆分的 `develop-android` / `develop-site` / `develop-ios` 三分支模式（v6.0 起），**该模式从未落地，这三个分支在任何时候都不存在**。实际采用的是「**一条开发线一个长期 `feature/` 分支**」——例如 Agent 线的全部阶段族（F1–F9）都在 `feature/agent-build` 上连续提交，站点同步在 `feature/site-sync` 上。本节已按实际校准，不要再按旧的三分支图操作。

### 日常开发

1. 在对应开发线上直接建/切到该线的长期分支（如 `feature/agent-build`）。
2. **按阶段族提交**：一族一笔（`type(<线>): <阶段族名>` + 中文正文），零碎的仓库工程/文档改动攒够后用一笔 `chore(repo)` 收口。
3. 涉及 shared 模块的改动同样在该线上开发，无需切换分支。
4. 小改动（修 bug、改配置、重写 commit message）直接在该线上提交。
5. **合并回 master** 走发布窗口：切 `release/X.Y.Z` → PR → 触发 CI 发版（见 §5）。

---

## 5. 发版流程

### 5.1 MINOR / MAJOR 发版

适用于新功能、架构变更等较大版本升级。

```bash
# 1. 从 master 拉出 release 分支
git checkout master
git pull
git checkout -b release/X.Y.0

# 2. 合入开发线（改动在 feature/<line> 上，尚未进 master）
git merge feature/agent-build

# 3. 更新版本号
#    gradle.properties: hmp.versionCode + hmp.versionName
#    ROADMAP.md: 新增版本条目 + 更新「当前版本」
#    site/: 更新版本号链接

# 4. 本地构建验证
./gradlew :android:app:assembleRelease

# 5. 提交版本 bump
git add -A && git commit -m "bump: vX.Y.0"

# 6. 推送并创建 PR 到 master
git push origin release/X.Y.0
# → 在 GitHub 创建 PR: release/X.Y.0 → master
```

PR 合入 master 后，CI 自动执行：
- 单元测试 + 版本号校验
- Android + 桌面端并行构建
- 生成分类 Release Notes + SHA256 校验
- 创建 `vX.Y.0` tag + GitHub Release
- 部署产品展示站点到 GitHub Pages

### 5.2 PATCH 发版

适用于 bug 修复、配置调整等小改动。

```bash
# 1. 从 master 拉出 release 分支
git checkout master
git pull
git checkout -b release/X.Y.Z

# 2. 合入开发线（如有需要）
git merge feature/agent-build

# 3. 更新版本号
#    gradle.properties: hmp.versionCode++ , hmp.versionName → X.Y.Z
#    ROADMAP.md: 新增版本条目 + 更新「当前版本」
#    site/: 更新版本号链接

# 4. 本地构建验证
./gradlew :android:app:assembleRelease

# 5. 提交版本 bump
git add -A && git commit -m "bump: vX.Y.Z"

# 6. 推送并创建 PR 到 master
git push origin release/X.Y.Z
```

PATCH 发版流程与 MINOR/MAJOR 相同，统一走 `release/* → master` PR 触发 CI。

### 5.3 手动触发（workflow_dispatch）

当需要跳过 PR 流程直接发布时，可在 GitHub Actions 页面手动触发：

1. 打开 Actions → Release → Run workflow
2. 选择分支，可勾选 `Dry run` 仅构建不发布

---

## 6. 发版检查清单

每次发版前，确认以下事项：

### 版本号
- [ ] `gradle.properties` 中 `hmp.versionName` 已更新
- [ ] `gradle.properties` 中 `hmp.versionCode` 已递增
- [ ] 版本号与 ROADMAP.md 中的版本一致

### 文档
- [ ] `ROADMAP.md` 已新增版本条目（日期 + 变更说明）
- [ ] `ROADMAP.md` 「当前版本」已更新
- [ ] 站点 `site/index.html` 版本号已更新
- [ ] 站点 `site/download.html` 下载链接已更新
- [ ] 站点 `site/changelog.html` 已新增版本条目

### 构建验证
- [ ] 本地 Release 构建通过
- [ ] 真机测试通过（如涉及功能改动）
  - [ ] **平台能力路径必测**（历次 UI 层统一曾在此漏检，见 `7_x/A shared-ui/UI层统一-能力搬迁点检.md`）：
        首启权限流程、锁屏/实时活动、桌面目录选择、后台播放、通知

---

## 7. CI/CD 自动发布

工作流定义在 `.github/workflows/release.yml`。

### 触发条件

- `release/*` 分支的 PR 合并到 `master` 时自动触发
- 支持 `workflow_dispatch` 手动触发

### 流程图

```
                    ┌─ validate（单元测试 + 版本号校验）
                    │
PR 合入 master ────┼─ build-android ──────┐
                    │                       │
                    ├─ build-desktop-macos ─┤
                    ├─ build-desktop-win  ──┼─ release（收集产物 + Notes + tag）
                    └─ build-desktop-linux ─┘     │
                                                    └─ deploy-site
```

### 构建产物

| 平台 | 产物 | 格式 |
|------|------|------|
| Android | APK + AAB | `HMP-vX.Y.Z-release.apk` / `.aab` |
| macOS | DMG | `HMP-vX.Y.Z-macos.dmg` |
| Windows | MSI | `HMP-vX.Y.Z-windows.msi` |
| Linux | DEB + AppImage | `HMP-vX.Y.Z-linux.deb` / `.AppImage` |
| 校验 | SHA256 | `SHA256SUMS.txt` |

### Release Notes

基于 commit message 自动生成，按前缀分类：

| commit 前缀 | 归类 |
|-------------|------|
| `feat:` / `feature:` | ✨ New Features |
| `fix:` | 🐛 Bug Fixes |
| `perf:` / `optimize:` / `refactor:` | ⚡ Performance & Refactoring |
| 其他 | 📦 Other Changes |

### 版本号校验

CI 会自动检查 `gradle.properties` 中的版本号是否与已有 tag 重复，重复则构建失败。

---

## 8. 与 ROADMAP 的同步

- **ROADMAP.md** 是版本历史与变更日志的**单一事实来源**。
- 版本号、发布日期、变更说明以 ROADMAP 为准。
- `versionName` 与 ROADMAP 中的版本号保持一致。

---

**适用范围**：本规范自 **v5.6.1** 起施行。分支策略自 **v6.0** 起调整为按平台拆分的 develop 分支模式（*该模式从未落地*），**自 2026-09 起按实际校准为「一条开发线一个长期 `feature/` 分支」**，见 §4。

---

© 2026 Hearable Music Player | Developed by WLYB
