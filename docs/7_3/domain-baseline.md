# 领域施工基线（v7.3.0）

> **状态**：全量审查已完成（九域 D1–D9 + 横切线 X + v10 迁移设计，共 109 条发现，2026-09-30）。**施工条目尚未落地**——`taskbook/` 现在只有 README。本文件是后续施工的唯一入口。
> **取证日期**：2026-09-29 工作树（`feature/architecture-hardening`，= v7.2.2 已发布代码）

---

## 零、定位

**这是一份独立重读的产物，不是任何既有审查报告的续作。**

- 本基线的内容**全部来自对本仓库代码的实读**，不从 `review-7.3-architecture.md`、`review-7.3-code.md`、`TODO.md` 的既有条目派生、归置或裁决。
- 那两份文档保留在原地（它们是带日期的历史证据，不改写），但**不进入本基线的输入集**：不引用其编号、不沿用其分类、不继承其结论（包括它们自陈为真的那些）。
- 若本轮独立重读撞出与旧报告相同的现象，**视为互证**，照常立案编号，不因"已经报过"而合并或跳过。反过来，本轮读不出来的旧条目，也不会因为"有人报过"就自动继承。
- 本基线填完后即驱动施工。域章节是唯一工作分解来源。

---

## 一、覆盖论证（先证明没有域是空的、没有东西没人管）

域的边界按**代码里的实际入口**划定，不按模块目录（`shared` / `shared-ui`）切——按模块切会切断一条功能链（"歌词"横跨 domain、shared-ui、三端引擎、iOS Swift）。

下面三组入口是从工作树里直接数出来的，全部有归属：

**18 张表**（`shared/schemas/.../9.json`）
`music` `musicExtra` `userInfo` `musicLabel` → D2 ｜ `playlist` `playlist_item` → D3 ｜ `PlaybackHistory` `listeningDuration` `user_profile_evidence` `user_profile_portrait` `user_profile_narrative` → D5 ｜ `agent_task` `agent_audit_log` `agent_message` `hello_card_cache` `hello_report_narrative` `forgotten_delivery` `token_ledger` → D6

**13 个 `@Dao` 宿主文件**（`shared/src/commonMain/.../data/database/`）
`Music` `UserProfile` `PlaybackHistory` `MusicLabel` `ListeningDuration` `PlayList` `PlaylistItem` `HelloAgentDao` `AgentTask` `AgentAuditLog` `AgentMessage` `TokenLedger` `ForgottenDelivery`

**29 个 `*Screen.kt`**（`shared-ui/src/commonMain/`）
按 §二 的域定义分配；`TabScreen` / `SubScreen` / `GalleryScreen` / `CustomScreen` / `IntroScreen` 属公共层与首启，归 D8 / D9。

> **未归属清单**在施工过程中维护：任何一个 `.kt` 若找不到域，写进该清单而不是硬塞进某个域。

---

## 二、九个域的定义

> 每域给出：UI 入口 / 数据入口 / 三端实现点。**这三组就是该域的审查范围。**

### D1 播放引擎与控制
队列、seek、播放速度、gapless、音效、睡眠定时、媒体键 / 锁屏 / 通知 / Live Activity。
- 屏：`PlayerScreen`（播放页）、`AudioEffectsScreen`
- 三端实现点：Android `MusicPlayService`（Media3）｜Desktop `DesktopMusicController` + `FFmpegAudioEngine`｜iOS `PlayerEngine.swift` + `MusicPlayerController.swift`
- 共享契约：`shared-ui/.../ui/platform/PlaybackController`（三端实现同一接口，差异收口点）

### D2 曲库扫描与元数据
扫描目录、格式白名单、标签解析、封面、软删除与恢复、排序。
- 屏：`ListScreen` `AlbumScreen` `ArtistScreen` `SearchScreen` `SongDetailScreen` `EditMusicTagsScreen` `LibrarySettingsScreen`
- 表：`music` `musicExtra` `userInfo` `musicLabel`
- 三端实现点：`DeviceMusicScanner`（expect/actual 三份）｜`MusicTagParser`（Android/Desktop 走 JAudioTagger，iOS 经 Swift 桥 `MusicMetadataParser` / `ArtworkExtractor`）｜封面取像素（Coil / Skia）

### D3 歌单
CRUD、拖拽排序、智能歌单、导入导出。
- 屏：`PlaylistScreen` `PlaylistManageScreen`
- 表：`playlist` `playlist_item`

### D4 歌词
侧车 `.lrc`、内嵌歌词、悬浮歌词、歌词设置。
- 屏：`LyricsScreen`
- 三端实现点：`LrcParser`（commonMain）｜内嵌歌词解析（三端各自的元数据通道）｜Android 悬浮歌词（`FloatingLyricsOverlay`）｜歌词设置读写路径

### D5 统计与画像
听歌历史、时长统计、热力图、用户画像、报告卡。
- 屏：`UserScreen` `UserUsageDataScreen`
- 表：`PlaybackHistory` `listeningDuration` `user_profile_evidence` `user_profile_portrait` `user_profile_narrative`

### D6 AI Agent 运行时
Master / Radio / Enrich / Hello 四个 Agent、工具注册与执行、确认闸门、策略与权限、token 计量、审计与撤销、每日推荐与刷新策略。
- 屏：`ChatScreen` `AIScreen` `AgentConfigScreen` `AgentMonitorScreen` `AuditLogScreen`
- 表：`agent_task` `agent_audit_log` `agent_message` `hello_card_cache` `hello_report_narrative` `forgotten_delivery` `token_ledger`
- 关键类型：`MasterAgent` `RadioSubAgent` `EnrichSubAgent` `HelloSubAgent` `ToolRegistry` `ToolCallExecutor` `PolicyGuard` `DialogManager` `AgentScheduler` `ReActLoop`

### D7 设置、备份恢复、迁移、密钥
设置读写、备份与恢复、Room 迁移链、API Key 存储。
- 屏：`SettingScreen` `BackupSettingsScreen` `ProfileSettingsScreen`
- 三端实现点：`SettingsRepository`（三端 `*Impl`）｜`SecureStorageHelper`（三端）｜`BackupFileRepository`｜`AppDatabase` 迁移链 1→9（`shared/schemas/1..9.json`）

### D8 UI 与导航、设计系统、i18n、无障碍
导航图与路由、屏幕公共层、主题、设计 token 落地、多语言、无障碍。
- 公共层：`TabScreen` `SubScreen` `GalleryScreen` `CustomScreen`（**其余所有屏的排版与 token 使用也归本域的横查项**）
- 技术点：`Routes` / `HmpNavBackStack` / `NavigationGraph`｜`composeResources` 多语言｜`docs/spec/hmp-design.md` 与实际实现的差

### D9 平台原生层与生命周期
三端各自的壳、权限、后台存活、分享、打包注入。
- Android：`android/app`（入口）+ 通知 / MediaSession / 权限 / 分享
- Desktop：`Main.kt`（无边框窗口、托盘、单实例、退出时序）+ FFmpeg 二进制注入
- iOS：Swift 原生壳（`ios/HMP/**`）、`AppDelegate` 桥注册、`HMPNowPlaying` 扩展、权限与后台

---

## 三、施工批次（这是施工顺序，不是审查顺序）

排序依据是**能否合并成一次 v10 迁移**。域审查很可能挖出新的 schema 需求（索引、字段、持久化语义），散在多批做等于把迁移拆成多次。

| 批次 | 域 | 先做的理由 |
|---|---|---|
| 批一 · 数据面 | D2 / D3 / D5 / D7 | 先定完，schema 需求并进 v10 迁移（当前库 `version=9`） |
| 批二 · 运行时 | D1 / D4 / D6 | |
| 批三 · 平台与界面 | D8 / D9 | 依赖前两批的目标状态 |

**横切线单列，不进域**：构建脚本与拓扑、CI 与闸门、发版与产物链、依赖矩阵、文档真值、`storybook` 去留。这批不属于任何功能域，混进域章节会让每个域都背一份工程债、永远排不动。横切线的条目单独成文，编号 `X-{序号}`。

---

## 四、每个域章节的固定结构

写进 `docs/7_3/domain/D{n}.md`：

1. **功能清单 × 三端对齐矩阵**——该域承诺了什么 vs 三端实际有什么（应有 / 部分 / 空壳 / 未做）。**价值最高的产物**：横切视角看不见"某个域整体缺一半"。清单来源取 README / ROADMAP / 设计规范 / UI 上实际可见的入口。
2. **目标状态**——做完之后这个域应该是什么样。没有终点，施工就只有缺陷清单没有收敛条件。
3. **本轮发现**，编号 `D{n}-{两位序号}`。S1 必须亲验到 `file:line`。
4. **四项横查**：数据正确性 / 并发与生命周期 / 可观测性 / 测试覆盖。
5. **验证缺口**——需 macOS 实机、需真机的，单列并写明谁能验。
6. **刻意不做**（带改判条件）。

---

## 五、编号与判据规则

- 编号：`D{域号}-{两位序号}`（例 `D2-01`）；横切线用 `X-01`。
- 每条写全四项：**现状（符号名 + 当日坐标）→ 影响 → 动作 → 判据**。
- **判据优先写成某条断言，并写明落在哪个测试源集**：`:shared:desktopTest` / `:shared-ui:testAndroidHostTest` / `commonTest`。
- 落不进测试源集的（实机、打包、iOS 运行期），写明**谁在什么机器上验、验完看什么**。
- **没有判据的条目不进施工队列。**
- 可拆成动作的执行条目放 `docs/7_3/taskbook/D{n}-{序号}.md`；本文件与域章节只留结论与判据。

---

## 六、方法纪律

- 行号是当日工作树坐标，**动手前按符号名复验**。
- **计数不作判据**。任何"N 处 / N 个"只作量级描述，且必须写明 grep 口径；不写进散文当结论。
- **正面结论与缺陷结论同一套验收标准**。"这块做得好"同样要亲验，不凭印象抄。
- 机械排除 `build/` 生成码与 KSP 产物。
- 每条结论要么标**亲验**（含 `file:line`），要么标**待验**（写明验证条件）。中间状态不写。

---

## 七、域章节索引（全部已落，2026-09-30）

- `docs/7_3/domain/D1.md` 播放引擎与控制
- `docs/7_3/domain/D2.md` 曲库扫描与元数据
- `docs/7_3/domain/D3.md` 歌单
- `docs/7_3/domain/D4.md` 歌词
- `docs/7_3/domain/D5.md` 统计与画像
- `docs/7_3/domain/D6.md` AI Agent 运行时
- `docs/7_3/domain/D7.md` 设置、备份恢复、迁移、密钥
- `docs/7_3/domain/D8.md` UI 与导航、设计系统、i18n、无障碍
- `docs/7_3/domain/D9.md` 平台原生层与生命周期
- `docs/7_3/domain/X.md` 横切线（构建 / CI / 发版 / 依赖 / 文档真值）
- `docs/7_3/v10-migration.md` 批一收敛：v10 迁移设计（schema 增量 + 门禁 + 依赖）

---

## 八、批状态

| 批次 | 域 | 状态 | 产物 |
|---|---|---|---|
| 批一 · 数据面 | D2 / D3 / D5 / D7 | **已完成**（2026-09-29） | `domain/{D2,D3,D5,D7}.md` + `v10-migration.md` |
| 批二 · 运行时 | D1 / D4 / D6 | **已完成**（2026-09-30） | `domain/D1.md` / `domain/D4.md` / `domain/D6.md` |
| 批三 · 平台与界面 | D8 / D9 | **已完成**（2026-09-30） | `domain/D8.md` / `domain/D9.md` |
| 横切线 | X 系列 | **已完成**（2026-09-30，不进域） | `domain/X.md` |

> **全量审查已完成（2026-09-30）**：九域（D1–D9）+ 横切线（X）+ v10 迁移设计全部落地。下一步是按 `v10-migration.md` 与 `taskbook/` 把「目标状态 + 判据」翻译为可执行施工条目，并在 `version=9` 上统一收口 v10。
> **归口状态（2026-09-30）**：本基线已接为 v7.3.0 的唯一工作分解入口——`AGENTS.md` §十、`docs/README.md`（三处索引 + 目录树 + 推荐阅读）与 `TODO.md` §一/§六 均已指向本文件；`TODO.md` §六 的 R39–R56 改作**证据台账**，两份审查报告的对照表与三条待裁项见 `TODO.md` §六 文末。
> **下游产物**：[plan.md](plan.md)（v7.3.0 施工计划，**已评审**）——把本文件的 §三 批次与 109 条发现切成 34 个工作包并定义验收，**是施工顺序的真源**；施工开始后条目落 `taskbook/`。

> 批一结论已收口进 v10：**schema 增量以 `v10-migration.md` 为唯一真源**；批二若挖出新 schema 需求，必须并入 v10 或另开 v11，禁止在 `version=9` 上偷加字段。

---

**© 2026 Hearable Music Player · 领域施工基线**
