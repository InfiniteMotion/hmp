# HMP Agent 计划书 · 总纲

> **上游**：`../design/agent.md`（设计总纲，单一事实来源）｜`../design/agent-architecture.md`（架构详解：铁则 F1-F6、两层结构、源码索引）
> **状态**：v11（2026-09-21：新增 **F14 Agent 界面自适应与多语言** [T1 UI 适配 → T3 组件基建对齐 → T2 字符串收拢 + 14 语言，用户裁定顺序与范围扩展]。F13 收尾章同日完成 —— desktopTest 961 全绿；方向 B 余 F10 语音会话与 F14）
> **本目录**：`docs/7_x/B agent-build/taskbook/` —— 阶段推进计划与验收档案

***

## 1. 这份计划书怎么读

内容分四类，各自有家：

| 类别 | 去处 | 说明 |
|------|------|------|
| **生命线**（做到哪了） | 本文件 | 进度主轴、依赖关系、当前状态。只讲结论，不含细节 |
| **事前规划**（打算做什么） | 各章节文件 | 原始里程碑 M0–M6，**主轴** |
| **后加入的规划** | 各章节文件 | `R`/`S`/`T`/`U`/`V`/`W`/`A0`，实施中新增的横切与收口阶段，**插叙** |
| **验收结果与经验** | 各章节文件 | 每阶段的「已交付」「验收核验」「踩坑与决策」写在该章节末尾 |

**主轴与插叙的区别**：M0–M6 是动工前就划定的里程碑（对应设计总纲 7.4 的 B0–B6），其中原规划的 M7（报告与语音）已拆开 —— 报告部分并入 **F9 报告与设置**，语音部分移出为 **F10 语音会话**。字母阶段是实施过程中发现必须插入的横切工作（还债、工具补全、架构重塑、日志治理、批次加固、UI 呈现、能力面统一）。两者都推进同一目标，但**字母阶段不占里程碑编号**，因此归为插叙。

### 章节归并原则

章节文件按**阶段族**划分，编号 `f1`–`f9`（**九章，编号连续无跳号**），后续阶段用 `f10` 起继续（`f10` 语音会话 · `f11` 后台生命周期 · `f12` Token 计量与窗口治理）：

- 同一族 = 相邻日期 + 同一目标 + 改同一批文件 + 有明确依赖关系
- 一章内按阶段分节（如 F5 内含 S/T/U 三节），**不再为每个阶段单开文件**
- **章节编号与提交族编号一一对应**；尚未开工的族先行占号，落地后直接填内容
- 每章的「阶段族」字段标注该族由哪些原始阶段组成，M 编号保留在章内用于任务 ID（如 F9-T1）

***

## 2. 生命线（进度主轴）

```
F1 规划与地基 ──▶ F2 锚点与协议 ──▶ F3 工具与引擎 ──▶ F4 对话与清债
                                                              │
                                                              ▼
                              ┌─────────── 插叙段（横切与收口）───────────┐
                              │  F5 体系重塑（S → T → U）                 │
                              └──────────────────┬───────────────────────┘
                                                 ▼
                     F6 电台与富化 ──▶ F7 Hello 呈现 ──▶ F8 编排内核与收尾
                                                                  │
                                                                  ▼
                        F9 报告与设置（T0 → A0 → T1 → T2）✅ 已收口
                                                                  │
                                                                  ▼
                        F10 语音会话（RealtimeVoiceTransport → 语音会话）
                        —— 后续独立阶段，不占 F9 编号
                                                                  │
                                                                  ▼
                        F11 后台生命周期（AgentRuntime → 自持前台服务 → iOS 有限保活 → Desktop）
                        —— 插叙修复：agent 体系退后台不存活（RC1/RC2）
                                                                   │
                                                                   ▼
                        F12 Token 计量与窗口治理（T1 真值 + 账本 → T2 分账视图 → T3 窗口 → T4 组装策略）
                        —— 插叙修复：token 计量七处断裂 + 无分账维度 + 超窗静默故障
                                                                   │
                                                                   ▼
                        F13 Agent 工作收尾（可见性收敛 → 结构对齐 → DI 核对 → 测试补齐）
                        —— 收尾章：分层升级为编译期保证，结论可复跑成证据，新增代码盖上测试
                                                                   │
                                                                   ▼
                        F14 Agent 界面自适应与多语言（T1 UI 适配 → T3 组件基建对齐 → T2 字符串收拢 + 14 语言）
                        —— 横切加固：任意窗口尺寸看得舒服，任意系统语言看得懂
```

**依赖要点**：

- `F2 锚点层一期` 为 `F4` 的前置 UI 骨架，可与 `F1`/`F3` 完全并行
- `F4 对话与清债` 是 `F6 电台` 的校准基准（确认交互「有感」须先于电台隐式接受「无感」交付）
- `F6 电台` 依赖 `F4 + F5`（F5 提供 SubAgent 基类、`AgentScheduler`、`ToolRegistryView`）
- `F8` 依赖 `F6 + F7`（DJ 衔接 / 跳过感知 / 批次加固 / 卡片池 已就绪）
- `F9-T0 用户认识模块` 是 `F9-T1/T2` 的**前置**（报告叙事段是它的第一个消费方；「记忆与信任 / 记忆管理」两区展示它）
- `F9-A0 Capability 统一化`（F9 插入的插叙阶段，不占里程碑编号）—— 统一 SubAgent 能力面（`Capability` 接口 + `CapabilityStatusTool`）、清理 DJ 工具绕路债；依赖 F6 + F8
- `F9-T1/T2` 依赖 `F6 + F8` 与 `T0`
- **`F10 语音会话`** 依赖 `F4`（对话页已就绪，语音只是加一条 WebSocket Transport）与 `F9-T2`（设置页的「嗓音与耳朵」分区是它的配置入口）。**独立阶段、独立验收**：端点不可用或验证不过则整体延期，不影响 F1–F9 的任何交付
- **`F11 后台生命周期`** 依赖 `F6 + F8`（电台会话与编排内核已就绪，本阶段只补生命周期所有者与保活）与 `F9-T2`（设置的 Agent 配置面）。**插叙修复**：不引入新能力，只让已有的 agent 体系在后台活下来；设计依据 `../design/agent-lifecycle.md`。不改 F1–F10 任何交付的对外契约
- **`F12 Token 计量与窗口治理`** 依赖 `F3`（引擎循环四件套里已有 `ContextBudget`）+ `F5`（多 Agent 运行时与独立 transport）+ `F9-T2`（设置页的全局 Agent 参数面）。**插叙修复**：不改决策能力与对外契约，只把"估算"换成"实测"、把"拍脑袋的窗口"换成"固定假设 + 前置护栏"；设计依据 `../design/agent-token.md`。**与 F11 无依赖关系**（一个管进程存活，一个管 token 与窗口），可并行
- **`F13 Agent 工作收尾`** 依赖 `F5`（多 Agent 运行时与 SubAgent 体系）+ `F12`（收口经验）。**收尾章**：不加能力，只回答"能不能放心收工" —— 分层升级为**编译期保证**（可见性收敛）、结论做成**可复跑证据**（DI 图核对 / 反向边复测 / 六任务编译 / 测试 XML 计数）、新增代码**盖上测试**（agent 域 14 类 106 例 → 961 全绿）；不改任何对外行为契约；设计依据 `../design/agent-architecture.md` §可见性与接口面。**与 F10 无依赖关系**，可并行
- **`F14 Agent 界面自适应与多语言`** 依赖 `F13`（页面清单与共享件在其结构收敛后固定）。**横切加固**：不加能力 —— T1 让 agent 页面消费既有 `WindowSizeClass` 三档断点（对话限宽 / 看板分栏 / 表单限宽居中，手机竖屏基线零回归）；T3 组件基建对齐（表单页 16 处原生 `TextField`/`Button` 换基础件、空态走 `DefaultEmpty`、语义间距换 `LocalHMPDimens` 令牌、`Severity.Warn` 色收进 `AgentStatusColors`；**AgentNoticeBar 调查为死组件，不并入、留待单独决断**）；T2 把 agent UI 面向文案收拢进 `strings.xml` 并 **14 语言全量交付**（LLM 面向文案不翻译；14 文件键集合脚本核验一致）。执行序 **T1 → T3 → T2**（组件骨架先于文案收拢）。不改交互逻辑与信息架构。**与 F10 无依赖关系**，可并行


***

## 3. 进度状态

**已完成**：F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → **F9（T0 用户认识模块 · A0 Capability 统一化 · T1 听歌报告与遗忘唤醒 · T2 伙伴设置页）** → **F11 后台生命周期（L1–L3 / L5 落地，Android/iOS 真机核验待手动）** → **F12 Token 计量与窗口治理（T1–T4 落地，T2b 配额候补 / T5 成本可见后置）** → **F13 Agent 工作收尾**（阶段 1–7 全部完成：可见性收敛 —— runtime 顶层类型与 MasterAgent 成员按编译反推法收口、装配迁入 `:shared`、4 类拆出 7 个纯逻辑文件；人工核对 DI 图 —— Koin **不会**绕过编译期可见性，顺带修掉 1 处 `Json` 重复注册；agent 域测试补齐 —— 纠偏「32 类零覆盖」误报后新增 14 个测试类 / 106 例，desktopTest **961 全绿**、既有测试零回归）
**进行中**：**F14 Agent 界面自适应与多语言**（执行序：T1 UI 适配 → T3 组件基建对齐 → T2 字符串收拢 + 14 语言全量）。**T1 已交付**（2026-09-22，七页断点适配 + T3 ④ 顺带收口）
**待做**：**F10 语音会话**（`RealtimeVoiceTransport` + 语音会话；独立阶段，可整体延期）

| 章节 | 阶段族 | 含阶段 | 轴别 | 状态 | 完成日 | 提交 |
|------|--------|--------|------|------|--------|------|
| [F1](f1-规划与地基.md) | 规划与地基 | 设计编制 → M0 | 主轴 | ✅ | 2026-08-27 | 3 笔 |
| [F2](f2-锚点与协议.md) | 锚点与协议 | M1 → M2 → review | 主轴 | ✅ | 2026-08-28 | 3 笔 |
| [F3](f3-工具与引擎.md) | 工具与引擎 | M3 → M4 | 主轴 | ✅ | 2026-08-28 | 2 笔 |
| [F4](f4-对话与清债.md) | 对话与清债 | M5 → R | 主轴+插叙 | ✅ | 2026-08-30 | 2 笔 |
| [F5](f5-体系重塑.md) | 体系重塑 | S → T → U | 插叙 | ✅ | 2026-09-01 | 3 笔 |
| [F6](f6-电台与富化.md) | 电台与富化 | M6 → V | 主轴+插叙 | ✅ | 2026-09-02 | 2 笔 |
| [F7](f7-hello呈现.md) | Hello 呈现 | W0-W2 → W0 → 卡片 | 插叙 | ✅ | 2026-09-04 | 3 笔 |
| [F8](f8-主播与收尾.md) | 编排内核与收尾 | RadioAgent → W 收尾 | 插叙 | ✅ | 2026-09-14 | 2 笔 |
| [F9](f9-报告与设置.md) | 报告与伙伴设置 | **T0 用户认识（T0a / T0b）** → **A0 Capability 统一化** → **T1 听歌报告 + 遗忘唤醒** → **T2 伙伴设置页** | 主轴+插叙 | ✅ | T0: 2026-09-16 · T1: 2026-09-17 · T2: 2026-09-18 | 3 笔 |
| F10 | 语音会话（未开工） | `RealtimeVoiceTransport` → 语音会话 + CONFIRM 口头化 | 后续独立阶段 | ⬜ 待开工 | — | — |
| [F11](f11-后台生命周期.md) | 后台生命周期 | **L1 AgentRuntime 所有者** ✅ → **L2 Android 自持前台服务** ✅ → **L3 iOS 有限保活** ✅ → **L5 Desktop 收口** ✅（~~L4 电台快照落盘~~ **已撤销**）| 插叙 | 🟢 主体完成（L1–L3/L5 落地；Android/iOS 真机核验待手动）| 2026-09-19 | c1f188a |
| [F12](f12-token计量与窗口.md) | Token 计量与窗口治理 | **T1 计量打真值 + 落账本（`token_ledger`）** ✅ → **T2 分账视图（按 Agent / 端点 / 时间）** ✅ → **T3 窗口治理（固定 64K + 前置护栏）** ✅ → **T4 上下文组装策略** ✅ → T5 成本可见（后置）；~~T2b 配额接线~~ **候补** | 插叙 | ✅ 主体完成（T1–T4 落地）| 2026-09-19 | c1f188a |
| [F13](f13-可见性与接口面收敛.md) | **Agent 工作收尾**（可见性与接口面收敛 + 测试覆盖） | **① 收口 runtime 实现层顶层类型** ✅（净收口 18 个） → **② 分拣 MasterAgent 成员方法** ✅（净收口 21 / 门面 34） → **③a 装配迁入 `:shared`** ✅ · **③b 收口调度计量簇 + 抽门面** ✅ · **③c 拆巨型类** ✅（部分：4 类共拆出 7 个新文件，"纯逻辑/词表"外移；状态编排保留） → ④ 编译/测试/反向边验证 ✅ → **⑤ 3c 后续可维护性收口** ✅（补 3 测试类 37 例并抓出 1 个真缺陷 · `builtinIntent` 表驱动 · LLM 调用形态收敛 · Hello 会话状态收敛；`AgentFacade` **判为不做**） → **⑥ 人工核对 DI 图** ✅（结论：Koin **不绕过**编译期可见性；顺带修掉 1 处 `Json` 重复注册） → **⑦ agent 域测试补齐** ✅（纠偏「32 类零覆盖」误报；新增 14 测试类 / 106 例，desktopTest 961 全绿零回归） | 插叙 | ✅ 全部完成 | 阶段1–5: 2026-09-20 · 阶段6–7: 2026-09-21 | `ba25365` |
| [F14](f14-自适应与多语言.md) | Agent 界面自适应与多语言 | **T1 UI 适配**（消费既有 WindowSizeClass 三档：对话页限宽居中 / 看板 Expanded 三列 / 表单限宽居中；手机竖屏基线零回归）→ **T3 组件基建对齐**（表单页 16 处原生 `TextField`/`Button` 换 `HMPTextField`/`MyButton`；空态统一 `DefaultEmpty`；语义间距换 `LocalHMPDimens`；`Severity.Warn` 色收进 `AgentStatusColors`；AgentNoticeBar 经调查为死组件**不并入**）→ **T2 字符串收拢 + 14 语言**（agent 子树 ~250+ 新键入 `strings.xml`，占位符位置参数化，LLM 面向文案不翻译；14 文件键集合脚本核验一致） | 横切加固 | 🟡 进行中（T1） | 2026-09-21 立 | — |

**已知验收缺口**（不阻塞主线，需补）：

- 本地化（14 语言）横切项未做（叙事段 / 报告页所需的文案键已随 T2 的 Prompt 多语言机制部分落地，其余语言待同步）
- 审计页 / 撤销动作按定义留 M6 → 已随 F8 落地
- T 阶段 E1/E2 待手动冒烟（需 LLM 端点 + 测试曲库）
- U 阶段 U-T4 裸 println 部分迁移（Desktop/FFmpeg 待续）
- ~~**窗口统计查询无 DAO 级单测**~~ → **2026-09-21 已补（收尾第④步第2项）**：6 条窗口 SQL 在 `shared/src/desktopTest/.../WindowedAnalyticsDaoTest.kt`（真实 Room + BundledSQLiteDriver，8 例）覆盖 —— `getTotalDurationSince` / `getWindowedPlaybackCount` / `getTopSongsSince` / `getRecentPlaybackSince` / `getSourceBreakdownSince` / `getTopLabelsSince`；详见 `f9-报告与设置.md` §2.3.3。另 **收尾第④步第3项·shared-ui `commonTest` 扫障**：新增 `commonTest`+`desktopTest` 源集与测试依赖，3 个纯逻辑测试（`LabelExtensionsTest` / `RoutesTest` / `UiStateTest`）从 `androidHostTest` 迁入 `commonTest`，可在 JVM 上跑、不再绑死 Android SDK
- ~~**`WindowedBundle.narrative` 悬空字段**~~ → **2026-09-19 已删**（恒为 `null` 的死字段；报告叙事实际走 `PersonalityBundle.narrative`，空态判断改为只看 `analytics`）
- **本地化真实工作量被高估**：原以为"缺 4 个 key × 12 语言"，实为 **agent UI 约 20 个文件把界面文字硬编码在 `Text("…")` 里、未接入 `strings.xml`** → 全量 14 语言 = 先重构为 `stringResource` 再翻译。**决定后置为独立阶段**（详见 §5 横切工作）
- **agent 体系退后台不存活**（RC1/RC2）—— 已立 **F11 后台生命周期**，见 `f11-后台生命周期.md`（原 RC3「电台零持久化」**已改判为刻意边界**，不进缺口清单）
- ~~iOS 编译未验证~~ → 2026-09-13 已核验通过（`:shared:` + `:shared-ui:compileKotlinIosSimulatorArm64`，macOS 环境）；后续阶段仍需逐阶段回归


***

## 4. 章节索引

### 主轴章节

| 章节 | 内容 | 关键交付 |
|------|------|----------|
| [F1](f1-规划与地基.md) | 设计总纲编制 + 三端 Repository 去重 + Room v2 | `agent.md` 单一事实来源；去重 -40%；标签溯源四列 |
| [F2](f2-锚点与协议.md) | 三胶囊底栏 + 轻量浮层 + 播放页重排 + C 键；`LlmTransport` 流式 + tools 参数 | 锚点层 UI 骨架（Fake 驱动）；手动 SSE、5 服务商兼容 |
| [F3](f3-工具与引擎.md) | `ToolSpec` DSL + 十项工具；引擎循环四件套 + 双层预算 | 工具校验防漂移；`Orchestrator`/`PolicyGuard`/`TrustLedger`/`ContextBudget`/`PresenceBus` |
| [F4](f4-对话与清债.md) | 对话页 + 五类气泡 + 确认流；首轮注入 / 漏斗 / 真实播放端口 / 多确认门 / 会话持久 | 纯文字体验闭环（T3/T5/T6/T7 由 R 补齐） |
| [F6](f6-电台与富化.md) | Radio SubAgent + 三轮协作 + DJ 衔接 + 审计页；Enrich v2 批次策略重写 | 电台子系统；Enrich 两轮 review 13 修复 |
| [F9](f9-报告与设置.md) | **T0 用户认识模块**（两层画像 / 两个平级建模器 / 分级记忆 L1-L3 / 对话侧写门槛放开 / 认知准入三档 / **明确拒绝 MBTI**）+ **T1 听歌报告与遗忘唤醒**（累计画像置顶 + 双轴筛选 5×5 + `ForgottenDelivery` 卡）+ **T2 伙伴设置页**（六分区 + Agent 配置收拢 + Prompt 多语言 + P4 看板 v2） | 用户认识模块（`user_profile_evidence` + `user_profile_portrait`，Room v6）、窗口统计六条 SQL（零迁移）、`AgentConfigScreen(agentRole)` 通用配置页、`docs/LOGGING.md` |

### 插叙章节

| 章节 | 内容 | 为什么插入 |
|------|------|-----------|
| [F5](f5-体系重塑.md) | 27 原子工具；Master 唯一大脑 + 两层基础设施；Kermit 日志统一 | M3 十工具到可用工具层的补全 → agent 是"植物人"（闭箱/失忆/被动）需重塑运行时 → 全项目零统一日志基础设施 |
| [F7](f7-hello呈现.md) | W0–W2 设计文档 + HelloSubAgent + HelloSlideCards | 引擎能力已齐但 UI 没接线，用户感知不到；Hello 是第三个 SubAgent |
| [F8](f8-主播与收尾.md) | RadioAgent 编排内核落地 + W 呈现层收尾 + 电台卡重设计 | 电台能选歌了，缺"把编排思路传达出来"的最后一公里 |
| [F13](f13-可见性与接口面收敛.md) | **收尾章**：收口 agent 域可见性 + 测试覆盖 —— runtime 实现层 38 个顶层类型标 `internal` + `MasterAgent` 47 个成员方法分拣 + 抽门面并拆 4 个巨型类（**3c 实况**：Enrich 1179→591 / Hello 1969→1844 / Radio 1869→1732 / Master 1811→1751，共拆出 7 个新文件；**阶段 5** 再补 3 测试类 37 例并抓出 1 个真缺陷 + 三处代码收敛；**阶段 7** agent 域测试补齐 14 类 106 例，desktopTest 961 全绿） | 包结构重整（17 包 / 0 反向边）已完成，但 **Kotlin 没有「包内可见」**，分层只是约定；`internal` 仅占 5.3%，UI 可直连引擎内部（实测 3 处越界），重构时无法判断影响面 |
| [F14](f14-自适应与多语言.md) | **T1** 自适应显示适配（消费既有 `WindowSizeClass` 三档：对话页限宽居中 / 看板 Expanded 三列 / 表单限宽居中；手机竖屏基线零回归）+ **T3** 组件基建对齐（表单基础件替换 16 处、空态统一、尺寸令牌、状态色散点收口——源自 2026-09-21 十项基建排查）+ **T2** 字符串收拢与 14 语言（agent UI 文案 ~250 键入 `strings.xml`、占位符位置参数化、14 文件键集合校验一致；LLM 面向文案不翻译） | F13 收尾盘点挂账的两个**交付质量**缺口：agent 页面宽窗拉伸不可读；硬编码中文令 14 语言设施对 agent 失效（非 zh 系统语言下整页中文）。属横切加固（不加能力、不改交互），故单列 |

### 后续阶段（未开工）

| 章节 | 内容 | 为什么单列 |
|------|------|-----------|
| F10 语音会话 | `RealtimeVoiceTransport`（WebSocket 双向流：JSON 控制事件 + 二进制音频帧，纯 common 无 expect/actual）+ 语音会话（transcript 双向流 → 一 UI 两形态 / `VoiceSessionController` / CONFIRM 口头化 / 语音文字混排）；验收 = `FakeRealtimeTransport` 协议测试 + 语音写操作有文字记录 | **B6 里唯一真正新增的传输层**，需要真实端点才能验证，与报告角色/设置页无耦合。设计已定稿（`../design/agent.md` §4.4 / §7.4），落地条件单列，故移出 F9 另立阶段 |
| [F11](f11-后台生命周期.md) | **Runtime 生命周期所有者**（`AgentRuntime` / 启动即初始化）+ **Android 自持前台服务**（`MusicPlayService` 改造，保活条件扩为「音频在播 或 agent 活跃」）+ **iOS 有限保活**（`beginBackgroundTask`）；验收 = 电台退后台存活。~~最小电台快照落盘~~ **已撤销**（D3：维持"从不落盘"）| 方向 B 的 F1–F9 只建设了"前台可用"，**从未指定"谁持有运行时生命周期"**，导致退后台进程被回收即丢会话（RC1/RC2）。属**横切修复**（不改决策能力），设计依据 [`../design/agent-lifecycle.md`](../design/agent-lifecycle.md)，故单列 |
| [F12](f12-token计量与窗口.md) | **真 usage 计量**（`LlmEvent.Usage` + 进程级 `TokenMeter` 唯一记账口）+ **分账账本**（Room 表 `token_ledger`：每次调用一行，含 agent / 端点 / 模型 / 时间 / 实测标记；**永久保留**）+ **分账视图**（按 Agent / 端点·模型 / 时间三维聚合；看板数字改由明细聚合）+ **窗口治理**（`EngineDefaults.AGENT_CONTEXT_WINDOW = 64_000` 固定常量，删三处硬编码，超窗**前置拒绝**并走本地保底，失败原因与"模型判 none"**可区分**）+ **上下文组装策略**（常驻裁剪 vs 超窗降级分开、摘要回注、收敛两处魔数）；验收 = 真值入账 / 四条路径全覆盖 / 分账四 Agent 齐全 / 超窗不炸且可辨 / 账本不泄密。**不设**上下文上限与压缩方式；**配额接线候补** | token 在开发阶段被刻意忽略，共**七处断裂**：**真 usage 拿到却扔掉**（流式 DTO 连字段都没有）、**记账只覆盖 ReActLoop 一处**（导致配额熔断对最大的消费者 Enrich 失明）、**日配额是假控件**、**窗口按 Agent 硬编码导致换小窗口模型即静默故障**、**压缩空转**、**无分账维度**。属**横切修复**（不改决策能力与对外契约），设计依据 [`../design/agent-token.md`](../design/agent-token.md)，故单列 |

### 相关文档（本目录外）

| 文档 | 内容 |
|------|------|
| `../design/agent.md` | 设计总纲（定位 / 交互语言 / 场景流 / 组件清单 / **§4.4 语音二档制** / §7.4 实施序列），**单一事实来源** |
| `../design/agent-architecture.md` | 架构详解：设计铁则 F1-F6、两层结构、各 Agent 详解、源码位置索引 |
| [`../design/agent-profile.md`](../design/agent-profile.md) | **用户认识模块（画像）唯一依据**（v3.8）：两层数据模型（含迁移 DDL） / 槽位闭集（谓词即槽位限定名） / 分级记忆与承载字段 / C2 定稿（画像可读、**仅供参考**非指令） / 审计四问 / 三写入三读取 / 验收剧本 P1-P21 |
| [`../design/agent-w.md`](../design/agent-w.md) | W 阶段全景：W0 引擎侧 + W1 页面级（P1-P5）+ W2 组件级（C1-C11）+ W 缺口登记 G1-G16 |
| [`../design/agent-radio.md`](../design/agent-radio.md) | 电台 agent 唯一依据（契约 + 验收剧本 + 设计演进附录） |
| `../design/agent-g6-recommend-design.md` | G6 首页双推荐页设计规格 |
| [`../../LOGGING.md`](../../LOGGING.md) | 日志规范（`LogTag` / `HmpLog` 门面 / `MemLogWriter`），F9-T2 落地 |

***

## 5. 横切工作

贯穿全部阶段的常驻事项：

| 项 | 内容 | 挂靠 |
|----|------|------|
| 本地化 | ⚠️ **口径修正（2026-09-19）**：真实工作量不是"80–120 条 × 14 语言"，而是 **agent UI 约 20 个文件把界面文字硬编码在 `Text("…")` 里**（`AgentMonitorScreen` / `AgentConfigScreen` / `CompanionBubble` / `ChatScreen` / `AuditLogScreen` 等，未接入 `strings.xml`）。全量本地化 = **先重构为 `stringResource` 再翻译**，属重构级工程 | 后置为**独立阶段**（本次方向 B 收尾不做）；F9-T2 已建 Prompt 多语言机制（`Lang` / `L10N_PROMPTS`） |
| 测试基建 | Fake\* 替身：`FakeLlmTransport`（F2）/ `FakePlaybackCommandPort`（F3）/ `FakeRealtimeTransport`（F10）；Room in-memory 迁移测试（F1） | 阶段内 |
| 审计 | `agent_audit_log` 写入埋点：工具调用 / 许可裁决 / 云端修正（NOTIFY 级一律留痕） | F3 起 |
| 日志 | Kermit 统一（F5-U）→ F9-T2 收敛为 `com.hmp.log` 门面（`LogTag` / `HmpLog` / `MemLogWriter`），规范见 `../../LOGGING.md`；Tag 层级 `Agent.*` | F5 起 |
| 文档 | 本计划书随进展更新；与 `agent.md` 双向同步；TODO.md 方向 B 任务编号（B0-B6）对齐 | 全程 |
| 可见性 | **Kotlin 没有「包内可见」**（无 package-private）⇒ 包分层只是约定。凡新增 agent 域公开声明，须自问「模块外有人用吗」：只在 `:shared` 内使用的应加 `internal`。判据与手法（编译反推法）见 [F13](f13-可见性与接口面收敛.md) | F13 起 |

***

## 6. 挂起参数与建议默认值

| 参数 | 建议默认 | 说明 |
|------|----------|------|
| `EnrichTask.targetCoverage` | 0.9 | 富化覆盖率目标（**已实现**，`EnrichSubAgent.kt:59` 构造默认值；挂在每次富化任务上，非全局常量）。低于此值 Master 创建 Enrich |
| `maxBatchSize` | 20 | Enrich 单批歌曲数上限 |
| `acceptableFailureRate` | 0.1 | 批次失败率阈值，超过则缩批并更新 prompt |
| 步数预算 | 8 | Master 主循环硬熔断 |
| `CHUNK_SPLIT_SIZE` | 20 | 单次 LLM call 最大歌曲数 |
| `BIG_ARTIST_THRESHOLD` | 3 | ≥ 3 首未富化 → 走 ArtistGroup（可预热） |
| `MIXED_GROUP_SIZE` | 10 | 小歌手累计 ≥ 10 首 → 走 MixedGroup（跳过预热） |
| 窗口查询 `days` | — | `NarrativeTimeRange.toDays()` 映射：日=1 / 周=7 / 月=30 / 年=365 / **全部=-1**（取 10 年前时间戳近似），见 `SlideModels.kt:40` |
| **上下文窗口** | **64000** | `EngineDefaults.AGENT_CONTEXT_WINDOW`（F12-T3 新增）——**固定常量，不探测、不可配**。全部 Agent 统一；超窗前置于 `64K × 0.9 = 57.6K`。**使用前提**：端点须支持 ≥64K |
| 历史保留条数 | 6 | **内部常量，不上升为设置项**（F12-T4 收敛两处魔数：`AgentContextBudget.recentMessagesToKeep` 6 与 `ChatAgentGateway.buildHistory` 30）|
| 日 Token 配额 | 500000 | `GlobalTokenCounter.DEFAULT_DAILY_TOKEN_QUOTA`。**现状为"假控件"**（设置项写进配置，运行时读常量 `val`）→ 原计划 F12-T2 修，**2026-09-19 改为候补**（配额先放开）。**注**：修它之前必须先修「Enrich 熔断失明」（F12 断层 2）|
| **Token 明细账本** | 永久保留 | `token_ledger` 表（F12-T1 新增）——每次 LLM 调用一行：`agent_id` / `endpoint_host` / `model` / `prompt_tokens` / `completion_tokens` / `measured` / `created_at`。**只存 host，不存 apiKey 与完整 URL**。体积估算 ~16 MB/年（F12 §3.1）|

完整实现参数见 `../design/agent-architecture.md` 与各章节文件。**F9-T2 起**，上表多数参数已从编译期常量退居为**出厂默认值**，可由 DataStore 覆盖（`AgentPolicyConfig.resolvedFor(role)`），见 `f9-报告与设置.md` §2.4。

***

## 7. 风险与 gate

| 风险 | 影响 | 缓解 |
|------|------|------|
| ~~iOS 编译长期未验证~~ | 三端一致性存疑 | **已核验**（2026-09-13，`compileKotlinIosSimulatorArm64`）；后续阶段逐阶段回归 |
| 本地化积压 | v1 无法面向非中文用户 | 横切项，需随阶段同步；F9-T2 已建 Prompt 多语言机制（`Lang` / `L10N_PROMPTS`），其余文案键待同步 |
| LLM 单故障点 | 额度耗尽 = agent 停摆 | 每 Agent 独立 `LlmTransport`；Enrich 失败降级不阻塞 |
| 云端 API 成本 | 富化全量调用成本高 | `ContextBudget` 配额 + 批次阈值控制；`GlobalTokenCounter.snapshot` 已可实时观测（F9-T2） |
| **F10 语音会话** | 唯一新增传输层，需真实端点才能验证 | **已移出 F9 立为独立阶段**，独立验收；端点不可用则整体延期，不影响 F1–F9 交付 |
| "组件完成度 ≠ 功能可用度" | 接线只做一半，事件发了没人消费 | 每个 `PresenceBus` 事件必须登记消费点，未接的显式标 ❌（F8 已实践）；**F9-T1 的 `WindowedBundle.narrative` 再次踩中 —— 字段建了没接线** |
| 新增 SQL 无 DAO 级单测 | `runCatching` 兜底只防崩溃，防不住返回错值 | SQL 类改动应强制配 DAO 用例；F9-T1 的 6 条窗口 SQL 是反面案例 |
| **agent 体系退后台不存活** | 进程被回收 → 电台会话/队列/恢复缓存全丢，回前台需重开 | **F11 后台生命周期**：`AgentRuntime` 所有者 + Android 自持前台服务；**不落盘**（进程被杀 = 电台丢为已接受行为）。设计依据 `../design/agent-lifecycle.md` |
| Android 14+ 前台服务类型校验 | "无音频但保持前台"可能被 `mediaPlayback` 类型前置条件拒绝 | F11-L2 实现二选一：构建期保持音频会话（占位音轨）/ 该窗口走 `dataSync` 型（见 `f11` §4 R1） |
| **假控件（设置项写了没人读）** | 用户以为能控，实际恒定不变 | 「日 Token 配额」是首个案例（写进 `GlobalAgentConfig`，运行时读 `val`）；**同族的还有"压缩方式"**（若照现状挂在窗口百分比上则永不触发）。判据：**改它之后行为会不会变**。F12-T2 修配额；F12-T4 直接**不提供**压缩方式这一项 |
| **超窗静默故障** | 换小窗口模型（本地 8K/16K）→ API 报错 → 电台判定变 `none`，图上看不出 | **F12-T3**：窗口改固定常量 + **前置拒绝**（不发出必失败的请求）+ 失败原因与"模型判 none"**可区分**。日志带窗口假设值 |
| **窗口前提无法预校验** | 规定"端点须支持 ≥64K"，但 OpenAI/DeepSeek 官方 `/models` **不返回窗口** → 配置时无从校验 | 只能靠**运行时显式暴露**：超窗日志与错误提示给出同一句话（"该模型窗口可能 <64K，本应用不支持"）。见 F12 §7 |
| **估算对英文高估 ~2.8×** | `length × 0.7` 对英文密集内容（曲库标题/艺术家）高估 → 估值偏高 + 阈值偏紧 = **误杀**（拒掉本可发出去的请求）| **T1（真值）必须先于 T3（护栏）**；T1 落地前不得把阈值收紧。见 F12 §4 R2 |
| **安全网无证据** | 超窗路径在 64K 假设下几乎不执行（峰值 1–3 万）→ 实为**未验证代码** | **F12-T4/S9**：必须用单测直接构造超长上下文跑通该路径，不允许靠"真机上应该不会发生"兜 |
| **分层重整的验收口径** | 只扫 `^import` 会**漏边**：`AgentPolicy.kt` 用**全限定名**引用 `runtime.ResolvedAgentConfig` / `RuntimeParams`，首轮重整因此把 `policy ↔ runtime` 的环记成了"已消除"（首轮自述"无反向边"与事实不符） | 包级依赖自查须**同时匹配 import 行与正文全限定名**，并**剥除注释**（KDoc 里的 `[com.hmp.domain.agent.policy.X]` 交叉引用**不是**依赖）；验收判据 = 剥注释后的边集**全部单调**。方法见 `../design/agent-architecture.md` §源码位置索引 |

***

## 8. 变更记录

> 本表只保留**决策结论**。过程性细节见各章节文件的「经验与踩坑」节；文档重组前的 922 行任务书原稿已删除（git 历史可查）。

| 日期 | 里程碑 | 结论 |
|------|--------|------|
| 2026-08-26 | v1 编制 | 依据 `agent.md`；阶段制 M0-M7，不绑定版本号；B0 归 M0 优先；语音独立 gate |
| 2026-08-28 | F2 review | C 键焦点改冒泡阶段；流式超时兜底；assistant `tool_calls` 协议形状预埋（避免 M4 返工） |
| 2026-08-30 | F4（R） | 首轮注入 / 漏斗 / 真实播放端口 / 多确认门 / 会话持久 / M5 剩余 UI。**遗留**：iOS 编译未验证、审计页与撤销留 M6、本地化未做 |
| 2026-08-31 | F5（S） | 批次 A 域前缀统一（17 工具）+ 批次 B 追加 10 → 共 27 原子工具；Registry 与 ToolNames 1:1；`desktopTest` 677 全绿 |
| 2026-08-31 | F5（T，第二轮重写） | **架构定案**：`ContextBudget` 拆两层（每 Agent 独立预算 + 全局纯规则 Scheduler）；Master 唯一大脑，子 Agent 只有执行权；Enrich 改为纯被动执行器。批次计划重构为 T1-T4 串行链；铁则 F1-F6 写入架构文档；Radio 留 M6 |
| 2026-09-01 | F5（T 收尾） | 权限体系简化 9→6 概念；`TrustLedger` 复活；Enrich 接入权限体系；`ConfirmGate` 扩展「总是允许」；`AgentPolicyConfig` DataStore 持久化；`MasterAgent` 瘦身 640→454 行，四层组件解耦 |
| 2026-09-02 | F6（V） | Enrich SubAgent v2 批次策略重写 + 两轮深度 review 13 个修复 |
| 2026-09-04 | F7 | W0–W2 设计文档 + HelloSubAgent + HelloSlideCards（卡片 Pager / 5 种卡 / CardPool） |
| 2026-09-14 | F8 | 呈现层六个表面 + 存在感四形态；RadioAgent 编排内核（RadioSession）；Radio 卡重设计 |
| 2026-09-15 | **文档重组 v3** | 任务书（922 行）拆分为总纲 + 14 份章节文件；架构内容剥离至 `../design/agent-architecture.md`。主轴/插叙分列 |
| 2026-09-15 | **文档重组 v4** | 14 章按**阶段族**归并为 8 章；提交 20 笔 → 8 笔。旧的 14 份单阶段文件删除 |
| 2026-09-15 | **v5 定稿** | 章节编号统一为 `f1`–`f9`（**编号连续**）：原 M7 章改号 **F9 报告与语音**（尚未开工的族先行占号，章内任务 ID 一并改为 `F9-T*`）。章节数 = 提交族数 = 9；八笔历史提交信息统一重写为 *conventional commits + 中文正文*（`计划书 F<n>` / 背景 / 交付 / 验收）。文档重组不占提交位，改动直接落工作区 |
| 2026-09-15 | **F9-T0 立项** | 用户认识模块（画像）登记为 **F9 前置**，契约 `../design/agent-profile.md` v1。认领 `agent-architecture.md` 悬空的两处承诺（"Recall 偏好画像" / `AgentMemory`）与 `f5` 未闭环的「Feedback → Recall → 推荐闭环」。定条目式数据模型 + 谓词闭集 7 条 + **C2 修订**（约束对象是模型推断，非用户显式陈述）+ Room v5→v6 |
| 2026-09-15 | **F9-T0 契约 v2-v2.5（评审驱动的演进）** | v2：条目式 → **侧写层 + 证据层**两层 + **分级记忆 L1-L3** + 认知准入标准 + 拒绝类型学。v2.1：补**产品消费面**（认知面 / 消费面分离，不得回流决策面）。v2.2：**换轴而非换皮**（三轴八型）+ 隐私合规章。v2.3：**曲库先验**补冷启动；核实 `Music` 表无 genre/year。v2.4：曲库改为**形态 / 内容两阶段**（阶段二是纠正而非补全）+ 覆盖率折扣。v2.5：**隐私收敛**（唯一动作 = 端点配置页一句提醒）+ 撤回两处过度外推。细节见 `../design/agent-profile.md` §14 |
| 2026-09-15 | **F9-T0 契约 v3** | **重新整理**：按最终决定重写全文，剥离 v2.1-v2.5 的过程性修订痕迹（收入变更记录）。同步本轮四项决定：① **行为建模改为定时读播放记录 + 状态快照 diff**（不细到单个操作，零新埋点）② 砍掉门面卡片反馈 ③ 搜索关键词登记为**未来流入** ④ **对话侧写门槛放开**（配三条约束 + 底线「推断音乐选择可以，推断人不行」）；C2 再修订为「判断回路只用会话内」。新增状态快照节 + **实施分档 T0a/T0b**（F9-T1/T2 依赖只挂 T0a），PF 收敛为 12 项 |
| 2026-09-16 | **F9-T0 + A0 落地** | 契约升至 v3.8；用户认识模块（`UserMemory` 归属 Master）全量落地；`Capability` 接口统一三个 SubAgent 能力面、8 个 DJ 绕路工具清零；`desktopTest` 821 全绿 |
| 2026-09-17 | **F9-T1 落地** | 听歌报告（累计画像置顶 + 5×5 双轴筛选 + 六条窗口 SQL，**零 Room 迁移**）+ 遗忘唤醒（`forgotten_delivery` 表，Room v7→v8）。报告页验收通过 |
| 2026-09-18 | **F9-T2 落地** | 伙伴设置页（`AgentConfigScreen(agentRole)` 一页覆盖四 Agent + Agent 监控看板 v2）+ Agent 配置收拢（`AgentPolicyConfig` 扩展 + `resolvedFor` 出厂回落）+ 统一日志规范（`docs/LOGGING.md` + `com.hmp.log`）+ Prompt 多语言（`Lang` / `L10N_PROMPTS`）。**F9 收口** |
| 2026-09-18 | **文档 v6：F9 收口 + 语音移出** | ① **语音档（原 F9-T3/T4）移出 F9，另立 F10 语音会话**（后续独立阶段）—— 理由：B6 里唯一真正新增的传输层，需真实端点验证，与报告/设置页无耦合，留在 F9 会让已完成的族长期挂着无法验收的项。② F9 章更名「报告与伙伴设置」（文件 `f9-报告与设置.md`）③ **`docs/superpowers/`（spec + plan 两份过程稿）整合进 `f9` 章 §2.3「报告页双轴筛选重构」，目录移除** —— 过程稿的逐 Task 施工步骤不保留，只留「解决的问题 / 交付内容 / 与方案稿的偏差 / 验收结果」 |
| 2026-09-19 | **文档 v7：新增 F11 后台生命周期** | ① 用户报"RadioAgent 退后台不存活"，核查得**三条根因**：RC1 运行时无独立生命周期所有者（`MasterAgent` 懒初始化）、**RC2 Android 播放服务仅 `BIND_AUTO_CREATE` 绑定、正常路径从不 `startService`（非自持前台服务）**、RC3 电台会话零持久化（`agent-radio.md:338`「从不落盘」）。② 新增设计文档 **`../design/agent-lifecycle.md`**（根因 / 目标架构 / 三端实现 / 迁移 L1-L5 / 待决策 D1-D5）+ 另立 **F11 后台生命周期** 章节。③ 收口口径：**本地化真实工作量被高估**——不是"缺 4 个 key"，而是 agent UI 约 20 文件硬编码字符串，全量本地化属重构级工程，后置为独立阶段。④ `WindowedBundle.narrative` 死字段已删。**【同日实施 L1+L2】**：L1 落 `AgentKeepAlivePort` 端口 + `MasterAgent` 生命周期面（`updateRadioKeepAlive`）+ `MusicApplication` 启动即初始化；L2 把 `MusicPlayService` 从 bind-only 改**自持前台服务**（`ensurePlaybackServiceStarted` + `ensureForeground` 无条件前台化 + `refreshForeground` 保活条件扩为「音频在播 或 agent 活跃」）+ Android 侧 `AndroidAgentKeepAlivePort` + Koin 接线。编译验证：`:shared:compileKotlinDesktop` / `:shared-ui:compileAndroidMain` / `:android:core-player:compileDebugKotlin` / `:android:app:compileDebugKotlin` / `:shared-ui:compileKotlinDesktop` 全绿 |
| 2026-09-19 | **修复：AI 配置热监听日志刷屏** | 后台每约 5s 刷一轮 `updateAiConfig` 日志。**根因**：热监听 `combine(aiAccessMode, customAiConfig)` 两上游均派生自**全局单例 `dataStore.data`**，而播放进度持久化（`MusicController.persistCurrentPosition → saveCurrentPosition`，节流约 5s）写入**同一个** DataStore —— 任意无关写入都会让监听重发 → 无谓的 per-Agent 配置重载 + 刷屏。**修复**：上游各自与末端各加 `distinctUntilChanged()`，仅"生效 AI 配置真变"时才重载（`ChatKoinModule`）。属既有潜伏 bug（F11 的启动即初始化只是让它从 app 启动就跑、更早暴露） |
| 2026-09-19 | **修订：电台退出逻辑（消除"用户接管退出"）** | 用户反馈"播放/暂停/上一曲/下一曲会退出电台"。核查：唯一自动退出点是 `RadioSubAgent.onPause` 的 **PAUSED + resume → `exitBecauseUserTookOver()`**（上一曲/下一曲本身走 `trackChangeEvents` 不退出，但电台暂停时切歌会触发 resume 撞上同一退出点）。此行为与 `agent-radio.md` **C3**「只有用户手动关闭才是终态」**自相矛盾**（§1.1 表格第 59 行 vs C3）。**修订**：① 代码——PAUSED + resume 改为**电台跟着恢复**（`resumeByUser → resumeRadio`），删除 `onUserTookOver` 机制与 `stopRadioInternal(pausePlayback)` 死参数；② 文档——`agent-radio.md` §1.1 表格/实现要点同步（含"上一曲/下一曲不退出"显式化）；③ 新增回归测试 `userResumeWhilePaused_radioResumesInsteadOfExiting` |
| 2026-09-19 | **UI：电台卡简化为「开 / 关」两态（外部不区分停止与暂停）** | 卡片去掉暂停/恢复操作与"已暂停"文案：整卡点击在**已开启**（PLAYING / PAUSED / BUILDING）时 = **关闭**（`stopRadio`）、**空闲**时 = **开启**（`startRadio`，内部 `tryResumeRetained` 自动判断「续档」还是「新开」）。**暂停态仍在模型内保留**（跟随播放器暂停 + 后续恢复），只是**不由卡片暴露**。新增 `drawable/stop.xml` 图标（原先无）；`actionLabel` / `actionIcon` 与真实行为对齐（原「停止电台」文案配暂停图标，名不副实）。`AgentMonitorScreen` 看板仍保留显式暂停/恢复/停止（调试面，未改） |
| 2026-09-19 | **修复：电台运行日志暴露的两个真 bug** | 真机会话日志（~4.5min、3 次切歌）分析所得。① **跨线程**：agent 经 `ControllerPlaybackCommandPort.execute(PLAY)` → `MusicController.playOrResume()` **同步**摸 ExoPlayer（`isMusicLoaded`），而 agent 在 `Dispatchers.Default` → `IllegalStateException: Player is accessed on the wrong thread`，致开播"补 PLAY 起声"失败、兜底 `PLAY_BY_ID` 从头重放。修复：`playOrResume()` 把播放判定+操作整体切 `scope`（= `Dispatchers.Main`）；核查 `pauseMusic`/`seekTo`/`playWith` 早已 `scope.launch`，仅此一处漏网。② **runLoop 启动即退出**：`RadioSubAgent.runLoop` 漏设 `isActive = true`（`Enrich`/`Hello` 的 runLoop 都有），`while (scope.isActive && isActive)` 首判即假 → 同毫秒 start/exited → Scheduler 仲裁与配额 soft-stop 对电台失效。修复：补 `isActive = true`。两处编译验证通过 |
| 2026-09-19 | **修复：电台"关闭后重开不复用上次内容"** | 真机日志显示 `[复用] 跳过：当前曲目已经不是上次那份队列里的歌`。三条根因叠加：① **镜像下标错**：`applyVerdict` 用 `currentPlaylist.getOrNull(playedCount)` 取在播曲，而 `playedCount` **只在经端口发起的 USER 切歌时累加**（通知栏/锁屏/播放器切歌不走端口）→ 全档停摆为 0，镜像头永久停在**开播那一首**（日志 `[执行]（在播 87 不触碰）` 全程是 87 即铁证）。修复：每轮换批**先从播放器刷新在播 id**（同 `contextSnapshot` 铁律：在播曲目必须问播放器，不能靠 playedCount 推算），镜像头按 musicId 定位。② **队列指纹比对错**：`tryResumeRetained` ③-b 用 `queue != r.playlist.map{id}` 整体比对，但**播放器队列稠密（`replaceQueueWith` 保留已播前缀）而电台镜像被压缩为 `[在播, ...后续]`**，前缀长度不同 → **永远不等 → 复用永远被误拒**。修复：只比「在播曲**之后**的尾巴」。③ 新增回归测试 `reopenWithDensePlayerQueue_reusesWhenTailMatches` |
| 2026-09-19 | **修复：复用会话只续 `messages`、节目档案丢失（"继续对话上下文不保留"）** | 用户反馈续档后"上下文不保留"。核查：`RadioSession` 的上下文分两层 —— `messages`（原始对话历史）+ **会话档案**（`executed` 已执行 / `intents` 编排思路 / `settled` 收听台账 / `pauses` / `originMs` / 三个计数），而**档案是每轮 prompt 从会话状态重渲染**的（`## 节目档案` / `## 本档收听台账`）。原 `exportConversation` 只导出 `messages`（`exportMessages()`），**档案全丢** → 续上后档案区空空如也。修复：① `RadioSession` 新增 `exportArchive()` + `resumeFrom(messages, archive)` 恢复（含 `originMs`/`turnIndex`，保证「第 N 分钟」「TURN#」跨档不重置）；② 新增 `RadioSessionArchive` 值对象，挂到 `RadioConversation.archive`（默认 null = 旧快照退化为只恢复 messages）；③ `exportConversation` 带上档案、`tryResumeRetained` 恢复时传入，复用不再补记「开播」（上一档的「已执行」已在档案里）；④ 新增回归测试 `reopen_reusesFullSessionArchive_notJustMessages`。设计文档 `agent-radio.md` §7.1 增「快照必须带档案」小节并修正队列指纹口径 |
| 2026-09-19 | **裁决：电台不做状态持久化（F11-L4 撤销，RC3 改判）** | 用户提出"还是不应该持久化 radio 对话"。核查确认项目已有**唯一**的跨会话记忆载体 `UserMemory`（`user_profile_evidence` / `user_profile_portrait` / `user_profile_narrative` 三表，且**每次写入都写 `agent_audit_log` 留痕**、画像过谓词闭集闸门）。**裁定 D3=(b) 维持「从不落盘」，不做任何形式持久化**（含"只落队列骨架"的极轻变体），四条理由：① 会开出**第二条记忆通路（影子记忆）**——不可见、不可审计、不受闸门管，且含"哪首歌只听了 24% 就切走"这类行为推断，与 F1–F9「记忆走正门、要留痕」冲突；② 电台档案重量在**情境性**内容（队列构成/在播曲/台账），数小时后前提全错，**过期档案比空档案更糟**；③ 产品语义上"接着上次那一档"反预期（电台是临场 DJ）；④ L2 已保护活跃期进程，剩余被杀场景恰是"用户不想让它继续"的信号，重开成本又低。**文档同步**：`design/agent-lifecycle.md` §0/§1.3/§3/§5（整节改写为《「从不落盘」是决定，不是妥协》）/§6 L4 划除/§7 R4 改判/§8 D3 裁定；`f11-后台生命周期.md` 阶段族与 §2/§3/§4/§5/§6 同步，验收删「冷启动续档」并加两条经验（"技术上可持久化 ≠ 应该持久化"、"不落盘是有依赖的决策"）。**跨会话认知唯一入口 = `UserMemory`** |
| 2026-09-19 | **UI：伙伴胶囊承载电台态 + 长按停止电台；徽标圆点方案废弃** | ① **新能力**：底栏伙伴胶囊在**电台开启期间**（`radioState` = PLAYING/PAUSED/BUILDING）兼任电台状态位——图标换成电台标识（`headphones_fill`，与 `RadioCard` 同形）并**恒定高亮 primary**，**长按 = 停止电台**（点按仍=回门面，导航锚点不夺）。分层接线：`CompanionCapsule` 加 `radioActive` 参数；`BottomFusionBar` 加 `radioActive` + `onRadioStop`，长按按电台态分流（开启→停电台；未开启→原轻量浮层）；`AppRoot` 订阅 `radioState` 并接 `stopRadio()`。新增资源 `agent_capsule_radio_desc`（base + zh）。② **发现并清理死通道**：核查确认 `PresenceBus.CompanionBadge` / `badgeState` **从未有消费者**——`RadioSubAgent` 发过事件、`CompanionCapsule` 从未渲染（无 badge 参数、无 `BadgeOverlay`），且 `f8` 文档把 W-T1 记成了 ✅（**与实际不符**）。按用户裁定"徽标圆点已废弃"，**删除该死通道**（`PresenceBus` 去掉 `CompanionBadge` + `badgeFlow`/`badgeState` 与 emit 特判；`RadioSubAgent` 删 2 处 emit），并在 KDoc 写明"不要再加圆点/徽标类事件"。③ **文档同步**：`agent.md` §5.2.1/存在感四形态/场景表 1·12/改造清单/阶段对齐 全处标注废弃；`agent-radio.md` §3.3 把胶囊登记为**第二存在面**并给出 C1 合规理由、C1 决议行同步；`agent-w.md` W2-C1 改为已废止；`f8` 纠正 W-T1 与验收表里的假 ✅；`f9` 修正"遗忘唤醒=胶囊徽标"（实际提示位只有门面卡 + 对话页消息）。**编译验证**：`:shared:compileKotlinDesktop` / `:shared-ui:compileKotlinDesktop` / `:android:app:compileDebugKotlin` 全绿 |
| 2026-09-19 | **设计：电台控制台（长按胶囊的全屏交互）** | 用户裁定两点：① 胶囊电台态图标改用 **`radiowaves`**（已改，编译通过；`RadioCard` 的入口身份仍用 `headphones_fill` —— 状态与身份分工）；② 「关闭不应该是简单的长按，需要更复杂的交互」。**产出设计文档 `../design/agent-radio-console.md`（未实施）**：长按胶囊不再直接停电台，而是拉起**全屏「电台控制台」**——关闭动作移入面板且更名**「结束这一档」**。核心判断写在 §1：给长按加个确认弹窗是"廉价的重"（用户仍不知道自己在关什么），正解是**让他先看清这一档**，顺带交付电台最差异化的东西 —— 节目单（每首带主播按语）+ 主播思路。面板结构 ① 抓手/头部（主题 + 运行中·已播N·待播M）② 在播卡（封面 + 按语）③ **节目单**（`radioPlaylist` 现成）④ 主播思路 ⑤ 台账 ⑥ 吸底收档；**v1 = ②③④⑦ 零后端改动**，⑤⑥ 需新增 `RadioArchiveState` 转发。含**手势分工**（列表未到顶时下滑=滚动；到顶后续拉/头部区域起始=拖拽面板，走 nested scroll overscroll 判定）、**收档语义**（§5：音乐暂停 / 队列保留 / 可续档 ⇒ **可逆动作，不走 STRONG_CONFIRM**，门槛由面板承担）、**边界**（不做对话输入 / 不换一批 / 不接管播放控制 / 不主动出现）。设计文档已登记 `design/README.md` 索引，`agent-radio.md` §3.3 增列"第三处：电台控制台（用户唤出 → 详情面，不算打扰）"。**待拍板 D1 承载方式 / D2 收档确认方式 / D3 v1 范围** |
| 2026-09-19 | **实施：电台控制台 v1（长按胶囊 → 全屏浮层弹窗）** | 用户裁定"内容=面板、形式=弹窗"，D1/D2/D3 随之落地：**D1=AppRoot 内全屏 overlay**（不走路由）；**D2=滑动收档（slide-to-stop）**（横滑到底触发，与既有横滑切歌手势同源，不引入第二层弹窗）；**D3=v1 用现成数据**。**新增 `common/components/RadioConsole.kt`**（面板：抓手 / 头部（主题 + `状态·待播N首` + 收起）/ 在播卡（封面 + 按语）/ **节目单**（`radioPlaylist.drop(1)`，逐首带 `why`）/ 主播思路（`lastAdjust`）/ 吸底**滑动收档** + 后果文案；遮罩点外关闭 + 面板吃内部点击不穿透）。**接线**：`BottomFusionBar.onRadioStop` → `onOpenRadioConsole`（长按在电台态改为**打开面板**，不再直接停电台）；`AppRoot` 持 `radioConsoleVisible` + `radioScope` + `Esc` 关闭 + `LaunchedEffect(radioActive)`（电台消失即自动收起 = 收档后淡出）。**v1 实测关键结论**：`MasterAgent.radioPlaylist` 已含 `title/artist/why` → **零后端改动**。**刻意收敛（非遗漏）**：① 头部原设计的「已播 N 首」未显示 —— `playedCount` 私有，**不编造数字**；② **下滑关闭未做** —— 需与列表滚动作 nested scroll overscroll 判定，属独立增量；③ 台账区（需 `RadioArchiveState`）留 v2。偏差与理由已写入 `agent-radio-console.md` §10。**编译验证**：`:shared-ui:compileKotlinDesktop` / `:android:app:compileDebugKotlin` 通过；⏳ 真机核验待做 |
| 2026-09-19 | **式样：电台控制台向 `MusicDetailDialog` 对齐（弹窗基座统一）** | 用户决议"弹窗式样需向音乐详情弹窗对齐"。核查发现项目弹窗有**统一基座 `common/dialogs/base/ScrimDialog.kt`**（内部真 `Dialog`：0.5 黑遮罩 + 居中 + 点遮罩关闭 + **系统返回键天然可用**），遂把控制台从"自绘遮罩 + 自建点外关闭"改为复用该基座，并对齐 `MusicDetailDialog` 全部式样令牌：圆角 **28dp**（原 24）、外边距 **四周 24dp**（原侧 12/顶 44）、**`hazeEffect` + `hazeTintAlpha()` 容器色** + elevation 0（原纯 `surface`）、标题 **`headlineMedium`**（原 titleMedium+Bold）、文本统一 **`onBackground`**（原 onSurface/onSurfaceVariant）、**去掉进出场动画**（与参考弹窗一致）。**顺带解决 D1 遗留**："需自己接返回键"不再成立。**刻意保留 2 处偏离**（可用性，非式样）：① 保留「收起」按钮（满屏面板 24dp 外缝太薄，单靠点外关闭不好按）；② 收档滑块用 `colorScheme.error` 语义色而非 `Color.Red`。对照表与理由写入 `agent-radio-console.md` §10.1。**编译验证**：桌面 + Android 均通过 |
| 2026-09-19 | **控制台：尺寸自适应 + 未开启长按屏蔽（确认）** | ① **尺寸自适应**（用户决议"大小自适应就行无需固定大小"）：面板由 `fillMaxSize()` 改为**内容驱动高度** —— 用 `BoxWithConstraints` 取实测可用高度封顶（不写死数字），中间列表 `weight(1f, fill = false)`（内容少则面板收缩，超高才滚动）；宽度沿用项目弹窗惯例（24dp 边距内 `fillMaxWidth`，各平台 `usePlatformDefaultWidth = false`）。② **未开启时长按已屏蔽控制台**（用户提问）：分流点在 `BottomFusionBar` —— `if (radioActive) onOpenRadioConsole() else onCompanionLongPress()`，未开启时保持原语义（轻量浮层）。理由：未开启时面板内容不存在（空壳死界面）／未开启时长按已有既定语义（M1-T2）不该被夺／图标已随模式变化，用户可预期"长按行为跟图标变"。补：`radioActive` 含 BUILDING，"正在编排中"也可打开面板。写入 `agent-radio-console.md` §10.2 / §10.3。**编译验证**：桌面 + Android 均通过 |
| 2026-09-19 | **移除：轻量浮层（`AgentQuickSheet`）彻底删除，「找伙伴」统一直达对话页** | 用户决议："轻量浮窗层想要彻底移除，感觉意义不大，长按直接进入对话页面就行"。**删除范围**：① 组件文件 `common/components/AgentQuickSheet.kt`；② `AppRoot` —— `companionQuickSheetVisible` 状态、渲染块、C 键分支（改为 `openCompanionChat()`）、Esc 分支、`chatEntryBroker` 注入（无消费后移除）；③ `PlayContent` —— `quickSheetVisible` 状态、渲染块、`onOpenChat: (String)->Unit` → **`() -> Unit`**、3 处 `onChatClick` 改为直跳；④ `PlayerScreen` —— `chatEntryBroker` 注入移除，`onOpenChat` 改为纯 `navController.add(Routes.Companion.Chat)`；⑤ 文案键 `agent_quick_sheet_hint` / `agent_quick_sheet_send`（values + values-zh）。**新增统一出口** `AppRoot.openCompanionChat`（带"已在对话页则不重复入栈"守卫）：长按胶囊（电台未开启时）/ 播放页「对话」按钮 / C 键 三处一律直达对话页。**顺带修正**：胶囊无障碍文案 `agent_capsule_radio_desc` 由"长按停止"改为"长按打开控制台"（语义漂移）。**文档同步**：`agent.md` 四条总则（六→五新表面）/ §5.2.1 / §5.2.2 / §5.2.4 锚点全景（去「长按浮层」行，新增移除说明）/ §5.3 三厚度→**两厚度** / 场景表 3·6 / §7.7 本地化 / §8 组件清单（8→7 项，补录 `RadioConsole`）/ 既有件改造 `PlayerScreen` 行 / B0-B6 对齐；`agent-w.md` W2-C2（改废止）/ C5 / C8；`agent-radio-console.md` §2 状态机图与 §10.3；代码内 6 处过期注释。**编译验证**：`:shared-ui:compileKotlinDesktop` / `:shared:compileKotlinDesktop` / `:android:app:compileDebugKotlin` 全绿；全仓 grep 确认零残留 |
| 2026-09-19 | **控制台：节目单只列前 5 + 点击跳转曲目** | 用户决议"控制台只显示待播的 5 条，要加入点击跳转曲目的功能"。① **只列前 5**：顶层常量 `UPCOMING_PREVIEW = 5`，`preview = upcoming.take(5)`；**头部仍报真实总数**（`主播排的 N 首（此处列前 5）`）—— 不制造"只有 5 首"的错觉。② **点击跳转**：每行 `.clickable { onPlayTrack(track.musicId) }`（保留 ripple 作为可点线索），跳完自动收起面板。**分层**：`RadioConsole` 只抛 `onPlayTrack: (Long) -> Unit`、**不持有播放依赖**；解析与播放在 `AppRoot`（`getMusicInfoByIds` → `playlistQueueViewModel.playWith`）。**为什么是 `playWith`**：`RadioTrack` 无 `MusicInfo` 需按 id 查回（同 `HomeScreen` 先例）；`MusicController.playWith = addToPlaylist（内部 none{} 查重，不重复入队）+ playAt（在既有队列定位+播）`——**在既有队列里跳，不新建队列**，否则会把主播刚排的队冲掉。**契约精确化**：§6「不接管播放控制」补例外说明 —— 允许"跳到某首"（用户对编排的表态，非电台替用户操作），但仍**不提供播放/暂停/切歌控制**。**观测后果（诚实记录）**：跳转走 UI→控制器（项目惯例"埋点下沉到控制器实现层"），电台经结算事件 `SKIPPED_NEXT` 看到并计入台账；但**「连跳感知重排」`skipEvents` 不覆盖** —— 该缺口与"播放页切歌"同源，非本次引入。写入 `agent-radio-console.md` §3/§6/§10.4。**编译验证**：桌面 + Android 均通过 |
| 2026-09-19 | **控制台：跳转曲目后不自动收起面板** | 用户决议"点完不要自动收起面板"：去掉 `onPlayTrack` 里的 `radioConsoleVisible = false`。理由：**跳一首不代表"用完了控制台"** —— 留着才能接着看编排、继续跳下一首；面板只读，开着不干扰播放。**同时诚实记录一个表现**：面板留着，跳转后"在播"行**可能要等电台下一轮判定才刷新**（镜像头 `radioPlaylist[0]` 在换批执行时重建，不随播放器实时同步）—— 跳转触发结算 → 电台跑一轮 → replace 则重建（通常几秒），none 则保持旧值。属**既有镜像机制**表现（`agent-radio.md` §7.1），非本次引入。写入 `agent-radio-console.md` §10.4 |
| 2026-09-19 | **设计：Token 计量与窗口治理整体方案（新文档 `../design/agent-token.md`）** | 用户提出"token 是开发阶段被刻意忽略的部分"，核查后确认**不是精度问题，是三层断裂**——① **真 usage 拿到却扔掉**（`OpenAiUsage` DTO 已解析，但 `LlmEvent` 无携带通道，传输层无处上报）；② **记账只覆盖一条路径**（`recordTokens` 全仓仅 `ReActLoop:123` 一处调用 → Radio/Enrich/Hello/报告/画像全部不记账，看板数字系统性偏低）；③ **日配额设置是假控件**（`AIScreen` 滑块写进 `GlobalAgentConfig`，但 `GlobalTokenCounter.dailyTokenQuota` 是 `val` 且唯一构造点只传 timeProvider → 永远 500K）；④ **窗口按 Agent 硬编码**（Enrich 32K/Radio 64K/Hello 128K，`AiEndpointConfig` 无窗口字段）→ 换小窗口模型即**静默故障**（超窗 → API 报错 → 电台判定变 `none`，图上看不出）；⑤ **口径混淆**（一个 `estimatedTokenCount` 同时当"窗口占用"与"日消耗"）；⑥ **压缩空转**（`buildMessages` 只发 `takeLast(6)` → 压缩分支几乎永不触发，且摘要不回注）。**方案**：把计量提升为一等公民——`LlmEvent.Usage`（真值唯一通道）→ 进程级 **`TokenMeter`**（唯一记账口）→ 分发到「日消耗（prompt+completion）」与「窗口占用（仅 prompt）」**两个必须分开的量**；估算降级为兜底并**打标 `measured=false`**；窗口由「内置模型窗口表 + 未知保守 32K + 端点级覆盖」解析，**超窗前置拒绝并降级**（不发出必失败请求）。**四阶段**：T1 计量打真值（地基，最独立）/ T2 配额接线与可见 / T3 窗口治理（消除静默故障）/ T4 窗口策略显式化 + 摘要回注（T5 成本可见后置）。含验收剧本 S1-S6 与待拍板 D1-D5；**首次写明**项目里存在**两套并存的上下文管理**（历史型 vs 电台的**重渲染型**，后者不依赖 history，T4 只对前者有效）。待拍板 D1-D5，**未实施** |
| 2026-09-19 | **裁定：上下文上限默认 100K + 用户可设上限与压缩方式（`agent-token.md` D3/D4/D6 落定）** | 用户裁定"上限可以有一个默认值 100k，然后允许用户设置上限和上下文压缩方式"。**先补实测判据**（§1.1，全部常量可查）：电台开播 ~6,000（`LIBRARY_TOKEN_BUDGET 6,000 ÷ TOKENS_PER_TRACK 28` → 214 行上限，真机日志 `189/189 全量`）、电台每轮 ~4,500（`TURN_CANDIDATE_ROWS 40`）、对话典型 ~8,000（送模型历史仅 **30** 条）、对话极端 ~30,000 → **峰值 1–3 万 token**。**关键推论**：现有三个硬编码窗口（32K/64K/128K）是按"模型能力"填的，与真实用量差 4–10 倍，**作为护栏永不触发**（静默故障成因）。**设计落定**：① 有效窗口 = **`min(模型窗口, 用户上限)`** —— **①模型窗口负责"兼容小窗口模型"，②用户上限只做"单次不超这么多"的自我约束**，两个输入分开后宽裕的默认值才无害（`GlobalAgentConfig.contextCapTokens = 100_000`，可调 8K–200K）；② 三个 Agent 硬编码窗口**删除**（峰值都远低于 32K，分档无依据）；③ 新增 `contextCompaction`（`RECENT_N` / `SUMMARY_RECENT_N` / `FULL`）+ `historyKeepCount`；④ **压缩必须与 cap 解耦**（现状 `windowUsage ≥ 0.85` 驱动 → 上限 100K 时 85K 永不达到 → **该设置永不生效 = 假控件 2.0**，与"日配额"同一模式）：cap 是**硬约束**，压缩是**组装期策略**；⑤ 压缩设置需在**两处**消费点生效（`AgentContextBudget.recentMessagesToKeep` + `ChatAgentGateway.buildHistory`，现状 6 与 30 两个魔数写死在不同文件）。含下限联动降级（曲库预算按比例缩，不拒绝）；验收补 S7/S8。**未实施** |
| 2026-09-19 | **补充：模型窗口的四层取值链（`agent-token.md` T3 / D3）** | 用户问"配置好端点之后能获取到使用模型的最大上下文窗口吗"。**事实核查**：**OpenAI 官方与 DeepSeek 官方的 `/models` 都不返回窗口**（DeepSeek 官方文档明确只有 `id`/`object`/`owned_by`；OpenAI 基线 schema 亦然 —— 社区专门维护手工窗口表补此缺口），而 **OpenRouter 返回 `context_length` + `top_provider.context_length`**、**vLLM 返回 `max_model_len`（即服务端校验用值）**、AI/ML API 类聚合平台与 AxonHub 类网关（`?include=all`）也返回。**故 D3 由"内置表 + 兜底 + 端点覆盖"细化为四层取值链**：用户覆盖 → **端点探测** → 内置表 → 保守 32K；**探测与内置表两层都要有**（只做探测会让最常见的 OpenAI/DeepSeek 端点误落 32K 兜底）。**两个关键坑**：① **"模型理论窗口" ≠ "服务端实际允许值"**（OpenRouter 模型级 `context_length` vs provider 级；vLLM 的 `max_model_len` 是裁剪后生效值）——**护栏必须用后者**，否则照样超窗；② `/models` 是"每模型一个窗口"的列表而 agent 只用其中一个 → 探测值**按模型名存**（新增 `modelWindows: Map<String, Int>`，**不动** `availableModels: List<String>`，因其被模型选择器与 iOS bridge 引用）。**实现**：零额外请求（复用 `AiSettingsViewModel` 配置时已在调的 `fetchModels`）+ 探测失败静默落第 3/4 层。**现状：连返回了都被丢掉** —— `MultiProviderApiAdapter.kt:157` 只 `map { it.id }`，`ModelItem` 仅 `id`/`owned_by`；加字段安全（三端 `createJson()` 均 `ignoreUnknownKeys = true`）|
| 2026-09-19 | **简化（撤回上次决定）：窗口固定 64K、降级不开放配置（`agent-token.md` T3/T4/D6 重写）** | 用户裁定"**规定用户配置的端点支持的上下文窗口最小为 64K，agent 默认以这个窗口来消费，上下文的降级为不支持自定义**"。**这撤回了同日早先"默认 100K + 用户可设上限与压缩方式"的决定**（D6 作废、D3 由"四层取值链"改判为固定常量）。**理由**：既然 OpenAI / DeepSeek 官方**都不返回窗口**（动态获取不可能全覆盖），做"探测 + 多级回退"仍留空洞，不如**规定下限 + 固定假设**。**落地**：① 窗口 = `EngineDefaults.AGENT_CONTEXT_WINDOW = 64_000` 单一常量，三个 Agent 硬编码删除；② 不再做探测/内置表/端点覆盖/`modelWindows` 缓存（T3 的事实核查**留档备问**，连同"理论窗口≠服务端实际值"两条坑）；③ `GlobalAgentConfig` **不加**任何上下文字段，设置页**不加**"上限"/"压缩方式"（**一并消掉了上一版"100K 上限让压缩永不生效"的假控件隐患**）；④ 降级为**内部固定**（曲库预算按比例缩 + 历史裁剪 + 拒绝走本地保底）。**⚠️ 唯一必须保留的兜底**：因为拿不到窗口，**"≥64K"无法在配置时校验** → 超窗仍可能发生 → **必须让"超窗"显式可辨、不得表现为"模型判定 none"**（这是"不支持自定义"成立的前提）。**另**：超窗路径在 64K 下几乎不执行，故**必须用单测直接覆盖**，不允许当死代码留着。验收改 S7（常量而非设置项）/S8（超窗可辨）/S9（安全网被验证）|
| 2026-09-19 | **立阶段：新增 F12 Token 计量与窗口治理（新章节文件）** | 完成 `agent-token.md` 的设计并定稿后，按项目纪律**补立阶段章节**（此前只有设计文档、taskbook 里无对应章节，扫进度表会漏）。**新增 `f12-token计量与窗口.md`**：阶段定位（六断层）、交付内容（四交付）、任务清单 **F12-T1..T5**、待拍板 D1–D5（D3 已定/D6 作废）、严格不做什么（8 条）、验收 S1–S9、经验与踩坑（7 条，均来自 2026-09-19 诊断）。**README 同步**：状态 v7→**v8**、生命线图接入 F12、依赖要点（依赖 F3/F5/F9-T2；**与 F11 无依赖、可并行**）、进行中/待做、章节索引、后续阶段表、§6 挂起参数（增 `AGENT_CONTEXT_WINDOW = 64000` / 历史保留条数 / 日配额）、§7 风险表（增 5 行：假控件、超窗静默故障、窗口前提无法预校验、估算高估导致误杀、安全网无证据）|
| 2026-09-19 | **深核：token 计量现状（断层 2 的后果被大幅强化）** | 用户问"token 计量现状是什么样的"，逐环核实后**发现原表述"数字偏低"严重不足**。① **真 usage 断两次**：请求侧 `OpenAiStyleRequest` **无 `stream_options` 字段**（流式请求压根不要求端点回 usage）；流式响应 `OpenAiStreamChunk`（`ApiDtos.kt:96-99`）**只有 `id`+`choices`，连 `usage` 字段都没有**；非流式 `OpenAiStyleResponse.usage`（`:81`）解析进对象但**全仓只有 DTO 单测引用**（死字段）。② **四条 LLM 路径互不相同、只有第一条入账**：Master 走 `ReActLoop`（`MasterAgent.kt:1638` **全仓唯一实例**）✅ / Enrich 走 `contextBudget.callLlmText`（`EnrichSubAgent.kt:500`）❌ / Hello 走 `callLlmText`（`HelloSubAgent.kt:1827`）❌ / **Radio 判定自建 `LlmCallExecutor()` 并直接取 `contextBudget.llmClient`**（`RadioSubAgent.kt:1005-1020`，**连 budget 包装都绕过**）❌。③ **最严重后果**：`AgentScheduler.decideEnrichState()`（`:169-174`）用 `tokenCounter.shouldStop(0.9)` 管 Enrich 暂停，而 Enrich 消耗不入账 → **`quotaOk` 恒真，闸门对自己最大的消费者永远开着**。④ 估算口径澄清：ReActLoop 的估算**含 system prompt**（`:71-76`）但**不含工具 schema**（27 个原子工具每次全量发）→ **高估与低估同时存在于同一次调用**。设计文档 §1 与 F12 §1/§7 已同步强化 |
| 2026-09-19 | **裁定：分账账本用明细表 + 永久保留；配额候补（`agent-token.md` D7 立/D2 候补，T1/T2 重写）** | 用户明确最终目标"**能统计各 Agent × 各端点的 token 消耗**"，并裁定"**永久保留吧先**"、"**token 限额啥的也可以先放开，候补**"。**D7（账本形态）立为已定**：选 **`(a) 明细表`** 而非 `(b) 日桶累计` 或 `(c) 只有全局累计一个数` —— 决定性理由是"**明细是累计的超集，只存累计不可逆**"，且两者成本几乎相同（同一份 Room 迁移 v8→v9、SQLite 体积量级相当）；项目还存在"只有分时能回答"的问题（判断 Enrich 是否趁夜间跑整库）。**体积估算**：~150 B/行 × 常态 ~300 行/天 ≈ **16 MB/年** → 判为可承受，故**不引入清理任务、暂不做日桶固化**（留"行数软上限"作后手）。**T1 扩为"计量打真值 + 落账本"**：新增 Room 表 `token_ledger`（字段 `agent_id`/`endpoint_host`/`model`/`prompt_tokens`/`completion_tokens`/`measured`/`created_at`，**只存 host、不存 apiKey 与完整 URL**）；补两处缺口（请求侧 `stream_options.include_usage`、`OpenAiStreamChunk` 缺 `usage` 字段）；**把 `RadioSubAgent.askJudge` 自建路径并入收口**（它绕过 budget 包装，是第 3 条漏收路径）。**T2 改判为"分账视图"**：三维度聚合（按 Agent / 按端点·模型 / 按时间），**看板数字改由明细聚合、不再依赖 counter**（于是配额候补不影响分账准确性）；**配额接线（`dailyTokenQuota` 可变 + 热更新 + 熔断读真值）降为 T2b 候补**，并记明**搬回前必须先修「Enrich 熔断失明」**。**边界新增**：token 明细**不喂给任何 LLM、不进 `UserMemory`**（与 F11「电台不落盘」不冲突 —— 那条管喂给模型的状态，这条是给用户看的诊断数据）。验收补 S10–S14（分账齐全 / 分时可查 / 不泄密 / 体积可承受 / Room 迁移）|
| 2026-09-20 | **UI 收敛：Agent 看板 + AI 设置页**（随 `ba25365` 落库） | **看板**（`AgentMonitorScreen`）：卡片定为**每行两个、同行等高**（`Row(IntrinsicSize.Max)` + `fillMaxHeight`）→ 进一步收敛为**固定六行**（图标/名字/状态/当前动作/Token/日志，空值渲染占位「暂无」而非不渲染，保证高度不随数据抖动）；运行日志按 Agent 分桶内嵌、只显示条数，明细改弹窗（对齐 `MusicDetailDialog` 的 haze 式样）；接入四个 Agent 图标（48dp 居中，图标资源从 `androidMain/res` 移到 `composeResources` 三端统一）；**移除假控件配额滑块、分时柱状图、按模型用量卡**；新增各 Agent Token 分账（`ledgerDao.sumByAgent`）与顶部累计同源；**新增共享令牌 `ui/agent/AgentVisuals.kt`**（图标映射 / 低饱和状态色板 / 状态文案），看板与设置页共用一份。**AI 设置页**（`AIScreen`）：分区标题正式化并放大（`titleLarge` + SemiBold；身体素质→**AI 接入方式**、嗓音与耳朵→**语音对话**→再并入「语言和语音」）；**「AI 接入方式」默认折叠**（端点属"配一次就不管"，铺开会把 Agent 入口挤到折叠线以下）；「Agent 管理」改为**一行四个快捷入口**（图标 34dp + 简称 + 运行状态角标）；清除全页 emoji；删掉与子页面重复的参数摘要/温度/语言三行。**子页面**（`AgentConfigScreen`）：去掉首次组合时的 `runBlocking`（改 `LaunchedEffect` 异步加载）、`policyConfig` 改为可变状态（修 reset 换新实例后 UI 仍指旧实例的"改了没变化"）、**信任档位与 Agent 启用开关下沉到子页**（此前主页面与子页都没有入口）、Prompt 编辑改为「出厂默认 + 用户覆盖 + dirty 追踪」。**随重写移除的两区**：`LoadMusicExtraInfo`（认识进度 / 富化进度）与 `DailyRefreshSettings`（每日刷新策略）——两者是 `public` 孤儿函数、全项目零调用方，其宿主的 AIScreen 收敛为**四分区**（AI 接入方式 / Agent 管理 / 语言和语音 / 记忆管理）；「记忆管理」改为侧写展示 + 重置 + 操作日志入口。**是否恢复「认识进度」待定**（引擎侧 `enrich_*` 意图与状态仍在，仅 UI 入口消失）|
| 2026-09-20 | **重构：agent 域包结构全量重整 + 漏网反向边补正**（随 `ba25365` 落库） | **首轮**（84 文件）：`com.hmp.domain.agent` 按**分层**重排为 18 个包并声称"无反向边"——四条历史反向边（`policy→tool` `infra→runtime` `tool→runtime` `policy→runtime`[ConfirmGate]）下沉到新叶子 `port/`；`tool/` 拆「协议 `tool/spec/` + 装配根 `tool/ToolCatalog.kt`」；`runtime/sub/` 按 agent 拆 `shared`/`hello`/`radio`/`enrich`（先抽 `shared/` 以避免 hello→radio 新环）；`Lang`→`runtime/i18n/`；通用 `model/` 撤销按职责归位。**次轮（本次补正）**：复核发现**首轮漏掉一条真反向边** —— `policy/AgentPolicy.kt` 的 `resolvedFor()` 用**全限定名**引用 `runtime.ResolvedAgentConfig` / `runtime.RuntimeParams`，`TrustLedger` / `AgentPolicy` 又 import `runtime.EngineDefaults`，于是 `policy ↔ runtime` **成环**（首轮只是把 `EngineConfig.kt` 拆成两个文件、宿主仍是 `runtime/`）。**处置**：新建叶子包 **`config/`**，收「出厂默认常量 + 配置数据模型」（`EngineDefaults` + `ResolvedAgentConfig` / `RuntimeParams`，判据 = **消费方同时含 policy 与 runtime ⇒ 必须位于两者之下**）；删死函数 `defaultTrustLevelFor`（零调用方，且是 `config→policy` 的唯一来源）；`EngineDefaults` 去掉未使用的 `TimeProvider` 导入；清 `port/ConfirmGate.kt` 的同包自引用导入。**验收**：剥除注释后的包级边**全部单调**（`port`/`config` 为零内部依赖叶子，反向边 **0 条**）；`:shared:compileKotlinDesktop` + `:shared-ui:compileKotlinDesktop` 通过；`:shared:desktopTest --rerun-tasks` **828 例 0 失败**。**沉淀（重要）**：包级依赖自查**必须连全限定名引用一起扫**，只扫 `^import` 会漏掉"只差一个 import"的边——这正是首轮漏检的机制 |
| 2026-09-20 | **立阶段：F13 可见性与接口面收敛（新章节文件）** | 用户问"agent 域是否足够健壮可维护"，实测得：**分层（0 反向边）与类内聚度达标**，但**可见性收口 / 公共接口面 / 测试覆盖分布**三项为短板。核实到根因是**Kotlin 没有「包内可见」（无 package-private）** —— 包分层只是约定，不携带访问控制能力，故`internal` 仅占 5.3%，UI 可直连引擎内部。**立 F13** 把「内部实现」与「对外接口」在类型系统里分开，使分层从「人工审计」升级为「编译期保证」。判据两条：① 可见性看「跨不跨模块」，**不看重不重要**（`AgentScheduler` 关键但要 internal，`RadioTrack` 微小但必须 public）；② `internal` 边界 = **现有 Gradle 模块边界**，不新建模块。收口前实测（**import 口径**）：跨模块公开 **68** / 仅模块内可见 **135**；已知越界 3 处（`ContextAssembler` / `EnrichSubAgent.EnrichProgress` / `resolvePrompt`）。分四阶段：① 收口 runtime 实现层 38 个顶层类型 → ② 分拣 `MasterAgent` 47 个成员方法（**编译反推法**）→ ③ 抽门面 + 拆 4 个巨型类 → ④ 三端验证。设计依据 `../design/agent-architecture.md` §可见性与接口面 |
| 2026-09-20 | **F13 阶段 1 完成：收口 18 个顶层声明 + 阶段 2 预演**（随 `ba25365` 落库） | **编译反推法**实证：38 个候选全标 `internal` → 编译只报 **23 处**「public 暴露 internal」→ 逐个判定后**净收口 18 / 被迫公开 20**，Desktop + Android + iOS 三端编译绿。净收口的 18 个以**电台内部词汇表**为主（`RadioSession` 一家 9 个，内聚性最好的一组），引擎循环件（`ReActLoop` / `ToolCallExecutor` / `LlmCallExecutor` / `CollectedLlmResult`）紧随。**两条关键发现修正了原计划**：① **`MasterAgent` 的装配位于 shared-ui**（`ChatKoinModule.kt:89`）—— UI 直接 new 领域对象并注入 `GlobalTokenCounter` / `TokenMeter` 等引擎内部件，这是 20 个「被迫公开」的主因，也是真正的耦合点；② **类的可见性带动整簇**（`AgentScheduler` public ⇒ `AgentPriority` / `AgentRegistration` 必须 public）⇒ 收口单位是「可见性簇」而非单个类型。阶段 2 预演（只读）：`MasterAgent` 58 个公开成员中 **37 个跨模块在用 / 21 个零用**，并把前者再二分为「合理 UI 接口」与「引擎机制泄漏」（`lifecycleScope` / `scheduler` / `tokenCounter` / `initialize` / `ensureProfileReadyForFirstTurn`）—— 后者是阶段 3 抽门面的靶子 |
| 2026-09-20 | **F13 阶段 3a+3b 完成：装配迁入 `:shared` + 调度计量簇整簇收口 + 抽计量门面** | **3a（用户选"彻底版：连装配位置一起改"）**：把 `MasterAgent` 的整套 Koin 装配从 `shared-ui/.../chat/ChatKoinModule.kt`（−80 行）迁入 `shared/.../di/SharedModules.kt`（+86 行）—— 这是阶段 1 查明「20 个被迫公开」的根因，装配不迁则调度/计量一整簇永远收不了口。仍在 shared-ui 注册、经端口接口解析的 bean（`PlaybackCommandPort` / `NowPlayingContextProvider` / `AiExtraEnrichPort` 适配器、`ToolRegistry` / `PolicyGuard` / `SessionStore` / `PresenceBus`）保持 public 不变（Koin 惰性求值，与模块加载顺序无关）。**3b**：55 个 `MasterAgent` 成员一次性标 `internal` → 编译器报 67 处（去重 33 个成员名）⇒ **净收口 21 / 被迫 public 34**（门面清单由编译产出，可复跑校验）；引擎机制顶层类型收口 7 个（`AgentScheduler` / `AgentPriority` / `AgentRegistration` / `SystemConditions` / `StopSignal` / `SchedulerStopSignal` / `TokenMeter`）+ `GlobalTokenCounter`；抽门面 1 处（`TokenSnapshot` 提为公开顶层数据类 + `MasterAgent.tokenUsage`，判据：**外部要"看到什么"而非"能做什么"时，抽数据形状比抽方法更省事**）；三个构造器收 `internal`（`AgentContextBudget` / `EnrichSubAgent` / `HelloSubAgent`，类可传递可读但外部造不出来）。阶段 2 判定的 5 处引擎机制泄漏，**4 处消除**（`lifecycleScope` / `scheduler` / `tokenCounter` / `initialize`），`ensureProfileReadyForFirstTurn` 判定为**正当接口**（Gateway 首轮要同步拿最新画像）。**新教训**：存在"第三个模块"—— `:android:app` / `:desktop:app` 都调 `MasterAgent.close()`，只编译 shared-ui 时是绿的，编译 Android 才炸 ⇒ **跨模块可见性判断必须把所有消费模块都编一遍，"编译绿"才成为证据**。验证：`:shared` + `:shared-ui` + `:android:app` + `:desktop:app` + iOS 五任务编译 **0 error**。阶段 3c（拆 4 个巨型类）判定为独立后续：本阶段目标（判断影响面）已达成，且拆类前置条件（接口面冻结）现已具备 |
| 2026-09-20 | **F13 阶段 4（部分）：五任务编译 0 error + 测试 828 全绿** | 编译五任务：`:shared:compileKotlinDesktop` · `:shared-ui:compileKotlinDesktop` · `:android:app:compileDebugKotlin` · `:desktop:app:compileKotlinDesktop` · `:shared:compileKotlinIosSimulatorArm64` —— **全部 0 error**（已核对各任务确实执行，非 UP-TO-DATE 跳过）。测试：`:shared:desktopTest --rerun-tasks` → **828 tests / 0 failures / 0 errors / 0 skipped**（从 `build/test-results/desktopTest/*.xml` 统计，103 个测试类）。剩余未做：包级反向边复测 · 3 处已知越界点处置 · DI 图人工核对 · 阶段 3c 拆巨型类 |
| 2026-09-20 | **F13 阶段 4：反向边复测 → 发现并修掉 1 条既存反向边** | 复测脚本报出 `tool.spec → tool`：`tool/spec/ToolRegistry.kt:72` 的成员方法 `bindCapabilityTools()` 直接 `new` 了 `tool.CapabilityStatusTool`。**这条边在 09-20 包结构重整时被漏检 —— 当时"17 包 / 0 反向边"的结论是假阴性。** 两个漏检原因：① 边写在**函数体内的全限定名**里（不是 import 行），只扫 import 看不到；② 扫描脚本把 `package com.hmp.domain.agent.tool.spec` 声明行按"取最后一段之前的包"处理，得到 `…agent.tool`，**凭空造出一条假边** —— 同一脚本既漏真边又造假边。处置：`bindCapabilityTools` 移为 `tool/ToolCatalog.kt` 的扩展函数（装配归装配文件），协议文件不再认识实现类；3 处调用点同步（`MasterAgent.initialize()` 加一个 import、`AgentToolsTest` 同包无需改、定义处）。**复测：19 条跨包边 / 0 条反向边。** 判据沉淀：**"0 反向边"这类结论必须能复跑出证据，且脚本本身要先被质疑**（本项目的 Bash `grep` 有已知静默漏报，务必用专用检索工具） |
| 2026-09-20 | **F13 阶段 3c：拆巨型类（部分）—— 4 类拆出 7 个新文件** | 切分线定在「**这段代码需不需要这个对象的状态**」：不需要 → 外移成 `object`（纯函数/常量表）；需要 → 留在类里。**状态搬不走** —— 搬它就得把开关它的方法一起搬。结果：`EnrichSubAgent` 1179→**591**（`EnrichPrompts` 304 + `EnrichResponseParser` 275）· `HelloSubAgent` 1969→**1801**（`HelloGreetingProfiles` 236）· `RadioSubAgent` 1869→**1734**（`RadioModels` 113 + `RadioSeedKeywords` 49）· `MasterAgent` 1811→**1753**（`ChatIntentRules` 106）；另抽三 Agent 共用的 `runtime/JsonText`（79）。**判据（本轮最有复用价值）**：*`private` 保护的是「不变量」，而无状态纯函数与常量表没有不变量* ⇒ 纯函数从类里搬到同模块 `object`（`private`→`internal`）**不构成可见性回退**；反之**带状态的成员不能这样搬**。**顺手修掉 3 处"写着共用、实则各抄一份"的重复**：① `extractJsonBlock`（Enrich/Hello 各一份，后者注释还写着"共用"）② 电台风格词表（`isRadioIntent`/`extractSeed` 各一份**逐字相同的 13 行**）③ 问候类型属性（原先散在**四个平行分支**，加一种类型漏改不编译报错 → 收敛为 `GreetingProfile` + **穷举 `when`**）。**未拆**：各类的生命周期与编排（与状态强耦合；正解是**把状态与其转移一起搬进协作类**，如已有的 `RadioSession` 718 行）。验证：每步单独编译 `:shared` 4 轮均 0 error；收尾六任务 0 error + **828 tests / 0 failures / 0 errors**（`--rerun-tasks`，103 个测试类） |
| 2026-09-20 | **F13 阶段 5：3c 后续可维护性收口（五项）** | 前四阶段解决"影响面可判断"，阶段 5 补它没覆盖的三件事：**抽出来的东西没人测 / 同一件事有多个写法 / 对象状态没有归属**。**① 补单测（先做，零风险）**：3c 抽出的 7 个文件在 `commonTest` 里**零命中**，遂新增 **3 个测试类 / 37 例**（`ChatIntentRulesTest` / `HelloGreetingProfilesTest` / `EnrichResponseParserTest`），**首跑即抓出 1 个真缺陷** —— `extractJsonArrayElements` 的"是否被数组包裹"判定只看 `indexOf('[') >= 0`，而 Round 1 三个字段本身就是数组 ⇒ 裸对象必含 `[` ⇒ 恒判为有包装 ⇒ 切出 0 对象 ⇒ **兜底分支是死代码**，后果是模型漏外层 `[...]` 时**整轮富化结果静默丢光**。修复＝加入位置比较（`[` 须在第一个 `{` 之前）+ 修掉兜底把对象序列当单对象。**② `builtinIntent` 表驱动**：171 行 / 9 个同形分支 → `BuiltinIntent` 表（**表的次序即正确性**：`radio_stop` 须在 `radio_start` 前；"停电台"同时命中强触发词"电台"）+ 单一执行器，`answered`/`unavailable` 两个工厂消掉 18 处 `AgentResult(...)` 样板。**③ LLM 调用形态收敛**：`LlmCallExecutor` 无状态却写成 `class` 且被 4 处各自 `new` → 改 `object`；新增 `AgentContextBudget.callOnce(...)` 把 `agentId`+`tokenMeter` 收进 budget（返回 internal 的 `CollectedLlmResult` ⇒ 编译器报 `'public' function exposes its 'internal' return type`，**可见性判定仍由编译产出**），Radio `askJudge` 改走它，4 处 `new` 清零。**⑤ Hello 会话状态收敛**：12 个散落的 `@Volatile private var`（36 行声明区）→ `private class HelloSessionState`，36 处引用加 `session.`；`reset()` **加过又删掉**（零合法调用点：挂 `shutdown()` 会改行为契约；`clearAllMemory()` 清的是 `UserMemory` 三表、与 `HelloMemory` 两个存储；且防重复缓冲本就特意镜像进 DAO+memory ⇒ 设计意图没有"清空"这条路）。**④ 门面：只补 1 处，`AgentFacade` 判为不做** —— 补 `MasterAgent.helloCards` 只读门面（UI 原拿 `helloAgent()` Agent 本体）+ `helloAgent()` 收 `internal`；不做的原因：**该短板的一半已被 3b 用更强手段消掉**（21 个成员收 `internal`，编译器直接拒绝 UI 触碰，强于"另建门面类"这种仍是约定的做法），剩下 34 个成员**全部有真实 UI 调用方** ⇒ 照搬就是 **34 个方法的 1:1 委托、可达面一个都没缩小**，且"改签名不知谁受影响"已由编译器解决。**判据沉淀：门面只有在「可达面比原类更窄」时才有价值；1:1 镜像的门面是纯粹的间接层。** 真要拆的正解是按关注点做**接口隔离**。**顺带修掉 7 处过期窗口注释**（`AgentContextBudget(32K/64K/128K)` 散在 Master 6 处 + Enrich 1 处，而窗口早已统一为 `AGENT_CONTEXT_WINDOW = 64_000`）。**验证**：六任务 `BUILD SUCCESSFUL` + `build/test-results/desktopTest/*.xml` 统计 **865 tests / 0 failures / 0 errors / 0 skipped（106 类）**，与 828+37 / 103+3 吻合 ⇒ 新用例确实执行 |
| 2026-09-21 | **F13 阶段 6：人工核对 DI 图 —— Koin 不会绕过编译期可见性** | 详见章节 §3.6。**三条实证**：全仓零 Koin 注解 / 无 `koin-annotations` / 无 KSP·KAPT；装配全为 DSL，`get<T>()` 是 `reified` 泛型函数（类型编译期实例化，非字符串查表）；三端 `startKoin` 全传 Kotlin 对象引用 ⇒ `internal` 类型被模块外 `get<T>()` 必被编译器拦住，**「编译绿」对本项目是充分证据，F13 全部收口结论成立**。**顺带修掉 1 处真缺陷**：`androidPlatformModule` 与 `sharedModule` 各注册一份**逐字等价的 `Json`**，靠默认 `override = true` 静默覆盖 —— 今天无害，但只改一处就会让 Android 与 Desktop/iOS 的 Json 配置**静默分叉**（编译期运行期均不报错，阶段 1 装配迁移残留）；处置：删 Android 侧重复注册，三端消费者行为不变。**验证**：`:shared:compileAndroidMain --rerun-tasks` 0 error，三端 Json 注册数 = 平台模块各 0 + sharedModule 1。**判据沉淀**：「重复定义 + override 静默覆盖」比「缺失定义」更危险 —— 缺失当场报错，重复表现为"一切都好"直到漂移；核对脚本报出的唯一交集是自己正则造的假阳性（`GlobalTokenCounter` 推断注册被误捕）—— 再证 **结论依赖脚本，脚本本身要先被质疑** |
| 2026-09-21 | **F13 阶段 7：agent 域测试补齐（纠偏误报 + 14 类 106 例 → 961 全绿）** | 起因：此前盘点误报「32 个 agent 类零覆盖」。Glob 交叉比对 75 个 agent 源文件与既有测试类后确认**既有测试已覆盖大部分**，真实缺口收敛为三块：① 子代理编排骨架（Hello / Enrich）；② **Batch B 十个工具**（library_artists / albums / songs_by_artist / album / tags / songs_by_tag / playback_enqueue / agent_budget / song_tag_user_add·remove）；③ 基础设施（PresenceBus / SessionStore / CardPool / EngineDefaults / GlobalAgentConfig / Lang）。**新增 14 个测试类 / 106 例**：本会话 9 类 71 例（PresenceBusTest 4·SessionStoreTest 7·CardPoolTest 8·EngineDefaultsTest 6·GlobalAgentConfigTest 3·LangTest 14·EnrichSubAgentTest 6·HelloSubAgentTest 4·BatchBToolsTest 19）+ 本 effort 前序会话 5 个 runtime 类 35 例（AgentSchedulerTest 7·GlobalTokenCounterTest 4·LlmCallExecutorTest 7·ReActLoopTest 8·ToolCallExecutorTest 9）；其中 5 个 runtime 测试文件在本会话**重写替换**（此前版本未提交、无 git 记录不可恢复，以现行为准 —— 透明记录）。配套新增 fakes `AgentEngineFakes.kt`；唯一 open 化 `FakeAgentMusicRepository`（供 Enrich 测试子类化，既有测试零影响）。**internal 类测试路径**：同模块 commonTest 可见 internal + 全默认参数构造 + FakeLlmTransport 脚本化传输 + open fake 子类化；`HelloSubAgent` **只测降级契约、不测 runLoop**（常驻协程在 runTest 虚拟时间挂死，属被测对象性质而非测试缺口）。**三轮修复判据沉淀**：① gradlew-lowmem 自带 `--console=plain` 外部不得再传；② `TimeProvider` 是 typealias 无 SAM 构造 / `SystemConditions` 是顶层 internal interface 非嵌套类 / pause·resume 是 suspend 须包 runTest；③ 断言层：**SharedFlow 无 replay 测试必须 backgroundScope + UNDISPATCHED 先订后发**（普通 launch 未取消 → UncompletedCoroutinesError）；**流中途异常丢弃全部 partial 文本**（toList 语义，text="" 异常进 failedMessage）；**工具拒绝非静默**（appendToolResult 回写「（用户拒绝执行，已跳过）」）；**CardPool.replace 默认 setFocus=true 写当前时间戳 → 全对象断言必败须字段断言**；**基类 runState 初值 PAUSED ≠ EnrichProgress.IDLE 的 UNREGISTERED**（UI 兜底语义不同源）。**验证**：`:shared:desktopTest` → **961 tests / 0 failures / 0 errors / 0 skipped（119 类）** = 既有（git 跟踪）105 类 855 例 + 本 effort 新类 106 例；第三轮 8 处失败全在新文件内、修复后全绿，**既有测试两轮对照零回归** |
| 2026-09-21 | **git 历史重写：F11+F12 合并为一笔 + 幽灵哈希全量校准**（F13 收尾） | 用户裁定"F11 和 F12 的内容混杂的问题，如果可以拆开就拆开两笔提交内容分明，不行的话就合并为一笔"。核查 `git show 84882a3 -- MasterAgent.kt` 证实 **hunk 级交错**（同一文件内既有 F11 的 `keepAlivePort` 保活参数/`updateRadioKeepAlive()`，又有 F12 的 `tokenMeter` 计量参数）→ 文件级拆分会把混合文件整文件塞进一笔，"内容分明"不可达 → 按兜底规则**合并为一笔** `c1f188a`（65 文件 +4822/−490，消息内 F11 L1–L5 / F12 T1–T4 / 电台控制台 UI 分节）。重写方式：`git reset --hard e44d857` + `cherry-pick --no-commit` 逐笔重建 + **双树不变式校验**（合并笔树 == 原 `8717357` 树；终树 == 备份分支，diff 为空）；备份分支 `backup/pre-merge-0488cd7`。**顺带系统性校准幽灵哈希**：此前文档记录的 `07ab5445` / `ab2b9e87` / `06354424` / `a1cca987` / `091a565d` 在 git 中从未存在（工作从未单独落库），全部校准到真实提交（README 6 处 + f13 章节 2 处 + 残留叙述 1 处）。**推送注意**：分支与 origin 分叉，需 `--force-with-lease` |
| 2026-09-21 | **立阶段：F14 Agent 界面自适应与多语言（新章节文件）** | 用户提出两项重要工作："agent 涉及页面的自适应显示适配" + "字符串资源的收拢与多语言"。三项决策：**先 T1 UI 适配、后 T2 字符串收拢**（用户明确顺序）/ **14 语言本轮全量** / **全部 agent 页面**。实测数据支撑立章：WindowSizeClass 设施已就位（`common/layout/WindowSizeClass.kt`，Material3 三档断点 Compact <600 / Medium 600–840 / Expanded ≥840 dp）但 **agent 页面 0 消费**（仅 RadioConsole 用 BoxWithConstraints 自适配）；字符串资源 14 语言目录齐备（values=英文默认 + ar/de/es/fr/hi/id/ja/ko/pt/ru/th/vi/zh，各 474 键）而 `ui/agent` 子树**硬编码中文 344 处**（全 ui 树 426 处），分布：AgentConfigScreen 84 / HelloCard* 10 文件 ~66 / AIScreen 41 / RadioConsole 20 / ControllerAgentPorts 17 / AgentMonitorScreen 16 / AuditLogScreen 10 / RadioCard 10 / CompanionBubble 10 等。T1 八页断点行为：对话页限宽 ~640dp 居中 / 看板 Expanded 三列两行 / 表单限宽 ~600dp（Prompt 编辑 720dp）/ 审计页 ~640dp / 通知条保持全宽内部限宽 / Hello 卡片与电台控制台仅回归验证；限宽统一 `widthIn(max=…)` + 水平居中，不引入新依赖。T2 键命名空间 `agent_monitor_* / agent_config_* / chat_* / companion_* / radio_console_* / hello_card_* / audit_log_*`；动态拼接改位置占位符 `%1$s`/`%1$d`；**甄别铁律：只收 UI 面向文案，发给 LLM 的消息、领域回退、日志文本保留原样（翻译会污染模型输入）**，代码注释标注「LLM 面向，勿本地化」。新章节 `f14-自适应与多语言.md`；README 状态 v9→v10 |
| 2026-09-21 | **F14 范围扩展：立 T3 组件基建对齐 + AgentNoticeBar 死组件调查** | 用户问"还有没有类似这两种（自适应/多语言）的基建欠账"，对十项横切基建逐项 grep 排查：**五项绕行**——①表单基础件（`HMPTextField`/`MyButton` 在 agent 仅 1 处消费，`AIScreen` 9 处 + `AgentConfigScreen` 7 处直用原生 `TextField`/`OutlinedTextField`/`Button`）②空态件（`DefaultEmpty` 存在而 `AuditLogScreen` 手写 emoji `EmptyState`）③尺寸令牌（`LocalHMPDimens` 消费面 21 文件中 agent 仅 3 个，六页魔数 dp）④状态色散点（`AgentMonitorScreen:362` `Severity.Warn` 写死 `Color(0xFFB26A00)` 未进 `AgentStatusColors`）⑤AgentNoticeBar。**七项健康**（记录在案免重复排查）：ScrimDialog 零自绘 / HmpLog 零 println / TimeProvider 零直取 / RTL 全 CenterStart·End / 审计链收口 / LLM 计量已收口 / 装饰图标 `contentDescription=null` 合规。**用户裁定 ①–④ 并入 F14 → 立 T3**（执行序 T1 → T3 → T2：组件骨架先于文案收拢，避免二次迁移）；**⑤ AgentNoticeBar 三层核实为死组件**——已挂载（`AppRoot:554`）、渲染接线通但撤销链未兑现（`showUndo=false` 不传 `onUndo`）、**事件源零实装**（`SkipDetected` 全仓无生产 emit；M6-T2 设计链 `skipEvents → MasterAgent 连跳≥2 → emit` 只修了前半，`MasterAgent.kt:1115` 注释自认"等观测面落地后再接"）——补活属功能工作不并入，留待单独决断；**f8 表格"SkipDetected → ✅ 已接 AgentNoticeBar"为假 ✅，待修正**。README 状态 v10→v11 |



> **已废弃方案**（2026-08-31 初版 T 阶段）：定义 5 根脊柱但过度设计——`AgentProfile` 独立 DAO 层 / `AgentSenses` expect-actual / Scheduler 复活 / `FallbackOrchestrator` 独立类。已被第二轮重写完全取代。
>
> **已废弃编号**（2026-09-15）：早期的 `m0-foundation.md` … `w-presentation.md` 单阶段命名（14 份）已全部删除，改用阶段族命名。
>
> **已废弃目录**（2026-09-18）：`docs/superpowers/` 的 spec/plan 两份过程稿已整合进 `f9-报告与设置.md` §2.3 并删除目录；该目录是无关本仓库文档体系的临时施工稿位置，不再使用。
