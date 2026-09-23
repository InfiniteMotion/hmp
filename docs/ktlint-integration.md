# ktlint 融入开发流程 · 设计方案

> **状态**：**暂缓 —— 等 agent 分支线合并后再启动**（2026-09-15 定稿，同日经决定延后）
> **启动条件**：`feature/agent-build` 线并入 `master`、工作区无未提交改动。方案内容与分支无关，只是 §5.1 的「先收口分支」被提升为启动门槛，见 §7 的 T0。
> **前置**：本方案是 [`build-tasks-plan.md`](7_x/B%20agent-build/design/build-tasks-plan.md) 的配套项 —— 那份解决「构建任务」，这份解决「代码风格门禁」。
> **实测依据**：ktlint CLI 1.8.0 + JDK 21，全部数据来自本仓库真实运行（见 §8 与 §2–§5 各表的实测标注）。

**为什么延后**：格式化会产生 446 文件 / 16,819 行的机械 churn，而当前 `MasterAgent.kt` / `RadioSubAgent.kt` / `build.gradle.kts` 既在 ktlint 的改动清单上、又在未提交的工作区里 —— 此时格式化会让机械 diff 与功能 diff 混在一起，两边都没法 review。等分支线落定，格式化是「新基线成立前的最后一步」，一次跑完即可。**方案本身不需要修订，恢复时从 §7 的 T0 直接开始。**

## 1. 定位与边界

**要解决什么**：把「代码风格」从「靠记忆」变成「靠工具」——一处定义、三道闸门、一条命令。

**不是什么**：不是格式化教程，也不是 ktlint 功能清单。是一份**可执行的落地顺序**。

**核心约束（本项目的特殊性，决定了方案形态）**：

| 约束 | 后果 |
|---|---|
| 本机跑不动 `testAll`（内存），iOS 也编不了 | 「格式化没把代码改坏」这件事**只能靠 CI 证明** → 排序必须让 CI 能接住 |
| 本机 bash shim 损坏（管道不可用、git ref 写入被吞） | 包装脚本与 hook 需要单独实测，不能假设 POSIX 环境 |
| 单人开发，无 reviewer | 门禁的价值是「别让 master 坏掉」，不是「拦住别人」→ 越轻越好 |
| CI 现在完全不调自定义 Gradle 任务 | lint 走独立 CLI 比走 Gradle 更贴合既有习惯 |
| 三条分支装着同一批工作（见 §5） | **格式化必须在分支收口之后**，否则机械 diff 与功能 diff 混在一起 |

## 2. 单一定义源：`.editorconfig`

一个文件同时被三方消费：**ktlint CLI / Android Studio 的 Format Code / CI**。这是「融入流程」的地基——只要它存在，其余三处都只是调用方。

### 2.1 成品内容（可直接落地）

```ini
root = true

# ⚠️ 段落头必须写成 [*.{kt,kts}]（逗号后无空格）。
#    IntelliJ 的编辑器格式化会在逗号后插空格变成 [{kt, kts}]，
#    而 ktlint 用的 .editorconfig 解析库遇到列表中的空格会跳过整节 —— 规则静默失效。
#    改动本文件后请复查这一行。
[*.{kt,kts}]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 4
insert_final_newline = true

ktlint_code_style = intellij_idea
max_line_length = off

# Compose 函数按约定是 PascalCase；不豁免会产生 320+ 处误报
ktlint_function_naming_ignore_when_annotated_with = Composable,Preview

# 与本项目既有设计冲突，明确关闭（逐条理由见 §2.3）
ktlint_standard_backing-property-naming = disabled
ktlint_standard_no-wildcard-imports = disabled
ktlint_standard_filename = disabled
ktlint_standard_kdoc = disabled

# IntelliJ / Android Studio 代码风格键 —— IDE 原生读取，使 Format Code 与 ktlint 同源
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^
ij_kotlin_indent_before_arrow_on_new_line = false
ij_kotlin_line_break_after_multiline_when_entry = true
ij_kotlin_packages_to_use_import_on_demand = com.hearablemusic.player.ui.generated.resources.*,org.junit.Assert.*
```

> 上述 `ij_*` 与 `ktlint_*` 键名取自 `ktlint generateEditorConfig --code-style=intellij_idea` 的**命令输出**，不是手写。

### 2.2 为什么是 `intellij_idea` 而不是默认档

同一份代码只换档位（实测）：

| 档位 | 违规 | 说明 |
|---|---|---|
| `ktlint_official`（默认） | **37,155** | `argument-list-wrapping` 单规则 20,932 处；生成表 `PinyinLookupTable.kt` 一个文件占 19,224（52%） |
| `android_studio` | 17,803 | `max_line_length` 收到 100，且开启更多换行规则 |
| **`intellij_idea`** | **7,521** | 关闭行长硬限制，与存量代码现状最接近 |

选 `intellij_idea` 不是「更松所以好用」，而是**它把 `max_line_length` 设为 `off`** —— 仓库里有 287 行超过 140 字符，任何开启行长限制的档位都会额外制造约 3,000 处告警。若将来要开行长限制，必须配合一次真正的长行拆分，属于独立议题。

### 2.3 四条被关闭的规则：逐条理由

这四条合计 80 处，**全部是「规则与既有设计冲突」而非「代码有问题」**：

| 规则 | 处数 | 关闭理由 |
|---|---|---|
| `no-wildcard-imports` | 33 | 集中在 `com.hearablemusic.player.ui.generated.resources.*`（CMP 生成资源，逐个列举数百条无意义）与 `org.junit.Assert.*`。两者都是该场景的惯例写法 |
| `backing-property-naming` | 29 | 规则要求 `_foo` 必须是 `private`。本项目 iOS 桥接需要 `internal`（Swift 经 `IosPlaybackStateSink` 访问，见 `IosPlaybackController.kt`），改成 `private` 会**破坏 iOS** |
| `kdoc` | 12 | 全部是文件头/类头 KDoc 的空行与星号对齐偏好，仓库现有写法自洽 |
| `filename` | 6 | 规则要求「一文件一顶层声明」。`DataStore.kt` / `DataStore.{android,desktop,ios}.kt` / `StatusBars.kt` / `BuiltInApiKey.kt` 是多声明工具文件，属正常 Kotlin |

> 判断口径：**规则与设计冲突时关规则，不迁就规则** —— 与本项目文档体系「事实以代码为准」同源。

### 2.4 残留 107 处的处置（除被关规则外，还剩 27 处）

| 处置 | 处数 | 内容 |
|---|---|---|
| 删除文件 | 2 | `LyricsComponentConfigTest.kt` / `WeightManagerTest.kt` —— 内容只剩一行 `// Already covered in ...` 的**墓碑文件**，真实测试已在他处 |
| 加 `@Suppress` | 5 | `WindowHelper.kt` 的 JNA 绑定 `ReleaseCapture` / `SetWindowRgn` / `CreateRoundRectRgn` —— 必须与 Win32 符号同名，**不能改名** |
| 加括号 | 1 | `PlayContent.kt:972` 混用 `&&` / `\|\|` 未加括号（`mixed-condition-operators`）——这是真实的可读性信号 |
| 改 `const val` | 9 | `property-naming`：`val LO = 0.02f` / `val BINS = 24` / `val SOURCE_LLM = "LLM"` 等。规则只对 `const val` 放行 SCREAMING_SNAKE，改 `const val` 即可（`EMPTY_FACT_MARKERS = setOf(...)` 非编译期常量，需改小驼峰） |
| **待决策** | 10 | `PlaybackCommandPort.kt` 的 `data class SEEK_TO(...)` / `data object SHUFFLE_ON` —— 密封接口实现故意用 SCREAMING_SNAKE 表达「命令」。改名（`SeekTo`）或对该文件 `@Suppress`，二选一 |

> 汇总核账：80（关规则）+ 27（处置）= 107 ✅ 无遗漏。

## 3. 命令入口：包装脚本

**必须把坑编码进脚本，而不是让人记住。**

与项目现有的 `gradlew-lowmem.bat` / `gradlew-lowmem` 成对同构，新增 `ktlint.bat` / `ktlint`：

| 子命令 | 行为 |
|---|---|
| （默认）`check` | 全量检查，**内置 `!**/build/**` 与 `!**/*.gradle.kts`** |
| `format` | 同上 + `-F` |
| `staged` | 仅检查暂存区 `.kt`（供 pre-commit hook 调用） |

脚本职责：
1. **jar 分发**：从 Maven Central 下载到 gitignore 的目录（如 `tools/ktlint/`），**校验 SHA256**，版本号与哈希写在脚本顶部常量里；已存在且校验通过则跳过。
2. **模式集中**：`!**/build/**` 这类排除只写一处。**这条是硬需求** —— 忘了排除 `build/` 会把 KSP 生成代码一起 lint，实测虚增约 2.5 万处（`indent` 27,705 / `backing-property-naming` 17,333），看起来像灾难，其实全是生成代码。
3. **`.kts` 排除（第一阶段）**：`!**/*.gradle.kts`。理由见 §4.4。

> ⚠️ **不要用 `--code-style` CLI 参数**（已废弃）；也**不要依赖 `--editorconfig=<path>`** —— 它的语义是「仅当路径上没有 `.editorconfig` 指定该属性时才生效」，把 `.editorconfig` 放仓库根即可，不需要这个参数。

## 4. 三道闸门

### 4.1 编辑时（IDE）

`.editorconfig` 里的 `ij_kotlin_*` 键是 **Android Studio 原生读取**的代码风格配置 —— 贴进去后，IDE 的 Reformat Code 与 ktlint 同源，**不需要装任何插件**。

若要「保存即合规 + 违规实时标红」，可另装 IDE 插件 `KtLint`（Plugin ID `com.nbadal.ktlint`，已由原非官方插件转为官方，默认内置 ktlint 1.8.0，与本方案 CLI 版本一致；支持 `distract free` 模式 + format on save，配置同样读 `.editorconfig`）。

**建议**：至少做前半段（`ij_*` 键）；插件按需，它解决的是「编辑体验」而非「正确性」。

### 4.2 提交时（pre-commit hook，可选）

**先说结论：可选，且必须手写，不能用 ktlint 自带的安装器。**

- `ktlint installGitPreCommitHook` 在本机**直接段错误**（Windows 退出码 `3221225477` = `0xC0000005` 访问冲突，hook 文件未生成）。`installGitPrePushHook` 同样崩。
- 因此 hook 要手写：调用包装脚本的 `staged` 子命令即可。
- **安装顺序硬约束**：必须**先完成全仓格式化再装 hook**，否则第一次提交就会被约 7,500 处存量告警拦住。
- Hook 只 check、**不 `-F`** —— 自动改写读者的暂存内容会制造难以理解的提交。
- 若已启用 IDE 的 format on save（§4.1），hook 基本是冗余兜底，可以不做。

### 4.3 CI（PR / 推送门禁）

放进 `ci.yml` 的 `lint` job（与 CI/CD 讨论里的 `testAll` + 版本号一致性检查同属一个工作流）：

```
ubuntu + JDK 21
→ checkout
→ 缓存 ktlint jar（key = 版本号 + SHA256）
→ 运行包装脚本 check
```

| 决策 | 取值 | 理由 |
|---|---|---|
| 全量 or 增量 | **全量** | 只需 12 秒（本地实测），增量逻辑换不来收益 |
| 是否 `-F` | **否** | CI 自动改代码会产生无人负责的提交；CI 只做判定 |
| 是否必填检查 | **是** | 否则等于装饰 |
| `timeout-minutes` | 建议 10 | 与 `release.yml` 的既有风格一致 |
| `concurrency` | `cancel-in-progress: true` | 与 `release.yml` 的串行策略相反 —— 这是校验不是发布 |

**`release.yml` 不动**：master 上的代码必经 PR 校验，发布从 master 出，所以发布期再做一次 lint 是冗余的。保持那条唯一可用的发布流水线稳定。

### 4.4 第一阶段 lint `.kts` 吗？——不 lint

`.kts` 实测只有 8 个文件 / 53 处，但代价不对等：

- `desktop/app/build.gradle.kts` 有 6 处 `no-multi-spaces` —— 那是**刻意对齐**，格式化会抹平；
- 根 `build.gradle.kts` 有 21 处（含 9 处 `string-template`、7 处 `argument-list-wrapping`），而这份 458 行文件承载了大量讲解性注释与手工分节，churn 纯属成本。

留作后续可选项（去掉排除模式即可）。若要开，先单独跑一次 `check` 看完整清单再决定。

## 5. 存量收敛：一次性格式化的顺序

### 5.1 前置：先收口分支（**不可跳过**）

当前 git 状态：

| 分支 / 位置 | 指向 | 与 master 差 |
|---|---|---|
| `master` | `3abd5ab` | — |
| `feature/agent-build` | `a9514ed` | +21 |
| `backup/agent-build-pre-squash` | `f3a4f6a` | +20 |
| `refactor/agent-8`（当前分支） | `1dba707` | +8 |
| **工作区** | — | **42 项未提交**（21 个改动文件 + 重命名 + 删除 + 4 项未跟踪，含整个 `docs/7_x/B agent-build/`） |

三条分支装着**同一批工作**，且工作区还有 42 项挂在那里 —— 其中包括 `MasterAgent.kt`、`RadioSubAgent.kt`、`build.gradle.kts`，**这些文件恰好都在 ktlint 的改动清单里**。

此时跑 `ktlint -F` 的后果：机械改动与功能改动混进同一个 diff，两边都无法 review，`git blame` 也失去意义。

**因此顺序是：先把分支与工作区收口（提交掉 42 项、决定保留哪条线）→ 再格式化。**

好消息是**格式化的成本极低且幂等**（26 秒、结果确定），所以它永远应该是「某条线成为新基线之前的最后一步」，而不是夹在中间。若分支决策反复，重跑一次即可，不必为它保守。

### 5.2 提交切分（4 笔，各自可独立 review）

| # | 提交 | 内容 | 可否独立 review |
|---|---|---|---|
| 1 | `build: 引入 ktlint 代码风格配置` | `.editorconfig` + `.gitattributes` | ✅ 纯配置 |
| 2 | `style: 按 ktlint 规则全量格式化` | `-F` 结果 + §2.4 的 27 处处置 | ⚠️ 机械 diff，但 27 处需人看 |
| 3 | `build: ktlint 包装脚本` | `ktlint.bat` / `ktlint` + `.gitignore` 条目 | ✅ |
| 4 | `ci: 新增 PR 校验流水线（lint）` | `ci.yml` + `DEVELOP.md` 同步 | ✅ |

### 5.3 `git attributes` 与行尾（**新增发现，必须一并处理**）

实测：`core.autocrlf=true`，但工作区**混着 CRLF 与 LF**（`RadioSession.kt` 626 个 CRLF，`PlayContent.kt` 0 个），而 `end_of_line = lf` 会让 ktlint **把 CRLF 文件整体改写成 LF**。

后果量化：格式化 diff 共 16,815 行，其中**纯行尾 224 行（1.3%）**；但有 **35 个文件是「零内容改动、纯行尾变化」** —— 它们会以「全文件重写」的形态出现在提交里。

处置：**与 `.editorconfig` 同一笔提交加 `.gitattributes`**，把行尾策略从「本机 git 配置」升级为「仓库共享约定」：

```
* text=auto eol=lf
*.bat text eol=crlf
*.png binary
*.jpg binary
*.jks binary
```

> 不加也可以跑，但行尾将永远依赖每个人的 `core.autocrlf` —— CI（Linux）与本地（Windows）的不一致会反复以「幽灵改动」的形式出现。

### 5.4 `git blame` 保护

把提交 2 的 SHA 写进仓库根 `.git-blame-ignore-revs`（GitHub 自动识别），本地加 `git config blame.ignoreRevsFile .git-blame-ignore-revs`。这是让 446 文件的机械提交不毁掉追溯能力的标准做法。

## 6. 明确的取舍（不做的事）

| 不做 | 理由 |
|---|---|
| **ktlint 不进 Gradle 构建图**（不上 `org.jlleitschuh.gradle.ktlint`） | 插件本身没问题（14.2.0，13.1.0 起支持 Gradle 9，14.0.1 验证到 9.1），但：给低内存 Gradle 增配置期负担、**7 个模块产出 7 份 baseline**、默认捆的 ktlint 是 1.5（落后 CLI）、且本项目 CI 本来就不调自定义 Gradle 任务 |
| **不用 baseline** | 107 处一次收干净比分模块维护 7 份（或 1 份）baseline 便宜。baseline 的价值在有大量历史债 + 多人并行时 |
| **CI 里不跑 `-F`** | 见 §4.3 |
| **不引入 detekt** | 与 ktlint 职责重叠；本项目的实际痛点（缺校验）已由 lint + testAll 覆盖 |
| **不把 ktlint 挂进 `preflight`** | 会引入 Gradle → 外部脚本的耦合；lint 是独立命令，文档里并列写清即可 |
| **不做覆盖率门禁 / 不加 editorconfig-checker** | 单人项目，纯增负担 |

## 7. 实施顺序与验收

| 阶段 | 动作 | 验收 |
|---|---|---|
| **T0** | **等 agent 分支线合并进 `master`，并提交掉工作区未提交项（本方案的启动门槛）** | `git status` 干净、只剩 `master` 一条活跃开发线 |
| T1 | `.editorconfig` + `.gitattributes` | 包装脚本 `check` 输出 107 处（而非 7,521 或 37,155） |
| T2 | `ktlint -F` + 27 处处置 | `check` **0 处**；`git diff --stat` 约 446 文件 |
| T3 | `.git-blame-ignore-revs` | `git blame` 不指向机械提交 |
| T4 | 包装脚本 | 新克隆一次即跑通（含 jar 下载 + SHA 校验分支） |
| T5 | `ci.yml` 的 `lint` job | PR 上绿；人为改一处缩进 → 变红 |
| T6 | `DEVELOP.md` 同步 | 删除不存在的 `ktlint.gradle` 表述，补「怎么跑 lint」 |
| **T7** | **CI 跑 `testAll`** | **格式化未破坏编译/测试的最终证明**（本机无法前置，见 §1） |

> **T7 是本方案唯一未闭环的风险点**：格式化基于 PSI 改写，风险低但非零；而本机既跑不动 `testAll` 也编不了 iOS，所以**必须让 CI 的测试 job 在格式化提交上先跑一次**再继续后续工作。

## 8. 附录：本轮新踩的坑（可复用）

1. **生成代码必须显式排除**：ktlint 的默认 glob **不排除 `build/`**。实测 `**/*.kt` 扫出 612 项，其中 114 项是 `build/` 下 KSP 产物；正确写法 `**/*.kt !**/build/**` → 498 项、build 归零。**写死模块 glob（`shared/src/**/*.kt`）拦不住**，只有 `!` 否定模式有效。
2. **`.editorconfig` 段头不能有空格**：`[*.{kt,kts}]` 被 IDE 改成 `[{kt, kts}]` 后，ktlint 的解析库会跳过整节 → 全部规则静默失效。这类失败**没有任何报错**。
3. **`ktlint installGitPreCommitHook` 在 Windows 上段错误**（`0xC0000005`，hook 未生成）。同类命令 `installGitPrePushHook` 一样。→ hook 必须手写。
4. **`generateEditorConfig` 需要显式 `--code-style`**，否则报 `missing option --code-style`。
5. **JSON reporter 的 stdout 混有日志**：WARN 行会出现在 JSON 之前，直接 `JSON.parse` 会炸。需按行定位首个 `[` 行与末个 `]` 行再切片。
6. **本机 bash 管道不可用**：`| grep` 会让 stdout 整体为空（`grep: command not found`），排查时极易误判为「命令无输出」。一律用纯 node 脚本 + Write 工具落盘。
