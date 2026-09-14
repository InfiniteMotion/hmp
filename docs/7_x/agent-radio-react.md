# HMP W 阶段 · Radio ReAct 改造方案

> ⚠️ **过程稿**：电台的范围与验收以 `agent-radio-spec.md` 为准。
> 本文的「ReAct 自主 DJ」方向不变（ReActLoop / dj_* 工具复用），
> 但**判断阶段不暴露工具**，只输出「追加 / 替换 / 不动」（C5），且输出永不直接触达用户（C1）。

> **产出**：2026-09-13 | **分支**：`feature/agent-build`
> **上游**：`docs/7_x/agent.md`（设计总纲）、`agent-task-book.md`（M6 电台）、`agent-w-gaps.md`（缺口登记）
> **定位**：把电台从「单轮 LLM 排单器」改造为「ReAct 自主 DJ」。本文件是实现方案，完成后并入 `agent-w-gaps.md` 的 G1 / G12 条目。
> **结论先行**：架构零件**已全部备好且参数已按电台场景预设**，本改造是「装配」而非「新建体系」。

***

## 0. 设计意图的证据（为什么是 ReActLoop）

| 既有设施 / 注释                                                        | 指向                              |
| ---------------------------------------------------------------- | ------------------------------- |
| `ReActLoop` KDoc「MasterAgent 对话 / **Radio 第 3 轮 diff 仲裁共用**」，参数注明「stepBudget（Master 8，Radio 仲裁 4）」「temperature（对话 0.7，**仲裁 0.1**）」 | 第 3 轮本该是循环，参数已预设                |
| `DjTools.kt:9`「DJ Agent 专属工具集 —— 供 **RadioSubAgent ReActLoop** 使用」；`ToolNames.kt:46` 同述 | 8 个 `dj_*` 工具就是为循环准备的            |
| `ToolRegistryView.radio()` 白名单含 `"dj_"`；Master 视图不含该前缀               | 权限面已按「只给电台」切好                   |
| `AgentPolicy.radio()` 预设：`role=RADIO`、`maxLevel=NOTIFY(1)`、不配 confirmGate（后台静默）；`dj_*` 全为 `SILENT` 可通行 | 角色与护栏已就绪                        |
| `ToolResult.detail` 注释「成功时的详细补充（可选，如热结果 JSON）」                     | 结构化回传通道已预留                      |

**现状落差**：`RadioSubAgent` 类内**零使用** `toolRegistry` / `toolRegistryView`，LLM 调用写死 `LlmCallExecutor().call(tools = emptyList())` —— 8 个 `dj_*` 工具没有任何 LLM 可见，是纯备货。

***

## 1. 改造前链路

```
触发 → Master 编排 → RadioSubAgent.startRadio
                       ├─ Step 0 种子提取（seed 关键词 → nowPlaying 标签 → 全局 Top3）
                       ├─ Step 1 本地保底（多标签计分，取 24 候选）
                       ├─ Step 2 单轮 LLM：只输出 JSON [{localIndex, why}]  ← 无工具
                       └─ Step 3 本地 diffArbitration（去重 + 截 12）
                     → 返回 List<RadioTrack>（★ 无任何一步推入播放引擎）
```

问题：LLM 只能"重排本地池 + 编理由"，**不能查库、不能感知状态、不能决定队列**；能表达"为什么"却不能表达"我要哪首"。

***

## 2. 目标架构（四段式）

```
A 秒开段（本地，保留）
   种子三级兜底 → 本地保底 → 推 PLAY_BY_ID + ADD_TO_QUEUE（AGENT_INTERNAL）→ 立即可听
        │
B 自主段（ReActLoop，核心改造）
   agentPolicy = AgentPolicy.radio()
   registry    = ToolRegistry(toolRegistryView.allTools())   ← 按 radio 视图裁剪
   stepBudget  = 5、temperature = 0.1
   stopSignal  = SchedulerStopSignal（调度器暂停时挂起）
   auditLog / presenceBus / tokenCounter = 复用 Master 侧实例
   LLM 自主：dj_current_song / dj_library_stats / dj_get_top_labels（感知）
            dj_search_by_tags / dj_get_top_artists（检索）
            dj_queue_replace_next（写队列，SILENT 级）
        │
C 收口段（解析 + 状态落定）
   取最后一次 dj_queue_replace_next 的结构化结果 → currentPlaylist（含 why）
   → radioState = PLAYING → auditLog.logRadioStart
        │
D 兜底段
   ReAct 失败 / 超时 / 无端点 → 沿用本地保底；且失败不影响 A 段已在播的内容
```

***

## 3. 改造清单

| 编号  | 改动                                                                                   | 文件                                       | 预估   |
| --- | ------------------------------------------------------------------------------------ | ---------------------------------------- | ---- |
| R1  | 让 RadioSubAgent 具备策略与配额上下文：构造参数补 `policyGuard`（复用 `chatPolicyGuard`）与 `tokenCounter` | MasterAgent.startRadio / RadioSubAgent      | 0.2 |
| R2  | 构造电台专属 registry：`ToolRegistry(toolRegistryView.allTools())`（用现有视图做权限裁剪）              | RadioSubAgent                             | 0.2 |
| R3  | Step 2/3 替换为 `ReActLoop.run(...)`；删除 `enrichWithLlm` 单轮 JSON 与 `parseLlmSonglist`        | RadioSubAgent                             | 0.5 |
| R4  | `why` 数据通道（**见决策 D1**）：ReAct 内工具写入的队列如何回传给 `RadioTrack.why`                             | ToolExecutionRecord / DjTools             | 0.5 |
| R5  | `dj_queue_replace_next` schema 扩展：可选 `reasons` 参数（与 `music_ids` 同序），结果走 `ToolResult.detail`  | DjTools                                   | 0.3 |
| R6  | 本地保底重新定位：从"最终结果"退为「秒开占位 + ReAct 失败兜底」，不再参与 diff 仲裁                                    | RadioSubAgent                             | 0.3 |
| R7  | 拆分 G1：G1a 秒开推送（A 段，本方案承担）+ G1b 换序（由 ReAct 内的 `dj_queue_replace_next` 承担）               | RadioSubAgent / DjTools                   | 0.3 |
| R8  | 卡片与消息适配：BUILDING 文案细化（"AI 正在查库选歌…"）、电台卡 `why` 来源改为工具链结果                                | HelloSlideCards / MasterAgent 文案         | 0.3 |
| R9  | 单测：`FakeLlmTransport` 脚本化 tool_calls，断言循环步数 / 工具调用 / 队列一致性 / 降级不抛异常                  | shared commonTest                         | 0.5 |

***

## 4. 决策点（需拍板）

**D1 · `why` 回传通道（最关键）** —— ReAct 里队列由工具写入，`AgentResult.toolCalls` 只带 `summary`（人读文本），而 UI 需要每首的 `why`。三选一：

- **① `ToolExecutionRecord.detail`**：给 record 加 `detail` 字段、`ToolCallExecutor` 透传 `ToolResult.detail`（基础设施小改，收益是所有 Agent 通用；`detail` 语义注释里已写明"如热结果 JSON"）。**我倾向这个。**
- **② summary 内嵌 JSON**：`dj_queue_replace_next` 的 summary 直接返回可解析 JSON。改动最小，但污染人读文本。
- **③ LLM 最终文本输出 JSON**：保留现在的解析。风险是"LLM 说的"和"实际写进队列的"可能不一致。

**D2 · 是否给 `dj_queue_replace_next` 加 `reasons` 参数**：加了才能让 LLM 自己产出每首的理由（卡片 why 质量最好），代价是 schema 变复杂、LLM 可能不给或数量不齐（需容错填默认值）。

**D3 · ReAct 能否推翻秒开队列**：`dj_queue_replace_next` 是"保留当前播放曲、替换后续"的语义（不打断）。是否允许 LLM 用 `playback_control` 切歌/`SKIP_ALL` 重来？建议**不允许**——A 段已在播的曲目不许被换掉，保证"秒开"承诺。

**D4 · 电台视图是否收窄**：现白名单是 `playback_ / playlist_ / library_ / dj_`（含歌单增删等写操作）。建议收窄为 `dj_ + library_`（只读感知/检索）+ 必要的播放控制，降低 LLM 越权面（`playlist_delete` 之类对电台无意义）。

**D5 · 无 LLM 端点时的行为**：保持现状（纯本地保底）还是降级为"本地保底 + 更少理由"。建议保持现状，`enableLlm` 语义与 Hello 一致。

***

## 5. 分阶段

| 阶段           | 内容                                                                   | 预估   | 状态 |
| ------------ | -------------------------------------------------------------------- | ---- | ---- |
| **R-Phase 1**「能出声 + 工具参与」 | R1 + R2 + R3（D1 暂用方案 ② summary JSON）+ R6 + R7                       | 1 人天 | ✅ 2026-09-13 落地（D1 直接用了方案 ①，见下） |
| **R-Phase 2**「质量与结构」   | D1 切到方案 ①（record.detail）+ R5 reasons + R8 文案 + R9 单测                | 1-1.5 人天 | 🚧 R5（reasons）与 R9（单测）已随 Phase 1 完成，R8 文案待做 |
| **R-Phase 3**「决策全循环化」  | `continueRadio`（增量补歌）与 `reorder`（连跳重选）也改走 ReActLoop（stepBudget 各 3） | 1 人天 | ☐ 未开始（对应 G12） |

### Phase 1 实际落地情况（2026-09-13）

**已实现**：`RadioSubAgent` 构造新增 `policyGuard` / `tokenCounter` / `agentPolicyConfig`；`radioPolicy = AgentPolicy.radio(...)`；`radioRegistry = ToolRegistry(toolRegistryView.allTools())`；`startRadio` 四段式（秒开推送 → ReActLoop(stepBudget=5, temperature=0.1) → 收口 → 兜底）；删除 `enrichWithLlm` / `parseLlmSonglist` 与单轮 JSON 提示词；`dj_queue_replace_next` 增加可选 `reasons` 并回传 `ToolResult.detail`；`ToolExecutionRecord` 增加 `detail` 字段（ToolCallExecutor 透传）；`ToolArgs` 增加 `optionalStringList`；`MasterAgent.startRadio` 注入 `chatPolicyGuard` + `tokenCounter`。

**与方案的偏差（调整记录）**：

- **D1 用了方案 ①（`ToolExecutionRecord.detail`）而不是原计划的过渡方案 ②** —— 改动只有两处（record 加字段 + executor 透传），一次做对，省掉一次迁移。
- **D2（reasons）提前到 Phase 1** —— 不加就没有 per-track `why`，卡片质量会回退，属"落地即可用"的必要项。
- **D4（收窄视图）改用 PolicyGuard 兜** —— 未收窄白名单（电台 LLM 现在能看到 30 个工具），改由 `AgentRole.RADIO` 的 Phase1 身份门把 CONFIRM 级以上工具全部 Deny。好处是不动白名单、安全线靠硬编码红线；代价是 schema 偏大（**待调**：可收窄到 `dj_ + library_ + 必要播放控制`，减少 LLM 走弯路）。

**验证**：新增 `RadioSubAgentTest` 4 条用例（ReAct 成功写队列 / LLM 失败 / 不调工具 / 无 LLM 配置）全绿；`:shared:desktopTest` 663 用例 0 失败；`:shared-ui:testAndroidHostTest` 87 用例 0 失败；三端编译（Android assembleDebug / Desktop / iOS simulatorArm64）通过。

***

## 6. 验收标准

- **单测（可确定性验证）**：脚本化 `FakeLlmTransport` 返回 tool_calls → 断言 ① 循环步数 ≤ stepBudget ② `dj_queue_replace_next` 被调用且参数合法 ③ `currentPlaylist` 与工具写入一致 ④ LLM 失败/不调工具时不抛异常且回落本地保底 ⑤ `AgentRole.RADIO` 下 CONFIRM 级工具被拒。
- **手动（desktop）**：起电台 → 有声（A 段秒开）→ ReAct 完成后队列被替换（B/C 段）→ 电台卡 `why` 来自 LLM → 侧条与审计有记录。
- **回归**：三端编译 + `:shared:desktopTest` 全绿（当前 659 用例）。

***

## 7. 风险

| 风险                                  | 应对                                                     |
| ----------------------------------- | ------------------------------------------------------ |
| LLM 不按约定调 `dj_queue_replace_next`    | 收口段必须容错：失败即回落本地保底（D 段），不能停在空队列                        |
| stepBudget=5 不够（感知 2 + 检索 1 + 写队列 1 + 答复 1） | 首版给 5，实测后调整；预算耗尽时仍按已有工具结果收口                            |
| token 配额偏紧（radio 现注册 1500 tok/min）   | ReAct 多步会超，需实测后上调（Hello 是 500、Radio 建议 3000-5000）        |
| RADIO 角色 `maxLevel=NOTIFY` 误拒工具     | 已核：8 个 `dj_*` 全为 `SILENT`（0 ≤ 1）可通过；若收窄视图需再核 `library_*` 级别 |
| ReAct 期间用户又点了收音机卡                    | 现有 `radioLifecycleMutex` + 幂等判据已覆盖，改造不得绕过它              |

***

© 2026 Hearable Music Player | Developed by WLYB
