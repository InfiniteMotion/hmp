# 7.6 期 · P2 Agent 运行时状态所有权改造台账

> **用途**：逐债判定、施工记录与口径订正。规划本体见 [plan.md](plan.md)，本文件不重复规划论证。
> **口径**：日期 + 工作树实测；行号会过期，动手前按符号名 grep。**未验证的项如实标注，不把「改完」当「验过」**。
> **状态**：2026-11-11 起施工中，**全部改动仍在工作树、未提交**；HEAD 为 `dd078a2`。

---

## 一、工作树盘点（建台账前实测）

| 项 | 实测 |
|---|---|
| 分支 / HEAD | `feature/architecture-hardening` / `dd078a2` |
| 已跟踪改动 | 44 个文件，+454 / −624（`git diff --stat`，未含已暂存删除） |
| 已暂存删除 | 4 个文件 / −338 行（`CommandLexicon.kt` + test、`ChatIntentRules.kt` + test） |
| 未跟踪新文件 | 5 个：`agent/health/AgentHealth.kt` + test、`agent/card/HelloCardCodec.kt`、`agent/card/HelloCardRecovery.kt`、`agent/tool/CapabilityControlTools.kt` |
| 验证 | **未跑** `ktlint` / `compileAll` / `testCore` / `testAll`；iOS 未编译 |

> 计数会随工作树变化；提交前以 `git status` / `git diff --stat` 重测。

---

## 二、已落改动

### 2.1 R5 步数上限删除（结案）

- **决议**：用户 2026-11-11「不要步数上限，有 token 上限就行」。
- **动作**：删除 `EngineDefaults.STEP_BUDGET`、`RuntimeParams.stepBudget` 与 master 解析分支、`MasterAgent` 构造参数、`ReActLoop` 的 `while (steps < budget)` 与熔断分支、`TerminationReason.STEP_BUDGET_EXHAUSTED`、`AgentConfigUiState.stepBudget` / `onStepBudgetChange` / 设置滑块、14 语言 `agent_cfg_step_budget*`、`SharedModules` 传参、`ReActLoopTest` 步数用例、`EngineDefaultsTest` 断言；`stepsUsed` 仅作观测值。
- **现状**：`ReActLoop.run()` 为 `while (true)`，退出条件为模型最终回答 / LLM 失败 / 配额前置拒绝 / StopSignal 软停止（`ReActLoop.kt:26-32`、`:107`）。
- **已知风险**：PAID 与未开启自定义限额的 CUSTOM 模式下配额永不耗尽，模型持续返回工具调用时循环不会自行终止。

### 2.2 R3 前半：取消收尾与配置落盘

- **动作**：`ReActLoop.run()` 用 `try/finally` 包住循环（`ReActLoop.kt:106`、`:196-200`），`finally` 发 `idle`；`alwaysAllow` 改为勾选即回调 `onAlwaysAllowChanged`（`:188-191`），上层立即落盘；删除 `onSessionComplete` 与循环末尾持久化。
- **判据**：`ReActLoopTest` 新增「勾了总是允许 → 立即触发保存回调并写入白名单」「循环中途中断 → 保存已发生且思考态仍复位」两条（`:111`、`:129`）；现 8 条用例。
- **仍缺**：tool 轮次跨轮持久化（见 §四）。

### 2.3 对策调整 A：对话只保留 LLM 路径 + 能力控制工具

- **背景**：原实现有两层本地捷径 —— `CommandLexicon` 高频指令零 token 直执行（`ChatViewModel.launchDirect`）与 `MasterAgent.builtinIntents` 关键词短路（`ChatIntentRules` + `BuiltinIntentId` / `intentHandled`）。两层都绕过 LLM，能力启停依赖关键词表，改词容易静默失效。
- **动作**：
  - 删除 `domain/agent/funnel/CommandLexicon.kt`、`runtime/ChatIntentRules.kt` 及两份测试；`AgentResult.intentHandled` / `BuiltinIntentId` 删除；`MasterAgent` 的 `builtinIntents`、`builtinIntent()`、`answered()`、`isRadioActive()`、`enrichStatusSummary()` 删除。
  - 新增 `tool/CapabilityControlTools.kt`：8 个 LLM 工具 `radio_start` / `radio_stop` / `radio_resume` / `enrich_start` / `enrich_stop` / `enrich_pause` / `enrich_resume` / `enrich_rescan`，由 `MasterAgent.initialize()` 经 `bindCapabilityControlTools(...)` 绑定（回调转调既有 `startRadio` / `startEnrich` 等方法）；`ToolRegistry.registerIfAbsent()` 支持幂等注册。
  - `ChatViewModel.send()` 只走 `launchRun()`（`ChatViewModel.kt:265-267`），构造参数去掉 `PlaybackCommandPort`（DI 6→5）；`SearchViewModel.isIntent` 改为「输入非空即提供 AI 入口」；`ChatAgentGateway` 从工具回执识别 `radio_start` 成功并补 `RadioStarted` 事件，其余工具逐条 `ToolExecuted`。
  - `playback_control` 描述补充「别放了 / 停一下用 pause，只有明确关电台才用 radio_stop」。
- **影响**：所有对话动作（包括播放控制）都经 LLM，消耗 token；未配置 AI / 额度耗尽时对话返回统一健康门槛的对应文案（见 §2.8），不再有零 token 直执行后路。
- **判据缺口**：当前无「用户说停一下 / 关电台」的端到端用例；工具选择依赖模型，原有词表用例已随词表删除。

### 2.4 对策调整 B：AgentHealth 健康门控

- **背景**：`initialize()` 原先硬编码「按覆盖率建富化 / 无条件启 Hello / 冷启画像」，没配 AI 时也会拉起后台服务。
- **动作**：
  - 新增 `health/AgentHealth.kt`：`AgentHealthChecker` 纯查询（LLM 配置 / 配额、曲库是否为空、记忆开关），依赖全可空；`AgentHealthGate` 用 `Mutex` 串行化，`@Volatile last` 保存最近结果。
  - `MasterAgent.initialize()` 末尾 `healthGate.evaluate()`（`:639`），由 `applyHealth()` 决定 Hello / 富化 / 画像是否启动（`:659`）；`onLibraryMutated()`（`:507`）与 `updateAiConfig()`（`:1315`）触发重检。
  - 四个入口（`startRadio` / `startHello` / `startEnrich` / `handleUserMessage`）先过门槛；**2026-11-13 起读的是同一份 `AgentHealth`**（见 §2.8），不再是另写的 `llmReady()`。
- **影响**：无 AI 配置 / 配额耗尽 / 空库时后台 SubAgent 不启动；配置或曲库变化后自动补启。`settingsRepo` 为 null 的测试 / 旧装配按放行处理。
- **判据**：`AgentHealthCheckerTest` 先建 5 条（免费模式空库、自定义未配置、就绪、配额耗尽、记忆开关），§2.8 再补 3 条（空库双否、富化开关、依赖未接线放行）。

### 2.5 对策调整 C：Hello 卡片缓存回退

- **背景**：Hello 改为「无可用 LLM 不启动」后，首页卡片不能空着。
- **动作**：
  - 新增 `card/HelloCardCodec.kt`（从 `HelloSubAgent` 私有 JSON 抽出的统一解码）与 `card/HelloCardRecovery.kt`（`HelloCardCache.toSlideCard()`，仅恢复 RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY）。
  - `HelloCardsViewModel` 新增可空 `HelloCardCacheDao`；`init` 读取今日缓存（`getTodayAllEntities(now-24h)` + `generatedForDate == today`），与 live `helloCards` 合并：live 优先、按类型补位（`HelloCardsViewModel.kt:71`、`:93-110`）。DI 用 `getOrNull()`。
  - `HelloSubAgent` 的 `enrichConfig` 改名 `helloConfig`，`updateAiConfig` 形参同步 —— 修正「Hello 用 enrich 配置」的命名误导，实际来源仍是 per-agent `hello` 配置。
- **影响**：首启空库且无 AI 时无卡可显示（缓存也空），这是预期；缓存读取失败静默回落空列表。

### 2.6 口径修正（随本批代码一起）

- `AgentTask.budgetUsed` 注释改为「实际消耗步数，仅观测」；`AgentPolicy.runtimeParams` 注释去掉 `stepBudget` 示例。
- `Capability.kt` / `PlaybackCommandPort.kt` 注释去掉 `builtinIntent` / `CommandLexicon` 引用。
- `docs/7_5/progress.md` 的 `SearchUiState.isIntent` 条目补注「词表已删，改为输入非空即提供 AI 入口」。
- `TODO.md` R5 结案、R22 去掉「内建意图路由」、R6 判据改判、`docs/7_4/residuals-7.3.md` R5 结案。

### 2.7 Enrich 状态收口样板（2026-11-11，P2 子 Agent 第一刀）

- **目标**：把 Enrich 做成三个子 Agent 的状态所有权样板，验证「单一快照 + finally 收尾 + 暂停可见」后再套 Hello / Radio。
- **动作**：
  - 删除 `processedCount` / `successCount` / `failCount` / `currentUnitSize` / `currentArtist` / `chunkIndex` / `chunkTotal` / `currentPhase` 八个 `@Volatile` 标量；`_progressState` 成为唯一运行快照，`getProgress()` 直接返回 `.value`。
  - 新增 `transition(state)` 与 `updateProgress {}` 两个写入口；初值统一为 `EnrichProgress.IDLE`（`runState` 同步为 `UNREGISTERED`）。
  - `runLoop()` 包 `try/catch(CancellationException)/finally`，正常退出 / 取消 / 异常统一走 `finish()`；取消时 phase = `已停止`。
  - `pause()` / `resume()` 增加 `isActive` 守卫；新增 `markSchedulerPaused()` / `markSchedulerResumed()`，Master 的 `onPause/onResume` 同时通知 StopSignal 与 Agent 状态。
  - `enrichConfig` 加 `@Volatile`；`shutdown()` 取消 `stateIn` 的 scope；`stateFlow.detail` 改用 `chunkIndex/chunkTotal`，不再用累计 `processed/currentUnitSize`。
  - `MasterAgent.stopEnrichLocked()` 改为先 `cancel + join`（让 finally 发布终态）再 `shutdown`。
- **判据**：`EnrichSubAgentTest` 新增「取消仍发布终态」「Scheduler 暂停更新共享状态」两条；`pauseResume` 改为在真实运行中的 loop 上验证；现有自退出 / 无配置 / Round 1 失败 / Free 模式用例保持。
- **验证**：`./ktlint check` 0；`:shared:desktopTest --tests "...EnrichSubAgentTest"` 通过（8 条 + FreeMode 2 条）；`testCore` BUILD SUCCESSFUL；`:shared-ui:compileKotlinDesktop` BUILD SUCCESSFUL。
- **未做**：单首两路落库事务、`toolRegistryView` 死参数、`accessMode` 热切换；Hello / Radio 尚未套用。

### 2.8 门槛合一与 Enrich 终态出口（2026-11-13）

- **背景**（讨论中发现的自相矛盾）：启动决策、入口判断、引擎兜底各判一套 —— `applyHealth()` 看 `canEnrich`（配置 + 额度 + 曲库）、`llmReady()` 只看 `isConfigured`、`startEnrich(task)` 内部另看 `enrichPolicyConfig.enabled`。结果空库 / 额度耗尽时，UI 直调与 `enrich_start` 工具仍能绕过健康门控把 Enrich 拉起来白跑一轮；用户关掉富化时又被第三层静默挡掉，门控以为自己启成功了。
- **动作 A · 一个检查、一份条件**：
  - `AgentHealth` 增 `enrichEnabled`、`canHello`、`canEnrich`；`libraryBlocks` 规定**只有 `LibraryHealth.Empty` 才拦**，读不到曲库按放行（与 LLM 侧「读失败返回 Ready」同约定）。
  - `AgentHealthChecker` 增 `enrichEnabledProvider`（Master 传 `{ enrichPolicyConfig.enabled }`）；`settingsRepository` 未接线从「按不可用判」改为「无法判断 → 放行」，避免测试 / 旧装配被误拦。
  - `AgentHealthGate.inspect()`：只读、不加锁、不触发 `onEvaluated` —— `evaluate()` 的回调会调到 `start*`，加锁会自锁、触发回调会递归拉起服务。
  - `MasterAgent` 删除 `llmReady()`，四个入口统一 `health()` = `healthGate.inspect()`；`applyHealth()` 改用 `canHello` / `canEnrich`。对话不可用文案按 `LlmHealth` 分档（额度耗尽 / 未配置 / 其它），不再把额度问题说成「没填 Key」。
- **动作 B · 终态接出状态出口**：Enrich 早在 §2.7 就有 `finish()`，但终态只落在 `phase` 文本与日志里，`CapabilityState.Status` 的 `COMPLETED` / `ERROR` 全仓无人写（UI 侧 `AgentVisuals` 的颜色与 14 语言文案其实早已备好）。
  - 新增 `EnrichOutcome { COMPLETED, CANCELLED, FAILED }`，`EnrichProgress.outcome` 由 `runLoop()` 的 `finally` 唯一写入；`catch (Throwable)` 区分真异常与达标自退，不再把崩溃记作「完成」。
  - 映射抽成纯函数 `EnrichProgress.toCapabilityState()`：`COMPLETED→COMPLETED`、`FAILED→ERROR`、`CANCELLED→IDLE`。
- **判据**：`AgentHealthCheckerTest` 补 3 条（空库 Hello/富化双否、富化开关关闭、依赖未接线放行）；`EnrichSubAgentTest` 补终态断言与一条纯映射用例（四种 outcome → Status）。
- **未做 / 边界**：额度恢复的重检已由 §2.9 覆盖；Master 消费终态与清账见 §2.10（**刻意不重开**）；本次未加「回调不误清新实例」的并发判据；Hello / Radio 依旧没有 `finally`；未加「stateFlow 异步消费后 UI 显示 COMPLETED」的端到端用例。

### 2.9 应用回到前台触发健康重检（2026-11-13）

- **背景**：`evaluate()` 原本只有三个触发点 —— `initialize()`（冷启动）、`onLibraryMutated()`（扫曲库）、`updateAiConfig()`（配置真变）。自定义模式开了每日限额、App 跨天不重启时，额度按 `dayStart` 归零但**没有任何事件**触发重检：四个入口的 `inspect()` 会立刻放行，之前被门控跳过的 Hello / 富化 / 画像却仍不启，要等用户改配置、扫曲库或重启 App。
  - 补正：切换免费 / 付费 / 自定义、修改每日限额属于**配置变化**，本来就会触发 `updateAiConfig()` → `evaluate()`；免费额度是一次性 1 亿 token 硬顶、不按日重置，不存在「第二天自动恢复」。
- **决议**：用户定「每次启动 Master 就应该检查服务的 health」—— 把重检绑到**生命周期节点**，而不是逐个状态补触发，也不做轮询。
- **动作**：
  - `MasterAgent.onAppForeground()`：`scope.launch { healthGate.evaluate() }`，与 `onLibraryMutated()` 同形；`applyHealth()` 与三个 `start*` 全部幂等，重复回前台只多一次自检。
  - `AppRootViewModel.onAppForeground()` 转发（UDF 形态：组件只上报事件）。
  - `AppRoot` 用 `LocalLifecycleOwner` + `LifecycleEventObserver` 监听 `ON_RESUME`。该 API 在 `commonMain` 已有先例（`LibrarySettingsScreen` 的权限复检），三端共用一个组合根，**不新增 expect/actual、不改 Android / Desktop / iOS 平台入口**。
- **边界**：冷启动 `initialize()` 本就 evaluate 一次，首次 `ON_RESUME` 会再跑一次（幂等，代价是一次本地查询）；不做轮询，也不订阅 `TokenQuotaGate.usage`（它同样是「查配额才刷新」，不是时间驱动）；iOS 的 `LocalLifecycleOwner` 行为沿用既有先例，本机无编译反馈。
- **判据**：新增 `AgentHealthGateTest` 2 条 —— `evaluate()` 每次都触发回调（回前台能补启）；`inspect()` 只刷新快照、不触发回调（入口不递归）。

### 2.10 Master 消费 Enrich 终态（2026-11-13）

- **背景**：§2.8 把终态写进了状态出口，但 Master 没有任何完成回调（全仓 0 处 `invokeOnCompletion`）。达标自退后 registry 引用、Scheduler 注册、进度转发协程、Job 引用一直挂到下次开工或 App 关闭；「完成 / 取消 / 崩溃」对 Master 是同一件事。
- **研究结论（用户逐条拍板）**：
  - 实际退出路径 6 种：达标自退、配额软停止（`shouldSoftStop()`）、用户主动停 / 重建 cancel、真异常、「未达标没活可拉」等待循环、LLM 连败 backoff —— 后两种**不是终态**。
  - **配额软停止原先被记成 `COMPLETED`**（`break` 出循环后没有标记），是死法分派最大的失真。
  - **网络超时不需要 Master 重试**：数据层有 `AiApiError.NetworkError` 分类，但 `LlmCallExecutor` 的 `catch (e: Exception)` 把它吞成 `failed=true`、`callLlmText()` 返回 null，Enrich 只看到 "call failed or timeout"；循环内已有「连续 5 次失败 → 退避 15s → 继续」消化它，根本走不到 `FAILED`。用户定「网络超时可重试，其他无必要」→ 能到 `FAILED` 的都是真异常 → **不重开**。
  - **额度耗尽不需要自己重试**：§2.9 的「回到前台重检」会在额度恢复后 `applyHealth → startEnrich` 补启。
  - 提示不做全局 toast：`CapabilityState.detail` 本是引擎层中文字符串（现有 `phase` 即硬编码中文），`agentDetailOf()` 直接显示，**零新增 14 语言文案**。用户定「状态行 + 原因」。
  - 审计：`AuditEntry.outcome` 注释里已列 `budget_exhausted`，`chatAuditLog` 已在 Master 手里，零新增类型。
- **动作**：
  - `EnrichOutcome` 加 `QUOTA_EXHAUSTED`；`resolveOutcome(raw, softStop)` 纯函数判死法（不用加字段，break 出来时额度判断仍为 true）；`QUOTA_EXHAUSTED` 映射 `Status.PAUSED` + `detail = "额度已用完，恢复后自动继续"`。
  - `startEnrich()` 给 Job 挂 `invokeOnCompletion`；回调做 Job / 实例双重身份校验，`shuttingDown` 时跳过；清账放进 `scope.launch + enrichLifecycleMutex`。
  - `stopEnrichLocked()` 在 `cancel()` 之前先把 `enrichRunLoopJob` 置空 → 旧 Job 回调自我作废，既不重复清理、也不会稍后清掉新实例的注册项。
  - `releaseEnrichAfterRunLocked()`：停 Scheduler 注册与转发协程、把最后一帧进度写进 `_enrichProgress`、记一条 `enrich.run` 审计；**刻意不 `shutdown()` / 不 `uninstall` / 不重置进度** —— `capability()` 与固定转发流仍指向这个只读实例，监控页才能随时看到终态；下次开工由 `stopEnrichLocked()` 完整回收，单实例模型不会累积。
- **判据**：`EnrichSubAgentTest` 补 `resolveOutcome` 四种组合与 `QUOTA_EXHAUSTED → PAUSED + detail`；随 `testAll` 跑过。
- **未做**：没有「回调不误清新实例 / 不与主动停止重复清」的并发机器判据（需可拨时间或可注入的 Master 替身）；现有 backoff 是无限循环（网络长期不通时 runLoop 永不退出），本次未收敛；Hello / Radio 仍无 `finally`。

### 2.11 门控按 agent 分桶感知额度（2026-11-13）

- **背景**：讨论 Hello 改造时发现 `AgentHealthChecker` 只查**总池**（`totalUsed >= totalCap`），而三个子 Agent 的 `SchedulerStopSignal.shouldSoftStop()` 查的是各自**分桶**（`isBucketExhausted(agentId)`）。口径差导致：hello 分桶耗尽 → Hello `break` 退出 → 门控仍判 `canHello=true` → §2.9 的回前台重检立刻重启 → 又立刻退出，形成抖动。Enrich / Radio 同类问题一起存在。
- **决议**（用户）：「Master 应该能感知各个子 agent 的额度，来决定是否启动」—— 不是给 Hello 加特判，而是把分桶额度变成门控对**每个 agent** 的独立判据。
- **动作**：
  - `AgentHealth` 增 `exhaustedBuckets: Set<String>`；`check()` 里 `quotaGate.snapshot()` 只取一次（内部 `ensureSeededLocked` 会刷新 `exhaustedBuckets`），同时供总池判定与 `checkBuckets()`；后者直接调 `gate.isBucketExhausted()`，**与运行时软停止读同一份数据**，不另算一遍。
  - 派生属性：`canChat(master)` / `canRadio(radio)` / `canHello(hello + 曲库)` / `canEnrich(enrich + 曲库 + 用户开关)`，各自叠加「自己的桶没耗尽」。
  - 四入口各用各的：`startRadio` 从 `canUseLlm` 改 `canRadio`、`handleUserMessage` 改 `canChat`（master 单桶耗尽也给「额度已用完」文案）、`applyHealth` 的画像改 `canChat`（`profile` 归 MASTER 桶）；`startHello` / `startEnrich` 判据名不变但语义已含分桶。
- **效果**：Hello 抖动闭环（分桶耗尽 → 重检不重开 → 额度恢复后自动补启）；Enrich 同类口径差一并收掉；Radio 分桶耗尽时不再启动后走本地保底白跑；master 桶耗尽时对话直接给提示，不再进循环被前置闸门拦一次。
- **边界**：**不能替代** Hello 的 `finally` / R6 —— 启动后额度仍可能用到上限，`shouldSoftStop()` 照样 `break`，终态出口仍要做。总池耗尽时 `refreshDerived()` 会把四桶全标记为耗尽，与 `LlmHealth.QuotaExhausted` 天然一致。
- **判据**：`AgentHealthCheckerTest` 补 3 条（hello 桶耗尽但总池充足 → 只挡 Hello；enrich 桶耗尽 → 只挡富化；总池耗尽 → 四个全否），并把 `exhaustedDao()` 参数化成 `bucketDao(label, used)`。

### 2.12 Hello 终态收口（2026-11-13）

- **背景**：按 Enrich 样板套第二个子 Agent，但 Hello 有五处实质差异 —— 准常驻无「达标完成」、`stateFlow` 构造期冻结（R6）、`runLoop` 无 `finally`、内部自建 scope 起两个常驻协程、卡片展示已从 DAO 解耦。
- **范围收敛**：没有把 `HelloSessionState` 十几个 `@Volatile` 业务字段整体收敛成 data class —— 那些是「一次运行内部的局部状态」，7.6 §1.1 本就说该留在 `run()` 里。本次只把**生命周期状态**收成 `HelloRuntimeState` 唯一快照，改动面小、业务行为不变。
- **动作**：
  - `HelloOutcome { QUOTA_EXHAUSTED, CANCELLED, FAILED }` —— **故意没有 COMPLETED**，Hello 没有「干完」这回事。
  - R6：`_runtimeState` / `_capabilityState` + `transition()` 统一写入口；`stateFlow` 直接读 `MutableStateFlow`，**不依赖 `stateIn` 收集协程**（`shutdown()` 会 cancel 内部 scope，收集协程一停就丢最后一帧终态）；override `pause()/resume()` 同步出口且终态不被覆盖。
  - `finally`：`runLoop` 包 `try/catch/finally`，三种退出统一 `finish()` —— cancel `presenceJob`/`tickJob`、`cardPool.clear()`、写终态。取消 / 异常路径不再跳过清理，内部常驻协程随运行结束停掉。**终态把 `runState` 写成 `UNREGISTERED`**：`startHello()` 的幂等守卫把「非 UNREGISTERED」当作还活着，若留 `PAUSED`，保留只读实例会在额度恢复后挡住补启；`pause()/resume()` 也加了终态守卫，避免把 `UNREGISTERED` 改回去。
  - 终态出口：`QUOTA_EXHAUSTED → PAUSED + detail「额度已用完，恢复后自动继续」`（与 Enrich 一致，零新增文案）、`FAILED → ERROR`、`CANCELLED → IDLE`。
  - Master 消费：`helloRunLoopJob` 挂 `invokeOnCompletion` + Job / 实例双重身份校验 + `shuttingDown` 跳过；`stopHelloLocked()` 在 `shutdown/cancel` 之前先置空引用（与 Enrich 同形，防误清新实例）；`releaseHelloAfterRunLocked()` 停 Scheduler 注册与两个转发协程、记 `hello.run` 审计，**不 shutdown / uninstall / 不清空卡片与推荐流** —— 保留只读实例让监控页看到终态，下次开工完整回收。
  - 不重开：额度耗尽由 §2.11 分桶门控 + §2.9 回前台重检补启；`FAILED` 多为真异常，自动重试会掩盖问题。
- **判据**：`HelloSubAgentTest` 补 2 条 —— `stateFlow_tracksRunState_R6`（构造后 resume / pause 状态流跟着变，证明 R6 修复）、终态纯映射四种组合（含 detail 含「额度已用完」）。按该文件既有约定不跑完整 runLoop（常驻协程会吃光虚拟时钟）。
- **未做 / 边界**：没有「回调不误清新实例」的并发机器判据（同 §2.10）；保留只读实例期间内部 scope 未 cancel，但 `finish()` 已停掉两个常驻协程、无活动任务；`shutdown()` 仍由下次 `startHello` 的 `stopHelloLocked()` 触发；无「跨天额度恢复后 Hello 自动补启」的端到端用例。

### 2.13 Radio 终态收口（2026-11-13）

- **背景**：第三个子 Agent，与前两个有实质差异 —— 按需开台、每次新建实例（`retainedRadio` / `exportConversation()` 手工续接对话）；决策内核是 `RadioSession` 的 `Channel<Trigger>` 单消费循环，`runLoop` 只是 500ms 看门；关电台是**高频正常操作**，Master 已有一条完整业务收口 `stopRadioLocked()`（停监听器 → 导出对话 → `stopRadio()` 关 Channel → shutdown → unregister/uninstall → 撤销保活 → 暂停播放 → cancel+join → 停 4 个转发流 → 清固定流）。
- **因此不另写清账函数、也不保留只读实例**：自然结束（额度软停止 / 异常）由完成回调**复用 `stopRadioLocked()`**。实例被卸载后，UI 靠 `_radioState = null` 隐藏电台卡 —— 这本来就是「关台」的既有语义，不需要再留一个墓碑来显示终态。代价是额度耗尽的原因只留在审计与日志里，界面上不显示（与 Enrich / Hello 的取舍不同）。
- **动作**：
  - `RadioOutcome { QUOTA_EXHAUSTED, CANCELLED, FAILED }`（无 COMPLETED）；`_terminalOutcome` 独立持有，避免 `stopRadio()` 把 `_radioState` 置回 IDLE 时把终态冲掉。
  - `radioCapabilityState(rs, outcome)` 纯函数：终态优先（额度耗尽 → `PAUSED` + 中文 detail、失败 → `ERROR`、取消 → `IDLE`），否则原业务映射不变；`stateFlow` 改 `combine(_radioState, _terminalOutcome)`，**不动 `RadioState` 密封类**（41 处消费点）。
  - `runLoop` 补 `try/catch/finally`，`finish()` 写终态 + `runState = UNREGISTERED`；会话 Channel / 播放 / 转发流的收口全部交给 Master。
  - 补 `shutdown()` 覆写：此前 Radio 用基类实现、**内部 scope 从不取消**（`stateIn` 收集与 `RadioSession` Job 泄漏）；现在 cancel 内部 Job，最后一帧状态仍保留。
  - Master：`startRadio` 挂 `invokeOnCompletion` + 双重身份校验 + `shuttingDown` 跳过；`stopRadioLocked()` 在收口前先置空 `radioRunLoopJob`（防回调误伤新实例 / 重复收口）；回调记 `radio.run` 审计后复用完整收口，**不重开**（radio 分桶耗尽由 §2.11 门控拦住，恢复后由用户重新开台）。
- **判据**：新增 `RadioTerminalOutcomeTest` 2 条 —— 终态优先于业务态（含 detail 原因）、无终态时原业务映射不变。
- **未做 / 边界**：与 Enrich / Hello 同样没有「回调不误清新实例」的并发机器判据；没有「关台时 UI 显示额度原因」的端到端用例（实例被卸载，只有审计与日志留痕）；R8 已于 §2.14 改判 —— 电台改播放队列属开台授权范围内的会话内动作，非缺陷。

### 2.14 R8 改判：写路径「不过闸」不是缺陷（2026-11-13）

- **用户决议**：「radio 开启之后就相当于接管了播放队列，因此感觉没问题」—— 成立，且代码本来就这么设计。
- **核实到的证据**（比台账更硬）：
  - `PlaybackCommandPort` 注释：`AGENT_INTERNAL` = 「子 Agent 内部原子操作（Radio 重建队列…）→ 吞掉事件，这些是实现细节，不该被 MasterAgent 当成用户意图」。
  - `pushLocalQueueForInstantPlay`：「开播前提：无在播曲时播放器队列归电台管」；`stopRadioInternal`：「关闭电台 ≠ 清空播放列表」，只暂停不清队列。
  - 产品决议 `agent-radio.md` C3：「用户的播放控制不退出电台」—— 电台开着时用户照样能自己切歌，两者共享同一段会话。
  - 边界完整：`generation++` 拦关台后的在途模型结果、`stopRadio()` 关 Channel、`verifyQueueAfterAppend` 回读校验队列是否真落地。
- **分类错误**：台账拿「主对话 LLM 工具调用」的权限模型（`PolicyGuard.decide` → `ConfirmGate.request` → `auditLog.record`）去套「后台确定性编排代码」。前者模型可能误解意图 / 被提示注入诱导，逐次确认有必要；后者是固定代码路径，用户点开电台那一刻已一次性授权整档接管，逐次弹确认会毁掉体验。原判据「Deny 时不执行」不成立：`ToolCallExecutor` 里后台 Agent 无 `confirmGate` 会把 RequireConfirm 级工具直接 Denied，硬塞进去电台功能会被全拒 —— 这正说明那套门不是为此设计的。也解释了此前 `EffectRequest` 讨论为何别扭：把「电台续歌」和「模型调工具改歌单」当成同一种写动作本就不对。
- **改判结果**：
  - 电台改播放队列（11 处 `playbackPort.execute`）：**按设计结案**，不是缺陷。
  - 富化写标签 / extra：持久化、跨会话，「接管」理由弱一些，但富化能力本身干的就是补全歌曲信息，用户开启时同样隐含授权；转归「后台能力写持久化数据的可观测性 / 撤销」（接近 R16），**不再挂在「授权门缺失」上**，也不再要求逐次审计。
  - 仅剩的可观测性事实：审计只有 `logRadioStart`，事后查「这档电台换过哪些歌」不便；日志里已有队列快照与清扫记录。这是颗粒度问题，不是权限漏洞，暂不立项。
- **台账同步**：`TODO.md` R8 勾掉并重写、`docs/7_4/residuals-7.3.md` R8 勾掉、`plan.md` §1.3 / §六 去掉 R8、`AGENTS.md` 头部改口径、§三 / §2.13 边界行同步。
- **未改**：`ROADMAP.md` v6.11.1 的「R8 优化」是 ProGuard keep 规则收紧的**历史版本记录**，与 TODO 的 R8 编号同名不同事，按归档证据不动。

### 2.15 R4 并发与自愈（阶段 0 判据 + 修复，2026-11-13）

- **背景**：`AgentScheduler.agents` / `states` 是两张裸 `mutableMapOf`；仲裁循环每秒 `agents.entries.forEach` 遍历，注册/注销从 Master 的 `start*/stop*` 在别的协程就地改表。撞上后抛 CME → 循环协程死亡（`SupervisorJob` 吞掉、进程不崩），但 `arbitrationStarted` 仍为 true，`startArbitration()` 的幂等守卫挡住重启 → **全进程 pause/resume 永久失效**，静默、无测试变红。
- **阶段 0 先落判据（先红）**：`AgentSchedulerTest` 新增 2 条 ——
  - `并发注册注销与遍历不抛 CME`：50 写协程 × 20 轮 + 一个 3000 次读协程（真实 `Dispatchers.Default`，照 `SubAgentRegistryTest` 夹具）。
  - `仲裁循环抛异常后仍能继续仲裁`：`FlakyConditions` 第 2 次 `batteryLevel()` 抛一次，用 `runBlocking + withTimeoutOrNull(5s)` 断言仲裁仍在推进。
  - **修复前定向跑：9 例 / 3 失败** —— 并发用例读到坏数据（`requireNotNull` 失败）、自愈用例超时、未捕获异常污染了后续 `Hello 永远 RUNNING` 用例。三种表现正好对应 R4 的三种后果，证明判据有效。
- **R4 修复（后绿）**：
  - 两张表改成 `@Volatile` 不可变快照，**写侧 `@Synchronized` 整份替换（COW）**，读侧只读快照；形态照抄已修好的 `SubAgentRegistry`。新增 `applyStateChanges()` 让仲裁只提交变化项，避免覆盖并发注册刚写入的状态。
  - 仲裁循环单轮包 `try/catch`：异常记 WARN、等下一秒重试，**循环不再死亡**；`CancellationException` 仍上抛。
  - `startArbitration()` 给循环 Job 挂 `invokeOnCompletion`，意外死亡时复位 `arbitrationStarted`，幂等守卫不再卡死。
  - `stopArbitration()` 也改读快照 + 整份替换，不再就地改表。
- **修复后**：定向 `--rerun` 9 例全绿（50s）；`compileAll` / `testAll` 见 §五。
- **未做 / 边界**：`AgentSchedulerTest` 全量并行下的偶发 flaky 本轮未复现，**仍未定性**；阶段 0 另一半「跨轮重复执行写工具」判据**已随 §2.16 补上**；D6-08 已随 §2.17 结案；C 类 `CapabilitySnapshot` 已随 §2.18 结案。

### 2.16 R3 后半：跨轮工具摘要（方案 B，2026-11-13）

- **背景**：`ReActLoop` 内的 tool 轮次只活在局部 `messages` 里，`ChatViewModel` 只落 user/agent 两条 → 下一轮 `buildHistory()` 看不到「已执行过」，可能重复执行写工具；取消时更糟（Gateway 的 `ToolExecuted` 是循环结束后统一 emit，取消时一条都没有）。
- **方案（用户定 B）**：不存完整 tool 轮次、不扩 `role` 契约；只落一条**工具执行摘要**，用 `renderHint = tool_summary` 标记，UI 不渲染、只给模型看。用户理由：不需要回退/回放功能。
- **动作**：
  - `ReActLoop.run()` 加 `onRunFinished(tools)`，在既有 `finally` 里用 `withContext(NonCancellable)` 调用 —— **取消 / 异常也必达**，这是取消场景能留痕的关键。
  - `buildToolRunSummary(toolLog)` 纯函数：空列表返回 null；最多列最近 6 个 `name(outcome)`；末尾提示「不要重复执行同样的写操作」。
  - `MasterAgent` 注入可空 `AgentMessageStore`，`persistToolRunSummary()` 用 `currentOrNewSessionId()`（与 `buildHistory` 同一 ID）append 一条 `role="agent"` + `renderHint=tool_summary`。
  - `buildHistoryMessages(messages, keep)` 纯函数：最近一条 `tool_summary` 转成 **system** 消息放**最前**（不占 6 条对话窗口，也不影响末尾「与当前输入去重」）；其余只保留 user/agent。
  - `MasterChatGateway.buildHistory()` 改用它并多加载一条（`KEEP+1`）以便分离摘要；`ChatViewModel` 渲染历史时过滤 `tool_summary`。
- **判据**：3 条 —— `ToolRunSummaryTest`（空 / 含名与结果 / 截断）、`AgentHistoryProjectionTest`（摘要转 system 不占窗口 / 无摘要行为不变 / 非法 role 仍丢弃）、`ReActLoopTest` 新增「取消时仍回调已执行工具」（`HangingAfterFirstTransport` 第二轮挂起 → cancel → 断言 captured 含 `recording_tool`）。定向 3 个测试类全绿。
- **未做 / 边界**：只保留**最近一轮**摘要（旧摘要自然过期）；摘要全列所有工具（含只读），未按权限级别区分；没有「两轮 + 取消 → 模型真的不重复调用」的端到端用例（只到摘要生成与历史投影层），需要可脚本化的两轮 Master + Fake store 替身。

### 2.17 D6-08：删掉全局确认链死路径（2026-11-13）

- **复核发现**：`DialogManager.requestConfirm` 的唯一调用方是 `DialogConfirmGateAdapter`，而该适配器**全仓零构造点**；`DialogEvent.ConfirmChain` 全仓也只有 `DialogManager` 自己 emit。即这条「全局双确认链」是**无人调用的死路径**，`pendingConfirms` 裸 Map 在生产上没有任何写入者 —— 台账写的「后台协程写 / UI Main 读」是按代码形态推断的，实际调用链不存在。生产确认走的是 `ConfirmBridge` + 对话内联确认卡（那张表已有 `@Synchronized`）。
- **决议（用户）**：形态保留气泡内（确认是对话的一部分，非模态可回看；强确认已在卡内用「默认不勾选」区分），全局那条删掉。跨页可见性（切到别的页面看不到确认卡）另立一笔，先验 VM 生命周期。
- **删除内容**：`DialogManager` 的确认链（`PendingConfirm` / `pendingConfirms` / `requestConfirm` / `advanceConfirmStep` / `denyConfirmChain` / `cancelConfirmChain` / `allocateConfirmId`）；`DialogConfirmGateAdapter.kt`；`DialogEvent.ConfirmChain`；`AppRoot` 的 `confirmChain` 状态、collector 分支与 `AlertDialog` 段；`ConfirmStep` / `ConfirmDecision`（`ConfirmChain.kt`，删后无引用）；`DialogManagerTest.kt`（3 条确认链用例）。保留 `showMessage` / `showDialog` / `shareMusic`（toast / 导航 / 分享仍在用）。
- **顺带**：`_dialogEvent` 从 `SharedFlow<DialogEvent?>` 改为非空（`null` 原本是确认链收尾信号，已无人发）。
- **判据**：D6-08 从「未修」变为「不存在」—— 删除后全仓 `ConfirmChain / ConfirmStep / ConfirmDecision / pendingConfirms` 零命中；`ktlint` 0、`compileAll` 三端通过、`testAll` 全绿。
- **未做 / 边界**：「跨页待确认提示」未做（切到别的 Tab 时确认卡不可见）；**但已补超时兜底**（§2.20）：60 秒无响应按 `Timeout` 处理并结束本轮，不再无限等待。`DialogManager` 仍承担 toast / 导航 / 分享，未改名。

### 2.18 C 类收口：能力出口只交快照（2026-11-13）

- **背景**：`MasterAgent.capability(name)` / `capabilities` 返回 `Capability`（即 `SubAgent` 实例），状态只读靠注释约定；`Capability.start()/stop()` 全仓零调用（只有 `RadioSubAgent` 实现过）。
- **动作**：
  - 新增 `data class CapabilitySnapshot(name, status, detail)`（无方法、无 `raw`）；`CapabilityState.toSnapshot(name)` 丢弃 `raw`（可能携带子 Agent 内部对象）。
  - 出口改为 `capabilitySnapshots(): Map<String, CapabilitySnapshot>`（工具一次性查询）与 `capabilityState(name): Flow<CapabilitySnapshot>?`（UI 订阅）；删 `capability()` / `capabilities` 返回实例的版本。
  - 删 `Capability.start/stop`（零调用）与 `RadioSubAgent` 的两个 override。
  - `CapabilityStatusTool` 改读快照字段；`AgentMonitorViewModel` 改订阅 `capabilityState(id)`，uiState 三个字段类型换 `CapabilitySnapshot?`；`AgentVisuals` / `AgentMonitorScreen` 的参数类型同步。
- **判据**：新增 `CapabilityStatusToolTest` 5 条 —— 映射保留 status / detail 且丢弃 raw；不传 name 返回全部；传 name 只返回该能力；未知 name 失败并列出可用；空能力空态。「UI 拿不到可写实例」由类型系统保证（快照没有任何方法），不需要运行时用例。
- **未做 / 边界**：`CapabilityState.raw` 字段本身保留（三个子 Agent 的 stateFlow 仍产出），只是出口丢弃；`capabilityState()` 每次调用返回新的 `Flow`（`map` 派生），由订阅方生命周期管理，不做 `stateIn` 缓存；`check-udf.py` 白名单未变（唯一 agent 相关条目 `HelloCardRadioStatus.kt → MasterAgent` 与本项无关）。

### 2.19 阶段 6 收口：约定、预检与逐债归档（2026-11-13）

- **书面约定定稿**：`docs/7_6/plan.md` §3.1 四条原则更新到与实物一致 —— 单一写者补「写侧 `@Synchronized` / `Mutex` 整份替换」与 `AgentScheduler` 落地样本；不可变出口补 `CapabilitySnapshot` 与 `CardPool` 的读—改—写边界；取消与终态补「三个子 Agent 的 `finally` + 仲裁单轮自愈」；§3.3 把 D6-08 从「加互斥」改判为「删死路径」。`AGENTS.md` §六 新增「状态所有权约定（四条）」入口段，并同步门控 / 子 Agent 终态 / 能力出口三处已失真的描述。
- **flaky 定性**：`AgentSchedulerTest` 在全量并行下的偶发 flaky，本批累计 15+ 次 `testAll` **未复现**；无法稳定复现就不能写回归用例，**仍未定性**，留待 CI 恢复跑测（X-01）后观察。
- **预检**：`./gradlew-smart.bat preflight` BUILD SUCCESSFUL（8s，`checkVersion` / `checkReleaseConsistency` / `checkDesignTokens` / `testAll` 均 UP-TO-DATE，因刚跑过全量）。
- **逐债归档**：核心四债 R3 / R4 / R5 / R6 + D6-08 + C 类 `CapabilitySnapshot` 全部结案；范围外 R7 / R9 / R11 / R12 / R16 仍在 `TODO.md`，R8 已改判；未做的只剩「跨页待确认提示」（另立一笔）与 flaky 定性。


### 2.20 确认超时兜底：告诉模型并结束本轮（2026-11-13）

- **背景**：`ConfirmBridge.awaitApprovals` 是 `deferred.await()`，无超时；用户切走后 agent 会一直等（§2.17 的遗留边界）。
- **用户决议**：「超时之后主动告诉模型，结束对话」—— 不静默按拒绝处理，也不让循环继续。
- **动作**：
  - `ConfirmOutcome` 加 `Timeout`（与 `Deny` 区分：不回拨信任）；`ConfirmBridge` 用 `withTimeoutOrNull(CONFIRM_TIMEOUT_MS = 60s)`，超时后 `takePending` 清理并返回全 `Timeout`（此后 `submit` 返回 false，不会唤醒已超时的一轮）。
  - `batchDecideApprovals` 返回 `ApprovalBatch(approvals, timedOut)`：超时项 `approved=false` + 记入 `timedOut`；`executeOne(..., timedOut=true)` 记审计 `outcome="timeout"`、回传「确认超时：用户未在时限内回应，该操作已跳过；本轮对话到此结束」。
  - `ReActLoop` 检测到 `record.outcome == "timeout"` 即 `terminated = CONFIRM_TIMEOUT`、跳过剩余工具并 `break`；`finalText` 兜底文案「确认超时，本轮对话已结束。需要的话请重新发起操作。」
  - `TerminationReason` 加 `CONFIRM_TIMEOUT`。
- **判据**：`ConfirmBridgeTest` 2 条（超时返回全 Timeout 且后续提交无效 / 按时提交返回用户决策）、`ToolCallExecutorTest` 2 条（超时标记与拒绝可区分 / `timedOut` 记录 timeout 并回传明确文案）、`ReActLoopTest` 1 条（超时 → `CONFIRM_TIMEOUT`、工具不执行、文案含「确认超时」）。定向全绿。
- **未做 / 边界**：超时时长写死 60s，未做设置项；跨页可见性仍未做（用户不在对话页时不会主动提示，只是 60 秒后会收到「确认超时」的结束文案）；`ChatViewModel` 的确认卡在超时后由 `Finished` 分支清理，未单独加「已超时」的 UI 态。
---

## 三、规划外调整的边界与风险

| 项 | 边界 |
|---|---|
| LLM-only | 不改 `ChatAgentGateway` 的 6 种事件协议、不改 `ConfirmBridge` 握手；不新增用户可见功能，只删本地捷径 |
| 能力控制工具 | 依赖方向仍为 `tool → tool/spec`；真实方法由 `MasterAgent.initialize()` 回调绑定，没有 `tool → runtime` 反向边 |
| 健康门控 | 不做轮询；冷启动 / 配置变化 / 曲库变化 / 应用回到前台四个节点重检（§2.9）；不阻止 UI 注入 `MasterAgent`；启动决策与四个入口共用同一份 `AgentHealth`（§2.8），且按 master / radio / enrich / hello 各自分桶判额度（§2.11） |
| 卡片缓存 | 缓存只作展示兜底，不作为事实源；解析失败返回 null |
| 未覆盖 | 没有端到端「工具路由」用例；iOS 零机器反馈；无「两轮 + 取消 → 模型真的不重复调用」的端到端用例（只到摘要 / 投影层）；无「跨天额度自动恢复后服务补启」与「回调不误清新实例」的端到端 / 并发判据；`AgentSchedulerTest` 全量并行偶发 flaky 未定性；后台能力写持久化数据的可观测性 / 撤销（原 R8 富化半边，归 R16 类）未立案 |

---

## 四、按规划仍待施工

| 项 | 状态 |
|---|---|
| 子 Agent 状态收口（Enrich 样板） | **三个已全部套用**：Enrich §2.7/§2.8/§2.10、Hello §2.12、Radio §2.13（复用现成关台收口） |
| 阶段 0（R4 并发 / 自愈判据） | **并发 / 自愈判据已落**（§2.15）：2 条用例先红后绿；仍缺「跨轮重复执行」判据与 flaky 定性 |
| R4 并发与自愈 | **已结案**（§2.15）：COW 快照 + 单轮异常自愈 + 完成回调复位；TODO / 7.3 残余台账已勾 |
| R3 跨轮持久化 | **已结案**（§2.16）：不扩 role，改落 `tool_summary` 摘要并投影成 system 提醒；取消路径由 `finally + NonCancellable` 必达 |
| R6 Hello `stateFlow` | **已结案**（§2.12）：唯一快照 + `transition()` 写入口 + `pause/resume` 同步；TODO R6 已勾 |
| D6-08 `DialogManager.pendingConfirms` | **已结案**（§2.17）：复核为死路径，整体删除；生产确认走 `ConfirmBridge` + 对话内联确认卡 |
| C 类 `CapabilitySnapshot` | **已结案**（§2.18）：出口只交 `CapabilitySnapshot`，`start/stop` 一并删除 |
| 同步形态书面约定 | **未做** |
| 收口（ktlint / compileAll / testAll / preflight） | **全部已跑**：`ktlint` 0 处、`compileAll` / `testAll` 全绿、`preflight` BUILD SUCCESSFUL（8s，各任务 UP-TO-DATE） |

---

## 五、验证状态

- 2026-11-13 复核（§2.20 后，最新一批）：`./ktlint check` 0 处；`compileAll`（三端）BUILD SUCCESSFUL（2m8s）；`testAll` BUILD SUCCESSFUL（2m12s）；超时判据定向全绿（`ConfirmBridgeTest` 2 / `ToolCallExecutorTest` 2 / `ReActLoopTest` 1）；`check-udf` 0 违规；`checkDesignTokens` 通过；`sync --check` 9 项派生点一致。
- 按用户 2026-11-13 约定：**每批改动默认跑全量测试**，只有明确说「不要跑全量测试」才跳过。
- 首次跑 `testAll` 时 `stateFlow_tracksRunState_R6` **变红**：快照初值沿用基类 `PAUSED` → 映射成「已暂停」，与原来对外 `IDLE` 不符。把 `HelloRuntimeState` 初值改为 `UNREGISTERED` 后重跑全绿 —— 这条判据正好守住了「修 R6 不能改变原有对外状态」。
- 判据累计：`AgentHealthCheckerTest` 11 条、`AgentHealthGateTest` 2 条、`AgentSchedulerTest` 9 条（含并发 / 自愈 2 条）、`ToolRunSummaryTest` 3 条、`AgentHistoryProjectionTest` 3 条、`ReActLoopTest` 含取消回调 1 条、`CapabilityStatusToolTest` 5 条、`EnrichSubAgentTest` 终态 / `resolveOutcome` / 纯映射、`HelloSubAgentTest` 6 条、`RadioTerminalOutcomeTest` 2 条、`ConfirmBridgeTest` 2 条；均随 `testAll` 跑过。
- 前几批同样全绿：§2.8 `compileAll` 5m23s / `testAll` 2m6s；§2.9 2m31s / 1m28s；§2.10 3m55s / 1m56s；§2.11 3m25s / 2m8s；§2.12 3m / 1m46s。
- `AgentSchedulerTest` 的 flaky 仍未定性；`preflight` 未跑；iOS target 本机不能编译，`commonMain` 改动对 iOS 零机器反馈。

---

## 六、提交拆分建议

1. `refactor(agent): R5 —— 删除步数上限`（含 14 语言文案与设置页）
2. `refactor(agent): R3 —— 取消收尾与 alwaysAllow 即时落盘`
3. `refactor(agent): 对话只保留 LLM 路径 + 能力控制工具`
4. `feat(agent): 启动健康门控 + Hello 卡片缓存回退`
5. `docs: 7.6 台账与口径同步`

> 拆分只按「可独立回退」划分；若要求一笔提交，用 `refactor(agent): P2 起步 —— R5/R3 与三项对策调整` 合并。

---

## 七、失真登记（本批同步）

| 旧口径 | 实物 | 处置 |
|---|---|---|
| 7.6 plan「规划建立，未开工」 | 工作树已有 R5 / R3 前半与三项调整 | 已改 plan 状态行 / §1.2 / §1.3 / §2.6 / §四 / §五 / §七 / §八，并建本台账 |
| AGENTS.md / DEVELOP.md「30 个工具」 | 30 个基础 + 8 个能力控制 = 38（`initialize()` 后） | 已改 AGENTS §六 / DEVELOP |
| `ToolCatalog` 注释「34 实例 = 26 + 8 DJ」 | 基础 29 + `capability_status` 1 + 能力控制 8 = 38；DJ 早删 | 已改注释 |
| TODO R6 判据「共用契约测试」 | 无对应实现 | 改为「同一组状态迁移下产出一致 `CapabilityState`」 |
| TODO R8「写路径不过闸」 | 电台开台即授权整档接管队列，代码注释与产品决议均按会话内实现细节设计 | 2026-11-13 改判为非缺陷，见 §2.14 |
| `HelloSubAgent` 的 `enrichConfig` | 实际是 hello 自己的配置 | 已改名 `helloConfig` |
| `docs/README.md` 多处「7.6 未开工」 | 已开工（工作树未提交） | 已同步 |
