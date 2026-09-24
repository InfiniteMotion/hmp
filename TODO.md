# Hearable Music Player 待办事项

本文档**只保留未完成的可执行任务**，每条写成「现状 → 动作 → 完成判据」。已完成条目不入此档，历史见 [ROADMAP](ROADMAP.md)。

> **2026-09-24**：删去 120 条已完成条目（v5.10 全章、v6 已闭合项、方向 A A1–A10、方向 B F1–F9/F11–F14）。藏在已完成章节里的未闭合项按映射捞回：`P6.10`→R10、`P6.12`/`P6.15`→实为已完成（旧未勾是漂移）、`P7.46–49`→并入 R29、`P6.11`→I1、`P10.2`→I2、`T3`→R30。
> R 系列的证据出处：[docs/7_x/B agent-build/review-7.2.md](docs/7_x/B%20agent-build/review-7.2.md)。**行号是当天工作树的坐标，动手前先 grep 符号名。**

## 一、v7.2.1：v7.2.0 review 的 B 级遗留（R1–R17）

> 4 项 A 级 + B3 已在 `release/7.2.0` 修完（含回归用例），以下是当时判定「不该拖住发版」的余项。

### 数据与备份安全（优先级最高）

- [ ] **R1** 迁移测试只覆盖 1→2 / 5→6 / 6→7，本次新增的 7→8、8→9 无人守 → 补 `runMigrationsAndValidate(8)`、`(9)` 各一条，再加一条 `createDatabase(1)` 跑全链到 9。**判据**：`desktopTest` 出现这四段用例，且 A4（已去掉 destructive 兜底）之后仍全绿
- [ ] **R2** `AppDatabaseMigrationTest.migratedDatabase_canBeOpenedByRoom` 是假测试：Room KMP 首次查询才真正开库，用例只取了 DAO 引用 → 改成先跑一次真实 DAO 查询再断言。**判据**：故意写错一条 DDL 时该用例会红
- [ ] **R26** `PlaybackHistory` 无索引，而本次新增了十余条 `WHERE playedAt>=…` / `JOIN … ON musicId` 聚合 → 给 `playedAt`、`musicId` 建索引并重导 schema。**判据**：`EXPLAIN QUERY PLAN` 不再走全表扫；注意这需要开 **v10 迁移**，必须与 R1 的全链用例同批做（A4 之后缺迁移就是硬失败）
- [ ] **R23** 无界增长：`token_ledger` 有意永久保留但 `deleteAll()` 零调用方、看板 `sumByAgent(0L)` 每次全表扫；记忆证据表只增不减（`deleteById` 仅显式否决时调），且每次刷新 `evidenceDao.getAll()` 全表载入 + N+1 非事务写 → 加保留窗口（或手动清理入口）+ 批量事务写入。**判据**：刷新画像时的 SQL 语句数与库体积都不随历史线性增长

### 引擎正确性

- [ ] **R3** `ReActLoop` 无 `try/finally`：取消路径丢掉 `onSessionComplete`（alwaysAllow 不落盘）与终态 Presence；且熔断发生在工具执行**之后**、tool 轮次不回传对话历史（`ChatAgentGateway` 只映射 user/agent 文本）→ 下一轮模型看不到「已执行」，可能**重复执行写工具**。补 finally + 把 tool 轮次持久化。**判据**：一条「取消/熔断后重进对话，写工具不被执行两次」的用例
- [ ] **R4** `AgentScheduler` 的 `agents`/`states` 是普通 `MutableMap`，注册/注销无同步，而仲裁循环每秒在另一线程遍历 → 一次 CME 就让仲裁协程死亡且 `arbitrationStarted` 仍为 true，**全进程 pause/resume 永久失效且不可重启**。换并发容器 + `try/finally` 重启守卫。**判据**：并发注册/遍历的压力用例不抛 CME，循环异常后能自愈
- [ ] **R5** `stepBudget` 用户可改可持久化，但 Master 恒取 `EngineDefaults` 常量 → 设置项完全无效。接上 `resolvedFor("master").runtimeParams.stepBudget`，或先从 UI 撤下。**判据**：改成 3 与 15 后，实际循环步数跟着变（日志里的 `steps_budget` 可断言）
- [ ] **R6** `HelloSubAgent.stateFlow` 是构造期一次性求值的 `MutableStateFlow`，此后无人写回 → `capability_status` 与监控页的 Hello 永远停在构造瞬间、「活跃子 Agent 数」少 1 → 改为由 `runState` 派生（`map` + `stateIn(…, Eagerly, …)`）。**判据**：与 Enrich/Radio 共用同一条 Capability 状态契约测试
- [ ] **R11** 首轮对话在 `viewModelScope`(Main) 同步跑「画像就绪 + 全库快照 + 使用统计」→ 挪 `withContext(Dispatchers.Default)`，或首轮不阻塞（用旧画像异步刷新）。**判据**：首轮期间主线程无库级聚合（systrace / 日志埋点计时）
- [ ] **R12** 画像刷新的 `musicLabel` 聚合仍把全表 + 全量已删曲目拉进内存 groupBy（注释称「已改 SQL GROUP BY」的方法恰是漏改的那个）→ 换成已有的 `getTopLabelsSince` 一类 SQL 聚合。**判据**：该方法调用栈里不再有全表 `getAll`

### 安全与授权

- [ ] **R7** 提示注入：曲库标题/艺人/歌单名以裸字符串拼进 prompt，并经工具 `summary` 原样回灌对话上下文，全链路无定界与不可信标记；`EnrichResponseParser.normalizeFacts` 之外还有多条 AI 自由文本**无长度上限**直存 DB，成为后续所有 Agent 的载荷 → 统一加引用定界 + 入库截断。**判据**：构造一首标题里带 `[INST]`/伪工具名的歌曲，AI 输出与后续 prompt 都只把它当数据
- [ ] **R8** 写路径不过闸：电台直连 `playbackPort` 做 SKIP_ALL / REPLACE_QUEUE / ADD_TO_QUEUE，富化直写标签与 extra，二者都绕过 PolicyGuard 与 ConfirmGate，审计只记了 `logRadioStart` → 收进执行器，或至少补显式白名单 + 每次写都落审计。**判据**：`agent_monitor` 里能看到电台改队列的记录，且 Deny 时不执行
- [ ] **R9** 日志泄露曲库：富化/电台有若干 **WARN 级**日志打印模型响应原文 200–600 字符与歌名，而三端 release 的门槛正是 Warn → 降 DEBUG 或只记长度与计数。**判据**：release 构建跑一轮富化，日志里检索不到任何曲名
- [ ] **R10** iOS 密钥仍是 XOR 伪加密 + 明文密钥文件落在 `Documents/.keys`（与密文同目录、一起进 iCloud 备份），且用的是非 CSPRNG 的 `kotlin.random.Random` → 换 Keychain（`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`），Desktop 的 `KEYSTORE_PASSWORD` 硬编码另计。同时把 README「API 密钥加密存储」的口径改准。**判据**：`grep -r SecItem shared/src/iosMain` 命中且 key 不落沙箱文件；旧库能平滑迁移
- [ ] **R16** 撤销链路从未实装：审计只存 `argsHash`（不存参数与旧值）→ 数据层面无法还原「改了哪个列表、原内容是什么」；`AgentNoticeBar.onUndo` 全仓未传入、`AppRoot` 唯一分支 `showUndo=false`（挂载但永不显示的死组件）；`DialogConfirmGateAdapter` 的 STRONG_CONFIRM 二次确认是死代码。**另加一条本次修复留下的账**：修复前已写进白名单的不可逆工具项，策略上已被忽略，但设置页仍显示为「总是允许」→ 接出撤销列表时一并清掉。**判据**：删歌单后可从审计页一步还原；设置页显示的可撤销项与 PolicyGuard 实际生效项一致
- [ ] **R17** 产品决策：`playlist_delete` 这类不可逆工具，当前实现 = **永远逐次确认**（A2 之后白名单不再放行）。要不要给「本次会话内免确认」这类介于一次性与永久性之间的折中档，需要你先定，再谈实现

### 界面与文案

- [ ] **R13** i18n 三件事：① 泰语 454/789 条与默认英文逐字节相同 → 全量重译；② `shared-ui/src/androidMain/res/values*` 是 composeResources 的旧平行副本，未并入本轮新键且 12 语言缺 `karaoke_lyrics` → 收拢或删除；③ **f14 承诺的键集合校验脚本在仓库里根本不存在**，唯一的 `LangTest` 只测 LLM 词表不读 XML → 把「键集合一致 / 占位符逐条 / 未译率阈值」做成单测并挂 CI。**判据**：故意删一个键或写错 `%1$s`，CI 就红
- [ ] **R14** 中文直供 UI 收尾：电台 `actionText`/`phase`、Hello 的兜底模板与 60 条 `HelloGreetingProfiles` 保底文案、`HelloCardNarrative` 报告卡标题，都是 shared 侧拼好中文字符串经端口上屏（非中文用户在「建库」阶段整程见中文；LLM 未配置时兜底串必然命中）→ 端口传枚举或 `UiText.Res`，文案进 strings。**判据**：`grep` agent 子树无非注释中文字面量，且切到 en/th 后电台状态卡跟着翻
- [ ] **R15** `AgentConfigScreen` 单个 composable 装了六分区 + 30 个 `remember` + 校验 + 持久化 + 直接改写 `masterAgent` 活实例，且 `ConfigFormLeftColumn` 把五张卡又写了一遍（双写）→ 第一刀抽 `AgentConfigViewModel`（save/reset），第二刀把六张卡各抽成小节 composable 让单栏与双栏复用同一批。**判据**：文件从 1658 行降到 <600，双栏不再有重复卡片
- [ ] **R24** 断点两套机制并存（`LocalWindowSizeInfo` 五页 vs `RadioConsole`/`HelloCardCoverflow` 自算 `BoxWithConstraints`），且 `AgentNoticeBar` 两档宽度同值 520.dp（分支退化）→ 统一到 `LocalWindowSizeInfo`。**判据**：agent 相关页只有一处断点来源
- [ ] **R25** `plurals` 未实现而有 47 个 `%1$d`+名词模板（`songs_count`、`timer_minutes`…），ru/ar 等语法必然错 → 引入 plurals 并迁移这批键

## 二、方向 C：播放功能增强补齐（C1–C9，未排期）

> 原按「7.1 批一 / 7.2 批二」排期，两批都没随版本落地，现统一未排期。纯引擎层工作，与 UI 线正交可并行。

- [ ] **C1** 播放速度：`PlaybackController` 扩 `speed` + 三端实现（Media3 `setPlaybackSpeed` / FFmpeg `atempo` / AVPlayer `rate`）+ shared-ui 控件。**判据**：三端 0.5×–2× 可播且时长显示随之校正
- [ ] **C2** 格式白名单统一到 commonMain 常量；iOS 补 opus（现 7 种 vs Desktop 9 种）。**判据**：三端读同一常量，无平台私有列表
- [ ] **C3** Gapless：Android 走 Media3 原生、Desktop 预加载下一曲、iOS 靠 AVPlayer 衔接。**判据**：连续专辑切歌无间隙
- [ ] **C4** Desktop 音效系统：FFmpeg avfilter 做 EQ / 低音 / 环绕，接共享 `AudioEffectViewModel`（Android/iOS 已有，Desktop 是空白）。**判据**：三端同一套 UI 与持久化参数
- [ ] **C5** ReplayGain：扫描器读标签 + 播放端应用增益（三端）。**判据**：开启后响度专辑间趋于一致
- [ ] **C6** 交叉淡入淡出（切歌 fade，三端引擎）
- [ ] **C7** Desktop 放行 DSD(DSF/DFF) / APE / WV（FFmpeg 原生可解）。**判据**：白名单 + 标签解析都不报错
- [ ] **C8** 待评估：bit-perfect 输出（Windows WASAPI 独占等，发烧友向）
- [ ] **C9** 待评估：桌面小组件（Android Glance / iOS WidgetKit）与手势操作 —— README 既定承诺

## 三、iOS 原生层残留

- [ ] **I1** `MusicTagParser.ios.kt` 已用 Bridge 委托 Swift `MusicMetadataParser`，但仍缺**技术元数据**（bitRate / sampleRate）与**歌词提取**（ID3 USLT 或同名 LRC）。**判据**：iOS 详情页与歌词页字段与 Android 对齐
- [ ] **I2** `MusicPlayService.swift` 已被 PlayerEngine 替代 → 删除并回归锁屏控制 / RemoteCommand / Live Activity。**判据**：删后 iOS 构建 + 锁屏实操通过

## 四、清理线（R20–R22、R27–R30，可与方向 C 并行）

- [ ] **R20** 死抽象：`ToolRegistryView` 全类零消费（注入三个 SubAgent 后基类只存不读）、`Capability.start/stop` 生产零调用、`continueRadio` 自陈无调用点、`ALL_BATCH_B` 无引用 → 要么接上（电台/富化将来真要用工具时接进 per-agent 白名单），要么删掉这层
- [ ] **R21** 注释与口径漂移（**taskbook 的打勾不可信，动手前以代码为准**）：`ToolNames` 注释「29 个」、`ToolCatalog`「34 实例=26+8DJ」（dj_* 全仓 0 命中）、`SharedModules` 称 iOS 未提供 `AgentKeepAlivePort`（其实 `IosModules` 已提供）、`f12` 验收表四个 DAO 聚合零调用方却打 ✅、`f8` §7 的 W-T5/W-T6 未实装仍记完成、`f6` M6-T2 `PresenceEvent.SkipDetected` 与 `CloudQuotaExhausted` 零 emit、`f11` L1「三端启动即初始化」实为仅 Android → 逐条改文档或补实现，并把「工具数 / DAO 消费方」加进断言防回归
- [ ] **R22** 大文件拆分：`MasterAgent.kt`（本次修完 A1 后 1760 行，六职责混装，`init` 里还 `runBlocking` 读 DataStore）、`EnrichSubAgent.runLoop`（167 行 / 31 分支）、`RadioSubAgent.startRadio`（113 行 / 4 处队列写入，且 494 行处有 R-Phase 3 重做计划）、`HelloCardCoverflow`（283 行 / 45 分支）→ 优先拆 Master 的三套 SubAgent 生命周期与内建意图路由
- [ ] **R27** `getMusicIdListByType` 新默认 `LIMIT 100` 且无 `ORDER BY` → 未显式传 limit 的调用点被静默截断、取哪 100 条不确定。给默认值改 `MAX_VALUE` 或调用点显式传，并补确定序
- [ ] **R28** 测试盲区：生命周期并发重入、`AgentScheduler` 仲裁循环、`ToolCallExecutor` 之外的 CE 吞没点（agent 域 `catch (Exception)`/`runCatching` 共 184 处，仅 4 处复抛 CE）、Hello 有效行为（现 4 例全是「零依赖构造不崩」）、`pauseResume_transitionsRunState` 把错误语义锁进了断言。**本次新增的两条 A1 用例只断言「不超时」，没断言旧实例确实被收摊；enrich/hello 同形改动也无等价用例** → 补这三块
- [ ] **R29** 发版前必做的实机核验：① **A4 之后降级库的表现完全没测过** —— 三端都去掉了 destructive 兜底且没注册 `RoomDatabase.Callback`，拿一个 `user_version=10` 的库在三端各跑一次，按结果决定要不要加「库版本较新，请升回新版本或恢复备份」的提示；② `iosMain` 本机（Windows）无法编译，本次 A3/A4 的 iOS 分支只做了源码级核对；③ F11 后台存活真机核验、iOS 锁屏 / Live Activity 交互核验（原 P7.46–P7.49）、三端首启引导实操
- [ ] **R30** 工程一致性：`checkVersion` 补 `notCompatibleWithConfigurationCache`（现配置缓存下直接构建失败，发版预检被卡）；`android/app/build.gradle.kts` 的 `51000`/`"5.10.0"` 兜底改为读不到就失败；`SettingsRepositoryImpl` 三平台各 ~500 行高度重复（原 T3）→ 通用逻辑提取到 commonMain 基类，**本次 A3 加的 `enabled` 键正是第四处需要三端手工同步的例子**

## 五、CI / 发版通道（2026-09-24 排障追加）

- [ ] **R31** CI 可观测性：`Test` 任务仍无挂钟超时（validate 只能靠 job 级 `timeout-minutes: 20` 掐断，卡住时看不出卡在哪个用例）→ 诊断期给 `tasks.withType<Test>` 加 `timeout = 10.minutes` + `testLogging { events(STARTED) }`，定位后撤掉 STARTED 免污染日志。validate 静默挂死**尚未结案**：已删 `org.gradle.configureondemand`（与 parallel 的配置期锁竞态、AGP 不兼容、实测配置耗时无差别），需连绿两次才算收口
- [ ] **R32** `injectFFmpeg` 规则实机收口：已放宽为「目录名 `bin` + 路径含 `runtime`」，并在失败时打印真实目录树。Windows 本机实测注入成功（落到 `app/HMP/runtime/bin/ffmpeg.exe`）；**顺带发现 jlink runtime 里没有 `java.exe`**（只有 69 个 dll），所以"用有没有 java 可执行文件判定 JRE bin"这个看似更稳的判据不成立。macOS 仍未实机验证 —— 若 CMP 1.11 把 runtime 目录整个改名，下次 CI 的目录树输出即答案；Windows job 在加引号前从未真正跑到该任务
- [ ] **R33** 发布口径核对：`-Phmp.release-build=true` 是 `8ba051cc`(2026-09-01) 一次加进三个 job 的，而 Windows 的 `run:` 默认 pwsh 会在点号处断词 → **推断自那以后每个 Windows job 都该失败**，需在 Actions 历史确认最后一次成功的 MSI 是哪一版；macOS 侧 `injectFFmpeg` 的断言是 `d977e41` 新加的，它一响就说明此前 DMG 一直没带上 FFmpeg（长期静默失败）。结论：v7.2.0 的 Release Notes 里"桌面端三平台可安装"必须等三平台产物真出来后按实物写

## 六、挂起（不排期，可整体延后）

- ⏸ **F10** 语音会话（`RealtimeVoiceTransport`）—— 方向 B 里唯一真正新增的传输层，需真实端点验证，与主线解耦、未开工；端点不可用即整体延期，v1 完整性不依赖语音

***

© 2026 Hearable Music Player | Developed by WLYB
