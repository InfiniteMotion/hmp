# HMP W1 · 页面级改造

> 承接 `agent.md`（设计总纲）。
> **依赖**：T + M6 + V + **W0**（引擎侧 HelloSubAgent 完整实现）。
> **核心工作**：W0 把 HelloSubAgent 和 Radio/Enrich 的 emit 事件补齐，W1 把这些事件接到页面级 UI 消费点。

***

## W0 · 引擎侧前置（页面改造无法跳过）

### W0 范围

| # | 改动 | 说明 | 详细方案 |
|---|------|------|---------|
| 1 | AgentScheduler 加 HELLO(4) 枚举 | `AgentPriority` + `decideHelloState()`（永不暂停） | `agent-hello.md §0` |
| 2 | HelloSubAgent 完整实现 | 三个工作协程 + 卡片池 + DAO + 七种卡型生成 + 报告叙事段 | **独立方案** `docs/7_x/agent-hello.md` |
| 3 | RadioSubAgent emit AgentProgress | `startRadio()` 成功后 + `stopRadio()` 时 emit `AgentProgress(agentId="radio", ...)` | `agent-hello.md §0` |
| 4 | EnrichSubAgent 统一运行态接口 | 当前只有 `getProgress()`，需暴露统一方法（或 MasterAgent 统一聚合返回），供 P4 概览仪表板按相同协议读取 Master/Enrich/Radio 三 Agent 运行态 | 本文档 P4 数据源表 |

### W0 与 W1 的边界

- **W0** 完成时：引擎侧 `cards: StateFlow<List<SlideCard>>` + `presenceBus.events` 全部就绪，UI 拿到的是可 collect 的 StateFlow
- **W1** 完成时：HomeScreen/P2/P3/P4/P5 五个页面全部 collect 上这些 StateFlow，用户看到完整的 Agent 交互体验
- **没有 W0 就没有 W1**：W1 所有 UI 改造依赖的数据源头都在 W0，不能独立推进

***

## W1 · 页面级改造清单（5 个）

| #  | 类型      | 页面                                  | 路由                              | 当前形态                                                     |
| -- | ------- | ----------------------------------- | ------------------------------- | -------------------------------------------------------- |
| P1 | 🔴 整体改造 | HomeScreen → Agent 主交互页             | `Routes.Main.Home`              | 静态问候 + hero + 心动歌单                                       |
| P2 | 🔴 整体改造 | AIScreen → 伙伴设置页                    | `Routes.AI.AI`                  | AI 服务配置三 tab + LoadMusicExtraInfo + DailyRefreshSettings |
| P3 | 🟠 重大改造 | ChatScreen 对话页增强                    | `Routes.Companion.Chat`         | 五类气泡就绪                                                   |
| P4 | 🟠 整体升级 | AuditLogScreen → AgentMonitorScreen | `Routes.Settings.AgentMonitor`  | 280 行只读 ToolExecutionRecord 列表                           |
| P5 | 🟠 整体升级 | UserUsageDataScreen → 听歌报告页         | `Routes.UserData.UserUsageData` | 纯数字图表（Overview + Taste + Ranking + PieChart）             |

***

## P1 · HomeScreen → Agent 主交互页

### 主区域

三个区域自上而下：

| # | 区域       | 形态                   | 内容                             | 备注                                  |
| - | -------- | -------------------- | ------------------------------ | ----------------------------------- |
| ① | 动态信息展示区  | 堆叠卡组件                | HelloSubAgent 管理卡片生命周期         | 竖屏 3:2 卡片；月度叙事卡融入此处；仅栈顶可见           |
| ② | 电台 + 推荐区 | 左 1:1 收音机卡 + 右侧两行歌单卡 | 📻收音机（开关）+ 🎵今日推荐歌单 + ❤️最近收藏歌单 | 收音机 1:1 正方形独立开关；右侧歌单卡（点击播放 · 箭头进详情） |
| ③ | 功能入口区    | 横向等宽卡片 Row           | 💬对话 / ⚙️配置 / 📊看板             | 固定高度                                |

**跨区域融入**：最近收藏（→ 区域②右下歌单卡）、月度叙事卡（→ 动态堆叠）
**砍掉的独立区域**：认识进度（无需独立展示）、独立问候卡 / 正在听卡（→ 动态堆叠）

### 区域① · 动态信息展示区

**形态**：z-axis 堆叠（overlay），多卡叠加，**仅栈顶可见**。HelloSubAgent 管理生命周期（push / pop / 显示时长），UI 渲染 `Stack + AnimatedVisibility`。

```
        ┌──────────────────────────────┐
top →   │ 💬 问候（10s，可展开暂停）      │ ← DjBlank → push 栈顶，覆盖常驻卡
        │ "深夜了，来一首慢慢的？"        │    到时 pop → 露出下一张
        ├──────────────────────────────┤
        │ 🎧 正在听（常驻）              │ ← duration=0，DjBlank 时被临时覆盖
        │ "深夜 23:15 · BPM 85"         │    问候卡退场后自动回到栈顶
        │ 🔽 为什么                     │
        ├──────────────────────────────┤
        │ 📻 电台运行中（常驻）          │ ← Radio 运行时在正在听下方
        │ "12 首备选 · 下首：搁浅"       │    Radio 停止 → pop
        ├──────────────────────────────┤
        │ 🎵 深夜适合听（15s）           │ ← 每日凌晨 push
        │ "上周同时段跳过 3 首快歌"       │    到时 pop
        └──────────────────────────────┘
```

**卡片生命周期**：

| 卡型          | 显示时长     | 触发源                                                    |
| ----------- | -------- | ------------------------------------------------------ |
| 正在听锚定卡      | 0（常驻）    | `PlaylistQueueViewModel.cards` 变化时刷新内容                 |
| 电台运行态       | 0（运行时常驻） | `RadioSubAgent` emit `AgentProgress` → push；stop → pop |
| 问候 + DJ 衔接语 | 10s      | DjBlank 切歌 → push（替换列表中已有旧问候卡）                         |
| 可解释推荐       | 15s      | 每日凌晨批量生成                                               |
| 歌手/风格探索     | 12s      | 每日凌晨批量生成                                               |
| 遗忘唤醒        | 12s      | 查 30/90 天未播曲目 → 有则 push                                |
| 纪念日         | 15s      | 每日凌晨扫历史 → 有则 push                                      |

**可展开暂停**：展开状态下自动暂停倒计时，收起后恢复。展开后可看推荐理由、遗忘卡的循环次数、纪念日的详细统计。

**数据模型**：

```kotlin
data class SlideCard(
    val cardId: String,           // UUID，同类型卡唯一标识
    val type: SlideType,          // GREETING / RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY / RADIO_STATUS / ANCHOR
    val content: SlideContent,    // 各类型对应 content data class
    val displayDurationMs: Long,  // 0 = 常驻
)
```

**HelloSubAgent 管理规则**：

* 栈内同类型卡永远只保留一张：新卡用 `replace(type, card)` 替换旧卡，非同类型用 `push(card)` 追加

* DjBlank 切歌 → `replace(GREETING, 问候卡)`（替换已有旧问候卡）

* `RadioSubAgent` emit `AgentProgress` → `replace(RADIO_STATUS, 电台状态卡)`；stop → `pop(RADIO_STATUS)`

* 每日凌晨 → `replace(RECOMMEND/DISCOVER/ANNIVERSARY, 新卡)` 批量重算

* 每分钟 tick → 检查时段变化 → `replace(RECOMMEND, 适合当前时段的新卡)`

* 卡片到时 → `CountDownTimer` 触发 `pop(cardId)`

### 区域② · 电台 + 推荐区

**布局**：

```
┌──────────────┬─────────────────────────┐
│              │  🎵 今日推荐              │
│  📻 收音机   │  [歌单卡 · 箭头]          │
│  🔘 ON/OFF   │                         │
│  （1:1 卡）   ├─────────────────────────┤
│              │  ❤️ 最近收藏              │
│              │  [歌单卡 · 箭头]          │
└──────────────┴─────────────────────────┘
```

**交互**：

| 元素      | 行为                                                                                          |
| ------- | ------------------------------------------------------------------------------------------- |
| 收音机开关   | OFF → ON 调 `startRadio()`；ON → OFF 调 `stopRadio()`；运行态时旁显示「自动续歌中 · 下首：{nextTrackName}」（动态值） |
| 今日推荐歌单卡 | 点击 → 播放整个歌单；箭头 → 歌单详情页                                                                      |
| 最近收藏歌单卡 | 点击 → 播放整个歌单；箭头 → 歌单详情页                                                                      |

**数据源**：

| 子块   | 数据源                                                      | 兜底                                    |
| ---- | -------------------------------------------------------- | ------------------------------------- |
| 收音机  | `MasterAgent.isRadioActive()` StateFlow                  | 恒有                                    |
| 今日推荐 | HelloSubAgent 每日生成 → 存 DAO                               | DAO 为空时旧 `RecommendationEngine` 算 1 张 |
| 最近收藏 | `MusicRepository.getRecentFavorites(limit = 30)` → 聚合为歌单 | 收藏为空时隐藏此块                             |

### 区域③ · 功能入口区

横排三张等宽卡片，与区域②歌单卡同视觉语言，固定高度。

| 卡片          | 路由                             | 目标页面                      |
| ----------- | ------------------------------ | ------------------------- |
| 💬 和伙伴聊聊    | `Routes.Companion.Chat`        | P3 ChatScreen 对话页         |
| ⚙️ Agent 配置 | `Routes.AI.AI`                 | P2 AIScreen 伙伴设置页         |
| 📊 Agent 看板 | `Routes.Settings.AgentMonitor` | P4 AgentMonitorScreen 监控页 |

***

## P2 · AIScreen → 伙伴设置页

### 整体结构

一个 SegmentedControl 五 Tab 切换：`[全局] [Master] [Hello] [Enrich] [Radio]`，每个 Tab 独立表单。

### 配置项

| Tab                | 配置项                                                                                       |
| ------------------ | ----------------------------------------------------------------------------------------- |
| **全局**             | AI 接入方式（Free/Custom/Paid · 现有三 tab）、日 Token 配额滑杆、回复语言下拉、嗓音与耳朵（M7 gate）、清除所有推荐缓存           |
| **MasterAgent**    | 信任档位（三档+回拨）、alwaysAllow 重置、最大对话步数（3-15）、Temperature（0-2）                                  |
| **HelloSubAgent**  | 人格预设（知音/DJ/馆长）、健谈度/主动度/话题宽度滑杆、称呼习惯、每日推荐卡数量、问候卡时长、堆叠卡池上限、报告叙事生成频率（自动/每日/每周/每月）、Temperature |
| **EnrichSubAgent** | 信任档位（三档+回拨）、alwaysAllow 重置、批次大小、大歌手阈值、小歌手阈值、富化开关、Temperature                              |
| **RadioSubAgent**  | 信任档位（三档+回拨）、自动续歌开关、续歌模式（DJ\_BLANK/SILENT）、备选曲目数量、Temperature                              |

### 移除的旧组件

| 组件                   | 原因                             | 迁移去向       |
| -------------------- | ------------------------------ | ---------- |
| LoadMusicExtraInfo   | 富化进度 / 控制全归 AgentMonitorScreen | P4 概览仪表板   |
| DailyRefreshSettings | HelloSubAgent 自行定时，无需用户配置      | Agent 引擎内部 |

***

## P3 · ChatScreen 对话页增强

### 改动清单

| # | 改动                              | 实现方向                                                                                                                                               |
| - | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | 问候气泡                            | `ChatViewModel.init` 时自动插入 `TEXT` 消息，从 `MasterAgent.fallbackGreetings` 取                                                                           |
| 2 | Assistant 打字机                   | 引擎侧 `ReActLoop` emit `TextDelta` 流；UI 侧 Assistant 气泡 collect StateFlow 增量渲染 + **自动滚动联动**：气泡高度随 token 增加时自动滚到底部（throttle 60fps）；用户回看时暂停，滚回底部或发消息时恢复 |
| 3 | 回看暂停 auto-scroll                | 监听 `listState` 位置，不在底部时暂停自动滚；滚回底部或发消息时恢复                                                                                                           |
| 4 | `ConfirmMatrixCard` → 原生 Dialog | **从对话流分离**：确认请求弹阻塞式原生 Dialog，用户勾选提交后关闭；执行状态独立区域在对话气泡下方临时出现（进度 + 每项 ✓/...），执行完消失；回执并入 Assistant 打字机输出                                               |

### Dialog 流程

```
Dialog 弹出：
  Assistant 执行到需确认的工具调用
    → Dialog 弹出（阻塞，必选）
    → 用户逐项勾选 + 点「照做」/「跳过全部」
    → Dialog 关闭 → 引擎开始执行

Dialog 内部结构：
  ┌──────────────────────────────┐
  │ 🔒 MasterAgent 请求执行      │
  │ 信任档位：建议                │
  ├──────────────────────────────┤
  │ ☐ 创建歌单「深夜专属」        │
  │ ☐ 将 5 首歌加入歌单            │
  │ ☐ 播放歌单                    │
  │                              │
  │ 信任档位下拉：[建议 ▼]        │
  │  ▸ 建议（每次确认）           │
  │  ▸ 代劳（自动执行，仍可中断）  │
  │  ▸ 静默（后台执行，不打扰）    │
  ├──────────────────────────────┤
  │         [跳过全部]  [照做]     │
  └──────────────────────────────┘

"跳过全部"语义：本次所有勾选项均跳过，不执行。
"信任档位下拉"：可当场提升/降低信任档位，影响后续该类工具是否需要确认。

执行状态提示（Dialog 关闭后出现，不阻塞）：
  ┌──────────────────────────┐
  │ Assistant 气泡（打字机输出） │
  ├──────────────────────────┤
  │ ● 正在执行 2/3 项...       │
  │   ✓ 创建歌单              │
  │   ... 播放中...           │
  │   ○ 待执行                │
  └──────────────────────────┘
  执行完 → 状态提示消失 → Assistant 继续打字机输出回执
```

### 组件变更汇总

| 组件                | 之前             | 现在                        |
| ----------------- | -------------- | ------------------------- |
| ConfirmMatrixCard | 嵌在对话流底部的非模态悬浮卡 | 阻塞式原生 Dialog，与对话气泡完全分离    |
| 状态提示区域            | 不存在            | 独立区域，Dialog 关闭后临时出现，执行完消失 |

***

## P4 · AgentMonitorScreen（原 AuditLogScreen）

### 改名范围

| 项            | 之前                         | 现在                             |
| ------------ | -------------------------- | ------------------------------ |
| 路由           | `Routes.Settings.AuditLog` | `Routes.Settings.AgentMonitor` |
| Screen 文件    | `AuditLogScreen.kt`        | `AgentMonitorScreen.kt`        |
| ViewModel 文件 | `AuditLogViewModel.kt`     | `AgentMonitorViewModel.kt`     |
| Room 表       | `agent_audit_log`          | 不动                             |
| UI 标题        | "操作审计日志"                   | "Agent 监控"                     |

### 整体结构

```
AgentMonitorScreen（SubScreen，标题 "Agent 监控"）

┌─────────────────────────────────┐
│ ① 概览仪表板（顶部固定）           │
│ ┌─ Token ──────┐ ┌─ Trust ─────┐ │
│ │ ▓▓▓▓▓░░░     │ │ Master: ACT │ │
│ │ 320K/500K 64%│ │ Enrich: SIL │ │
│ │ 熔断阈值 95%  │ │ Radio: SUG  │ │
│ └──────────────┘ └─────────────┘ │
│ ┌─ Agent 运行态 ────────────────┐ │
│ │ ● 电台运行中 · 自动续歌中       │ │
│ │ ● 富化已暂停 · 87%              │ │
│ └───────────────────────────────┘ │
├─────────────────────────────────┤
│ ② FilterRow（复用现有）            │
│ [全部] [电台] [跳过重排] [LLM]   │
│ [只看异常]  ← 新增                 │
├─────────────────────────────────┤
│ ③ 操作历史 LazyColumn（复用现有）   │
│ ┌──────────────────────────────┐ │
│ │ playlist.create ✓  [撤销]     │ │ ← 撤销按钮占位
│ │ radio.start ✓                │ │
│ │ radio.reorder ✓              │ │
│ └──────────────────────────────┘ │
└─────────────────────────────────┘
```

### 概览仪表板数据源

| 模块              | 数据源                                                                 | 引擎侧改动                |
| --------------- | ------------------------------------------------------------------- | -------------------- |
| Token 消耗        | `GlobalTokenCounter.usedToday()` / `remainingToday()`               | 无                    |
| TrustLedger × 3 | `AgentPolicyConfig`（Master / Enrich / Radio）DataStore 读             | 无                    |
| Agent 运行态       | `MasterAgent.isRadioActive()` + `MasterAgent.enrichStatusSummary()` | 引擎侧统一运行态接口（见前置清单 #3） |

### 撤销按钮

* 每条可撤销日志行右侧加 🔄 按钮 → STRONG\_CONFIRM 弹窗

* **边界判定**：检查 `argsHash`（已存在于 `AgentAuditLog.argsHash` 字段）与当前状态是否一致。不一致 → STRONG\_CONFIRM 追加文案"此操作之后数据已被修改，撤销可能造成歧义"

* **反向工具**：W 阶段不实现，用户确认后提示"反向撤销功能开发中"

### "只看异常" Filter

* Filter 条件：`outcome IN (rejected, circuit_break, budget_exhausted)`

* 点击行展开显示 `reason` 字段（拒绝原因）

***

## P5 · UserUsageDataScreen → 听歌报告页

### 当前结构

```
UserUsageDataScreen
├── SegmentedControl（周/月/年）
├── OverviewCard（总时长/次数/歌曲数）
├── TasteCard（风格分布 Canvas）
├── RankingAndHistoryCard（排行 + 最近历史）
└── PlaySourcePieChart（播放来源饼图 Canvas）
```

### 改造方向

```
SegmentedControl: [全部] [日] [周] [月] [年]   ← 「全部」默认选中

每个 tab 内容：
┌─────────────────────────────┐
│ 🤖 伙伴总结（Agent 叙事段）    │  ← 顶部新增，与 tab 时间范围联动
│ "这两年你深夜听了最多歌，周杰伦占 Top 3" │
│ 🔄 重新生成                  │
├─────────────────────────────┤
│ OverviewCard（时间范围内数据） │  ← 保留，数据范围与 tab 联动
├── TasteCard                 │
├── RankingAndHistoryCard     │
└── PlaySourcePieChart        │
```

### 叙事段生成策略

**默认 HelloSubAgent 自适应**（用户可在 P2 HelloSubAgent Tab 覆盖）：

```
用户日均听歌时长        →  报告叙事更新频率
─────────────────────────────────────────
≤ 30 分钟              →  周更新（低活跃，内容不够每日一更）
30 分钟 ~ 2 小时        →  日更新（正常活跃度）
≥ 2 小时               →  日更新（高活跃，内容多值得每日更新）
```

**定时批量生成时机**：

| 维度 | 生成时机         | 条件                    |
| -- | ------------ | --------------------- |
| 全部 | 自适应频率更新      | DAO 有缓存直接显示，过期后台异步重生成 |
| 日  | 每天凌晨         | 当天有播放数据才生成            |
| 周  | 每周一凌晨        | 上周有数据才生成              |
| 月  | 每月 1 号凌晨     | 上月有数据才生成              |
| 年  | 每年 1 月 1 号凌晨 | 上年有数据才生成              |

**DAO 缓存**：每次生成写入 DAO，带 `generatedAt` + `timeRange`。报告页直接读 DAO 显示，不阻塞 UI；DAO 过期时**后台异步触发重新生成**（不打断用户看旧数据）。
