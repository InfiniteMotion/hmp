# v10 迁移设计（批一收敛产物）

> **状态**：需求与依赖已收敛，待施工。本文是 `domain-baseline.md` §三 批一的落地入口：批一四域（D2 / D3 / D5 / D7）的审查共同指向**同一次** Room 迁移，散做等于把迁移拆三次。
> **来源**：本文全部条目溯源到 `docs/7_3/domain/{D2,D3,D5,D7}.md`，不重复论证，只做归并与定序。
> **当前库**：`version = 9`（`AppDatabase.kt:40`，`shared/schemas/.../9.json`）。目标 `version = 10`，迁移 `MIGRATION_9_10`。

---

## 一、为什么必须是一次

批一四域各自独立发现了"库要变"，但落到 Room 上它们是**同一份 schema 版本号**：

- D2-07 四表零索引 → 要加 9 条索引；
- D3-03 / D3-05 缺两个唯一索引；D3-15 要删一列；
- D5-11 `PlaybackHistory` 缺两个索引 + 要定保留窗口（**2026-09-30 决策 3：不自动清理，只给手动清理入口**）；
- D7-09 / D7-10 迁移链本身缺门禁与降级出口。

这些不能同时开三次 `version++`。所以统一在 **v10** 一次做完，迁移测试 `AppDatabaseMigrationTest` 只守一道关（`9 → 10`）。

---

## 二、Schema 增量（落在 `MIGRATION_9_10`）

### 2.1 新增索引

| 表 | 索引 | 来源 | 现有状态 |
|---|---|---|---|
| `music` | `(isDeleted)` | D2-07 | `9.json` 中 `indices: null` |
| `music` | `(title)` | D2-07 | 同上 |
| `music` | `(artist)` | D2-07 | 同上 |
| `music` | `(album)` | D2-07 | 同上 |
| `musicLabel` | `(label)` | D2-07 | 主键 `(musicId,label)`，`label` 在第二列，前缀索引无效 |
| `musicLabel` | `(type)` | D2-07 | — |
| `musicLabel` | `(source)` | D2-07 | — |
| `userInfo` | `(isDeleted)` | D2-07 | `9.json` 中 `indices: null` |
| `musicExtra` | `(isGetExtraInfo, isDeleted)` | D2-07 | 同上 |
| `playlist_item` | `UNIQUE(playlistId, itemOrder)` | D3-03 | 仅 `index_playlist_item_playlistId` |
| `playlist` | `UNIQUE(name)` | D3-05 | `name` 上无唯一约束 |
| `PlaybackHistory` | `(playedAt)` | D5-11 | `indices: null` |
| `PlaybackHistory` | `(musicId, playedAt)` | D5-11 | 同上 |

**实现纪律**：每条索引必须**同时**改 `@Entity` 的 `indices` 与手写 `MIGRATION_9_10`，并在三端各自的 `DatabaseBuilder.{android,desktop,ios}` 注册（`AGENTS.md` §七）。索引语句写进迁移、又写进 `@Entity` 是 Room 的常态，漏一端漏一处都会让 `9.json` 与代码漂移。

### 2.2 删列

| 表 | 动作 | 来源 | 说明 |
|---|---|---|---|
| `playlist_item` | 删除 `songUrl` 冗余列 | D3-15 | Room 无原生删列，需建新表 + 搬数据 + 删旧表 + 改名；存量 `songUrl = ` 构造点（grep 口径：单文件 `songUrl = `，约 12 处）需同步改写 |

### 2.3 建索引前的去重（迁移内顺序敏感）

- `playlist_item(playlistId, itemOrder)` 唯一索引：**先跑一次去重**（同歌单内 `itemOrder` 重复的行合并/重排），再 `CREATE UNIQUE INDEX`，否则存量库迁移直接失败（D3-03）。
- `playlist.name` 唯一索引：**先跑去重 / 自动改名**（同名歌单后缀 `_2`），再建（D3-05）。

### 2.4 保留窗口（非索引，属同批次落地）

- `PlaybackHistory` **不做自动清理**（2026-09-30 决策 3，覆盖本文件原先"建议 24 个月"的口径）：只提供**手动清理入口**（D5-11）。窗口本身不是 schema 字段，故本项**不产生任何迁移语句**；索引照做——它治的是查询退化，不是库体积。

---

## 三、迁移工程门禁（D7-09 / D7-10）

1. **单一 `ALL_MIGRATIONS` 常量**：`AppDatabase` 暴露 `ALL_MIGRATIONS`，三端 `addMigrations(...)` 都引用它 —— 结构上不可能漏端（D7-09 目标 6）。
2. **逐环 `runMigrationsAndValidate`**：`1 → 9` 每一环补断言；新增 v10 忘了写迁移 = `AppDatabaseMigrationTest` 红（D7-09）。
3. **降级守卫**：`user_version > 9`（库比代码新）时，不静默失败、不崩溃 —— 记 `HmpLog.e(LogTag.Data*)` + 给用户「库版本高于本版本，请升级或恢复备份」的可见路径（D7-10）。当前无任何 `RoomDatabase.Callback` 处理此场景。

---

## 四、随 v10 一起修的逻辑（不是 schema，但必须同批次，否则迁移白做）

| 逻辑项 | 来源 | 说明 |
|---|---|---|
| 软删除只由用户决定：扫描结果不覆盖 `isDeleted`；文件缺失导致的软删保留可分辨记录 | D2-02 / D2-03 | 否则 v10 加了索引，但全量重扫仍抹掉隐藏标记 |
| 全量重建包进单事务 + upsert 语义，不碰 `musicExtra`/`userInfo` 用户资产 | D2-01 | `withTransaction` 全仓 0 命中，需在 `AppDatabase` 暴露事务上下文 |
| 歌单批量写/重排/恢复进业务事务；`itemOrder` 进 domain 模型与 mapper | D3-03 / D3-04 / D3-02 | 否则唯一索引建了，但写入仍可能撞号/丢序 |
| 按名删除改按 id 删除；系统歌单自愈按 id | D3-05 | 唯一索引建了，但按名路径绕过保护仍会出事 |
| 派生字段 `songCount`/`totalDurationMs`/`coverUri` 在同一事务内重算或改为查询时算 | D3-08 / D3-07 | 避免软删后歌单计数不刷新 |
| iOS 存量时间戳修正：`createdAt < 1_000_000_000_000L` 加回偏移（按平台判别） | D5-01 | 否则新口径下旧数据全错 |
| 窗口聚合从 `strftime` 改为 Kotlin 侧算 `[dayStart, dayEnd)` 毫秒区间 | D5-01 / D5-11 | 配合 `PlaybackHistory(playedAt)` 索引走范围扫描 |

---

## 五、明确不在 v10（除非被上面的依赖带入）

- **`year` / `genre` / `track` 入库列**（D2-11）：D2 已判「刻意不做」——不为它单独开迁移；改判条件是"v10 已因别的理由开了就顺带带"（D2 刻意不做 #3）。**当前不列**。
- **FTS5 / 拼音列**（D2 刻意不做 #2）：库 <2 万首且搜索无延迟前不做。
- **年度（365 天）热力图**（D5 刻意不做 #1）：只要求名实相符，允许降级改文案。
- **`songCount`/`totalDurationMs` 改查询时聚合**（D3 刻意不做 #4）：本轮只补刷新时机，长期方案才动表。
- **`musicLabel` 主键改造**（D2 刻意不做 #1）：有意的"同一事实单槽位"建模，不动。
- **`playlist` / `playlist_item` 主键外键结构**（D3 刻意不做 #7）：除本文 §2.2 / §2.1 已列的索引与删列外不动。

---

## 六、验证（判据落点）

1. `:shared:desktopTest` 的 `AppDatabaseMigrationTest` 新增 `9 → 10`：
   - `PRAGMA index_list` 含 §2.1 全部预期索引；
   - `PRAGMA table_info(playlist_item)` 不含 `songUrl`；
   - `SELECT COUNT(*) FROM playlist_item` 迁移前后相等。
2. `EXPLAIN QUERY PLAN` 断言 `getWindowedAnalytics` 的 cutoff 查询走 `PlaybackHistory(playedAt)` 索引而非 `SCAN TABLE`（D5-11）。性能绝对值不进判据，只断言计划形态。
3. `10.json` schema 导出 diff 中 `music.indices` 含 §2.1 五项（`D2-07`）。
4. 降级守卫：构造 `user_version = 11` 的库打开，断言不崩且日志/UI 出现版本过高提示（D7-10）。

---

## 七、依赖与批二/批三的接口

- 本迁移由**批一收口**，不依赖批二（D1/D4/D6）或批三（D8/D9）的任何结论；但批二若挖出新的 schema 需求（例如 D1 播放引擎要落新的持久化字段），**必须并入 v10 或另开 v11**，不允许在 `version=9` 上偷偷加字段。
- 批二开工前，本文件 §2.1 / §2.2 应是 v10 迁移实现的唯一 schema 真源；具体 `file:line` 改动在动手前按符号名复验（`AGENTS.md` 行号纪律）。

**© 2026 Hearable Music Player · v10 迁移设计 · 批一收敛**
