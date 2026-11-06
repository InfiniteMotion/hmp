# 7.4 期 · 正式规划：ktlint 代码风格门禁

> **范围决议（用户，2026-11-05）**：7.4 期**只做 ktlint**。UDF 单向数据流改造、设计校准、CI 编译测试（X-01）与 7.3 其余残余均不在本阶段施工，继续留在 [residuals-7.3.md](residuals-7.3.md) 台账。
> **后续阶段（用户，2026-11-05）**：**UDF 单向数据流改造当时定于 7.4.1 进入**，范围决议为**应用所有页面**；**2026-11-06 改判**：因体量升为独立 minor 期 **7.5.0**，正式规划见 [7.5 期规划](../7_5/plan.md)，活跃线迁至 `docs/7_5/`；设计校准、X-01 与其余残余继续留在台账。
> **施工依据**：[`../ktlint-integration.md`](../ktlint-integration.md)（ktlint CLI 1.8.0 + JDK 21 全仓实测，261 行）。本文件只记 **7.4 的改判与顺序**，不复制方案正文；两者冲突处以本文件为准。
> **权威口径**：`AGENTS.md` → `ROADMAP.md` → 本文件。归档 `docs/archive/**` 只进不改。
> **建账**：2026-11-05。分支 `feature/architecture-hardening`；工作区干净。
> **状态（2026-11-05）：7.4 已收口。** 配置 / 双端包装脚本 / CI lint 闸门 / T2 全量格式化 / 28 处人工处置全部落地，`./ktlint check` = 0 处（POSIX 与 Windows 双端一致），格式化后 `compileAll testCore testUi` = BUILD SUCCESSFUL。两笔提交：`0d88404`（`style`，整个 ktlint 合为一笔的机械提交）+ `ac4165e`（`chore`，`.git-blame-ignore-revs`）；本地 `blame.ignoreRevsFile` 已配置并验证生效。**仅剩**：首个 PR 验证 lint job 与 Verdict 判定（需人工 push / 开 PR，`AGENTS.md` 禁止工具代做）。

---

## 一、范围与完成态

唯一交付：**仓库从「无机械化风格闸门」变成「一处定义、两道闸门、全仓 check 为 0」**。

| 交付物 | 状态 |
|---|---|
| `.editorconfig`（ktlint / IDE / CI 单一定义源） | 待建 |
| `.gitattributes` 扩 `.kt` 行尾约定 | 待改（文件已存在） |
| `ktlint` / `ktlint.bat` 包装脚本 + `tools/ktlint/` 忽略项 | 待建 |
| `pr-check.yml` 的 `lint` job（并入 Verdict） | 待建 |
| 全仓格式化 + 27 处人工处置 | 待建，**本阶段主交付** |
| `.git-blame-ignore-revs` | 待建 |
| 文档口径同步（本索引 / ROADMAP / TODO / DEVELOP / AGENTS） | 随本文件落地 |

**完成态判据**：`ktlint check` 全仓 0 处；PR 上人为改一处缩进 → CI 红且 Verdict 不允许合入；格式化未破坏编译与测试（§四）。

---

## 二、范围收窄后的两处改判

### 2.1 T2 全量格式化：从「最危险」变为「必须做」

7.3 台账 §一 的排序约束是「ktlint 全仓 churn（446 文件 / 1.6 万行）不得与功能性 diff 混批；先格式化还是先 UDF，7.4 正式规划定」。**这条约束在「7.4 只做 ktlint」下自动消解**——本阶段不存在功能性 diff，混批风险为零，排序问题不复存在。

反过来，若沿用 TODO R50 的旧恢复入口「跳过 T2、只做增量守门」，7.4 将只交付配置与脚本：存量 107 处继续挂账、全量 `check` 仍红，门禁管不住既有代码，「引入 ktlint」没有完成态，方案 261 行里最大的一块（存量收敛）永远悬着。

> **裁定**：T2 一次跑完，作为 7.4 的收口动作。顺序为「配置与闸门 → 全量格式化 → 挂 hook / 收口验收」，不必再等 UDF。

### 2.2 T7：不依赖 CI，改由本机闭环

方案 §1 把「本机跑不动 `testAll`（内存）」列为硬约束，§7 据此把「CI 跑 `testAll`」定为格式化正确性的唯一证明（T7），并成为全案唯一未闭环的风险点。**该前提已过期**：`gradlew-smart.bat` 的 `rescue` 档（1024m / `--no-daemon` / workers=1 / Kotlin in-process）是 `AGENTS.md` §二 记录的「实测唯一能连测试 JVM 一起跑通」的配置。

因此 T7 重写为**本机三验**，X-01 不必解禁、不必进 7.4：

1. **格式化前**：`gradlew-smart.bat compileAll testAll`。**2026-11-05 实测结论（降级已生效）**：`compileAll` 绿；`testAll` 在 `:desktop:core-player:desktopTest` 挂死 —— 日志 13 分钟无新输出、java 进程 CPU 空转，17 分钟后手动终止，与历史 R31「跑测挂死」同型。多数子测试还是 `UP-TO-DATE`（沿用旧报告），本就证明力有限。故按预留降级：**基线取 `compileAll` 绿 + `testCore` / `testUi` 分层**，不再等 `testAll`。（测试报告落在被依赖的底层任务目录 `<模块>/build/reports/tests/<任务名>/`，聚合任务自身不出报告。）
2. **格式化后、提交前**：`compileAll`（`.kt` 全变，必然真编译、不会 `UP-TO-DATE`）+ `testCore` / `testUi` 再跑一次，结果一致；**不再挂 `testAll`**。
3. **`git diff -w` 复核**：**2026-11-05 实测订正** —— `-w` 只压行内空白，压不住 ktlint 的跨行重组（trailing comma、参数换行、statement wrapping、import 重排都改变了行的构成）。实测全量 diff 为 555 文件 / 12,306 插入 + 10,510 删除，`-w` 之后仍有 520 文件 / 8,287 + 6,491。所以它**不是**语义等价性的证明，只作辅助观察；真正的证明是格式化后的 `compileAll`（源集全变、必然真编译）+ 分层测试绿。

**剩余缺口（如实登记）**：本机 `testAll` 不覆盖 iOS 源集（`compileAll` 只挂 desktop/android，iOS 编译需 macOS）。ktlint 基于 PSI 改写、风险低但非零；iOS 半边的最终签收随 7.4 之后的实机池（台账 §六）。

> **2026-11-05 后续订正（本文件为 7.4 收口时的历史施工记录，此处不重写历史）**：上文与 §四 验收表、§六 风险表里所有「`testAll` 实测挂死」「不再挂 `testAll`」「降级为 `testCore` / `testUi`」均为**当时的权宜口径**。同日稍后已定位根因并修复 —— `DesktopQueueDbReflowTest` 的 6 处 `advanceUntilIdle()` 被前面测试类遗留的无限 `delay(100)` 协程拖进无限虚拟时间（R31 结案，见 `TODO.md`）；改 `runCurrent()` 后 `compileAll testAll` = `BUILD SUCCESSFUL in 7s`，`preflight` 恢复可用。**本阶段结论不受影响**（当时的分层验证确已跑绿，格式化未破坏代码）；后续阶段的验收链以 [7.5 期规划](../7_5/plan.md) §五 为准。

---

## 三、对 `ktlint-integration.md` 的逐条改判

| 方案原文 | 7.4 口径 | 依据 |
|---|---|---|
| §1 / §6「CI 完全不调自定义 Gradle 任务」 | 前提失真。理由换成「低内存 Gradle 不增配置期负担 / 7 模块 7 份 baseline / 插件捆的 ktlint 1.5 落后」；**结论（独立 CLI）不变** | `review-7.3-architecture.md` §四；`pr-check.yml` 的 version job 实调两个自定义任务 |
| §4.3「放进 `ci.yml` 的 `lint` job」 | 改落 `pr-check.yml` 新增 job，并**加进 `preflight`（Verdict）的 `needs` 与汇总表** | 仓库无 `ci.yml`；不进 Verdict 则门禁拦不住合入 |
| 同上：触发范围 | 与 `release-info` 一致，**所有非 draft PR**；不能只跟 `release/*` | TODO §六 决议① 第 4 点：feature PR 当前零门禁 |
| §5.3「新增 `.gitattributes`」 | 文件已存在（只覆盖 `.github/**` / `scripts/**` / `*.md`），改为**扩一行 `*.kt text eol=lf`**，沿用白名单风格；别照抄 `* text=auto eol=lf`（会连 `.bat` 与二进制一起改） | 本文件实测 |
| §7 T0「agent 分支线合并」 | 三条 agent 分支已消失，只剩 `feature/architecture-hardening` + `master`；**只剩 `residuals-7.3.md` 一笔未提交** | `git branch -a` / `git status` |
| §7 T1 验收「check 输出 107 处」 | **表述有误，已按实测订正**：107 是 **T2 格式化之后**的残留，不是格式化前的 check。2026-11-05 本机实测格式化前 `check` = **9,183 处**（intellij_idea 档、695 个 `.kt`），与方案当年 7,521 同量级（默认档 37,155） | 本机实跑 `./ktlint check`；`AGENTS.md` §一.3：计数会过期 |
| §5「446 文件 / 16,819 行」 | **施工前重测**。7.3 收口后又补了测试基建与设计体系落地，规模已变 | 同上 |
| §7 T7「CI 跑 `testAll`」 | 改为本机三验，见 §二.2 | 同上 |
| §1「本机 bash shim 损坏」 | 包装脚本照旧 Windows / POSIX 双份；`--code-style` 已废弃、`--editorconfig` 参数语义有坑两处仍按方案执行 | 方案 §3 / §8 |

**不改的**：`.editorconfig` 成品内容（§2.1）、四条关闭规则（§2.3）、第一阶段排除 `*.gradle.kts`（§4.4）、不进 Gradle 构建图 / 不用 baseline / 不引 detekt / CI 不跑 `-F` / 不挂 Gradle `preflight`（§6）。

---

## 四、验收

| 关口 | 判据 |
|---|---|
| 配置生效 | `check` = **9,183 处**（2026-11-05 本机实测，格式化前）；规则分布无 `max-line-length`、四条关闭规则零命中、`argument-list-wrapping` 仅 429（默认档 20,932）；`build/` 与 `*.gradle.kts` 命中为 0。否则说明 `.editorconfig` 段头被 IDE 改坏、整节静默失效 |
| 格式化收敛 | `check` = **0 处**；`git diff -w` 的非空白改动可逐条解释 |
| 未破坏代码 | 格式化前 `compileAll` 绿；格式化后 `compileAll` + `testCore` / `testUi` 绿（降级口径见 §二.2）。`testAll` 实测挂死，不再作为判据 |
| 闸门有效 | PR 上人为改一处缩进 → `lint` job 红、Verdict 判「不允许合入」 |
| 追溯保护 | `.git-blame-ignore-revs` 指向格式化提交 SHA，`git blame` 不落在机械提交上 |

**2026-11-05 终验结果**：配置生效（9,183 处）、格式化收敛（T2 后 `check` = 0 处，POSIX 与 Windows 双端一致）、未破坏代码（`compileAll testCore testUi` → `BUILD SUCCESSFUL in 5m 47s`，144 tasks / 49 executed）、追溯保护（`.git-blame-ignore-revs` 指向 `0d88404`，`git blame` 实测不再落到格式化提交）**均已完成**。全量机械 diff 为 555 文件 / 12,306 插入 + 10,510 删除。**唯一未闭环**：闸门有效，待首个 PR 的 CI 首跑。

**收尾状态**：两笔提交 `0d88404` + `ac4165e` 已完成，工作区干净。剩余事项：① 首个 PR 上验证 `lint` job 与 Verdict 判定（需人工 push / 开 PR，`AGENTS.md` 禁止工具代做）；② 可选的手写 pre-commit hook **决定不装** —— `./ktlint check` 与 CI 闸门已覆盖，方案 §4.2 本就把 hook 定位为冗余兜底，且 Windows 上 ktlint 自带安装器会段错误。

---

## 五、施工顺序（提交级）

> **2026-11-05 用户决议（实际执行口径）**：不按下列 6 笔切分，**整个 ktlint 合为一笔格式化提交**；`.git-blame-ignore-revs` 因需引用主提交 SHA，另作第二笔 `chore` 提交。下表保留为原规划留档。

| # | 提交 | 内容 | 备注 |
|---|---|---|---|
| 0 | — | 把 `docs/7_4/residuals-7.3.md` 这笔暂存提交掉 | T0 收口，非本规划产物；**不要由工具代提交** |
| 1 | `build: 引入 ktlint 代码风格配置` | `.editorconfig` + `.gitattributes` 扩行 | 纯配置，规则集待 §六.1 确认 |
| 2 | `style: 按 ktlint 规则全量格式化` | `-F` 结果 + 27 处人工处置 + R21（`ToolNames.kt` 注释「共 29 个」）顺手修 | 机械 diff 单独一笔；前后各跑一次 §二.2 的三验 |
| 3 | `build: ktlint 包装脚本` | `ktlint.bat` / `ktlint` + `.gitignore` 的 `tools/ktlint/` | 内置 `!**/build/**`、`!**/*.gradle.kts`、jar SHA256 校验 |
| 4 | `ci: PR 校验新增 lint job` | `pr-check.yml` + Verdict 汇总表 | 所有非 draft PR、JDK 21、只 check 不 `-F` |
| 5 | `chore: blame 保护与文档同步` | `.git-blame-ignore-revs` + `DEVELOP.md` 改成「已有门禁，怎么跑」 | |
| 6 | 可选 | 手写 pre-commit hook 调 `staged` | **必须在提交 2 之后**装；Windows 上 ktlint 自带安装器段错误（§8.3） |

> 提交 2 与 3/4 的顺序可调，但**格式化必须在 hook 之前，且不与任何功能改动同批**。

---

## 六、决策记录（2026-11-05 用户已确认）

1. **规则集**：确认**照单全收** —— `intellij_idea` + 关 4 条、不引 detekt。实测生效（§四），`.editorconfig` 未再改动。
2. **`SEEK_TO` / `SHUFFLE_ON`**：确认采用 `@Suppress` + 注释。已在 `PlaybackCommandPort.kt` 加 `@file:Suppress("ktlint:standard:class-naming")` 与理由，那 5 个引用文件一字未动。

### T2 后剩余 28 处的实际处置（与方案 §2.4 有出入，以本次实测为准）

| 类别 | 处数 | 处置 |
|---|---|---|
| `class-naming` | 10 | `PlaybackCommandPort.kt` 文件级 `@file:Suppress`（用户决议） |
| `function-naming` | 3 + 7 | `WindowHelper.kt` 的 JNA 绑定 3 处、`PlaybackObservationBusTest.kt` / `UserMemoryIntegrationTest.kt` 的描述性测试名 7 处，各加文件级 `@file:Suppress` + 理由注释；**未放宽 `.editorconfig` 规则集** |
| `property-naming` | 5 | `FakeMusicRepository` 的 `SOURCE_LLM/AGENT`、`DynamicBackground` 的 `LO/HI`、`ThemeViewModel` 的 `BINS` 都是**局部变量或实例成员、不能 `const`**，按方案「非编译期常量改小驼峰」口径改名（引用均在同一文件内） |
| `annotation-spacing` | 1 | `PlaylistItem.kt`：KDoc 原夹在 `@Entity` 与 `data class` 之间，移到 `@Entity` 之前 |
| `import-ordering` | 1 | `PlaybackEnqueueAndBudget.kt`：一个被注释隔开的迟到 import 合并进顶部并按字典序重排 |
| `no-empty-file` | 1 | 删除墓碑文件 `LyricsComponentConfigTest.kt`（只剩一行「Already covered in ...」注释）；方案点名的 `WeightManagerTest.kt` 已不存在 |

> 方案 §2.4 的「27 处」与「改 `const val` 9 处」是旧工作树坐标；本次实测 28 处，且那些常量并非编译期常量，故按同一口径改小驼峰。

---

## 七、明确不做（范围外，留在台账）

UDF 单向数据流改造（R15 / R48）、设计校准、X-01 CI 编译测试、实机池、其余 7.3 残余（台账 §三–§六）全部**不进 7.4 施工**，仍由 [residuals-7.3.md](residuals-7.3.md) 持有。其中 **UDF 现定于 7.5.0 进入**（原 7.4.1，11-06 因体量改判），不属 7.4.0 范围；其范围后由用户决议为**应用所有页面**，详见 [7.5 期规划](../7_5/plan.md) §一。

与 ktlint 的边界：

- R21 注释口径漂移已在格式化批顺手修（`ToolNames.kt` 「共 29 个」→ 30）；
- CI 里只加 `lint` job（跑 ktlint CLI），**不加 Gradle 编译 / 测试 job** —— 那是 X-01 的领地；
- **注释清理不做**（2026-11-05 用户决议）：讨论过 AI 遗留垃圾注释的清理与「全删注释」的影响后，决定**保持现状** —— 不清理存量、不加注释门禁、不改注释规约。盘点结论留档：全仓 14,203 行注释（12.2%，含 KDoc 10,280 行，是「为什么」的主要载体）；7 处 `// design-token-exception` 是 `checkDesignTokens` 的例外协议，删注释会让 `preflight` 变红，因此全删不可行。

---

## 八、风险登记

| 风险 | 处置 |
|---|---|
| `.editorconfig` 段头 `[*.{kt,kts}]` 被 IDE 改成 `[{kt, kts}]` → 整节规则**静默失效、无任何报错** | 提交 1 后立刻用 `check` 输出量级反证；每次改该文件后复查这一行 |
| 漏 `!**/build/**` → KSP 生成码虚增约 2.5 万处告警 | 排除模式只写在包装脚本一处 |
| 本机 JDK 17（方案实测与 CI 均为 21） | 施工前用 17 与 21 各跑一次 `check` 对比；CI 固定 `setup-java@v4` 的 21 |
| `end_of_line = lf` 把 CRLF 文件整体改写成 LF（方案实测 35 个文件「零内容改动、纯行尾变化」） | `.gitattributes` 与 `.editorconfig` 同批；`git diff -w` 复核 |
| 本机 `testAll` 不覆盖 iOS 源集 | 见 §二.2 剩余缺口，随实机池签收 |
| **Windows 批处理吞掉 `!` 否定模式** | 2026-11-05 实测：`ktlint.bat check` 曾输出 96,822 行（`!**/build/**` 与 `!**/*.gradle.kts` 变成显式包含，KSP 生成码与所有 `.kts` 被扫）。已改为全局 `DisableDelayedExpansion`、仅 `:staged` 局部启用；修复后与 POSIX 版违规集合逐行一致（9,183 处）。改脚本后必须复跑这个比对 |
| 方案里的所有计数与行号 | 施工前按符号名 grep 重测，见 §三 |
