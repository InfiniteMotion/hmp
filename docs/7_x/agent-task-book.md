# HMP Agent 落地任务书

> **上游**：`docs/7_x/agent.md`（设计总纲，单一事实来源）——本文件是其实施规划，与之冲突处以上游为准。
> **状态**：v1（2026-08-26）
> **编排方式**：阶段制 **M0-M7**，不绑定版本号；发版归属由当时进度决定（M0 无 agent 依赖，可与任何版本捆绑提前还债）。
> **约定**：工作量单位为**人天**（个人项目口径，含单测与文档，可调）；验收标准全部可确定性验证（单测/编译/模拟器交互），延续项目 Fake 测试基建传统（shared commonTest 49 个文件的现有体系）。
> **与上游编号对照**：本任务书 M0-M7 对应总纲 7.4 的 B0-B6（M1 锚点层为总纲 8「锚点层可先行」的独立实施；M5 对 B4，M6 对 B5，M7 对 B6）。

***

## 1. 目标与交付物

**目标**：把总纲落地为可运行的「音乐伙伴」——v1 交付**纯文字完整闭环**（锚点系统 → 对话 → 引擎 → 电台 → 报告），语音为独立 gate（M7-T7.3）。

**最终交付物**：

| 层  | 交付物                                                                                                           |
| -- | ------------------------------------------------------------------------------------------------------------- |
| 数据 | Room v2 三表（agent\_task/agent\_audit\_log/agent\_message）+ MusicLabel 溯源四列 + 迁移测试                              |
| 协议 | LlmTransport（手动 SSE + tools 参数）、RealtimeVoiceTransport（WebSocket，独立 gate）                                     |
| 工具 | ToolSpec DSL + ToolRegistry + 十项工具（包装既有 UseCase）                                                              |
| 引擎 | MasterAgent（唯一大脑，对话 handleUserMessage 即原 AgentOrchestrator.run() 循环，步数预算 8）+ AgentScheduler + PolicyGuard + TrustLedger + ContextBudget + SessionStore + PresenceBus |
| 端口 | PlaybackCommandPort（:shared 定义接口，三端适配器复用现有 Controller 桥）                                                      |
| UI | 三胶囊底栏、轻量浮层、对话页（五类气泡）、确认卡片流、通知侧条、审计日志页、门面二期、伙伴设置页六分区                                                           |
| 场景 | 15 场景中的 11 个（一/二/三梯队全量 + 四梯队的听歌报告与遗忘唤醒）                                                                       |

**明确不做（v1 边界）**：实时语音（除非端点可用且 M7 验证通过）、四梯队艺术家深读/歌词注解的完整版、任何在线音乐服务闭环。

***

## 2. 阶段总览与依赖图

```
M0 地基与还债 ──▶ M2 协议层 ──▶ M3 工具层 ──▶ M4 引擎循环 ──▶ M5 对话与锚点二期 ──▶ U Kermit 日志治理 ──▶ M6 电台与事件 ──▶ V Enrich v2 批次加固 ──▶ W Agent UI 呈现层 ──▶ M7 报告与语音
   (B0)            (B1)          (B2)           (B3)              (B4)                       (横切)              (B5)                                   (B6)
                                                                      ▲                          ▲                  ▲                                       ▲
                                                             R 债务清零 ─┘                          │                  │                                       │
                                                             S 工具层终局 ─┘（批次 A 重命名 17 + 批次 B 追加 10 = 27）          │                                       │
                                                             T Agent 体系 ─── Enrich SubAgent v1 基类 + Master 终局 ──────────────────────────────────────────────┘

M1 锚点层一期（B4 的前置 UI 骨架，Fake 数据驱动，与 M0-M7 完全并行）
横切：本地化（贯穿 M1-M7）· 测试基建（Fake* 替身随阶段建立）
```

| 阶段 | 内容                               | 依赖          | 关键退出标准                                  |
| -- | -------------------------------- | ----------- | --------------------------------------- |
| M0 | 三端 Repository 去重 + Room v2（B0）   | —           | 全平台编译 + 迁移单测绿 + 既有 650 单测零回归            |
| M1 | 锚点层一期：三胶囊/浮层/播放页重排/C 键（Fake 驱动）  | —（可与 M0 并行） | 三端编译 + 模拟器锚点交互核验                        |
| M2 | LlmTransport 流式协议 + tools 参数（B1） | M0          | 协议层单测全绿（SSE 解析/错误路径/5 服务商兼容）            |
| M3 | 工具层：ToolSpec DSL + 十项工具首次交付；终局由 S 阶段完成（27 原子工具域前缀统一）      | M2          | 工具校验单测防漂移                               |
| M4 | 引擎循环四件套 + 双层预算（B3）               | M3          | 引擎全行为确定性测试（步数/许可/拒绝/审计断言）               |
| M5 | 对话页 + 五类气泡 + 确认流 + 门面二期 + 漏斗（B4） | M4 + M1     | 纯文字完整体验三端可用                             |
| M6 | Radio SubAgent 完整实现 + 电台三轮协作 + 跳过感知 + DJ 衔接 + 审计页（B5）  | M5 + T（SubAgent 基类 + AgentScheduler + ToolRegistryView） | Radio SubAgent 独立运行 + FakeLlm+FakePlaybackCommandPort 电台确定性测试 |
| V  | Enrich SubAgent v2：批次策略重写（Repository 层 ArtistGroup/MixedGroup 拉活 + chunk 拆分）+ 6 轮编排 + 两轮深度 review（13 个修复：chunk history 隔离/预热清 history/EMPTY_FACT_MARKERS 统一/JSON 兜底扩展/隐式依赖清除）✅ 2026-09-02 | T | 批次拆分确定性 + history 零污染 + 空值标记 13 项统一 + JSON 兜底不误伤 + compileKotlinDesktop + desktopTest 绿 |
| W  | **Agent UI 呈现层**：六个表面 + 存在感四形态 + 锚点系统接线——把 Master/SubAgent/PresenceBus/AuditLog 全链路引擎能力转化为可交互界面 | M6（DJ/跳过/电台后端已就绪）+ V（对话气泡确认卡已就绪） | Compose 三端编译 + 桌面真机核验：① 底栏胶囊徽标实时反映 Enrich/Radio 状态 ② 门面问候 DJ 衔接语（LLM 或 fallback）③ 对话页五类气泡 + 流式打字机效果 ④ 轻量浮层 C 键/长按唤起 + 带话进对话页 ⑤ AgentNoticeBar 4s 侧条 + 撤销 ⑥ 审计页撤销动作 STRONG_CONFIRM 降级 ⑦ 正在听卡常驻对话页顶部 |
| M7 | 报告角色 + 伙伴设置页 + 语音档（B6）           | M6 + W      | 语音为独立 gate，可整体延期不影响 v1 完整性              |
| R  | 债务清零与交互地基修复（首轮注入/漏斗/真实播放端口/多确认/会话持久/M5 收尾 UI）✅ 2026-08-30 | M5 未完成 + 定义级漏项 | 交互主干（触发→理解→执行→呈现→反馈→审计）闭环；三端编译 |
| S  | 工具层终局：批次 A 域前缀统一重命名拆分（17 原子工具）+ 批次 B 追加 Library 聚合/Song USER 标签写入闭环/PlaybackEnqueue（+10 = 27 原子工具）✅ 2026-08-31 | M3 首次交付 + R 阶段暴露出的工具层遗留 | ToolNames.ALL 27 ↔ ToolRegistry 27 注册 1:1；desktopTest 677 全绿；compileAndroidMain/compileKotlinDesktop 通过 |
| T  | Agent 体系终局——Master Agent（唯一大脑：派发/验收/生命周期）+ Enrich SubAgent（纯被动执行器）+ 两层基础设施（AgentContextBudget 每 Agent 独立 + AgentScheduler 全局纯规则仲裁）✅ 2026-08-31 | S（27 原子工具）+ R（感知锚点） | Enrich SubAgent 端到端跑通 + Master 唯一决策铁则 + 两层 ContextBudget 生效 + Scheduler pause/resume 自动触发 + Radio SubAgent 基类预留（M6 填实现） |
| U  | Kermit 日志体系统一治理——引入 Touchlab Kermit 替换三套 Agent Log object + 66 处裸 println 渐进迁移 + Release 级别裁剪 + 三端原生桥接 | T（Agent 体系是最大消费者） + M5（UI 层有 Log 调用） | AgentLog/AgentRuntimeLog/AgentSubAgentLog 全部删除；Agent 体系 40+ 调用点迁完；三端（Android Logcat / iOS OSLog / Desktop stdout）原生桥接生效；desktopTest 全绿 |

***

## 3. 任务分解

### M0 地基与还债（B0，\~5 人天）✅ 2026-08-27 完成

> 先还债后造轮：引擎还没动就清掉三端重复，认识溯源是后面一切的地基。**此阶段可独立于 agent 交付**（搭任何版本顺风车）。
>
> **验收记录**：`desktopTest` 583 用例全绿（迁移 + 溯源 + 既有回归）；`:shared:compileAndroidMain` ✓；**iOS 编译需 macOS 环境验证**（Windows 本机无 Xcode；iosMain 改动为桌面已验证实现的镜像 + 标准 expect/actual 模式）。
> review 修复（2026-08-28）：**备份快照 v2 携带标签溯源字段**（`MusicLabelSnapshot` + source/confidence/created\_at/updated\_at，可空默认 → v1 存量 JSON 兼容）——还原后 USER 标签保留规则①保护（此前 v1 快照还原会让用户修正退化为可被模型覆盖）；审计留痕改为「主数据先落库、审计后写」；移除 UNKNOWN println 残留。

| ID    | 任务                                                                                                                                                                                                                                                                                            | 涉及文件                                                                                                                                        | 验收                                                                                                 |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| M0-T1 | Room v2 迁移：新建 agent\_task/agent\_audit\_log/agent\_message 三表 + DAO；MusicLabel 加 4 列（source/confidence/created\_at/updated\_at）；编写 v1→v2 迁移 + 迁移测试（KMP `MigrationTestHelper`）                                                                                                                 | `shared/.../data/database/AppDatabase.kt`（version=2+`MIGRATION_1_2`）、`MusicLabel.kt`、新建 `AgentTask.kt`、`AgentAuditLog.kt`、`AgentMessage.kt` | ✅ `AppDatabaseMigrationTest` 2 用例绿（存量保留/加列可写/新表可写 + Room 重开）；schema 导出 `schemas/**/{1,2}.json` 已入库 |
| M0-T2 | `MusicRepositoryImpl` 三端共享逻辑上提 commonMain 基类 `MusicRepositoryBase`（约 40 方法：AI/标签/统计/播放历史/备份）——**逐方法判定**；扫描、排序分叉（getAllMusicInfoAsList 三端语义刻意不同、getRandomMusicInfoWithExtra 桌面 Room 绕过、getDeletedMusicIdsGroupedByFolder）留平台层；新增 expect/actual 日期工具（todayDateString/parseDateToMillis）收口三端日期差异 | `MusicRepositoryBase.kt`（commonMain）+ 三端 Impl 重写 + `DateFormats.{common,android,desktop,ios}.kt`                                            | ✅ 三端实现 **2843→1714 行（-40%）**，去重达目标上限；既有 579 用例零回归                                                  |
| M0-T3 | 审计字段语义接线：`source=USER` 永不被模型覆盖、富化写 `source=LLM`、T2 更新保留 createdAt 滚动 updatedAt；新增 `MusicRepository.addUserMusicLabel`（T1 路径）                                                                                                                                                                  | `MusicRepositoryBase`（addMusicLabel/addUserMusicLabel + 来源常量）、`MusicRepository` 接口、`FakeMusicRepository`                                    | ✅ `MusicRepositoryBaseTest` 4 用例全绿（规则①拒写/槽位隔离/时间戳溯源）                                               |

**退出**：`./gradlew test` 全绿 + `:shared:compileAndroidMain` / `:shared:compileKotlinIosSimulatorArm64` / desktop 编译通过。

### M1 锚点层一期（\~6 人天，与 M0 并行）✅ 2026-08-27 完成（主体；运行核验待办）

> 纯 UI 骨架，**无 agent 引擎也可先行**——UI 用 Fake 数据驱动（PresenceBus 存根 + 假徽标），作为引擎就绪前的交互验证。引用总纲 5.2。
>
> **验收记录**：`:shared-ui:compileKotlinDesktop` ✓、`:shared-ui:compileAndroidMain` ✓、`:shared-ui:desktopTest` ✓；
> 桌面/模拟器**交互级核验待办**（长按 600ms 手势、三胶囊宽度实测）；C 键焦点问题已修复（2026-08-28 review：`onPreviewKeyEvent` 捕获阶段会吞掉文本输入的字母 C，改 `onKeyEvent` 冒泡阶段由输入框先消费）；iOS 待 macOS。
> review 修复（2026-08-28）：QuickSheet 唤起即 `requestFocus`（桌面此前需再点一次）；收藏键移除本地乐观翻转（外部参数单源）；`BottomFusionBar` var→val。
> 范围说明：门面二期（M1 第二期：Home→门面）依任务书留 M5；FusionSidebar（Medium 横屏）形态适配随门面二期。

| ID    | 任务                                                                                                                                                                                                    | 涉及文件                                                     | 验收                                                                         |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------- |
| M1-T1 | `BottomFusionBar` 改造为三胶囊：导航胶囊左侧插入伙伴胶囊位；`bottomTabs` 去 Home（余三 Tab）；页↔tab 索引重映射（`tabIndexForPage = page-1`，门面页时三 Tab 均不高亮、折叠态 getOrNull 兜底）；宽度压缩（图标 28→24dp、间距 12→8dp、外边距 16→12dp、歌名列收窄、折叠图标区 56→48dp） | `common/components/BottomFusionBar.kt`                   | ✅ 编译 + 代码级重映射验证；360dp/320dp 宽度实测待模拟器核验                                     |
| M1-T2 | `CompanionCapsule`：伙伴形象（`Avatar` 先例；未配置=默认形象）+ 状态徽标圆点 + 点按=门面 / 长按 600ms=浮层（触觉 TICK/LONG\_PRESS）                                                                                                      | 新建 `common/components/CompanionCapsule.kt`               | ✅ 编译；手势核验（长按与点按不叠加）待模拟器                                                    |
| M1-T3 | `AgentQuickSheet` 轻量浮层：自底上滑单行条（haze 材质胶囊）；有底栏贴底栏上方（AppRoot Column 排布）、无底栏贴屏底（PlayContent Align.Bottom）；发送/imeAction.Send 提交清空（回复二分 M5）                                                                | 新建 `common/components/AgentQuickSheet.kt`                | ✅ 编译；交互核验待模拟器                                                              |
| M1-T4 | 播放页控制区重排：副行五键（播放模式/收藏/**对话**(scope 图标)/播放列表/**更多**）；心动模式与睡眠定时收入「更多」DropdownMenu（定时激活项显示倒计时+按钮角标）；三布局调用点（竖屏/手机横屏/平板横屏）同步                                                                               | `player/pages/PlayContent.kt`（`PlaybackControlsButtons`） | ✅ 三调用点统一命名参数；布局一致待模拟器                                                      |
| M1-T5 | 键盘 C 键 = 唤起浮层、Esc 收起（挂 AppRoot 全局，覆盖播放页）                                                                                                                                                              | `AppRoot.kt`（`onKeyEvent`）                               | ✅ 编译；review 修复 2026-08-28：改冒泡阶段（`onKeyEvent`），文本输入聚焦时字母 C 由输入框先行消费，根节点不再吞键 |
| M1-T6 | PresenceBus 存根 + Fake 驱动：`companionBadgeVisible`/`quickSheetVisible` 状态接入 AppRoot（徽标消费点就绪；侧条/DJ 线消费点 M5 建 UI，接口预留）                                                                                    | `AppRoot.kt`                                             | ✅ 徽标驱动底栏伙伴胶囊；侧条/DJ 线预留                                                     |

**退出**：三端编译 + 模拟器/桌面核验完成；M1-T1 重映射逻辑有单测。**注意**：此阶段 UI 文案即进入本地化管线（见第 4 章），不要积压到 M5。

### M2 协议层（B1，\~4 人天）✅ 2026-08-27 完成

> 验收记录：desktopTest 全量 **606 用例全绿**（网络层新增 23 用例）；`:shared:compileAndroidMain` ✓；
> iOS 编译需 macOS 验证（全部新代码为纯 commonMain，无平台差异面）。
> review 补齐（2026-08-28）：DTO/`LlmMessage` 增加 **assistant** **`tool_calls`** **消息形状**（`OpenAiAssistantToolCall`/`OpenAiFunctionCall` + `LlmToolCall`）——M4 引擎循环「tool\_call → 执行 → 回传」需要原样回传 assistant 工具调用消息，协议层已备，避免 M4 中途返工。
> review 修复（2026-08-28）：流式 `withTimeout` 兜底（默认 120s，`timeoutMillis<=0` 禁用——**runTest 虚拟时间 auto-advance 会误触发虚拟超时，单测须禁用**；半开连接转 `Failed`）；`CancellationException` 与超时区分（取消向上传播不误转 `Failed`，M4 预算熔断以 cancel 实现时不混乱）；`ToolCall.id` 空缺生成 `call_N` fallback；`OpenAiChoice.finishReason` 补 `@SerialName("finish_reason")`（此前非流式响应解码恒 null）。

| ID    | 任务                                                                                                                                          | 涉及文件                                                                                                    | 验收                                                                                                                                             |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| M2-T1 | `OpenAiCompatibleAdapter` 扩展 tools（function-calling）参数：OpenAiStyleRequest/DTO 增加 tools/tool\_choice，保持 5 家服务商兼容（能力探测+降级：不返回 tools 的端点退化纯文本） | `data/network/MultiProviderApiAdapter.kt`、`data/network/dto/ApiDtos.kt`                                 | ✅ `OpenAiRequestDtoTest` 5 用例（tools 形状/tool\_choice/工具结果 tool\_call\_id/温度档位/往返）；`OpenAiLlmTransportTest` 覆盖"请求带 tools 但端点回文本=退化纯文本"           |
| M2-T2 | 手动 SSE 流式解析（ByteReadChannel 逐行，\~80 行）：`LlmTransport` 接口（流式 chunk 回调 + 工具调用事件）；**不升级 Ktor**（3.1.1 锁定，避 Darwin 取消泄漏）                         | 新建 `domain/agent/port/LlmTransport.kt`、`data/network/SseParser.kt`、`data/network/OpenAiLlmTransport.kt` | ✅ `SseParserTest` 10 用例（事件边界/注释/CRLF/分片/流尾/畸形行）；`OpenAiLlmTransportTest` 8 用例（文本流/工具分片组装/兜底 flush/HTTP 错误转 Failed/畸形 chunk 跳过）——MockEngine 端到端 |
| M2-T3 | `temperature` 1.3f → 0.2-0.4（JSON 任务过高的遗留修正；对话类任务可用较高值，按任务类型分档）                                                                             | MultiProviderApiAdapter（`callChatApi` 参数化默认 0.3）、ApiDtos（`@EncodeDefault(ALWAYS)` 保证默认值入报文）             | ✅ 参数单测（0.3 档/0.7 覆盖/0.25 往返）                                                                                                                   |
| M2-T4 | `FakeLlmTransport`：脚本化响应序列（文本/工具调用/中断/超时）+ 调用记录（messages/tools/temperature）                                                                 | `commonTest/.../fakes/FakeLlmTransport.kt`                                                              | ✅ 供 M3/M4/M6 使用（M4 引擎测试前置替身已就位）                                                                                                                |

**退出**：协议层单测全绿；现有 AI 功能（富化/每日推荐）回归不破坏。

### M3 工具层（B2，\~4 人天）✅ 2026-08-29 首次交付（十项工具原型）；终局迭代见 S 阶段

| ID    | 任务                                                                                                                                                                                                                                                      | 涉及文件                                        | 验收                 |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ------------------ |
| M3-T1 | `ToolSpec` DSL：手写 schema 生成 + 参数校验器（与 kotlinx.serialization 协同）                                                                                                                                                                                         | 新建 `domain/agent/tool/ToolSpec.kt`          | schema↔校验单测防漂移     |
| M3-T2 | **27 原子工具 + ToolRegistry**（批次 A 重命名拆分 17 + 批次 B 追加 10）。域前缀统一：playback_* 瞬时控制/playback_play_at/playback_enqueue、playlist_* 八件套（CRUD+曲目管理+系统歌单保护）、library_* search/similar/stats/recent_history/**artists/albums/tags/songs_by_***、song_* tags_get/enrich_llm/**tag_user_add/tag_user_remove**、agent_budget；批次 B 补 MusicRepository 聚合查询（getAllArtistsSummary/getAllAlbumsSummary）、DAO deleteUserLabel、PlaybackCommand.ADD_TO_QUEUE 枚举 | `domain/agent/tool/` 十个实现文件 + ToolRegistry | ✅ desktopTest 677 全绿；compileAndroidMain/compileKotlinDesktop 通过；FakeAgentMusicRepository/FakeMusicRepository 同步补方法 |
| M3-T3 | 工具结果回填语义：工具执行结果强制回填上下文（防幻觉型假成功），失败=异常结果入审计                                                                                                                                                                                                              | ToolRegistry                                | 回填与失败路径单测          |

**退出**：✅ 批次 A 2026-08-29（17 工具重命名拆分）+ 批次 B 2026-08-31（追加 10 工具 = 27 原子工具）；ToolNames.ALL 常量 ↔ ToolRegistry 注册 1:1；desktopTest 677 全绿；compileAndroidMain/compileKotlinDesktop 通过；工具白名单与许可级已被 M4 引用。

### M4 引擎循环（B3，\~6 人天）——复杂度在护栏，不进循环

| ID    | 任务                                                                                                                                                             | 涉及文件                                                       | 验收                                         |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| M4-T1 | `AgentOrchestrator`：唯一循环（tool\_call → 执行 → 回传 → 循环至回答），步数预算 8（硬熔断+审计记录终止原因）；回传路径=assistant `tool_calls` 消息（`LlmToolCall`，M2 review 已备协议形状）+ `role="tool"` 结果消息 | 新建 `domain/agent/engine/AgentOrchestrator.kt`              | 引擎循环确定性测试（脚本化响应序列驱动）                       |
| M4-T2 | `PolicyGuard` 四级许可（SILENT/NOTIFY/CONFIRM/STRONG\_CONFIRM + 信任阶梯状态机：建议→代劳→静默，档位可回拨）+ `Scheduler`（触发器：召唤/事件/定时 + 冷却）                                             | 新建 `engine/PolicyGuard.kt`、`Scheduler.kt`、`TrustLedger.kt` | 策略表/信任状态机单测 + 断言 audit\_log                |
| M4-T3 | `ContextBudget` 上下文组装：三层配额（任务状态/曲库概况清单↔概览两级切换/工具结果滚动淘汰）；`SessionStore` 会话状态                                                                                    | 新建 `engine/ContextBudget.kt`、`SessionStore.kt`             | 组装器单测 + 快照测试（清单/概览分档）                      |
| M4-T4 | `PlaybackCommandPort` 接口（:shared 定义，密封指令集）+ 三端适配器（复用现有 PlaybackController 桥——Android/Desktop/iOS 已有实例）；`FakePlaybackCommandPort`                               | 新建 `domain/agent/port/PlaybackCommandPort.kt` + 各端适配       | :shared 不反向依赖 shared-ui（编译期验证）；Fake 驱动引擎测试 |
| M4-T5 | `PresenceBus`（伙伴唯一嗓音：事件 → 徽标/侧条/DJ 线消费点）+ 双层预算接线（云端频率/额度配额，额度耗尽→本地兜底）                                                                                          | 新建 `engine/PresenceBus.kt`                                 | 事件分发单测；额度耗尽降级路径测试                          |

**退出**：引擎一切行为确定性可测（步数/许可/拒绝纪律/预算/审计断言全覆盖）；`:shared` 依赖方向铁律守住。

### M5 对话与锚点二期（B4，\~9 人天）——纯文字完整体验　<span style="color:#2e7d32">✅ T1/T2/T4 交付 2026-08-28；T3/T5/T6/T7 由 R 阶段 R-T6 补齐 2026-08-30（AgentNoticeBar 组件/门面二期/搜索条带）；侧条 NOTIFY 接线、审计页留 M6</span>

> **刻意纪律**：确认交互（「有感」）必须在电台隐式接受（「无感」）之前交付——M5 是 M6 的校准基准。

| ID    | 任务                                                                                                                                                                                                   | 涉及文件                                                         | 验收                                    |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------- |
| M5-T1 | `ChatScreen` + `ChatViewModel`：问候区/正在听卡（点按进播放页）/对话流（LazyColumn 自动滚底+回看暂停+顶部翻页）/任务进行条（三点脉动+取消，完成原地替换结果）/常驻输入条（胶囊形+发送）；SubScreen 基座                                                                    | 新建 `chat/ChatScreen.kt`、`chat/ChatViewModel.kt`              | 消息流单测（session 分页/滚底语义）；三端编译 ✅（8/8 用例） |
| M5-T2 | `CompanionBubble` 五类气泡：text/song（AlbumCover+题名+菜单）/songlist（FixedMusicList 复用 HomeScreen 心动歌单配置+尾部动作行）/explain（三轮轨迹折叠）/confirm（逐项勾选）；路由注册（Routes.Companion.Chat + NavigationGraph + HmpNavBackStack） | 新建 `chat/CompanionBubble.kt`；`common/navigation/Routes.kt` 等 | render\_hint 驱动渲染单测/快照 ✅              |
| M5-T3 | `AgentNoticeBar` 通知侧条（4s 退场+撤销；撤销窗口后入口转审计页——可逆性非限时优惠）+ 与 MessageToast 分工                                                                                                                             | 新建 `common/components/AgentNoticeBar.kt`                     | 侧条生命周期单测                              |
| M5-T4 | 确认卡片流：流内非模态（不挡输入条）、逐项可跳过、「照做」只执行选中项、执行后→NOTIFY+卡状态更新「已建 9 首，跳过 3 首」                                                                                                                                  | CompanionBubble.confirm + ChatViewModel                      | 确认流单测（跳过/部分执行/拒绝不纠缠） ✅                |
| M5-T5 | 门面二期：HomeScreen 改造（问候区+「和伙伴聊聊」触发钮+每日推荐/心动歌单口吻化卡片+认识进度轻提及）；pager 手术 `userScrollEnabled = currentPage != 0`；门面页隐藏 TabPageIndicator 页点；savedTabIndex 保持                                                 | `MainShell.kt`、`library/pages/HomeScreen.kt`                 | 冷启动回门面；门面禁滑出/画廊右滑不受阻                  |
| M5-T6 | 搜索框条带：漏斗未命中+意图特征→条带（「交给伙伴」/「只是搜索」）；空结果页追加「问问伙伴？」；拒绝纪律（本次会话不纠缠）                                                                                                                                       | `library/pages/SearchScreen.kt`                              | 判定逻辑单测                                |
| M5-T7 | 两级漏斗：高频指令词表直映射（零 token、50ms 内）；模糊意图升级单轮 agent 任务                                                                                                                                                     | 新建 `domain/agent/funnel/CommandLexicon.kt`                   | 漏斗单测（命中/未命中/升级语义）；FREE 模式可用           |

**退出**：纯文字完整闭环三端可用（锚点→对话→任务→确认→回执）；M5 全部交互并入审计。

### U 阶段：Kermit 日志体系统一治理（横切，\~1 人天）

> **背景**：全项目零统一日志基础设施。Agent 体系自封了三套几乎一模一样的 `object AgentLog` / `AgentRuntimeLog` / `AgentSubAgentLog`（底层全是 println + 不同 TAG 前缀），加上其余 40+ 处裸 println 散落各模块。println 在 Android 上输出到 `System.out`（Logcat 里混在系统输出中无法按 TAG 过滤），iOS 上仅进 App 沙箱 stdout（Xcode Console 看不到）。根因是项目从未引入过 KMP 日志库。
>
> **方案**：引入 [Touchlab Kermit 2.1.0](https://github.com/touchlab/Kermit)——KMP 社区事实标准，1k stars，Apache 2.0，三端原生桥接（Android → `android.util.Log` 进 Logcat、iOS → `OSLog` 进系统日志、Desktop → stdout + ANSI 颜色）。零外部依赖代价（纯 Kotlin 库，与 Room/Ktor/Koin 同级别），但立即获得 Logcat TAG 过滤、iOS 系统日志可见、Release 级别裁剪、per-tag 覆盖、测试捕获等能力。

| ID    | 任务                                                                                                                                                                                                 | 涉及文件                                                                                                                       | 验收                                              |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| U-T1 | **依赖引入**：`gradle/libs.versions.toml` 加 `kermit = "2.1.0"` 版本号 + `kermit` library alias（坐标 `co.touchlab:kermit`）；`shared/build.gradle.kts` 的 `commonMain.dependencies` 加 `implementation(libs.kermit)`；同步 shared-ui 的 commonMain（AgentLog 被 UI 层 ChatViewModel/ChatAgentGateway 引用） | `gradle/libs.versions.toml`、`shared/build.gradle.kts`、`shared-ui/build.gradle.kts` | `./gradlew :shared:dependencies` 见 kermit 在 commonMain 类路径；`compileKotlinDesktop` 通过 |
| U-T2 | **三端初始化**：Android 端 `MusicApplication.onCreate()` 加 `Logger.setMinSeverity(Severity.Debug)`（Release 构建用 `Severity.Warn` 可后续通过 BuildConfig 注入）；Desktop 端 `HmpDesktopApplication.kt` main 加 `Logger.setMinSeverity(Severity.Debug)`；iOS 端 `AppDelegate` 加初始化；三端统一用 `platformLogWriter()`（Android 走 Logcat、iOS 走 OSLog、Desktop 走 System.out） | `android/app/MusicApplication.kt`、`desktop/app/HmpDesktopApplication.kt`、`ios/HMP/AppDelegate.swift`（或 Kotlin 桥接层）           | Android Studio Logcat 过滤 TAG 能看到 Kermit 格式日志；iOS Xcode Console 有子系统日志；Desktop stdout 带 ANSI 颜色 |
| U-T3 | **Agent 三套 Log 合并**：删除 `AgentLog.kt` / `AgentRuntimeLog`（AgentContextBudget 内的 internal object）/ `AgentSubAgentLog`（SubAgent 内的 internal object）；全项目 ~40 处 Agent 日志调用改为 Kermit `Logger.withTag("Agent.xxx")` 或 `Logger.i("Agent.xxx") { ... }`；Tag 层级：`Agent.Master` / `Agent.Scheduler` / `Agent.Enrich` / `Agent.Tool` / `Agent.ReActLoop` / `Agent.LlmCall`；原 `AgentLog.truncate()` 辅助方法在消息构造层处理（字符串模板里截断） | 删除 `shared/src/commonMain/.../infra/AgentLog.kt`；`AgentContextBudget.kt` 删 8 行；`SubAgent.kt` 删 7 行；改 `MasterAgent.kt`、`ReActLoop.kt`、`ToolCallExecutor.kt`、`LlmCallExecutor.kt`、`EnrichSubAgent.kt`、`AgentScheduler.kt`、`ChatViewModel.kt`、`ChatAgentGateway.kt` | `grep -r "AgentLog\|AgentRuntimeLog\|AgentSubAgentLog" shared/ shared-ui/` 零结果；desktopTest 全绿 |
| U-T4 | **全局裸 println 渐进迁移 + Release 裁剪**：扫描 `println(` 共 66 处（Agent 已在 U-T3 迁完，剩 ~26 处）——FFmpegAudioEngine(12)、Desktop 窗口/托盘(14)、各 Repository impl(13)、DailyRecommendationUseCase(10)、其他(5)；按模块渐进迁移（本阶段先迁 Repository + DailyRecommendation，Desktop/FFmpeg 可后续）；`Logger.config.minSeverity` 通过 BuildConfig 在 Release 构建注入 `Severity.Warn`（屏蔽 DEBUG/INFO） | `MusicRepositoryImpl.android/desktop/ios.kt`、`GetDailyMusicRecommendationUseCase.kt`、`FFmpegAudioEngine.kt`（可延迟）、三端 `build.gradle.kts` 加 BuildConfig minSeverity | `grep -rn "println(" shared/` 只剩 Desktop/FFmpeg 平台特定实现（可接受）；Release APK 运行 DEBUG/INFO 不输出 |

**退出**：
- AgentLog / AgentRuntimeLog / AgentSubAgentLog 三个文件全删除
- Agent 体系 40+ 调用点统一用 Kermit
- 三端原生桥接生效（Android Logcat TAG 可过滤、iOS 进 Xcode Console、Desktop ANSI 颜色）
- `compileAndroidMain` / `compileKotlinDesktop` / desktopTest 全绿
- 66 处裸 println 至少迁完 Repository + Agent + UseCase 域（Desktop/FFmpeg 可延迟到后续阶段）

**刻意纪律**：
- 不在本阶段引入 `kermit-io`（文件日志轮转）、`kermit-crashlytics`（崩溃上报）等扩展——本地播放器 + 单人开发，console 足够
- 不做 `withTag` 作用域工厂、Coroutine 上下文传播等高级特性——保持 API 使用极简（静态调用 + TAG 字符串），降低迁移心智成本
- `truncate()` 辅助方法不复用 Kermit API，在消息字符串模板中内联处理——避免自定义 wrapper 增加间接层

### M6 电台与事件（B5，\~5 人天）

| ID    | 任务                                                                                                                                           | 涉及文件                                        | 验收                                             |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ---------------------------------------------- |
| M6-T1 | AI 电台三轮协作接线：种子认识（第 0 步 enrichSong）→ 本地保底队列（零等待开听）→ 云端全量清单决策 → diff 仲裁回传；开电台=`songlist` 卡（「今夜电台 · 12 首备选」）、播完续歌 SILENT、徽标=电台运行点、每次续选「为什么」入审计页 | MasterAgent + RadioSubAgent 骨架接线 + ChatViewModel + Room Migration 2→3（agent_message.data_json 结构化 songlist） | FakeLlm+FakePlaybackCommandPort 电台确定性测试（三轮各路径） |
| M6-T2 | 跳过感知重排（连跳 2 首→重排 SILENT+侧条「换了一批安静的，↩恢复」）；一句话切换（浮层→漏斗→执行+侧条+对话页沉淀）                                                                            | PresenceBus 事件接线                            | 事件触发测试                                         |
| M6-T3 | DJ 衔接预生成（曲间一句，门面问候区轮换+对话页 text 沉淀；播放中不弹浮面硬纪律）                                                                                                | PresenceBus + 门面                            | 预生成零延迟核验；硬纪律断言（手势进行中无浮面）                       |
| M6-T4 | `AuditLogScreen`：agent\_audit\_log 驱动、时间倒序、每行动作+「为什么」展开（→T0 行为证据）+撤销；撤销边界（被用户改过的动作降级为「删除」走 STRONG\_CONFIRM；重排队列类始终可撤销）                       | 新建 `audit/AuditLogScreen.kt`                | 撤销边界单测                                         |
| M6-T5 | STRONG\_CONFIRM 双确认链：删歌单/改 ID3 走 DialogHost 模态（伙伴问意图→系统问权限，顺序不合并）                                                                            | 复用 AppRoot DialogHost + EditMusicTagsScreen | 双确认链路核验                                        |

**退出**：电台三轮协作全路径确定性测试；审计页承载全部「为什么」。

### V 阶段：Enrich SubAgent v2 批次策略 + 两轮深度 review 鲁棒性加固（13 个修复）✅ 2026-09-02

> **前置**：依赖 T 阶段交付的 Master Agent + Enrich SubAgent v1 基类 + 工具层终局。T 阶段跑通了端到端骨架，但 Enrich v1 的批次拆分、history 隔离、空值标记、JSON 兜底四处有"能跑但不稳"的硬伤——改阈值就踩坑、预热残留污染、LLM 微偏格式就丢歌。
>
> **本次做的事**：批次策略从 Enrich 层内存 groupBy 下沉到 Repository 层一次拉活（`fetchNextEnrichWorkUnit`），加上 chunk 保护、预热 history 清理、空值标记统一、JSON 兜底扩展。两轮 review 共 13 个修复。

**核心参数**（硬编码在 EnrichSubAgent 伴生对象）：
| 参数 | 值 | 说明 |
|------|----|------|
| `CHUNK_SPLIT_SIZE` | 20 | 单个 LLM call 最大歌曲数（任何来源 ArtistGroup / MixedGroup 都 chunked） |
| `BIG_ARTIST_THRESHOLD` | 3 | 某歌手 ≥ 3 首未富化 → 走 ArtistGroup（可预热） |
| `MIXED_GROUP_SIZE` | 10 | 小歌手累计 ≥ 10 首 → 走 MixedGroup（跳过预热） |
| `EMPTY_FACT_MARKERS` | 13 项 | `待定/不详/未知/TBD/N/A/NA/—/-暂无/暂无/—/null/NULL/undefined` |

**6 轮编排**（runLoop + processChunk）：
```
Round 0  预热 → runLoop 处理（ArtistGroup 才走，缓存复用）
Round 1  枚举批量 → genre/mood/scenario 一次性输出 JSON array
Round 1.5 枚举自检 → 上一轮结果与 LLM 内部"真实判断"对比，差异修正
Round 2a 自由文本易 → description / singerIntroduce（LLM 相对靠谱）
Round 2b 自由文本难 → rewards / lyricBackground（极易编造，强 prompt 约束）
Round 3  总体反思 → patch 格式，只返单字段修改（ReflectionPatch(index, field, fix)）
```

**批次策略**（Repository 层一次拉活，不是内存 groupBy）：
```
MusicRepositoryBase.fetchNextEnrichWorkUnit():
  getAllUnenrichedIds() → exclude deleted + exclude LLM/AGENT 标签已有的
  groupBy artist → count DESC（先大歌手）
  遇 ≥ BIG_ARTIST_THRESHOLD 首 → ArtistGroup(artist, songs)
  遇小歌手累计 ≥ MIXED_GROUP_SIZE → MixedGroup(songs)
  最后一批没填满 → 全取（兜底）
```

**两轮 review 13 个修复**：

| 轮次 | # | 级别 | 问题 | 根因 | 修复 |
|------|---|------|------|------|------|
| 第一轮 | P0-1 | 🔴 | chunk 间 history 未清，chunk N-1 输出污染 chunk N | `clearHistory()` 在 for 循环外 | 移到 for 循环内部，每个 chunk 结束即清 |
| | P0-2 | 🔴 | 手动注入预热只有 assistant 消息，历史不对称 | orphan assistant 消息 | 配对注入 `user+assistant` |
| | P0-3 | 🔴 | `extractJsonArrayElements` 纯文本兜底当单对象 → 静默丢歌 | 无格式检测 | 纯文本（无 `{`/`[`）返回 emptyList |
| | P1-4 | 🟡 | `normalizeFacts` 只处理 3 个空值标记 | 空值枚举不全 | 扩展到 13 个 |
| | P1-5 | 🟡 | `parseEnumSelfCheckPatch` 名不副实 | 函数名暗示 patch，实际返回 full list | 改名 `parseEnumSelfCheckFullList` + `applyEnumReplacements` |
| | P1-6 | 🟡 | PresenceEvent total 参数传错 | 传 chunk 大小而非工作单元大小 | 改为 `currentUnitSize` |
| | P1-7 | 🟡 | system prompt 自信角色 vs 预热 prompt "不要编造"冲突 | prompt 约束不对称 | system prompt 加 ⚠️ 核心约束段落 |
| | P2-8 | ⚪ | 注释写"6 轮编排"实际 processChunk 是 5 轮 | 注释不一致 | 修正注释表述 |
| | P2-9 | ⚪ | FreeTextFactsResult.errorInfo 多余 | LLM 不知道自己出错 | 删掉 data class 字段 + prompt |
| 第二轮 | P0-A | 🔴 | Round 0 预热后 user 消息残留 → chunk 开头重复注入预热 | `callLlm()` 只追加 user，预热完没清 | 预热完立刻 `clearHistory()` |
| | P0-B | 🔴 | MixedGroup 无 chunk 拆分保护 → 隐式依赖 `mixGroupSize < CHUNK_SPLIT_SIZE` | MixedGroup 代码遗漏 | MixedGroup 也 `chunked(CHUNK_SPLIT_SIZE)` |
| | P1-C | 🟡 | `applyPatch` 空值判断只认 `"-暂无"` 和 blank → LLM 返回 `"待定"` 当真实值 | 没复用 `EMPTY_FACT_MARKERS` | `isEmptyMarker` 判断改为遍历 13 项 |
| | P1-D | 🟡 | `extractJsonArrayElements` LLM 输出 `{...},{...},{...}` 没包数组 → 整个 parse 失败 | 兜底只处理单对象 | 兜底加分支：检测到 `{` 和 `}` 都有时用 `splitJsonObjects` 拆分 |

**关键设计决策（不是 bug，后续可能要动）**：

1. **`callLlmText` 不把 assistant 回复追加到 history**（`AgentContextBudget` 内部只追加 `newMessages` = 用户消息）。对 Enrich 没问题——所有 context 都是手动拼的（`buildEnumSelfCheckPrompt` 插入 `enumSummary` 等）。但 `estimatedTokenCount` 只累加用户侧。若将来需精确 token 预算控制（API 成本），需把 assistant 回复也追加。
2. **批次确定性依赖歌曲位置索引**（不是 ID）。Round 1 → Round 3 都是按输出顺序匹配输入 chunk 的第 N 首。若 LLM 乱序输出则错位。prompt 里显式约束「输出顺序必须与输入一致」。
3. **预热缓存存 `preheatCache: Pair<String, String?>`**（同名歌手 chunk 0 调用一次 Round 0，后续 chunk 复用）。预热失败不阻塞流程（降级为无预热）。

| ID | 任务 | 涉及文件 | 验收 |
|----|------|----------|------|
| V-T1 | Repository 层批次拉活：`fetchNextEnrichWorkUnit()` 一次返回 `ArtistGroup` / `MixedGroup`（不再 Enrich 层内存 groupBy） | `EnrichModels.kt`（新增 sealed class）+ `MusicRepository.kt`（接口）+ `MusicRepositoryBase.kt`（实现） | 单测：ArtistGroup 优先大歌手、小歌手累计兜底 |
| V-T2 | runLoop 重构：`while(isActive) { fetch → when ArtistGroup/MixedGroup → chunk 循环 }`；Round 0 预热后立刻 `clearHistory()` | `EnrichSubAgent.kt` runLoop | 断点核验：history 在 chunk 循环外是空、chunk 内 5 轮累积、chunk 末清零 |
| V-T3 | MixedGroup chunk 拆分 + `isActive`/`waitResume`/`shouldSoftStop` 三重护栏 | `EnrichSubAgent.kt` runLoop MixedGroup 分支 | 改 `mixGroupSize=100` 不崩（兜底） |
| V-T4 | `applyPatch` 空值判断复用 `EMPTY_FACT_MARKERS`（13 项）+ `normalizeFacts` 保持一致 | `EnrichSubAgent.kt` | 单测：`fix="待定"` 不写入、`fix="ROCK"` 正常写入 |
| V-T5 | `extractJsonArrayElements` 兜底扩展（单对象 / 无数组包装多对象 `{...},{...}` / 纯文本三路分流） | `EnrichSubAgent.kt` JSON 工具 | 单测：LLM 三种"格式不对"的输出都不误伤 |
| V-T6 | 第一次 review 9 个修复：chunk history 隔离 + 预热对称注入 + 纯文本兜底 + normalizeFacts 扩展 + parseEnumSelfCheck 重命名 + PresenceEvent total + system prompt 约束 + 注释修正 + errorInfo 删除 | `EnrichSubAgent.kt` + `MasterAgent.kt`（适配签名）+ `FakeMusicRepository.kt` + `AgentToolFakes.kt`（fake stub） | desktopTest 零回归 |

**退出**：

| 项 | 结果 |
|----|------|
| `:shared:compileKotlinDesktop` | ✅ BUILD SUCCESSFUL |
| `:shared-ui:compileKotlinDesktop` | ✅ UP-TO-DATE |
| `:shared:desktopTest` | ✅ BUILD SUCCESSFUL |
| history 零污染 | ✅ 断点核验：chunk 外 history 为空 |
| 预热不重复 | ✅ Round 0 后 `clearHistory()` + chunk 开头配对注入 |
| MixedGroup 安全 | ✅ `chunked(20)` 兜底，隐式依赖消除 |
| 空值 13 项统一 | ✅ `applyPatch` 与 `normalizeFacts` 共享 `EMPTY_FACT_MARKERS` |
| JSON 兜底不误伤 | ✅ 三路分流 |

### W 阶段：Agent UI 呈现层——六个表面 + 存在感四形态（\~10 人天）

> **前置**：依赖 M6（DJ 衔接语 / 跳过感知 / PresenceBus 事件全链路已 emit）+ V（Enrich v2 批次 + 对话气泡确认卡已就绪）。引擎侧能力已经齐了——PresenceBus 在发 `NoticeAvailable` / `SkipDetected` / `TaskProgress` / `CompanionBadge`，MasterAgent 有 `handleDjBlank` + `generateDjSegue` + `fallbackGreetings` 轮换，ChatScreen 有五类气泡，AgentNoticeBar / AgentQuickSheet / CompanionCapsule 三个骨架组件已写好但接线只做了一半。**本阶段的核心工作是把引擎事件接到 UI 消费点上，让用户能真正感知到**。
>
> **引擎→UI 映射总表**：
>
> | 引擎产出（已存在） | 消费 UI（本阶段做） |
> |-------------------|-------------------|
> | `PresenceBus.badgeState`（CompanionBadge） | 底栏伙伴胶囊徽标（未读红点 / 待确认脉冲 / 电台运行小绿点） |
> | `PresenceEvent.NoticeAvailable`（MasterAgent.handleDjBlank emit） | AgentNoticeBar 侧条（带撤销） |
> | `PresenceEvent.SkipDetected`（MasterAgent skip listener emit） | AgentNoticeBar 侧条 "跳过了《XX》，正在换一批…" |
> | `PresenceEvent.DjBlank`（trackChange emit） | 门面问候区触发 `generateDjSegue()` 或 fallback |
> | `PresenceEvent.TaskProgress`（ReActLoop.run emit） | 对话页三点脉动 |
> | `PresenceEvent.AgentProgress`（Enrich emit） | 胶囊徽标脉冲 + 门面"富化进行中"提示 |
> | `PresenceEvent.CloudQuotaExhausted`（GlobalTokenCounter） | AgentNoticeBar "云端额度耗尽，已切换本地模式" |

**引擎→UI 接线现状**（AppRoot.kt LaunchedEffect 当前消费的事件）：

```
PresenceEvent.NoticeAvailable   → ✅ 已接 AgentNoticeBar（但 showUndo=false 硬编码）
PresenceEvent.SkipDetected      → ✅ 已接 AgentNoticeBar（同上）
PresenceEvent.DjBlank           → ❌ 未接（门面问候区没写）
PresenceEvent.CompanionBadge    → ❌ 未接（胶囊徽标状态没渲染）
PresenceEvent.TaskProgress      → ❌ 未接（对话页三点脉动没写）
PresenceEvent.AgentProgress     → ❌ 未接（后台状态胶囊徽标没反映）
PresenceEvent.CloudQuotaExhausted → ❌ 未接（本地兜底无 UI 提示）
```

骨架组件现状（已存在但接线不完整）：

```
CompanionCapsule.kt   ✅ 114 行完整实现（detectTapGestures + haze + 36dp 胶囊形态）
                       ❌ AppRoot 只传了 onClick 没传 onLongPress，徽标状态参数未加
AgentQuickSheet.kt    ✅ 126 行完整实现（单行输入 + 28dp 圆角 + focusRequester 自动聚焦）
                       ❌ C 键全局监听没做；Esc/点外/浮层关闭没做；回复二分法没实现
AgentNoticeBar.kt     ✅ 91 行完整实现（haze 侧条 + 4s 自动退场 + 撤销点按位）
                       ❌ AppRoot showUndo=false 硬编码；撤销回调未接反向工具
```

**页面与组件清单**（按 agent.md 第 5 章「交互语言」的六条总则，嫁接优于新建）：

| 载体 | 形态 | 现有/需新建 | 后端依赖 | 备注 |
|------|------|-----------|---------|------|
| **底栏胶囊徽标** | CompanionCapsule 改造：`CompanionBadge` StateFlow → 红点（有未读/待确认）/ 脉冲圈（Enrich 运行）/ 小绿点（Radio 运行） | 已有骨架 CompanionCapsule.kt（114 行）+ AppRoot LaunchedEffect 收事件，徽标状态还没渲染 | `PresenceBus.badgeState`（StateFlow） | **锚点总则③：Tab 不加第五位**——胶囊是对底栏第三格的改造 |
| **门面问候区** | Home 第 0 页改造：一行文案区，`PresenceEvent.DjBlank` → 显示 `fallbackGreetings` 轮换句（LLM 可用时调 `generateDjSegue`） | 需新建门面问候组件（~80 行）；`MasterAgent.fallbackGreetings + greetingIndex` 已就绪 | `PresenceEvent.DjBlank` + `handleDjBlank` | agent.md 5.1 总则①：六个表面之一——门面不是功能区是伙伴的脸 |
| **轻量浮层 AgentQuickSheet** | 底栏上方单行条；长按胶囊 600ms / C 键 / 播放页按钮唤起；回复二分法（一句话直接浮层内显 / 卡片摘要带"查看"进对话页） | 已有骨架 AgentQuickSheet.kt（126 行），AppRoot 里接了 CompanionCapsule 的 onClick 但**没接长按**；C 键全局监听没做 | ChatAgentGateway / MasterAgent.handleUserMessage | agent.md 3.3 回复二分法 |
| **对话页 ChatScreen 增强** | ① 流式打字机：接收 ReActLoop/Enrich 的 TextDelta 增量渲染 ② 正在听卡：对话页顶部常驻 NowPlayingContextProvider ③ CONFIRM 执行中 loading 态 + ticket 号 ④ TaskProgress 阶段指示（三点脉动） | ChatScreen.kt（299 行）+ ChatViewModel.kt（318 行）已有五类气泡，**缺流式增量、正在听卡、执行中态** | ChatAgentGateway 需接 LlmEvent.TextDelta 流；AgentContextBudget 需 emit TaskProgress | agent.md 5.3 |
| **AgentNoticeBar 侧条** | 底栏上方 haze 侧条，4s 自动退场 + 撤销点按；撤销窗口过期→入口转审计页 | 已有骨架 AgentNoticeBar.kt（91 行）+ AgentNotice data class；AppRoot LaunchedEffect 已收 NoticeAvailable/SkipDetected → **没接撤销**（showUndo=false 硬编码） | `PresenceEvent.NoticeAvailable`（MasterAgent.handleDjBlank emit）；撤销需要 ToolRegistry 加反向工具（playlist_delete / song_label_remove） | agent.md 5.4.1：撤销边界——被后续修改过的降级为 STRONG_CONFIRM |
| **锚点系统（手势 + C 键）** | CompanionCapsule 长按 600ms → 浮层；点按 → 门面；C 键 → 浮层；播放页重排（用户决策 2026-08-27：播放页不重排，浮层贴屏底） | CompanionCapsule 骨架已写 `detectTapGestures(onTap/onLongPress)` → **但 AppRoot 没传 onLongPress 回调**；全局 C 键监听没做 | 纯 UI | agent.md 5.2 总则③：一锚之外零占位 |
| **审计页增强** | AuditLogScreen（280 行）：**只读→可撤销**——每行右侧加撤销钮 + 边界判定（被后续修改过的动作降级为 STRONG_CONFIRM 二次确认） | AuditLogScreen 已有时间倒序展示，**缺撤销入口 + 边界判定** | ToolRegistry 需新增反向工具（playlist_delete / playlist_revert / song_label_remove_llm）；AuditEntry 已有 argsHash + reason | agent.md 5.4.1：撤销边界 |
| **胶囊正在听卡** | 门面或对话页顶部常驻：当前播放歌曲 + 简短标签摘要；`NowPlayingContextProvider` 已有 | 需新建 ~60 行；ChatScreen 顶部插一条 | MasterAgent.startRadio 已注入 nowPlayingProvider | agent.md 5.5 存在感四形态之三 |

**存在感四形态覆盖检查**：

| 形态 | 说明 | 本阶段做 |
|------|------|---------|
| 通知侧条 | NOTIFY 完成（建歌单/重排/跳过） | ✅ AgentNoticeBar 撤销接入 |
| DJ 线 | 曲间预生成一句衔接 | ✅ 门面问候区 + 对话页 text 沉淀 |
| 正在听卡 | 对话页顶部常驻 | ✅ 新建组件 + 插 ChatScreen 顶部 |
| 胶囊徽标 | 未读/待确认/电台运行 | ✅ CompanionBadge StateFlow → CompanionCapsule 渲染 |

**严格不做什么**：
- ❌ 不做新路由——全部嫁接既有六面（门面 / 对话 / 浮层 / 侧条 / 设置 / 审计）
- ❌ 不做语音（M7 独立 gate）
- ❌ 不做报告叙事（M7-T1）
- ❌ 不做 agent_memory DAO 持久化（T 阶段明确留后续）
- ❌ 不做跨会话历史恢复（M7-T2 会话持久已覆盖）
- ❌ 播放中不弹伙伴浮面——**唯一硬纪律：用户手势进行中永远闭嘴**（agent.md 5.5）

| ID    | 任务 | 涉及文件 | 验收 |
| ----- | ---- | ------- | ---- |
| W-T1 | **胶囊徽标接线**：AppRoot.LaunchedEffect 收 `PresenceBus.badgeState`（StateFlow），CompanionCapsule 渲染三种徽标态——红点（有未读确认）/ 脉冲圈（Enrich 运行中）/ 小绿点（Radio 运行中）。CompanionCapsule 新增可选 `badgeState` 参数 + `BadgeOverlay` composable | `CompanionCapsule.kt`（改）、`AppRoot.kt`（LaunchedEffect + 参数传递） | 桌面端核验：启动 Rich 状态 → 胶囊变绿点；暂停 Enrich → 绿点消失；Radio 跑 → 绿点变脉冲 |
| W-T2 | **门面问候区**：Home 第 0 页 `HomeScreen` 顶部插 `CompanionGreetingBar`——一行文案区 + 动画淡入淡出 + 自动轮询 `fallbackGreetings`（5 句）。收到 `PresenceEvent.DjBlank` 优先触发 `MasterAgent.generateDjSegue()`（有 enrichConfig 时），失败 fallback。门面问候**不阻塞播放**（agent.md 5.5 铁则：播放中不弹浮面，但门面问候是常驻轻量区不算"弹"） | 新建 `ui/common/components/CompanionGreetingBar.kt`（~80 行）+ `HomeScreen.kt`（插一行）+ `AppRoot.kt`（LaunchedEffect 收 DjBlank 事件 → 触发 Gateway 取问候语） | 桌面端核验：切歌 → 门面问候句刷新（LLM 可用时 15-20 字中文衔接；LLM 不可用时 fallback 轮换） |
| W-T3 | **锚点手势 + C 键全局监听**：① CompanionCapsule.onLongPress 接入 AppRoot → 唤起 AgentQuickSheet（当前 CompanionCapsule 只传了 onClick）。② 全局 C 键监听 `Modifier.onKeyEvent`（AppRoot Scaffold 根节点）→ 唤起 AgentQuickSheet，输入框自动 focus（AgentQuickSheet.focusRequester 已就绪） | `AppRoot.kt`（Scaffold 根节点加 onKeyEvent + CompanionCapsule 参数）、`CompanionCapsule.kt`（onLongPress callback 透传）、`AgentQuickSheet.kt`（onSubmit → Gateway 带话进对话页逻辑已做） | 桌面端核验：① 长按胶囊 600ms → 浮层弹出 + 输入框已聚焦；② 按 C → 同上；③ Esc / 点浮层外 / 发送后 → 浮层关闭 |
| W-T4 | **对话页增强：流式打字机 + 正在听卡 + 执行中态**：① ChatGateway → ChatViewModel → ChatScreen 接 LlmEvent.TextDelta 流（ReActLoop 已 emit），Assistant 气泡增量渲染。② 对话页顶部插 `NowPlayingCard`（新建 ~60 行）：封面 + 歌曲名 + "正在听"标签。③ ConfirmCard 加三态：勾选→`submitting`（loading spinner + "正在执行…"）→ receipt（回执文案） | `ChatAgentGateway.kt`（ReActLoop 接流式）、`ChatViewModel.kt`（StateFlow 加 streaming 标志 + nowPlaying state）、`ChatScreen.kt`（顶部插卡 + 打字机动画 + ConfirmCard 三态）、新建 `NowPlayingCard.kt` | 桌面端核验：① 电台启动 → ChatScreen 流式渲染 `songlist` 卡（打字机效果）；② 对话页顶部常驻当前播放歌曲；③ 确认卡点"照做" → 转 loading → 回执 |
| W-T5 | **AgentNoticeBar 撤销接线 + AgentNotice 模型扩展**：AppRoot LaunchedEffect 收 NoticeAvailable/SkipDetected → AgentNotice 的 `showUndo` 从硬编码 false 改为 true（撤销入口）。撤销回调 → ToolCallExecutor 反向工具（`playlist_delete / song_label_remove_llm`）。撤销窗口过期（4s）→ Gateway 写审计日志 `undo_expired` 标签 → 审计页入口 | `AppRoot.kt`（showUndo + onUndo callback）、`AgentNoticeBar.kt`（onUndo 透传）、`ToolRegistry`（新增 2 反向工具 + expect 工具定义）、`MasterAgent.handleDjBlank`（emit NoticeAvailable 时带可撤销标签） | 桌面端核验：① Radio 跳过重排 → AgentNoticeBar 4s 侧条 + "撤销"钮 → 点撤销 → 歌单回滚；② 4s 内不操作 → 侧条消失 + 审计页可见 undo_expired |
| W-T6 | **审计页撤销入口 + 边界判定**：AuditLogScreen 每行 ToolExecutionRecord 右侧加撤销钮。边界判定：被用户后续修改过的动作（song_label 含 source=USER 覆盖）→ 撤销降级为 STRONG_CONFIRM 二次确认；playlist_reorder 始终可撤销。撤销调用反向工具 + AuditLogPort 写 `action_undone` 标签 | `AuditLogScreen.kt`（每行加撤销按钮 + 边界判定逻辑 + STRONG_CONFIRM 弹窗）、`ToolRegistry`（反向工具已在 W-T5 注册） | 桌面端核验：① 审计页电台重排动作 → 撤销按钮 → 歌单恢复；② 手动改了某歌单名字 → 该歌单创建动作撤销 → STRONG_CONFIRM 弹窗 |

**退出**：

| 项 | 验收标准 |
|----|---------|
| `:shared-ui:compileKotlinDesktop` | ✅ |
| `:shared-ui:compileAndroidMain` | ✅（iOS 留 macOS） |
| `:shared-ui:desktopTest` | ✅ |
| 桌面真机核验 W-T1~T6 | 每个任务描述的核验点全部跑通 |
| 存在感四形态全覆盖 | 胶囊徽标 / 门面问候 / 正在听卡 / AgentNoticeBar 撤销 |
| 交互纪律不违规 | 播放中无浮面、胶囊不是新 Tab、新路由零新增 |

### M7 报告角色与语音档（B6，\~7 人天，语音独立 gate）

| ID    | 任务                                                                                                            | 涉及文件                                                       | 验收                                         |
| ----- | ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| M7-T1 | 听歌报告（月度门面卡片入口→报告页图表复用 `ListeningChart` + 伙伴叙事段）；遗忘唤醒（胶囊徽标低频+对话页消息，点开才算送达不追问）                                  | 门面卡片 + ChatViewModel                                       | 报告数据管线测试                                   |
| M7-T2 | 伙伴设置页六分区（AI 页演进不推翻：身体素质/人格/嗓音与耳朵/认识进度/记忆与信任/记忆管理）；`LoadMusicExtraInfo` 重命名复用为认识进度；信任阶梯档位回拨 UI；审计页入口           | `settings/pages/AIScreen.kt`、`UserScreen.kt` 伙伴卡           | 设置读写 DataStore 单测；路由复用 Routes.AI.AI（无路由迁移） |
| M7-T3 | `RealtimeVoiceTransport`（WebSocket：JSON 控制事件+二进制音频帧+Ktor 3.1.1 三端，已验证 iOS 构件）——**独立 gate**：能力探测+超时熔断+失败静默回退文字 | 新建 `domain/agent/port/RealtimeVoiceTransport.kt`（纯 common） | FakeRealtimeTransport 协议测试（事件序列回放）         |
| M7-T4 | 语音会话：transcript 双向流→一 UI 两形态（语音气泡=文字+内存内重放）、会话模式（VoiceSessionController）+CONFIRM 口头化、混排                       | ChatScreen 气泡扩展                                            | 会话协议测试；语音会话写操作有文字记录（可审计）                   |

**gate 规则**：M7-T3/T4 端点不可用或验证不过→**B6 语音整体延期**，报告角色+设置页照常交付——v1 完整性不依赖语音。

### R 阶段：债务清零与交互地基修复（还债阶段，横切；先于 M5 完成态）✅ 2026-08-30 代码层完成

### S 阶段：工具层终局（批次 A + 批次 B）✅ 2026-08-31 完成

> R 阶段修了工具层遗留（searchLibrary 标签与 id / controlPlayback play_by_id 引导），但 10 工具原型到 27 原子工具的域前缀统一、原子拆分、Library/Song/Playback 域补齐，留到本阶段闭环。**S 阶段独立于 M0-M7 主序列**——M3 首次交付（十项工具）已完成，S 是 R 之后、回到 M4/M6 之前的工具层补全。

| 批次 | 任务 | 结果 |
|------|------|------|
| 批次 A | 域前缀统一（playback_*/playlist_*/library_*/song_*）+ 原子拆分（control vs play_at）+ Playlist 八件套（CRUD + 曲目管理 + 系统歌单保护） | ✅ 10 → 17 原子工具；desktopTest 全绿 |
| 批次 B | Library 域聚合查询 6 个（artists/albums/tags + songs_by_*）+ Song 域 USER 标签写入闭环（tag_user_add/remove，source=USER 永不被模型覆盖）+ PlaybackCommand.ADD_TO_QUEUE + agent_budget stub | ✅ 17 → 27 原子工具；desktopTest 677 全绿；compileAndroidMain/compileKotlinDesktop 通过 |

底层依赖补完（批次 B 触发）：
- MusicRepository 接口补 getAllArtistsSummary / getAllAlbumsSummary / removeUserMusicLabel
- MusicRepositoryBase 用 Kotlin 侧 groupBy 实现聚合（不新增 DAO SQL，避免三端平台层同步）
- MusicLabelDao.deleteUserLabel（硬编码 `source='USER'` 防误删 LLM 富化标签）
- PlaybackCommand 密封接口补 ADD_TO_QUEUE(Long) 枚举
- FakeAgentMusicRepository + FakeMusicRepository 同步补 3 方法

ToolNames.ALL 27 常量 ↔ ToolRegistry 27 注册 1:1 匹配；批次 A 结束 desktopTest 全绿；批次 B 结束 desktopTest 677 全绿。

**退出**：工具层 27 原子工具闭环，LLM 可 function-calling 组合出任意编排。

> **进度**：R-T1..R-T6 全部实现（首轮注入/漏斗/真实播放端口/多确认门/会话持久/M5 剩余 UI）。
> **验收缺口（待补）**：iOS 编译未验证（仅 android/desktop）；门面/搜索条带交互核验未深入；审计页/撤销按定义留 M6；本地化（14 语言）为横切项未做。

> **定位**：M5 一期交付了「能跑通」的对话页，但交互主干多处断裂——首轮上下文为空、两级漏斗缺失、
> 真实播放端口是 Fake、多确认门会挂起、会话不持久、M5 剩余 UI（T3/T5/T6/T7）未做。
> 本阶段把**定义有、实现空**的债务一次清掉，并并入当前必需的 B 内容。**独立于 M0-M7 编号**（不清原里程碑），
> 与总纲 7.4 的 B 展开按实际进度调和，此处不绑定版本号。
> **本次并入的 B 内容**：仅 R-T3 真实播放/现在听端口（M5 反馈闭环与 `controlPlayback` 工具能真跑的硬前提）。
> **明确留后续（不并入 R）**：DJ 线、事件触发/存在感全量、审计页/撤销、听歌报告、语音档、伙伴设置页六分区。

| ID | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| R-T1 | 首轮上下文注入装配器：CompanionProfile(人格v0)/称呼 + 曲库概览聚合(概览法) + 真实当前曲目(NowPlaying) + 时段 + 认识进度 → 喂进 `RunContextInput` + `buildChatSystemPrompt` | 新 `runtime/ContextAssembler.kt`、`MasterAgent.buildChatSystemPrompt`、`RunContextInput`、`ChatAgentGateway` | 首轮系统提示含 5 类内容；快照单测；三端编译 |
| R-T2 | 两级漏斗 `CommandLexicon`：高频词表直映射(零 token/50ms) + 模糊意图升级 agent + FREE(无Key) 高频命令可用 | 新 `domain/agent/funnel/CommandLexicon.kt` + ChatScreen/浮层接线 | 命中/未命中/升级语义单测；FREE 模式高频命令可用 |
| R-T3 | 接**真实播放/现在听端口**适配器（复用 `PlaybackController` 桥）替换 Fake | shared-ui `platform/` 新适配器 + `ChatKoinModule` | `controlPlayback` 真实生效；`getNowPlayingContext` 返回真实当前曲目；`:shared` 不反向依赖 shared-ui；Fake 测试保留 |
| R-T4 | 多确认门修复：`ChatViewModel` 确认槽队列 + `ConfirmBridge` 多批次 | `ChatViewModel.kt`、`ConfirmBridge` | 单轮多次确认不覆盖/不挂起；审批同序测试 |
| R-T5 | `agent_message` 会话持久化 + `session_id` 分页（ChatViewModel 真实读写） | `ChatViewModel.kt` + `AgentMessageDao` 接线 | 会话持久；分页单测 |
| R-T6 | M5 剩余 UI：`AgentNoticeBar`(T3) + 门面二期(T5) + 搜索条带(T6) | 新 `common/components/AgentNoticeBar.kt`、`HomeScreen.kt`、`SearchScreen.kt` | 三端编译 + 模拟器/桌面交互核验 |

**退出**：交互主干（触发→理解→执行→呈现→反馈→审计）闭环——首轮注入完整 + 漏斗 + 真实播放端口 + 无确认挂起 + 会话可持久 + M5 剩余 UI 三端可用。

### T 阶段：Agent 体系终局——Master Agent 唯一大脑 + Enrich SubAgent 跑通 + 两层基础设施

> **当前诊断（2026-08-31）**：agent 是"植物人"——闭箱、失忆、被动。27 原子工具 + 引擎循环 + 确认护栏都齐全，但：
> - 跑一轮对话 = 醒一次，醒来就失忆（跨会话零积累）
> - 闭箱运行：agent 调 `playback_play_at` 让你听一首歌，但你手动跳过 → agent 不知道
> - 完全被动：只有你发消息才运行，不会主动排下一首
> - 学习闭环断裂：跳过了 agent 推的歌 = 无事发生，永远第一次见面
> - LLM 是单故障点，额度耗尽 = agent 死
>
> **根因**：当前架构只有 Tool + 单个对话 Agent 两层。这个 Agent 就是 chatbot——一次性 run()、被动响应、无自主行为。缺的是：独立的长期运行时（Sub Agent）、跨 Agent 共享的感知/记忆/配额管理。
>
> **本阶段定位**：重塑 Agent 体系的**基础设施层 + Master 内核 + Enrich 端到端**，Radio SubAgent 留 M6 填实现（T 只预留基类）。不推倒重来——现有 chatbot（AgentOrchestrator）、27 原子工具、ToolRegistry 全部复用，T 是「在现有 chatbot 上焊一层 Master 管理逻辑 + 加一个 Enrich 执行器」。

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

## 4. 横切工作

| 项    | 内容                                                                                                                  | 挂靠   |
| ---- | ------------------------------------------------------------------------------------------------------------------- | ---- |
| 本地化  | 伙伴文案（对话/浮层/侧条/门面/伙伴设置）预估 80-120 条 × **14 语言**，v6.12 规范（占位符/换行/实体转义）；键沿用 `ai_*`/`settings_*` 前缀；**随 M1-M7 增量同步，不积压** | 全部阶段 |
| 测试基建 | Fake\* 塞系：FakeLlmTransport（M2）/FakePlaybackCommandPort（M4）/FakeRealtimeTransport（M7）；Room in-memory 迁移测试（M0）        | 阶段内  |
| 审计   | agent\_audit\_log 写入埋点：工具调用/许可裁决/云端修正（NOTIFY 级一律留痕）                                                                 | M4 起 |
| 文档   | 本任务书随进展更新；与总纲（agent.md）双向同步；TODO.md 方向 B 任务编号（B0-B6）对齐                                                              | 全程   |

***

## 5. 挂起参数与建议默认值

以下参数总纲显式挂起至实施规划，本任务书给出**默认值**（实施中可调，调整须记入变更记录）：

| 参数        | 建议默认                                           | 归属                |
| --------- | ---------------------------------------------- | ----------------- |
| 步数预算      | 8 步（总纲 7.1 已定）                                 | MasterAgent.handleUserMessage |
| 云端频率/额度配额 | 单日云端调用上限 100 次（可配置；额度耗尽→本地兜底）                  | ContextBudget     |
| 信任阶梯升级阈值  | 同类写动作连续隐式接受 3 次升一档（建议→代劳→静默）                   | TrustLedger       |
| 跳过感知触发    | 连跳 2 首（总纲场景 2 已定）                              | PresenceBus 事件    |
| 任务进行条文案   | 「正在翻你的曲库…」等 3-5 条（进本地化管线）                      | M5                |
| 审计保留策略    | agent\_message 保留全部/30 天/90 天三档（默认 90 天，设置页可切） | M7-T2 记忆管理        |

***

## 6. 风险与 gate

| 风险                  | 缓释                             | 触发 gate             |
| ------------------- | ------------------------------ | ------------------- |
| B0 去重不达预期（平台差异比预想大） | 逐方法判定；目标 30-40% 为下限而非野心值；不追求全量 | M0 退出时记录实际占比        |
| 5 家服务商 tools 支持度差异  | M2 能力探测+无 tools 端点降级纯文本        | 任一主流端点工具调用失败→降级路径兜底 |
| Realtime 语音端点质量/可达性 | 能力探测+超时熔断+静默回退；**语音独立 gate**   | M7：端点不可用即整体延期       |
| 上下文组装漂移（人格/记忆注入不一致） | 组装器单测+快照测试（M4-T3）              | —                   |
| 全局文案拖期（14 语言）       | 随阶段增量提交（第 4 章），不做末端一次性补        | M5 退出前对话页文案须全语言就绪   |
| 三端交互不一致（锚点手势）       | M1 三端模拟器核验先行；M5 复查             | —                   |

***

## 7. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-09-01 | **T 阶段代码层终局 + 权限体系简化 + 持久化闭环**：① 权限体系简化 9→6 概念：砍 TrustTier enum → trustLevel:Int + TrustLevel object 常量；砍 AgentPolicyConfig.allowFromOverride → 2 字段极简；砍 DirectToolExecutor 僵尸 object；TrustLedger 复活（持有 AgentPolicyConfig 引用 + onChange 回调驱动 trustLevel 升降档）；② EnrichSubAgent 接入权限体系（不再 DirectToolExecutor 裸跑，改用 ToolCallExecutor + AgentPolicy.enrich）；③ ConfirmGate UI 扩展"总是允许"（ConfirmMatrixCard + toggleAlwaysAllowConfirmItem），AllowOnce 明确不累积信任（单次确认无论多少次都不改 trustLevel）；④ AgentPolicyConfig DataStore 持久化（三端 SettingsRepository impl + MasterAgent init 块 runBlocking 读 + TrustLedger onChange 异步写 + ReActLoop.onSessionComplete 统一刷 alwaysAllow 变化）；⑤ MasterAgent 瘦身 640→454 行，ReActLoop/ToolCallExecutor/StopSignal 四层组件彻底解耦；⑥ T 阶段退出条件 E1-E8 代码层全完成，可进入 M6 |
| 2026-08-31 | **T 阶段全面重写（第二轮）**：修正三处核心架构错误——① ContextBudget 拆为两层：`AgentContextBudget`（每 Agent 独立，管自己 LLM 上下文窗口 + 历史压缩）+ `AgentScheduler`（全局唯一，纯规则零 LLM，只管 pause/resume）；② Master 是唯一大脑：派发/验收/生命周期全归 Master，子Agent 只有执行权（F1 铁则），Enrich 不再自毁/自己找活干；③ Enrich SubAgent 重定义为纯被动执行器：只接收 Master 派发的 batchChannel、调独立 LLM 实例执行、写 DB，不做任何决策。批次计划从 T1-T5（Feedback/Memory/Perception/Radio/Fallback）重构为 T1-T4 严格串行链：T1 基础设施（拆两层 ContextBudget + 新 AgentScheduler + SubAgent 基类 + ToolRegistryView 权限过滤）→ T2 Master 内核（AgentOrchestrator 升级 + 富化健康度检测 + 派活/验收轻量协程循环）→ T3 Enrich 实现（纯被动执行器）→ T4 联调 + Radio 预留。Radio SubAgent 明确留到 M6 填实现（T 只预留 SubAgent 基类 + Scheduler priority=2 + ToolRegistryView 权限白名单）；M6 依赖从 M5 改为 M5 + T。设计铁则 6 条（F1-F6）写入文档作为实施红线。退出条件 8 条，scope 边界 7 条明确不做。依赖图 + 总览表 + M6 描述同步修正 |
| 2026-08-31 | **T 阶段方案（初版 → 修正版，已废弃）**：初版方案定义 5 根脊柱但过度设计（AgentProfile 独立 DAO 层 / AgentSenses expect-actual / Scheduler 复活 / FallbackOrchestrator 独立类），修正后 FeedbackCollector 直接写 MusicLabel。**已被第二轮重写完全取代** |

| 日期         | 变更                                                                                                                                                                                                  |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-08-26 | v1：依据 agent.md（深度合并稿）编制；阶段制 M0-M7，不绑定版本号；B0 归 M0 优先（可在非 agent 版本先行）；语音 gate 明确；挂起参数给建议默认值                                                                                                           |
| 2026-08-28 | M5 一期：ChatScreen/ChatViewModel + 五类气泡 + 批量确认卡流（T1/T2/T4）；Gateway 接口 + Orchestrator 真实接线；顺带修复 TrustLedger/MusicRepositoryBase 的 iOS 跨平台缺口（Map.getOrDefault→显式判空；System.currentTimeMillis→跨平台 expect） |
| 2026-08-31 | **M3 工具层终局（批次 A + B）**：批次 A 完成域前缀统一重命名/拆分（17 工具，批次 A 结束 desktopTest 全绿）；批次 B 追加 10 原子工具（playback_enqueue + library 域聚合查询 6 个 + song_tag_user_add/remove + agent_budget stub），Registry 17→27；底层依赖：MusicRepository.getAllArtistsSummary/getAllAlbumsSummary/removeUserMusicLabel（Kotlin 侧 groupBy，不新增 DAO SQL）、MusicLabelDao.deleteUserLabel（source=USER 过滤）、PlaybackCommand.ADD_TO_QUEUE 枚举；ToolNames.ALL 27 常量 ↔ ToolRegistry 注册 1:1 匹配；desktopTest 677 全绿，compileAndroidMain/compileKotlinDesktop 通过；FakeAgentMusicRepository + FakeMusicRepository 同步补 3 方法 |
| 2026-08-30 | **R 阶段落地**：R-T1 首轮注入（CompanionProfile/ContextAssembler/buildSystemPrompt + gateway 真实装配）、R-T2 漏斗（CommandLexicon + ChatViewModel 接线）、R-T3 真实播放/现在听端口、R-T4 多确认门（submittedConfirms）、R-T5 会话持久（AgentMessageStore/RoomAgentMessageStore + 三端 DI）、R-T6 M5 剩余 UI；工具层修复（searchLibrary 标签+id、controlPlayback play_by_id 引导、getRecentHistory 带标题）；AgentLog 运行时日志；对话页沉浸式 UI 重构（去顶栏/去头像/紧凑输入栏/键盘贴键盘/新增消息与聚焦自动滚底/横滑与页点恢复）。**验收缺口**：iOS 编译未验证、门面/搜索交互核验未深入、审计页/撤销留 M6、本地化横切未做 |

<br />
