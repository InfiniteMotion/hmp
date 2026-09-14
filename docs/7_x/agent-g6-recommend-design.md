# G6 设计规格：首页双推荐页（每日推荐 + 私人推荐）

> 状态：设计已对齐（2026-09-14），待实现
> 关联看板：`docs/7_x/agent-w-gaps.md` §G6

## 1. 背景与目标

**旧方式（待替换）**：首页"今日推荐"区消费 `RecommendationViewModel.heartbeatList`，其生成逻辑为「随机抽 1 首 `dailyMusic` + `getSimilarSongsByWeightedLabels` 加权相似度拼 11 首」。问题：无个性化、无内容消费价值、与 agent 体系脱节。

**新目标**：把首页电台卡右侧的两个入口，对接为两个**由 `HelloSubAgent` 生成**的推荐页，给用户更有价值的内容消费（agent 选曲 + 每首 AI 按语 + 场景总述）。

## 2. 整体架构

入口位置（**已按现状代码校正**，2026-09-14）：首页堆叠卡下方 **区域②** 是一个 `Row`——左侧 `RadioCard`（电台开/关 1:1 开关卡），其右侧 `Column` 内是**两个预留卡片入口**（现为 `PlaylistEntryCard` 占位）：

```
首页 HomeScreen · 区域②（堆叠卡下方 Row）
├── RadioCard（左 · 电台开/关，G6 不改动）
└── Column（右 · 两个入口卡）
    ├── 入口 A「🎵 今日推荐」 → 每日推荐页（Routes.DailyRecommend）
    └── 入口 B「❤️ 最近收藏」 → 私人推荐页（Routes.PrivateRecommend）
```

- 两个页面**同构**：UI 结构一致，实现上抽一个共用页面组件，参数化「数据源 StateFlow + 标题 + 总述生成」。
- 列表本体**不在首页内联**，首页入口卡仅做入口（露数量/按语预览），点击进二级页看完整列表。
- ⚠️ 区域②在**横屏与竖屏两处布局各有一份**（`HomeScreen.kt:143-191` / `232-280`），改动需同步两处。

## 3. 两个页面定义

| 维度 | 每日推荐页 | 私人推荐页 |
|------|-----------|-----------|
| 数据来源 | `HelloAgent` 每日主动选曲 | 用户**收藏曲目**（**新增 DAO 批量取 liked**，当前仅逐首 `getLikedStatus`）+ **收听数据**（`getRecentPlaybackHistoryGlobal` / `getRecentPlayRate`，均已存在） |
| 种子 | agent 选 **1 首**（与滑动卡 `RECOMMEND` **同源**，见 §4） | 从收藏 + 收听数据中选 **1 首**（与每日一致，单点扩散） |
| 扩列方式 | `HelloSubAgent` 内部「类似 radio 的扩列」 | 同左 |
| 刷新节奏 | 每天更新一次 | 每天更新一次（独立） |
| 与滑动卡关系 | **同源去重**（见 §4） | 独立，不参与同源 |
| 路由 | `Routes.DailyRecommend` | `Routes.PrivateRecommend` |

## 4. 生成机制与去重

- **扩列由 `HelloSubAgent` 内部实现**：以种子曲目为起点，用曲库相似度/标签匹配「类似 radio」地一次性产出 N 首连贯列表（**不复用 `RadioSubAgent`**，仅思路类比）。每首列表项调用 `reasonForTrack()` 生成按语。
- **每日推荐 ↔ 滑动卡 `RECOMMEND` 同源**：`HelloAgent` 每天只为「每日推荐」选曲**一次**——选出的 1 首种子既作为滑动卡 `RECOMMEND` 的展示内容（门面：1 首 + 理由），又作为二级页每日推荐列表的扩列种子（展开：同 1 首 + 扩列 N 首）。两处是同一套推荐的不同粒度，用户同日看到连贯、不割裂。
- **每日刷新挂载点**：两个列表的生成挂入 `HelloAgent.dailyRefreshOnce` 每日流程，保证每天各更新一次、不重复打 LLM。
- **目标数量**：建议约 8–10 首（旧为 11，可调）。

### 4.1 记忆复用（HelloMemory）——复用已有能力，不另造

`HelloSubAgent` 本就带记忆协调层 `HelloMemory`（跨卡/跨天协调 + 去重），G6 两个列表**直接吃它**：

**读侧（生成时注入）**
- 每日推荐选种子/扩列：复用 `HelloMemory.buildContextForCard("RECOMMEND")` 已有的协调文本（「昨日 RECOMMEND 已推歌曲 - 今日尽量避开」+「今日 DISCOVER 已提 label - 避开」）；并硬排除 `getYesterdayRecommendSongIds()`。
- 私人推荐：把记忆里的**近期偏好信号**作为画像补充——`getWeeklyForgottenArtists()`（7 天随笔歌手）、`getWeeklyDiscoverLabels()`（7 天探索 label）、今日 `recommendArtists/Labels`；扩列时避开，避免与 agent 其他卡重复。

**写侧（生成后回写，让记忆覆盖列表）**
- 两个列表生成后把其 `songIds / artists` **`record` 回 `HelloMemory`**：
  - 次日 `getYesterdayRecommendSongIds()` 能覆盖列表 → 不重复推荐；
  - 其他卡（DISCOVER / FORGOTTEN / GREETING）的 `buildContextForCard` 会避开列表已用歌手 / label。

**存储：复用 `HelloCardCache`（不新建表，取代原 §9「新建 `recommend_list_cache`」）**
- 两个列表各作为一条 `HelloCardCache` 记录：新 cardType `RECOMMEND_LIST_DAILY` / `RECOMMEND_LIST_PRIVATE`；`cardContentJson` 存列表 JSON；`recommendSongIds` / `recommendArtists` 填列表曲目 / 歌手。
- **收益**：持久化 + 记忆 + 去重**一次到位**——`cardCacheDao.insert` / `deleteSameDaySameType` / `getTodayAllEntities`（`loadToday` 自动重建缓存）全部复用。
- 需小改：`HelloMemory.TodayCache.mergeWith` 的 `when(cardType)` 增加两个列表分支（累加 `recommendSongIds` / `recommendArtists`，与滑动卡 `RECOMMEND` 同桶以最大化去重）。

> ⚠️ 待定小项：私人推荐列表并入 `recommend*` **同桶**（最大去重，倾向）vs 单独分桶（保留"收藏向"独立语义）。

## 5. 内容模型

列表项数据结构（新增）：

```kotlin
enum class RecommendSource { DAILY, PRIVATE }

/** 持久化存储单元（轻量：只存 id + 文案，避免整存 MusicInfo）——写入 Room */
data class RecommendItemRecord(
    val trackId: Long,
    val reason: String,
    val source: RecommendSource,
    val phase: TimePhase?,      // 生成时段（用于总述/展示）
)

/** UI 展示模型：读时由 getMusicInfoByIds(ids) hydrate 出 MusicInfo */
data class RecommendItem(
    val musicInfo: MusicInfo,   // 曲库实体（含 id/title/artist/cover）
    val reason: String,         // 每首按语（reasonForTrack 生成，LLM 缺失有模板兜底）
    val source: RecommendSource,
    val phase: TimePhase?,
)
```

- 持久化只落 `RecommendItemRecord`（`trackId` + `reason`），**不整存 `MusicInfo`**（避免大对象 JSON）；UI 侧 hydrate。
- `source` 用 **enum** 而非 `String`，避免拼写漂移。

**内容三要素（已对齐）**：
1. **音乐列表** —— `List<RecommendItem>`
2. **每首按语** —— `RecommendItem.reason`（有温度，结合时段/历史/歌词；模板兜底不空）
3. **顶部总述** —— 由 `HelloAgent` 生成的场景文案（如「傍晚通勤，给你几首慢下来的歌」），每页一次 LLM 调用

## 6. 兜底与状态（已对齐 + 三态补充）

入口与页面按**三态**呈现（避免"首屏生成期"被误当无数据）：

| 状态 | 判定 | 首页入口 | 二级页 |
|------|------|---------|--------|
| **生成中** | 今日尚未生成完 / 正在打 LLM | 显示但**置灰不可点** + "生成中…"（或骨架） | 骨架屏 / loading |
| **无数据** | 生成完成但为空（曲库空 / agent 不可用） | **不显示或不可点**，不跳转 | 空态引导 |
| **有数据** | 列表非空 | 正常显示（可露数量/按语预览） | 完整列表 + 按语 + 总述 |

- **不降级**到旧随机相似度列表（保持"无数据即不引导"的干净语义）。

## 7. 首页入口

- **位置**：区域② `Row` 内、`RadioCard` 右侧的两个 `PlaylistEntryCard`（见 §2）。
- 两个入口分别导向每日推荐页 / 私人推荐页——现有卡片已带 `onClickDetails`（当前跳通用 Playlist 路由），**天然可改接新路由**；`onClickPlay` 改为播放 agent 生成的列表（替换现在的 `addAllToPlaylistInOrder(heartbeatList)`）。
- 有数据时入口可展示预览信息（建议：推荐数量 + 一条按语预览）。
- 无数据时该入口不显示/不可点（见 §6）。
- **命名（已定）**：「❤️ 最近收藏」入口卡**改名为「❤️ 私人推荐」**。

## 8. 交互（初步建议，待细化）

- 列表项点击：**整组入队播第一首**（推荐默认）；备选：播单首 / 进歌详情。
- 页面提供「播放全部」按钮（整组入队）。
- 支持加收藏 / 加歌单（若现有组件已有此能力，复用）。

> ⚠️ 点击交互的具体行为属待细化项，实现前可在二级页布局细化时一并定。

## 9. 实现影响面

| 模块 | 改动（✅ 已实现，2026-09-14） |
|------|------|
| `SlideModels.kt` | 新增 `RecommendSource` / `RecommendItemRecord` / `RecommendListPayload` / `RecommendItem` + `RecommendSource.cardType()` |
| `HelloSubAgent` | 新增 `buildRecommendList` / `refreshRecommendList` / `restoreRecommendLists`（扩列复用 `getSimilarSongsByWeightedLabels`，排除集用记忆的昨日已推）；暴露 `dailyRecommendList` / `privateRecommendList`（`StateFlow<RecommendListPayload?>`）；挂入 `dailyRefreshOnce` + 启动恢复；生成后 `record` 回写记忆 |
| `MasterAgent` | 新增 `dailyRecommendList()` / `privateRecommendList()` 访问器 |
| `MusicRepository` / DAO | 新增 `getLikedMusicIds()`（`UserInfoDao.getLikedMusicIds` + 两个 Fake 同步） |
| `HelloMemory` | `mergeWith` 的 `when(cardType)` 增加两个列表分支（songIds/artists 并入 `recommend*`） |
| 持久化（**复用，非新表**） | 两列表各存一条 `HelloCardCache`（cardType `RECOMMEND_LIST_DAILY` / `RECOMMEND_LIST_PRIVATE`），复用 `cardCacheDao` 的 insert / deleteSameDaySameType / getTodayAllEntities，**跨重启当天不重算** |
| 路由 | `Routes.Recommend.Daily` / `Routes.Recommend.Private` + `HmpNavBackStack` subclass 注册 + `NavigationGraph` entry |
| 新建页面 | `shared-ui/.../library/pages/RecommendListScreen.kt`（共用；列表 + 每首按语 + 顶部总述 + 三态） |
| ~~新建 VM~~（**实现调整**） | **不新增 VM**：推荐页直接消费 agent 的 StateFlow（agent flow 即真相源），页面内 `produceState` 里 `getMusicInfoByIds` hydrate 出 `MusicInfo`。避免转发型样板 VM |
| `HomeScreen` | 区域②两入口改为 `RecommendEntryCard`（三态包装 `PlaylistEntryCard`，后者新增 `emptyText` 参数）；`onClickDetails` 跳新路由、`onClickPlay` 播 agent 列表 |
| `RecommendationViewModel` | **整体保留**（enrich 桥接 / 待处理数 / 收听时长仍被 AIScreen / UserScreen / IntroScreen 使用）；仅删除 `heartbeatList` 字段及 init 相似度生成逻辑 |

## 10. 验收标准

- [x] 每日推荐页展示 `HelloSubAgent` 生成的列表 + 每首按语 + 顶部总述（代码完成，真机待验）
- [x] 私人推荐页展示基于收藏/收听数据生成的列表 + 按语 + 总述（代码完成，真机待验）
- [x] 两页每天各更新一次（挂 `dailyRefreshOnce` + `HelloCardCache` 持久化保证跨重启不重算）
- [x] 无数据时对应首页入口不显示/不可点，不跳转（三态）
- [x] 点击列表可播放（整组入队播第一首 + 播放全部）
- [x] 旧 `heartbeatList` 随机逻辑已废弃
- [x] 统一编译通过：`:shared:compileKotlinDesktop` + `:shared-ui:compileKotlinDesktop` + `:shared:desktopTest`（含 G13 HelloMemoryTest）

## 11. 技术假设 / 待实现确认

- ✅ **`HelloSubAgent` 内部扩列手段（已核实）**：`MusicRepository.getSimilarSongsByWeightedLabels(musicId, limit)`（`MusicRepository.kt:74`）在仓库接口上，`HelloSubAgent` 持有 `musicRepository`，**可直接调用**——扩列不需新造查询。
- ✅ **同源挂载点（已核实）**：`dailyRefreshOnce`（`HelloSubAgent.kt:253`）本已 `generateRecommendCards(phase, count).firstOrNull()` 取单张 RECOMMEND 卡，其 `trackId` 即每日推荐种子。
- ✅ **持久化范式（已核实）**：`cardCacheDao.insert` + `deleteSameDaySameType`（`:290-292`）可照抄给两个列表。
- **二级页布局细节（唯一仍需细化）**：用户要求"仔细设计"，本规格仅定三要素与初步交互，具体视觉布局（按语长短、是否分组、总述位置）实现前细化。

### 11.1 实现注意（review 补充）

- **扩列排除规则**：种子作为列表**首项**；扩列结果**去重**并排除种子本身（避免首项重复）。
- **LLM 成本控制**：每页"总述"与列表生成**同批**执行（同一协程/上下文），避免每页独立多打一次；每首 `reasonForTrack` 复用现有内核（含模板兜底）。
- **同源一致性**：每日推荐列表首项 == 滑动卡 `RECOMMEND` 所展示曲目（同一次选曲），用户同日两处看到连贯内容。
- **记忆复用**：生成前用 `HelloMemory.buildContextForCard(...)` 注入协调文本 + 硬排除集（昨日推荐 / 7 天 label·artist）；生成后把两个列表 `record` 回写，纳入次日与其他卡的去重（详见 §4.1）。

## 12. 决策记录（对齐过程）

| 决策点 | 结论 |
|--------|------|
| 列表放哪 | 放二级页（不内联首页） |
| 页面数量 | 两个同构页（每日推荐 + 私人推荐） |
| 每日推荐种子 | `HelloAgent` 选 1 首，与滑动卡 `RECOMMEND` 同源 |
| 私人推荐来源 | 用户收藏曲目 + 收听相关数据 |
| 扩列实现 | `HelloSubAgent` 内部"类似 radio"扩列，**不复用 `RadioSubAgent`** |
| 刷新节奏 | 两页都每天一次 |
| 内容 | 列表 + 每首按语 + 顶部总述 |
| 总述来源 | `HelloAgent` 生成场景文案（非时段占位） |
| 兜底 | 无数据 → 入口不显示/不可点，不跳转 |
| 去重 | 每日推荐与滑动卡 `RECOMMEND` 同源一次选曲 |
| **入口位置**（2026-09-14 校正） | 堆叠卡下方**区域②**：`RadioCard` 右侧两个 `PlaylistEntryCard`，**不在 RADIO_STATUS 卡内** |
| **入口命名** | 入口 A「🎵 今日推荐」→ 每日推荐页；入口 B 由「❤️ 最近收藏」**改名「私人推荐」** → 私人推荐页 |
| **私人推荐种子源** | **新增 DAO 批量查询**取全部收藏曲目，叠加收听数据（`getRecentPlaybackHistoryGlobal` / `getRecentPlayRate`） |
| **列表持久化**（更新） | **复用 `HelloCardCache`**（新 cardType `RECOMMEND_LIST_DAILY/PRIVATE`）落 Room，保证跨重启当天不重算；**不新建表** |
| **记忆复用** | 读侧注入 `HelloMemory` 协调上下文（昨日避开 / 7 天 label·artist 避开）；写侧生成后 `record` 回写，让次日与其他卡都避开 |
| **入口/页面状态** | 三态：生成中(置灰/skeleton) / 无数据(隐藏/空态) / 有数据(正常) |
| **数据模型** | 持久化只存 `RecommendItemRecord`（trackId + reason）；`source` 用 enum |
| **VM 策略**（实现调整 2026-09-14） | **不新增 VM**——推荐页直接消费 agent 的 StateFlow（已是真相源），页面内 hydrate MusicInfo；入口显隐由 `HomeScreen` 直接读 payload 三态 |
| **扩列实现（已核实）** | 复用 `MusicRepository.getSimilarSongsByWeightedLabels`，`HelloSubAgent` 可直接调 |

## 13. 实现记录（2026-09-14）

### 改动文件
- `shared/.../agent/sub/SlideModels.kt`：`RecommendSource` + `RecommendItemRecord` + `RecommendListPayload` + `RecommendItem` + `RecommendSource.cardType()`
- `shared/.../agent/sub/HelloSubAgent.kt`：`buildRecommendList` / `refreshRecommendList` / `restoreRecommendLists`；两个 StateFlow；挂 `dailyRefreshOnce` + 启动恢复
- `shared/.../agent/sub/HelloMemory.kt`：`mergeWith` 两个列表分支
- `shared/.../agent/runtime/MasterAgent.kt`：两个访问器
- `shared/.../domain/music/MusicRepository.kt` + `data/repository/MusicRepositoryBase.kt` + `data/database/Music.kt`：`getLikedMusicIds()`
- `shared/src/commonTest/.../fakes/{FakeMusicRepository,AgentToolFakes}.kt`：实现补齐
- `shared/src/commonTest/.../HelloMemoryTest.kt`：补 `import kotlin.test.Test`（G13 编译修复）
- `shared-ui/.../common/navigation/{Routes,HmpNavBackStack,NavigationGraph}.kt`：两个路由
- `shared-ui/.../library/pages/RecommendListScreen.kt`：**新增**共用推荐页
- `shared-ui/.../library/pages/components/PlaylistEntryCard.kt`：新增 `emptyText` 参数
- `shared-ui/.../library/pages/HomeScreen.kt`：区域②两入口改 `RecommendEntryCard`（三态）+ 播放接线
- `shared-ui/.../settings/viewmodel/RecommendationViewModel.kt`：删 `heartbeatList` + init 相似度生成

### 关键实现决策
- **不引入转发型 VM**：agent StateFlow 已是真相源，页面直接消费。
- 每日推荐种子与滑动卡 `RECOMMEND` 同为「按时段 label 取第一首」→ 天然同源。
- 私人推荐种子 = `getLikedMusicIds().firstOrNull() ?: getRecentPlayRate(30d).firstOrNull()`。
- 扩列不足 `recommendListSize(=8)` 时，用当前时段 label 列表兜底补齐。
- 记忆排除集从 `Set<String>` 转 `Set<Long>` 后比较（坑）。

### 验证
`:shared:compileKotlinDesktop` ✅ · `:shared-ui:compileKotlinDesktop` ✅ · `:shared:desktopTest` ✅

### 沉浸式 v2（2026-09-14 傍晚，另一次迭代）

用户参照 **Apple Music iOS 26.4 改版**（全屏封面出血 + 列表背景取封面呼应色 + 内容优先去 chrome），要求页面更有冲击力。

**改动**
- `ThemeViewModel` 新增 `suspend fun paletteFor(albumArtUri): PaletteColors`——对任意封面取色，**不写全局** `_paletteColors`（复用 pixelsLoader / analyzeColors / 50 条 cache）。
- `RecommendListScreen.kt` 重写为沉浸式：
  - **英雄区**（LazyColumn 首项，`fillParentMaxHeight(0.58f)`）：种子曲封面全出血 + 同色系压暗渐变（`lerp(palette.background, Black, 0.5f)`，4 段 stops）+ 悬浮圆形返回钮 + 底部「小标签 / 时段大标题 / AI 总述 / 白色胶囊『播放全部』」。
  - **过渡带**：56dp `heroScrim → pageBg` 渐变，让封面溶进页面。
  - **列表**：去掉卡片底色，改间距 + 0.5dp 细分隔线；按语 `maxLines 3`；页面底 `pageBg = lerp(background, palette.primary, 0.06f)`（封面色轻微影响整页）。
  - 大标题 = `phaseTitle(TimePhase?)`（映射放 shared-ui 表现层；DAILY 用时段词、PRIVATE 用「为你而选」）。
- 未做（下一步）：滚动视差收起 hero + 粘性小标题；行内操作；当前在播高亮。

**环境提示**：本机可用内存低时 Gradle daemon 会被 OS 静默杀死（`daemon disappeared`）。用 `--no-daemon -Xmx1536m -Dkotlin.daemon.jvm.options=-Xmx1536m -Dorg.gradle.parallel=false` 可编译通过（详见 `~/.workbuddy/MEMORY.md`）。
