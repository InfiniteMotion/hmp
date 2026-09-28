# Hearable Music Player — 版本命名与发布规范

本文档为项目**版本号格式、发版流程与分支策略**的正式约定。**自 v5.6.1 起施行**。

---

## 1. 版本号格式

采用 **三位版本号**：`MAJOR.MINOR.PATCH`（如 `6.0.0`、`6.11.1`）。

- 文档与对外表述可带前缀 `v`，如 **v6.11.1**。
- 构建产物使用纯数字：`versionName = "6.11.1"`，真源是 `release.toml`（ROADMAP 只是它的归档镜像）。

## 2. 何时升级哪一位

| 类型 | 何时递增 | 示例 |
|------|----------|------|
| **MAJOR** | 不兼容的架构或产品方向大变更、重大破坏性改动 | 单体 → 模块化（v4 → v5） |
| **MINOR** | 新功能或明显体验/能力提升，保持向后兼容 | 新页面、新能力、较大重构 |
| **PATCH** | 仅 bug 修复、文案/样式小调整、文档/配置更新，无新功能 | 修崩溃、改文案、更新依赖说明 |

**原则**：

- 每次正式发布**只递增一位**：能 PATCH 就 PATCH，否则 MINOR，再否则 MAJOR。
- 避免跳号：从 `6.11.1` 下次应为 `6.11.2` 或 `6.12.0`，不直接出现未发布过的版本号。
- 版本条目由 `syncVersion` 追加到 ROADMAP / 站点时间线；「当前版本」页脚仍人写（脚本不碰散文）。

## 3. 版本号与构建系统

真源是仓库根的 **`release.toml`**，只写 `version`；`versionCode` 由脚本派生并写进 `gradle.properties`：

```toml
# release.toml —— 手改点
version = "7.2.1"
date = "2026-09-25"
```

```properties
# gradle.properties —— syncVersion 的产物，别手改
hmp.versionCode=72001
hmp.versionName=7.2.1
```

- **versionName**：与三位版本号一致。各模块仍用 `project.findProperty("hmp.versionName")` 引用（取值来源变了，用法没变）。
- **versionCode**：`MAJOR*10000 + MINOR*1000 + PATCH`（`7.2.1` → `72001`）。手写会立刻被 `checkVersion`
  的自洽断言拦下，所以脚本才负责算。

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

# 3. 更新版本号 —— 只改 release.toml 这一个文件
#    version = "X.Y.0"     （versionCode 由 MAJOR*10000+MINOR*1000+PATCH 派生，别手写）
#    date    = 发布当天再补（可先留空）
#    [[section]] 写这一版对用户说的话；commit hash 与 R 编号不要进这里，那些属于 ROADMAP
./gradlew syncVersion      # 把 release.toml 同步到各派生点
#    覆盖式写出：gradle.properties、site/js/config.js、site/index.html 的 JSON-LD、
#               shared-ios/Anchor.kt、iOS project.yml / Info.plist / project.pbxproj
#    追加式写出：ROADMAP 版本条目、site/changelog.html 时间线（该版已有手写条目则跳过）
#    ⚠️ 上面这些一律不要手改 —— 从前它们靠人抄 9 处，漏一处就是一次发版事故。
#    核对：python scripts/sync-release.py sync --check   （CI 的 validate 也跑它）

# 4. 本地构建验证（CI 已不跑单元测试 —— 这一步是唯一守门人，别跳）
./gradlew preflight
./gradlew :android:app:assembleRelease

# 5. 提交版本 bump
git add -A && git commit -m "bump: vX.Y.0"

# 6. 推送并创建 PR 到 master
git push origin release/X.Y.0
# → 在 GitHub 创建 PR: release/X.Y.0 → master
```

PR 合入 master 后，CI 自动执行：
- 版本号不变量 + 派生点一致性（`checkVersion` / `checkReleaseConsistency` → 委托 `sync --check`）
- Android + 桌面端并行构建
- 产物齐全断言（名单取自 `release.toml`）+ SHA256 + Release Notes（正文由 `release.toml` 渲染）
- 创建 `vX.Y.0` tag + GitHub Release（可原地重跑：tag 存在即复用）
- 部署产品展示站点到 GitHub Pages（只核对与 `release.toml` 一致，不再现场改写内容）

> ⚠️ **单元测试不在 CI 跑**（自 2026-09-24）：`testAll` 在 runner 上会静默挂死、拖住整条发版通道，
> 成因未查清（TODO R31），故从 validate 移除。代价是**master 上的测试回归没有无人值守的把关**，
> 单元测试改由本机 `./gradlew preflight` 在 bump 前负责 —— 这一步现在是唯一的守门人，别跳。

### 5.2 PATCH 发版

适用于 bug 修复、配置调整等小改动。

```bash
# 1. 从 master 拉出 release 分支
git checkout master
git pull
git checkout -b release/X.Y.Z

# 2. 合入开发线（如有需要）
git merge feature/agent-build

# 3. 更新版本号 —— 只改 release.toml（version / notes 小节），然后：
./gradlew syncVersion                    # 派生点全部跟上（含 ROADMAP 与站点时间线条目）
python scripts/sync-release.py sync --check   # 必须绿
#    完整清单与原因见 §5.1 第 3 步与 §6

# 4. 本地构建验证（CI 已不跑单元测试 —— 这一步是唯一守门人，别跳）
./gradlew preflight
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

### 版本真源（唯一手改点）
- [ ] `release.toml` 的 `version` 已更新 —— **`versionCode` 由脚本派生，不要手写**
- [ ] `[[section]]` 是用户视角的话；commit hash、R 编号、内部待办一律放 ROADMAP
- [ ] `[[artifact]]` 只在平台增减时改（改了要同步 `site/js/config.js` 的 `assets`，脚本会核对孤儿键）
- [ ] 跑过 `./gradlew syncVersion`

### 派生点（由 syncVersion 写，**禁止手改**）
- [ ] `gradle.properties`（`hmp.versionName` / `hmp.versionCode`）
- [ ] `site/js/config.js`（`version` / `released`）、`site/index.html`（JSON-LD `softwareVersion`）
- [ ] `site/changelog.html` 时间线条目、`ROADMAP.md` 版本条目（该版已有手写条目时脚本跳过）
- [ ] `ios/HMP/project.yml`（`CFBundleShortVersionString` + `MARKETING_VERSION`）
- [ ] `ios/HMP/HMP/Info.plist`、`ios/HMP/HMP.xcodeproj/project.pbxproj`
- [ ] `shared-ios/.../Anchor.kt`（`-aN` 后缀由脚本原样保留）
- [ ] 自查：`python scripts/sync-release.py sync --check` 或 `./gradlew checkReleaseConsistency`（同一实现）

### 脚本管不到、必须人看的
- [ ] `release.toml` 的 `date` 在发布当天回填（部署时只核对、不擅自改写；不符会告警）
- [ ] **`--check` 只证明"抄对了"，不证明"写得对"**：读一遍 `python scripts/sync-release.py notes`
      的输出，对里面每条平台/产物断言去实物找反证（v7.2.1 之前就出现过"文档说 AppImage 已修好、
      实际早改发 DEB"这种一手写错的口径）
- [ ] README / CLAUDE 里的「最新版本」叙述不在门禁内（TODO R36），发版后手改

### 构建验证
- [ ] **本地 `./gradlew preflight` 通过**（版本号不变量 + 发布一致性 + 全量单元测试；**CI 不跑单元测试，这里是唯一把关**）
- [ ] 发版分支推到远端后，PR 上的 **`Pre-release Check` 已通过**（自动校验版本声明一致性与 FFmpeg 资产可用性）
- [ ] 本地 Release 构建通过
- [ ] 真机测试通过（如涉及功能改动）
  - [ ] **平台能力路径必测**（历次 UI 层统一曾在此漏检，见 `7_x/A shared-ui/UI层统一-能力搬迁点检.md`）：
        首启权限流程、锁屏/实时活动、桌面目录选择、后台播放、通知

---

## 7. CI/CD 自动发布

发版保障由**两个工作流分担**：`.github/workflows/pr-check.yml` 在合入前拦截，`.github/workflows/release.yml` 在合入后构建发布。

### 合入前预检（pr-check.yml）

`release/*` 分支的 PR 打开/更新时自动触发（draft PR 跳过）。不产发布物、不写仓库，只回答「合入之后会不会炸」：

| 检查项 | 拦的问题 |
|--------|----------|
| 版本号自洽与多端声明一致 | `versionCode` 未递增、`versionName` 与 code 不自洽、站点 / iOS / 文档版本声明未同步 |
| 分支名与版本号一致 | `release/X.Y.Z` 分支里 `gradle.properties` 写的却是别的版本 |
| FFmpeg 资产可用性与架构 | Release 上的包缺失或被替换，以及**装了错的 CPU 架构** |

前两项直接调用 `release.yml` 的 validate 那一对 Gradle 任务，**不在 CI 里另写一套规则**，避免两处逻辑随时间漂移。

第三项容易被轻视但代价最大：SHA256 只能证明「下载的和配的对得上」，证明不了「装的是对的架构」。上游曾出现 `macos/amd64` 链接实际返回 arm64 单架构二进制 —— SHA256 对得上、CI 全绿，产出的 Intel 版 DMG 却无法运行，只能靠人工解包才暴露。所以预检会解包解析 Mach-O / ELF / PE 头部，与 `build.gradle.kts` 的 map key 对照。

本地复现：

```bash
./gradlew checkVersion checkReleaseConsistency
python3 .github/scripts/check-ffmpeg-assets.py
```

> **feature PR 不跑版本号检查，这是刻意的**：`checkVersion` 要求 `versionCode` 相对上一个 tag 严格递增，而功能 PR 不会 bump 版本号，跑了必红。

**若要连同三端打包一起验证**：手动 `workflow_dispatch` 运行 `Release` 工作流并勾选 `dry_run` —— 它在不合入的前提下跑完四个构建 job，只跳过最后的发布。验证打包工具链改动（如这次的 appimagetool / WiX）时用这个。

### release.yml 触发条件

- `release/*` 分支的 PR 合并到 `master` 时自动触发
- 支持 `workflow_dispatch` 手动触发（可勾 `dry_run`）

### 流程图

```
                    ┌─ validate（版本号不变量 + 发布一致性；不跑单元测试）
                    │
PR 合入 master ────┼─ build-android ─────────────┐
                    │                               │
                    ├─ build-desktop-macos ─────────┤
                    ├─ build-desktop-windows ───────┼─ release（收集产物 + Notes + tag）
                    └─ build-desktop-linux ─────────┘     │
                                                          └─ deploy-site
```

### 构建产物

| 平台 | 架构 | 产物 | 格式 |
|------|------|------|------|
| Android | — | APK + AAB | `HMP-vX.Y.Z-release.apk` / `.aab` |
| macOS | Apple Silicon (arm64) | DMG | `HMP-vX.Y.Z-macos-arm64.dmg` |
| Windows | x86_64 | MSI | `HMP-vX.Y.Z-windows-x86_64.msi` |
| Linux | x86_64 | **仅 DEB** | `HMP-vX.Y.Z-linux-x86_64.deb`（不提供 AppImage：`TargetFormat.AppImage` 是 jpackage 的解包目录，产不出 `.AppImage` 文件，该格式已于 v7.2.1 移除） |
| 校验 | — | SHA256 | `SHA256SUMS.txt` |

> 站点侧的下载链接模板在 `site/js/config.js` 的 `assets` 里，键必须与上表**一一对应** —— 多出来的键会指向不存在的资产、点开就是 404（曾出现过 `appimage` 键残留）。

### Release Notes

**正文由 `release.toml` 渲染**（`python scripts/sync-release.py notes`）：`[[section]]` 出小节与条目，
`[[artifact]]` 出产物表，`notes_footer` 出"iOS 不提供安装包"这类缺项说明。工作流里不再嵌任何文案或文件名。

> 历史：这里曾经是"从 ROADMAP 抽 `### vX.Y.Z (` 那一节"。散文当数据解析害处实测过两次 ——
> ① 最新版本后面再没有 `### v` 标题，awk 冲到 EOF，把「关键技术演进 / 未来发展方向」灌进公开 Notes
> （v7.2.1 的 `body.md` 172 行抽取段，线上正文是发布后人工删到 58 行的）；② ROADMAP 标题的形状
> （半角左括号紧跟日期）成了隐式解析契约，改一下就会静默漏整节而 CI 全绿。现在 ROADMAP 只给人读，
> 程序不再解析它。

发布时 `release.yml` 会把渲染结果整段打进日志（dry run 也会），**看一眼再放出去**。

commit message 分类降级为「工程提交」附录：

| commit 前缀 | 归类 |
|-------------|------|
| `feat:` / `feature:` | ✨ Features |
| `fix:` | 🐛 Fixes |
| `perf:` / `optimize:` / `refactor:` | ⚡ Performance & Refactoring |
| 其他 | 📦 Other |

### 版本校验

`validate` job 跑两个 Gradle 任务，任一失败则四个构建 job 全部不启动：

- **`checkVersion`**：`versionName` 必须是三段式；`versionCode` 与 `versionName` 自洽；
  不得与已有 tag 重名；`versionCode` 须比上一 tag **严格递增**（回退的包 Android 直接装不上）。
- **`checkReleaseConsistency`**：以 `gradle.properties` 为真源核对多端版本声明，
  受检清单见 §6「版本声明一致性」，任一项不一致即报 drift 并失败。

两者都能在本机跑，bump 提交前建议自查：

```bash
./gradlew checkVersion checkReleaseConsistency
```

### 桌面端的 FFmpeg 二进制

桌面端播放引擎依赖原生 FFmpeg，不随源码提交，供给方式：

- 托管在本仓库 GitHub Release **`ffmpeg-binaries`**（固定 tag，不随版本发布变动）
- 由**发版人手动上传**当前支持的三项：`macos/arm64`、`linux/amd64`、`windows/amd64`
- 构建时 `desktop/app/build.gradle.kts` 的 `downloadFFmpeg` 按「构建机 OS + 架构」精确下载，
  **SHA256 不符立即失败**，再由 `injectFFmpeg` 注入安装包
- 升级某项：同步改 `ffmpegArtifacts` 里该条的 `version` / `url` / `sha256` 三者

> 为何不再用第三方源：原第三方源在 CI 出网受限时会中断 macOS / Linux / Windows 三端打包。
> 改到 GitHub Release 后与 CI 同网，可用性由平台保证。
>
> macOS 目前只有 Apple Silicon 产物：上游 FFmpeg 9.0 **未提供真正的 x86_64 macOS 构建**，
> 其 `macos/amd64` 链接返回的实为 arm64 二进制（与 arm64 包的 SHA256 完全相同）。
> 待上游提供或另寻可用 x86_64 源后，再补齐 `macos-x86_64` 产物，见本节构建产物表。

---

## 8. 真源分层（谁是什么的权威）

| 层 | 载体 | 权威范围 | 谁写 |
|---|---|---|---|
| 配置 | **`release.toml`** | **当前版本**的号、日期、对外文案、产物清单 | 人（每版一次） |
| 流程 | `.github/workflows/*.yml` | 构建与发布步骤 | 人；**不放任何版本字面量或文案** |
| 工具 | `scripts/sync-release.py` | 派生点的写与核 | 人 |
| 派生 | `gradle.properties`、iOS 三处、`site/js/config.js`、`Anchor.kt` | 无自主权 | 脚本 |
| 归档 | **`ROADMAP.md`**、`site/changelog.html` 时间线 | 工程史与历史版本口径（含"某版未出包"） | 脚本追加 + 人补结论 |
| 工序 | `skills/release-prep/SKILL.md` | 发版步骤、判据、停机点 | 人 |

- **ROADMAP 不再是程序解析对象**，只作内部工程史：commit hash、R 编号、审查结论这类写给未来的自己。
- 它与 `release.toml` 会有内容重叠 —— 靠规矩划开（对外 vs 对内），**不靠脚本对齐两份散文**。
- 历史版本的说明看 ROADMAP 与站点归档；`release.toml` 只有当前版，`notes --version <旧版>` 会被拒。
- 版本作废（合进 master 却没出包）用 `scripts/sync-release.py mark-void <版> --reason "…"` 打两处标记，
  那句结论由人补写。

---

**适用范围**：本规范自 **v5.6.1** 起施行。分支策略自 **v6.0** 起调整为按平台拆分的 develop 分支模式（*该模式从未落地*），**自 2026-09 起按实际校准为「一条开发线一个长期 `feature/` 分支」**，见 §4。

---

© 2026 Hearable Music Player | Developed by WLYB
