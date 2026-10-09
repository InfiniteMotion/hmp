# v7.3.0 施工计划

> **状态**：**已评审通过**（2026-09-30，用户「计划先过」）。本文件是 v7.3.0 的**施工顺序真源**；§九 的 8 条决策定完后，条目原件按 `taskbook/` 规则拆出。
> **输入**（均为唯一真源，本文件不复述内容）：[domain-baseline.md](domain-baseline.md)（覆盖论证 / 九域定义 / 批次 / 判据规则）、`domain/D{1..9}.md` + `domain/X.md`（109 条发现的现状/影响/动作/判据）、[v10-migration.md](v10-migration.md)（schema 增量唯一真源）。
> **纪律**：**没有判据的条目不进队列**（本次核对：109 条全部有判据，无孤儿）。本文件只做**范围、排序、切包与验收定义**，不改写任何判据 —— 判据一律指回域章节，避免二次转述造成漂移。
> **口径**：文中的条目数为量级描述，口径 = `domain/*.md` 里 `^### (D\d+|X)-\d+` 标题计数（2026-09-30 实测 **109**）。

---

## 一、范围

### 进 v7.3.0

| 批次 | 域 | 收口条件 |
|---|---|---|
| 前置（批 0） | — | 迁移测试骨架 + `ALL_MIGRATIONS` 常量：给 v10 装上"会响的警报器" |
| 批一 · 数据面 | D2 曲库 / D3 歌单 / D5 统计画像 / D7 设置备份迁移 | 四域的 schema 需求**收口为一次** `version 9→10` 迁移 |
| 批二 · 运行时 | D1 播放引擎 / D4 歌词 / D6 AI Agent | 可测部分落本机测试源集；iOS 部分列实机清单 |
| 批三 · 平台与界面 | D8 UI 导航设计系统 / D9 平台原生层 | 含导航注册真闸门与 i18n 覆盖率门禁 |
| 横切线 X | 构建 / CI / 发版 / 依赖 / 文档真值 | 只做**成本低且为其他批提供安全网**的项 |

### 不进 v7.3.0

- **方向 C**（播放速度 / Gapless / ReplayGain / 交叉淡入 / Desktop 音效实现 / 格式白名单扩展）：未排期，D1-10 与 D2-09 已登记，改判条件在域章节。
- **F10 语音会话**：挂起（`AGENTS.md` §六），遇相关请求直接回拒。
- **存量整改类**：D8-03 的设计 token 存量收敛、X-07 的静态闸门全量格式化 —— 本版只落**约定 + 增量守门**。
- **X-01 CI 编译与测试**：已决议「当前环境能跑就都跑」，但用户 2026-09-30 决定**暂缓**（清单留档在 `TODO.md` §六 决议 ①）。
- 各域"刻意不做"共 **51 条**（含 X），清单见 §八。

### 队列规模

| 归类 | 条数 | 说明 |
|---|---|---|
| 进工作包 | 104 | 见 §三 / §四 / §五 |
| 正面确认 / 明确不入队列 | 5 | D2-04（原文明确不入）、D4-06（刻意不做）、D6-07、D8-05、X-02 —— 均为"已验正确/仅记录"，无施工动作 |
| 合计 | 109 | |

---

## 二、验收定义

### 单个工作包

1. 包内每条发现的判据都走完 **红 → 绿**：先能证明它当前红，再改到绿。判据文本在域章节，不在此复述。
2. 判据落点属于 `:shared:desktopTest` / `:shared-ui:testAndroidHostTest` / `commonTest` 的，**必须在本机跑过**，且用 §七 的强制重跑命令。
3. 判据标"待验/落不进测试源集"的**不阻塞包完成**，但必须登记进 §六 实机清单，写明谁在什么机器上验、验完看什么。
4. 动了 schema 的包：`10.json` 已由 KSP 重导并入库；三端 `addMigrations` 均引用同一常量；迁移用例覆盖 `9→10`。

### v7.3.0 全局（可发的判据）

- `./gradlew-lowmem.bat preflight` 绿（= `checkVersion` + `checkReleaseConsistency` + `testAll`）。
- v10 迁移在**三端**都注册；库版本高于代码时有可见提示而非静默失败（D7-10）。
- `AppDatabaseMigrationTest` 覆盖 `1→10` 全链；**故意写坏一条 DDL 会红**（当前不会，见 §三）。
- §六 实机清单中标注"阻塞发布"的项已由对应负责人签收；其余项带已知缺口发布，缺口写进 ROADMAP。

---

## 三、前置 · 批 0（必须先绿，在批一之前）

| 包 | 内容 | 条目 | 判据 | 依赖 |
|---|---|---|---|---|
| **P0-1** | 迁移测试补环：补 `7→8`、`8→9`，再加一条 `createDatabase(1)` 跑全链到 9 | D7-09 | `:shared:desktopTest` 出现这四段用例；故意写坏一条 DDL 必红 | — |
| **P0-2** | 修假用例 `migratedDatabase_canBeOpenedByRoom`：改为先跑一次真实 DAO 查询再断言 | D7-09 | 同上（它是"DDL 写错也绿"的那一条） | P0-1 |
| **P0-3** | `AppDatabase` 暴露 `ALL_MIGRATIONS`，三端 `addMigrations(...)` 引用它 | v10 §三.1 | 抽掉任一端引用后，回归用例能发现（而不是靠人记） | P0-1 |
| **P0-4** | 本机基线（2026-09-30 首测；批 0 后于 2026-10-08 复测） | — | 复测 **968 用例** / 119 套件 / 全绿；命令见 §七 | — |

> **批 0 状态（2026-10-08）：P0-1 / P0-2 / P0-3 已完成**，M0 判据达成。施工结果与两处判据收窄记在 `domain/D7.md` D7-09 的「施工结果」段（条目未拆进 `taskbook/` —— 该包三步在同一天内做完并回填，按 `taskbook/README.md` §一「做完就删文件」的口径，建一个即删的文件只留漂移面）。
> 顺带修掉一处测试基建缺陷：`AppDatabaseMigrationTest` 原先 8 个用例共用一个 `build/migration_test.db`，Windows 上一个用例失败就会让后续用例继承旧库、报出与自身无关的错。现每实例唯一文件名。

> **P0-1 的顺序硬约束（本次实测得出，`v10-migration.md` 未写）**：`10.json` 是 `runMigrationsAndValidate(10, …)` 的比对基准，必须**先**由 KSP 导出并入库，**再**写迁移用例。正确顺序为：改 `@Entity` + 写 `MIGRATION_9_10` → 构建导出 `10.json` → 写用例。反过来会先红在"找不到 `10.json`"。该条应写进批一-1 的 taskbook 条目。

---

## 四、批一 · 数据面（D2 / D3 / D5 / D7 + v10）

切包依据：判据落在同一测试源集，或**必须同批改动**（否则迁移白做）。v10 §四列出的"随迁移一起修的逻辑"已并入对应包。

| 包 | 内容 | 含条目 | 判据落点 | 依赖 |
|---|---|---|---|---|
| **一-1** | **v10 schema 增量**：13 条索引（9 条 D2 + 2 条唯一 D3 + 2 条 D5）+ 删 `playlist_item.songUrl`；迁移内先去重再建唯一索引 | D2-07、D3-03(索引段)、D3-05(索引段)、D3-15、D5-11(索引段) | `:shared:desktopTest`（`AppDatabaseMigrationTest`）：`PRAGMA index_list` 含预期索引、`table_info(playlist_item)` 不含 `songUrl`、行数前后相等、`EXPLAIN QUERY PLAN` 走索引而非 `SCAN TABLE`；`10.json` diff 含 `music.indices` 五项 | P0-1..3；**§九 决策 1、2** |
| **一-2**（**已完成，分 A/B 两批**） | **事务与写入安全**：批量写收口 `withTransaction`、导入前安全副本、`catch` 复抛 CE、重扫不抹软删、改扫描目录不再静默破坏性重扫 | D2-01、D2-02、D2-03、D3-03(事务段)、~~D3-04~~(归 一-4)、D5-12(动作①，②待决策)、D5-13、D7-02 | `:shared:desktopTest`：中途 CE 后不出现半空库；第 3 步失败则前 2 步回滚；重扫后已隐藏曲仍在；恢复后历史条数不翻倍 | 一-1 |
| **一-3**（**进行中：C1 + C2 已完成 2026-10-09**） | **时间与时区口径**：小时桶统一本地时区、「全部」时段不再被夹成 1 天、`days` 作窗口、`playDuration` 三端口径、热力图名实、文案带范围、窗口查询失败可见 | D5-01、D5-02、D5-03、D5-04、D5-05、D5-06、D5-07、D5-08、D5-09、D5-10、D5-11(窗口段)、D5-14、D3-01 | `:shared:desktopTest`（新增 `HourBucketParityTest` 等）+ `:shared-ui:testAndroidHostTest`（VM 级窗口断言、`formatHourRange`）；其中 D5-04、D5-05、D5-06、D5-07 的 iOS/Desktop 段为实机 | 一-1（索引） |
| **一-4** | **歌单一致性与派生字段**：按 id 而非按名删除、`itemOrder` 入 domain 模型、派生字段同事务重算、系统歌单自愈按 id、智能歌单配置生效、Agent 入参校验对齐 UI、上移下移接线 | D3-02、D3-04、D3-05(删改段)、D3-06、D3-07、D3-08、D3-09、D3-10、D3-11、D3-12、D3-13、D3-16、D3-17 | `:shared:desktopTest`（`PlaylistRepositoryImplTest` / `PlaylistDaoTest`）+ `:shared:commonTest`（`AgentToolsTest`）+ `:shared-ui:testAndroidHostTest`（`PlaylistViewModel`）；其中 D3-09、D3-10、D3-13 的 iOS/三端段为实机 | 一-1、一-2 |
| **一-5** | **曲库扫描与元数据**：搜索转义与 album 匹配、排序键枚举化、格式白名单统一常量、死码处置、iOS 元数据与标签写入、Windows 路径分组、封面命名与缓存 | D2-05、D2-06、D2-08、D2-09、D2-10、D2-11、D2-12、D2-13、D2-14、D2-15、D2-16、D2-17、D2-18 | `:shared:desktopTest`（`MusicDaoTest` / `MusicRepositoryBaseTest` / 新增 `MusicScanPersistTest`）+ `:shared-ui:testAndroidHostTest`（`SearchViewModel` 防抖、`LibraryViewModel`）；其中 D2-10、D2-11、D2-12、D2-14、D2-16 为实机 | 一-1（索引）、**§九 决策 7** |
| **一-6** | **设置、备份与密钥**：Android 恢复后密钥解不开要降级不崩、`rememberSaveable` 不落明文、备份路径约束、旧设置链的可靠性、Android 备份读写切 IO、快照覆盖面与 `version` | D7-01、D7-03、D7-04、D7-05、D7-06、D7-07、D7-08、D7-11、D7-12 | `:shared:desktopTest` + `:shared:commonTest`（`BackupSnapshotRoundTripTest`）；其中 D7-01、D7-03、D7-05、D7-06 的 iOS/Android 段为实机 | 一-2 |
| **一-7**（**已完成编码 2026-10-09，实机签收未做**） | **降级守卫**：`user_version > 9` 时不崩、记日志、给可见提示 | D7-10 | 构造 `user_version = 11` 的库打开：不崩 + 日志含 `dbVersion` + UI 出现版本过高提示；三端各一次（实机） | 一-1；**§九 决策 4** |
| **一-8** | **结构（可选，建议延后）**：三端 `PlaylistRepositoryImpl` / `SettingsRepositoryImpl` 抽公共基类 | D3-14 | 现有 `PlaylistRepositoryImplTest`(17 例) / `SettingsRepositoryImplTest`(57 例) / `PlaylistDaoTest`(17 例) 全绿 | 一-1..一-4 全绿后 |

> **一-1 已完成（2026-10-08）**：13 条索引 + 删 `songUrl` + `MIGRATION_9_10` + `10.json` 入库全落，判据走过红→绿，并在真实库副本上演练过（`v10-migration.md` §八 记了结果与三处偏离）。
> 两处需要后续包接手的：~~**`addToPlaylist` 并发现在会撞索引**（一-2 的优先级由"防半空库"升为"防崩溃"）~~ —— 已由一-2 A 批收口；**`musicPath` 死参数**与按名删除（一-4）。
> 顺带：`music(title/artist/album)` 三条 B-tree 索引对现有 `LIKE '%q%'` 搜索无效，别把 D2-05/搜索性能记成"已随索引解决"（详见 `domain/D2.md` D2-07 施工结果）。

> **一-2 拆成两批做（2026-10-08）**：**A 批已完成** —— 止血的部分全部可本机验，落在 `:shared:desktopTest` + `:shared:commonTest`：
> 歌单写路径收进 DAO 事务（D3-03 事务段，含一-1 遗留的"并发添加撞索引"崩溃）、统计恢复改整表替换不翻倍（D5-13）、
> 导入前先落 `pre-restore-` 副本 + Room 侧三条 restore 收进一个事务（设置那条走 DataStore 排在最后）+ 取消复抛（D7-02 / R39）。
> 结果与两处判据口径收窄记在 `domain/D3.md` D3-03、`domain/D5.md` D5-13、`domain/D7.md` D7-02 的「施工结果」段。
> **B 批已完成（2026-10-09）**：扫描流水线（D2-01、D2-02、D2-03）与画像批量写（D5-12 动作①）全部本机验完 ——
> 落点 `:shared:desktopTest`（`MusicScanPersistTest` 7 例、`UserMemoryIntegrationTest` 新增 2 例）+ `:shared-ui:testAndroidHostTest`（`LibraryScanPolicyTest` 4 例）。
> 三条探针都过：清空重灌→4 红、旁路事务→事务那条红、批量退逐条+旁路事务→两条新判据同时红。
> 一处 schema 后补：`userInfo.removedByUser` 随 §2.2b 并进未发布的 v10（`10.json` 已重导）。
> **仍留的口子**：D5-12 动作②（证据保留窗口）要先定"哪些证据可以删"，属产品口径，不在判据里；
> 设置页「已移除」列表的口径变更（只列用户移除的）见 `domain/D2.md` D2-02 施工结果。
> 一-1/一-2 的**真实大库验证**仍在 §六 阻塞发布清单里（那是签收动作，不是本包的编码前提）。
> D3-04（重排入参完整性）与 `musicPath` 死参数要改返回类型/签名，仍归 一-4。

> **一-3 分三组做（2026-10-09 起）**：**C1 已完成** —— 时间与窗口口径（D5-01、D5-02、D5-03、D5-11 的查询改写段），
> 落点 `:shared:desktopTest`（新增 `HourBucketParityTest` 9 例）+ `commonTest`（`WindowMappingTest` 3 例）；
> 探针：同时退回 UTC 自算 / `coerceAtLeast(1)` / 全表÷N 三处 → 精确 5 红、其余 4 绿（信号按发现分离）。
> 三处判据口径的收窄与一条限制（UTC 主机上时区断言自然相等）记在 `domain/D5.md` D5-01..03 的施工结果段。
> **C2 已完成（同日）**：热力图名实（D5-08，按推荐 (a) 真做日历热力图）、`UserScreen` 范围标签（D5-09）、
> 峰值时段文案（D5-10）、窗口查询失败与加载态可见（D5-14）。落点 `shared-ui` 的 commonTest（纯函数 8 例）+ androidHostTest（VM 4 例，mockk）。
> 两处如实记的收窄：D5-09 判据后半要"断言渲染出范围文案"，本模块没配 Compose UI 测试基建，改为"范围来自 30 天窗口"+ 签名约束，
> **所以 UserScreen 的渲染那半仍未被机器看住**；D5-14 判据的另一种写法（可注入 log sink）也没做，`HmpLog` 是全局 object。
> 探针：抹掉星期对齐 + 还原小时文案 + 让失败标记不落地 → 精确 4 红，其余 8 绿。
> **C3 未动**：`playDuration` 三端口径与来源、iOS 计时停摆、Desktop 退出落库（D5-04..07）。
> **另需一次决策**：D3-01（iOS 时间戳是 2001 纪元）的**存量数据**要不要在迁移里按偏移量加回来 ——
> 改的是用户库里的历史时间戳，判据只能实机验，我不替用户决定要不要动这些数据。

> **一-1 的两个前置决策**（不做完不能动迁移）：存量 `itemOrder` 去重策略、`playlist.name` 同名链与系统歌单豁免。见 §九 决策 1/2。

---

## 五、批二 · 运行时（D1 / D4 / D6）与批三 · 平台界面（D8 / D9）+ 横切线

### 批二 · 运行时

| 包 | 内容 | 含条目 | 判据落点 | 依赖 |
|---|---|---|---|---|
| **二-1** | 歌词单一存储源：读写收敛 `getComponentConfig` / `resolveConfig`，修 LRC 重复时间行与双语拆分 | D4-01、D4-02、D4-05 | `:shared:desktopTest` / `commonTest`（`LyricsSettingsUseCaseTest` / `LrcParserTest`）+ `:shared-ui:testAndroidHostTest`（端到端） | — |
| **二-2** | 歌词渲染：自动滚动居中、Desktop/iOS 悬浮空壳（实现或按平台隐藏） | D4-03、D4-04 | `:shared-ui:testAndroidHostTest`（位置断言，做不到则实机）+ 实机 | 二-1 |
| **二-3** | Agent 生命周期与并发：`close()` 取消 scope、`pauseRadio/resumeRadio` 持锁、`_subAgents` 并发容器 | D6-02、D6-03、D6-04 | `:shared:commonTest`（`F11LifecycleTest` / `MasterAgentRadioTest` 同族） | — |
| **二-4** | Agent 排序键与确认闸门：`"play_count"` 拼错静默错排、`DialogManager` 用毫秒当 key | D6-01、D6-08 | `:shared:commonTest` / `:shared-ui:testAndroidHostTest` | — |
| **二-5** | `token_ledger` 的手动清理入口 + 监控页改窗口化聚合（**不自动清理**，与 `TokenLedger.kt:19` 的"永久保留"注记一致） | D6-05 | `:shared:desktopTest`：插大量行 → 调手动入口后 `count()` 归零；断言监控页取数 `sinceMs > 0`（不再传 `0L` / 不再走裸 `sumTotal()`） | 无（索引早已存在，**无 schema、无迁移**，见 §九 决策 8） |
| **二-6** | 每日推荐触发时机 | D6-06 | `:shared:commonTest`（注入 `TimeProvider` 前进 1 天） | — |
| **二-7** | 播放引擎可测部分：Desktop 退出时序、seek 误报错误、启动恢复异常空 catch（Desktop 段） | D1-04、D1-05、D1-08(Desktop 段) | `:shared:desktopTest` | — |
| **二-8** | 播放引擎 iOS 空壳（以实机为主） | D1-01、D1-02、D1-06、D1-07、D1-09、D1-08(iOS 段) | 实机（macOS + iOS）—— 本机不可达 | 需 iOS 设备 |
| **二-9** | 音效空桩：实现 or UI 禁用 | D1-03 | `:shared-ui:testAndroidHostTest`（适配器不崩）+ 实机听感 | **§九 决策 5** |
| **二-10** | 播放增强登记（无施工） | D1-10 | 无判据（随方向 C 启动时补 `PlaybackController` 方法） | 方向 C |

### 批三 · 平台与界面 + 横切线（可并行的安全网）

| 包 | 内容 | 含条目 | 判据落点 | 依赖 |
|---|---|---|---|---|
| **三-1** | **导航注册真闸门**：补 2 条 serializer；把恒真断言换成"路由 ↔ entry ↔ serializer"遍历断言 | D8-01、D8-02、X-05 | `:shared-ui:testAndroidHostTest`（覆盖 commonTest）：删键/漏注册即红 | — |
| **三-2** | `Lang.resolvePrompt` 未知键静默空串 → 告警 + 回落 | D8-04 | `:shared-ui:testAndroidHostTest` | — |
| **三-3** | **i18n 覆盖率门禁**：键集合与占位符断言；androidMain 旧副本去留；未译率可见 | D8-06 | `:shared-ui:testAndroidHostTest`：删 `values-de` 一个键或改坏 `%1$s` 即红 | — |
| **三-4** | 设计 token 硬约定 + 例外白名单（**只做增量守门**） | D8-03 | 编译 + 评审 + `compileAll`；机械门禁归 X-07 | X-07 |
| **三-5** | iOS 平台层空壳：Live Activity 不在构建图、标签写入桥未注册、DMG 版本号硬编码 | D9-01、D9-02、D9-03 | 实机（macOS + Xcode）；D9-03 本机可跑打包后 `mdls` | — |
| **三-6** | Android 壳层：deep-link 声明无处理、`allowBackup` 与备份面 | D9-04、D9-05 | 实机（Android）；静态核查可本机 | — |
| **三-7** | Desktop 壳层：主题监听线程、单实例不聚焦（产品取舍） | D9-06、D9-07 | 实机（macOS/Windows） | — |
| **X-闸** | DI 模块图校验：`:shared:desktopTest` 接 `koin-test` 的 `checkModules` | X-04 | 删掉任意一条 `single` 即红 | — |
| **X-testAll** | `testAll` 去掉不存在的 `:shared-ui:desktopTest` 依赖 | X-09 | `testAll` 不再有 NO-SOURCE 空壳依赖 | — |
| **X-版本** | 版本读不到时的危险默认改 fail-fast（`android/app` 的 `51000`、`desktop/app` 的 `1.0.0`/`"1"`、`copyToReleases` 缺件不失败） | X-03 | 删掉 `hmp.versionName` 后构建**硬失败**；`releases/` 缺件时任务失败 | — |
| **X-目标** | `maybeDepends` 在错目标下静默零工作 → fail-fast | X-10 | `HMP_BUILD_TARGET=desktop ./gradlew testAndroid` 不再静默绿 | — |
| **X-其余** | 静态闸门 / storybook 去留 / 依赖矩阵 / 平台源集覆盖 | X-06、X-07、X-08、X-11 | 见域章节；**§九 决策 6** | 决策 6 |

> **三-1 已完成（2026-10-08）**：两条 serializer 补齐，恒真断言删除，遍历式闸门落在
> `shared-ui/src/androidHostTest/.../NavRegistrationGateTest.kt`（多态往返 + `entry` 覆盖，30 = 26 entry + 4 Tab 豁免）。
> 实测：无人注册的新路由会被精确点名；`:shared-ui:testAndroidHostTest` 93 例 / 0 失败。
> X-05 的**运行期那半**（真机"不保留活动"后回栈恢复）仍留在 §六 实机清单。施工记录与两个反射陷阱见 `domain/D8.md` D8-01 / D8-02。
>
> **闸门段进度（2026-10-08）**：**X-闸 已完成，且从 1 条扩成 3 条**，覆盖两端真实装配组合 ——
> `shared/src/desktopTest/.../KoinGraphVerificationTest.kt`（领域/数据层）、
> `shared-ui/src/desktopTest/.../DesktopKoinGraphVerificationTest.kt`（桌面完整装配）、
> `shared-ui/src/androidHostTest/.../AndroidAppKoinGraphVerificationTest.kt`（Android 完整装配）。
> 全用 koin-test `verify()` 静态校验（`checkModules` 会实例化定义 → 会打开本机 `~/.hmp` 真库，故弃用）。
> 实测信号：删 `single<MusicAllDao>` → `:shared` 那条红；删桌面侧 `single<PlaybackController>` → 桌面那条红且 Android 那条不受影响。
> **两个如实记录的覆盖边界**（详见 `domain/X.md` X-04 施工结果）：① 带默认值的构造参数被静态校验当成可缺省（删 `single { PresenceBus() }` 三条全绿，而运行期 `chatPresenceBus = get()` 照样抛）；② iOS 装配拼不出、`MainActivity` 里 `loadModules` 动态注册的 `PlatformServices` 看不见。
> **X-testAll 已完成，并按新事实修正**：先摘 `:shared-ui:desktopTest`（当时目录不存在），补另一半时发现 `shared-ui/build.gradle.kts` 早已声明该源集、只缺目录 —— 于是建出目录并把 `testAll`/`testUi` **重新挂回**，`testUiDesktop` 因此保留（不再是空绿陷阱）。
> 同类残留**已同日处理**：`desktop/app` 建出 `src/desktopTest/` 并放 `SingleInstanceGuardTest`（单实例锁 4 例，实测 `release()` 不释放会精确红一条），`testAll` 挂的 `:desktop:app:desktopTest` 从此有真实内容。
>
> **三-1 / X-闸 / X-testAll 建议先做**：三者的成本都在"改几行 + 加一条断言"，但它们把后续所有包的回归风险降一档。这也是上一轮审查"先闸门再 S1"的意图 —— 本轮按 baseline §三 的批次走，但闸门段可以并行提前。

---

## 六、实机验证清单（不可自动化的部分）

口径：判据行文本里出现"待验 / 落不进测试源集"的条目，人工分类，**只作量级**。本机是 Windows ⇒ **全部 iOS 项不可达**，Android 项需设备，Desktop 项本机可跑。

> **一条常被漏掉的边界（2026-10-09，一-2 B 批）**：本机不可达不只是"实机行为验不了"，还包括 **`iosMain` 的源码连编译器都没过一遍** ——
> KMP 的 Apple 目标只能在 macOS 主机上编，`testAll` 与 `compileAll` 都不含 iOS。B 批改了 `MusicRepositoryImpl.ios.kt`
> （删掉两份落库镜像、加 `transactionRunner` 构造参数、换 `getRemovedMusicIdAndPath()`），这些改动只经过人工比对 diff。
> 凡改到 `iosMain`/`ios/**`，合入前后都要在 macOS 上补跑一次 `./gradlew :shared:compileKotlinIosSimulatorArm64`（壳层改动再加 `xcodebuild`）。

| 域 | 判据含"待验/落不进"的条目（量级） | 主要去向 |
|---|---|---|
| D1 | 8 / 10 | macOS + Xcode（D1-09）、iOS 真机（D1-01、D1-02、D1-06、D1-07）、Windows/macOS 听感（D1-03）、Desktop 本机可跑（D1-04） |
| D2 | 4 / 18 | macOS + iOS 模拟器（D2-11、D2-12、D2-14、D2-16）、Android 设备（D2-10）、Windows（D2-13） |
| D3 | 4 / 17 | macOS + iOS 模拟器（D3-01、D3-09、D3-10、D3-13） |
| D4 | 1 / 6（另有 5 项在 §五 验证缺口） | macOS 实机 / iOS 模拟器（D4-01、D4-03、D4-04） |
| D5 | 3 / 14（另有 6 项 V1–V6） | iOS 真机（D5-05、D5-06）、Desktop 退出（D5-07） |
| D6 | 0 / 8（§五 列 4 类环境） | macOS / Windows / iOS 实机 |
| D7 | §五 单列 9 项 | iOS 真机 4 项、Android 真机 3 项、三端各一次（D7-10）、macOS（D7-05 Desktop 侧） |
| D8 | 2 / 6 | 三端真机走查（无障碍、毛玻璃） |
| D9 | 7 / 7 | macOS + Xcode、Android 实机、Windows |
| X | 5 / 11 | 运行期 save/restore（X-05）、CI（X-01，已暂缓）、静态闸门（X-07） |

**阻塞发布的项**（建议在 v7.3.0 发布前签收）：D7-10（三端降级守卫 —— 守卫逻辑与三端接点已落，缺的是三端各一次的启动实测）、D3-01（iOS 纪元）、D5-05（iOS 时长停摆）、一-1 与一-2 的真实大库验证（D2-01、D2-02）。其余可带缺口发布，缺口写进 ROADMAP。

---

## 七、本机测试与陷阱

- **唯一守门人是本机**（CI 已暂缓，`AGENTS.md` §一.5）。
- **强制重跑命令**（2026-09-30 实测，必须记牢）：

  ```bash
  ./gradlew-lowmem.bat :shared:desktopTest --rerun --no-build-cache
  ```

  理由：`gradlew-lowmem.bat testCore` 首次执行时报 `:shared:desktopTest FROM-CACHE` + `testCore UP-TO-DATE`，**BUILD SUCCESSFUL 但零用例执行**（`org.gradle.caching=true`），连测试报告都是缓存里那份。`.md` 里的"任务 UP-TO-DATE 时加 `--rerun`"不足以覆盖 `FROM-CACHE` 这种情况。
- **本机基线（2026-10-08 复测）**：119 套件 / **968 用例** / 0 失败 0 错误 0 跳过；由 2026-09-30 的 964 例 +4 得来，+4 全部来自批 0 给 `AppDatabaseMigrationTest` 补的四条（`migrate_7_8` / `migrate_8_9` / `1→9` 全链 / 链连续性）。最慢套件 `RadioSubAgentTest` 12.55s（旧测）。
- **测试源集实况**（8 个）：`shared/{commonTest,desktopTest}`、`shared-ui/{commonTest,androidHostTest}`、`desktop/core-player/desktopTest`、`android/core-player/test`（Robolectric，51 例）、`android/app/{test,androidTest}`（模板桩）。**`:shared` 无 `androidHostTest`**：AGP 9 KMP 库插件默认关闭 host test，需 `withHostTest{}` 显式 opt-in（`shared-ui/build.gradle.kts:19-21` 有先例）。
- **iOS 侧有任务、无源集**：`:shared` 有 `iosSimulatorArm64Test` / `iosX64Test`，会带上 `commonTest` 的 764 例，但**只能在 macOS 上跑**（本机验证不可达，属待验）。

---

## 八、刻意不做汇总（不进队列，改判条件在域章节）

| 域 | 条数 | 主要项 |
|---|---|---|
| D1 | 4 | 方向 C 四项（速度/Gapless/ReplayGain/交叉淡入）、不本轮回填 iOS 标签与心动算法、不自行开迁移、不实现 Desktop/iOS 真实音效 |
| D2 | 6 | `musicLabel` 主键设计、FTS/拼音列、`year/genre/track` 入库、历史排序键逐个修补、给将死代码写测试、`ArtworkExtractor.extractAsync` 死码评估 |
| D3 | 7 | 云同步/协作、m3u 等外部格式、歌单条目上限、`songCount` 改查询时聚合、真拖拽排序、合并 iOS 播放控制器、主键外键结构 |
| D4 | 4 | 侧车 `.lrc` 查找、歌词搜索跳转、Desktop/iOS 原生悬浮歌词、legacy 扁平键下线 |
| D5 | 7 | 年度热力图、动报告卡、画像建模规则、`PlaybackHistory` 清理策略定型、三端 `source` 闭集、新增平台测试源集、两个零引用 Composable |
| D6 | 5 | 改 `AuditLogViewModel` 注册、自行开 v10/v11、F10 语音、F11 保活、`DialogManager` 非 agent 路径 |
| D7 | 7 | 在线/云同步、挂 destructive 兜底、API Key 进备份、自动备份与轮转、新增平台测试源集、Desktop keystore 位置、快照加密 |
| D8 | 4 | 代码生成式双注册、补语种、agent prompt 改 `stringResource`、强收敛存量 token |
| D9 | 4 | 重复立案 D1 已覆盖项、替 D7 定性密钥、评估 iOS 后台音频引擎细节、重写 `HMPNowPlaying` |
| X | 3 | ktlint/detekt 进 Gradle 构建图、storybook 立即处置、版本默认值 fail-fast（已并入 X-版本 包，见 §五） |

---

## 九、决策记录（2026-09-30 全部已定）

> 本节是**决策台账**。施工时以本节为准：域章节的"动作"若与本节的决议冲突，**以本节为最新**（冲突处已在对应文档加带日期的订正说明）。

| # | 决策 | 决议（2026-09-30） | 影响的下游 |
|---|---|---|---|
| 1 | 存量 `itemOrder` 重号去重 | **每个歌单内按 `(itemOrder, id)` 排序后重编号 `0..n-1`** | 迁移脚本 `MIGRATION_9_10`（`v10-migration.md` §2.3）；不丢条目、相对顺序不变 |
| 2 | `playlist.name` 唯一索引的同名链 | **唯一索引覆盖全部行；创建同名自动后缀 `_2`/`_3`…（取第一个空位）；三个系统歌单名视为保留名，用户不得占用** | 迁移脚本 §2.3 + `createPlaylist` 路径（D3-05 动作）+ D3-16 的"入口反查名"须用同一份保留名常量 |
| 3 | `PlaybackHistory` 保留窗口 | **不自动清理，只给手动清理入口** | ⚠️ 覆盖 `v10-migration.md` §2.4 原"建议 24 个月"（已订正）；D5-11 的动作"定窗口"改为"只给入口"；旧台账 R23 的"库体积不随历史线性增长"随之**作废**（索引仍做，它治的是查询而非体积） |
| 4 | 库版本高于代码时的可见路径 | **启动即检 → 阻断式页面 + 「恢复备份」入口** | 一-7 因此含一处**新增屏**（UI 工作量计入该包）；`v10-migration.md` §三.3 的"可见路径"落到该屏 |
| 5 | `D1-03` 音效空桩 | **先从 UI 撤下 Desktop/iOS 音效入口**，实现留方向 C | 二-9 的动作；与 D2-14、D7-06 的"空壳入口"处置口径统一 |
| 6 | `X-08` storybook 孤儿模块 | **原地保留，声明为设计沙盒**（不恢复 `include`、不改代码、不保证可编译） | ⚠️ 覆盖 X-08 的"删除"建议。声明已落地：`AGENTS.md` §三 + `DEVELOP.md` 模块结构各加一行 |
| 7 | `D2-10` Android 扫描器死码 | **删除 `DeviceMusicScanner.android.kt`**（iOS/Desktop 的 actual 保留） | 一-5 的动作；判据含编译 + `testUi` + Android 模拟器一次"全量重建" |
| 8 | `token_ledger` 的增长与清理 | **不自动清理 + 给一个手动清理入口**（2026-09-30 最终口径） | **无 schema 增量、无迁移**：`token_ledger(created_at)` 索引在 `9.json` 里已存在（`index_token_ledger_created_at`，另有 `agent_id`）；"表无界增长"也不是缺陷——`TokenLedger.kt:19` 的设计注记写明 ~16 MB/年、**永久保留**。故 D6-05 **留在批二**（二-5），只做两件事：接线 `deleteAll()` 作手动入口、监控页改用带窗口的聚合 |

> **与建议不同的两条是 3 和 6**（以 ⚠️ 标出）：它们的共同取向是**不主动删用户数据 / 不删已有代码**，改由可见入口或文档声明兜住。若后续要改判，改判条件写回对应域章节。
> **决策 8 已闭环，无残留**：`token_ledger` 定为**不自动清理 + 手动入口**。核实过程中推翻了两条旧口径 —— ① `(created_at)` 索引早已存在，本条不需要迁移；② "无界增长是缺陷"不成立，`TokenLedger.kt:19` 的设计注记写明 ~16 MB/年、永久保留。**真正要修的是监控页**：`AgentMonitorScreen.kt:120` 传 `0L`、`:206` 走无 `WHERE` 的裸 `sumTotal()`，两次都是全历史聚合。订正已落 `domain/D6.md` D6-05。

---

## 十、风险与已知陷阱

| 风险 | 影响 | 处置 |
|---|---|---|
| **构建缓存假绿** | 以为回归通过，实则零用例执行 | 一律用 §七 的 `--rerun --no-build-cache`；不得凭"BUILD SUCCESSFUL"下结论 |
| **无 CI ⇒ 无机器反馈** | 改完代码没人拦 | 每个包合并前跑对应任务（`testCore` / `testUi` / `compileAll`）；发版前 `preflight` |
| **v10 是硬失败路径** | A4 已去掉 destructive 兜底：迁移写错 = 单端升级崩 | 批 0 必须先绿；迁移用例 + 三端注册 + `ALL_MIGRATIONS` 三者缺一不可 |
| **iOS 面几乎不可验证** | D1/D2/D3/D5/D7/D9 合计 20+ 项的判据落不到本机 | §六 清单显式登记；发布前至少签收"阻塞发布"那 4 项 |
| **测试覆盖结构性缺口** | `:shared` 无 `androidHostTest`、无 `iosTest`；`androidMain`/`iosMain` 实现覆盖 0 | 本轮不强推（各域刻意不做已记载）；X-06 只做决策与增量 |
| **闸门未建就动结构** | 与上一轮审查同样的教训 | 三-1 / X-闸 / X-testAll 建议提前并行做 |

---

## 十一、里程碑

| 里程碑 | 内容 | 完成判据 |
|---|---|---|
| **M0** | 批 0（迁移骨架 + 常量） | **已达成（2026-10-08）**：故意写坏 `MIGRATION_8_9` 的一个 NOT NULL → 精确 3 红；改回 8/8 绿。`1→9` 全链用例在跑，三端引用同一份 `ALL_MIGRATIONS` |
| **M1** | 批一（含 v10 迁移与四域逻辑） | **进行中：一-1 已完成（2026-10-08）、一-2 已完成（A 批同日、B 批 2026-10-09）**;一-3..一-7 待做。`10.json` 入库；三端注册；降级守卫可用 |
| **M2** | 批二（运行时） | 可测部分全绿；iOS 项进实机清单 |
| **M3** | 批三 + 横切线安全网 | 三-1..三-7 + X-闸/X-testAll/X-版本/X-目标 全绿 |
| **M4** | 发版准备 | `preflight` 绿；实机签收；走 `skills/release-prep` |

---

**© 2026 Hearable Music Player · v7.3.0 施工计划 · 草稿 2026-09-30**
