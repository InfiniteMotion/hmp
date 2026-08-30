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
| 引擎 | AgentOrchestrator（步数预算 8）+ Scheduler + PolicyGuard + TrustLedger + ContextBudget + SessionStore + PresenceBus |
| 端口 | PlaybackCommandPort（:shared 定义接口，三端适配器复用现有 Controller 桥）                                                      |
| UI | 三胶囊底栏、轻量浮层、对话页（五类气泡）、确认卡片流、通知侧条、审计日志页、门面二期、伙伴设置页六分区                                                           |
| 场景 | 15 场景中的 11 个（一/二/三梯队全量 + 四梯队的听歌报告与遗忘唤醒）                                                                       |

**明确不做（v1 边界）**：实时语音（除非端点可用且 M7 验证通过）、四梯队艺术家深读/歌词注解的完整版、任何在线音乐服务闭环。

***

## 2. 阶段总览与依赖图

```
M0 地基与还债 ──▶ M2 协议层 ──▶ M3 工具层 ──▶ M4 引擎循环 ──▶ M5 对话与锚点二期 ──▶ M6 电台与事件 ──▶ M7 报告与语音
   (B0)            (B1)          (B2)           (B3)              (B4)                     (B5)               (B6)

M1 锚点层一期（B4 的前置 UI 骨架，Fake 数据驱动，与 M0-M7 完全并行）
横切：本地化（贯穿 M1-M7）· 测试基建（Fake* 替身随阶段建立）
```

| 阶段 | 内容                               | 依赖          | 关键退出标准                                  |
| -- | -------------------------------- | ----------- | --------------------------------------- |
| M0 | 三端 Repository 去重 + Room v2（B0）   | —           | 全平台编译 + 迁移单测绿 + 既有 650 单测零回归            |
| M1 | 锚点层一期：三胶囊/浮层/播放页重排/C 键（Fake 驱动）  | —（可与 M0 并行） | 三端编译 + 模拟器锚点交互核验                        |
| M2 | LlmTransport 流式协议 + tools 参数（B1） | M0          | 协议层单测全绿（SSE 解析/错误路径/5 服务商兼容）            |
| M3 | 工具层：ToolSpec DSL + 十项工具（B2）      | M2          | 工具校验单测防漂移                               |
| M4 | 引擎循环四件套 + 双层预算（B3）               | M3          | 引擎全行为确定性测试（步数/许可/拒绝/审计断言）               |
| M5 | 对话页 + 五类气泡 + 确认流 + 门面二期 + 漏斗（B4） | M4 + M1     | 纯文字完整体验三端可用                             |
| M6 | 电台三轮协作 + 跳过感知 + DJ 衔接 + 审计页（B5）  | M5          | FakeLlm+FakePlaybackCommandPort 电台确定性测试 |
| M7 | 报告角色 + 伙伴设置页 + 语音档（B6）           | M6          | 语音为独立 gate，可整体延期不影响 v1 完整性              |
| R  | 债务清零与交互地基修复（首轮注入/漏斗/真实播放端口/多确认/会话持久/M5 收尾 UI） | M5 未完成 + 定义级漏项 | 交互主干（触发→理解→执行→呈现→反馈→审计）闭环；三端编译 |

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

### M3 工具层（B2，\~4 人天）

| ID    | 任务                                                                                                                                                                                                                                                      | 涉及文件                                        | 验收                 |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ------------------ |
| M3-T1 | `ToolSpec` DSL：手写 schema 生成 + 参数校验器（与 kotlinx.serialization 协同）                                                                                                                                                                                         | 新建 `domain/agent/tool/ToolSpec.kt`          | schema↔校验单测防漂移     |
| M3-T2 | 十项工具实现 + `ToolRegistry`：read/calc 六项静默（searchLibrary/getListenStats/getRecentHistory/getNowPlayingContext/getSimilarSongs/getMusicExtra）、enrichSong 通知、写三项确认（createPlaylist/addToPlaylist+reorderPlaylist/controlPlayback）；工具描述三段式（行为约束≤50 条/成本提示/替代指引） | 新建 `domain/agent/tool/` 十个实现 + ToolRegistry | 每工具单测（成功/空结果/参数越界） |
| M3-T3 | 工具结果回填语义：工具执行结果强制回填上下文（防幻觉型假成功），失败=异常结果入审计                                                                                                                                                                                                              | ToolRegistry                                | 回填与失败路径单测          |

**退出**：工具层单测全绿；工具白名单与许可级可被 M4 引用。

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

### M6 电台与事件（B5，\~5 人天）

| ID    | 任务                                                                                                                                           | 涉及文件                                        | 验收                                             |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ---------------------------------------------- |
| M6-T1 | AI 电台三轮协作接线：种子认识（第 0 步 enrichSong）→ 本地保底队列（零等待开听）→ 云端全量清单决策 → diff 仲裁回传；开电台=`songlist` 卡（「今夜电台 · 12 首备选」）、播完续歌 SILENT、徽标=电台运行点、每次续选「为什么」入审计页 | AgentOrchestrator 接线 + ChatViewModel        | FakeLlm+FakePlaybackCommandPort 电台确定性测试（三轮各路径） |
| M6-T2 | 跳过感知重排（连跳 2 首→重排 SILENT+侧条「换了一批安静的，↩恢复」）；一句话切换（浮层→漏斗→执行+侧条+对话页沉淀）                                                                            | PresenceBus 事件接线                            | 事件触发测试                                         |
| M6-T3 | DJ 衔接预生成（曲间一句，门面问候区轮换+对话页 text 沉淀；播放中不弹浮面硬纪律）                                                                                                | PresenceBus + 门面                            | 预生成零延迟核验；硬纪律断言（手势进行中无浮面）                       |
| M6-T4 | `AuditLogScreen`：agent\_audit\_log 驱动、时间倒序、每行动作+「为什么」展开（→T0 行为证据）+撤销；撤销边界（被用户改过的动作降级为「删除」走 STRONG\_CONFIRM；重排队列类始终可撤销）                       | 新建 `audit/AuditLogScreen.kt`                | 撤销边界单测                                         |
| M6-T5 | STRONG\_CONFIRM 双确认链：删歌单/改 ID3 走 DialogHost 模态（伙伴问意图→系统问权限，顺序不合并）                                                                            | 复用 AppRoot DialogHost + EditMusicTagsScreen | 双确认链路核验                                        |

**退出**：电台三轮协作全路径确定性测试；审计页承载全部「为什么」。

### M7 报告角色与语音档（B6，\~7 人天，语音独立 gate）

| ID    | 任务                                                                                                            | 涉及文件                                                       | 验收                                         |
| ----- | ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| M7-T1 | 听歌报告（月度门面卡片入口→报告页图表复用 `ListeningChart` + 伙伴叙事段）；遗忘唤醒（胶囊徽标低频+对话页消息，点开才算送达不追问）                                  | 门面卡片 + ChatViewModel                                       | 报告数据管线测试                                   |
| M7-T2 | 伙伴设置页六分区（AI 页演进不推翻：身体素质/人格/嗓音与耳朵/认识进度/记忆与信任/记忆管理）；`LoadMusicExtraInfo` 重命名复用为认识进度；信任阶梯档位回拨 UI；审计页入口           | `settings/pages/AIScreen.kt`、`UserScreen.kt` 伙伴卡           | 设置读写 DataStore 单测；路由复用 Routes.AI.AI（无路由迁移） |
| M7-T3 | `RealtimeVoiceTransport`（WebSocket：JSON 控制事件+二进制音频帧+Ktor 3.1.1 三端，已验证 iOS 构件）——**独立 gate**：能力探测+超时熔断+失败静默回退文字 | 新建 `domain/agent/port/RealtimeVoiceTransport.kt`（纯 common） | FakeRealtimeTransport 协议测试（事件序列回放）         |
| M7-T4 | 语音会话：transcript 双向流→一 UI 两形态（语音气泡=文字+内存内重放）、会话模式（VoiceSessionController）+CONFIRM 口头化、混排                       | ChatScreen 气泡扩展                                            | 会话协议测试；语音会话写操作有文字记录（可审计）                   |

**gate 规则**：M7-T3/T4 端点不可用或验证不过→**B6 语音整体延期**，报告角色+设置页照常交付——v1 完整性不依赖语音。

### R 阶段：债务清零与交互地基修复（还债阶段，横切；先于 M5 完成态）✅ 2026-08-30 代码层完成

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
| R-T1 | 首轮上下文注入装配器：CompanionProfile(人格v0)/称呼 + 曲库概览聚合(概览法) + 真实当前曲目(NowPlaying) + 时段 + 认识进度 → 喂进 `RunContextInput` + `buildSystemPrompt` | 新 `engine/ContextAssembler.kt`、`AgentOrchestrator.buildSystemPrompt`、`RunContextInput`、`ChatAgentGateway` | 首轮系统提示含 5 类内容；快照单测；三端编译 |
| R-T2 | 两级漏斗 `CommandLexicon`：高频词表直映射(零 token/50ms) + 模糊意图升级 agent + FREE(无Key) 高频命令可用 | 新 `domain/agent/funnel/CommandLexicon.kt` + ChatScreen/浮层接线 | 命中/未命中/升级语义单测；FREE 模式高频命令可用 |
| R-T3 | 接**真实播放/现在听端口**适配器（复用 `PlaybackController` 桥）替换 Fake | shared-ui `platform/` 新适配器 + `ChatKoinModule` | `controlPlayback` 真实生效；`getNowPlayingContext` 返回真实当前曲目；`:shared` 不反向依赖 shared-ui；Fake 测试保留 |
| R-T4 | 多确认门修复：`ChatViewModel` 确认槽队列 + `ConfirmBridge` 多批次 | `ChatViewModel.kt`、`ConfirmBridge` | 单轮多次确认不覆盖/不挂起；审批同序测试 |
| R-T5 | `agent_message` 会话持久化 + `session_id` 分页（ChatViewModel 真实读写） | `ChatViewModel.kt` + `AgentMessageDao` 接线 | 会话持久；分页单测 |
| R-T6 | M5 剩余 UI：`AgentNoticeBar`(T3) + 门面二期(T5) + 搜索条带(T6) | 新 `common/components/AgentNoticeBar.kt`、`HomeScreen.kt`、`SearchScreen.kt` | 三端编译 + 模拟器/桌面交互核验 |

**退出**：交互主干（触发→理解→执行→呈现→反馈→审计）闭环——首轮注入完整 + 漏斗 + 真实播放端口 + 无确认挂起 + 会话可持久 + M5 剩余 UI 三端可用。

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
| 步数预算      | 8 步（总纲 7.1 已定）                                 | AgentOrchestrator |
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

| 日期         | 变更                                                                                                                                                                                                  |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-08-26 | v1：依据 agent.md（深度合并稿）编制；阶段制 M0-M7，不绑定版本号；B0 归 M0 优先（可在非 agent 版本先行）；语音 gate 明确；挂起参数给建议默认值                                                                                                           |
| 2026-08-28 | M5 一期：ChatScreen/ChatViewModel + 五类气泡 + 批量确认卡流（T1/T2/T4）；Gateway 接口 + Orchestrator 真实接线；顺带修复 TrustLedger/MusicRepositoryBase 的 iOS 跨平台缺口（Map.getOrDefault→显式判空；System.currentTimeMillis→跨平台 expect） |
| 2026-08-30 | **R 阶段落地**：R-T1 首轮注入（CompanionProfile/ContextAssembler/buildSystemPrompt + gateway 真实装配）、R-T2 漏斗（CommandLexicon + ChatViewModel 接线）、R-T3 真实播放/现在听端口、R-T4 多确认门（submittedConfirms）、R-T5 会话持久（AgentMessageStore/RoomAgentMessageStore + 三端 DI）、R-T6 M5 剩余 UI；工具层修复（searchLibrary 标签+id、controlPlayback play_by_id 引导、getRecentHistory 带标题）；AgentLog 运行时日志；对话页沉浸式 UI 重构（去顶栏/去头像/紧凑输入栏/键盘贴键盘/新增消息与聚焦自动滚底/横滑与页点恢复）。**验收缺口**：iOS 编译未验证、门面/搜索交互核验未深入、审计页/撤销留 M6、本地化横切未做 |

<br />
