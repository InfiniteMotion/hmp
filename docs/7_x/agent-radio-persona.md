# HMP W 阶段 · RadioSubAgent 人格与工作流重设计（v2）

> ⚠️ **过程稿**：人格与工作流的定稿口径以 `agent-radio-spec.md` 为准 ——
> 「点开即播 / 持续收集播放行为 / 静默调整队列 / 关闭即停 / 只调队列」，
> 且新增 C1 决议（存在感只走 RadioCard，禁止模型生成文案）。本文的「五回合」「DJ 叙述框架」仅作背景参考。

> **产出**：2026-09-13 | **分支**：`feature/agent-build`
> **关系**：本文件**取代**「三轮协作」的叙述框架（`RadioSubAgent` 类 KDoc 与 `agent-radio-react.md` 的管道式描述），但**保留**其技术实现（ReActLoop + `dj_*` 工具 + 秒开/兜底）。产品定位仍以 `agent.md` 为准。
> **触发原因**：引入 ReAct 后，电台的能力从「一次推理的排序器」变成「能多次观察、能自我修正的代理」，「种子 → 保底 → 富化 → 仲裁」四段线性管道已经装不下真实结构。

***

## 0. 结论先行

1. **人格不是新造，而是认领**：电台 = `DefaultCompanionProfiles.DJ` 的「节目模式」，不是第四个人格。滑杆（健谈/主动/话题宽度）直接映射到电台行为参数。
2. **工作流从「管道」改为「会话」**：电台是一个长生命周期 Session，内部由**五类回合（Turn）**驱动，每回合都是同一个 ReAct 内核的实例化 —— **一个 DJ，多张任务卡**。
3. **状态必须显式化**：把现在散落在 `RadioSubAgent` 十几个 `@Volatile` 字段里的会话状态提升为 `RadioSession` 数据类 + `RadioMemory`（跨回合/跨重启），这也是 `continueRadio` / `reorder` 悬空的根因 —— 它们不是"另一个管道"，而是**同一会话的下一次回合**。

***

## 1. 为什么「三轮协作」不够了

| # | 问题                                                          | 具体表现                                                             |
| - | ----------------------------------------------------------- | ---------------------------------------------------------------- |
| 1 | **命名掩盖了结构**：三轮是"批处理管道"，暗示"每次从零构建一次完整歌单"                     | 真实结构是「秒开夹层 + 决策循环 + 兜底夹层」，循环可以跑多轮、每轮工具不同                          |
| 2 | **没有"下一次"的位置**                                              | `continueRadio()`（续歌）与 `reorder()`（连跳重选）在管道框架里无处安放 —— 于是有实现、无调用点（G12） |
| 3 | **没有记忆与连续性**                                                | 每次 `startRadio` 都从零开始：不知道刚播过什么、不知道用户跳过什么、主题无法演进                    |
| 4 | **人格漂移**                                                    | 我落地的 ReAct prompt 写「懂音乐又克制的老朋友」，与项目既有的 `DJ` 人格（热情高能）冲突            |
| 5 | **职责越界**                                                    | 电台的"衔接语"（DjBlank 15-20 字）现在由 `MasterAgent.generateDjSegue` 生成 —— 那是 DJ 的工作，不是大脑的工作 |

***

## 2. 人格定义（Persona v2）

### 2.1 人格来源：认领 `CompanionProfile`

```kotlin
// 既有资产，不新造
val persona = DefaultCompanionProfiles.DJ
// personaPrompt = "你是「DJ」，用户的听歌伙伴。热情、高能，喜欢推荐好歌、调气氛。
//                  回答干脆、有活力，擅长把曲库串成节目或电台。"
// greeting      = "今天想听什么风格？我来安排！"
```

电台是这份人格的**节目模式**（Radio Program Mode）：同一个人格，任务从"聊天"切成"主持一档节目"。

### 2.2 节目模式的三条硬约束（写进 system prompt，可断言）

1. **只能用真实的歌**：所有曲目 ID 必须来自工具返回结果，不许编造。
2. **不打断正在播的歌**：只替换"当前播放之后"的队列（`dj_queue_replace_next` 语义）。
3. **每次决策必须给理由**：每首 ≤20 字的 `why`（产品差异化，不是装饰）——没理由就不该排进队列。

### 2.3 六个可测试的行为准则

| # | 准则                                             | 由谁保证                          |
| - | ---------------------------------------------- | ----------------------------- |
| R1 | 队列里的每首都在曲库中且不重复                                | 工具层校验 + 收口去重                   |
| R2 | 用户连跳 ≥2 首 → **换风格**，而不是微调顺序                     | Reroute 回合的 prompt 硬规则 + 状态标记 |
| R3 | 一轮内不重复推过的曲目（会话级去重窗口）                           | `RadioSession.pushedIds`       |
| R4 | seed 明确时不跑偏（风格一致性优先于新奇）                        | prompt + 标签白名单                 |
| R5 | 队列剩余 ≤ 阈值时主动补歌，不等播完                            | Refill 回合触发条件                 |
| R6 | 每回合产出 ≤ `targetCount` 首，且必须附 `why`              | 收口校验（缺 why 的曲目降级为兜底文案）      |

### 2.4 滑杆 → 行为映射（复用 CompanionProfile 三滑杆）

| 滑杆              | 电台行为参数                                                  |
| --------------- | ------------------------------------------------------- |
| `talkativeness` | 衔接语频率与长度（低 = 少说话，只报歌名；高 = 每次切歌都有一句）                     |
| `proactiveness` | 主动补歌/换主题的积极度（低 = 只在你要求时换；高 = 连跳 1 次就调整）                 |
| `topicBreadth`  | 选歌风格宽度（低 = 紧扣 seed；高 = 允许跨风格延伸）                          |

> 滑杆目前是编译期常量（`CompanionProfile` 的 KDoc 已注明持久化归 B6/M7）——本设计先读常量，持久化不阻塞。

***

## 3. 工作流重设计：会话型电台（Session-based Radio）

### 3.1 三个概念

```
Session（电台会话）——一次「开启电台」到「关闭电台」的完整生命周期，带状态与记忆
   └─ Turn（回合）——一次「需要 DJ 做决策」的触发，内部跑一次 ReActLoop
        └─ Task Card（任务卡）——该回合的 prompt + 工具子集 + stepBudget（人格不变）
```

**核心原则**：人格（§2）是恒定的，变化的是任务卡。这样 ReAct 循环只有一个，规则只有一套，行为差异全部收口在 prompt 与工具子集里 —— 可测试、可调参。

### 3.2 五类回合

| 回合        | 触发                                          | 任务卡目标                        | 工具子集                       | 预算 | 产出                        |
| --------- | ------------------------------------------- | ---------------------------- | -------------------------- | -- | ------------------------- |
| **Start** | 用户点收音机卡 / 对话"来点电台"                          | 建立初始电台（先秒开，再选歌）              | 感知 + 检索 + `dj_queue_replace_next` | 5  | 队列（≤targetCount）+ 主题 + 一句思路 |
| **Refill** | 队列剩余 ≤ 阈值（建议 3 首）或播到 2/3                   | 增量补歌（延续当前主题）                 | 感知 + 检索 + 写队列              | 3  | 追加队列（不换主题）                |
| **Reroute** | 连跳 ≥2 首（`SkipDetected`）                     | 判定负反馈 → **换风格**重选             | 感知 + 检索 + 写队列              | 4  | 新队列 + 新的 why + 侧条说明       |
| **Segue** | 切歌（`trackChangeEvents`）                     | 生成 15-20 字衔接语（DJ 人格口吻）       | 无（纯文本）                     | 1  | 一句衔接语 → `NoticeAvailable` |
| **Wrap**  | `stopRadio()` / 会话超时                        | 收尾语 + 会话归档（选了哪些、跳了哪些）        | 无                          | 1  | 归档记录 + 可选收尾语              |

> **秒开仍然存在，但不再是"第一轮"** —— 它是 Start 回合里的**前置动作**（本地保底立即 `PLAY_BY_ID + ADD_TO_QUEUE`），与 DJ 的决策解耦。这样"秒开"是硬承诺，"DJ 决策"是可以慢、可以失败的软过程。

### 3.3 状态机（在现有四态上补"回合叠加态"）

```
IDLE ──Start──▶ BUILDING ──(秒开+Start回合)──▶ PLAYING ⇄ PAUSED
                  ▲                              │
                  │                              ├─ Refill  ──▶ PLAYING（追加）
                  └──── Reroute ◀── 连跳 ≥2 ─────┤
                                                 └─ Segue   ──▶ PLAYING（只发话）
IDLE ◀── Wrap ── 任意态
```

`radioState` 仍保持四态供 UI 订阅；**回合**是内部概念，通过 `RadioMessage`（已有 4 类短时消息）暴露给 UI —— 建议补 `Refilling` / `Rerouting` 两类。

### 3.4 与现有设施的映射

| 能力          | 现状                                        | 本设计                                  |
| ----------- | ----------------------------------------- | ------------------------------------ |
| ReAct 内核    | ✅ `ReActLoop`（已用于 Start）                  | 五个回合共用，仅参数不同                          |
| 权限          | ✅ `AgentPolicy.radio()`（maxLevel=NOTIFY）  | 不变                                   |
| 工具          | ✅ 8 个 `dj_*`                               | 保留 + 收窄视图（见 §5）                      |
| 事件          | ✅ `SkipDetected` / `DjBlank` / `NoticeAvailable` | 成为回合触发器                              |
| 短时消息        | ✅ `RadioMessage`（4 类，4s 自清）                | 补 Refilling / Rerouting                |
| 会话记忆        | ❌ 无（散落的 `@Volatile` 字段）                    | 新增 `RadioSession` + `RadioMemory`     |
| 衔接语         | ⚠️ 在 `MasterAgent.generateDjSegue`         | 移入 Segue 回合（人格统一）                     |
| 续歌/重排       | ⚠️ 有实现、无调用点（G12）                          | 成为 Refill / Reroute 回合                |

***

## 4. 会话状态与记忆

### 4.1 `RadioSession`（会话态，回合间共享）

| 字段                    | 说明                                        |
| --------------------- | ----------------------------------------- |
| `sessionId` / `startedAt` | 会话标识与开始时间                                 |
| `theme`               | 当前主题（seed 或 DJ 自定）+ 主题演进历史                |
| `seedLabels`          | 起始标签集合                                    |
| `pushedIds`           | 本会话已推过（含播放中）的曲目 ID —— 支撑准则 R3              |
| `playedCount` / `skippedTitles` | 已播数、最近跳过（现有字段收编）                   |
| `negativeSignals`     | 负反馈累积：被跳过的标签/歌手及计数 —— 支撑准则 R2              |
| `turnSeq` / `lastTurn` | 回合序号与最后回合类型（诊断 + 防抖）                      |
| `remainingHint`       | 队列剩余提示（供 Refill 判据）                       |

### 4.2 `RadioMemory`（跨会话，类似 `HelloMemory` 的定位）

- 复用 `HelloMemory` 已验证的模式：DAO 查询 + 内存缓存 + `buildContextForCard` 式注入。
- 承载：**最近 N 次电台的主题与 why**（避免重复选题）、**跨会话的负面偏好**（反复跳过的歌手/标签）、**固定偏好**（用户常听的风格）。
- 持久化两选一：**A** Room 新表 `radio_session`（与 5→6 迁移捆绑，可跨重启恢复电台）；**B** DataStore 键值（轻量，只存偏好不存会话）。**建议 A**：电台"接着听上次那档节目"是高价值体验。

***

## 5. 工具面调整

**保留** 8 个 `dj_*`（感知 4 + 检索 2 + 队列 2），**写操作唯一化**（只有 `dj_queue_replace_next` 能改队列）。

**建议新增 3 个**（都属 SILENT 级）：

| 工具                  | 用途                                            | 支撑   |
| ------------------- | --------------------------------------------- | ---- |
| `dj_session_state`  | 读会话状态（主题/已推/已播/负反馈）——当前 LLM 只能靠 prompt 猜   | R2/R3 |
| `dj_note_feedback`  | 把"这首被跳过了"写进 `RadioMemory` 负反馈                 | R2   |
| `dj_shift_theme`    | Reroute 回合显式换主题（并记录主题演进，避免来回横跳）                | R2   |

**视图收窄**（配合 §6 职责重划）：电台视图从 `playback_ + playlist_ + library_ + dj_` 收窄为 **`dj_ + library_`**，播放控制指令完全归 Master。这样 LLM 面从 30 个工具降到 ~19 个，减少走弯路。

***

## 6. 职责重划（RadioSubAgent vs MasterAgent）

| 职责                      | 现在                    | 重设计后                                |
| ----------------------- | --------------------- | ----------------------------------- |
| 生命周期（start/stop/pause/resume） | Master                | Master（不变）                          |
| 播放指令（PAUSE / PLAY / SKIP_ALL） | Master                | Master（不变）                          |
| 意图路由（"来点电台"、"停电台"）      | Master                | Master（不变）                          |
| 队列内容决策                  | RadioSubAgent（Start 回合） | RadioSubAgent（五回合）                  |
| **衔接语生成**               | **Master.generateDjSegue** | **RadioSubAgent（Segue 回合）**          |
| 连跳感知                    | Master 计数 → 调 reorder | Master 发事件 → RadioSubAgent 的 Reroute 回合 |
| 会话状态                    | 散落字段 / 无              | `RadioSession` + `RadioMemory`      |

一句话：**Master 管"电台在不在跑"，RadioSubAgent 管"这个 DJ 现在该说什么、该放什么"**。

***

## 7. 分阶段落地

| 阶段                | 内容                                                            | 预估   |
| ----------------- | ------------------------------------------------------------- | ---- |
| **P1 人格与任务卡**     | 认领 `CompanionProfile.DJ`；把 5 张任务卡抽成 `RadioTaskCard`（prompt + 工具子集 + stepBudget）；修掉现有 prompt 的人格冲突 | 0.5 人天 |
| **P2 会话状态**       | `RadioSession` + `RadioMemory`；Room 5→6 迁移（`radio_session` 表）；回合间状态传递 | 1 人天 |
| **P3 回合化**        | `continueRadio` → Refill、`reorder` → Reroute、衔接语移入 Segue；Master 侧对应收敛 | 1-1.5 人天 |
| **P4 工具与设置**      | `dj_session_state` / `dj_note_feedback` / `dj_shift_theme`；视图收窄；人格滑杆接设置页（依赖 B6/M7 的伙伴设置页） | 1 人天 |

***

## 8. 验收标准

- **回合级确定性测试**（沿用 `RadioSubAgentTest` 的 `FakeLlmTransport.perTurnScript` 基建）：
  - Start：秒开推送 + 队列收口 + 主题落定
  - Refill：剩余 ≤ 阈值自动触发、**不换主题**、追加而非替换
  - Reroute：连跳 ≥2 → prompt 含负反馈上下文、写入 `negativeSignals`、队列换风格
  - Segue：产出 15-20 字且**不调用任何工具**（纯文本回合）
- **人格准则断言**：R1（无编造 ID）/ R3（会话内不重复）/ R6（每首有 why）在测试里断言，而不是靠肉眼。
- **状态机测试**：`RadioSession` 的 push/played/skip 累积与去重窗口。
- **回归**：`:shared:desktopTest` 全绿 + 三端编译。
- **手动**：desktop 起电台 → 秒开有声 → DJ 决策换序 → 连跳 2 首触发换风格 → 侧条出现 DJ 口吻的说明。

***

## 9. 风险与取舍

| 风险                            | 应对                                                     |
| ----------------------------- | ------------------------------------------------------ |
| 回合增多 → LLM 调用变多（token 成本）      | Refill/Segue 用最小预算（3 / 1）；Segue 限频（talkativeness 低时直接跳过） |
| Refill 与用户手动切歌竞争               | 沿用 `radioLifecycleMutex` + 回合级防抖（`turnSeq`）            |
| `RadioSession` 持久化引入迁移风险       | 迁移测试先行（项目已有 `AppDatabaseMigrationTest` 基建）            |
| 人格滑杆未持久化 → 调整无法保存             | 本期先读编译期常量，设置页归 B6/M7，不阻塞                              |
| 视图收窄可能让某些流程失效（如电台调用播放工具）      | 分两步：先收窄到 `dj_ + library_`，若发现必需再单独放行最小播放控制子集            |

***

## 10. 需要拍板的决策点

- **DC1 人格归属**：认领既有 `DJ` 人格（建议）/ 新增第四个人格"电台 DJ"？
- **DC2 会话持久化**：Room 新表（建议，可"接着听上次那档"）/ DataStore 仅存偏好 / 本期先内存？
- **DC3 衔接语迁移**：从 Master 移到 Segue 回合（建议）/ 保持现状？
- **DC4 视图收窄**：收窄到 `dj_ + library_`（建议）/ 保持 30 工具？
- **DC5 触发阈值**：Refill 在"剩余 ≤3 首"触发 / 播到 2/3 触发？
- **DC6 负反馈时效**：跳过信号影响范围（本会话 / 24h / 长期入库）？

***

© 2026 Hearable Music Player | Developed by WLYB
