# Hearable Music Player — 版本命名与发布规范 —— 一处真源如何长出 9 个派生点与三端产物

> **约束**：版本号与对外发布文案只准改仓库根 `release.toml` 一处；9 个派生点一律由 `./gradlew syncVersion` 写、由 `python scripts/sync-release.py sync --check` 核，**手改任何派生点即一次发版事故**。
> **适用范围**：改 `release.toml` 或任一派生点；改 `.github/workflows/release.yml` / `pr-check.yml`；改根 `build.gradle.kts` 的 `checkVersion` / `checkReleaseConsistency` / `syncVersion` / `preflight`；改 `scripts/sync-release.py`；增减平台产物或站点下载键；动 versionCode 公式 ｜ **最后核对**：2026-09-28（按 `python scripts/sync-release.py inspect` / `sync --check` 与源码逐条对照，未跑 gradle）
> **判据**：① `python scripts/sync-release.py sync --check` rc=0（9 个派生点全一致，本地与 CI 同一把尺）② `./gradlew checkVersion`（三段式、versionCode 自洽且严格递增、不与已有 tag 重名；免 gradle 形态见 §3）③ `grep -nE '[0-9]+\.[0-9]+\.[0-9]+' .github/workflows/release.yml | grep -v '#'` 期望 0 行（工作流不得含版本字面量或文案）④ `python scripts/sync-release.py notes` 输出人读一遍（`--check` 只证明"抄对了"，不证明"写得对"）⑤ `./gradlew-lowmem preflight`（本机唯一守门人；`gradle.properties` 是 4G + parallel，直接 `./gradlew preflight` 会杀 daemon）

## 1. 版本号格式

采用**三位版本号** `MAJOR.MINOR.PATCH`（如 `6.11.1`）；文档与对外表述可带 `v` 前缀，构建产物（`versionName`）用纯数字。真源是 `release.toml`（ROADMAP 只是它的归档镜像），**禁止解析 ROADMAP 散文取版本号**。

```bash
python scripts/sync-release.py version   # 有输出即格式合法；非三段纯数字在 derive_code() 直接 Fail
grep -n "read(ROADMAP)" scripts/sync-release.py   # 期望 3 处（写出前原值 / 存在性核对 / mark-void），均非正文抽取
```

## 2. 何时升级哪一位

| 类型 | 何时递增 | 示例 |
|------|----------|------|
| **MAJOR** | 不兼容的架构或产品方向大变更、重大破坏性改动 | 单体 → 模块化（v4 → v5） |
| **MINOR** | 新功能或明显体验/能力提升，保持向后兼容 | 新页面、新能力、较大重构 |
| **PATCH** | 仅 bug 修复、文案/样式小调整、文档/配置更新，无新功能 | 修崩溃、改文案、更新依赖说明 |

每次正式发布**只递增一位**（能 PATCH 就 PATCH，否则 MINOR，再否则 MAJOR），**不跳号**（`6.11.1` 下一版只能是 `6.11.2` 或 `6.12.0`）。「只递增一位」**无机器判据** —— `checkVersion` 只保证严格递增（`build.gradle.kts:389-401`），从 `7.2.2` 跳到 `7.9.0` 照样绿，只有人守。版本条目由 `syncVersion` 追加到 ROADMAP / 站点时间线（脚本只碰 `<!-- BEGIN/END SYNCED RELEASE ENTRY -->` 标记块），页脚散文仍人写。

## 3. 版本号与构建系统

`release.toml` 是唯一手改点，只写 `version`；`versionCode` 由脚本派生、**禁止手写**，`gradle.properties` 的两行版本键同样禁止手改。各模块仍用 `project.findProperty("hmp.versionName")` 引用（取值来源变了，用法没变）。

```toml
# release.toml —— 手改点（下面只是公式讲解示例，当前真值见 release.toml）
version = "7.2.1"          # versionCode = MAJOR*10000 + MINOR*1000 + PATCH → 72001
# gradle.properties —— syncVersion 的产物，别手改（当前真值见 release.toml）
hmp.versionCode=72001
hmp.versionName=7.2.1
```

自洽断言在 `build.gradle.kts:369-379`：`parts.size != 3` → `hmp.versionName 必须是 MAJOR.MINOR.PATCH`；`derivedCode != currentCode` → 报错并指向 `docs/spec/hmp-release.md §3`。两条边界：① 版本号与已有 tag 重名即抛错，**发布之后、下次 bump 之前跑 `checkVersion` 必红，属正常状态**；② 读不到上一 tag 的 `gradle.properties` 时只打印 `[!] … 跳过递增校验`、不失败，浅克隆会让这道闸静默放行，故 `pr-check.yml:46` 强制 `fetch-depth: 0`。

```bash
python scripts/sync-release.py version                           # 不依赖 gradle 的自洽核对（本机 gradle 形态见 §5；Python 前置见 §8）
grep -nE '^hmp\.(versionCode|versionName)=' gradle.properties     # 例：72001 ↔ 7.2.1，按公式手算即可核
```

## 4. 分支策略

### 分支结构

- `master`：已发布版本，**保护分支**，仅通过 `release/X.Y.Z` 的 PR 合入。
- `feature/<line>`：**长期开发线，一条开发线一个分支**（如 `feature/agent-build` / `feature/site-sync`），直接在其上按阶段族提交。
- `release/X.Y.Z`：发版集成分支，从 `master` 拉出、PR 回 `master`；分支名与版本不一致由 CI 硬拦（`pr-check.yml:72-81`）。

> 历史注记：按平台拆分的 `develop-android` / `develop-site` / `develop-ios` 三分支模式（v6.0 起）**从未落地，这三个分支在任何时候都不存在**，别按它操作（`git branch -a | grep -c develop-` = 0）。本规范自 v5.6.1 起施行；2026-09 按实际校准为「一条开发线一个长期 `feature/` 分支」。

### 日常开发

1. 在对应开发线的长期分支上直接开发（shared 模块改动同样在其线上，无需切换分支）；小改动（修 bug、改配置、改 commit message）直接提交。
2. **按阶段族提交**（一族一笔：`type(<线>): <阶段族名>` + 中文正文；零碎的仓库工程/文档改动攒够一笔 `chore(repo)` 收口），**合并回 master 走发布窗口**：切 `release/X.Y.Z` → PR → 触发 CI 发版（见 §5）。

## 5. 发版流程

一次发版 = 只改 `release.toml` → `syncVersion` → `sync --check` 绿 → **本机 preflight** → bump 提交 → 推送建 PR；PATCH 与 MINOR/MAJOR 同流程。任何一环手改派生点或跳过核对，都按一次发版事故对待。

### 5.1 MINOR / MAJOR 发版

```bash
# 1. 拉 release 分支；2. 合入开发线（改动还在 feature/<line> 上时）
git checkout master && git pull && git checkout -b release/X.Y.0
git merge feature/agent-build
# 3. 更新版本号 —— 只改 release.toml 这一个文件（version / date / [[section]]）
#    versionCode 由 MAJOR*10000+MINOR*1000+PATCH 派生别手写；date 发布当天补；
#    [[section]] 写用户视角的话，commit hash 与 R 编号属 ROADMAP
./gradlew syncVersion
#    覆盖式写出：gradle.properties、site/js/config.js、site/index.html 的 JSON-LD、
#               shared-ios/.../Anchor.kt（-aN 后缀原样保留）、iOS project.yml / Info.plist / project.pbxproj
#    追加式写出：ROADMAP.md 版本条目、site/changelog.html 时间线（该版已有手写条目则跳过）
#    ⚠️ 以上一律不要手改；核对：python scripts/sync-release.py sync --check（CI 的 validate 跑同一把尺）
# 4. 本机构建验证 —— CI 已不跑单元测试，这一步是唯一守门人，别跳；
#    本机 gradle.properties 是 4G + parallel，直接 ./gradlew preflight 会杀 daemon，
#    实际形态是仓库根的包装脚本 ./gradlew-lowmem preflight（1G + in-process；Windows cmd 用 gradlew-lowmem.bat）
./gradlew-lowmem preflight
./gradlew-lowmem :android:app:assembleRelease
# 5. 提交版本 bump（release.toml 与 9 个派生点必须同批提交）；6. 推送并创建 PR 到 master
git add -A && git commit -m "bump: vX.Y.0"
git push origin release/X.Y.0
```

`preflight` = `checkVersion` + `checkReleaseConsistency` + `testAll`（`build.gradle.kts:459`）；版本号未 bump 的工作树上它必红在「与已有 tag 重复」，属正常状态（见 §3）。PR 合入后 CI 的动作见 §7。

### 5.2 PATCH 发版

```bash
# 1. 拉 release 分支：git checkout master && git pull && git checkout -b release/X.Y.Z
# 2. 合入开发线（如需要）：git merge feature/agent-build
# 3. 更新版本号 —— 只改 release.toml（version / notes 小节），然后：
./gradlew syncVersion
python scripts/sync-release.py sync --check      # 必须绿；完整派生点清单见 §5.1 第 3 步与 §6
# 4. 本机 preflight（唯一守门人，别跳；低内存形态见 §5.1 第 4 步）
# 5–6. 提交 bump 并推送建 PR：git add -A && git commit -m "bump: vX.Y.Z" && git push origin release/X.Y.Z
```

### 5.3 手动触发（workflow_dispatch）

Actions → Release → Run workflow，选分支，可勾 `Dry run`（仅构建不发布）。勾了 `Dry run` 就**不得触碰线上**：`Create tag and GitHub Release`（`release.yml:408`）与 `deploy-site`（`:456`）两步都挂了 `if: …dry_run != 'true'`；dry run 会真跑四个构建 job、collect、产物齐全断言与 Notes 渲染，只在发布前收手 —— v7.2.1 事故的一半原因就是 dry run 整个跳过 release job，「收集 + 断言」从没被演练过。

## 6. 发版检查清单

**① 版本真源（唯一手改点）**
- [ ] `release.toml` 的 `version` 已更新（**`versionCode` 由脚本派生，不要手写**）、`date` 发布当天回填；`[[section]]` 是用户视角的话（commit hash / R 编号 / 内部待办放 ROADMAP）
- [ ] `[[artifact]]` 只在平台增减时改（改了同步 `site/js/config.js` 的 `assets`，脚本核对孤儿键）；跑过 `./gradlew syncVersion` 且 `python scripts/sync-release.py sync --check` 绿

**② 派生点（由 syncVersion 写，禁止手改）—— 版本声明一致性受检清单**
- [ ] `gradle.properties`（`hmp.versionName` / `hmp.versionCode`）、`site/js/config.js`（`version` / `released`）、`site/index.html` 的 JSON-LD `softwareVersion`
- [ ] `ROADMAP.md` 版本条目、`site/changelog.html` 时间线条目（该版已有手写条目时脚本跳过）、`shared-ios/.../Anchor.kt`（`-aN` 后缀原样保留）
- [ ] iOS 三处：`ios/HMP/project.yml` / `HMP/Info.plist` / `HMP.xcodeproj/project.pbxproj`
- [ ] 自查 `./gradlew checkReleaseConsistency`（与 `sync --check` 同一实现）

**③ 脚本管不到、必须人看的**
- [ ] `date` 与发布日不符时 CI 只 warning 不失败，必须人工确认已按发布日回填（见 **D-6**）
- [ ] 读一遍 `python scripts/sync-release.py notes` 的输出，对每条平台/产物断言去实物找反证 —— `--check` 只证明「抄对了」，不证明「写得对」（条目数 > 80 脚本直接 Fail）
- [ ] README / CLAUDE 的「最新版本」叙述不在门禁内（见 **D-7**），发版后手改

**④ 构建验证**
- [ ] 本机 `preflight` 通过（版本号不变量 + 派生点一致 + 全量单测；**CI 不跑单元测试，这里是唯一把关**；低内存形态见 §5.1）
- [ ] PR 上 `Pre-release Check` 已通过；本地 Release 构建通过
- [ ] 真机测试（如涉及功能改动）：**平台能力路径必测** —— 首启权限流程、锁屏/实时活动、桌面目录选择、后台播放、通知

## 7. CI/CD 自动发布

发版保障由两个工作流分担：`.github/workflows/pr-check.yml` 合入前拦截，`.github/workflows/release.yml` 合入后构建发布。**同一条规则只准有一份实现** —— 工作流只调任务/脚本，不内嵌规则、版本字面量或文案。

### 合入前预检（pr-check.yml）

`release/*` 分支的 PR 打开/更新时自动触发（draft PR 跳过）；不产发布物、不写仓库，只回答「合入之后会不会炸」。前两项直接调用 `release.yml` validate 的那对 Gradle 任务，不在 CI 里另写一套规则：

| 检查项 | 拦的问题 |
|--------|----------|
| 版本号自洽与多端声明一致 | `versionCode` 未递增、`versionName` 与 code 不自洽、站点 / iOS / 文档版本声明未同步 |
| 分支名与版本号一致 | `release/X.Y.Z` 分支里写的却是别的版本（`pr-check.yml:72-81`） |
| FFmpeg 资产可用性与架构 | Release 上的包缺失或被替换，以及**装了错的 CPU 架构** |

- 第三项的理由：SHA256 只能证明「下载的和配的对得上」，证明不了架构 —— 上游曾出现 `macos/amd64` 链接实为 arm64 单架构二进制，SHA256 全绿而 Intel 版 DMG 无法运行；故预检解包解析 Mach-O / ELF / PE 头部，与 `build.gradle.kts` 的 map key 对照。
- **feature PR 刻意不跑版本号检查**：`checkVersion` 要求 `versionCode` 相对上一 tag 严格递增，功能 PR 不 bump 必红。
- 本地复现：`./gradlew checkVersion checkReleaseConsistency`（或本机形态 `./gradlew-lowmem …`）与 `python3 .github/scripts/check-ffmpeg-assets.py`（需出网 + `GITHUB_TOKEN`）。

### 触发条件

`release/*` 分支的 PR 合并到 `master` 时自动触发；也支持 `workflow_dispatch` 手动触发（可勾 `dry_run`，见 §5.3）。

### 流程图

```
                    ┌─ validate（版本不变量 + 派生点一致；不跑单测）
PR 合入 master ─────┼─ build-android ──────────┐
                    ├─ build-desktop-macos ────┼─ release（收集产物 + 齐全断言 + Notes + tag）
                    ├─ build-desktop-windows ──┤        │
                    └─ build-desktop-linux ────┘        └─ deploy-site
```

### 构建产物

| 平台 | 架构 | 产物 |
|------|------|------|
| Android | — | `HMP-vX.Y.Z-release.apk` + `.aab` |
| macOS | Apple Silicon (arm64) | `HMP-vX.Y.Z-macos-arm64.dmg` |
| Windows | x86_64 | `HMP-vX.Y.Z-windows-x86_64.msi` |
| Linux | x86_64 | **仅 DEB** `HMP-vX.Y.Z-linux-x86_64.deb`（AppImage 已于 v7.2.1 移除：`TargetFormat.AppImage` 是 jpackage 的解包目录，产不出 `.AppImage` 文件） |
| 校验 | — | `SHA256SUMS.txt` |

产物名单的真源是 `release.toml` 的 `[[artifact]]`：`from_dir`/`from_glob` 供收集与改名，`site_key` 必须与 `site/js/config.js` 的 `assets` 键一一对应，多出的孤儿键点开就是 404（曾出现 `appimage` 键残留）；CI 齐全断言的名字取自 `python scripts/sync-release.py assets`。

### Release Notes

**正文由 `release.toml` 渲染**（`python scripts/sync-release.py notes`）：`[[section]]` 出小节与条目、`[[artifact]]` 出产物表、`notes_footer` 出「iOS 不提供安装包」这类缺项说明；工作流里不含任何文案或文件名。发布时把渲染结果整段打进日志（dry run 也会），**看一眼再放出去**。

历史教训一句话：这里曾用 awk 从 ROADMAP 抽 `### vX.Y.Z (` 一节，最新版后面再没有该标题 → 冲到 EOF，把内部章节灌进公开 Notes（v7.2.1 的 `body.md` 抽了 172 行，线上正文发布后人工删到 58 行）。commit 消息分类只作附录：`feat:`/`feature:`→Features、`fix:`→Fixes、`perf:`/`optimize:`/`refactor:`→Performance & Refactoring、其他→Other。

### 版本校验

`validate` job 跑两个任务，任一失败则四个构建 job 全部不启动：`checkVersion`（三段式；`versionCode` 与 `versionName` 自洽；不得与已有 tag 重名；须比上一 tag **严格递增** —— 回退的包 Android 直接装不上）与 `checkReleaseConsistency`（多端版本声明一致性，受检清单见 §6 第 ② 组；实现已委托 `sync --check`，`build.gradle.kts:441-451`）。边界（免得当成不存在的保证）：① 「四 job 全不启动」只在 `release.yml` 的依赖图上成立，单独跑桌面打包或本机 `releaseDesktop` 都不经此闸（见 **D-3**）；② 两个任务都不读产物，核对全在源码层，**没有独立 oracle**（见 **D-2**）。

### 桌面端的 FFmpeg 二进制

桌面端播放引擎依赖原生 FFmpeg，不随源码提交：托管在本仓库 Release **`ffmpeg-binaries`**（固定 tag，不随版本发布变动），由**发版人手动上传**三项 `macos/arm64`、`linux/amd64`、`windows/amd64`。构建时 `downloadFFmpeg` 按「构建机 OS + 架构」精确下载，**SHA256 不符立即失败**，再由 `injectFFmpeg` 注入安装包；升级某项即同步改 `ffmpegArtifacts` 里该条的 `version` / `url` / `sha256`。

macOS 目前只有 Apple Silicon 产物：上游 FFmpeg 9.0 **未提供真正的 x86_64 macOS 构建**，其 `macos/amd64` 链接返回的实为 arm64 二进制（与 arm64 包 SHA256 完全相同），待上游提供真 x86_64 源后再补。

## 8. 真源分层

| 层 | 载体 | 权威范围 | 谁写 |
|---|---|---|---|
| 配置 | **`release.toml`** | **当前版本**的号、日期、对外文案、产物清单 | 人（每版一次） |
| 流程 | `.github/workflows/*.yml` | 构建与发布步骤；**不放任何版本字面量或文案** | 人 |
| 工具 | `scripts/sync-release.py` | 派生点的写与核 | 人 |
| 派生 | `gradle.properties`、iOS 三处、`site/js/config.js`、`site/index.html`、`Anchor.kt` | 无自主权 | 脚本 |
| 归档 | **`ROADMAP.md`**、`site/changelog.html` 时间线 | 工程史与历史版本口径（含"某版未出包"） | 脚本追加 + 人补结论 |
| 工序 | `skills/release-prep/SKILL.md` | 发版步骤、判据、停机点 | 人 |

- 脚本的 7 个子命令就是这层的接口：`inspect [--json]` / `sync --write|--check` / `notes [--version X] [--out]` / `version` / `assets` / `collect` / `mark-void <版> --reason "…"`；只有 `sync --write` 与 `mark-void` 写仓库。前置：Python 3.11+，或 3.9 / 3.10 + `pip install tomli`。
- 写出语义两种：覆盖式 = `gradle.properties` / `config.js` / `index.html` / `Anchor.kt` / iOS 三处；追加式 = ROADMAP 版本条目 / 站点时间线条目（该版已有手写条目则跳过，工程史优先）。每个写出点用唯一锚点定位，找不到或歧义即失败，绝不猜、绝不新建。
- **ROADMAP 不再是程序解析对象**，只作内部工程史（commit hash、R 编号、审查结论）；它与 `release.toml` 的内容重叠靠规矩划开（对外 vs 对内），不靠脚本对齐散文。历史版本说明看 ROADMAP 与站点归档；`release.toml` 只有当前版，`notes --version <旧版>` 会被拒。版本作废（合进 master 却没出包）用 `python scripts/sync-release.py mark-void <版> --reason "…"` 在 ROADMAP 与站点时间线打两处标记，结论由人补写。

## 偏差登记

| 编号 | 与代码不符处 | 证据（file:line） | 归属 |
|---|---|---|---|
| **D-1** | CI 不跑单测、`preflight` 是唯一守门人，但其低内存形态没被写进别处：`CLAUDE.md` 与 release-prep Skill 仍教 `./gradlew preflight` | `CLAUDE.md:334-335`；`skills/release-prep/SKILL.md:65`；对照 `gradle.properties:9,13`、`build.gradle.kts:459` | **R31** |
| **D-2** | 写与核共用同一套正则，属自比、**无独立 oracle**：锚点位移类错误能同时骗过 `--check`；`config.js` 取首个 `version:`、`pbxproj` 全量替换 `MARKETING_VERSION` | `scripts/sync-release.py:219`（`sub1`）、`:240`、`:269-274`、`:441-442`（`--check` 复用 `all_steps`） | **R53** |
| **D-3** | 桌面端版本兜底与静默缺件：`packageVersion` / `msiPackageVersion` 缺属性回落 `"1.0.0"`、`dmgPackageVersion = "1"` 硬编码；`copyToReleases` 找不到产物只 `println` 不失败；桌面 job 自身不跑 `checkVersion` | `desktop/app/build.gradle.kts:332,357,369`；`build.gradle.kts:41-50`；`release.yml:79,133,172,224` | **R45** |
| **D-4** | iOS 三处由脚本改写，但**从未在 macOS 上验证**（本机只能做源码级核对），改动可能被 Xcode 工程重载写回 | `scripts/sync-release.py:260-274`；`TODO.md:85` | **R38** |
| **D-5** | 发布**之后**的公开面（线上 Notes / 产物齐备 / 站点是否已随本次部署）无任何自动核对 | `TODO.md:84` | **R37** |
| **D-6** | `date` 与发布日不符只输出 `::warning::`、不失败，deploy-site 照常发布 | `.github/workflows/release.yml:483-488` | **待用户决定**（是否改硬断言） |
| **D-7** | 根文档「最新发布版本」散文不在门禁内，已实测失真：停在 v7.2.1，而 tag 已是 v7.2.2 | `CLAUDE.md:7,296`；`README.md:219`；`release.toml:20-21` | **R54 / R36** |

## 修订记录

- 2026-09-28：按三份规范统一格式**精简重写**（667 → 218 行）。删去 §0 速查表（21 条）、§9 常见错误与后果（12 条）、§10 的 9 行详表与「本节自身的判据」及文末「适用范围」散文段，改为文末 7 行「偏差登记」；删去 §0 末粘贴的 `inspect` / `sync --check` / `assets` 实测输出、各节「约束 / 判据 / 检查」三段式骨架、§5 末尾对 §7 CI 步骤的整段复述；`date` 告警等不再两处重复。
- 2026-09-28：**无内容删除** —— 事实逐条保留（含就地写明的 `gradlew-lowmem` 低内存形态，取自仓库根既有包装脚本）。§1–§8 编号与标题主体未动；新增的「偏差登记」「修订记录」置于文末；§3 示例值保留但已紧邻标注「当前真值见 `release.toml`」。
