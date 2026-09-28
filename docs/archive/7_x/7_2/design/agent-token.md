# Token 计量与窗口治理

> **本文件**：agent 体系 **token 计量 / 上下文窗口 / 日配额 / 成本可见** 的唯一设计依据。
> **回答什么**：真实用量从哪来、记到哪去、窗口怎么定、超窗怎么办、用户在设置里改的配额为什么必须生效。
> **上游**：`agent-architecture.md`（铁则 F2：每 Agent 独立 budget + 独立 transport）｜`agent.md` `agent-lifecycle.md`
> **状态**：方案**已定稿**（2026-09-19，含当日多次修订）；**D3 / D4 / D7 已定，D6 已作废，D2 候补**，D1 / D5 待确认。**未实施**
> **起因**：用户提出"token 是开发阶段被刻意忽略的部分"。核查后确认**不是精度不够，是七处断裂**（见 §1）。
> **最终目标**：**能统计各 Agent × 各端点的 token 消耗（分时或累计）**。

***

## 0. 结论先行

**把"计量"从散落的估算提升为一等公民：真值由传输层单点发出，经 `TokenMeter` 统一分发到「日配额」与「窗口占用」两个**不同**的量；窗口**固定为 64K 假设**（不做动态解析），超窗前置于拦截。**

一句话：**现在的问题不是"算得不准"，而是"没人拿到真的、拿到的人不记账、记账的人用错口径、窗口是拍脑袋定的"。**

**最终目标（用户明确，2026-09-19）**：**能统计各 Agent × 各端点的 token 消耗（分时或累计）**。这一句决定了 T1 的形态 —— **分账要维度、分时要时间，单个累加数做不到**，故 `TokenLedger` 从"可选后置"升为 **T1 必做**（见 §3.1）。

七个断层 → 四个阶段：

| 断层 | 阶段 |
|---|---|
| 真 usage 拿到了却扔掉（`LlmEvent` 没有出处）| **T1** |
| 记账只覆盖一条路径（只有 `ReActLoop`）| **T1** |
| 估算代替实测 + 口径混淆（窗口占用 vs 日消耗）| **T1** |
| **无分账维度（单个累加数）** | **T1**（`TokenLedger`）|
| 日配额设置是假控件（`val` + 默认值）| **T2** |
| 窗口按 Agent 硬编码，换小窗口模型 → **静默故障** | **T3** |
| "压缩"空转（实际是固定 6 条）+ 摘要不回注 | **T4** |

**窗口与降级（已定；2026-09-19 二次简化，取代当日早先的"用户可设上限/压缩方式"）**：

- **窗口 = 固定 64K 常量**（落 `EngineDefaults`），**全部 Agent 统一使用**；**不探测、不建内置表、不支持用户覆盖**。
- **使用前提**：用户配置的端点须支持 **≥ 64K** 上下文窗口；低于此值**不在支持范围**（这是应用对外声明的约束，不是运行时校验）。
- **上下文降级 = 内部固定策略，不对用户开放**：既无"上限"设置项，也无"压缩方式"设置项。
- **简化理由（不是偷懒）**：**OpenAI 与 DeepSeek 官方的 `/models` 都不返回窗口**（见 T3 事实核查）→ 动态获取**不可能全覆盖**。既然拿不到真值，与其做"探测 + 多级回退"却仍留空洞，不如**规定一个下限、按它消费**。
- **⚠️ 必须保留的兜底**：正因为拿不到窗口，**"≥64K"无法在配置时校验** → 超窗仍可能发生（用户填了 8K 本地模型）→ **必须让"超窗"显式可辨，不得表现为"模型判定 none"**。这条是"不支持自定义"能成立的前提。
- **64K 是"窗口假设"，不是"压缩触发线"**：实测峰值仅 1–3 万（§1.1），故常驻裁剪（`takeLast`）仍是决定基线大小的**主机制**；超窗压缩只是安全网（见 T4）。

***

## 1. 现状与断层（全部有行号）

| # | 断层 | 证据 |
|---|---|---|
| **1** | **真 usage 没有上报通道**：`OpenAiUsage(promptTokens/completionTokens/totalTokens)` DTO 已解析，但 `LlmEvent` 只有 `TextDelta/ToolCall/Failed/Completed` → 传输层无处抛出。`ReActLoop` 注释自认"未来 LlmTransport 升级后可以接真实值" | `data/network/dto/ApiDtos.kt:132`；`domain/agent/port/LlmTransport.kt`；`runtime/ReActLoop.kt:198` |
| **2** | **记账只覆盖一条路径**：`recordTokens` 全仓**只有一处调用**（Master 的 ReActLoop）→ Radio / Enrich / Hello / 报告 / 画像 / Prompt 多语言**全部不记账**。**后果不只是"数字偏低"，而是配额熔断对自己最大的消费者失明**（见下）| `grep recordTokens` → 仅 `ReActLoop.kt:123` |

**断层 2 的真实后果（2026-09-19 深核，比"数字偏低"严重得多）**：

`AgentScheduler.decideEnrichState()`（`AgentScheduler.kt:169-174`）用 `!tokenCounter.shouldStop(0.9)` 决定 **Enrich 是否暂停**。但 **Enrich 的 LLM 调用一次都不入账** → 计数器里没有 Enrich 自己的消耗 → **`quotaOk` 恒真**。结论：

> **Enrich 是全部 Agent 里最大的 token 消费者**（`targetCoverage 0.9` / `maxBatchSize 20` / `CHUNK_SPLIT_SIZE 20`，要跑遍曲库），**却是唯一被配额熔断管辖、而熔断又看不见它的那一个**。它可以一直烧，而"日配额"这道闸门对它永远开着。

**四条路径互不相同，只有第一条入账**（2026-09-19 逐一核实）：

| 调用方 | 路径 | 入账 |
|---|---|---|
| Master 对话 | `ReActLoop`（`MasterAgent.kt:1638` 全仓唯一实例）→ `LlmCallExecutor` | ✅ |
| Enrich | `contextBudget.callLlmText`（`EnrichSubAgent.kt:500`）| ❌ |
| Hello | `contextBudget.callLlmText`（`HelloSubAgent.kt:1827`，6 处收口于此）| ❌ |
| Radio 判定 | **自建 `LlmCallExecutor()` + `contextBudget.llmClient`**（`RadioSubAgent.kt:1005-1020`）—— **连 budget 的 `callLlm` 包装都绕过** | ❌ |
| 报告叙事 / 画像 / Prompt 多语言 | 各自路径 | ❌ |

> **另注**：ReActLoop 的估算**含 system prompt**（`messages` 首条即 system，`ReActLoop.kt:71-76`），但**不含工具 schema** —— 而 `tools = registry.allLlmSpecs` 每次调用都全量发出（项目文档记为 27 个原子工具）。这部分完全不在估算内，量级需在 T1 用真值量化。
| **3** | **口径混淆**：`AgentContextBudget.estimatedTokenCount` **同时**被当成"窗口占用"（`windowUsage`）和"消耗量"；而窗口占用应是 **prompt tokens**、日消耗应是 **prompt + completion** —— 两者被一个数代表 | `runtime/AgentContextBudget.kt:46/58` |
| **4** | **假控件**：设置页"日 Token 配额"滑块写进 `GlobalAgentConfig.dailyTokenQuota`，但 `GlobalTokenCounter.dailyTokenQuota` 是 **`val`**，唯一构造点**只传 timeProvider** → **永远 500K** | `settings/pages/AIScreen.kt:1284/1300`；`chat/ChatKoinModule.kt:90`；`runtime/GlobalTokenCounter.kt:23` |
| **5** | **窗口硬编码**：Enrich 32K / Radio 64K / Hello 128K，**与所选模型无关**；`AiEndpointConfig` 只有 `selectedModel: String`、**无窗口字段** | `runtime/MasterAgent.kt:616/733/967`；`setting/model/AiModels.kt:21-25` |
| **6** | **压缩空转**：`buildMessages` 只发 `takeLast(recentMessagesToKeep = 6)` → 窗口占用**天然接近不了上限** → `needsCompression`/`compressHistory` 几乎永不触发；且摘要在 `compressHistory` 里**不回注**（注释写"未来可用轻量 LLM"） | `runtime/AgentContextBudget.kt` |
| **7** | **无分账维度**：唯一的账本是 `GlobalTokenCounter` 的**单个累加数**，既无 agent 维度也无端点/模型维度、无时间线 —— **"哪个 Agent / 哪个端点在什么时候花了多少"这一问，现有结构答不了** | `runtime/GlobalTokenCounter.kt` |

**估算本身的偏差（T1 之后降级为兜底）**：`content.length * 0.7` 两处各写一遍（`AgentContextBudget` / `ReActLoop`）——英文/混合文本**高估约 2.8 倍**（英语约 4 字符/token），中文尚可；**两边都不计工具 schema**（27 个 schema 每次调用都发）；`AgentContextBudget` 还**不计 systemPrompt**。

### 1.1 判据实测：本仓 prompt 的真实量级（2026-09-19 测量）

回答"窗口该定多大"之前，先得知道**我们真正组装出多大的 prompt**。按代码自身的 0.7 口径测算（常量全部可查）：

| 场景 | 峰值 | 由什么决定 |
|---|---|---|
| 电台开播（曲库全量）| **~6,000** | `LIBRARY_TOKEN_BUDGET = 6_000` ÷ `TOKENS_PER_TRACK = 28` → 214 行上限；真机日志实测 `189/189（全量）`，在限内 |
| 电台每轮决策 | **~4,500** | `TURN_CANDIDATE_ROWS = 40` 行 + 台账/档案 + `takeLast(6)` 历史 |
| 对话典型长会话 | **~8,000** | 送模型历史仅 **30 条**（`ChatAgentGateway.buildHistory` 硬编码）+ system（画像 ≤500 字、曲库概览 ≤1,200 字）|
| 对话极端 | **~30,000** | 30 条千字长回复 + 单轮 ReAct 8 步工具回传累积 |

**结论：峰值 1–3 万 token。** 且表内数字偏保守——`0.7` 系数对英文高估约 2.8 倍，而曲库块正是英文密集，真实值更低。

**三条由此推出的结论**（本章后续决策全部基于它）：

1. **现有三个硬编码窗口（32K / 64K / 128K）是按"模型能力"填的，与真实用量差 4–10 倍** —— 它们作为"护栏"**永不触发**，这正是断层 5 静默故障的成因。
2. **16K 已覆盖电台全部场景**；对话极长时擦边 → **窗口假设定在 64K，相对峰值有 2–6 倍余量**（T3 最终取此值）。
3. **"常驻裁剪"与"超窗压缩"必须分开**：现状把压缩挂在窗口百分比上（`windowUsage ≥ 0.85`），而基线大小实际由 `takeLast` 决定 → **压缩分支几乎永不执行**。「压缩空转」（断层 6）的根因是**把安全网当成了主机制**（修法见 T4）。

**顺带记录一处口径不一致**：对话历史 UI 加载 **50** 条（`ChatViewModel.SESSION_LOAD_LIMIT`），送模型 **30** 条（`ChatAgentGateway.buildHistory` 硬编码）——两个数字写死在两个文件（T4 需收敛）。

***

## 2. 两个量必须分开（本方案的第一个决定性判断）

现状最大的概念问题：**一个数既当"窗口占用"又当"日消耗"**。它们的语义完全不同：

| 量 | 定义 | 用途 | 口径 |
|---|---|---|---|
| **窗口占用** | **最近一次请求的 prompt tokens** | 判断"这次请求塞得进吗"、触发压缩 | `prompt_tokens`（含 system + tools + history）|
| **日消耗** | **当日所有调用的 (prompt + completion) 累加** | 配额仲裁、成本可见 | `prompt_tokens + completion_tokens` |

于是：
- `AgentContextBudget` 的 `windowUsage` 应基于**实测 prompt tokens**，而不是自己累加历史；
- `GlobalTokenCounter` 记的是**累加消耗**，不是占用；
- **压缩的触发条件**应该是"**本次要发的 prompt** 逼近窗口"，而不是"历史累加值逼近窗口"—— 后者对一个"每轮重渲染"的上下文（电台！）根本无意义。

***

## 3. 目标架构：单一计量源

```
        ┌──────────────── LLM 端点（真 usage）────────────────┐
        ▼                                                     │
  LlmTransport（SSE / response 的 usage 字段）                 │
        │  emit LlmEvent.Usage(prompt, completion, cached?)   │
        ▼                                                     │
   ┌─────────┐                                                │
   │TokenMeter│ ← 进程级 single，**唯一记账口**                 │
   └────┬────┘                                                │
        ├──► GlobalTokenCounter  （**当日累加** → 配额熔断：单值、要快）
        ├──► AgentContextBudget  （该 Agent 的**窗口占用** = 实测 prompt）
        └──► TokenLedger         （**逐次明细** → 分账 / 分时：多维度）
```

**三个出口回答三个不同的问题**（「两个量必须分开」的延伸 —— 现在是三个）：

| 出口 | 形态 | 回答什么 | 谁消费 |
|---|---|---|---|
| `GlobalTokenCounter` | 单值（当日累加）| "今天还能不能继续调" | `AgentScheduler` 仲裁（`:172`）· `ReActLoop` 熔断 |
| `AgentContextBudget` | 单值（最近一次 prompt）| "这次请求塞得进吗" | T3 前置守卫 · T4 组装策略 |
| **`TokenLedger`** | **每次调用一行明细** | **"谁（agent / 端点 / 模型）在什么时候花了多少"** | **分账与分时视图（T2）** |

**为什么明细不能省**：分账要**维度**、分时要**时间**，而**累计是明细的聚合结果、反过来不成立**。只存累计等于永久放弃时间维度，且不可逆。

### 3.1 分账账本（TokenLedger）—— ✅ 已定（2026-09-19）

**目标（用户明确）**：能统计**各 Agent × 各端点**的 token 消耗，**分时**或累计均可。

**三个选项**：

| | (a) 明细表（每次调用一行）| (b) 日桶累计 `(day, agent, endpoint) → tokens` | (c) 只有全局累计一个数 |
|---|---|---|---|
| 分账（按 agent / 端点）| ✅ | ✅ | ❌ 答不了 |
| 分时（小时 / 天）| ✅ | ✅（**只能到天**）| ❌ |
| "哪一次特别贵" | ✅ | ❌ | ❌ |
| 实测 / 估算占比（S2 要）| ✅ 逐条打标 | ⚠️ 只能聚合后存比例 | ❌ |
| 可逆性 | — | 明细可聚合出桶；**桶无法还原明细** | **不可逆降级** |

**裁定：`(a) 明细表` —— 且明细永久保留（先不清理、暂不做日桶固化）。**

理由：

1. **明细是累计的超集**：累计 = `GROUP BY agent, endpoint`，反过来不成立。**只存累计是不可逆降级**，而两者成本几乎相同（同一份 Room 迁移、同样 SQLite 体积量级）。
2. **项目已有"只有分时能回答"的问题**：Enrich 是最大的 token 消费者却不受配额约束（§1 断层 2）—— 要判断"它是否趁夜间跑了一整库"，必须有时间线。**配额本身就按天滚动**（`rollDayIfNeeded`），时间是原生维度。
3. **后续阶段要明细做证据**：T3/T4 要定位"哪次调用把 prompt 撑大了"；S2 要区分实测/估算。

**明细字段**（`token_ledger` 表）：`id` · `created_at`(ms) · `agent_id` · `endpoint_host` · `model` · `prompt_tokens` · `completion_tokens` · `cached_tokens` · `measured`(bool) · `task_id`

**⚠️ 隐私与安全**：
- **只存 `endpoint_host`（如 `api.deepseek.com`），不存完整 URL、绝不存 apiKey**。完整 URL 可能含路径令牌，且"分端点"用 host 已足够。
- **这不违反"记忆不落盘"的裁定**（`agent-lifecycle.md` §5）：那条管的是**喂给模型的状态**（电台会话，会变成不可审计的影子记忆）。token 明细是**给用户看的本地诊断数据，不喂给任何 LLM、不进 `UserMemory`**。两者性质不同，不得混引。

**体积（决定"永久保留"可行性的关键数字）**：一行 ≈ 150 B。
- **常态 ~300 行/天**（对话 ~60 + 电台 ~40 + Hello ~20 + Enrich 偶发 ~150）→ **~45 KB/天 ≈ 16 MB/年**
- **极端日**（大曲库反复富化：1 万首 ÷ 20 每批 × 3 阶段 ≈ 1,500 行/次）→ 单日可达数千行

**结论：永久保留在当前量级下可行**（十 MB/年 级），故**不引入清理任务**。但**无界增长终究是风险**，故留一条后手：若将来发现实际写入量远超上述估算，再加"行数软上限（超出裁最旧）"即可 —— **属可选护栏，不阻塞 T1**。

**三条纪律**：

1. **凡发起 LLM 调用，必流经 transport**；因此**在 transport 的消费侧收口**是最省事的单点 —— 具体落在现有**两条 LLM 出口**：`LlmCallExecutor`（ReActLoop 用）与 `AgentContextBudget.callLlm/callLlmText`（Enrich / Hello 用）。两者都在 `collect` 时把 `LlmEvent.Usage` 交给 `TokenMeter`。
   - **⚠️ Radio 是第三条路径**：`RadioSubAgent.askJudge`（`:1005-1020`）**自建 `LlmCallExecutor()` 并直接取 `contextBudget.llmClient`**，绕过 budget 的 `callLlm` 包装。T1 必须把它一并收口，否则"分账"里永远缺 Radio（现状即如此）。
2. **实测优先，估算兜底**：端点不返回 usage（部分 OpenAI 兼容端点 / 流式不带 usage）时回落估算，并**打标 `measured=false`** —— 看板要能区分"实测 / 估算"，否则又回到"用假数字骗自己"。
3. **窗口占用只由实测 prompt tokens 更新**；估算仅用于**调用前预检**（还没真发出去时就必须判断塞不塞得进）。
4. **明细只写不读**：`TokenLedger` 是 append-only 事实表，所有视图（分账 / 分时）都由它聚合得出；**不得在别处再维护第二份累计**，否则又会分裂出两个真相。

**为什么不让 `AgentContextBudget` 自己去拉**：budget 是 per-Agent 实例、随 Agent start 构造。改为 **budget 主动上报、meter 只聚合**，避免引入"注册表 / 反查实例"这类易错耦合。

***

## 4. 四个阶段

### T1 · 计量打真值 + 落账本（地基，最独立）

| 件 | 动作 |
|---|---|
| `LlmEvent` | 新增 `data class Usage(promptTokens, completionTokens, cachedTokens = 0)` |
| 各端点解析 | SSE / 非流式响应把 `OpenAiUsage` 转成 `LlmEvent.Usage` 发出。**两处要补**：① 请求侧加 `stream_options.include_usage`（否则流式端不回 usage）；② `OpenAiStreamChunk`（`ApiDtos.kt:96-99`）**目前连 `usage` 字段都没有**，须补上 |
| `TokenMeter`（新） | 进程级 single；**唯一记账口**，一次调用写三处：`GlobalTokenCounter`（当日累加）· 对应 budget 的窗口占用 · **`TokenLedger` 一行明细** |
| **`TokenLedger`（新）** | Room 表 `token_ledger`（`AppDatabase` v8→**v9** + `MIGRATION_8_9`，写法照 `agent_audit_log`）：`created_at` / `agent_id` / `endpoint_host` / `model` / `prompt_tokens` / `completion_tokens` / `cached_tokens` / `measured` / `task_id`。**只存 host，不存完整 URL 与 apiKey** |
| 出口收口 | `LlmCallExecutor` + `AgentContextBudget.callLlm/callLlmText` 的消费侧调 `TokenMeter`；**并把 `RadioSubAgent.askJudge` 那条自建路径（`:1005-1020`）并入**（现状它绕过 budget 包装、完全不入账）|
| 估算 | 降级为 fallback + 打标；**取消 `AgentContextBudget` 自己累加 `estimatedTokenCount` 作为真相**（改存"最近一次实测 prompt tokens"）|

**验收**：`FakeLlmTransport` 发 `Usage` → 断言 `GlobalTokenCounter.usedToday()`、budget 窗口占用、**`token_ledger` 新增一行**三者**都记到真值**；**master / radio / enrich / hello 四条路径都入账**（现状只有 master）；不返回 usage 时落估算且 `measured=false`；断言 `token_ledger` 里**没有** apiKey 与完整 URL。

### T2 · 分账视图（主交付）；配额接线 → 候补

**本阶段做**：

| 件 | 动作 |
|---|---|
| **分账 + 分时视图** | 从 `token_ledger` 聚合三个维度：**按 Agent** / **按端点·模型** / **按时间**（小时桶 + 天桶），窗口支持"今天 / 7 天 / 30 天 / 全部"。落 `AgentMonitorScreen.TokenMonitorCard`（现只有一条进度条，升级为分账表 + 分时图）|
| **看板数据源改向** | 看板数字**改由 `token_ledger` 聚合得出，不再依赖 `GlobalTokenCounter`** —— 于是即便配额处于候补状态，分账依旧准、且覆盖全部四个 Agent |
| 实测 / 估算占比 | 明细有 `measured` 列 → 直接算比例并在图上区分 |
| 清理 / 日桶固化 | ❌ **不做**：明细永久保留（§3.1）；日桶暂无必要（明细已支持任意聚合）|
| ~~逐次调用留痕~~ | **原本列为"可选、默认关"的审计项，现由 `TokenLedger` 直接承担**（不再单列）|

**候补（用户 2026-09-19 决定："token 限额啥的也可以先放开，候补"）**：

| 件 | 动作 | 说明 |
|---|---|---|
| `GlobalTokenCounter.dailyTokenQuota` | `val` → `private var` + `updateQuota()` | 配额先不启用，故这一"假控件"暂不影响行为 |
| 热更新链路 | 复用既有 AI 配置热监听（**注意 `distinctUntilChanged`**）| 依赖上一条 |
| 熔断改读真值 | `AgentScheduler`（`:172`）/ `ReActLoop` 用真实值 | 依赖第一条 |
| ⚠️ **候补 ≠ 问题消失** | Enrich 的「熔断看不见自己」（§1 断层 2）**依然存在**，只是"闸门没启用"所以暂时无害。**一旦启用配额，必须先修它**，否则闸门照样是摆设。**这是候补项搬回时的前置条件** | |

**验收**：① 从 `token_ledger` 聚合出的"按 Agent"总和 = 各明细之和；② **四个 Agent 都出现**在分账里（现状只有 master）；③ 分时视图能看到"某小时 Enrich 突增"；④ `measured=false` 的条目在图上被标出；⑤ 断言 `token_ledger` 里**没有** apiKey 与完整 URL。

### T3 · 窗口治理（消除静默故障）

**窗口 = 单一常量**：`EngineDefaults.AGENT_CONTEXT_WINDOW = 64_000`。

| 件 | 动作 |
|---|---|
| 窗口来源 | **固定 64K**。删除三处按 Agent 硬编码（Enrich 32K / Radio 64K / Hello 128K），**全部 Agent 统一取该常量**；不做探测、不建内置表、不开放覆盖 |
| 为什么固定 | 动态获取**不可能全覆盖** —— 用户基数最大的两类官方端点（OpenAI / DeepSeek）**都不返回窗口**（下表），而 `/models` 是各家自由发挥的接口，逐家适配收益不抵复杂度 |
| 使用前提（对外声明） | 端点须支持 **≥ 64K**；**设置页须明示**（"请填写支持 ≥64K 上下文窗口的模型"），超窗报错时给出同一指引 |
| **前置守卫** | `callLlm` 组装完 messages（含 system + tools）后估算 prompt → 超 `64K × 0.9` = **57.6K** → 先按内部固定策略降级；仍超 → **拒绝发出**并返回明确"超窗"失败（**不发出必失败的请求**）|
| **降级（内部固定，不可配置）** | ① 曲库块预算 `LIBRARY_TOKEN_BUDGET`(6,000) / `MIN_LIBRARY_ROWS`(40) 按比例缩；② 历史按内部固定策略裁；③ 仍超 → 拒绝 + 走**既有本地保底**（电台 `buildLocalFallback` / `refillQueue`，富化跳过）|
| **超窗必须显式** | 失败原因要与"模型判 none"**可区分**（现状都表现为 `判定=none`）；日志带**窗口假设值** —— 该假设无法预校验，出问题时必须一眼看出是它错了 |
| 日志 | `[超窗] agent=x 需 N > 57.6K（窗口假设 64K，固定）→ 已拒绝并降级；该模型窗口可能 <64K，本应用不支持` |

**设置页不得出现**：~~上下文上限~~ · ~~压缩方式~~ —— 二者均为**内部固定**（本节 + T4）。

#### 为什么不做窗口探测（2026-09-19 事实核查，留档备问）

| 端点 | `/models` 是否带窗口 |
|---|---|
| **OpenAI 官方** | ❌ 基线 schema 只有 `id` / `object` / `created` / `owned_by`（社区为此专门维护手工窗口表）|
| **DeepSeek 官方** | ❌ 官方文档明确只有 `id` / `object` / `owned_by` |
| OpenRouter | ✅ `context_length`（模型级）+ `top_provider.context_length`（provider 级，可能更小）|
| vLLM（自建） | ✅ `max_model_len`（服务端校验用值）|
| AI/ML API 类聚合平台、AxonHub 类网关 | ✅ `info.contextLength` / `?include=all` |

**结论**：能拿到的只有少数，**最大的两家都不给** → 采用"**规定下限 + 固定假设**"，而非"动态探测 + 多级回退"。**留档目的**：此结论会被反复问到，记此以免重开评估。

**顺带留档两条事实**（将来若重启探测，须先解决）：

1. **"模型理论窗口" ≠ "服务端实际允许值"**：OpenRouter 的 `context_length` 是模型级理论值，`top_provider.context_length` 才是该 provider 的实际值；vLLM 的 `max_model_len` 是显存/量化裁剪后的生效值。**护栏必须用后者**，否则照样超窗。
2. **`/models` 返回"每模型一个窗口"的列表**，而 agent 只用其中一个 → 探测值必须**按模型名存**（且 `availableModels: List<String>` 被模型选择器与 iOS bridge `IosSettingsBridge.kt:36` 引用，不宜改类型）。

**现状备注（本决定下保持不变）**：即便某端点返回了窗口，**我们也会丢掉** —— `MultiProviderApiAdapter.kt:157` 只 `map { it.id }`，`ModelItem`（`ApiDtos.kt:149`）仅 `id` / `owned_by`。加字段是安全的（三端 `createJson()` 均 `ignoreUnknownKeys = true`），但按本决定**不加**。

**验收**：
- **正常路径**：电台 / 对话在 64K 假设下**不触发预检**（峰值 1–3 万），无降级日志；
- **超窗路径**：把窗口常量临时调到极小（如 4K）→ 出现"前置拒绝 + 本地保底"，**不出现 API 报错**，日志明确写"超窗"而非"模型不想动"；
- **不可配置**：设置页**没有**"上下文上限"与"压缩方式"两项（确认无残留控件）。

### T4 · 上下文组装策略（内部固定，不对用户开放）

**两层机制必须分开看**：

| 层 | 何时发生 | 作用 | 可配置 |
|---|---|---|---|
| **常驻裁剪** | **每一轮** | 决定基线大小（现状：`takeLast(6)`；对话侧另有 30）| ❌ 内部固定 |
| **超窗降级** | 仅当估算 prompt > **57.6K**（0.9 × 64K）| 安全网：先压；仍超则拒绝 + 本地保底 | ❌ 内部固定 |

**为什么"压缩"不再是用户设置项**（取代当日早先的 `ContextCompaction` 枚举方案）：

1. 实测峰值 1–3 万（§1.1）→ 在 64K 假设下**永不触发**。给一个永不生效的选项就是**假控件**，与"日配额"同一模式。
2. 降级是**兜底机制**，不是用户偏好 —— 用户无法判断"该压还是该拒"，把选择权给他只会制造错误配置。

**仍必须坚持的两条（保留当日结论）**：

- **"常驻裁剪"与"超窗压缩"不是一件事**：前者每轮都跑、是决定基线大小的**主机制**；后者是**安全网**。别把 `takeLast` 当成"压缩"。
- **摘要必须回注**：`compressHistory` 的摘要要作为一条 summary 消息回注（否则"压缩" = 有损丢弃）；注释里"未来可用轻量 LLM"要么落地要么删掉 —— 按 D4，**不调 LLM**。

**必须收敛的魔数**：`AgentContextBudget.recentMessagesToKeep = 6` 与 `ChatAgentGateway.buildHistory(sid, 30)` —— 同为"保留条数"却分散两处，**收敛为单一内部常量**（不上升为设置项），否则将来改一处漏一处。

**安全网必须有用例覆盖**：正常情况下超窗路径永不执行（峰值离 57.6K 很远）→ 它实际上是**未验证代码**。必须用单测直接构造超长上下文把该路径跑通，**不允许靠"真机上应该不会发生"来兜** —— 这是它和"死代码"的分别。

**验收**：① 直接构造超长上下文 → 断言走完"降级 → 仍超 → 拒绝"路径，且**摘要出现在后续 messages 里**；② 断言常驻裁剪按统一常量生效（两处消费点一致）；③ 正常 64K 场景下**不产生**降级日志。

### T5 · 成本可见（可选，后置）

模型价目表（可内置 + 用户覆盖）→ 按 `prompt/completion` 分别计价 → 看板在**已有分账维度**上叠加"≈ ¥x.xx"。
**收益**：把 token 数变成用户能理解的钱。**代价**：价目表要维护（模型改名/调价即失效）——故后置。
**注意 T5 与 T2 的关系**：T2 已经把**维度**（agent / 端点 / 时间）建好了，T5 只是**给维度加一个价格系数** —— 所以 T5 是纯增量，不返工。

***

## 5. 与既有设计的关系 / 边界

**不改**：
- **铁则 F2**（每 Agent 独立 budget + 独立 transport 物理隔离）—— 本方案只把"估算"换成"实测"、把"窗口"变成动态，**不动隔离结构**；
- `AgentScheduler` 的仲裁逻辑与阈值（`SCHEDULER_PAUSE_THRESHOLD` 0.9 / `REACT_STOP_THRESHOLD` 0.95）—— 配额搬回时只让它读到**真实**的 `usedToday()`；
- 不引入本地 tokenizer（多端一致性 + 体积代价高；实测 + 保守估算已足够）。

**必须写清（否则后人误改）**：agent 体系里**有两套并存的"上下文管理"**——
1. **历史型**（Master 对话 / Enrich）：靠 `AgentContextBudget` 的 history + 压缩；
2. **重渲染型**（**电台**）：每轮 `renderUserMessage` 全量重渲染台账 + 节目档案，**根本不依赖 history 累积**（`RadioSession.trimHistory` 只保最近 4 轮对话质感）。

因此 T4 的"窗口策略"**只对第 1 类有效**；第 2 类的窗口治理靠"控制候选池大小 / 台账条数"。**这个区别目前只存在于代码里，本文首次写明。**

**不新增设置面（2026-09-19 决定）**：窗口**固定 64K**、降级**内部固定** → `GlobalAgentConfig` **不加**任何上下文相关字段，设置页**不加**"上下文上限" / "压缩方式"。唯一与窗口有关的用户面是**两句同义声明**：设置页的"端点须支持 ≥64K"，以及**超窗报错里的同一句话**。

**严格不做**：
- ❌ 不做"按 token 实时计费拦截"（拦在调用前，用户会莫名失败）
- ❌ 不做多模态 / 图片 token 计量（当前无多模态输入）
- ❌ 不把 `dailyTokenQuota` 做成"每 Agent 独立配额"（全局口径是 Scheduler 的输入，改口径会动仲裁语义）
- ❌ **不做"每 Agent 一个上下文窗口"** —— 统一取固定 64K（§1.1 实测三个 Agent 峰值都远低于 32K，分档无依据）
- ❌ **不做窗口探测 / 内置模型表 / 端点级窗口覆盖** —— 理由与留档见 T3
- ❌ **不向用户暴露上下文上限或压缩方式** —— 理由见 T4（前者在 64K 下永不触发，后者是兜底机制而非偏好）

**关于"明细永久保留"的边界**（2026-09-19 裁定）：
- ✅ **不引入清理任务、不做日桶固化**（§3.1 估算：常态 ~16 MB/年，可接受）。
- ⚠️ 但**无界增长终究是风险**，故留一条**后手**：若实测写入量远超估算，再加"行数软上限（超出裁最旧）"。**属可选护栏，不阻塞 T1。**
- ❌ **不得因为"永久保留"就把它当长期记忆用**：`token_ledger` 是**本地诊断数据**，**不喂给任何 LLM、不进 `UserMemory`**（§3.1）。

***

## 6. 决策记录（D1–D7）

> D3 / D4 / D6 / D7 已于 2026-09-19 拍板；D1 / D2 / D5 为建议值，实施前确认即可。

| # | 决策 | 选项 | 裁定 / 建议 |
|---|---|---|---|
| **D1** | 端点不返回 usage 时 | (a) 估算兜底 + 打标 / (b) 视作 0 / (c) 报错 | **(a)**：打标是底线，否则看板真假混在一起（现状就是混的）|
| **D2** | 日配额口径 | (a) 维持 total（prompt+completion）/ (b) 输入输出分权 | ⏸ **候补**（用户 2026-09-19："token 限额先放开"）。原建议 **(a) 维持**：分权要改 Scheduler 语义，收益不抵。**注意 D2 与"配额启用"绑定，搬回时一并定** |
| **D3** | 模型窗口来源 | (a) 探测 / 内置表 / 端点覆盖 的多级取值 / (b) **固定常量** | ✅ **(b) 已定 · 2026-09-19 二次修订**：**固定 64K**。理由：OpenAI / DeepSeek 官方**都不返回窗口** → 动态获取不可能全覆盖，逐家适配收益不抵复杂度。**同日早先的"四层取值链"方案已作废**（事实核查作为留档保留在 T3）|
| **D4** | 摘要压缩是否用 LLM | (a) 只做规则化摘要回注（不调 LLM）/ (b) 用轻量 LLM 摘要 | ✅ **(a) 已定**（2026-09-19）：避免"省 token 花更多 token"的递归问题 |
| **D6** | ~~上下文上限默认值与形态~~ | ~~(a) 默认 100K + 用户可设~~ | ❌ **已作废 · 2026-09-19 二次修订**：用户裁定"**端点窗口最小 64K、agent 按它消费、上下文降级不支持自定义**" → **不设上限设置项、不设压缩方式设置项**。安全性改由"**固定假设 + 超窗显式可辨**"保证，而非"用户可调" |
| **D7** | 分账账本形态 | (a) **明细表** / (b) 日桶累计 / (c) 只有全局累计一个数 | ✅ **(a) 已定 · 2026-09-19**：**明细表 + 永久保留**（不做清理、暂不做日桶）。理由：**明细是累计的超集，只存累计不可逆**；且项目有"只有分时能回答"的问题（Enrich 何时在烧）。**只存 host、不存 apiKey/完整 URL**。体积估算见 §3.1 |
| **D5** | 实施范围 | (a) T1+T2 先做 / (b) T1–T3 / (c) 全做 | **(b) 建议**：T1 是唯一地基且最独立；**T3 已无外部依赖**（窗口是固定常量，不用等探测方案），消除静默故障的价值最高。**配额部分（原 T2 的一半）已候补** |

***

## 7. 验收剧本（汇总）

| 剧本 | 期望 |
|---|---|
| S1 真值入账 | Fake 发 usage → `GlobalTokenCounter` / budget 窗口占用 / **`token_ledger` 新增一行** 三处**都记到真值**；**master / radio / enrich / hello 四条路径都入账**（现状只有 master）|
| S2 无 usage 兜底 | 不返回 usage → 落估算且 `measured=false`，看板可见区分 |
| S3 ~~配额真生效~~ | ⏸ **候补**：配额启用时再验（设置改配额 → `remainingToday()` 与看板立即变）。**搬回前必须先修 §1 断层 2（Enrich 熔断失明）** |
| S4 超窗不炸 | 极小窗口 + 长上下文 → 前置拒绝 + 本地保底出声，**无 API 报错**，日志明确"超窗" |
| S5 压缩有回注 | 超长历史 → 触发压缩 → 摘要出现在后续 messages 中 |
| S6 电台仍出声 | 上述任一降级路径下，电台不冷场（本地保底）|
| **S7 窗口是常量、不是设置项** | 设置页**没有**"上下文上限" / "压缩方式"两项；窗口取自 `EngineDefaults` 单一常量（三处 Agent 硬编码已删）；把常量临时调到 4K → 出现前置拒绝 + 本地保底 |
| **S8 超窗可辨（最关键）** | 构造超窗 → 失败原因明确是"**超窗**（含窗口假设值 64K）"，**而非**"模型判定 none"；日志能一眼区分二者 |
| **S9 安全网被验证过** | 超窗降级路径有**单测直接覆盖**（不依赖真机复现）；常驻裁剪的两处消费点取同一常量 |
| **S10 分账维度完整** | `token_ledger` 按 Agent 聚合 → **四个 Agent 都出现**（现状只有 master）；按端点·模型聚合 → 换端点后出现两行；聚合总和 = 明细之和 |
| **S11 分时可查** | 按小时桶与天桶聚合出的序列，能看出"**某小时 Enrich 突增**"；`measured=false` 的条目在图上被标出 |
| **S12 账本不泄密** | `token_ledger` 中**没有** apiKey、没有完整 URL（只有 `endpoint_host`）|
| **S13 永久保留可承受** | 连续写入 N 天（模拟）后查询仍快；体积与 §3.1 估算同量级（若远超，则启用"行数软上限"后手）|
