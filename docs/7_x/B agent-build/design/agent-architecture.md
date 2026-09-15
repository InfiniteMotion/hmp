# HMP Agent 架构详解

> **上游**：`agent.md`（设计总纲，单一事实来源）
> **姊妹文档**：`../taskbook/README.md`（阶段推进档案）
> **状态**：v1（2026-09-15 从任务书剥离）
>
> 本文承接 Agent 体系的**规范性架构描述**——设计铁则、两层结构、各 Agent 职责与执行链路。
> 任务书只记录「做什么、做到哪」，架构细节以本文为准。
>
> **注意**：文中 Kotlin 伪代码为**设计期示意**，用于表达职责边界与调用关系；
> 实际实现已远超其简化程度（如 `MasterAgent.kt` 现为 1434 行）。
> 涉及具体行为时以 `shared/src/commonMain/kotlin/com/hmp/domain/agent/` 下源码为准。

---

## 设计铁则（实施中不可动摇）

| 编号 | 铁则 | 反对什么 |
|------|------|----------|
| F1 | **Master 是唯一大脑**：派发任务、验收结果、决定子Agent 生命周期，全部收口 Master；子Agent 只有执行权，无决策权 | 子Agent 自毁、子Agent 自己找活干、子Agent 自己判断完成 |
| F2 | **每个 Agent = 独立 LLM 实例 + 独立 AgentContextBudget**（只管自己的上下文窗口 + 历史压缩） | 全局一份 ContextBudget 分账、串行调用 |
| F3 | **全局唯一 AgentScheduler**：纯规则、零 LLM，只管「Agent 能不能跑」（pause/resume 由电量/网络/Token 日配额触发） | ContextBudget 兼管运行仲裁 |
| F4 | **Master 的派活/验收循环是轻量规则协程**，不用 LLM（LLM 用在子Agent 执行上） | Master 用 LLM 判断富化是否完成 |
| F5 | **Enrich 的 system prompt 由 Master 生成并注入**，子Agent 自己不维护 prompt 演化 | Enrich 自改 prompt、自调整策略 |
| F6 | **SubAgent 是纯无状态执行器**（除了自己的 AgentContextBudget 历史），不知道"任务是否完成"、不知道"当前是不是 WiFi" | 子Agent 持有全局状态感知 |

---


### 源码位置索引

| 组件 | 实现文件 | 行数 |
|------|----------|------|
| Master Agent（唯一大脑） | `runtime/MasterAgent.kt` | 1434 |
| ReAct 循环 | `runtime/ReActLoop.kt` | 215 |
| 工具调用执行器 | `runtime/ToolCallExecutor.kt` | 226 |
| 注册表权限视图 | `runtime/ToolRegistryView.kt` | 100 |
| 每 Agent 上下文预算 | `runtime/AgentContextBudget.kt` | 185 |
| 全局调度仲裁 | `runtime/AgentScheduler.kt` | 187 |
| 停止信号 | `runtime/StopSignal.kt` | 99 |
| Enrich SubAgent | `sub/EnrichSubAgent.kt` | 1094 |
| Hello SubAgent | `sub/HelloSubAgent.kt` | 1767 |
| Radio SubAgent | `sub/RadioSubAgent.kt` | 1732 |
| Radio 会话状态 | `sub/RadioSession.kt` | 627 |
| SubAgent 基类 | `sub/SubAgent.kt` | 84 |
| 许可与信任 | `policy/PolicyGuard.kt` `policy/TrustLedger.kt` `policy/AgentPolicy.kt` | 250 |
| 工具协议 DSL | `tool/ToolSpec.kt` | 353 |
| 工具名常量表 | `tool/ToolNames.kt` | 112 |

> 根路径：`shared/src/commonMain/kotlin/com/hmp/domain/agent/`（行数截至 2026-09-15）

---

## 架构总览（修正版）

### 两层结构

```
┌──────────────────────────────────────────────────────────────────┐
│ 第一层：Agent 内部（每个 Agent 独立）                               │
│                                                                  │
│  Master Agent                                                  │
│  ├── AgentContextBudget (maxContextTokens=128K, 对话模型)         │
│  ├── 独立 LlmTransport 实例 (windowSize=128K)                   │
│  ├── 派活/验收循环（轻量协程，不用 LLM）                            │
│  └── subAgents 注册表（Map<String, SubAgent>）                    │
│                                                                  │
│  Enrich SubAgent（T 阶段实现）                                    │
│  ├── AgentContextBudget (maxContextTokens=32K, 轻量批量模型)       │
│  ├── 独立 LlmTransport 实例 (windowSize=32K)                    │
│  ├── batchChannel（从 Master 接收批次）                            │
│  └── 执行循环（被动接收 → LLM 调用 → 写 DB → 回报进度）             │
│                                                                  │
│  Radio SubAgent（T 只预留基类，M6 填实现）                          │
│  └── ...                                                         │
│                                                                  │
│  每个 Agent 的 LLM 调用是物理并行的（Ktor async + 独立 coroutine）    │
│  一个 Agent 的 LLM 超时/爆上下文，不影响其他 Agent                    │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ 第二层：全局共享（纯规则/零 LLM）                                   │
│                                                                  │
│  AgentScheduler（全局唯一）                                        │
│  ├── 接受 SubAgent 注册（priority / onPause / onResume）           │
│  ├── 每秒循环判断电量/网络/Token 日配额                               │
│  ├── priority=1（Master）永不暂停                                   │
│  ├── priority=2（Radio）电量≥20% 或 WiFi 允许                        │
│  └── priority=3（Enrich）电量≥50% 且 WiFi 且 日配额剩10% 允许          │
│                                                                  │
│  GlobalTokenCounter（全局唯一，只记当日 Token 总消耗，供 Scheduler 用）  │
│                                                                  │
│  ToolRegistryView（给每个 SubAgent 的权限过滤视图）                    │
│  ├── Master → 27 原子工具 + SubAgent 管理工具                        │
│  ├── Enrich → library_* + song_*（不能调 playback_*/playlist_*）     │
│  └── Radio → playback_* + playlist_* + library_*（M6 定义）           │
│                                                                  │
│  共享 ToolRegistry（所有 Agent 共用同一套原子工具实现，IO 操作无需独立实例） │
│  PresenceBus（所有 Agent emit 状态变化，UI 消费）                      │
│  AgentMemory（持久化，跨会话，所有 Agent 共享但各有独立命名空间）          │
│  AuditLogPort（所有 Agent 操作都入审计）                              │
└──────────────────────────────────────────────────────────────────┘
```

### ContextBudget 两层结构对比（修正之前的混淆）

| 维度 | AgentContextBudget（每 Agent 独立） | AgentScheduler（全局唯一） |
|------|-----------------------------------|---------------------------|
| 职责 | 管自己 LLM 实例的上下文窗口（token 估算 + 历史压缩） | 管「Agent 能不能跑」（纯规则判断） |
| 是否用 LLM | 历史压缩用轻量 summary 模型（独立于主窗口） | 零 LLM |
| 触发 | 每次 LLM 调用前自动检查 | 每秒循环 |
| 跨 Agent 协调 | 不涉及 | 决定 pause/resume 回调 |
| 错误场景 | 一个 Agent 上下文爆 → 只影响它自己，压缩历史后继续 | 资源不足 → 自动暂停低优先级 Agent |

---


---

## 各 Agent 详解

### Master Agent（唯一大脑 · 升级现有 AgentOrchestrator）

| 项 | 说明 |
|----|------|
| **驱动** | LLM function-calling（用户对话）+ 轻量协程（派活/验收循环） |
| **生命周期** | 应用启动时初始化，随应用销毁；用户对话时激活 LLM，闲置时 LLM 挂起但派活循环常驻 |
| **触发源** | 用户发消息 → LLM 循环激活；富化健康度不足 → 自动派活循环启动 |
| **目标** | ① 理解用户意图 → 执行一次性任务 ② 创建/管理/验收/销毁 SubAgent ③ 把子Agent 状态翻译给用户 |
| **system prompt** | persona（知音/DJ/馆长）+ Recall 偏好画像 + 曲库概况 + 认识进度 |
| **上下文** | 用户对话历史（自己的 AgentContextBudget 管） |
| **LLM 实例** | 独立 LlmTransport（windowSize=128K，对话专用模型） |
| **能做什么** | 调 27 原子工具 + 所有 SubAgent 管理工具（enrich_start/status 等） |
| **不能做什么** | 直接执行批量富化任务（派给 Enrich）、绕过 SubAgent 管理工具直接操作 Enrich/Radio |

**Master 启动链路（伪代码）**：

```kotlin
suspend fun initialize() {
    // ① 绑定自己的 AgentContextBudget
    myBudget = AgentContextBudget(
        agentId = "master",
        maxContextTokens = 128_000,
        llmClient = LlmTransport.create(windowSize = 128_000)
    )

    // ② 向 AgentScheduler 注册自己（永不暂停）
    scheduler.registerAgent(AgentRegistration(
        agentId = "master", priority = 1,
        tokenUsagePerMin = 2_000,
        onPause = {}, onResume = {}
    ))

    // ③ 启动 AgentScheduler 仲裁循环（全局唯一）
    GlobalScope.launch { scheduler.arbitrationLoop() }

    // ④ 富化健康度检测 → 决定是否创建 Enrich
    val health = musicRepo.getEnrichHealth()
    val targetCoverage = userPrefs.getEnrichTargetCoverage() // 默认 0.9
    if (health.coverageRate < targetCoverage) {
        // 生成任务单 → 创建 Enrich → 启动派活/验收循环
        val enrichTask = EnrichTask(
            targetCoverage = targetCoverage,
            maxBatchSize = 20,
            acceptableFailureRate = 0.1
        )
        enrichAgent = createEnrichSubAgent(enrichTask)
        GlobalScope.launch { enrichTaskLoop(enrichTask) }
    }

    // ⑤ 进入用户对话循环（现有 AgentOrchestrator 的 run() 升级）
    startUserInteractionLoop()
}
```

**Master 的派活/验收循环（核心新增）**：

```kotlin
// 轻量协程循环，不用 LLM，固定节奏
private suspend fun enrichTaskLoop(task: EnrichTask) {
    while (enrichAgent != null) {
        // 【派发】决定下一批
        val nextBatch = musicRepo.getUnenrichedSongs(limit = task.maxBatchSize)
        if (nextBatch.isEmpty()) {
            // 没有新待富化的 → 检查目标是否达成
            val actualHealth = musicRepo.getEnrichHealth()
            if (actualHealth.coverageRate >= task.targetCoverage) {
                // ✅ 验收通过 → 下令销毁 Enrich
                enrichAgent!!.shutdown()
                scheduler.unregisterAgent("enrich")
                enrichAgent = null
                agentMemory.store("enrich_completed", ...) // 持久化完成记录
                return
            } else {
                // 可能之前失败了 → 重派失败批次
                val failed = musicRepo.getFailedEnrichSongs(limit = task.maxBatchSize)
                if (failed.isNotEmpty()) enrichAgent!!.assignBatch(failed)
            }
        } else {
            // 有新批次 → 派给 Enrich
            enrichAgent!!.assignBatch(nextBatch)
        }

        // 【验收】等 5s → 查 DB 实际结果（不是查 Enrich 回报）
        delay(5000)
        val results = musicRepo.getRecentEnrichResults(since = lastCheckTime)
        val successRate = results.successCount.toFloat() /
            (results.successCount + results.failureCount)

        if (results.successCount == 0 && results.failureCount == 0) {
            // Enrich 可能被 Scheduler pause 了 → 等一会儿
            delay(10000)
        } else if (successRate < task.acceptableFailureRate) {
            // ❌ 失败率太高 → 调整策略
            task.maxBatchSize = 10 // 减小批次
            enrichAgent!!.updateSystemPrompt(...) // 更新注入的 prompt
        }
        lastCheckTime = System.currentTimeMillis()
    }
}
```

**Master 持有的 SubAgent 注册表**：

```kotlin
val subAgents = mutableMapOf<String, SubAgent>()
// "enrich" → EnrichSubAgent 实例
// "radio" → null（M6 创建）
```

Master 暴露给 LLM 的 SubAgent 管理工具（Master 的 LLM 通过这些工具管理 SubAgent，LLM 不知道背后是独立运行的 Kotlin 类）：
- `enrich_start` / `enrich_pause` / `enrich_resume` / `enrich_status` / `enrich_rescan`
- `radio_start` / `radio_pause` / `radio_resume` / `radio_stop` / `radio_state` / `radio_instruction`（M6）

---

### Enrich SubAgent（纯被动执行器 · T 阶段完整实现）

| 项 | 说明 |
|----|------|
| **驱动** | LLM function-calling（自己的独立实例） |
| **生命周期** | Master 创建 → 执行 Master 派发的批次 → Master 下令 shutdown（**永不自毁**） |
| **触发源** | Master 派发批次到 batchChannel（Channel.receive()，阻塞等待） |
| **目标** | 接收 Master 派发的歌曲批次 → 调用 LLM 生成 AI 标签 → 写数据库 |
| **system prompt** | Master 注入的执行手册（无自演化逻辑） |
| **上下文** | 自己的 AgentContextBudget 历史（每批独立，不跨批膨胀） |
| **LLM 实例** | 独立 LlmTransport（windowSize=32K，轻量批量模型，省 Token） |
| **能做什么** | 调 `library_*` + `song_*` 工具（ToolRegistryView 权限过滤）、写 DB、emit PresenceBus 进度 |
| **不能做什么** | 自己找活干、判断任务是否完成、调整批次大小、暂停/恢复自己的运行（全归 Master 和 Scheduler） |

**Enrich SubAgent 内部只有**：

```kotlin
class EnrichSubAgent(
    private val contextBudget: AgentContextBudget, // 独立 LLM 实例 + 独立上下文窗口
    private val systemPrompt: String,               // Master 注入的执行手册
    private val toolRegistryView: ToolRegistryView, // 权限过滤后的工具视图（只有 library_* + song_*）
) : SubAgent() {

    private val batchChannel = Channel<EnrichBatch>(capacity = 10) // 唯一输入口

    // ===== 对外接口（只有 Master 能调，不对外暴露）=====

    /** Master 派发批次的唯一入口 */
    fun assignBatch(batch: EnrichBatch) = batchChannel.trySend(batch)

    /** Master 下令销毁的唯一入口 */
    suspend fun shutdown() {
        isActive = false
        contextBudget.releaseLlmClient()
    }

    /** Scheduler pause/resume 回调 */
    suspend fun suspendCoroutine() { // Scheduler 调用，挂起执行循环 }
    suspend fun resumeCoroutine() { // Scheduler 调用，唤醒执行循环 }

    /** Master 更新注入的 system prompt */
    fun updateSystemPrompt(newPrompt: String) { systemPrompt = newPrompt }

    // ===== 内部执行循环（极简，不做任何决策）=====

    suspend fun runLoop() {
        while (isActive) {
            // 阻塞等待 Master 派发的批次 —— 绝不主动拉活
            val batch = batchChannel.receive()

            // 调自己的独立 LLM 实例（和 Master/Radio 物理并行）
            val response = contextBudget.callLlm(
                systemPrompt = systemPrompt,
                newMessages = batch.toMessages(),
                tools = toolRegistryView.getToolDefs()
            )

            // 把结果写数据库（IO 操作，不用 LLM）
            musicRepo.saveEnrichResults(response.toolCalls)

            // 回报进度（PresenceBus 事件，Master 会从 PresenceBus 感知）
            presenceBus.emit(AgentProgress("enrich", batch.size, response.toolCalls.size))
        }
    }
}
```

**Enrich 的 system prompt 模板（Master 注入，无自演化）**：

```
你是一个音乐标签富化助手，负责给以下歌曲补充 AI 生成的标签。

执行规则：
1. 只处理 Master Agent 派发的当前批次歌曲，不要处理其他歌曲
2. 每首歌最多生成 3 个 AI 标签（风格 / 情绪 / 场景 各 1 个）
3. 标签格式：调用 song_tag_ai_add 工具写入，source="LLM"
4. 不要覆盖已有 USER source 的标签（source="USER" 永不被模型覆盖）
5. 当前批次大小上限：${maxBatchSize}

当前批次歌曲列表：
${batchSongs.toBulletList()}
```

**Enrich 的执行链路（完整，全被动）**：

```
Master 检测到 coverageRate < 目标
  │
  ├─ 生成 EnrichTask（targetCoverage=0.9, maxBatchSize=20）
  ├─ 创建 EnrichSubAgent（独立 LlmTransport + 独立 AgentContextBudget(32K)）
  ├─ 向 AgentScheduler 注册（priority=3, onPause=挂起, onResume=唤醒）
  └─ 启动自己的派活/验收协程循环

Master 派活循环第 1 轮：
  ├─ 查 DB：getUnenrichedSongs(20) → 得到批次 [Song1..Song20]
  ├─ enrichAgent.assignBatch([Song1..Song20])  ← 塞进 Enrich 的 batchChannel
  └─ 等 5s → 查 DB：getRecentEnrichResults(since=...) → 验收

Enrich 执行循环同时运行（并行）：
  ├─ batchChannel.receive() → 拿到 [Song1..Song20]
  ├─ contextBudget.callLlm(ENRICH_PROMPT, 批次消息, toolDefs) ← 独立 LLM 实例
  │   └─ LLM function-calling: song_tag_ai_add × N
  ├─ musicRepo.saveEnrichResults(toolCalls) ← 写 DB
  └─ presenceBus.emit(AgentProgress("enrich", 20, N)) ← 回报进度

Scheduler 仲裁并行进行（每秒）：
  ├─ 电量 60% + WiFi → Enrich priority=3 满足条件 → onResume() 已在跑
  └─ 突然切移动数据 + 电量掉到 48% → Enrich priority=3 不满足 → onPause() 触发 → 挂起 coroutine
      → batchChannel 里剩余批次保留，resume 后从断点继续
```

---

### Radio SubAgent（T 只预留骨架，M6 填实现）

T 阶段做的：
- `SubAgent` 基类：定义 `assignBatch()` / `shutdown()` / `suspendCoroutine()` / `resumeCoroutine()` 四个接口
- Master 的 `subAgents` 表里预留 `"radio"` 键（初始 null）
- AgentScheduler 预留 priority=2 档注释
- ToolRegistryView 预留 Radio 的权限白名单配置位

M6 阶段要做的：
- `RadioSubAgent` 继承 SubAgent 基类，实现持续编排播放队列
- 独立 system prompt（电台 DJ persona）
- 独立 AgentContextBudget（64K 窗口）
- 触发源改为 AgentSenses 事件（PlaybackChanged/Skipped/Favorited）

---


---

## 批次计划（严格串行，依赖关系不可跳过）

```
T1 基础设施重构 ──▶ T2 Master 内核改造 ──▶ T3 Enrich 实现 ──▶ T4 联调 + Radio 预留
  （拆两层结构）        （在现有chatbot上升级）   （纯被动执行器）     （全链路验证）
```

---

### T1 基础设施重构（地基）

**目标**：把当前混在一起的「全局 ContextBudget / 单例 LlmTransport」拆成两层，为 Master/SubAgent 并行铺路。

| ID | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| T1-T1 | **拆分 ContextBudget**：现有 `ContextBudget` 拆为：<br>• `AgentContextBudget`（每个 Agent 一份，绑定 LLM 实例，管自己的上下文窗口 + 历史压缩）<br>• `GlobalTokenCounter`（全局唯一，只记当日 Token 总消耗，供 Scheduler 用） | 新增 `domain/agent/runtime/AgentContextBudget.kt`<br>新增 `domain/agent/runtime/GlobalTokenCounter.kt`<br>**改造** 现有 `engine/ContextBudget.kt`（如果有的话） | `AgentContextBudget` 单测：token 估算准确、超窗口 85% 自动压缩历史、LLM 实例绑定正确 |
| T1-T2 | **重构 LlmTransport**：从单例改为可创建多实例的工厂 `LlmTransport.create(windowSize: Int, modelType: ModelType)`，每个 Agent 绑定独立实例 | **改造** `domain/agent/llm/LlmTransport.kt`（移除 companion object 单例，加工厂方法） | `LlmTransport.create()` 单测：多实例互不干扰、各用各自的 windowSize |
| T1-T3 | **新增 AgentScheduler**：全局唯一纯规则仲裁器：<br>• 接受 SubAgent 注册（priority / onPause / onResume）<br>• 每秒循环判断电量/网络/Token 日配额，触发 pause/resume<br>• priority=1（Master）永不暂停 | 新增 `domain/agent/runtime/AgentScheduler.kt` | `AgentScheduler` 单测：priority 1/2/3 各档位触发条件正确、pause/resume 回调正确、每秒循环不阻塞主线程 |
| T1-T4 | **新增 SubAgent 基类 + ToolRegistryView**：<br>• `abstract class SubAgent`：暴露 `assignBatch(batch)` / `shutdown()` / `suspendCoroutine()` / `resumeCoroutine()` 四个接口<br>• `ToolRegistryView`：权限过滤，给每个 SubAgent 的 ToolRegistry 视图（白名单过滤） | 新增 `domain/agent/sub/SubAgent.kt`<br>新增 `domain/agent/runtime/ToolRegistryView.kt` | `ToolRegistryView` 单测：Enrich 视图只能拿到 library_* + song_*，拿不到 playback_* / playlist_* |

**依赖**：无（纯重构，不碰业务逻辑）
**验证**：跑 `./gradlew :shared:test` 全绿，现有 chatbot 功能不受影响（Master 的 AgentContextBudget 先和原来的全局 ContextBudget 等价替换，功能不变）

---

### T2 Master Agent 内核改造（在现有 chatbot 上升级）

**目标**：把现有 `AgentOrchestrator`（chatbot）升级成 Master Agent——加富化健康度检测、Enrich 任务管理循环、子Agent 生命周期管理，同时**不破坏现有用户对话功能**。

| ID | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| T2-T1 | **Master 初始化重写**：在 `AgentOrchestrator.initialize()` 里加入：<br>• 创建自己的 `AgentContextBudget`（绑定 128K 对话模型的 LlmTransport 实例）<br>• 向 `AgentScheduler` 注册自己（priority=1）<br>• 启动 Scheduler 仲裁循环<br>• 新增：调用 `MusicRepository.getEnrichHealth()` 检测富化状态 | **改造** `domain/agent/orchestrator/AgentOrchestrator.kt` | 初始化日志包含 `[Master] enrich health score=XX` 和 `[Master] registered with Scheduler priority=1` |
| T2-T2 | **富化健康度 Repository 接口**：新增 4 个查询：<br>• `getEnrichHealth(): EnrichHealth`（coverageRate / unenrichedCount / lowConfidenceCount）<br>• `getUnenrichedSongs(limit)` / `getFailedEnrichSongs(limit)` / `getRecentEnrichResults(since)` | 新增接口到 `MusicRepository.kt`<br>实现到 `MusicRepositoryImpl.kt` / DAO | FakeMusicRepository 补 4 个方法；单元测试覆盖各场景（空库/全覆盖/部分覆盖） |
| T2-T3 | **派活/验收循环**：Master 内部启动一个轻量协程循环（不用 LLM）——决定批次、派给 Enrich、等 5s、查 DB 验收、达标则下令 shutdown | 新增 `AgentOrchestrator.enrichTaskLoop()` 私有方法 | 循环单测：完整派发→执行→验收→shutdown 全链路；Scheduler pause 后循环等待不崩 |
| T2-T4 | **SubAgent 注册表**：Master 持有 `val subAgents = mutableMapOf<String, SubAgent>()`，对外暴露 `master_query_sub_agents` 工具（用户问"当前后台有什么"时可回答） | **改造** `AgentOrchestrator.kt` | LLM 通过 `master_query_sub_agents` 工具能拿到当前子Agent 状态 |

**依赖**：T1 完成
**验证**：Master 启动后能检测富化健康度；现有用户对话功能正常；`./gradlew :shared:test` 全绿

---

### T3 Enrich SubAgent 实现（纯被动执行器）

**目标**：实现 `EnrichSubAgent`，严格遵守 F1-F6 铁则——**只接收 Master 派发的批次、执行、写 DB，不做任何决策**。

| ID | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| T3-T1 | **EnrichSubAgent 实现**：继承 SubAgent 基类：<br>• 构造函数接收 Master 注入的 `EnrichTask`（转换成 system prompt）<br>• 内部只有一个循环：`batchChannel.receive()` → `contextBudget.callLlm()` → 写 DB → emit PresenceBus<br>• 无任何决策逻辑 | 新增 `domain/agent/sub/EnrichSubAgent.kt` | 单元测试：batchChannel 收到批次 → LLM 被调用 → toolCalls 写 DB；Scheduler pause 后 coroutine 挂起 |
| T3-T2 | **Enrich 专属 ToolRegistryView**：配置权限白名单：只能调 `library_*` + `song_*`，拿不到 `playback_*` / `playlist_*` | **改造** T1 的 `ToolRegistryView.kt` | 权限白名单单测：`playback_play_at` 在 Enrich 视图里不可见 |
| T3-T3 | **Enrich system prompt 模板**：Master 注入的执行手册常量（无自演化逻辑） | 新增常量到 `domain/agent/sub/EnrichPrompts.kt` | prompt 注入单测：Master 传入的 EnrichTask 参数正确填入 prompt |
| T3-T4 | **Scheduler pause/resume 与 Enrich 联动**：Enrich 的 coroutine 支持外部挂起/唤醒，batchChannel 缓冲区 10 保证 pause 期间 Master 派发的批次不丢失 | EnrichSubAgent 内部 + T1 Scheduler | Scheduler 切移动数据 → Enrich coroutine 挂起；切 WiFi → 自动唤醒，从缓冲区继续 |

**依赖**：T1（SubAgent 基类 + ToolRegistryView）、T2（Master 派活逻辑）
**验证**：Master 派发一批 10 首歌 → Enrich 能收到 → 调 LLM → 写 DB → Master 验收查到结果；Scheduler pause 后 Enrich 暂停，resume 后从断点继续

---

### T4 端到端联调 + Radio 预留

**目标**：跑通 Master → Enrich 全链路，给 Radio 留好扩展位。

| ID | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| T4-T1 | **全链路集成测试**：测试完整流程：<br>1. 清空测试库 AI 标签 → 启动应用<br>2. Master 检测到覆盖率不足（如 0%）→ 创建 Enrich<br>3. Master 派第一批 20 首 → Enrich 执行 → 写 DB<br>4. Master 验收，派第二批...直到覆盖率达标<br>5. Master 下令 Enrich shutdown → 销毁实例 | 新增 `MasterEnrichIntegrationTest.kt`（commonTest） | 测试全绿；Room DB 里 `music_label` 表有 AI 源新标签；日志有 `[Master] enrich target achieved, shutdown Enrich` |
| T4-T2 | **Radio 预留扩展位**：只做骨架，不实现逻辑：<br>• Master 的 `subAgents` 表预留 `"radio"` 键（初始 null）<br>• AgentScheduler 预留 priority=2 档注释<br>• ToolRegistryView 预留 Radio 权限白名单配置位<br>• SubAgent 基类注释标注「RadioSubAgent 待 M6 实现」 | **改造** `AgentOrchestrator.kt` / `AgentScheduler.kt` / `ToolRegistryView.kt` | 代码级检查：所有预留位有 TODO 注释指向 M6 |
| T4-T3 | **现有 chatbot 回归**：验证 Master 作为对话入口的原有功能完全不受影响（用户输入 → LLM 回复 → 工具调用） | 回归现有 `AgentOrchestratorTest.kt` + 手动测试 | `AgentOrchestratorTest` 全绿；手动测试：输入"推荐一首摇滚" → Master 正常回复 |
| T4-T4 | **两层 ContextBudget 正确性验证**：Master 的 AgentContextBudget 和 Enrich 的 AgentContextBudget 独立实例化，各自管各自的窗口；Scheduler 的 GlobalTokenCounter 正确统计当日总量 | 新增 `TwoLayerContextBudgetTest.kt` | 单测：Master 的 128K 窗口压缩 → 不影响 Enrich 的 32K 窗口；Scheduler pause/resume 只影响 Enrich，不影响 Master |

**依赖**：T1 + T2 + T3 全完成
**验证**：`./gradlew :shared:test` 全绿；手动联调全链路通过；Radio 骨架不影响现有功能

---


---

## T 阶段退出条件（必须同时满足）

| 编号 | 退出条件 | 验证方式 | 状态 |
|------|----------|----------|------|
| E1 | Master 启动时自动检测富化健康度，覆盖率不足时自动创建 Enrich | 手动清空测试库 AI 标签 → 启动 → 日志有 `[Master] enrich health score=XX, creating EnrichSubAgent` | ✅ 代码层完成；**待手动冒烟**（需 LLM 端点 + 测试曲库） |
| E2 | Enrich 能接收 Master 派发的批次，执行后标签写入 DB | 查 Room `music_label` 表，有 AI 源新标签；Master 验收日志有 `[Master] enrich batch success rate=X.XX` | ✅ 代码层完成；**待手动冒烟**（需 LLM 端点 + 测试曲库） |
| E3 | Master 验收逻辑生效：覆盖率达标后下令 Enrich 销毁 | 日志有 `[Master] enrich target achieved, shutdown Enrich`；DB 里 EnrichSubAgent 实例已释放 | ✅ 代码层完成 |
| E4 | AgentScheduler 仲裁生效：移动数据 + 电量<50% 时 Enrich 暂停，WiFi 时恢复 | 切移动数据 + 电量<50% → Enrich coroutine 挂起；切 WiFi → 自动唤醒，从断点继续 | ✅ 代码层 priority 逻辑完整；**开发阶段放开额度**（无电量/网络事件 expect-actual 桥接） |
| E5 | 两层 ContextBudget 各自独立：一个 Agent 的上下文爆 → 只影响它自己 | 强制 Master 的 contextBudget 爆 128K → 自动压缩历史继续跑 → Enrich 的 32K 上下文不受影响 | ✅ 完成（AgentContextBudget + GlobalTokenCounter 两层分离） |
| E6 | 现有 chatbot 功能完全不受影响 | 手动对话测试（输入"推荐一首摇滚" → Master 调 `library_search` → 返回结果） | ✅ 完成（compile + desktopTest 全绿） |
| E7 | Radio SubAgent 基类预留完成 | 代码级检查：SubAgent 基类 + Master 的 subAgents 表 + Scheduler priority=2 + ToolRegistryView 预留位全部到位 | ✅ 完成 |
| E8 | 测试全绿 + 编译通过 | `./gradlew :shared:test` 退出码 0；`:shared-ui:compileKotlinDesktop` 通过 | ✅ 完成（2026-09-01 最后一次验证） |

> **T 阶段整体状态（2026-09-01）**：代码层 8/8 完成；E1/E2 待手动冒烟，E4 按开发阶段要求放开额度。**可进入 M6 阶段（Radio 占位 T4-T2 已完成）**。

---


## T 阶段明确不做什么（严格 scope 控制）

| 明确不做 | 理由 |
|----------|------|
| Radio SubAgent 实现 | 留到 M6，T 只预留 SubAgent 基类接口 + Scheduler priority=2 + ToolRegistryView 权限白名单 |
| AgentMemory 跨会话持久化（Master 派活/验收循环的状态） | T 阶段 Master 的派活验收循环用临时内存状态，跨会话持久化留到后续 |
| 用户可配置的富化目标覆盖率 UI | 先用 DataStore 默认值 90%，后续加设置界面 |
| iOS 端编译验证 | 当前开发环境是 Windows，iOS 端留到后续 macOS 环境验证 |
| Feedback → Recall → 推荐闭环 | M6 Radio Agent 实现时才需要，T 不做 |
| 撤销 UI（Agent 操作的可撤销） | M6 审计页 + M7 伙伴设置页做 |
| agent_budget 全局查询工具 | T 只实现 GlobalTokenCounter 数据层，agent_budget 工具 UI 留后续 |

***

