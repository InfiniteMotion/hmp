# HMP W 阶段 · 待补全清单

> **产出时间**：2026-09-13（`feature/agent-build` 走读产出）
> **上游**：`docs/7_x/agent.md`（设计总纲）、`agent-task-book.md`（M0-M7 任务书）、`agent-hello.md`（W0 方案）、`agent-w1.md`（页面级）、`agent-w2.md`（组件级）
> **本文件定位**：只登记**待补项 + 证据 + 验收标准**，不替代上述实施方案文档；勾掉一项时在对应行标记 ✅ 并注明提交号。
> **背景**：本分支经历过一次代码回退，造成「新调用方 + 旧被调方」混杂、编译失败。编译层已修复（见 §4），修复过程中走读代码，发现一批**「引擎已就绪、消费端缺位」**的遗留项，登记于此。

***

## 0. 总览

| 编号 | 待补项                                           | 优先级 | 归属层      | 预估   |
| -- | --------------------------------------------- | --- | -------- | ---- |
| G1 | 电台曲目从未推入播放引擎（起电台有声无实）                    | P0  | 引擎 · Radio | 0.5 人天 |
| G2 | 堆叠卡全部不可点（HomeScreen 未传回调）                  | P0  | UI · P1  | 0.5 人天 |
| G3 | 每卡停留时长未实现（设计 10/15/12s，实际全局 4s）            | P1  | UI · P1  | 0.5 人天 |
| G4 | 堆叠形态与设计不符（z 轴 overlay ↔ VerticalPager）     | P1  | UI · P1  | 1-2 人天（含决策） |
| G5 | 月度叙事卡无载体（`SlideType` 无 NARRATIVE）           | P1  | UI · P1 / P5 | 1 人天 |
| G6 | 推荐曲目接首页（双推荐页：每日 + 私人）                     | P1  | 引擎 + UI · P1 | 1 人天 | ☑ 2026-09-14 |
| G7 | HelloMemory 写端 6 字段 → 补 4 留 2（trackId 反查）     | P1  | 引擎 · Hello | 0.5 人天 | ☑ 2026-09-14 |
| G8 | `radioMessageFlow()` 降级分支每次新建 StateFlow       | P2  | 引擎 · Master | 0.2 人天 |
| G9 | 电台卡重建 key 已含 playlist（4bf5765 已修，见 §3）        | P2  | UI · 电台卡  | 0（已修复） |
| G10 | PAUSED 释放堆叠锁（BUILDING\|PLAYING 才锁）            | P2  | UI · P1  | 0.1 人天 | ☑ 2026-09-14 |
| G11 | `RadioTrigger.RESUME` 未接线（"继续电台"会重建而非恢复）     | P2  | 引擎 · Master | 0.3 人天 |
| G12 | `continueRadio()` 无调用点（续歌能力悬空）                | P2  | 引擎 · Radio | 0.5 人天 |
| G13 | HelloMemory 无单测 → 已补 HelloMemoryTest.kt          | P2  | 测试        | 0.5 人天 | ☑ 2026-09-14 |
| G14 | 注释/死列失准（ANNIVERSARY 短路、`anniversarySubject`）  | P3  | 引擎 · Hello | 0.2 人天 | ☑ 2026-09-14 |
| G15 | ENRICH_TRACKING 短按行为与其他家族不一致                 | P3  | UI · 组件   | 0.1 人天 |
| G16 | LLM 端点（本地留空，CI 构建注入，非问题）                    | 已澄清 | 配置        | —    |

> **2026-09-14 决策更新（产品口径修正）**
>
> 用户拍板，三项按「新需求」关闭，不再按原设计文档执行：
>
> - **G4 关闭**：维持 `VerticalPager` 上下轮播形态，**不改为 z 轴 overlay**。原文「堆叠形态与设计不符」的判断作废，`agent-w1.md` 的 overlay 描述已被判定为过时设计。
> - **G3 关闭**：维持全局 `AUTO_ROTATE_MS = 4000L` 统一轮播，**不做分卡时长**（GREETING 10s / RECOMMEND 15s 等不再实现）。
> - **卡片点击（G2）方案定案**：**不做悬浮弹窗**，短按直接接入播放；**长按本次不做**（手势骨架保留在代码中）。
> - **G5 叙事卡**：已实现（见下），**点击落点暂空**，待 P5 报告页改造后接线。
>
> ⚠️ 上述 G3/G4 关闭与 `agent-w1.md`、`agent-hello.md` 的原始设计存在冲突，两份设计文档的回改已于 **2026-09-14 完成**（`agent-w1.md` 区域①形态/堆叠图/时长表、`agent-hello.md` §5 枚举注释与各卡型小节标题；`agent-w2.md` 全文无 overlay 描述，无需改）。

> **2026-09-14 13:41 重新勘探更正**
>
> 重新核对 `git` 真实状态与 `4bf5765`（上一笔提交，09-11 RadioAgent 完整落地，51 文件 / +8392 −828）的实际代码，发现并更正一处误标：
>
> - **G9 误标修正**：原登记「电台卡重建 key 不含 playlist（未做）」属误判。09-13 走读当时工作树经历过一次代码回退（旧版 `HelloSlideCards` 无 `radioPlaylist` 变量），据此记为遗留项；但当前 `4bf5765` 提交中 `LaunchedEffect(radioState, currentMusic, radioPlaylist)` 已含响应式 `radioPlaylist`，G9 实为随电台卡重写一并修复，**改为已完成**。
> - **其余 G 项经代码核对与文档一致**：G1 推队列调用（`RadioSubAgent.kt:385` PLAY_BY_ID / `:389` ADD_TO_QUEUE / `:1072` REPLACE_QUEUE）确认在 `4bf5765`；G8（`MutableStateFlow(null)` 降级）、G11（`RESUME` 未路由）、G12（无调用点）、G7（6 字段仍 null）、G15（短按不一致）、G10（PAUSED 仍锁）均仍为遗留项，状态不变。
> - **当前 git 状态**：HEAD = `4bf5765`；G2/G5 代码改动仍在**工作区未提交**（与文档 §4.2 一致）；`.gitignore` 已修（`.workbuddy/` 纳入忽略）。

**依赖关系简图**

```
G1（出声）──▶ G12（续歌接线）
G2 / G3 ──▶ G4（形态决策后，时长与点击一并按新形态实现）
G6 / G7 ──▶ 卡片与推荐内容质量
G5（叙事卡）独立，可与 P5 报告页同批
```

***

## 1. P0 —— 影响功能可用性

### ☑ G1 电台曲目从未推入播放引擎 —— 2026-09-13 已落地

> 落地方式见 `agent-radio-react.md`：G1a 秒开推送（本地保底 → `PLAY_BY_ID + ADD_TO_QUEUE`，AGENT_INTERNAL）与 G1b 换序（ReAct 内 `dj_queue_replace_next` → `REPLACE_QUEUE`）均已实现，`:shared:desktopTest` 663 用例覆盖（`RadioSubAgentTest` 断言"秒开推送 + REPLACE_QUEUE 顺序 + 收口队列与工具写入一致"）。剩余：真机/desktop 手动听感验收。

- **现象**：点收音机卡（或对话说"来点电台"）后，`MasterAgent.startRadio()` 返回 12 首 `RadioTrack` 即结束；播放引擎队列仍为空，电台不会出声。
- **证据**：
  - 全仓只有 `playback_play_at` / `playback_enqueue` 两个工具与 `dj_queue_replace_next` 会发队列命令；`startRadio` 链路、`RadioCard`、`ChatViewModel`（只渲染 songlist 气泡）、`HelloSlideCards`（只读状态）均不调用；`playAt` 仅出现在 `REPLACE_QUEUE` 的实现内部。
  - `PlaybackCommandPort.REPLACE_QUEUE` 的 KDoc 写明预期：「先用本地保底队列立即开听（PLAY_BY_ID + ADD_TO_QUEUE），等 LLM enrich 跑完后 REPLACE_QUEUE 后台无缝换歌序 + 更新每首的 why 理由」。
  - `CommandSource` 的 KDoc 把「Radio 重建队列」列为 `AGENT_INTERNAL` 的典型子 Agent 内部操作。
  - `HelloSlideCards.buildRadioStatusCard` 的 BUILDING 分支注释：「本地 fallback 已经构建好并推入播放引擎，能拿到 nowPlaying 封面」。
- **判断**：以上四处书面证据反向指认此处原本应有推送逻辑，判为回退丢失。
- **修法**：在 `RadioSubAgent.startRadio` 内补两步，均以 `CommandSource.AGENT_INTERNAL` 传参（避免污染 Master 的连跳计数）：
  1. 本地保底队列得到后立即 `PLAY_BY_ID(local.first())` + `ADD_TO_QUEUE(local.drop(1))`（秒开）；
  2. `diffArbitration` 完成后 `REPLACE_QUEUE(arbitrated.map { it.musicId })`（无缝换序）。
- **验收**：起电台后 `PlaybackController.currentPlayingMusic` 有值、RADIO_STATUS 卡左半显示真实封面与曲名；desktop 手动跑一遍；`shared:desktopTest` 全绿。
- **改造方案**：见 `agent-radio-react.md`（电台 ReAct 化）。该方案落地后本条拆为 **G1a 秒开推送**（本地保底 → `PLAY_BY_ID + ADD_TO_QUEUE`）与 **G1b 换序**（由 ReAct 内的 `dj_queue_replace_next` 承担）。

### ☑ G2 堆叠卡全部不可点 —— 2026-09-14 已落地

> **实现方式**：不做悬浮弹窗，短按直接接入播放（用户决策）。`HomeScreen.kt` 新增 `onSlideCardClick` 回调，两处 `HelloSlideCardStack` 调用点（宽屏左栏 + 窄屏区域①）均已传入。按卡型分流：
>
> | 卡型 | 短按行为 |
> |------|---------|
> | RECOMMEND / FORGOTTEN | 按 `trackId` 查出 MusicInfo → 清队 → 入队 → 播放 → 进播放页 |
> | ANNIVERSARY | 同上；`PLAYLIST_CREATE` 子类型（`trackId=0`）不响应 |
> | DISCOVER | 整组 `trackIds` 入队，从第一首播起 → 进播放页 |
> | ANCHOR / RADIO_STATUS | 进播放页 |
> | GREETING / ENRICH_TRACKING / NARRATIVE | 不响应 |
>
> **注意**：`PlaylistQueueViewModel.playWith()` / `addAllToPlaylistInOrder()` 需要 `MusicInfo` 而非 `Long`，因此回调内用 `MusicRepository.getMusicInfoByIds(ids)` 先查回实体，再入队（在 `rememberCoroutineScope` 中执行）。
>
> **未做**：长按事件（手势骨架 `onLongClick` 在各家族中保留，`AppRoot` 未传回调）。
>
> **验证**：`:shared:compileKotlinDesktop` ✅ / `:shared-ui:compileKotlinDesktop` ✅ / `:shared-ui:compileAndroidMain` ✅ / `:shared-ui:compileKotlinIosSimulatorArm64` ✅ / `:shared:desktopTest` ✅

- **验收**：四类卡型的短按行为明确且不打断 Pager 手势。**剩余**：真机/桌面手动点击验收。

### ☑ G5 月度叙事卡无载体 —— 2026-09-14 已落地（点击落点留空）

> **实现方式**：新增 `SlideType.NARRATIVE` + `NarrativeContent(narrative, timeRange, generatedAt, avgDailyMinutes)` + `FamilyNarrativeCard`（紫色渐变，标题行显示时间维度 + 相对生成时间，正文最多 6 行，底部显示日均听歌时长）。数据源在 `HelloSlideCardStack` 内异步读 `MasterAgent.getReportNarrative(NarrativeTimeRange.MONTH)`，取 MONTH 维度（原设计「月度叙事卡」）；文案空白时不入卡。叙事卡插在 ANCHOR 之后、电台未激活时展示。
>
> **连带修复**：`HelloSubAgent.stringToCardContent()` 的 `when(type)` 穷尽性分支补 `NARRATIVE`（叙事卡不走卡池 DAO，直接读 `hello_report_narrative` 表）。
>
> **未做**：点击落点（原设计「进报告页」）暂空 —— `HomeScreen` 回调中 NARRATIVE 分支为 `else -> Unit`，待 P5 报告页改造后接线。

***

## 2. P1 —— 体验与正确性

### ⊘ G3 每卡停留时长 —— 2026-09-14 按新需求关闭（不实现）

- **原现状**：`SlideType` 注释标注了设计时长（GREETING 10s / RECOMMEND 15s / DISCOVER 12s / FORGOTTEN 12s / ANNIVERSARY 15s / ANCHOR·RADIO_STATUS 常驻），但 `RotatingPersistentCards` 只有全局常量 `AUTO_ROTATE_MS = 4000L`，所有卡一律 4 秒轮播。
- **决策**：**维持现状全局统一 4s，不做分卡时长。**用户确认当前时长符合需求。
- **回改**：✅ 2026-09-14 已完成——`agent-hello.md` §5 枚举注释（357–361）+ 各卡型小节标题（445/456/469/481/493）的 10/15/12s 描述已改为「统一 4s 轮播」，并加 G3 关闭注记。

### ⊘ G4 堆叠形态 —— 2026-09-14 按新需求关闭（保持现状）

- **原设计**（`agent-w1.md:44-70`）：z 轴 overlay 堆叠、**仅栈顶可见**、`push` 覆盖常驻卡、到时 `pop` 露出下一张。
- **实际实现**：`RotatingPersistentCards` 用 `VerticalPager` 上下轮播 + 右侧指示点，所有卡同时可见（缩放/透明度渐隐）。
- **决策**：**承认 VerticalPager 为最终形态，不改为 overlay。**用户确认当前形态符合需求。
- **回改**：✅ 2026-09-14 已完成——`agent-w1.md` 区域①「形态」改为 VerticalPager 描述、push/pop 堆叠图改为轮播图、时长表改统一 4s（`agent-w2.md` 全文无 overlay 描述，无需改）。

### G5 月度叙事卡 —— 见 §1「☑ G5」（2026-09-14 已落地，点击落点留空）

### ☑ G6 推荐曲目接首页（已实现 · 2026-09-14）

> 📄 完整设计规格见 [`agent-g6-recommend-design.md`](agent-g6-recommend-design.md)（含 §13 实现记录）。

**实现状态**：✅ 完成。两个同构推荐页（`Routes.Recommend.Daily` / `Private` + `RecommendListScreen`）+ `HelloSubAgent` 生成内核（`dailyRecommendList` / `privateRecommendList` StateFlow，挂 `dailyRefreshOnce`）+ 记忆复用（读侧注入 / 写侧回写）+ `HelloCardCache` 持久化（跨重启不重算）+ 区域②两入口三态接线；旧 `heartbeatList` 已删除。编译（`:shared` / `:shared-ui` desktop + `:shared:desktopTest`）通过；真机体验待验。

**目标**：首页电台卡右侧两个入口 → 两个**同构**推荐页（每日推荐 + 私人推荐），取代旧「随机1首+相似度」占位。

**设计决策（已全部对齐）**：
- 两个**同构**页：UI 结构一致（音乐列表 + 每首按语 + 顶部总概述 + 无数据兜底），实现上抽一个共用页面组件、参数化数据源与标题。
- **每日推荐**：`HelloAgent` 每日选曲 **1 首种子**，与滑动卡 `RECOMMEND` **同源**（一次选曲：滑动卡展示种子本身，二级页用同种子的 radio 扩列）；radio 式扩列产出完整列表；每天更新一次。
- **私人推荐**：以用户**收藏曲目 + 收听相关数据**为种子，radio 式扩列产出；每天更新一次（独立于每日推荐）。
- **内容三要素**：音乐列表 + 每首按语（`reasonForTrack()`，LLM 缺失有模板兜底）+ 顶部总概述（`HelloAgent` 生成的场景文案）。
- **兜底**：无数据 → 对应首页入口**不显示/不可点**，不跳转（不降级随机列表）。
- **扩列实现归属**：由 `HelloSubAgent` **内部**实现「类似 radio 的扩列」（以种子曲目标签/相似度从曲库一次性产出 N 首连贯列表），**不复用 `RadioSubAgent`**。② 两个列表生成挂入 `HelloAgent.dailyRefreshOnce` 每日流程，避免重复打 LLM。

**实现影响面**：
- `HelloSubAgent`：新增每日推荐 / 私人推荐两个列表生成，暴露 StateFlow；
- 新建共用页面组件 + `Routes` 注册 2 路由；
- `HomeScreen`：电台卡右侧两入口接两页，无数据隐藏；
- 旧 `heartbeatList` 随机逻辑废弃。

**验收**：两页展示 agent 生成的列表+按语+总述；无数据时首页入口不显示；点击可播放。

### ☑ G7 HelloMemory 写端 6 字段恒为 null —— 2026-09-14 完成（方案 A：补 4 留 2）

- **决策**：方案 A（不改模型）。`buildHelloCardCache`（`HelloSubAgent.kt:1506`）改为 `suspend`，按 `trackId` 经 `musicRepository.getMusicInfoByIds` 反查歌手名填入：
  - `recommendArtists` ← RECOMMEND.trackId 反查；
  - `forgottenArtists` ← FORGOTTEN.trackId 反查；
  - `anniversaryArtist` ← ANNIVERSARY.trackId 反查（PLAYLIST_CREATE 子类型 trackId=0 跳过）；
  - `recommendLabels` ← `RecommendContent.currentPhase.zhName()`（时段 label，**无需查库**）。
  - 反查统一经 `musicRepository?.getMusicInfoByIds(...)?.firstOrNull()?.music?.artist?.takeIf { isNotBlank() }`，repo 为 null / 查不到时优雅降级为 null。
- **显式留 null 的 2 个（各有理由，非遗漏）**：
  - `greetingMentionedArtists`：`GreetingContent` 不绑定单曲（金句/歌词/冷知识卡），无 artist 可反查；要填需改模型 + LLM 生成吐 artist（超出 G7 范围）。
  - `anniversarySubject`：G14 已判死列，全仓无读取。
- **连带修复**：`buildForgottenContext`（HelloMemory.kt:191）原本因 `forgottenArtists` 恒空整段是死代码，现随写端修复一并生效。
- **验收**：G13 单测覆盖「生成 RECOMMEND+FORGOTTEN 后，下一张卡 prompt 出现『已推歌手 / 7 天已随笔歌手』」。**本改动随 G 系列批次统一编译（尚未单独跑）。**

***

## 3. P2 / P3 —— 健壮性与细节

### ☐ G8 `radioMessageFlow()` 降级分支每次新建实例

- `MasterAgent.kt:835-837` 的 `?: MutableStateFlow(null)` 每次调用返回新对象，`collectAsState` 以实例为 key 会重复订阅。当前因只有电台激活时渲染而不触发，建议改为 Master 持有的缓存字段。

### ☑ G9 电台卡重建 key 已含 playlist —— 2026-09-14 重新勘探更正

- **更正说明**：原 09-13 走读基于一次回退后的工作树（旧版 `HelloSlideCards.kt` 无 `radioPlaylist`），误标为未做。经 09-14 重新核对当前 `4bf5765` 提交，`HelloSlideCards.kt:198` 的 `LaunchedEffect(radioState, currentMusic, radioPlaylist)` **已包含 `radioPlaylist`**，且 `radioPlaylist` 来自响应式 `radioPlaylistFlow()`（`:138-142`），队列变化时卡会正确重建，「共 N 首备选 / 下一首」不再滞后一拍。
- **结论**：G9 在 `4bf5765` 中已随电台卡重写一并修复，非遗留项。

### ☑ G10 PAUSED 释放堆叠锁 —— 2026-09-14 完成（用户决策：选项 A）

- **决策**：PAUSED 视为临时态（用户去忙别的、恢复即续播），**不再锁住堆叠**。仅 `BUILDING || PLAYING` 锁电台卡。
- **改法**：`HelloSlideCards.kt:144` 把 `radioActive = radioState !is IDLE && radioState != null` 收窄为 `radioState is BUILDING || radioState is PLAYING`。`radioActive` 同时驱动三处（`:220` permanentLock、`:202` ANCHOR 入列、`:204` 叙事卡入列），一并随判据收窄生效：
  - PAUSED 时 `permanentLock = false` → 自动轮播恢复、用户可自由滑动；
  - ANCHOR / 叙事卡重新入列（`if (!radioActive)` 分支重新打开）；
  - RADIO_STATUS 卡本身（`:213`）无条件入列，暂停时作为普通卡随轮播，点击仍走 G2「进播放页」路由。
- **已知代价**：恢复电台（"继续电台"→ PLAYING）会重新锁并 `animateScrollToPage` 拽回电台卡，略突兀但符合预期。
- **验证**：引擎层测试（`RadioSubAgentTest`/`MasterAgentRadioTest`）仅断言状态流转，不受此 UI 判据影响；UI 层无 `radioActive` 单测，建议 Desktop 手动点暂停/恢复确认手感。**本改动随 G 系列批次统一编译（尚未单独跑）。**

### ☑ G11 `RadioTrigger.RESUME` 未接线 —— 2026-09-14 完成

- **根因**：`MasterAgent.builtinIntent` 对"继续电台"类措辞经 `RadioTrigger.fromChatInput` 得到 `RESUME`，但 `isRadioIntent("继续电台") == true`（含"电台"强触发词）会先命中启动分支、走 `startRadio(seed, RESUME)` 重建而非 `resumeRadio()` 恢复。
- **修法**：在 `builtinIntent` 的电台启动分支**之前**插入 RESUME 分支——`fromChatInput(input) == RESUME` 且 `radio.queryState() is RadioState.PAUSED` 时调 `resumeRadio()` 返回"电台继续播放了"；否则返回 noop 提示"电台当前没有在暂停，不用恢复"（不重建、不误启动）。
- **验收**：聊天输入"继续电台/恢复电台/resume radio"在 PAUSED 态下正确恢复播放；非 PAUSED 态给出明确提示而非误重建。

### ☑ G12 `continueRadio()` 无调用点 —— 2026-09-14 完成（标注冗余 API）

- **澄清**：`continueRadio()` 全仓无外部调用点（仅定义 + `startRadio` 自调用 + 注释）。测试 `RadioSubAgentTest.kt:232` 与 `agent-radio-persona.md` / `agent-radio-interaction-model.md` 均说明它**已被 G1(`refillQueue`) 取代**——续歌由 `PlaybackObservation` 的 `QueueLow` 事件经 `refillQueue` 真正推进播放队列，而 `continueRadio` 只切本地镜像列表、不推真实队列。
- **处置**：给 `continueRadio()` 加 `@Deprecated`（level=WARNING）+ 注释，明确「续歌由 refillQueue 负责，此 API 仅保留供 R-Phase 3 回合化重做参考」。**不删除**（避免破坏性改动 + Room schema 影响），视 R-Phase 3 重做时一并清理。
- **结论**：G12 的"续歌能力"实际已由 G1 提供，本项从「漏接调用点」澄清为「冗余 API 已标记」。

### ☑ G13 HelloMemory 无单测 —— 2026-09-14 完成

- 新增 `shared/src/commonTest/kotlin/com/hmp/domain/agent/sub/HelloMemoryTest.kt`（3 用例，纯内存 `HelloMemory(null)`，不依赖 Room）：
  1. `recordRecommendAndForgotten_populatesCrossCardArtistCoordination` —— 记录 RECOMMEND+FORGOTTEN 后，FORGOTTEN 卡的 prompt 含「RECOMMEND 已推歌手 / 周杰伦」「最近 7 天 FORGOTTEN 已随笔歌手 / 陈奕迅」；RECOMMEND 卡的 prompt 含「FORGOTTEN 已随笔歌手 / 陈奕迅」且**不含**自身「RECOMMEND 已推歌手」（同类型去重）。
  2. `crossCardCoordinationAbsentWhenArtistsNull` —— artist 留 null 时不误吐协调行（G7 修复前的常态，需保证不崩、不误提示）。
  3. `greetingTypeDedup_recordedAndVisible` —— GREETING 类型记录后进入去重协调文本。
- **价值**：把 G7 的 artist 维度回归钉死；后续若有人再把写端字段改回 null，此测试会立刻红。**随批次统一编译运行（尚未单独跑）。**

### ☑ G14 注释/死列失准 —— 2026-09-14 完成（注释级，无结构性改动）

- `HelloSubAgent.kt:557` 注释修正：原「ANNIVERSARY 已有 `isAnniversaryQueriedToday` 短路拦截」位置模糊，改为明确「纪念日查询走 `HelloMemory.isAnniversaryQueriedToday` 独立短路（仅 `dailyRefreshOnce` 每日调一次），不依赖 `cardType` 注入」。
- `anniversarySubject` 死列：在 `HelloAgentDao.kt:77` 与 `HelloSubAgent.kt:1529` 两处加注释标注为「预留字段（G14 死列），当前写入恒为 null、全仓无读取；待实现或随 schema 演进移除」。**未删列**（删除需 Room migration v5→v6，超出 P3 低风险范围、且需决策）。

### ☑ G15 ENRICH_TRACKING 短按行为不一致 —— 2026-09-14 完成（保留 no-op 一致）

- **原问题**：`FamilyEnrichTrackingCard` 长按分支 `onTap = { onCardClick?.invoke(card) }`（短按直接触发点击），其他 7 个家族均为 `onTap = { /* 短按留给 Pager 手势 */ }`。已改为与其他家族一致，短按让给 Pager、长按触发 `onLongClick`。
- **决策（用户 2026-09-14 拍板）**：保留「no-op 一致」现状，**不做**更激进的改造（显式非交互 / 接详情落点）。理由：ENRICH_TRACKING 是 Enrich Agent 的被动进度卡，无单曲可播，no-op 短按与 GREETING/NARRATIVE 卡同款；若日后要给它真实落点（进 Enrich 进度/对话详情页），需等 P5 类导航目标就绪，属较大活、本次不收口。

### ✓ G16 LLM 端点（非问题，CI 注入）—— 2026-09-14 用户澄清

- `MasterAgent.kt:629`：`enableLlm = helloTransport != null && enrichConfig?.isConfigured == true`。本地 `gradle.properties` 的 `BUILT_IN_AI_API_KEY=` 留空**是预期状态**：GitHub Action 构建时会自动注入该密钥，无需在本地/代码里硬编码。
- **结论**：G16 不构成遗留项或阻塞点，从「待办」移除。本地若想手动验证 LLM 产出（而非兜底模板），自行临时填入端点即可，不影响 CI 正常启用。

***

## 4. 已完成

### 4.1 编译层修复（2026-09-13，已提交于 `4bf5765`）

> 10 文件 / +1045 −27。

| 项                                                              | 说明                                                                                    |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `shared/schemas/…/5.json`                                       | 原为 0 字节空文件 → KSP `JsonDecodingException`；删除后由 Room 重新导出（24471 字节）                     |
| `ToolNames.kt` / `ToolRegistry.kt`                              | 补 8 个 `dj_*` 常量 + 注册 DJ 工具（仅 `ToolRegistryView.radio()` 白名单可见，Master 视图按前缀过滤看不到）         |
| `RadioSubAgent.kt`                                              | 重建新 API：`RadioTrigger`（含 `fromChatInput`）、`startRadio(seed, trigger, chatContext)`、`pauseRadio`/`resumeRadio`、`RadioState.PAUSED`、`RadioMessage`（4 子类）+ `messageFlow`（4s 自清）、`onTrackPlayed`、`reorder(List<String>)`、`RadioTrack.artist` |
| `ControllerAgentPorts.kt`                                       | `execute` 补 `source: CommandSource`；`AGENT_INTERNAL` 吞掉 skip / trackChange 事件                 |
| 测试 Fake ×3                                                     | `ChatTestFakes.kt` / `AgentToolFakes.kt` / `MusicRepositoryBaseTest.kt` 补新接口成员             |
| `HelloSubAgent.kt` / `DjTools.kt`                               | iOS 专有 API 等价替换：`MutableMap.merge` → 手动累加、`Math.random()` → `kotlin.random.Random.nextDouble()` |

**验证结果**：`:android:app:assembleDebug` ✅ / `:desktop:app:compileKotlinDesktop` ✅ / `:shared:` + `:shared-ui:compileKotlinIosSimulatorArm64` ✅（本机首次 iOS 编译通过）/ `:shared:desktopTest` 659 用例 0 失败（含 `AppDatabaseMigrationTest` 2/2）/ `:shared-ui:testAndroidHostTest` ✅

### 4.2 G2 卡片点击接线 + G5 叙事卡（2026-09-14，未提交）

| 文件 | 改动 |
|------|------|
| `shared/.../agent/sub/SlideModels.kt` | `SlideType` 加 `NARRATIVE`；新增 `NarrativeContent` + `NarrativeTimeRange.zhName()` |
| `shared/.../agent/sub/HelloSubAgent.kt` | `stringToCardContent()` 的 `when(type)` 补 `NARRATIVE` 穷尽分支 |
| `shared-ui/.../library/pages/components/HelloSlideCards.kt` | 新增 `FamilyNarrativeCard` + `formatGeneratedAgo()` / `formatAvgMinutes()`；`FamilyDispatch` 注册 NARRATIVE；`HelloSlideCardStack` 异步读 `getReportNarrative(MONTH)` 并入卡（ANCHOR 之后） |
| `shared-ui/.../library/pages/HomeScreen.kt` | 新增 `onSlideCardClick` 按卡型分流到播放；两处 `HelloSlideCardStack` 调用点传入回调 |

**验证结果**：`:shared:compileKotlinDesktop` ✅ / `:shared-ui:compileKotlinDesktop` ✅ / `:shared-ui:compileAndroidMain` ✅ / `:shared-ui:compileKotlinIosSimulatorArm64` ✅ / `:shared:desktopTest` ✅

### 4.3 G8 + G15 两个快项清理（2026-09-14，未提交）

| 文件 | 改动 | 性质 |
|------|------|------|
| `shared/.../runtime/MasterAgent.kt` | 新增私有字段 `_emptyRadioMessageFlow`，`radioMessageFlow()` 降级分支返回缓存实例（G8） | 纯优化，零风险 |
| `shared-ui/.../components/HelloSlideCards.kt` | `FamilyEnrichTrackingCard` 短按 `onTap` 与其他 7 个家族对齐（G15） | 一致性修复 |

**决策（G15）**：保留「no-op 一致」现状，不做显式非交互/接详情落点等更激进改造（详见 G15 节）。

**验证结果**：G8 已编译通过（`shared:compileKotlinDesktop`）；G15 改动随 G2/G5 同批编译过（4.2 验证结果覆盖）。本批两处改动**尚未跑完整编译验证**（编译被中断），状态见 §4.3 末注。

***

## 5. 建议推进顺序

> 2026-09-14 更新：G1/G2/G5 已完成，G3/G4 按新需求关闭。

1. ~~**G1 + G2 + G3**~~ → G1 ✅（09-13）、G2 ✅（09-14）、G3 关闭；
2. ~~**G4 决策**~~ → 已决策：保持 VerticalPager，关闭；
3. **G6 + G7**：补齐 HelloSubAgent 的两条断线（推荐区接入 + 记忆层 artist 字段），此后 HelloSubAgent 才算呈现闭环；
4. **G13**：HelloMemory 单测（配合 G7，能把记忆层问题暴露在测试里）；
5. **G8-G12、G14-G16**：随手清理（其中 G11/G12 与电台续播相关，建议同批）；
6. **点击落点补全**：G2 已完成短按播放，G5 叙事卡点击待 P5 报告页 —— 两者可一并处理；
7. **验证**：真机/桌面手动验收（G16 已澄清：端点由 CI 注入，本地无需配置；若想本地手动看 LLM 真产出，临时填 `BUILT_IN_AI_API_KEY` 即可，否则看到的是兜底模板）。

***

© 2026 Hearable Music Player | Developed by WLYB
