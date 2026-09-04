# HMP W2 · 组件级改造

> 承接 `agent.md`（设计总纲，定位/交互语言/交互全景/场景流/组件清单）。
> **依赖**：T + M6 + V + W1。
> **核心工作**：把引擎 emit 的事件接到组件级 UI 消费点上。引擎已齐，UI 还没"接线"。

***

## W2 · 组件级改造清单（11 个）

### 锚点层（agent.md §5.2，无引擎也可先行合入）

| #  | 组件                   | 当前状态                                                            | 引擎依赖                                       | W2 做什么                                                           |
| -- | -------------------- | --------------------------------------------------------------- | ------------------------------------------ | ---------------------------------------------------------------- |
| C1 | CompanionCapsule 徽标态 | 114 行骨架，只传了 onClick                                             | PresenceBus.CompanionBadge / AgentProgress | 加 BadgeOverlay composable + LaunchedEffect 收徽标状态 + 渲染（红点/脉冲圈/绿点） |
| C2 | AgentQuickSheet 轻量浮层 | 126 行骨架                                                         | ChatAgentGateway.pendingInput              | Esc/点外关闭 + CompanionCapsule.onLongPress 透传 + C 键唤起               |
| C3 | AgentNoticeBar 侧条    | 91 行骨架，showUndo 硬编码 false                                       | PresenceBus.NoticeAvailable + 反向工具         | showUndo 改 true + onUndo callback 接 ToolRegistry 反向工具            |
| C4 | 锚点手势矩阵               | CompanionCapsule detectTapGestures 声明了 onLongPress 但 AppRoot 没传 | —                                          | onTap / onLongPress / 播放页「对话」按钮 的完整手势映射表                         |
| C5 | C 键全局监听              | 没做                                                              | —                                          | AppRoot Scaffold onKeyEvent 拦截 C 键 → 唤起 AgentQuickSheet          |

### 场景入口（agent.md §6 场景交互流 落点挂到既有页面）

| #   | 组件     | 挂载页面                                  | 当前状态                                                         | 引擎依赖                                                | W2 做什么                                                         |
| --- | ------ | ------------------------------------- | ------------------------------------------------------------ | --------------------------------------------------- | -------------------------------------------------------------- |
| C6  | 伙伴卡    | UserScreen                            | L256 profileCard → SettingsListCard，中间空着                     | CompanionProfile（未配置=引导态）                           | 在 profileCard 和 SettingsListCard 之间插伙伴卡（未配置=「去配置」→AIScreen P2） |
| C7  | 伙伴条带   | SearchScreen                          | L143-146 已有 `showIntentStrip` + `CommandLexicon.classify` 骨架 | ChatAgentGateway.pendingInput                       | 结果顶部两级漏斗：「交给伙伴」=输入作首条消息进会话 / 「只是搜索」=收起且本次会话同类输入不再弹             |
| C8  | 「对话」按钮 | PlayerScreen（PlaybackControlsButtons） | 主行三键 + 副行五键                                                  | CompanionCapsule 轻量浮层                               | 副行重排加「对话」按钮 → 唤起 AgentQuickSheet（播放页沉浸态不跳门面）                   |
| C9  | 「问歌词」钮 | LyricsScreen                          | 纯歌词滚动 + 翻译切换                                                 | MasterAgent.handleUserMessage（场景流 15）               | 歌词页加「问歌词」钮 → 调引擎生成翻译卡 + 典故 explain 卡                           |
| C10 | 歌单解释行  | PlaylistScreen                        | 纯歌曲列表                                                        | ToolExecutionRecord（区分 agent 创建 vs 用户手建）            | 伙伴生成歌单顶部加解释行（「小知按你的听歌习惯…」+「为什么」折叠）；用户手建歌单不加                    |
| C11 | 深读区块   | ArtistScreen / AlbumScreen            | 基础信息 + 歌曲列表                                                  | EnrichSubAgent 产出的 singerIntroduce / 风格标签图谱（场景流 14） | 既有信息之下插 agent 富化正文（「这个乐队的历史可以追溯到…」）+ 关联歌曲 / 播放统计               |

