# Hearable Music Player 待办事项

本文档**只保留未完成的可执行任务**，每条写成「现状 → 动作 → 完成判据」。已完成条目不入此档，历史见 [ROADMAP](ROADMAP.md)。例外：§五 的 R32/R33 以 `[x]` 结案记录留在原位，当发版通道的处置台账（结论带证据与 commit，下次改动直接用，不必重新考古）。

> **2026-09-24**：删去 120 条已完成条目（v5.10 全章、v6 已闭合项、方向 A A1–A10、方向 B F1–F9/F11–F14）。藏在已完成章节里的未闭合项按映射捞回：`P6.10`→R10、`P6.12`/`P6.15`→实为已完成（旧未勾是漂移）、`P7.46–49`→并入 R29、`P6.11`→I1、`P10.2`→I2、`T3`→R30。
> R 系列的证据出处：[docs/archive/7_x/7_2/review-7.2.md](docs/archive/7_x/7_2/review-7.2.md)。**行号是当天工作树的坐标，动手前先 grep 符号名。**

## 一、v7.3.0：v7.2.0 review 的遗留（R1–R17、R23–R26）

> 4 项 A 级 + B3 已在 `release/7.2.0` 修完（含回归用例），以下是当时判定「不该拖住发版」的余项。
> **2026-09-28 改列 v7.2.2**：这批原本标着 v7.2.1，但 v7.2.1 实际只发了发版通道与桌面打包修复（PR #41/#42，已发布 2026-09-25），本节一条没动；版本号不复用，故整体顺延。**编号不连续**：本节是 R1–R17 + R23–R26，R20–R22、R27–R30 归 §四 清理线。
> **2026-09-28 再顺延 v7.3.0**：v7.2.2 已发布（2026-09-28，只发 `release.toml` 发版链路本身），本节仍一条未动。与 §六（架构实现审查 R39–R56）同批做，其中 **R1/R2/R26 必须与 R39 同批**——三者都要开 v10 迁移，分开做就是两次 schema 变更。
> **2026-09-30 归口**：v7.3.0 的施工分解真源已切到 [docs/7_3/domain-baseline.md](docs/7_3/domain-baseline.md)（九域 + 横切线，109 条发现），本节与 §六 的 R 条目改作**证据台账**。开工前读 §六 开头的「台账口径」与文末对照表/决议。

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

- [ ] **I1**（2026-09-29 按实物缩窄）技术元数据与内嵌歌词**已实装**：`MusicMetadataParserBridge.swift:75-130` 用 `estimatedDataRate` 供 bitRate、`CMAudioFormatDescriptionGetStreamBasicDescription` 供 sampleRate，歌词走 `commonMetadata` + 全格式二次扫描（`©lyr` / `USLT` / `UNSYNCEDLYRICS`）+ FLAC Vorbis comment 三次扫描。**仍缺**：① **同名 `.lrc` 文件查找**（现只有 `domain/lyrics/LrcParser.kt` 解析内容，没有去曲库目录找侧车文件）；② 年份 / 流派 / 音轨号 / 封面 四项在桥里传 `nil`。**判据**：iOS 详情页与歌词页字段与 Android 对齐，侧车 LRC 能加载
- [x] ~~**I2** 删除 `MusicPlayService.swift` 死文件~~ **已结案（2026-09-29 核，待办本身过期）**：`find ios -name "MusicPlayService.swift"` 零命中，文件早已被 `PlayerEngine.swift` + `MusicPlayerController.swift` 取代并删除。锁屏 / RemoteCommand / Live Activity 现由 `MediaSession/` 五文件 + `HMPNowPlaying/` 扩展承担。留此结案记录以免下次又去"删"一遍

## 四、清理线（R20–R22、R27–R30，可与方向 C 并行）

- [ ] **R20** 死抽象：`ToolRegistryView` 全类零消费（注入三个 SubAgent 后基类只存不读）、`Capability.start/stop` 生产零调用、`continueRadio` 自陈无调用点、`ALL_BATCH_B` 无引用 → 要么接上（电台/富化将来真要用工具时接进 per-agent 白名单），要么删掉这层
- [ ] **R21** 注释与口径漂移（**taskbook 的打勾不可信，动手前以代码为准**）：`ToolNames` 注释「29 个」、`ToolCatalog`「34 实例=26+8DJ」（dj_* 全仓 0 命中）、`SharedModules` 称 iOS 未提供 `AgentKeepAlivePort`（其实 `IosModules` 已提供）、`f12` 验收表四个 DAO 聚合零调用方却打 ✅、`f8` §7 的 W-T5/W-T6 未实装仍记完成、`f6` M6-T2 `PresenceEvent.SkipDetected` 与 `CloudQuotaExhausted` 零 emit、`f11` L1「三端启动即初始化」实为仅 Android → 逐条改文档或补实现，并把「工具数 / DAO 消费方」加进断言防回归
- [ ] **R22** 大文件拆分：`MasterAgent.kt`（本次修完 A1 后 1760 行，六职责混装，`init` 里还 `runBlocking` 读 DataStore）、`EnrichSubAgent.runLoop`（167 行 / 31 分支）、`RadioSubAgent.startRadio`（113 行 / 4 处队列写入，且 494 行处有 R-Phase 3 重做计划）、`HelloCardCoverflow`（283 行 / 45 分支）→ 优先拆 Master 的三套 SubAgent 生命周期与内建意图路由
- [ ] **R27** `getMusicIdListByType` 新默认 `LIMIT 100` 且无 `ORDER BY` → 未显式传 limit 的调用点被静默截断、取哪 100 条不确定。给默认值改 `MAX_VALUE` 或调用点显式传，并补确定序
- [ ] **R28** 测试盲区：生命周期并发重入、`AgentScheduler` 仲裁循环、`ToolCallExecutor` 之外的 CE 吞没点（agent 域 `catch (Exception)`/`runCatching` 共 184 处，仅 4 处复抛 CE）、Hello 有效行为（现 4 例全是「零依赖构造不崩」）、`pauseResume_transitionsRunState` 把错误语义锁进了断言。**本次新增的两条 A1 用例只断言「不超时」，没断言旧实例确实被收摊；enrich/hello 同形改动也无等价用例** → 补这三块
- [ ] **R29** 发版前必做的实机核验：① **A4 之后降级库的表现完全没测过** —— 三端都去掉了 destructive 兜底且没注册 `RoomDatabase.Callback`，拿一个 `user_version=10` 的库在三端各跑一次，按结果决定要不要加「库版本较新，请升回新版本或恢复备份」的提示；② `iosMain` 本机（Windows）无法编译，本次 A3/A4 的 iOS 分支只做了源码级核对；③ F11 后台存活真机核验、iOS 锁屏 / Live Activity 交互核验（原 P7.46–P7.49）、三端首启引导实操
- [ ] **R30** 工程一致性：`android/app/build.gradle.kts` 的 `51000`/`"5.10.0"` 兜底改为读不到就失败（现在会静默产出一个版本号错误的包）；`SettingsRepositoryImpl` 三平台各 ~500 行高度重复（原 T3）→ 通用逻辑提取到 commonMain 基类，**A3 加的 `enabled` 键正是第四处需要三端手工同步的例子**。~~`checkVersion` 缺 `notCompatibleWithConfigurationCache`~~ 已修（本轮，同时把它扩成 versionName↔versionCode 自洽 + 递增 + 跨文件一致性三件事）

## 五、CI / 发版通道（2026-09-24 排障追加；2026-09-28 结案 R32/R33 与 R34/R35、新增 R35b/R36）

- [ ] **R31** 单元测试已移出 CI（2026-09-24）：`testAll` 在 runner 上会**静默挂死**（最后一行停在某个 `> Task`，之后无输出；plain console 的标题行只代表任务开始，所以看到的不是卡住的那个任务）。头号嫌疑是 `:android:core-player` 的三个 Robolectric 用例在执行期从 Maven Central 现拉 `android-all` 大 jar（默认无超时、不输出，`setup-gradle` 只缓存 Gradle home 不缓存 `~/.m2`）；本机 `~/.m2` 是热的，所以本地永远复现不出来。**未结案**，代价是 master 的测试回归从此无人值守 → 本机 `preflight` 成为唯一守门人（已写进 `docs/spec/hmp-release.md` §5/§6）。恢复 CI 跑测的路径，二选一：① 缓存 `~/.m2/repository/org/robolectric` + 给 `Test` 任务加挂钟超时；② 上一版加过的「输出静默 240s 就 dump `jstack` + `ss -tnp`」看门狗（代码在 `af51437`，取回来用一次就能定案）。另外 `configure-on-demand` 已从 `gradle.properties` 删除（与 parallel 的配置期锁竞态；实测配置耗时无差别），validate 保留 `timeout-minutes: 10`
- [x] ~~**R32** `injectFFmpeg` 根因已定、待 CI 复验~~ **已结案（随 v7.2.1 发布，2026-09-28 核）**：根因是 CI 目录树证明 **macOS 的 app image 里没有 `bin` 目录**（jlink 只出 `Contents/Home/{lib,legal,conf}`，启动器是 `Contents/MacOS/HMP`），而运行时按 `java.home/bin/ffmpeg` 找 —— 所以 DMG 从来没带上 FFmpeg，旧规则 `endsWith("Home/bin")` 从一开始就不可能命中。规则已改为「归一化到 java.home」：Windows 走 `bin/server`（jvm.dll 在 bin 下）、macOS/Linux 走 `lib/server`，找到后**缺 bin 就补建**（`1b7cca4`）。**结案依据**：v7.2.1 成功 run（`36170367906`）带三端 FFmpeg 进包断言全绿，deb 内清单核查在 `e8525f6` 修掉 SIGPIPE+pipefail 误判后通过，DMG/MSI 产物已上 Release。**残留尾巴**：macOS 真机启动播放仍未验（本机是 Windows，验不了），所以「FFmpeg 在包里」≠「macOS 上真能解码」
- [x] ~~**R33** 发布口径核对~~ **已结案（2026-09-28 按 Actions/Release 实况核对）**：`-Phmp.release-build=true` 是 `8ba051cc`(2026-09-01) 一次加进三个 job 的，而 Windows 的 `run:` 默认 pwsh 在点号处断词 → 推论成立，**最后一次成功的 MSI 是 v7.1.0**（2026-08-26，产物表可查），此后 v7.2.0 的 Release run（`35956812895`）失败、无 tag 无产物，直到 v7.2.1 把该步骤改 `shell: bash` 才重新出 MSI。macOS 侧 `injectFFmpeg` 断言（`d977e41`）一响即证实此前 DMG 一直没带上 FFmpeg（长期静默失败）。**已按实物落地的口径**：v7.2.1 的 Release Notes 与站点条目按五件产物写；v7.2.0 在 ROADMAP 标题与 `site/changelog.html` 标为**未发布**（不再装作发过）。产物文件名自 v7.2.1 起带平台/架构后缀（`-macos-arm64.dmg` / `-windows-x86_64.msi` / `-linux-x86_64.deb`），旧版是 `-macos.dmg` 那类无后缀形态 —— 改链接时要按新命名核对
- [x] ~~**R34** 版本声明由「校验」升级为「生成」~~ **已结案（2026-09-28 落地，未随已发布版本）**：真源换成仓库根 `release.toml`（只描述当前版本），`scripts/sync-release.py` 负责写出 `gradle.properties` / 站点 `config.js` + JSON-LD / iOS `project.yml`+`Info.plist`+`pbxproj` / `Anchor.kt`（`-aN` 后缀原样保留）/ ROADMAP 与站点时间线条目；Gradle 侧新增 `syncVersion`，`checkReleaseConsistency` 改为委托 `sync --check`。iOS 未迁 `.xcconfig`（pbxproj 直接由脚本改文本），**首次切换需你在 macOS 上 xcodegen + 构建验一次**
- [x] ~~**R35** Release Notes 正文抽取没有终止边界~~ **已结案（同上，根治而非加防护）**：正文改由 `release.toml` 渲染，ROADMAP 从此不被程序解析，awk 那段连边界带临时防护一起删掉；`release.yml` 与 dry run 都把最终正文打进日志（v7.2.1 那次是发布后人工删到 58 行的）。判据 `MAX_ITEMS=80` 移进脚本
- [ ] **R36** 门禁仍管不到 Markdown 叙述：`release.toml` → `syncVersion` 已接管**可机器判定**的派生点（`gradle.properties` / 站点 `config.js` + JSON-LD / iOS 三处 / `Anchor.kt` / 归档条目存在性），但 **README / CLAUDE 里「最新发布版本」这类自然语言没人核** —— 本轮就抓到三处漂移（DEVELOP 版本片段停在 7.2.0、CLAUDE 概述停在 7.1.0、README 写着"v7.2.1 发布中"而它早已发布）。**动作**：给 `sync-release.py` 加一个"版本号出现处"检查（只在明确标记的段落里断言等值，别去解析整篇散文），或约定这两处由 release-prep Skill 的第 ⑧ 步交给人改。**判据**：故意把 README 的版本改错，`sync --check` 或 pr-check 能红
- [ ] **R37** 发布**之后**的公开面核对还没自动化：本期做到"发布前预览 + CI 把最终正文打进日志"，但合并之后没人比对"线上 Notes 是否等于 `sync-release.py notes` 的输出""三端产物是否真的齐""站点是否已随本次发布部署"。**动作**：加 `verify-published` 子命令（拉 `gh release view` 的 body 与 assets 与 `release.toml` 比对），挂进 `release.yml` 发布后一步或 deploy-site 之后；也可做成 `release-ship` Skill，但那是"给 agent 的工序"，判据仍应落在脚本里。**判据**：手动把线上 Notes 改一个字，检查会红
- [ ] **R38** iOS 三处首次由脚本改写的验证缺口：`scripts/sync-release.py` 现在直接文本替换 `project.yml` / `Info.plist` / `project.pbxproj`（本机 Windows 只能做源码级核对）。**动作**：下次 bump 时在 macOS 上跑一次 `xcodegen generate` + 模拟器构建，确认 pbxproj 的 `MARKETING_VERSION` 改动没有被工程重载写回；通过后再决定是否按原 R34 的想法收敛成一份 `.xcconfig`（那能同时消掉 `Info.plist` 与 `pbxproj` 两处重复）。**判据**：`git diff` 里这三处与 `release.toml` 一致，且 Xcode 构建产物 `defaults read .../Info CFBundleShortVersionString` 等于版本号

## 六、v7.3.0：架构实现审查的遗留（R39–R56，2026-09-28）

> 出处：[review-7.3-architecture.md](docs/7_3/review-7.3-architecture.md)（5 路分片并行审 + 主审对全部 S1 逐条重跑证据；含误报否决记录与「刻意不做」清单）。

> **⚖️ 台账口径（2026-09-30 归口，动本节之前先读这段）**
> - **v7.3.0 的工作分解真源是** [docs/7_3/domain-baseline.md](docs/7_3/domain-baseline.md) → `docs/7_3/domain/D{1..9}.md` + `domain/X.md` + [v10-migration.md](docs/7_3/v10-migration.md)；可执行条目落 `docs/7_3/taskbook/`。
> - **施工顺序以 `domain-baseline.md` §三 / §八 的批次为准**：批一数据面（D2/D3/D5/D7，schema 需求统一收口 v10）→ 批二运行时（D1/D4/D6）→ 批三平台界面（D8/D9），横切线 X 单列。本节原来的"先闸门 → 再 S1 → 后结构"是**上一轮审查的排序，已不作施工顺序**（原文见下方删除线，留档备查）。
> - **本节 R 条目保留为 2026-09-24 / 09-28 两轮审查的证据台账**：`file:line` 与判据仍是取证记录，但**不作为施工分解来源**（新基线明确声明不继承其编号与分类）。开工前按下表找到落点；**旧条目不得因"新基线没提"而被自动视为已处置**。
> - ~~**顺序是硬约束**：先闸门批（R44→R43→R41 闸门段→R47 闸门段→R56），再 S1 批（R39/R40/R42/R45/R46），结构批（R48–R53）放最后。闸门未绿之前动结构，等于在没有安全网的地方重构。~~（**已被 baseline §三 批次取代**，原文留档）

### 闸门（本节的头，其余各项的前置）

- [ ] **R44** 合入前闸门**不编译、不测试**：`pr-check.yml` 四个 job 只跑 `checkVersion checkReleaseConsistency`（`:65`），`release.yml` validate 同样，三个 build job 全是打包，**CI 里没有 iOS job** → pr-check 加 `HMP_BUILD_TARGET=desktop ./gradlew compileAll` + `:android:app:assembleDebug`；再加一个 macOS 编译 job。**关键省力点**：`shared/build.gradle.kts:86-91` 的 KSP iOS target 已配齐，`:shared:compileKotlinIosArm64` 是**零新代码**的 iOS 闸门；`commonTest` 依赖（`:77-83`）全是完整 KMP 构件，故 `:shared:iosSimulatorArm64Test` 能直接跑现有 commonTest。**判据**：故意提交一个语法错的 PR 会红。**与 R31 的分工**：R31 管"测试在 runner 上挂死"，本条管"编译这道闸门根本没建"；本条先行，且必须按 R31 的教训给 job 加挂钟超时
- [ ] **R43** DI 装配无图校验：`checkModules|verify()` 全仓 **0 命中**，12 个 module 文件、131 `single` + 3 `viewModel` 对 495 处 `inject/get`；三端启动校验强度不同（Android 只 eager 解 MasterAgent、Desktop 只解控制器、iOS 纯懒）→"进页面才崩"在 iOS 成立 → `:shared:desktopTest` 接 `koin-test` 的 `checkModules`。**判据**：删掉任意一条 `single`，测试红
- [ ] **R41** 在用的 NavKey 未注册 nav3 serializer：`Routes.kt` 有 **30** 个 NavKey，`HmpNavBackStack.kt` 的 `SerializersModule` 只有 **28** 条 `subclass(`，文件内 `grep Agent` 零命中 → `Routes.AI.AgentConfig`（跳转 `AIScreen.kt:318,358`、entry `NavigationGraph.kt:173`）与 `Routes.Settings.AgentMonitor`（entry `:113`）都漏注册，而该文件注释 `:20-22` 自陈"漏注册无编译期报错，仅在该 key 参与保存/恢复时运行时报错"。现有 `RoutesTest.kt:215-246` 手写 22 条 `is NavKey` 断言是**恒真闸门** → 补注册 + 把测试改成"遍历 NavKey 集合 ⊖ 白名单 ⇒ 必须有 serializer 且必须有 entry"。**判据**：加一条路由而不写 `entry` / 不注册，测试红
- [ ] **R47** 三端镜像内容漂移零防护：32/29 两种正则口径下的 expect 声明都**文件级 1:1:1 配对无孤儿**（23 个 expect 宿主文件，android/desktop/ios 各 23 个 actual 宿主文件），问题是**整文件镜像**——`PlaylistRepositoryImpl.{desktop,ios}` 逐行 diff≈0、`SettingsRepositoryImpl.{desktop,ios}` 各 ~500 行、`getRoomDatabase`/`createJson` 三端各写一份迁移列表（漏一端就是单端升级崩溃）；平台源集 `.kt` 共 **107** 个（shared 20/20/23、shared-ui 15/13/16），其中 `*Impl*.kt` **12** 个只有 **2** 个有同名测试（都在 desktopTest），`shared/src` 无 androidHostTest / 无 iosTest → androidMain、iosMain 覆盖 **0**。已发生样本：`17d9d61`（A3 `enabled` 不落盘，修复需 3×4 行同步）→ `commonTest` 加源集扫描断言（每个 expect 名在三端各有含 `actual` 的同名文件）+ 把"三端刻意差异"收成 commonMain 白名单常量。**判据**：某端删掉一个 actual 文件即红；白名单外的"三端逐字相同"被识别为可下沉候选。**真正的下沉**（Settings/Playlist/Backup 抽 base）单列，见审查报告「刻意不做」
- [ ] **R56** 依赖矩阵无人守：Coil2 与 Coil3 并存（`shared-ui:70-73`）、coroutines 主库 1.10.1 vs 测试 1.9.0、`koin-compose` 别名**只存在于注释里**（活雷）、alpha/beta 进生产、`android/app:7` 与 `core-player:5` 绕过 alias 硬编码版本、KSP 在两模块 apply 却零 processor → 加一条 catalog 使用率与一致性断言。**判据**：定义未用 / 使用未定义 / 同库多版本即红

### S1 · 数据与安全

- [ ] **R39** 备份与恢复整条路径**无事务**（用户可触发的数据丢失）：`withTransaction|inTransaction` 全仓 **0 命中**；`PlaylistRepositoryImpl.android.kt:142-143` 与 `MusicRepositoryImpl.android.kt:150-152` 都是 `deleteAll()` 后逐条 insert，desktop/ios 同型；`ImportUserDataBackupUseCase.kt:23-28` 串行 4 个仓库 restore 后 `catch→Result.failure`，无回滚、无导入前安全副本；路径已接线可达（`BackupViewModel.kt:74-86` + iOS），现有测试用 Fake 只测 happy roundtrip → 批量写唯一入口收敛到 `AppDatabase.withTransaction`，导入前落安全副本，`catch` 复抛 `CancellationException`。**判据**：故意让第 3 步失败，第 1、2 步必须回滚。**必须与 R1/R2/R26 同批**（都要开 v10 迁移）
- [ ] **R40** iOS 的扫描目录设置整体是空壳：`SettingsRepositoryImpl.ios.kt:189-190` = `flowOf(ScanDirectoryConfig())` + 空函数体的 save；`DeviceMusicScanner.ios.kt:28-42` 写死扫 Documents；`MusicRepositoryImpl.ios.kt:174-176` 构造时不接 `settingsRepository`（Android/Desktop 都真消费）→ 实装，或先在 iOS 撤下入口并共享侧按平台隐藏。**判据**：iOS 改扫描目录→重启→扫描结果随之变化，或该入口在 iOS 不可见
- [ ] **R42** 歌词设置双存储、写入端无消费端：播放页读的 `LyricsSettingsUseCase.kt:85-105` 全标 `@Deprecated`（legacy 扁平键），设置页写的是新 per-component JSON，而全仓唯一新模型消费者是 Android 悬浮歌词（`FloatingLyricsOverlay.kt:72`）→ 用户在设置页改字号/行距，播放页静默不生效 → 读写收敛同一模型，legacy 键一次性迁移后删。**判据**：一条 VM 级用例断言"设置页改值后播放页取到同一值"。**注意路径**：真身在 `domain/setting/usecase/`，不在 `domain/lyrics/`
- [ ] **R10 追加两条**（iOS 密钥，既有项扩账）：① `ios/HMP/project.yml:43` 开着 `UIFileSharingEnabled: true`，而密钥就在 `Documents/.keys`（`SecureStorageHelper.ios.kt:17-25`）→ 关文件共享或把密钥彻底移出 Documents；② 算法实际是 `xor`（`:57-70`）却把密钥文件命名为 `.aes_key`，属误导性命名，换 Keychain 时一并纠正。另记：三端密文互不可解，跨端恢复被 `runCatching` 静默吞（`SettingsRepositoryImpl.desktop.kt:239`）

### S1 · 产物与发版链

- [ ] **R45** 版本号读不到时静默回落到错误值：`desktop/app/build.gradle.kts` 的 `packageVersion` 与 `msiPackageVersion` 都写 `?: "1.0.0"`，`dmgPackageVersion = "1"` 硬编码不随 versionCode，而桌面 job 不跑 `checkVersion` → 文件名像 v7.2.2、包内版本是 1.0.0；`build.gradle.kts:41-50` 的 `copyToReleases` 找不到产物只 `println` 不失败（CI 侧 `sync-release.py collect` 会失败，本机不会）→ 读不到就硬失败。**判据**：删掉 `hmp.versionName` 后构建红；`releases/` 缺件时任务失败。这是 **R30**（android 的 `51000`/`"5.10.0"` 兜底）的同类扩用
- [ ] **R46** 生成的 podspec 入库且被 Windows 写成反斜杠：`shared-ios/shared_ios.podspec` 是被 track 的生成物（`shared/build.gradle.kts:29` 每次 cocoapods 任务重写它），当前工作树 `:45` 为 `spec.resources = ['build\compose\cocoapods\compose-resources']`（本地未提交改动），`spec.version` 恒 `1.0.0`，`.gitattributes` 无 `*.podspec` 规则 → 出库加 `.gitignore` / 入库补 `text eol=lf` / 改由 `pod lib lint` 现场生成，三选一并纠正反斜杠。**判据**：macOS `pod install` + 构建能取到 compose-resources（与 **R38** 同批实机验证）。**2026-09-29 扩账（订正 DEVELOP 时新发现的同类）**：仓库里其实有**两份**被 track 的生成 podspec —— 除 `shared-ios/shared_ios.podspec`（`baseName = "sharedIos"`，Podfile 真正集成的那份），还有 `shared/shared.podspec`（`:shared` 自己的 cocoapods 块生成，`baseName = "shared"`、`homepage` 是占位的 `github.com/hmp/shared`、`source` 为空）。后者**没有任何消费方**，属第三个"生成物入库"实例，处置方案与上面三选一同样适用；顺带 `spec.version` 两处都恒 `1.0.0`

### S2 · 结构与真源

- [ ] **R48** UI 状态归属分裂：主流走 VM（20 VM / 29 屏），但 **agent 区整块裸奔**——`AgentConfigScreen.kt`(1658 行) 0 VM、17 处 `remember{mutableState}`，保存/校验/提示词覆盖判定全在 composable 的 `save` lambda 里并直接改写 `masterAgent` 活实例（`:161,167,173-243,256-306`）；`AppRoot.kt` 内 8 `koinInject` / 7 `LaunchedEffect` / 4 `scope` / 16 `collectAsState` → 本条是 **R15** 的具体化并扩到整个 agent 区。**判据**：agent 区每屏有 VM，composable 内 0 挂起调用
- [ ] **R49** 设计 token 是"自愿制"：硬编码 hex 色 **53 处/13 文件**（扣 token 文件 37 处/12 文件）、`fontSize` 字面量 **43 处/9 文件**、`RoundedCornerShape(N.dp)` **148**、动效毫秒 **56**、`.dp` 字面量 **1444 处/91 文件** vs `dimens.*` 仅 209 处/24 文件（14%）；卡片两套并存（`HMPCard` 28 站 vs 裸 `Card(/Surface(` 57 站）；**336 个 `@Composable` 只有 81 个带 `modifier: Modifier`（24%）**。另 `docs/spec/hmp-design.md` 有三处与代码冲突（§6.1 说 4 底部 Tab 实为 3；§6.2 的 Expanded→NavigationRail 实为 `isLandscape && height==Compact`；§3.1/§4.2/§9 称 iOS=SwiftUI/SF Pro，实际 iOS 跑同一套 `AppRoot`）→ 先在文档落**硬约定 + 例外白名单**（含 Modifier），再加机械检查。**判据**：新文件里出现 `Color(0x` 或裸 dp 即被拦
- [ ] **R50** 无任何静态闸门：无 detekt、无 ktlint、无 `.editorconfig`、无 `core.hooksPath`（`.git/hooks` 只有 10 字节空 `post-checkout`/`post-commit`），而 `DEVELOP.md:396-400` 声称"使用 ktlint 检查"并列出三样都不存在的文件；`docs/ktlint-integration.md`(261 行) 通篇是待办方案且前提已失效（§20/§147 假设的 `ci.yml` 不存在，T7 要 CI 跑测试而 CI 恰好不跑 = R31）→ 按该文档 §7 的 T0–T7 走，但**只做增量守门**。**卡点**：KMP 源集、`build/` KSP 生成码排除（只有 `!` 否定 glob 有效）、Windows CRLF（35 个文件纯行尾重写）、`.editorconfig` 段头空格会**静默失效**、`installGitPreCommitHook` 在 Windows 段错误。**判据**：故意在新文件写违规格式即红，存量文件不被卷入
- [ ] **R51** 构建拓扑平铺：1582 行 build 脚本 / 无 convention plugin；root `build.gradle.kts` 540 行里发版链 **285 行（53%）**、`checkVersion` 自带 git shim 62 行、24 个自定义 task 中 **11 个** `notCompatibleWithConfigurationCache`（全仓 13）。实测重复只集中在 **3 段**：android JVM21+proguard 逐字 2 份（`android/app:73-83` vs `core-player:29-38`）、desktop targets+测试源集 4 份、`settings.gradle.kts:5-15` vs `22-32` 的仓库声明镜像 2 份 → 只值得抽这 ~60 行，其余发版 task 外迁脚本。**判据**：抽取后 `tasks --all` 与产物清单不变。**代价先写清**：build-logic 里失去 `libs.` 访问器；且抽象会掩盖"iOS target 集刻意不一致"（`shared:26-36` vs `shared-ios:27-41`）这类有意差异
- [ ] **R52** 三套构建图互不一致：`settings.gradle.kts:38-56` 用 `HMP_BUILD_TARGET` 条件 include，立论注释靠"configure-on-demand 下不配置该模块"，但 `gradle.properties:15-16` 已停用 COD → desktop 分支仍全量配置 `:android:core-player`，CI 能过只因 runner 预装 Android SDK；`build.gradle.kts:34-37` 的 `maybeDepends` 在错目标下静默丢依赖 → 本地(无 env)/CI(有 env)/IDE 三图取并集校验，或收敛成单一全图 + 任务级过滤。**最小可复现场景（当判据用）**：`export HMP_BUILD_TARGET=desktop` 后跑 `./gradlew testAndroid` **必绿**，而该任务实际零工作
- [ ] **R53** 发版真源链路的结构边界：① `sync-release.py` **写与核共用同一套正则** → 锚点位移类错误能同时骗过 `--check`，无独立 oracle；② `site/js/config.js` 取**首个** `version:`、`pbxproj` 全量替换 `MARKETING_VERSION`、ROADMAP 生成块内手改被无警告覆盖；③ 真源集不全：iOS build number 恒 `1`（`project.yml:51`）、部署目标三源（26.3 / 16.0 / 文档）、三套包名空间、`dmgPackageVersion="1"` → 引入独立 oracle（从产物侧读回真实版本比对），并决定 `pbxproj`/`Info.plist` 是否收敛成一份 `.xcconfig`（与 **R38** 同批）。**判据**：故意把某个派生点的锚点位移，核对会报错而不是静默通过
- [ ] **R54** 文档真值失真已影响对外：审查确认 **14 条失真**（逐条见审查报告 §四），其中一条**已随 v7.2.2 进入公开 Release Notes**——ROADMAP 写着"`release.toml` 已修正 README/CLAUDE/DEVELOP 版本口径与 Kotlin 2.3.21"，实际 `DEVELOP.md:273` 仍写 Kotlin 2.2.21 / `:276` 仍写 AGP 9.0.0（真值 2.3.21 / 9.1.1）、`CLAUDE.md:7` 与 `README.md:219` 仍写"最新 v7.2.1 / 下一版 v7.2.2 未开工"（v7.2.2 已发布）、`CLAUDE.md:296` 仍写 72001、`DEVELOP.md:473` 说 release 组 8 个（实为 10）、`CLAUDE.md:85,88` 教的 `:shared-ui:testDebugUnitTest` 任务不存在（应为 `testAndroidHostTest`）、`README.md:83` 说 17 个 Swift 文件（实为 22）、`TODO` 的 I2 待删文件已删 → **与 R36 合并执行**：硬计数改脚本生成，散文只在标记段断言等值。**判据**：故意把 README 的版本改错，`sync --check` 或 pr-check 能红。**⚠ 2026-09-29 部分订正，判据仍未达成故不结案**：借建 `AGENTS.md` 之机，上列失真已按实物逐条改准（`CLAUDE.md` 掏空为 `@AGENTS.md` 指针、`README.md` 开发日志段版本口径、`DEVELOP.md` 全档 **24 处** —— 含 Kotlin/AGP/Gradle/SDK 版本、ktlint 三物不存在、JUnit 实为 `kotlin.test`、Git Flow 与 `develop-*` 分支不存在、版本号真源已移 `release.toml`、AppImage 已移除、任务数 26→27、release 组 8→10、Release Notes 不再取 ROADMAP、五个不存在的类名 `MusicScanner`/`ID3Parser`/`PlayControlViewModel`/`MediaSessionManager`/`MusicViewModel`）。**残留**：①「最新发布版本」这句散文**至今没有任何机器核对**，下次 bump 还会漂（这才是本条判据，归 **R36** 做）；② `DEVELOP.md` 学习资源段仍列 SwiftUI 文档与教程，而项目只剩 1 个 SwiftUI 文件；③ `docs/ktlint-integration.md` 的 `ci.yml` 前提失效属 **R50**，未动
- [ ] **R55** `storybook` 是孤儿模块：有 `build.gradle.kts`(42 行) 却**未被 `settings.gradle.kts` include**，不依赖 `:shared-ui` 而自带 fork 组件与旧版 icons(1.7.3)，仓库唯一 lockfile（`kotlin-js-store/wasm/yarn.lock`）服务的正是它 → 只做决策（删除 / 归位 / 明确声明为设计沙盒），**不进重构范围**（不在构建图里的东西无法被验证）

### 与新基线的对照表（2026-09-30 归口时逐条核）

> 口径：左列旧条目 → 右列 `docs/7_3/` 中的落点。**"未见对应"= 在 `docs/7_3/**` 全域检索该条目的特征符号（`ReActLoop` / `stepBudget` / `ToolRegistryView` / `LocalWindowSizeInfo` / `getMusicIdListByType` / `plurals` / `提示注入` / `泰语` / `podspec` / `convention plugin` / `oracle` 等）0 命中**，**不等于问题不存在**，须逐条复核后再决定立案或结案。

| 旧条目 | 新基线落点 | 状态 |
|---|---|---|
| R39 备份/恢复无事务 | D7-02、D3-03、D2-01、D5-12、v10 §四 | ✅ 已重现 |
| R1 / R2 迁移测试只覆盖部分环、假测试 | D7-09 | ✅ 已重现 |
| R26 `PlaybackHistory` 无索引 | D5-11、D2-07、v10 §2.1 | ✅ 已重现 |
| R23 无界增长（`token_ledger` / 证据表） | D6-05、D5-12 | ✅ 已重现 |
| R4 `AgentScheduler` 竞态 | D6-04 | ✅ 已重现 |
| R10 iOS 密钥 XOR | D7-03 | ✅ 已重现 |
| R40 iOS 扫描目录空壳 | D2-14、D7-06 | ✅ 已重现 |
| R41 NavKey 漏注册 serializer | D8-01、D8-02、X-05 | ✅ 已重现 |
| R42 歌词设置双存储 | D4-01 | ✅ 已重现 |
| R43 DI 无图校验 | X-04 | ✅ 已重现 |
| R45 版本号静默回落 | X-03、D9-03 | ✅ 已重现 |
| R49 设计 token 自愿制 | D8-03 | ✅ 已重现 |
| R50 无静态闸门 | X-07 | ✅ 已重现 |
| R52 三套构建图不一致 | X-09、X-10 | ✅ 已重现 |
| R55 `storybook` 孤儿模块 | X-08 | ✅ 已重现 |
| R30 Android 版本兜底 | X-03 | ✅ 已重现 |
| R29 发版前实机核验 | 各域 §5「验证缺口」 | ◐ 部分 |
| R47 三端镜像漂移 | D3-14（仅 Playlist Impl 一节） | ◐ 部分 |
| R48 / R15 agent 区无 VM | D6 矩阵第 5 行（记为"待验\*"，**未立案**） | ◐ 部分 |
| R53 发版真源结构边界 | D9-03、X-02、X-03（"独立 oracle"未落） | ◐ 部分 |
| R54 文档真值失真 | X-07 附加 doc-truth | ◐ 部分 |
| R56 依赖矩阵无人守 | X-11（**结论不同**：X-11 判 KMP 侧已有守护） | ◐ 部分 |
| R44 CI 不编译、不测试、不编 iOS | X-01 已改判为"按当前环境实现" | ✅ 已定调 ① |
| R3 `ReActLoop` 无 `try/finally` | D6 §4 横查**不构成反驳**；R3 前半与熔断顺序为真缺陷 | ✅ 已定调 ②，待立案 |
| R13 i18n 三件事 | **已立案 D8-06** | ✅ 已处置 ③ |
| R5 设置项 `stepBudget` 无效 · R6 Hello `stateFlow` 不更新 · R7 提示注入 · R8 写路径不过闸 · R9 WARN 日志泄露曲库 · R11 首轮阻塞主线程 · R12 `musicLabel` 聚合 · R16 撤销链路 · R17 产品决策 · R20 死抽象 · R21 注释口径漂移 · R22 大文件拆分 · R24 断点两套 · R25 `plurals` · R27 静默截断 · R28 测试盲区 · R31 CI 测试挂死 · R36 版本号散文核对 · R37 发布后核对 · R38 iOS 脚本改写验证 · R46 `podspec` 入库 · R51 构建拓扑平铺 | —（另注：R31 的新证据见 `docs/7_3/taskbook/README.md` §四：Robolectric 全仓只存在于 `:android:core-player`） | ❓ 未见对应，须逐条复核 |

### 归口时发现的三个问题与决议（2026-09-30）

**① CI 编译与测试 —— 决议：按"当前环境能跑就都跑"实现。**
- **原冲突**：旧 **R44** 要求 `pr-check` 加 `HMP_BUILD_TARGET=desktop ./gradlew compileAll` + `assembleDebug` 与一个 macOS 编译 job；`domain/X.md` **X-01** 与**刻意不做**主张"维持 CI 不跑单测、不编 iOS"；而 `taskbook/README.md` §四 的实测结论是"`:shared:desktopTest` 与 `:shared-ui:testAndroidHostTest` 现在就能进 CI"。
- **决议（用户，2026-09-30）**：编译测试是必要的；开发环境有时确实不支持 iOS，所以**按当前环境实现** —— Linux runner 上跑得到的编译与测试一律纳入；iOS 只在 macOS runner 可用时跑，**缺环境跳过而非失败**。
- **落地清单（属 X-01；2026-09-30 用户决议「CI 先放着」→ 暂缓施工，清单留档以免重新论证）**：
  1. `verify` job（`ubuntu-latest`）：`HMP_BUILD_TARGET=desktop ./gradlew compileAll` + `:android:app:assembleDebug`；测试跑 `:shared:desktopTest` + `:shared-ui:testAndroidHostTest`（这两个不依赖 Robolectric）。
  2. `:android:core-player` 是**全仓唯一带 Robolectric 的模块**（口径见 `taskbook/README.md` §四），其测试要单独处理：先加挂钟超时 / 缓存 `~/.m2/repository/org/robolectric` 再纳入，否则会复现 R31 的挂死。
  3. iOS：macOS runner 上跑 `:shared:compileKotlinIosArm64`（零新代码，KSP target 已配齐），可选 `:shared:iosSimulatorArm64Test`；**仅在 macOS runner 可用时触发**。
  4. **触发范围**：现 `pr-check.yml` 只在 `pull_request → master` 触发，开发线 `feature/*` 的提交**完全无 CI**。要让开发线有反馈，需加 `on: push: branches: ['feature/**']`（或约定手动 dispatch）。
  5. 落地后同步改 `AGENTS.md` §一.5（现写"CI 既不跑单元测试、也不编译 iOS"）与 X-01。
- **判据**：提交一个语法错的 commit，CI 红；无 macOS runner 时 iOS job 跳过而非失败。

**② R3 与 D6 对 `ReActLoop` 的结论 —— 决议：两者都对，问的不是同一件事（2026-09-30 亲验实物）。**
- **事实**（`shared/src/commonMain/.../runtime/ReActLoop.kt`）：
  1. **确实没有 `try/finally`**：收尾的 `presenceBus.emit(idle)`（`:176`）与 `onSessionComplete?.invoke()`（`:179`）在循环体外，异常路径不执行。协程取消（`LlmCallExecutor.call` / `batchDecideApprovals` / `executeOne` 抛 `CancellationException`）直接跳过这两行 → 终态 Presence 不发（停在 `thinking active=true`）、`onSessionComplete` 不跑 → `persistMasterPolicy()` 不落盘，本次会话里累积的 `alwaysAllow`（`:158-160` 原地改 `MutableSet`）**丢失**。→ **R3 前半成立。**
  2. **"tool 轮次不回传"要分两层**：**同一次 run 内确实回传** —— `:95` 以 `trackMessages = true` 构造 `ToolCallExecutor`，`:162` 把每个工具结果 append 成 `role="tool"` 消息（`ToolCallExecutor.kt:198-205`），下一步 LLM 看得到。**跨轮看不到** —— 下一轮 `history` 由 `ChatAgentGateway.buildHistory()` 从 `agent_message` 表装载（`:230-238`），只映射 `"user"` 与 `"agent"/"assistant"`，其余一律 `mapNotNull → null` 丢弃；存储端口注释也写明 role 只有 `user / agent / system`（`AgentMessageStore.kt:11`）。→ **R3 此点成立于跨轮，不成立于同一 run 内。**
  3. **熔断确实在工具执行之后**：`:158-163` 先执行本步全部工具，`:165` 才判 `steps >= stepBudget` 并 break —— 该步写工具已落地，但模型没再被叫起来总结（`finalText` 空 → 走 `:183-184` 兜底文案）。→ **R3 此点成立。**
- **结论**：D6 §4 横查（`:109`）的"单 Agent 内部生命周期正常"指的是**退出机制**（`while` + `stepBudget` 不挂死不泄漏），也成立 —— 但它没回答"取消路径的收尾"，**不构成对 R3 的反驳**。两条都留：D6 不改；R3 的前半与第三点作为真缺陷立案（补 `try/finally` + 把 tool 轮次持久化）。
- **遗留**：R3 里"下一轮看不到已执行 → **可能**重复执行写工具"是**推理不是实测** —— 最终答复文本（`role="agent"`）是存下来的，若模型在文本里说了"已加进歌单"就仍有信号。要坐实需一条用例：同一会话两轮，第一轮执行写工具后取消/熔断，看第二轮是否重复执行。

**③ i18n —— 决议：同意立案，已落 `D8-06`。**
- 已在 [docs/7_3/domain/D8.md](docs/7_3/domain/D8.md) 追加 **D8-06**（androidMain 旧平行副本 / 无键集合校验 / 泰语 258 行未译），并把该文件 §4「i18n 无键漂移」的"结构性保证"措辞订正为"实测齐平但无机制保证"、§6 语种条注明"覆盖承诺"不含翻译质量。证据与口径见 D8-06 全文，此处不重复；域发现总数随之 **108 → 109**。

---

## 七、挂起（不排期，可整体延后）

- ⏸ **F10** 语音会话（`RealtimeVoiceTransport`）—— 方向 B 里唯一真正新增的传输层，需真实端点验证，与主线解耦、未开工；端点不可用即整体延期，v1 完整性不依赖语音

***

© 2026 Hearable Music Player | Developed by WLYB
