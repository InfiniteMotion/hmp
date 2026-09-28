# Agent 生命周期架构

> **本文件**：agent 体系（MasterAgent + 全部 SubAgent + Scheduler + 观测面）的**生命周期与后台存活**的唯一依据。
> **回答什么**：谁持有 agent 体系的运行时生命周期、退后台怎么不死、跨平台怎么统一。
> **对应阶段**：[`../taskbook/f11-后台生命周期.md`](../taskbook/f11-后台生命周期.md)（推进与验收档案）。
> **状态**：方案已定（2026-09-19）。**D3 裁决：不做电台快照落盘**（见 §5）。冲突时以本文 + `agent-radio.md` 的会话边界（C3）为准。

***

## 0. 结论先行

**问题不是"RadioAgent 缺个钩子"，而是整个 agent 体系没有独立的生命周期所有者。**

**两条根因**（任一单独存在都会让后台存活失败），外加一条**已决边界**（刻意为之，不是缺陷）：

| 编号 | 根因 | 现状 |
|---|---|---|
| **RC1** | agent 运行时是**进程级单例 + 懒初始化**，无独立生命周期所有者 | `MasterAgent` 是 Koin `single`，但只在"首次被 UI 注入"时才 `initialize()`；`lifecycleScope` 是脱离任何生命周期的裸 `SupervisorJob` |
| **RC2** | **Android 播放服务只是 bind-only，不是自持前台服务** | `MusicPlayService` 仅被 `MainActivity.onStart → bindService(BIND_AUTO_CREATE)` 绑定；正常播放路径**从不 `startService`**。服务生命周期绑在 Activity 上，退后台被回收时进程一起消失 |
| **RC3** | 电台会话**纯内存、零持久化** | ⚠️ **2026-09-19 已决：这是刻意不落盘，不是缺陷** —— 见 §5。进程被杀 = 电台丢，明确接受 |

**推荐方案（一句话）**：把 agent 运行时提升为**显式的、进程级、随应用启动即初始化的生命周期所有者**；Android 侧把播放服务改成**自持前台服务**并让它同时承载 agent 保活（一条通知），iOS 侧用**音频后台模式 + 有限后台任务**。**不做电台快照持久化**（§5）。

> 本方案修正 `agent-radio.md` C3「退后台不算关闭，会话保留」背后的**未落实假设**——它假设"前台播放服务会保住进程"，而该假设在 Android 上从未成立（RC2），在 iOS 上也只覆盖"音频正在播"的窗口。

***

## 1. 现象与根因

### 1.1 现象

电台播放中退到后台，应用被系统回收后：

- 电台会话消失，回到前台需要**重新开播**（队列、主题、对话全丢）；
- 「继续电台」入口点不到之前那一档（`retained` 也是内存态，一并丢失）。

### 1.2 逐项排除（已核源码）

| 假设 | 核查结果 |
|---|---|
| 有生命周期钩子显式停了电台 | ❌ 不存在。`AgentMonitorScreen` 的 `onStop` 是监控屏"停止按钮"回调，非 Compose 生命周期钩子 |
| `MasterAgent.lifecycleScope` 随退后台取消 | ❌ 它是进程级 `SupervisorJob`，只在 `shutdown()/close()`（真正销毁）时取消 |
| Android 在某处 `masterAgent.close()` | ❌ 仅 `MusicApplication.onTerminate` / JVM shutdown hook（真退出路径） |
| 续歌链路挂在 UI 生命周期上 | ❌ 链路 `MusicController → PlaybackObservationBus(单例) → MasterAgent → radio.onQueueLow → refillQueue` 全是进程级 |

**排除了"被谁停掉"，剩下的唯一解释就是：进程/状态被系统回收，且没有恢复能力。**

### 1.3 根因展开

#### RC1 — 没有独立的生命周期所有者

- `MasterAgent`（`ChatKoinModule` 的 `single`）：`.also { lifecycleScope.launch { initialize() } }`——**懒**，首次 UI 注入才初始化。
- `AgentScheduler`：自带 `CoroutineScope(SupervisorJob() + Dispatchers.Default)`，每秒仲裁循环。
- SubAgent：`Hello` 随 `initialize()` 自动起、`Enrich` 条件起、`Radio` 按需起。
- 结论：**运行时全部"住在进程里"，但没人对"进程该不该活着"负责。**

#### RC2 — Android 播放服务不是自持前台服务（关键）

```
MainActivity.onStart()    → musicController.bindService()      // BIND_AUTO_CREATE
MainActivity.onDestroy()  → musicController.release(); unbindService()
全仓 startForegroundService → 仅 MusicNotificationReceiver（点通知按钮时）
```

- `onCreate()` 会建 `ExoPlayer` + `MediaSession`；`startForeground()` 只在"曲目 ready"监听器（`updateNotificationWithCover`）或 `onStartCommand` 里被调。
- 但服务**从未被 `startService`/`startForegroundService` 启动**（除通知按钮），所以它是 **bound-only**：生命周期跟随绑定它的 Activity。退后台 Activity 被回收 → 解绑 → 服务销毁 → 进程可回收。
- **同进程的 agent 因此陪葬。**

#### RC3 — 零持久化（**已决：刻意不落盘**）

`RadioSubAgent` 的可恢复状态全部在内存（`retained` 快照、队列镜像、会话 `messages` + 会话档案），明确不落盘。进程死亡 = 会话丢失，无法续档。

**但这是设计选择，不是待修的缺陷**（2026-09-19 裁决，理由见 §5）。它与 RC1/RC2 性质不同：RC1/RC2 是**没落实的假设**（能力缺失），RC3 是**落实了的边界**（有意不接受跨进程恢复）。

***

## 2. 现状盘点

### 2.1 运行时组件（作用域与初始化）

| 组件 | 作用域 | 初始化时机 | 退后台是否存活 |
|---|---|---|---|
| `MasterAgent` | Koin `single`（进程） | **懒**（首次注入） | 取决于进程 |
| `lifecycleScope` | 裸 `SupervisorJob` | 随 Master 构造 | 是（仅真销毁时取消） |
| `AgentScheduler` | 自带进程 scope | 随 Master 构造 | 是 |
| `HelloSubAgent` | Master 持有 | `initialize()` 自动起 | 是 |
| `EnrichSubAgent` | Master 持有 | `initialize()` 条件起 | 是 |
| `RadioSubAgent` | Master 持有 | `startRadio()` 按需起 | 是（但状态内存态） |
| `PlaybackObservationBus` | Koin `single` | 首次注入 | 是 |

### 2.2 平台保活现状

| 平台 | 机制 | 覆盖窗口 | 缺口 |
|---|---|---|---|
| **Android** | `MusicPlayService`（`foregroundServiceType=mediaPlayback`，`START_STICKY`） | **名义上**"播放中" | 实际只是 bind-only，无自持前台存活（RC2）；且不覆盖"电台构建中/等待模型"窗口 |
| **iOS** | `UIBackgroundModes=[audio]` + `AudioSessionManager` | **仅音频正在播** | 暂停 / 切歌间隙 / 电台等模型时无音频 → 被挂起 |
| **Desktop** | 进程常驻（窗口/托盘） | 应用运行期 | 最小化到托盘需确认不 dispose 运行时 |

***

## 3. 设计目标与原则

| 目标 | 说明 |
|---|---|
| **G1 单一所有者** | 有一个明确对象负责 agent 运行时的生命周期与保活诉求 |
| **G2 单一保活锚点** | 同一平台最多一条常驻通知；不与现有播放通知打架 |
| **G3 保活窗口 ⊇ 活跃窗口** | "agent 活跃"（电台构建中/等模型/Enrich/主动问候）也应保活，不局限于"音频在播" |
| **G4 跨平台一致抽象** | shared 定义统一生命周期模型，平台只实现"保活"这一段 |
| **G5 最小侵入** | 不推翻现有 Koin/作用域结构，只补"所有者 + 保活"两件事（**不加持久化**，见 §5）|
| **G6 可降级** | 拿不到保活能力时，退化为"前台可用、后台尽力"，不崩 |

**原则**：**不新开"第二个纯 agent 前台服务"**。Android 每条前台服务都要常驻通知，双服务 = 双通知 + 双生命周期协同，收益不抵成本。保活锚点复用播放服务。

***

## 4. 目标架构

### 4.1 核心：显式的 `AgentRuntime` 所有者（shared）

在 shared 侧引入**进程级、随应用启动即初始化**的生命周期所有者（可命名为 `AgentRuntime`，或直接由 `MasterAgent` 承担，二选一，见 §8 待决策）：

```
AgentRuntime (进程级单例)
├── 持有：MasterAgent / AgentScheduler / 观测面订阅
├── 生命周期：应用启动 initialize() → 应用销毁 shutdown()
├── 状态：IDLE | ACTIVE（radio/enrich/hello 任一在跑）| PLAYING
└── 依赖：AgentKeepAlive（expect/actual，平台保活桥）
```

关键变化：

- **初始化时机**：从"首次 UI 注入懒初始化" → **应用启动即初始化**（`MusicApplication.onCreate` 显式调 `AgentRuntime.start()`，幂等）。
- **保活诉求**：`AgentRuntime` 在进入 `ACTIVE`/`PLAYING` 时向 `AgentKeepAlive.acquire()`，退出活跃时 `release()`。**平台据此决定是否保活**。

### 4.2 平台保活抽象（expect/actual）

```kotlin
// shared: 平台无关
interface AgentKeepAlive {
    fun setKeepAlive(reason: KeepAliveReason)   // PLAYING / BUILDING / BACKGROUND_TASK
    fun clearKeepAlive(reason: KeepAliveReason)
}
enum class KeepAliveReason { PLAYING, RADIO_BUILDING, PROACTIVE, ENRICH_TASK }
```

### 4.3 生命周期状态机（谁保活）

| 应用状态 | 音频 | agent 活跃 | 需要保活？ | Android 现实现 |
|---|---|---|---|---|
| 前台 | 任意 | 任意 | 否（前台天然存活） | 前台 |
| 后台 | 在播 | 任意 | **是** | 前台服务保持 |
| 后台 | 暂停/构建中 | **是** | **是** | 前台服务保持（覆盖"等模型"窗口）|
| 后台 | 停止 | 否 | 否（可被回收） | 允许 stopForeground / 进程回收 |

> 表头第三列修正：应为"Android 实现"。

### 4.4 Android 实现

**核心改动 = 把播放服务变成真正的自持前台服务，并扩展保活条件。**

1. **启动方式改为 started + foreground**：播放/建台路径（`MusicController.play*` / `startRadio`）调 `context.startForegroundService(MusicPlayService)`，服务内 `onStartCommand` 立即 `startForeground`（去掉"只在通知按钮触发"的依赖）。返回 `START_STICKY`。
2. **保活条件扩展**：服务保持前台的条件从"音频在播"扩展为 `音频在播 OR AgentRuntime.ACTIVE`。这样"电台构建中/等模型（无音频）"时进程仍受保护。
3. **解绑语义修正**：`MainActivity.onDestroy → unbindService()` 不再等于服务结束（因为服务已 started，`START_STICKY` 自持）。绑定仅用于 UI 取 `PlayControl`。
4. **通知复用**：沿用现有 `music_channel` 单条通知；电台活跃但无音频时，通知文案切到"电台准备中/由 <伙伴> 陪听"。
5. **前台服务类型**：继续用 `mediaPlayback`（电台本就是音频场景）。若未来做纯 Enrich 后台任务，另起 `dataSync` 型服务（独立议题）。

> ⚠️ 该改动会**同时修复"后台播放本身不稳"**（当前 bind-only 的服务在后台本就不受保护），属于顺带收益。

### 4.5 iOS 实现

- **播放窗口**：`UIBackgroundModes=[audio]` 已配，音频在播时进程存活 → agent 天然存活。
- **非播放的活跃窗口**（电台等模型、主动问候）：
  - 用 `beginBackgroundTask` 申请**有限后台时间**（约 30s），覆盖"当前回合 LLM 调用"；到时未完成则落回前台续。
  - 周期 Enrich 用 `BGProcessingTask`（受系统调度，非实时）。
- **约束**：iOS 不允许"无音频的长期后台常驻"。设计上**必须接受**：无音频时 agent 只能被短暂保活。若要长保活，需在等模型期间保持音频会话（可播静音低音量占位）——列为可选策略（见 §8）。

### 4.6 Desktop 实现

- 进程常驻即可。确认"最小化到托盘"不释放 Koin/`AgentRuntime`（当前 `Main.kt` 只在窗口关闭/退出时 `close()`，符合预期）。
- 无需额外保活。

***

## 5. 「从不落盘」是决定，不是妥协（D3 裁决，2026-09-19）

**裁决：维持 `agent-radio.md` 的「快照从不落盘」。不做任何形式的电台状态持久化。**

起草本文时曾建议"有限度修订"——只落 `{seed, nowPlayingId, 队列 id, 队列指纹, 会话消息, dayPart, closedAtMs}` 以兜住 RC3。**该建议被否决**，理由四条：

| # | 理由 | 说明 |
|---|---|---|
| **1** | **会造成第二条记忆通路（影子记忆）** | 项目已有**唯一**的跨会话记忆载体 `UserMemory`（`user_profile_evidence` / `user_profile_portrait` / `user_profile_narrative` 三表），且它有明确纪律：**每次写入都写 `agent_audit_log` 留痕**、画像过谓词闭集闸门、可查看可审计。电台快照若自行落盘，等于开出**不可见、不可审计、不受闸门管**的第二条通路，内容还含"哪首歌只听了 24% 就切走"这类行为推断 —— 与 F1–F9 建立的"记忆要走正门、要留痕"直接冲突 |
| **2** | **它是全项目时效性最差的状态** | 电台档案的重量集中在**情境性**内容（当前队列构成、在播曲、本轮台账）。隔数小时恢复，这些前提**全是错的** → 模型带着错误记忆推理。**空档案只是"新的一档"（无伤），过期档案是"带着错误记忆的同一档"（有害）** |
| **3** | **产品语义相反** | C2「只做会话内」是对的：电台是**临场 DJ**，用户点开要的是"此刻合身的一档新节目"，不是"上次那档的续集"。"接着上次那一档"是反预期的 |
| **4** | **收益窗口本就窄，且信号相反** | L2 已保护"音频在播 **或** agent 活跃"的进程。剩余被杀场景 = 用户手动清后台 / 极端内存压力 —— **那恰是"用户不想让它继续"的信号**。重开成本又低（本地秒开 + 模型定队列） |

**边界（必须同时成立）**：

- **进程内复用仍要完整**（本次已修）：关闭 → 重开（进程未死）时，`messages` **与会话档案**（`executed` / `intents` / `settled` / `originMs` / `turnIndex`）必须一起恢复，否则表现为"续上了但上下文不保留"。
- **跨会话认知走正门**：若将来需要"电台记得用户"，正确做法是把电台信号喂给 `UserMemory`（可审计、走闸门），**而不是让电台自己记事**。
- **接受"进程死 = 电台丢"**：不作为缺陷登记，不设兜底。

> 一句话：**该落盘的不是电台会话，而是"用户是谁"。前者是情境，后者才是记忆。**

***

## 6. 迁移计划（分阶段）

| 阶段 | 内容 | 依赖 | 验收 |
|---|---|---|---|
| **L1** | `AgentRuntime` 所有者 + 应用启动即初始化（三端）| 无 | 冷启动后 agent 已就绪（日志可见 `initialize` 在 app start 触发）|
| **L2** | Android：播放服务改 started+foreground，保活条件扩展 | L1 | 电台播放中退后台 → 杀后台压力下进程存活 ≥ 可感知时长；回到前台会话仍在 |
| **L3** | iOS：`beginBackgroundTask` 覆盖等模型回合 | L1 | 电台等模型时退后台 → 当前回合能完成 |
| ~~**L4**~~ | ~~最小电台快照持久化（RC3）~~ → **已撤销**（D3 裁决，见 §5）| — | 不交付 |
| **L5** | Desktop 托盘保活确认 + 文档收口 | — | 最小化到托盘后 agent 仍在 |

**顺序理由**：L1 是所有后续的地基；L2 解决 Android 主诉（"退后台不会存活"）。**L4 已撤销**——电台不落盘（§5），RC3 不再作为待闭环项，其余阶段不受影响。

***

## 7. 风险与开放问题

| 风险 | 说明 | 缓解 |
|---|---|---|
| R1 Android 14+ 前台服务限制 | `mediaPlayback` 类型需匹配真实音频场景；"无音频但保持前台"可能触发平台校验 | 电台活跃时保持音频会话（静音占位）或明确用 `dataSync` 型 |
| R2 双服务通知 | 若坚持独立 `AgentRuntimeService` → 双通知 | 本方案默认复用播放服务，规避之 |
| R3 iOS 长期后台 | 无音频时无法长期常驻 | 明确降级预期；可选静音占位策略 |
| R4 进程被杀 = 电台丢 | 不落盘（§5）的必然结果 | **明确接受，不设兜底**；靠 L2 把"活跃期进程不被回收"做扎实 |
| R5 初始化幂等 | 应用启动即初始化可能与 UI 首次注入重复 | `initialize()` 已有单次语义，补幂等守卫 |

***

## 8. 待决策项

| 编号 | 决策 | 选项 | 建议 |
|---|---|---|---|
| **D1** | 所有者形态 | (a) 新增 `AgentRuntime` 类 / (b) 由 `MasterAgent` 兼任 | (b) 最小侵入，先兼任，后续需要再抽 |
| **D2** | Android 保活载体 | (a) 改造 `MusicPlayService` / (b) 新 `AgentRuntimeService` | (a) 单通知、单生命周期 |
| **D3** | 电台快照是否落盘 | (a) 落盘最小集 / (b) 维持"从不落盘" | ✅ **裁定 (b) 维持不落盘**（2026-09-19，理由见 §5）。否决"最小集落盘"：会造成影子记忆、且过期档案比空档案更糟 |
| **D4** | iOS 无音频窗口保活 | (a) 仅 `beginBackgroundTask` 有限保活 / (b) 静音占位保持音频会话 | 先 (a)，(b) 视真机表现再评估 |
| **D5** | 实施范围 | (a) 仅 Android 主诉 / (b) 三端一次性 | 建议 L1+L2 先行（Android 主诉），L3-L5 跟进 |

***

## 附录 A：核查证据（源码位置）

| 结论 | 证据 |
|---|---|
| Koin 在 Application 级初始化 | `android/app/.../MusicApplication.kt` `onCreate → startKoin` |
| `MasterAgent` 懒初始化 | `shared-ui/.../chat/ChatKoinModule.kt:118` `.also { lifecycleScope.launch { initialize() } }` |
| `lifecycleScope` 为进程级 | `shared/.../runtime/MasterAgent.kt:370` `CoroutineScope(Dispatchers.Default + SupervisorJob())` |
| 仅在真销毁时取消 | `MasterAgent.kt:506 / :520`（`shutdown()` / `close()`）|
| Android 服务仅被绑定 | `android/app/.../MainActivity.kt:53` `bindService()`；`:60` `unbindService()` |
| 播放路径从不 start 服务 | 全 android 模块 `startForegroundService` 仅命中 `MusicNotificationReceiver.kt:22` |
| 服务非前台（正常路径）| `MusicPlayService.kt:517` `startForeground` 仅在 `onStartCommand` / 曲目 ready 监听器内 |
| iOS 后台模式 | `ios/HMP/HMP/Info.plist` `UIBackgroundModes=[audio]` |
| 电台快照不落盘 | `agent-radio.md` 第 338 行 |
| 会话边界契约 | `agent-radio.md` 第 237 行（C3：退后台不算关闭）|
