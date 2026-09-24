# v7.2.0 合入前 review 报告

**审查对象**：`origin/master(=v7.1.0)...release/7.2.0` —— 453 文件 / +77570 / −6879（方向 B Agent 化 F1–F14 + 官网改版 + 收尾修复）
**审查日期**：2026-09-24
**方法**：机械排除（Room schemas / 文档 / 14 语言资源不人肉读）→ 6 路分片并行审（护栏 / 引擎与生命周期 / SubAgent 与 Capability / 数据密钥日志 / taskbook 退出条件 / 三端与 i18n 对称性）→ 主审对全部「阻塞」级结论逐条复核
**分级**：`A 阻塞发版` = 修完再进 master；`B 合入后必修` = 一版内偿还；`C 记录待办`

## 闸门结果

| 闸门 | 结果 |
|---|---|
| `:shared:desktopTest` + `:shared-ui:desktopTest` | ✅ BUILD SUCCESSFUL（1m37s，961 `@Test` / 119 类，静态计数与文档吻合） |
| `checkVersion` | ✅ 7.2.0 vs tag 7.1.0 不重复。**但必须加 `--no-configuration-cache`**：`checkVersion` 漏标 `notCompatibleWithConfigurationCache`，配置缓存下构建失败 |
| 三端编译 | ⬜ 未跑（iOS 需 macOS） |
| 真机/交互 | ⬜ 未跑（F11 后台存活、iOS 锁屏与 Live Activity、三端首启引导） |
| i18n 键集合校验 | ❌ **仓库中不存在**文档承诺的校验脚本与单测，CI 亦无此步 |

---

## 处置结果（2026-09-24，`release/7.2.0`）

**已在本分支修完**（`:shared:desktopTest` + `:shared-ui:desktopTest` 全绿，`:shared:compileAndroidMain` 通过）：

| 项 | 修法 | 回归 |
|---|---|---|
| A1 | `MasterAgent` 三组 `stopXxx()` 拆出锁内版 `stopXxxLocked()`，`startXxx` 锁内改调锁内版。**修复过程中发现同一触发路径上还有第二个成因**：`startXxx` 开头无条件 `xxxRunLoopJob?.join()`，而 `pauseRadio` 只翻 `runState`、不清 `isActive`，旧 runLoop 仍在自旋 → join 永不返回，压根走不到锁重入那一步。改为「先判活跃 → 需要收摊时才 cancel + join」，join 移入 `stopXxxLocked()` | `MasterAgentRadioTest.startRadio_afterPause_doesNotHang` / `startRadio_afterStop_doesNotHang`（`withTimeout` 兜住，修复前必挂） |
| A2 | `PolicyGuard` Phase 0 加 `level != STRONG_CONFIRM` 前置；`ConfirmItem` 透传 `permissionLevel` 并让不可逆项默认不勾选、不显示「总是允许」，VM 侧 `toggleAlwaysAllowConfirmItem` 对不可逆项直接不响应 | `EngineGuardTest` 原用例「whitelist bypasses all later phases」把这条漏洞当契约断言（`:126` 明确要 `AllowSilent`），已改名为 `bypasses CONFIRM but never STRONG_CONFIRM` 并反转该断言 |
| A3 | 三端 `SettingsRepositoryImpl` 增加 `agent_policy_${role}_enabled` 键并在 get/save 读写（get 缺省 `true`，与旧行为兼容） | 逻辑在平台源集，commonTest 够不到；**需手动验一次**：关电台 → 杀进程 → 重启应仍为关 |
| A4 | 去掉三端 `fallbackToDestructiveMigration(dropAllTables = true)`。原计划用 `Builder.requireMigration(9)` 只放行 v8 以下，**该重载在 Room 2.8.3 KMP 不存在**（编译期 `requireMigration` 只有 Boolean 属性），故按「1→9 迁移链本就完整」直接移除兜底：降级/未知版本改为打开失败，而不是静默清库 | 无自动化；行为是「宁可打不开也不丢数据」，若嫌硬可后续加 `Callback` 引导恢复备份（列 R29） |
| B3 | `ToolCallExecutor.executeOne` 在 `catch (Exception)` 前复抛 `CancellationException` | `ToolCallExecutorTest.\`CancellationException 复抛而非记为 failed\`` |

**未修而有意保留**：A2 的产品决策面（不可逆工具要不要给「本次一律允许」的折中档）→ R17；`DialogConfirmGateAdapter` 二次确认接线 → R16。

**验证缺口**：`iosMain` 在本机（Windows）无法编译，A3/A4 的 iOS 分支改动只做了源码级核对；`desktopTest` 不含 iOS target。

**余项归档**：B 级余项 → TODO **R1–R17**；C 级 → **R20–R29**。

---

## A · 阻塞发版（4 项，均为小改动）

### A1 同协程重入生命周期锁 → pause/resume 永久锁死
`MasterAgent.kt` 三个 `startXxx` 在持有各自 lifecycle mutex 时调用公开的 `stopXxx()`，而后者会再拿同一把不可重入 mutex：

- Radio：`:720` `radioLifecycleMutex.withLock` → `:733` `stopRadio()` → `:849/858` `stopRadioInternal()` → `:870` `withLock`
- Enrich：`:595` `withLock` → `:602`/`:607` `stopEnrich()` → `:688` `withLock`
- Hello：`:958` → `:972` → `:1075` 同型

可达路径明确：暂停电台（`RadioSubAgent.kt:464` 置 PAUSED、实例仍在 `_subAgents`）后再说「开电台」；日配额耗尽致 runLoop 退出后再开电台。此后所有 start/stop 永久挂起，只能杀进程。**生命周期转移零单测覆盖**，所以没被 961 例抓住。
**建议**：抽出无锁内核（`stopEnrichInternal()` 等），约定「锁内只调无锁变体」；补两条回归用例（pause→start、stop→start）。

### A2 `alwaysAllow` 可永久静默 `STRONG_CONFIRM`，且 UI 对所有项默认勾选
链路三段合成一个数据丢失面：

1. `PolicyGuard.kt:46-52` Phase 0 命中 alwaysAllow 即 `AllowSilent`，**优先于** Phase 2 的 `STRONG_CONFIRM → RequireConfirm`（`:70` 的注释还写着「硬规则，不随信任松绑」，代码与自身注释矛盾）
2. `ChatViewModel.kt:251-254` 把 `ConfirmItem` 组装时**丢弃 `permissionLevel`**，且 `selected = true` 全批预选
3. `CompanionBubble.kt:225-243` 对每一项一律显示「总是允许」；`DialogConfirmGateAdapter.kt:31` 设计的二次敏感确认全仓未接线（死代码）；撤销 API `MasterAgent.kt:1674-1684` 为 internal 且零调用方，设置页只显示条数

结果：一次「照做」即可让 `playlist_delete`（全库唯一 STRONG_CONFIRM 工具）永久静默放行，不可撤销、不可见。
**建议**：Phase 0 前置 `level == STRONG_CONFIRM` 不进白名单短路；ConfirmItem 透传级别，高危项默认不选中且不显示「总是允许」；撤销列表接出来。

### A3 Agent 启用开关不落盘，重启后自动复活
`AgentPolicyConfig.enabled`（`AgentPolicy.kt:63`，默认 `true`）被 `MasterAgent.kt:614/736/974/1512` 真实消费，但三端 DataStore 适配器都没有对应 key —— 以 androidMain 为例，`SettingsRepositoryImpl.android.kt:63-68` 只有 trustLevel / alwaysAllow / temperature / runtimeParams / promptOverrides / preferredLang 六个键，`:367` 构造时不传 `enabled`，`:377` 保存时也不写。
**后果**：用户关掉电台/富化/Hello，重启即恢复启用，继续消耗其 API 额度并写库。属知情同意问题。
**建议**：补 `agent_policy_${role}_enabled` 键，三端各一处。

### A4 降级或未知版本号 → 静默清空全库且无提示
`fallbackToDestructiveMigration(dropAllTables = true)` 三端都在（`DatabaseBuilder.{android,desktop,ios}.kt`），它同时置 `requireMigration=false` 与 `allowDestructiveMigrationOnDowngrade=true`。

先纠正一个前提：**v7.1.0 用户库的 `user_version` 是 1 不是 7**（master 侧 `AppDatabase.kt` 一直 `version = 1` 且 `exportSchema=false`），所以真实升级是 1→9 全链；迁移 DDL 本身经逐条重放比对与 `9.json` **无差异**，升级路径是安全的。
风险在另一侧：**任何 `user_version > 9` 的库**（beta 回退、从新设备备份恢复、新库拷回旧安装）会被**静默 drop 全部表**，播放列表 / 听歌统计 / Agent 画像记忆全没，且三端都没注册 `RoomDatabase.Callback`，无日志无提示。7.2 把用户长期记忆放进同库，代价比历史上任何一版都高。
**建议**：去掉对降级方的兜底（改 `fallbackToDestructiveMigrationOnDowngrade` 语义为拒绝启动并提示），至少加 `onDestructiveMigration` 回调提示 + 引导导出备份。

---

## B · 合入后必修

| # | 位置 | 问题 |
|---|---|---|
| B1 | `AppDatabaseMigrationTest.kt:27-269` | 只测 1→2、5→6、6→7；本次新增 **7→8、8→9 零测试**，也无 1→9 全链用例 |
| B2 | 同上 `:114-135` | 假测试：Room KMP 首次查询才开库，用例只取 DAO 引用即断言「可被 Room 打开」，校验从未执行 |
| B3 | `ToolCallExecutor.kt:177` | `catch (Exception)` 吞 `CancellationException` → 取消后继续执行剩余写工具并记审计；同文件族 `LlmCallExecutor.kt:74` 写法正确，属漏改 |
| B4 | `ReActLoop.kt:98-179` | 无 `try/finally`：取消路径丢 `onSessionComplete`（alwaysAllow 不落盘）；`:162` 工具已执行、`:165` 才熔断，且 tool 轮次不回传历史（`ChatAgentGateway.kt:231` 只映射 user/agent 文本）→ 下一轮模型看不到「已执行」，**可能重复执行写工具** |
| B5 | `AgentScheduler.kt:69-70,108-127` | `agents`/`states` 普通 MutableMap 无同步，仲裁循环每秒跨线程遍历 → 一次 CME 就让仲裁协程死亡且 `arbitrationStarted` 仍为 true，**全进程 pause/resume 永久失效且不可重启** |
| B6 | `AgentConfigScreen.kt:188,266` × `MasterAgent.kt:110,1548` | `stepBudget` 用户可改可持久化，但 Master 恒取 `EngineDefaults` → 设置项完全无效（`runtimeParams` 无消费方） |
| B7 | `HelloSubAgent.kt:1834` | `stateFlow` 为构造期一次性求值的 `MutableStateFlow`，此后无人写回 → `capability_status` 与监控页的 Hello 恒显示构造瞬间状态，「活跃子 Agent 数」少 1 |
| B8 | 曲库不可信输入 → prompt | `EnrichPrompts.kt:86`、`RadioSubAgent.kt:1280`、`PlaylistTools.kt:31,56` 裸插标题/艺人/歌单名，工具 `summary` 原样回灌上下文，无定界与不可信标记；`EnrichResponseParser.kt:243` AI 自由文本**无长度上限**直存 DB 并成为后续所有 Agent 的载荷 |
| B9 | Radio / Enrich 写路径不过闸 | `RadioSubAgent.kt:411/425/965/1139` 直连 `playbackPort` 改队列，`EnrichSubAgent.kt:545-566` 直写标签/extra，均无 PolicyGuard、无 ConfirmGate；Radio 的审计只记 `logRadioStart`（`:383`），队列改写零审计 |
| B10 | 日志泄露曲库 | `EnrichSubAgent.kt:535`、`EnrichResponseParser.kt:85,283`、`RadioSession.kt:434`、`RadioSubAgent.kt:1416` 为 **WARN 级**，三端 release 不过滤 Warn，内容含模型响应原文 200–600 字符与歌名 |
| B11 | iOS 密钥 | `SecureStorageHelper.ios.kt:57-75` 实为 XOR 伪加密 + 明文 key 文件落 `Documents/.keys`（与密文同目录、同进 iCloud 备份），全仓无 `SecItem`/Keychain 调用，`kotlin.random.Random` 非 CSPRNG。Android=AES/GCM+KeyStore，Desktop=AES/GCM 但 keystore 口令硬编码。属 v5.10 既有债，但 7.2 新增 per-Agent 多份 key，暴露面成倍 |
| B12 | 首轮主线程聚合 | `ChatAgentGateway.kt:213-274` + `ChatViewModel.kt:239-245`：首轮在 `viewModelScope`(Main) 同步跑 `ensureProfileReadyForFirstTurn` + 全库快照 + `getUserUsageAnalytics` |
| B13 | `MusicRepositoryBase.kt:1039-1076` | 画像刷新把 `musicLabel` 全表 + 全量已删曲目拉进内存 groupBy（注释称「已改 SQL GROUP BY」的方法恰是漏改的那个） |
| B14 | i18n | `values-th` 454/789 与默认英文逐字节相同（泰文区命中率 42%）；`shared-ui/src/androidMain/res/values*` 仍是 composeResources 的旧平行副本、未并入 317 新键且 12 语言缺 `karaoke_lyrics`；**无校验脚本/单测/CI** |
| B15 | 中文直供 UI | `RadioModels.kt:26` + `RadioSubAgent.kt:310/319/336/346/811/1476` 的 `actionText` → `HelloCardRadioStatus.kt:259,342` 上屏；`HelloSubAgent.kt:1665,1694-1697,907,1038` 兜底中文模板 → `HelloCardSingleTrack.kt:236` 等（LLM 未配置时必然命中）；`HelloGreetingProfiles.kt:57-66` 60 条中文保底文案；`HelloCardNarrative.kt:101` 直接 `Text` |
| B16 | `AgentConfigScreen.kt:161-1018` | 单个 composable 承载六分区 + 30 个 `remember` + 校验 + 持久化 + 直改 `masterAgent` 活实例；且 `ConfigFormLeftColumn:1028-1290` 把五张卡**又写了一遍**（`:537/:1081`、`:595/:1120`、`:665/:1186`）双写 |
| B17 | 撤销链路 | `AgentNoticeBar.kt:55` `onUndo` 全仓未传入，`AppRoot.kt:278-285` 唯一分支 `showUndo=false` → 组件挂载但永不显示的死组件；审计只存 `argsHash` 不存参数与旧值，`AuditLogViewModel.getAll()` 全表载入，撤销在数据层面不可能还原 |

---

## C · 记录待办（择要）

- 死抽象：`ToolRegistryView` 全类零消费（注入到三个 SubAgent 后 `SubAgent.kt:39` 只存不读），`Capability.start/stop` 生产零调用，`continueRadio`(`RadioSubAgent.kt:494-513`) 自陈无调用点，`ALL_BATCH_B` 无引用
- 注释/口径漂移：`ToolNames.kt:57`「29 个」（实为 29 基础 + `capability_status` = 30）、`ToolCatalog.kt:20`「34 实例=26+8DJ」（dj_* 全仓 0 命中）、`SharedModules.kt:140` 称 iOS 未提供 `AgentKeepAlivePort`（`IosModules.kt:73` 已提供）
- 结构：`MasterAgent.kt`(1751) 六职责混装（含 `init` 里 `runBlocking` 读 DataStore）、`EnrichSubAgent.runLoop` L211-378(167 行/31 分支)、`RadioSubAgent.startRadio` L277-390(113 行/4 处队列写入)、`HelloCardCoverflow` L90-373(283 行/45 分支)
- 无界增长：`token_ledger` 有意永久保留（16MB/年估）但 `deleteAll()` 无调用方、看板 `sumByAgent(0L)` 全表扫；证据表只增不减，`evidenceDao.getAll()` + N+1 非事务写
- 断点两套机制并存（`LocalWindowSizeInfo` 5 页 vs `BoxWithConstraints` 自算的 RadioConsole/Coverflow）；`AgentNoticeBar.kt:82-83` 两档同值 520.dp
- `plurals` 未实现而 47 个 `%1$d`+名词模板存在，ru/ar 语法必然错
- `PlaybackHistory` 无索引而本次新增十余条 `WHERE playedAt>=…` 聚合 —— **趁 v9 未进 master 加索引并重导 9.json，可免开 v10**
- `getMusicIdListByType` 新默认 `LIMIT 100` 且无 `ORDER BY`，未显式传 limit 的调用点被静默截断
- 测试盲区：生命周期转移、并发重入、`ToolCallExecutor` CE 吞没、仲裁循环并发；Hello 4 例全为「零依赖构造不崩」，`pauseResume_transitionsRunState` 还把错误语义写进了断言
- 工程：`checkVersion` 补 `notCompatibleWithConfigurationCache`；`android/app/build.gradle.kts:43-44` 的 `51000`/`"5.10.0"` 兜底应改为直接失败

---

## 复核与否决记录

主审逐条读码核实，据此调整了分片给出的级别：

- **已确认**：A1（读到 `:720`→`:733`→`:870` 与 enrich/hello 同型）、A2（读到 Phase 0 早于 `:70` 且 `:34` 注释自相矛盾 + `ChatViewModel.kt:251` 丢级别）、A3（读到 androidMain 六键无 enabled）、A4（三端 builder 一致）、B3、B6、B7、B11、B15、B17
- **降级**：分片称「`ToolRegistryView` 未生效 ⇒ F2 权限过滤是装饰、Radio 绕过身份门 ⇒ 阻塞」。核实结论：三个 SubAgent **实际不调用 LLM 工具**（`toolRegistryView.` 全仓零命中，SubAgent 未消费），真正的工具执行只走 Master 的 ReActLoop→ToolCallExecutor→PolicyGuard 一条闸。故 per-agent 工具白名单属**死抽象（C）**而非权限漏洞；Radio 直连端口改写队列记为 **B9 审计缺口**（播放队列非用户资产，不等同删歌单）
- **否决**：分片提出的「三端 DataStore key 冲突」一条，经复核不成立；`HelloCardCoverflowTuning.kt` 非调试残留（被 Coverflow:92 / Stack:214 / HomeScreen:168 引用）

## 发布说明需改口

见 ROADMAP v7.2.0 条目本次同步修订的 6 处（F12 分账维度、F11 落地范围、14 语言口径、中文清零、伙伴设置页分区数、「全量落地」措辞）。
