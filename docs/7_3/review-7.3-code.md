# 代码实现审查报告（2026-09-29）· 修复归入 v7.3.0

**审查对象**：`feature/architecture-hardening` 分支基线（= `5fe7eff`，v7.2.2 已发布代码）。本轮**不看架构与真源**（那是 2026-09-28 那份的辖区），只看**实现级缺陷**：静默空转、并发与生命周期、可观测性落点、数据层正确性、类型边界。
**方法**：5 路分片并行实读（agent 运行时 / 可观测性与弹性 / 类型安全 / 数据层 / UI 层），主审对**每一条 S1 亲验到 `file:line` 并复跑 grep**。下表标 ✔ 的即主审亲验。
**分级**：`S1` = 会造成错误行为 / 静默空转 / 数据风险 / 用户可感知的卡死 → `S2` = 结构性债务 → `S3` = 一致性 / 可发现性。
**编号**：延续 [review-7.3-architecture.md](review-7.3-architecture.md) 的 R 系列，本轮新增 **R57–R78**（R78 是同日复核时从一条被我误写的"优点"里挖出来的，见文末修正记录）。与既有 R__ 同项的**不开新号**，在第七节登记归属。

---

## 一、总体判断

上一份审查的结论仍然成立并且被再次印证：骨架选型是对的。但本轮挖出了一类它没覆盖的缺陷形态——**静默空转**：字符串词表对不上、`when` 落 `else`、声明了却不校验的参数、收了参数却不使用。这类问题**不崩、不报错、不让 CI 变红，只是功能悄悄不做**，因此对"CI 不跑测试 + agent 域大量用 Fake"的本仓库来说是最难被发现的一类。

三条实证足以说明量级：

1. ✔ 电台的"最常播放"排序**三端从未生效**——调用方传 `"play_count"`，三端实现比的是 `"playCount"`，全部落进 `else` 返回 id-DESC。
2. ✔ `getRecentSkipRate/getRecentPlayRate(limit, days)` 的 `days` **在方法体里从未被引用**，注释却写"7 天内"。用户看到的"最近"其实是** lifetime**。
3. ✔ `Lang.kt:551` 未知提示词键返回 `""`，而 `LangTest.kt:119` **用断言把这个行为锁死了**——提示词键拼错，Agent 带着空 system prompt 出门，且保证不报错。

第二类的根因是**边界无类型**：`shared/` 主源集零 `!!`、工具入参那条边界做得确实好（descriptor 驱动 schema+校验器同源，找不到绕过路径），但**边界之外**的 `orderBy` / `role` / `endpoint` / `filePath` / `promptKey` 全是裸 `String` + `else ->` / `?: DEFAULT` 兜底，把契约违约换算成静默丢数据。

结论：**这些绝大多数能靠 `enum` + 无 `else` 的穷尽 `when` 变成编译错误**。本轮没有一条需要改架构。

---

## 二、S1 明细

### ✔ R57 词表漂移造成静默空转（四处实证）
- `RadioSubAgent.kt:955,1252,1595` 传 `"play_count"`，实现比 `"playCount"`（`MusicRepositoryImpl.android.kt:109` / `.desktop.kt:252,258`）→ 电台按播放次数排序在所有平台不生效。
- `MusicListConfig.kt:256,291,297` 对 UI 宣称 `fileSize` / `date` / `year`，Android 实现只认 `duration` / `playCount`，其余落 `else -> mappedList`（不排序）→ 用户选"按日期"拿到乱序列表，而索引条仍按已排序计算锚点，跳转落点错位。
- `MasterAgent.kt:355-362` `when (role)` 无 `else`（语句位）后**无条件**写 `saveAgentPolicyConfig(role, …)`，而 `role` 是自由 `String`（`agent_endpoint_${role}` 拼键）→ 未知 role 写出孤儿配置键，读回时 `else -> AgentPolicyConfig()` 把问题藏住。
- `Lang.kt:551` 未知 key 返回 `""` + `LangTest.kt:119` 锁死该行为。
- **动作**：`enum class MusicOrderBy / AgentRole / PromptKey`，删掉 `else ->`；`resolvePrompt` 未知键改抛。
- **判据**：新增一个枚举值而不补 `when` 分支 → 编译失败；一条断言遍历 UI 宣称的排序键 ⇒ 必须是仓库认识的键。

### ✔ R58 电台每轮 prompt 无上限增长
`RadioSession.kt:475` 每轮重发**整个** `settled` 听歌账本、`:499-500` 重发整个 `executed` 动作列表，两者**从不裁剪**（`intents` 有 `INTENT_KEEP=5`、`messages` 有 `trimHistory`，唯独这两个没有），而 judge 走 `contextBudget.callOnce` 无窗口预Guard。
**影响**：长会话把 prompt 线性推高直至超出端点窗口，`askModel` 返回 null，电台**静默降级为不再仲裁**，外加 token 成本失控。
**判据**：一条用例断言第 N 轮的 prompt 长度不随 N 线性增长。

### ✔ R59 确认链 id 冲突 + 事件被丢 → Agent 永久挂起
`DialogManager.kt:59` `val id = nowEpochMillis()` 作 `pendingConfirms` 的 key，同帧两次调用即互相覆盖；`:28-32` 事件流是 `replay=0, extraBufferCapacity=1, onBufferOverflow=DROP_OLDEST`。
**影响**：第一个 continuation 永不 resume → 工具调用**永久挂起**，用户端表现为"Agent 卡死"。这是本轮**最容易复现、最难定位**的一条。
**动作**：原子自增 id；确认链换可靠投递（state-based，或独立 `replay=1` 流）。
**判据**：同帧发起两次 confirm，两者都能被应答。

### ✔ R60 桌面端路径按 `/` 切分
`MusicRepositoryImpl.desktop.kt:269` `path.substringBeforeLast("/", "Unknown")`，而 `DeviceMusicScanner.desktop.kt:82` 存的是 `file.absolutePath`。Windows 路径无 `/`。
**影响**：Windows 用户所有已删曲目在"按文件夹分组"的恢复界面塌缩成单个 `Unknown`。Android 侧用 `File(path).parent` 是正确的——同一逻辑三端各写一份的老问题（与 R47 同因，但这里是**已发生的错误行为**，不是债务）。

### ✔ R61 数据层的"贵"和"错"经常是同一行
- ✔ `shared/schemas/.../9.json`：**18 个实体里 `music` 表索引数为 0**（仅 `playlist_item` 1、`user_profile_evidence` 1、`token_ledger` 2）。`isDeleted`（几乎每条查询都过滤）、`artist` / `album`（`getMusicInfoByArtist/Album` 的 WHERE）、`title` 全走全表扫。
- ✔ `MusicRepositoryBase.kt:147` `searchMusic("%$query%")` 直接用未转义的原始输入 → 用户搜 `%` 命中全库。对照 `MusicRepositoryImpl.android.kt:321-363` 对 MediaStore 是**做了转义的**——同一仓库内纪律不一致。
- ✔ `MusicRepositoryBase.kt:413-424` `getSimilarSongsByWeightedLabels` 先 `getAllMusicInfoAsList("id","ASC")` 全库载入，再对**每一行**调 `getMusicLabels(id)` → N+1。而它是热路径：切歌时触发（`MusicController.kt:804`、`DesktopMusicController.kt:605`），也被 agent 工具调用。
- ✔ `getRecentSkipRate` / `getRecentPlayRate` 收了 `days` 却从不按时间过滤（见第一节）。
- ⚠️ 分片另报 `getAllArtistsSummary`/`getAllAlbumsSummary` 全库载入只为计数、`@Relation` 与 LEFT JOIN 组合导致 join 白做 + 每行关系子查询——**我未亲验**，列在第五节。
- **判据**：`EXPLAIN QUERY PLAN` 在 `music` 的主要查询上不出现 `SCAN`；`searchMusic("%")` 返回空而非全库；相似歌曲的单次调用 SQL 语句数与曲库规模解耦。

### ✔ R62 引擎层把取消当事件记成 ERROR
`FFmpegAudioEngine.kt:404-405` `catch (e: Exception) { HmpLog.e(...) { "🎬 exception: ${e.message}" } }`，而 `stop()` / `seekTo()` 都会 `playbackJob?.cancel()` —— `CancellationException` 是 `Exception`。
**影响**：每次 seek/stop 落一条假 ERROR。真出解码故障时，日志里满是无害红行，**失去分辨能力**。同一 `catch` 里的 `onError` 派发走 `withContext(Dispatchers.Main)`，在取消上下文中会被短路——错误既被记错、又可能派不出去。
**动作**：先 `catch (e: CancellationException) { throw e }` 再 `catch (Exception)`。另有一处 `catch (_: Exception) { durationMs = 0L }` 零日志的探测失败路径，配合控制器已置 `_isPlaying=true`（`DesktopMusicController.kt:259`）→ 用户看到"正在播放"却无声、不可拖、无报错、无日志。

### ✔ R63 设置恢复假成功
`SettingsRepositoryImpl.desktop.kt:356-379`（iOS `:381` 同形）：正则拆 JSON → `when (key)` **15 个字面量键、无 `else`** → 逐键 `catch (_: Exception) { }` → 循环外**无条件** `Result.success(Unit)`。
**影响**：备份里凡不在那 15 个键之列（`lyricsPlayerConfig` / `agent_policy_*` / `custom_ai_endpoint`）或类型不符的项**全部静默丢弃**，UI 报"恢复成功"。这是 R39 之外的另一条假成功路径：R39 讲的是缺事务，这条讲的是**吞掉失败并声称成功**。

### ✔ R64 播放启动路径零日志静默
`MusicController.kt:321,334,372`（另 `:598` 附近）写作 `catch (e: Exception) { // Ignore }`，Desktop 侧 `DesktopMusicController.kt:186,197,370,401,427` 是空体 `catch (_: Exception) {}`——**两字面量不同，静默吞异常这一结论一致**。
**影响**：DB/DataStore 在启动期抛错时，队列恢复、进度持久化、最近播放记录一起消失，**三处任何地方都不留痕迹**。对照 iOS 至少 `HmpLog.e`（`MusicPlayerController.swift:640,659`）——恰恰是**唯一有测试的 desktop 侧**和 Android 侧最盲。

### ✔ R65 双份 `LabelName` 靠 `valueOf(name)` 桥接，异常被换算成 null
`domain/enum/LabelName.kt` 与 `data/database/myenum/Label.kt` 各一份，经 `myenum/Label.kt:103`（Room `@TypeConverter`）与 `MusicMapper.kt:116,122` 的 `valueOf(name)` 对接，**无奇偶校验测试**；而 `MusicRepositoryBase.kt:262` 把它包在 `try/catch → null` 里。
**影响**：任一侧加一个常量 → 要么全库标签读取抛错，要么**标签被静默清空**。后者更糟。
**判据**：一条 5 行用例断言两份枚举常量集合相等（或删除孪生枚举后此条自动成立）。

### ✔ R66 用户可改的日 token 配额是死字段
`GlobalAgentConfig.kt:14` `dailyTokenQuota` 在**主源集只有它自己的声明这一处引用**（`commonTest` 里有 3 处测试引用，不构成消费）；`GlobalTokenCounter` 用自身的 `DEFAULT_DAILY_TOKEN_QUOTA` 构造（DI 未传值），`MasterAgent.kt:1251` 只是把计数器自己的值拼进日志字符串。
**影响**：改配额、存下来、什么都不发生。与 R5（`stepBudget` 同类）**同因不同字段**——建议合并成一条"设置项必须被消费"的断言闸门。

### ✔ R67 `close()` / `shutdown()` 不取消引擎 scope，`close()` 还漏停仲裁
`MasterAgent.kt:158` `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`，全文件**无 `scope.cancel()`**；`:549` 与 `:563` 取消的是**另一个** `lifecycleScope` 的 Job。`scope` 的未跟踪子任务（`onLibraryMutated` :421、`ensureProfileReady` :539、`maybeRegenerateNarrative` :1628、`maybeExtractDialogue` :1635）在 close 之后继续持有 transport/DAO 并回写 `_radioState`。
另：`close()`（非 suspend，是平台终止入口）**没有**调 `scheduler.stopArbitration()`（只有 `shutdown()` :545 调），而 `AgentScheduler.stopArbitration()`（`:115-123`）自己也只置 `arbitrationStarted=false`、**从不取消它的 `scope`**，靠 ≤1s 的 delay 退出。
**判据**：一条用例断 close 后 scope 已取消、且仲裁不再产生第二次 tick。

### ✔ R68 `pauseRadio` / `resumeRadio` 绕过生命周期互斥
`radioLifecycleMutex` 只在 `:722`（start）与 `:870`（stop）加锁；`pauseRadio()` `:922`、`resumeRadio()` `:935` 直接 `_subAgents["radio"]` 取实例就调进去，而 `stopRadioLocked`（:874-914）在锁内 `shutdown()` 并 `remove` **同一个实例**。
**影响**：暂停/恢复可驱动一个已收摊的 agent，或与 generation 计数赛跑。属 R4 的并发家族但**是不同的对象与不同的锁**。

### ✔ R69 全仓零生命周期感知收集
`collectAsStateWithLifecycle` = **0 处**，`collectAsState()` = **128 处**（同日复核重算；原文 126 少计 2 处）。最坏是 `AppRoot.kt:158` 在 781 行根作用域直接收 `currentPosition`。
**影响**：播放进度每秒触发整树重组；Android 退到后台仍在收集与重组（电耗）。属三端共享代码，一处改三端受益。

### ✔ R71 API Key 被写进 OS saved-state
`AIScreen.kt:573` `var apiKeyValue by rememberSaveable { mutableStateOf("") }`。`rememberSaveable` 靠把值写进系统 Bundle 来跨进程死亡恢复 → **明文密钥落盘**，直接抵消 `SecureStorageHelper` 的整套设计。密钥字段应退回 `remember`。

### ✔ R72 备份路径无目录约束
`BackupFileRepositoryImpl.desktop.kt:40,69` 对传入的绝对路径**直接** `File(filePath)` 后 `delete()` / 读取，无"必须位于备份目录内"的校验。
**影响**：`deleteBackup("C:\\Users\\<u>\\Documents\\thesis.docx")` 会删掉库外文件并返回 `Result.success`。属可达面问题（当前调用方只传备份目录内的值），但边界类型本身不给任何保证。

---

## 三、S2 明细

| | 编号 | 发现 | 证据 | 判据要点 |
|---|---|---|---|---|
| ✔ | **R70** | UI 直连 DAO + `remember` 构造 VM | `AuditLogScreen.kt:76-77` `koinInject<AgentAuditLogDao>()` + `remember { AuditLogViewModel(dao) }`（**未在任何 Koin module 注册**）；`AgentMonitorScreen.kt:111,199` 注入 `TokenLedgerDao`；commonMain 内 16 处 `import com.hmp.data.*`。该 VM `init{refresh()}` 里 `dao.getAll()` **无上限**且 `runCatching getOrDefault(emptyList())` → 库出错时渲染成"没有日志" | 页面不持有 DAO；`onCleared()` 真被调用 |
| ✔ | **R73** | 校验声明与校验实现脱钩 | `ToolSpec.kt:113,128` 的 `maxItems` 只出现在**声明**与 `:120,:135` 的 schema 生成，`parseArgs` 从不读它 → `PlaylistTools.kt:193` 声称的 200 上限不存在，重排列表长度不受限。`StringParam` 无 `maxLength`，工具入参可无上限进 DB（与 R7 相邻）。`ContextPlaybackTools.kt:87-93` 允许 10 条命令，`:108-120` 用 `else -> null` 映射，`:102` 于是会报一句**与真因无关**的"缺少必要参数" | schema 与校验同源；加一个允许值不补映射即编译或测试红 |
| ✔ | **R74** | iOS block 版通知观察者从不注销 | `PlayerEngine.swift:112-117`（实际路径 `ios/HMP/HMP/Features/Player/`）用 `addObserver(forName:object:queue:using:)` 且**丢弃返回 token**；`cleanup()` 起于 `:125`，其中的 `removeObserver(self)` 在 **`:134`**，它**不能**注销 block 版注册（同文件三个 KVO `invalidate()` 在 `:135/:137/:139`，是对的） | 数量级需运行时计（见第五节）；修法是先存 token 再 `removeObserver(token)` |
| ✔ | **R78** | agent 运行时的 `!!` 靠 22 行外的上游守卫兜命 | `ToolCallExecutor.kt:91` 在确认闸门里 `registry.find(tc.name)!!.permissionLevel`。今天不炸，只因 `:69` 的 `?: continue` 先把未知工具名挡在 `pending` 外；两处相隔 22 行且不在同一函数作用域——**任何新增的 `pending` 入口漏掉那道守卫，确认闸门即 NPE**。同模式主源集合计 **15 处**：`AgentPolicy.kt:110`、`SessionStore.kt:28`、`RadioSubAgent.kt:801`、`HelloSubAgent.kt:977/1000`、`LrcParser.kt:103/132`、`TimerUseCase.kt:49`，及 `iosMain` 的 `BackupFileRepositoryImpl`/`SettingsRepositoryImpl`/`DataStore`/`DeviceMusicScanner`/`SecureStorageHelper` | **不是活 bug**（已确证上游守卫存在），按潜在脆弱性记 S2 而非 S1。修法：单次 `find(...) ?: return` 后复用对象，不二次查表。判据：一条用例让 `pending` 含未知工具名而不崩 |
| ✔ | **R75** | 同一"小时"指标两套时区 | DAO 聚合用 `strftime(…, 'localtime')`（`PlaybackHistory.kt:101,113`、`PlayList.kt:76`），而 `MusicRepositoryBase.kt:1221-1223` 的 `hourOf`/`dayKey` 是 `epochMs/3_600_000 % 24` = **UTC** → `getHourlyDistribution` 与 `getBehaviorSnapshot` 对同一设备给出打架的小时分布 | 统一口径并一条用例钉住 |
| ✔ | **R76** | 软删除标记止步于数据层 | 实体 `Music.kt` 内 `isDeleted` 出现 35 次，而领域模型 `MusicModels.kt` **0 次**、`MusicMapper` 也不映射 → 任何忘记 `WHERE isDeleted = 0` 的 DAO 会复活已删曲目，类型上毫无信号 | 要么让领域层承载该不变量，要么把过滤收进唯一入口 |
| ⚠️ | **R77** | Desktop 退出与落盘竞争 | `DesktopMusicController.kt:698-704` 的 `release()` 用 `scope.launch` 异步排 `endCurrentPlaybackSession` / `persist*`，而 `Main.kt:185-188` 随即 `exitApplication()`；shutdown hook 只关 `MasterAgent`，从不 join 控制器 scope → 最后一次会话结算与队列尾部非确定性地丢 | 需退出时序实验 |
| ✔ | — | `_subAgents` 是无锁 `mutableMapOf` | `MasterAgent.kt:381` 声明；变更在 per-agent mutex 下，但 `updateAiConfig` :1101、事件监听 :1139/:1147/:1156、`statusSnapshot` :1248、`capabilities` :386 **无锁读** → JVM 目标上可 CME。属 R4 家族但不同对象，不另开号 |
| ⚠️ | — | 分片另报的三项我未亲验：`estimatePromptTokens` 内容系数被**双乘 0.7**（`AgentContextBudget.kt:278` 与 `:287`）使"保守高估"变成低估；`historySummary` `:257` 只增不减并每轮作 system message 重发；`AgentConfigModels.kt:56-57` 默认值写字面量而相邻行用 `EngineDefaults.*` | 见第五节 |

---

## 四、主审复核：证实、否决与下调

1. **✔ 证实并升级**：R59（DialogManager）。分片报为 HIGH，我认为它是本轮**唯一能造成用户可感知"卡死"且日志里查不到**的一条，S1 首位。
2. **✔ 证实但改写论据**：R66。分片称 `MasterAgent.kt:1251` 消费了 `GlobalAgentConfig.dailyTokenQuota`；实测那行读的是**计数器自己的** `dailyTokenQuota` 属性，`GlobalAgentConfig` 那份全仓仅 1 处引用 = 它自己的声明。结论更强（字段完全是死的），论据已换。
3. **✔ 证实但路径更正**：R74。分片写 `ios/HMP/HMP/PlayerEngine.swift`，实际在 `ios/HMP/HMP/Features/Player/PlayerEngine.swift:112`。文件存在、用法确属错误，故保留；但**影响幅度我没有运行时证据**（NotificationCenter 对 `object:` 的持有语义决定了它是"每首累积"还是"随 item 释放而失效"），因此降级为 S2，并在第五节给出定量条件。
4. **⚠️ 部分采信**：R65 分片给的 `Label.kt:103` 与两份枚举常量数我用不同 grep 复核，路径与 `valueOf` 位置属实，但"55 个常量"这一计数未复核——按本报告规矩，**计数不作判据**，只作量级描述。
5. **✗ 否决**：分片称 `MusicRepositoryImpl.android.kt` 的 `getAllMusicInfoAsList` 与 iOS 版"注释互称刻意不同实则逐字相同"是本轮新发现——那是上一份审查的 **R47** 已立案项，不重复开号。
6. **✗ 否决**：分片报的"iOS `PermissionService` 对权限恒返回 `true`"是 **R40 家族**的已知现状，且属产品刻意取舍（iOS 无悬浮窗权限概念），不当 bug 报。
7. **⚠️ 不作为判据**：分片报"11 处 `items(` 仅 5 处带 key"。我的 grep 口径复跑结果与之矛盾（正则无法区分 `items(list){}` 与 `items(items=…, key=…)`），**无法证实**，移入第五节，不写进结论。

---

## 五、验证缺口（写清用什么条件才能定案）

- **`@Relation` 与 LEFT JOIN 的真实代价**：`Music.kt:330-355` 的查询对 `musicExtra`/`userInfo` 做了 LEFT JOIN，但 `MusicInfo` 把它们声明为 `@Relation` 而非 `@Embedded`。Room 会另发关系查询——是否批量取决于版本。**定案条件**：在 `MusicDaoTest.kt:267,278` 处计数 SQL 语句数（Room 支持 `setQueryLogger`），断言一次调用不随行数线性增长。
- **相似歌曲 N+1 的实际频率**：静态可证 `MusicRepositoryBase.kt:413-424` 是全库 × 每行查询；但它是否真的卡到用户，取决于曲库规模。**定案条件**：1k 曲库上测一次调用耗时。
- **R74 泄漏量级**：**定案条件**：iOS 上连续播 50 首，打印 `PlayerEngine` 的注册计数。
- **R77 退出竞争**：静态可证无 join。**定案条件**：退出前写一条哨兵设置，重启看是否落库。
- **`items()` key 覆盖率**、`estimatePromptTokens` 双 0.7、`historySummary` 无上限：均**未经主审亲验**，动手前按符号名复跑。
- **可及性**：分片用自制括号解析数出 59 处 `contentDescription = null`，自述"漏解析嵌套 lambda"。**不要采用该数字**；正解是跑 Android Lint 的 `ContentDescription` 检查（属 R50 的静态闸门线）。

---

## 六、刻意不做（带理由）

| 项 | 不做的理由 | 何时改判 |
|---|---|---|
| 把 `collectAsState` 全量替换成 `collectAsStateWithLifecycle` | 128 处机械替换会淹没 v7.3.0 的 diff，且**根因是 AppRoot 的收集位置**（R69），不是 API 名字。先治根，再谈全量 | 只对新代码强制；存量随 R48 那批 agent 区重构一起动 |
| 一次性把 `orderBy` 从 `String` 改 `enum` | 涉及已持久化的偏好值与三个平台的读取端，属 schema 语义变更，**和 v10 迁移撞车** | 与 R1/R2/R26/R39 那一批（都要开 v10）合并做 |
| 给 `searchMusic` 加全文索引 | `music` 表加索引本身就是 v10 迁移的一部分（同 R26 的 `playedAt`/`musicId`），单独做等于把一次迁移拆成两次 | 同上 |
| 把 `AgentConfigScreen` 的 17 处无键 `remember` 改成 `remember(agentRole)` | 治的是症状；病是这屏没有 VM（**R48/R15**）。先补 VM，键问题随之消失 | 随 R48 |
| 直接删 `GlobalAgentConfig.dailyTokenQuota` | 它是**设置项未被消费**这一类问题的第一个样本，删字段就丢了这个类 | 先立"设置项必须被消费"的断言（并入 R5 的闸门），再让断言决定去留 |

---

## 七、对 v7.3.0 执行顺序的影响

上一份把顺序定为「先闸门 → 再 S1 → 后结构」。本轮证据**支持这个顺序，但修正一处**：

- **R44 那道编译闸门抓不到本轮任何一条**。上面所有 S1 都能干净编译。能兜住它们的是 **R41/R43/R47/R56 那类断言 + R57 的枚举化**。
  > ⚠️ **本条原文接着写"而断言要在 CI 跑起来又卡在 R31"，同日复核证明该判断不成立**：Robolectric 只在 `android/core-player`（`build.gradle.kts:60`），而 `testAll` 硬挂的 `:shared:desktopTest` 与 `:shared-ui:testAndroidHostTest` 只用 junit/mockk/coroutines-test，`commonTest` 更被刻意约束成"只用平台无关测试库"（`shared-ui/build.gradle.kts` 注释）。**CI 现在就能跑这两条而无需等 R31**，详见 taskbook README §四 的修正。
- 本轮新增项与既有批次归并建议：
  - **闸门批** +R57 的断言段（词表遍历）、+R66/R5 合并成的"设置项必须被消费"断言、+R73 的"schema 与校验同源"断言。
  - **S1 批** +R58 R59 R60 R61 R62 R63 R64 R65 R67 R68 R71 R72。其中 **R59（Agent 卡死）与 R63（恢复假成功）优先级应与 R39 并列**——它们都是用户可感知的数据/体验事故，而 R39 不是唯一一条。
  - **v10 迁移批**：R1/R2/R26/R39 + **R61 的索引** + **R57 的 `orderBy` 枚举化** + **R42 的歌词 legacy 键一次性迁移**（同日复核补入——原文把它丢在"空转清零"轮，却因同一理由把 R57 拉了进来，属同因不同判）。
  - **结构批** +R70 R74 R75 R76 R77 R78。
  - **不进 v7.3.0**：R69 的全量替换、`items()` key 治理（待证实）。

---

## 八、值得保留的做法（本轮实测）

- **工具入参边界是全仓最强的类型面**：`ToolSpec` 的 descriptor 同时产 schema 与校验器，required / enum / 整型边界 / clamp 一路齐全，且我**没有找到绕过 `ToolRegistry.executeTool` 的路径**。R73 是唯一缺口（`maxItems` 声明未校验）。这个模式值得反向推广到 `orderBy` / `role` / `endpoint`。
- **LLM 链路弹性最强**：`HttpTimeout` 三端都装（`HttpClient.{android,desktop,ios}.kt:15-23`）、`OpenAiLlmTransport.kt:112-128` 传输级超时 + 正确复抛 CE、`EnrichSubAgent.kt:352-367` 有退避。
- **取消幂等做得好**：`RadioSession` 用 generation 快照在 `askModel` 前后比对，暂停/关闭后迟到的 REPLACE/APPEND 结果被正确丢弃；`SchedulerStopSignal` 的 lock/unlock 配对与 `while(pauseRequested)` 重检查无漏；`LyricsConfigResolver` 真能阻自环。
- **`EnrichResponseParser` 是全仓最好的解析器**：索引钳制 `:192`、`getOrNull` `:210`、未知字段记日志 `:234`、数组包裹陷阱已修并在 `:259-265` 写清原因。
- **`LlmEvent` / `PlaybackCommand` / `CommandSource` 都是规矩的 sealed 类型**；`MemLogWriter` 是 200 条有界环；iOS `CoverCache` 走 `NSCache` 且 `countLimit=200`。
  > ⚠️ 本条原文曾同时声称"`shared/` 主源集零 `!!`"——**该断言为假，已删**。实测主源集有 **15 处 `!!`**（9 个文件，其中 5 处在 agent 运行时），已改立为 **R78**。
- **迁移链 1→9 连续无断档**，7→8 / 8→9 的 DDL 与实体和 `9.json` 相符；全仓无 `fallbackToDestructiveMigration`。问题只在**测了 3/8 环**（R1/R2）与**无降级守卫**（R29 已在代码中确证：三端都没有 `RoomDatabase.Callback`）。

---

**主审签署口径**：第二节与第三节中标 ✔ 者为主审亲验到 `file:line`；标 ⚠️ 或未标记者为分片实测，动手前须按符号名复跑。本轮计数类数字（索引数、`collectAsState` 处数、`isDeleted` 出现次数）均由主审当场 grep 得出，仍不应当作长期判据——它们属 R54「硬计数改脚本生成」的对象。

---

## 九、同日修正记录（2026-09-29 复核）

本报告发布后另派三路独立审计（证据核对 / 计划一致性 / 判据可执行性）反查自己，结果**推翻了我一条断言、修正了五处文字、并新增一项**。按房内规矩，事实错误就地改准，但**改了什么必须留痕**，否则 ✔ 图例就失去意义。

| 类别 | 原述 | 实况 | 处置 |
|---|---|---|---|
| **断言为假** | §八 "shared/ 主源集零 `!!`" 被当作 ✔ 优点写入 | 主源集 **15 处**，9 个文件，agent 运行时占 5 处 | 删除该断言并在原位留警示；由它派生出 **R78** |
| **措辞过宽** | R66 "全仓仅 1 处引用" | 主源集成立；`commonTest` 有 3 处测试引用 | 收窄为"主源集"，并补"测试引用不构成消费" |
| **引文串行** | R64 把 Desktop 站点引作 `catch (e: Exception) { // Ignore }` | Desktop 实为空体 `catch (_: Exception) {}`；`// Ignore` 只在 Android | 分开表述，结论不变 |
| **行号偏** | R74 `cleanup():129` | `cleanup()` 起于 `:125`，`removeObserver(self)` 在 **`:134`** | 已改；顺带确认三个 KVO `invalidate()` 在 `:135/:137/:139` |
| **计数少算** | R69 `collectAsState()` = 126 | 实测 **128** | 已改两处引用。这正是"计数不该写进散文"的现行例证 |
| **战略误判** | §七 "断言要在 CI 跑起来又卡在 R31"，并据此建议"第 1 步只加编译、测试等 R31" | R31 **可拆**：挂死嫌疑是 `:android:core-player` 的三个 Robolectric 用例，而 `:shared:desktopTest` / `:shared-ui:testAndroidHostTest` 不依赖它 | 删除该建议，改为"CI 现在就能跑 KMP 侧测试"。这条修正直接改变了 t0 的形状 |
| **归类不一致** | R42 留在"空转清零"轮，却把同因的 R57 `orderBy` 拉进迁移轮 | 两者都动持久化语义 | 一并归入 v10 迁移批 |

**没有推翻的**：R57–R76 的 20 条 ✔ **实体结论全部成立**（只有上述引文/行号级漂移）；`music` 表零索引、`play_count`/`playCount` 不匹配、`days` 参数未被使用、`DialogManager` 的 id 与缓冲策略、`MasterAgent.scope` 从不取消——均经第二人独立复跑确认。

**方法教训**：✔ 只对"我逐条查过的那 20 条"有效，而 §八 的优点清单是我**没有**逐条查就抄过来的。正面结论和缺陷结论适用同一套验收标准——这一点本报告第一版没做到。

**© 2026 Hearable Music Player · 代码实现审查（v7.3.0 修复范围，续 review-7.3-architecture.md）**
