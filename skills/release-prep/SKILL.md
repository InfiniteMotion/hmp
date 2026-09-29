---
name: release-prep
description: HMP 发版前半程：定版本号 → 写 release.toml → 同步派生点 → 点检 → 本机 preflight → 备好 PR。当用户说"准备发版""发 X.Y.Z"" bump 版本""跑发版流程"时使用。合并之后的发布由 release.yml 自动执行，本 Skill 不碰 push/PR/合并。
---

# HMP 发版预检（release-prep）

真源是仓库根的 `release.toml`，它只描述**当前这一次发布**。所有派生点
（`gradle.properties`、iOS 三处、站点、ROADMAP/站点归档条目）由 `scripts/sync-release.py` 写与核。

## 铁律

1. **不许手改派生点。** 版本相关的一切改动都要经过 `sync`。历史上 9 处声明靠人抄，抄漏就是
   发版事故（DEVELOP.md 漏改、站点 `appimage` 死链都是这么来的）。
2. **不许为了让检查变绿而放宽判据**（`MAX_ITEMS`、锚点、断言）。要动判据 → 停下问用户。
3. **不可逆动作只准备不执行**：`git push`、建 PR、合并、删 tag、发布 Release 一律交给用户。
4. **自修循环 ≤ 3 轮**。超限就报"哪几处不一致 + 试过什么 + 卡在哪"，不要无限重试。

## 步骤

### ① 取仓库实况（只读）
```bash
python scripts/sync-release.py inspect --json    # 当前版本/派生点漂移/是否已打 tag
git tag --sort=-v:refname | head -5              # 确认新版本号没复用已发布的号
gh release list --limit 5                        # 上一版实际产物
git log --oneline <上个tag>..HEAD                # 本次要对外说的内容来源
```
判据：`inspect` 的 `drift` 必须为空，否则先说明来龙去脉再继续。

### ② 定版本号
按 `docs/spec/hmp-release.md`：MAJOR.MINOR.PATCH，`versionCode = MAJOR*10000+MINOR*1000+PATCH`（脚本派生，别写）。
**规则**：进过 master 的版本号不复用。档位（是 patch 还是 minor）**要用户拍**，给出你的理由与备选。

### ③ 起草并写 `release.toml`
- 改 `version`，`date` 留发布当天填（可先空着）。
- `[[section]]` 写**用户视角**的话：不要 commit hash、不要 R 编号、不要内部待办 —— 那些进 ROADMAP。
- `[[artifact]]` 只有平台增减时才改；改了必须同步 `site/js/config.js` 的 `assets`（脚本会核对，孤儿键会提示 404）。
- **落盘前先给用户看预览**：拟改的字段 + 文案全文，得到确认再写。

### ④ 同步派生点
```bash
./gradlew syncVersion            # 或 python scripts/sync-release.py sync --write
```
它会打印改了哪些文件；ROADMAP / 站点归档里该版本的条目由它追加（已有手写条目则跳过）。

### ⑤ 点检（两层，缺一不可）
```bash
python scripts/sync-release.py sync --check                    # 一致性：抄对了没
python scripts/sync-release.py notes | tee /tmp/notes.md       # 正确性：读一遍将要公开的话
```
`--check` 绿**只证明抄对了**，不证明写得对。仍要抽查：文案里每条平台/产物断言，去实物里找反证
（`grep -n TargetFormat desktop/app/build.gradle.kts`、`gh release view <上版> --json assets`、
`ls` 本地产物）。这类反例真实存在过：ROADMAP 曾写"AppImage 打包已修好"，而实际早就改发 DEB 了。

### ⑥ 有问题分类处理
| 类别 | 例 | 动作 |
|---|---|---|
| 脚本 bug | 锚点找不到、幂等不成立 | 可自修脚本，判据不变；修完重跑 ④⑤ |
| 真源写错 | 产物名/文案与实物不符 | 改 `release.toml`，重跑 ④⑤ |
| 判据要动 | 条目超 80、锚点形状确实变了 | **停下问用户** |
| 外部状态 | tag 已存在、产物缺平台、iOS 本机验不了 | **停下交用户** |

### ⑦ 本机全量测试（CI 不跑单测，这是唯一守门人）
```bash
./gradlew-lowmem.bat preflight    # Windows；macOS/Linux/Git Bash 用 ./gradlew-lowmem
# = checkVersion + checkReleaseConsistency + testAll
```
不要用裸 `./gradlew preflight`：默认 `-Xmx4096m + parallel` 会让 daemon 被 OS 静默杀死，
**"跑不动"会被当成"跳过"且不留痕迹**（口径见 `AGENTS.md` §二）。

### ⑧ 交给用户
列出：分支名、将要发布的版本号、diff 涉及的文件、Notes 全文、仍需 macOS 验证的项（iOS 三处）。
**不要自己 push / 建 PR / 合并。**

## 发版之后（不属于本 Skill，但要知道）
合并进 master 会触发 `release.yml`：构建四端 → 齐全断言 → 用 `release.toml` 渲染 Notes → 打 tag →
发 Release → 部署站点。dry run 用 `workflow_dispatch` 勾 `dry_run`，它会一路跑到断言与 Notes 生成
再把正文打进日志 —— 验证发布链路改动只认这条路（PR 阶段对 `release.yml` 零反馈）。

某版合了但最终没出包时：`python scripts/sync-release.py mark-void <版本> --reason "…"`
会在 ROADMAP 与站点两处打标；**作废说明的那句话由人补**，脚本不替你下结论。
