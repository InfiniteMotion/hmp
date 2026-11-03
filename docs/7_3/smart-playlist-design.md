# 智能歌单设计（v7.3 草案）

> 状态：**v5（已完成）**。D1–D4 已落代码（D4 见 §4.6）；旧生成器退役已完成（2026-11-03）。
> 已落：内部黑盒权重（每次随机偏重维度）、每日两单覆盖式持久化、模型一次调用选曲、播放页电台入口。
> 退役：`GeneratePlaylistUseCase` / `GeneratePlaylistComboButtons` / `PlayerCallbacks.onGeneratePlaylist` 等已删除。
> 相关：`domain/D3.md` D3-11、`domain/D4.md`（歌词无关）、`plan.md` §五（二-2 之后的新包）。

## 0. 已定决策

| # | 决策 |
|---|---|
| D1 | **权重是内部黑盒**：不提供任何选择器；每次生成随机抽一个偏重维度（流派/情绪/场景/年代）+ 其余维度扰动，因此每次推荐的偏重点都不同 |
| D2 | **每日两单持久化覆盖，但不做真歌单**：每日生成后写入本地存储，覆盖到下一次生成；不建 `playlist` 行、不进歌单列表 |
| D3 | **Agent 生成采用 radio 初始化同款**：本地保底先出结果，模型在候选池上编排；模型失败/额度不足回落本地 |
| D4 | **播放页生成器替换为电台区域**：`generate` tab 退役，改成电台控制区（细化见 §4） |

## 1. 现状（基线）

| 入口 | 选曲 | Agent 层 | 落盘 |
|---|---|---|---|
| 音乐伙伴页 · 每日推荐 / 私人推荐 | 本地：种子 → `getSimilarSongsByWeightedLabels()` → 时段补齐 → 跨天去重 | 只写总述与每首按语（`buildListOverview` / `reasonForTrack`） | `hello_card_cache.cardContentJson`，按 `(type, date)` 一行；启动只恢复**今天** |
| 播放页 · 生成推荐列表 | 本地：`GeneratePlaylistUseCase` → `PlaylistAlgorithms`（相似 / 心动链） | 无 | 只进播放队列，不落盘 |
| 电台 | 本地：种子标签频率 → `buildLocalFallback` | 有：模型在本地队列之后编排/补歌 | 会话级，不落盘 |

两个已知缺陷：
- 播放页权重模板 UI 存在，但 `SimilarityStrategies` 硬编码 `getDefaultWeights()` → **从未生效**（v5.6 首版即如此）
- 每日两单的 `getSimilarSongsByWeightedLabels()` 用仓库内固定 `labelCategoryWeight`，同样不吃模板

## 2. 权重模型（D1，内部黑盒）

### 2.1 每次生成一套权重（随机偏重维度）

```kotlin
data class SmartWeightSet(
    val seed: Long,
    /** 本次刻意加权的维度（每次生成随机，尽量不与上一次重复） */
    val emphasis: LabelCategory,
    val weights: Map<LabelCategory, Double>, // 已归一
)

object SmartWeights {
    // 只做轻微先验，防止某个维度被压没；不用原 BALANCED 的 4:3:2:1 差距
    val PRIOR = mapOf(GENRE 1.1, MOOD 1.2, SCENARIO 1.0, ERA 0.9)
    fun randomSeed(): Long
    fun forSeed(seed: Long, avoidEmphasis: LabelCategory? = null): SmartWeightSet
}
```

消费者（按优先级）：
1. 每日推荐（DAILY）
2. 私人推荐（PRIVATE）
3. 电台本地保底（仅**以曲目为种子**时；关键词种子没有曲目标签，仍走原标签频率）

### 2.2 算法

```text
rng       = Random(seed)
emphasis  = 在 PRIOR 的 4 个维度里随机抽一个（避开 avoidEmphasis）
boost     = U(1.8, 2.6)
weighted  = PRIOR.map { (cat, prior) ->
                prior * (if (cat == emphasis) boost else 1 + U(-15%, +15%))
            }
weights   = weighted / weighted.values.sum()
```

- **先验只负责"不把某维度压没"**，真正的偏重由每次随机抽的 `emphasis` 决定
- 因此同一首歌、同一天、连续两次生成会得到**不同的权重与不同的偏重维度**——这是设计意图
- 只有 4 个维度，`avoidEmphasis` 只能保证"尽量不连续重复"；避不开时允许重复
- 测试断言：主维度必须是最大权重；50 个 seed 至少覆盖 3 个不同主维度

### 2.3 种子来源

| 场景 | seed | 效果 |
|---|---|---|
| 每日两单 | `randomSeed()`，每次生成现取 | 同一首歌 / 同一天 / 连续两次都不同 |
| 电台一档 | `randomSeed()`，开档时取一次 | 每档不同；同一档内补歌沿用同一套权重 |

`seed` 与 `emphasis` 一起写进 `RecommendListPayload` / `RadioSession`，便于复现与排查（不展示给用户）。

## 3. 每日两单（D2 + D3）

### 3.1 生成流程（radio 式双层）

```text
① 选种子
   DAILY   : 当前时段 label 的第一首
   PRIVATE : 收藏优先，其次近 30 天高频
② 本地保底（零 LLM）
   labels   = repo.getMusicLabels(seedId)
   weights  = SmartWeights.forSeed(randomSeed(), avoidEmphasis = 上次的 emphasis)
   expanded = repo.getSimilarSongsByWeightedLabels(seedId, limit = N*2, weights)
   fill     = 时段 label 补齐；排除昨日已推（memory）
   local    = seed + expanded + fill → take(N)（N=8）
③ 曲库视图（给模型）
   全量 getAllMusicInfoAsList(PLAY_COUNT DESC) + getAllMusicLabels() 批量标签
   预算 24k token ≈ 660 行；超出则取播放次数最高的 660 行 + 强制包含本地 N 首，并注明省略数
④ 若 LLM 可用且额度未耗尽
   prompt = 情境 + 本地推荐（★，参考可整体替换）+ 曲库视图 + 任务
   返回 { overview, items:[{id, reason}] }
   校验：id 必须 ⊆（曲库 ∪ 本地）；数量不足用 local 补齐；解析失败/超时 → 用 local
⑤ 落盘（覆盖式，见 3.2）
```

- **模型只在候选池内选**，不让它自由编 id（避免幻觉与越权）
- **一次调用**同时返回总述与按语；prompt 新增 `hello.recommend.select`（ZH/EN 两份）
- 额度：走 hello 桶；`enableLlm=false` 或桶耗尽 → 直接 local，`llmUsed=false`
- 失败不阻塞：`dailyRefreshOnce` 的 runCatching 语义保留
- **token 变化（如实记）**：旧实现是「每首一次长文按语（300–500 字）+ 总述一次」≈ N+1 次调用；
  新实现 1 次，但 prompt 因为带曲库视图而变大（满预算约 24k input token）。
  两次/天 ≈ 48k token/天；free 的 hello 桶（10M 总量）约能撑 200 天，需观察实际消耗。
  按语从长文变成 ≤24 字的一句，UI 上更紧凑，但不再是"乐评短文"。

### 3.2 持久化（覆盖，不做歌单）

复用 `hello_card_cache`，把语义从"按天追加"改为"当前快照"：

- 写入前 `deleteByType(source.cardType())`，再 `insert` → **每个 type 只保留一行**
- `restoreRecommendLists()` 改用 `getLatestOfAnyDate(type)`，启动显示上次结果（哪怕不是今天）
- `generatedForDate` 保留，`dailyRefreshOnce` 仍按"今天有没有卡"决定是否重跑
- 不加 `playlist` 行、不进歌单列表；UI 仍是 `RecommendListScreen`

收益：跨重启可见、每日覆盖、无 schema 变更。
代价：不保留历史（用户未要求）；将来要历史另加 snapshot 表。

### 3.3 Prompt 结构（与实现一致）

```text
system:
  [hello.recommend.select：策展人角色]  +  [memoryBriefing ≤300 字（画像/叙事）]

user:
  ## 情境
  - 来源：每日推荐 / 私人推荐
  - 时段：…
  - 曲库：共 N 首
  ## 本地算法推荐（参考，可整体替换）
  [1] ★ id=… | 标题 — 艺人 | 标签:A/B | 播放 M 次 · 已收藏
  ## 曲库（共 N 首；截断时注明"已省略 X 首"）
  [1] id=… | 标题 — 艺人 | 标签:A/B | 播放 M 次
  ## 任务
  选 8 首、排序；只能用户出现过的 id
  输出 {"overview":…,"items":[{"id":…,"reason":…}]}
```

- 标签来自 `getAllMusicLabels()` 一次批量查询（每首最多 3 个）
- 本地参考段带 ★；曲库段不重复列这些 id
- 截断顺序为 `PLAY_COUNT DESC`，且强制包含本地 8 首

## 4. 播放页：电台区域（D4，细化）

### 4.1 tab 替换

`PlayContent` 现有 5 tab：`controls / lyrics / info / generate / playlist`。
`generate` → `radio`（图标 `radiowaves`），整块替换为电台控制区。

### 4.2 三态布局

| 状态 | 内容 | 主操作 | 次操作 |
|---|---|---|---|
| **IDLE**（`RadioState.IDLE`） | 当前曲封面/标题；文案「以此歌开电台」；副文案「本地先排一批，AI 接着排」 | 「开电台」→ `startRadio(seed=null, trigger=HOME_CLICK)` | — |
| **BUILDING** | 进度条 + `actionText` + 目标数 | 「取消」→ `stopRadio()` | — |
| **PLAYING / PAUSED** | 主题（`card.theme`）、`upcomingCount`、`nextTitle`、`lastAdjust` | 「暂停」/「继续」→ `pauseRadio`/`resumeRadio` | 「节目单」→ 打开 `RadioConsole`；「结束这一档」→ `stopRadio()` |

- 额度耗尽：主按钮置灰 + `ai_quota_exhausted_hint`（复用 `RadioCard` 的 `quotaGate.isBucketExhausted("radio")` 判断）
- 有在播曲时 `startRadio` 会接在当前曲之后、不顶掉当前曲（现有 `startAfterCurrent` 语义，保持）

### 4.3 复用而不是重写

- 状态：`masterAgent.radioState` / `radioCardState` / `radioPlaylist`
- 节目单：现有 `RadioConsole`（ScrimDialog）。tab 内只放摘要卡 + 「查看节目单」，**不内嵌完整 Console**
- 打开 Console 的可见性目前由 `AppRoot` 私有 state + `BottomFusionBar.onOpenRadioConsole` 控制；播放页要复用得先抽一个共享 opener（Koin 单例或 `MasterAgent` 上的 `MutableStateFlow<Boolean>`），不能各页各建一份
- 文案：优先复用 `agent_radio_*` / `radio_card_*` 既有键，新增键只在三态表需要时加

### 4.4 无权重入口（黑盒）

- 任何页面都**不暴露**权重模板 / 算法类型选择器
- 参数由 domain 的 `SmartWeights` 决定；UI 不读、不写、不展示
- 可观测性只在 DEBUG 日志里输出 `seed` 与权重摘要，供排查，不进 UI

### 4.5 退役清单

- `GeneratePlaylistComboButtons`（`MyButton.kt`）
- `PlaylistQueueViewModel.generatePlaylist` / `GeneratePlaylistUseCase` / `PlaylistAlgorithms` 及两个 Strategy（或把加权相似度下沉为共享服务后删壳）
- `PlayContent` 的 `onGeneratePlaylist` / `onSaveDefaultConfig` 回调链
- 测试：`GeneratePlaylistUseCaseTest`（6 例）、`AdaptiveExtensionTruncationTest`（12 例）随代码退役
- `AlgorithmType` / `WeightTemplate` UI 退役；两个 enum 与 `saveDefaultWeightTemplate` / `getDefaultWeightTemplate` 若无消费者一并删（设置键见 §4.6）

### 4.6 施工结果（2026-11-03）

- `RadioConsoleController`（commonMain，Koin 单例）接管面板可见性；AppRoot 改为 collect 它，底栏长按与播放页共用同一 opener。
- 新增 `RadioEntryCard`（`player/components/`）：IDLE / BUILDING / PLAYING·PAUSED 三态；**不设暂停/继续**（由主播放键承担）；
  「结束这一档」不直接 stop，打开 Console 走滑动收档。
- `PlayContent`：竖屏第 4 页、手机横屏第 4 页替换为电台卡；平板横屏新增第 4 个「电台」tab；`PlaylistTabContent` 内的生成组件移除。
- 无在播曲目不渲染（播放页本身也进不来，用户 2026-11-03 口径）。
- 旧 `GeneratePlaylistComboButtons`、`PlayerCallbacks.onGeneratePlaylist`、`GeneratePlaylistUseCase`、`PlaylistAlgorithms`、
  `WeightManager`、`AlgorithmType` / `WeightTemplate` / `ExtensionConfig` 以及三端设置存取**已全部删除**（2026-11-03 退役批）。
- 验证：`:shared-ui:compileKotlinDesktop`、`:shared-ui:compileAndroidMain`、`:shared-ui:compileKotlinIosSimulatorArm64` 通过；
  **无 Compose UI 测试基建**，三态渲染仍需真机/桌面走查。

### 4.7 设置键清理

现有三个键随 UI 退役：`default_algorithm_type` / `default_weight_template` / `default_extension_config`。

- 本轮**只停止读写，不主动清 DataStore**：旧键留在设备上无副作用，也不会被新代码读取
- 三端 `SettingsRepositoryImpl` 的对应方法与 `SettingsRepository` 接口声明一并删；`FakeSettingsRepository` 同步
- 若将来要彻底清，走一次设置迁移（与 v10 无关，属 DataStore 键空间）

## 5. 实施顺序

1. **内部权重服务**：`SmartWeights.PRIOR` + `SmartWeightSet`（seed + emphasis + 扰动）+ `getSimilarSongsByWeightedLabels(weights)` + 测试（无 UI）
2. **每日两单**：接入权重 + 覆盖式持久化 + 测试（同 seed 稳定 / 换天变化 / 重启恢复 / 每 type 一行）
3. **每日两单 agent 选曲**：prompt + 解析 + 候选校验 + 回落 + 测试
4. **播放页电台区域**：tab 替换 + 三态 + 复用 Console + 额度拦截
5. **退役旧生成器**：删 UI / 用例 / 算法 / 测试，`compileAll` + 全量回归

## 6. 内部默认（黑盒，不再问用户）

1. **电台本地保底**：以曲目为种子时吃同一份权重；关键词种子没有曲目标签，保持原标签频率。两条路径在代码内分流，UI 无感。
2. **偏重与扰动**：每次生成 `randomSeed()`；主维度在 4 类里随机（避开上次），其余维度 ±15%。每日两单与电台都遵循这一条。

> 若实际听感不对，改 `SmartWeights` 的幅度/种子即可，不涉及 UI 与契约。

## 6.1 待评审

- §4 电台区域的三态布局与操作是否符合预期（这是唯一还需要你点头的部分）

## 7. 不做

- 不把每日两单做成 `playlist` 行、不进歌单列表（D2 明确）
- 不暴露权重 / 算法选择器（D1 黑盒）
- 不保留每日历史（将来要再加 snapshot）
- 不在本轮改 iOS 的心动/最近播放（D3-09 环境受限）
