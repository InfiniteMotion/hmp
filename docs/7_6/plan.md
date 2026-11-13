# 7.6 期 · P2 Agent 运行时状态所有权改造规划

> **版本归属（用户，2026-11-06）**：UDF 工作分 **P1 页面体系 = 7.5.0** / **P2 Agent 运行时状态所有权 = 7.6.0**，先后推进，两者**不共用批次与验收判据**。P1 已于 2026-11-11 收口（`6df3409`），台账见 [../7_5/progress.md](../7_5/progress.md) §十四。
> **边界（[../7_5/plan.md](../7_5/plan.md) §1.1 / §1.3）**：**不要把 `UiState` / `onXxx` / `reduce` 推进引擎内部**。`ReActLoop.run()` 是有限步循环、状态全是局部变量、副作用本身就是主流程；P2 是「统一与守门」，不是从零引入 UDF。`ChatAgentGateway` 的 6 种不可变 `ChatAgentEvent` 与 `ConfirmBridge` 握手协议不改。
> **状态（2026-11-11 建立，2026-11-13 更新）**：**施工中，改动仍在工作树、未提交**。已落 R5 结案（删除步数上限）、R3 前半（`try/finally` + `alwaysAllow` 即时落盘），并追加三项对策调整（对话只保留 LLM 路径 / `AgentHealth` 健康门控 / Hello 卡片缓存回退）；**R6 已于 2026-11-13 结案**（Hello 状态出口 + 终态收口，见 [progress.md](progress.md) §2.12）；**R4 已于 2026-11-13 结案**（见 [progress.md](progress.md) §2.15）；**R3 后半也已结案**（跨轮工具摘要，见 §2.16）；**D6-08 已于 2026-11-13 结案**（复核为死路径，整体删除，见 §2.17）；**C 类 `CapabilitySnapshot` 已于 2026-11-13 结案**（能力出口只交快照，见 §2.18）。逐项证据、判据与验证状态见 [progress.md](progress.md)。P1 收口后已补一笔 `bump: 7.5.0` 只标注提交（`release.toml` 现为 **7.5.0**、`date` 留空；无 tag / 无包 / 无 push、不发行），正式发行仍待 P1 三项前置。P2 的 bump 尚未做（见 §八 ①）。
> **取证口径**：本规划 §二 全部计数为 2026-11-11 工作树实测（`git HEAD = 6df3409`）。行号坐标会过期，动手前按符号名 grep；归档 `docs/archive/**` 只进不改；文档与代码冲突时以代码为准。
> **台账**：已建 [progress.md](progress.md)（逐债判定、对策调整、验证状态与提交拆分），不在本文件里滚动施工记录。

---

## 一、范围与边界

### 1.1 与 P1 的分界

| | **P1 页面体系**（7.5.0，已收口） | **P2 Agent 运行时**（7.6.0，本规划） |
|---|---|---|
| 区域 | `shared-ui/commonMain` UI 层 + 适配 VM | `shared/.../domain/agent/`；唯一跨模块交界是 `shared-ui/.../dialogs/controller/DialogManager.kt` |
| 状态模型 | 一屏的快照 → `StateFlow<XxxUiState>` | 一次会话 / 一次 `run()` 的进度与生命周期 |
| 目标形态 | 完整 UDF：单一状态出口 + 事件上送 + 副作用归 VM + 一次性效果 | actor / 状态机：`Mutex`、`StateFlow.update`、Channel 单消费循环 + 不可变事件 |
| 对应台账债 | R15 / R48（**本规划不代为结案**，见 §二 失真登记） | R3 / R4 / R6 / D6-08 |
| 验收 | `ktlint` + `check-udf.py` 形态门禁 + `preflight` | 并发压力 / 取消路径 / 状态所有权检查，**不套用 P1 判据** |

### 1.2 做什么

1. **收口四条已立案的并发 / 生命周期债**（R3 / R4 / R6 / D6-08），每条「现状 → 动作 → 判据」见 §四。
2. **把已有的三种同步形态收敛成一份书面约定**：`CardPool` / `SubAgentRegistry` / `TokenQuotaGate` / `RadioSession` 实际分别是原子 `update` / COW 快照 / `Mutex` / Channel 单消费循环，**只有 `RadioSession` 是完整 actor 形状**（见 §2.3.1）。P2 不重写它们，只把「何时用哪种」写成约定（放 `shared/.../domain/agent/` 同目录，不引入基类、不加依赖），作为新增运行时组件的默认答案。
3. **修引擎的能力出口契约**：`MasterAgent.capability()` 目前把 sub-agent 活实例交出去；改为不可变 `CapabilitySnapshot`，并统一 `Capability.stateFlow` 实现（与 R6 同一笔）—— **2026-11-13 已落地**，见 §2.18。**UI 侧已在 VM 内转成快照，不再搬注入**（§2.5）。
4. **落并发 / 取消路径的机器判据**：CI 不跑单测，本机是唯一守门人；这四类债不能靠 review 把关。R3 的取消收尾判据已于 2026-11-11 补上两条（`ReActLoopTest`），但 R3 跨轮重复执行写工具与 R4 并发自愈的判据**仍不存在**（§四 阶段 0）。
5. **对策中追加的三项调整（2026-11-11，原规划外）**：① 对话只保留 LLM 路径 —— 删内建意图短路与本地词表，`radio_*` / `enrich_*` 改由 LLM 工具承担；② 启动改为健康门控 —— `AgentHealthChecker` / `AgentHealthGate` 决定 Hello / 富化 / 画像是否拉起；③ Hello 未启动时卡片从 `hello_card_cache` 恢复 —— `HelloCardCodec` / `HelloCardRecovery`。动机、边界与风险见 [progress.md](progress.md) §二/§三。

### 1.3 不做什么

- 不重写 18,265 行运行时、不引入 actor 框架、不改 `ChatAgentGateway` 事件协议、不做 MVI 化。
- 不把功能缺陷顺手扩进来：R7（提示注入）、R9（WARN 日志泄露曲库）、R11（首轮阻塞主线程）、R12（`musicLabel` 全表聚合）同属「引擎正确性」，但**不在状态所有权范围**，仍由 `TODO.md` 持有。R5（步数上限）已于 2026-11-11 整体删除、结案；**R8 已于 2026-11-13 改判为非缺陷**（播放队列按设计结案，见 [progress.md](progress.md) §2.14），均不再挂账。
- 三项对策调整不新增用户可见功能面：它们是删除本地捷径 / 启动兜底后的必要补位（LLM-only、健康门、缓存回退），不是新功能扩张。
- 不做 P1 的发行前置（iOS target 编译、Android/Desktop 手动回归、设计校准）、不做 7.5.0 / 7.6.0 的 bump（见 §八）。

---

## 二、现状基线（2026-11-11 实测）

### 2.1 规模与源集分布

| 源集 | agent 相关 `.kt` 文件 | 备注 |
|---|---|---|
| `shared/src/commonMain/.../domain/agent/` | **83 文件 / 18,265 行** | 主战场；`HelloSubAgent.kt` 1988、`MasterAgent.kt` 1869、`RadioSubAgent.kt` 1760、`EnrichSubAgent.kt` 774 四个重文件约占 **35%** |
| `androidMain` / `desktopMain` / `iosMain` | 2 / 2 / 3 | 平台桥，非状态所有权债所在 |
| `commonTest` / `desktopTest` | 45 / 2 | 有 `AgentSchedulerTest`、`ReActLoopTest`、`SubAgentRegistryTest`、`RadioSessionTest`、`HelloSubAgentTest`、`CardPoolTest`、`TokenQuotaGateTest`、`MasterAgentLifecycleTest` 等 |
| `iosTest` / `androidHostTest` | **0 / 0** | iOS target 无任何测试源集 |
| `shared-ui/commonMain` 的 agent 包 | 39 文件 | 卡片 / 监控 / 电台控制台 / 聊天；本阶段只碰 `DialogManager.kt` 与能力快照的消费侧复核 |

> **与规划原文的差异**：[../7_5/plan.md](../7_5/plan.md) §1.1 记「18,232 行」，实测 **18,265 行**（+33）。按「计数为当日工作树实测、行号会过期」口径如实登记，不回改 7.5 规划的历史计数。
> **施工中口径（2026-11-11）**：上表是 HEAD `6df3409` 的基线；工作树改动已删 `funnel/CommandLexicon.kt` 与 `runtime/ChatIntentRules.kt`、新增 `health/` / `card/` / `CapabilityControlTools.kt` 5 个文件。当前 `MasterAgent.kt` 1755 行、`ReActLoop.kt` 223 行、`HelloSubAgent.kt` 1988 行；逐项差异见 [progress.md](progress.md) §一。

### 2.2 并发原语分布（`commonMain` agent 目录 grep 命中**行数**，非独立组件数）

| 原语 | 命中行 | 说明 |
|---|---|---|
| `Mutex` / `withLock` | 35 / 24 | 已有相当规模，集中在生命周期与配额，不是「从零引入同步」 |
| `StateFlow` / `MutableStateFlow` | 82 / 30 | 出口多，但写者是否唯一才是判据 |
| `.update { }` | 6 | **全部集中在 `CardPool` 的 4 个方法**，其余 30 个 `MutableStateFlow` 仍是 `_x.value = ...` |
| `Channel` | 3 | **全部在 `RadioSession`** 的 `Trigger` inbox，全目录仅此一处单消费循环 |
| `mutableMapOf` | 14 | 含 R4 两张表、`TokenQuotaGate.usedByBucket`（已由 Mutex 护住）等 |
| `ConcurrentHashMap` / `atomic` / `CompletableDeferred` | 0 / 0 / 0 | 无并发容器、无 atomicfu；`CompletableDeferred` 在 `shared-ui` 的 `ConfirmBridge`（`confirmBridge.kt`，`Mutex` + `@Synchronized` + `CompletableDeferred` 握手） |
| `finally` | 1 | 整个 agent 目录仅一处，**不在 `ReActLoop`** —— R3 属实 |

### 2.3 四个「已按 actor 形态写」组件的核实结果

| 组件 | 实测形态 | 评价 |
|---|---|---|
| `SubAgentRegistry.kt` | `Mutex.withLock` 串行写 + `@Volatile` 不可变 `snapshot` 整份替换（copy-on-write）；读侧无锁；`clearNow()` 单次引用替换并**书面登记可接受窗口** | COW 快照的参考实现；**不是 Channel actor**，但读侧零竞态 |
| `CardPool.kt` | `_cards: MutableStateFlow<List<SlideCard>>` + `_cards.update {}`；但 `clear()` 直接赋 `.value`，`popByType()` / `containsType()` 先读 `.value` 再 update | 列表不可变故不会 CME；**读—改—写分离仍是竞态窗口**，约定里要写明边界 |
| `TokenQuotaGate.kt` | `Mutex` 护 `usedByBucket: MutableMap` 与 `check/record/snapshot`；4 个 `MutableStateFlow` 派生出口（`usage` / `exhausted` / `bucketUsage` / `currentMode`） | 写者唯一，合规；`isBucketExhausted()` 直接读 `.value` 属 StateFlow 自身线程安全 |
| `RadioSession.kt` | `Channel<Trigger>(UNLIMITED)` + 单消费 `run()` 循环；`messages` 普通 `MutableList` 只在单消费者内改，`exportMessages()` / `messagesSnapshot()` 走 `toList()` 拷贝 | **唯一完整的 actor 形状**（状态私有 + 事件单消费）；`close()` 时 `receive()` 抛 `ClosedReceiveChannelException` 是正常退出路径 |

#### 2.3.1 与旧叙述的失真登记（本规划据实物立论，不照抄台账）

| 旧叙述 | 实物 | 处置 |
|---|---|---|
| 7.5 规划 §1.1：四组件「已按 actor 形态写」（`Mutex` / `StateFlow.update` / Channel 单消费循环 + 不可变事件） | 四份是**三种**形态；仅 `RadioSession` 有 Channel 单消费循环，全目录 `Channel` 只此一处；`Mutex` 确有 35 行、`StateFlow.update` 只 `CardPool` 一处 | §3.1 写三者适用边界，不假装已统一；本规划 §1.2 第 2 条按此措辞 |
| 7.5 规划 §1.1：18,232 行 | 18,265 行 / 83 文件 | §2.1 如实登记，不回改历史计数 |
| `progress.md` §十三：13 处 `koinInject` 基线归 7.6 | 基线文件为空，UI 无 composable 直注 `MasterAgent` | 已在 §十四 订正，详见 §2.5 |
| `TODO.md` R6 判据「与 Enrich / Radio 共用同一条 Capability 状态契约测试」 | Enrich / Radio 已各自 `stateIn(...)`，全仓**无共用契约测试**；Hello 是落后的一方 | 判据改为「Hello 与 Enrich / Radio 在同一组状态迁移下产出一致的 `CapabilityState`」 |
| `TODO.md` R3「tool 轮次不回传对话历史」 | 循环内已 `trackMessages = true` 并把 tool_calls / tool result 写回局部 `messages`；丢的是 `MasterChatGateway.buildHistory()` 的**跨轮持久化** | §2.4 已订正，判据按跨轮重写 |
| 7.3 残余台账 R6 坐标 `HelloSubAgent.kt:1939` | 实现已漂到文件末（约 `:1979`） | 行号会过期，按符号名 `stateFlow` 定位，不回改归档 |

### 2.4 四条台账债的代码现状（不照抄文档）

| 债 | 实物 | 与台账叙述的差异 |
|---|---|---|
| **R3** `ReActLoop` 取消收尾 | **2026-11-11 已修前半**：`run()` 已用 `try/finally` 包住循环，`finally` 里发 idle，取消 / 异常也复位 Presence；`alwaysAllow` 改为勾选即落盘（`onAlwaysAllowChanged`），`onSessionComplete` 与末尾持久化已删；步数熔断随上限整体删除 | 台账「tool 轮次不回传对话历史」需修正：循环内 `trackMessages = true`，且已把 assistant `tool_calls` 与各 tool result 加进局部 `messages`。**真缺口是 `MasterChatGateway.buildHistory()` 只保留 user/agent 文本的跨轮持久化** |
| **R4** `AgentScheduler` 竞态 | `agents` / `states` 仍是裸 `mutableMapOf`（`:67-68`）；`startArbitration()` 有 `@Synchronized`，但仲裁循环 `agents.entries.forEach` 无同步、无 `try/finally` 重启守卫，`arbitrationStarted` 置 true 后循环抛异常即永久失效 | 与台账一致；**2026-11-11 未动** |
| **R6** `HelloSubAgent.stateFlow` | 仍是构造期一次性 `MutableStateFlow(CapabilityState(... when (state()) ...))`（约 `:1979`），此后无人写回 | 与台账一致；**2026-11-11 未动**；`SubAgent` 基类已有 `runState`，改法是 `runState.map { … }.stateIn(..., Eagerly, ...)`，与 Enrich / Radio 现有写法对齐 |
| **D6-08** `DialogManager.pendingConfirms` | 裸 `mutableMapOf`（`:49`）；`idMutex` 只护 ID 分配（`:61-74`，碰撞那一半确已修），map 的读 / 写 / remove（`:93/:104/:111/:134/:141`）无保护 | 与台账一致；**2026-11-11 未动**；**位置在 `shared-ui/commonMain`，不在 `shared`** —— P2 唯一跨模块改动点。**11-13 补记：复核发现唯一调用方 `DialogConfirmGateAdapter` 全仓零构造点，属死路径，已整体删除，见 §2.17** |

### 2.5 引擎快照与 UI 交界（口径订正）

- `MasterAgent.capability(name): Capability? = _subAgents[name] as? Capability`（`:446`）返回的是 **sub-agent 活实例**；`capabilities`（`:443`）同样是活实例映射。
- UI 侧：`shared-ui/commonMain` 排除 import 后约 **20 余处活 `koinInject`**，**无一处 composable 直接注入 `MasterAgent` 或调用 `capability()`** —— 11-10 复审已把卡片与看板收进 `HelloCardsViewModel` / `AgentMonitorViewModel`。全仓唯一 `capability()` 调用是 `AgentMonitorViewModel.refreshCapabilities()` 里订阅 `masterAgent.capability(id)?.stateFlow` 并 `_uiState.update {}` 存 `CapabilityState` 快照。
- 因此 **P2 的「不可变能力快照」是引擎契约问题，不是 UI 注入债**：要做的是让 `Capability` 出口不再交付可写活实例、统一三个 SubAgent 的 `stateFlow` 实现（R6），而非再搬一轮 UI 注入。
- `scripts/udf-inject-baseline.txt` **基线为空**（自述「既有债已全部清理」）；`check-udf.py` 的 `CARD_RENDER_ALLOWLIST` 里 `HelloCardRadioStatus.kt / MasterAgent` 是**残留登记**（源文件现只剩一句「由 HelloCardsViewModel 快照下发，卡片不再直连 MasterAgent」注释），P2 收口时可清。
- `docs/7_5/progress.md` §十三 例外条目「13 处 `koinInject` 基线归 7.6 引擎快照」是 §十 11-09 的中间态，已在 §十四 订正，**不要按 13 处这个旧数字开工**。
- `docs/7_5/plan.md` §1.1 把 R15 / R48 列为 P1 对应台账债，但 `progress.md` 未逐条核销、`TODO.md` §六 对照表仍标「◐ 部分」—— 本规划**不代为结案**。

### 2.6 对策中追加调整的现状（2026-11-11）

| 调整 | 实物 | 边界 |
|---|---|---|
| 对话只保留 LLM 路径 | `CommandLexicon` / `ChatIntentRules` / `BuiltinIntentId` / `intentHandled` 已删；`ChatViewModel` 只走 `launchRun()`；8 个 `radio_*` / `enrich_*` 工具在 `MasterAgent.initialize()` 动态注册 | 不改 `ChatAgentGateway` 事件协议与 `ConfirmBridge`；播放控制经 `playback_control` 工具 |
| 健康门控 | `AgentHealthChecker`（LLM 配置 / 配额、曲库空、记忆开关）+ `AgentHealthGate`（`Mutex` + `@Volatile last`）；`initialize()` / `onLibraryMutated()` / `updateAiConfig()` 触发 | 不做轮询；`settingsRepo` 为 null 的测试装配按放行 |
| Hello 卡片缓存回退 | `HelloCardCodec` 统一解码、`HelloCardRecovery.toSlideCard()` 只恢复四类卡；`HelloCardsViewModel` 从 `hello_card_cache` 读今日缓存并与 live 合并 | 缓存只作展示兜底；解析失败返回 null |

> 三项调整的实现证据与风险见 [progress.md](progress.md) §二/§三；本规划只登记范围与边界。

---

## 三、目标形态

### 3.1 四条原则（书面约定，不落框架）

1. **单一写者**：一份可变状态只允许一条写路径；读多写少用 `@Volatile` 不可变快照（copy-on-write），写侧用 `@Synchronized` / `Mutex.withLock` 串行化；需要挂起语义时用 `Mutex`。禁止「某几个方法加锁、另几个裸写」。
   - 落地样本（2026-11-13）：`SubAgentRegistry`、`AgentScheduler`（`agentSnapshot` / `stateSnapshot` + `@Synchronized` 整份替换，仲裁只提交变化项）。
2. **不可变出口**：对外只暴露 `StateFlow<T>` 或 `T` 快照；更新走 `StateFlow.update {}`；集合 / Map 一律整份替换，不就地 `put` / `remove` 后让别人遍历。
   - 落地样本：`CapabilitySnapshot`（能力出口不再交 `Capability` / `SubAgent` 实例，`start/stop` 一并删除）。
   - 边界：`CardPool.popByType` 是「读—改—写」两步，`removed` 只用于日志、不参与一致性；`clear()` 直接赋值，语义是「从现在起清空」，允许与并发 push 竞争（后写者赢）。
3. **事件单消费**：跨协程指令走 `Channel` + 单消费 `run()` 循环；`close()` 的通道关闭异常是正常退出路径，必须显式区分，不记错误。**目前只有 `RadioSession` 落地，不作为全引擎强制形态** —— 有限步循环（`ReActLoop.run()`）用局部状态是对的。
4. **取消与终态可恢复**：`ReActLoop` 与三个子 Agent 的 `runLoop` 都用 `try/finally` 保证终态必达（取消 / 异常也执行）；`AgentScheduler` 的仲裁循环用「单轮 `try/catch` + 下一秒重试」自愈，并用 `Job.invokeOnCompletion` 复位启动标志 —— **不因一次 CME 永久失效**。配置变更一律「变更即落盘」，不依赖收尾。

### 3.2 组件分类

- **A 类（已合规，只补约定与回归用例）**：`SubAgentRegistry` / `CardPool` / `TokenQuotaGate` / `RadioSession`；其中 `CardPool` 的读—改—写窗口要在约定里写明边界。
- **B 类（按债收口）**：R3 / R4 / R6 / D6-08，逐笔动作与判据见 §四。
- **C 类（引擎契约快照）**：`MasterAgent.capability()` / `capabilities` → 不可变 `CapabilitySnapshot`；统一 `Capability.stateFlow` 契约（与 R6 同一笔改）。UI 侧已在 VM 内转成快照，**不再搬注入**；只复核 `check-udf.py` 的残留白名单是否可清。**2026-11-13 已落地**：出口改 `capabilitySnapshots()` / `capabilityState()`，`start/stop` 一并删除，见 §2.18。

### 3.3 不改协议

`ChatAgentGateway` 的 6 种不可变事件 → `ChatViewModel` 唯一消费并 `_state.update { copy(...) }`；`ConfirmBridge` 的 `CompletableDeferred` 握手。**D6-08 最终是删死路径而非加锁**：全局确认链（`DialogManager.pendingConfirms` + `DialogConfirmGateAdapter`）无人调用，已整体删除，`DialogEvent.ConfirmChain` / `ConfirmStep` / `ConfirmDecision` 一并移除（2026-11-13，见 [progress.md](progress.md) §2.17）。

---

## 四、施工方式（建议：先落判据，再逐债收口）

> **与 P1 的差异**：P1 按用户 2026-11-06 决议「取消前置契约 / 门禁 / 批次设计，让规则从真实代码里长出来」。P2 是并发与取消路径改造，缺机器判据等于盲改，故建议**先落阶段 0 的测试基建**。此点待用户确认，见 §八 ②。

| 阶段 | 内容 | 每笔验证 |
|---|---|---|
| **已落（11-11）** | R5 删除步数上限（结案）；R3 前半（`try/finally` + `alwaysAllow` 即时落盘）；三项对策调整（LLM-only / 健康门 / 卡片缓存回退）；**Enrich 子 Agent 状态收口样板**（单一快照 / finally / 暂停可见）。均在工作树、未提交；Enrich 样板已跑 `ktlint` + `testCore`，其余未验证 | 见 [progress.md](progress.md) §二/§五/§2.7 |
| **0 判据基建** | **并发 / 自愈判据已落（2026-11-13）**：`AgentSchedulerTest` 新增 2 条（50 写协程 × 20 轮 + 3000 次读；`FlakyConditions` 注入单次异常），修复前 9 例 / 3 失败、修复后全绿，见 [progress.md](progress.md) §2.15。**跨轮判据也已补**（§2.16：`ReActLoopTest` 取消回调 + 摘要 / 投影纯函数）；仍缺：`AgentSchedulerTest` 全量并行偶发 flaky 未定性 | 定向 `--rerun` 全绿；`testAll` 见 §五 |
| **1 R4** | **已结案**（2026-11-13，见 [progress.md](progress.md) §2.15）：两张表改 `@Volatile` 不可变快照 + 写侧 COW 整份替换；仲裁循环单轮 `try/catch` 自愈 + Job 完成回调复位标志（比「重启循环」更稳） | 两条判据先红后绿 |
| **2 R3** | **已结案**（2026-11-13，见 [progress.md](progress.md) §2.16）：前半 `try/finally` + `alwaysAllow` 立存；后半改为**跨轮工具摘要**（用户定 B，不扩 role、不存完整轮次）—— `finally` + `NonCancellable` 落摘要，`buildHistoryMessages()` 投影成 system 提醒 | 摘要纯函数 / 历史投影 / 取消回调 3 条判据 |
| **3 R6** | **已结案**（2026-11-13，见 [progress.md](progress.md) §2.12）：改为生命周期唯一快照 `HelloRuntimeState` + `transition()` 统一写入口；刻意不用 `stateIn`，避免 `shutdown()` 取消内部 scope 后丢最后一帧终态 | `HelloSubAgentTest` 的 `stateFlow_tracksRunState_R6` 与终态纯映射用例 |
| **4 D6-08** | **已结案**（2026-11-13，见 [progress.md](progress.md) §2.17）：复核确认全局确认链是死路径（唯一适配器零构造点），整体删除 —— 不是加锁，而是让问题不存在。保留 toast / 导航 / 分享 | 全仓 `ConfirmChain / ConfirmStep / ConfirmDecision / pendingConfirms` 零命中；`compileAll` / `testAll` 全绿 |
| **5 C 类引擎快照** | **已结案**（2026-11-13，见 [progress.md](progress.md) §2.18）：`CapabilitySnapshot(name/status/detail)` + `capabilitySnapshots()` / `capabilityState()`；删 `capability()` / `capabilities` 与零调用的 `start/stop`；两个消费方跟着换 | `CapabilityStatusToolTest` 5 条；「UI 拿不到可写实例」由类型系统保证；`check-udf` 0 违规 |
| **6 收口** | 书面约定定稿并同步 `AGENTS.md`；`ktlint` / `compileAll` / `testAll` / `preflight` 全绿；逐债判定归档进 `progress.md` | 见 §五 |

**每笔护栏**：只动一类状态所有权，不夹带功能修复；iOS target 本机编译不到，`commonMain` 改动对 iOS **零机器反馈**，三端对称性靠人工 + 两个 `GraphVerificationTest`；提交信息前缀 `refactor(actor): <债/组件> — <动作>`。

---

## 五、验收与缺口

| 层级 | 命令 / 判据 |
|---|---|
| 每笔 | `./ktlint check` = 0 处 + `./gradlew-smart.bat compileAll` + 该债的专项用例 |
| 收口 | `testCore` / `testAll` / `preflight` 全绿 + `check-udf.py` 0 违规 + `git diff --stat` 复核 |
| **不套用 P1 判据** | 不要求 `XxxUiState` / `onXxx()` / 单一 VM 出口；引擎侧局部状态本就该留在 `run()` 里 |

**如实登记的缺口，不假装闭环**：

- **判据缺口**：R3 的取消收尾已有两条用例（2026-11-11：勾选即触发保存、循环中断仍复位思考态）；但 `TODO.md` 写明的剩余判据（跨轮不重复执行写工具 / R4 并发压力不抛 CME 且能自愈）**仍没有对应用例**，落在阶段 0；不补就开工等于盲改。
- **本批未验证**：工作树改动尚未跑 `ktlint` / `compileAll` / `testCore` / `testAll`；提交前必须补，不把「改完」当「验过」。
- **iOS**：本机 Windows 不编译 iOS target、无 `iosTest` 源集，`commonMain` 改动对 iOS 零机器反馈；发行前用 macOS 验。
- **CI**：`pr-check.yml` 不编译不跑测（X-01 暂缓），本机 `preflight` 是唯一守门人。
- **flaky**：`AgentSchedulerTest` 曾在 `testCore` 全量并行下偶发失败一次（单独重跑全绿），阶段 0 要定性，不把 flaky 当绿灯。
- **覆盖**：18,265 行运行时不做全覆盖；只按四条债 + 引擎快照逐点收口，其余靠每笔 review 与既有 45 个 `commonTest` 兜底。
- **P1 遗留**：R15 / R48 未结案、P1 三项发行前置未做，均不在本阶段验收范围内。

---

## 六、范围外

P1 发行前置（iOS target 编译、Android/Desktop 手动回归、设计校准）、7.5.0 / 7.6.0 的 bump 与发版通道、R5 / R7 / R9 / R11 / R12 等其它引擎正确性债（R8 已改判，见 §2.14）、R15 / R48 结案复核、X-01 CI 编译测试、注释清理、7.3 其余残余、实机池签收、`storybook/` 沙盒、ktlint 规则集调整。

---

## 七、决议与取证记录

| 日期 | 事项 |
|---|---|
| 2026-11-05 | UDF 范围定为应用所有页面；工作分 P1 页面体系 / P2 Agent 运行时两部分 |
| 2026-11-06 | 版本归属 **P1 7.5.0 / P2 7.6.0**；P2 为 actor 形态、独立规划待建；边界与形态差异见 [../7_5/plan.md](../7_5/plan.md) §1.1 |
| 2026-11-10 | `docs/7_4/residuals-7.3.md` 把 D6-08 / R3 / R4 / R6 明确归口 P2 / 7.6.0，不在 7.5 期施工 |
| 2026-11-11 | **P1 收口**（`6df3409`）；本规划建立。实测 commonMain agent **83 文件 / 18,265 行**、源集分布与并发原语计数见 §二；取证同时订正六处旧叙述（四组件 actor 形态、18,232 行、13 处注入基线、R6 判据、R3 回传口径、R6 行号坐标），见 §2.3.1 与 [../7_5/progress.md](../7_5/progress.md) §十四。同日用户决议补做 `bump: 7.5.0` 只标注提交（不发行），`release.toml` 现为 7.5.0 —— §八 ① 随之定案 |
| 2026-11-11 | **R5 结案**：用户决议「不要步数上限，有 token 上限就行」—— 步数预算相关代码 / 设置项 / 14 语言文案整体删除，`stepsUsed` 仅作观测 |
| 2026-11-11 | **对策调整 A**：对话只保留 LLM 路径 —— 删本地词表与内建意图短路，能力启停改由 `radio_*` / `enrich_*` 工具承担 |
| 2026-11-11 | **对策调整 B**：`AgentHealth` 健康门控 —— 无可用 LLM / 空库时不再拉起 Hello / 富化 / 画像，配置或曲库变化后重检 |
| 2026-11-11 | **对策调整 C**：Hello 卡片缓存回退 —— Hello 未启动时 `HelloCardsViewModel` 从 `hello_card_cache` 恢复今日卡；`HelloCardCodec` 抽出统一解码 |
| 2026-11-11 | **施工方式（事实）**：按「边改边补判据」推进 —— R3 取消 / 落盘判据已补 2 条；R4 并发 / 自愈判据仍缺，阶段 0 余项未做（**11-13 补记**：R4 两条判据已落并先红后绿，见 [progress.md](progress.md) §2.15；阶段 0 余「跨轮重复执行」判据与 flaky 定性） |
| 2026-11-11 | **子 Agent 样板**：Enrich 第一刀落成 —— 单一 `_progressState` 出口、`try/finally` 收尾、暂停 / 取消可见、`enrichConfig` 可见性；判据 `EnrichSubAgentTest` 10 条 + `testCore` 全绿。Hello / Radio 待套 |

**参考**：[7.5 期规划](../7_5/plan.md)、[7.5 收口台账](../7_5/progress.md)、[7.3 残余台账](../7_4/residuals-7.3.md)、[TODO.md](../../TODO.md) §一 引擎正确性、`scripts/check-udf.py`、`scripts/udf-inject-baseline.txt`。

---

## 八、待用户确认的决议

1. **bump 节奏**：**已定（用户 11-11）** —— P1 收口后补做一笔只标注 `bump: 7.5.0`（`release.toml` 7.4.0 → 7.5.0、`sync --write`、无 tag / 无包 / 无 push），11-10 的「bump 属发行前置」口径作废。P2 默认沿用同一形态：**开工时一笔只标注 `bump: 7.6.0`**，`date` 留空、notes 换 7.6 文案、派生点交 `sync --write`。**截至 2026-11-11，P2 的 bump 尚未做**；是否延到 P2 收口后才做，待定。
2. **施工方式**：**已按「边改边补判据」实际开工** —— R3 的取消 / 落盘判据补了 2 条，R4 的并发 / 自愈判据仍缺。是否仍把阶段 0 作为 R4 开工的前置，待确认；若沿用 P1 的轻量口径，阶段 0 余项可后置到 R4 同笔。
3. **引擎快照范围**：默认只到 `capability()` / `capabilities` 两处出口 + `AgentMonitorViewModel` 消费侧 + `Capability.stateFlow` 契约统一；是否连 `HelloCardRadioStatus.kt` 残留白名单与卡片只读投影一并复核，需定范围。
4. **与 P1 发行前置的顺序**：默认先做 P2 规划定稿、不动发行前置；**2026-11-11 已先开工 P2**，P1 的三项发行前置（iOS target 编译、Android/Desktop 手动回归、设计校准）仍待做。若要先补这三项，P2 收口顺延。
