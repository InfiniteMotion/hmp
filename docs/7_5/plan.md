# 7.5 期 · P1 页面体系 UDF 改造规划

> **范围决议（用户，2026-11-05）**：UDF 改造范围为**应用所有页面**，不是只改 agent 区。
> **结构决议（用户，2026-11-05）**：分 **P1 页面体系**（本规划）与 **P2 Agent 运行时状态所有权**（独立规划待建）两部分，共享原则、形态不同、各自验收。
> **版本归属（用户，2026-11-06）**：P1 = **7.5.0**、P2 = **7.6.0**，先后推进；因 P1 体量由 7.4.1 升为独立 minor 期，活跃线迁至 `docs/7_5/`，7.6 届时另开目录。`docs/7_4/` 保留 7.4 施工记录与 7.3 残余台账（台账仍活跃，不归档）。
> **前置**：7.4.0 ktlint 已收口 —— `./ktlint check` = 0 处、555 文件格式化（`0d88404`）、blame 忽略生效（`ac4165e`）。UDF 从合规基线出发。
> **bump 节奏**：`release.toml` 仍为 **7.4.0**，规划阶段不动；P1 开工 bump 7.5.0、P2 开工 bump 7.6.0，两次都按「只标注不 release」（无 tag / 无包 / 无 push），派生点交 `./gradlew syncVersion`。
> **施工方式决议（用户，2026-11-06）**：**取消开工前的契约 / 门禁 / 批次前置设计**。改为「先定一个最轻的 UiState 共通模板 → 按页面顺序逐个改 → 最后收口契约与门禁」，让规则从真实代码里长出来。
> **状态（2026-11-07）**：**已开工**。§4.1 定模板已落地（本阶段第 1 笔提交）：`LoadState` 命名归位 19 文件 / 226 处、新增 `common/util/UdfConventions.md`、`EditMusicTagsScreen` 收成单一状态出口 + `SharedFlow` 一次性效果。基线 HEAD `ed2c8293`（UDF 规划定稿），工作树此前干净。`release.toml` 仍 **7.4.0**，按 §4.3 收口时 bump 7.5.0（口径依据见 §七 11-07 记录）。验证：`ktlint` 0 处 / `compileAll` 绿 / `testUi` 绿。§4.2 逐页改进行中，逐屏判定与提交记录见 [progress.md](progress.md)。
> **取证口径**：所有计数为 2026-11-05/06 工作树实测；行号会过期，动手前按符号名 grep；归档 `docs/archive/**` 只进不改。

---

## 一、范围与边界

### 1.1 两个部分

| | **P1 页面体系**（本规划，7.5.0） | **P2 Agent 运行时**（7.6.0，规划待建） |
|---|---|---|
| 区域 | `shared-ui/commonMain` UI 层 + 适配 VM | `shared/.../domain/agent/`，18,232 行 |
| 状态模型 | 一屏的快照 → `StateFlow<XxxUiState>` | 一次会话 / 一次 `run()` 的进度 |
| 目标形态 | 完整 UDF：单一状态出口 + 事件上送 + 副作用归 VM + 一次性效果（`DialogEvent` 形态） | actor / 状态机：`Mutex`、`StateFlow.update`、Channel 单消费循环 + 不可变事件 |
| 对应台账债 | R15 / R48 | R3 / R4 / R6 / D6-08 |
| 验收 | `ktlint` + `preflight` + 收口门禁 | 并发压力 / 取消路径 / 状态所有权检查，**不套用 P1 判据** |

**不要把 `UiState` / `onXxx` / `reduce` 推进引擎内部**：`ReActLoop.run()` 是有限步循环、状态全是局部变量、副作用本身就是主流程；P2 是「统一与守门」（`CardPool` / `SubAgentRegistry` / `TokenQuotaGate` / `RadioSession` 已按此写），不是从零引入 UDF。

### 1.2 覆盖 ≠ 重写

30 屏**全部**纳入盘点、门禁与每屏判定记录，但**只有存在业务状态归属问题的页面才动代码**。[state-audit.md](state-audit.md) 逐条盘点了 174 处 `remember` 状态：45 处明确进 VM、91 处留 UI、38 处灰区（倾向进 29 / 留 6 / 需判 3）。按此实际搬运约 **74 处**，其余约 **97 处**不动。判定合规的屏也留一条记录，但不制造 diff。

### 1.3 不推到引擎内部

- **交界点**：`MasterAgent.getAgentPolicyConfig()` 返回内部可变实例本身。VM 用 `AgentPolicy.snapshot()` 取不可变副本，改完走 `saveAgentPolicyConfig()` 整体替换，不原地写活引用。这是边界适配，不是 P2 改造。
- **适配边界已存在**：`ChatAgentGateway` 的 `ChatAgentEvent`（6 种不可变事件，唯一发射）→ `ChatViewModel:263-351` 唯一消费并 `_state.update { copy(...) }` → `ChatUiState`；`ConfirmBridge` 用 `CompletableDeferred` 握手。协议不改。
- P2 不进本阶段任何提交。

---

## 二、现状基线

| 指标 | 实测 |
|---|---|
| L1 页面 / 组合根 | 30 屏 / 15,392 行；`AppRoot.kt` 772 行 |
| VM | 21 个文件，19 个以 `viewModel {}` 三端注册 |
| 状态 / 出口 | 174 处 `remember` 状态持有；143 处 `collectAsState` |
| 副作用 / 业务直连 | 110 处 `LaunchedEffect` / `launch` / scope；62 处业务对象直调 |
| 测试 | 9 / 30 屏有相关测试 |
| 契约 | `Intent` / `onEvent` / `reduce` = 0 处 |

三条改判：

1. **不是「给屏幕补 ViewModel」**：6.12 的 VM 作用域改造已落地（NavDisplay entry decorator、`activityViewModel()`、三端 19 个 `viewModel {}`）。本阶段改的是数据流形态。
2. **不引入 MVI 框架**：仓库已有异步态封装（本次改名为 `LoadState<T>`）+ `LoadStateContent` + `asLoadState()`、`DialogEvent` + `SharedFlow`、`combine().stateIn()` 等零件；不造 `BaseViewModel<S,E>`，不加强制 `sealed Intent`。
3. **`preflight` 已恢复**：R31 根因（`DesktopQueueDbReflowTest` 的 `advanceUntilIdle()` 与跨测试类遗留 `delay(100)` 协程在虚拟时间上互锁）2026-11-05 定案修复，`compileAll testAll` 与 `preflight` 均 `BUILD SUCCESSFUL in 7s`。

---

## 三、共通模板（开工唯一前置，1 笔提交）

**名字归位（用户 2026-11-06 决议）**：旧 `common/util/UiState.kt` 的 `sealed interface UiState<out T>`（Idle / Loading / Success / Error / Empty）本是「一次异步操作的结果」，却已被 5 个 VM 当成状态出口使用（共 8 个 `StateFlow<UiState<...>>`），语义已经分裂。**把它改名为 `LoadState<T>`，把 `UiState` 这个名字让给整屏状态** —— 不再保留第二套同名概念，也不沿用旧类型作内部字段。

配套机械改名：`UiStateExtensions.kt` → `LoadStateExtensions.kt`（`asUiState()` → `asLoadState()`）、`UiStateContent` → `LoadStateContent`、`UiStateTest` / `SearchDebounceTest` 跟着改。全仓 **19 文件 / 210 处**，其中 4 个基础与测试文件占约 140 处；编译期全检、无静默失效风险，**与模板同一笔提交完成**，不另开批次。

模板 = **复用 3 件 + 新增 2 件**，不引入基类、不引入框架、不加依赖：

| | 内容 |
|---|---|
| 复用 | 改名后的 `LoadState<T>` 作为 `XxxUiState` 里**异步字段**的类型；推广 `Flow.asLoadState()` / `runCatchingToLoadState()`，替代各自 `_x.value = ...` |
| 复用 | `DialogManager` 的 `SharedFlow<DialogEvent>` 形态作一次性效果（toast / 导航 / 弹窗）；**VM 不持有 `NavBackStack` / `RouteNavigator`** |
| 复用 | `ChatViewModel` 的 `when(event) → _state.update { copy(...) }` 作事件 → 状态的标准写法 |
| 新增 | 一份写法约定（放 `common/util/` 同目录）：每屏一个不可变 `data class XxxUiState` + 单一 `val uiState: StateFlow<XxxUiState>`；用户动作只走 VM 的 `onXxx()`，方法不返回业务结果。**`UiState` 只指整屏状态，`LoadState<T>` 只用于异步字段，项目里不再有通用 `UiState` 类型** |
| 新增 | 一个改好的样板屏作复制起点（`EditMusicTagsScreen`，已有 `EditMusicTagsUiState` / `SaveTagsResult`，是全仓仅 2 个用 `update{}` 的 VM 之一） |

**5 个 VM 的 8 个旧出口**（`PlaylistViewModel` 3 个、`ArtistAlbumViewModel` 2 个、`LibraryViewModel` / `SearchViewModel` / `SongDetailViewModel` 各 1 个）在逐页改到对应屏时收进 `XxxUiState`，异步部分用 `LoadState<T>` 字段 —— 本来就要改这些屏，不增加额外批次。

模板附 [state-audit.md](state-audit.md) §三 的 **7 类白名单**（滚动分页 / 拖拽测量 / 动画时间戳 / 展开收起 / 协程作用域 / 卡片缓存 / 进度条拖动中间态），作为「哪些 `remember` 不用搬」的默认答案。全局播放态与主题态作为组合根投影白名单，不要求每屏各自聚合，否则会把 143 处订阅变成 30 份重复播放状态。

---

## 四、施工方式（三步）

### 4.1 定模板

见 §三，1 笔提交。

### 4.2 逐页改（每屏 1 笔提交）

顺序按 `NavigationGraph.kt` 的 entry 表走一遍，不再按领域 / 层级 / 批次命名。每屏：

1. 对照 [state-audit.md](state-audit.md) 判定：有业务状态或业务直连 → 按模板改；没有 → 记一句「判定合规」，**代码不动**
2. 每笔只改一屏、不做视觉调整；`./ktlint check` = 0 处 + `./gradlew compileAll` 绿
3. 遇到模板没覆盖的形态（全局播放态、确认弹窗链、可变实例），顺手往模板补一句规则，不另开分支
4. 重的屏（`AgentConfigScreen` 1719 行、`AIScreen` 1325 行）可拆 2 笔，单独多花时间

**三条每笔必查的护栏**：

- **新增 / 拆分 VM 必须 Android / Desktop / iOS 三处 `viewModel {}` 同改**（iOS 本机编译不到，最易漏）；`AuditLogViewModel` 顺手补三端注册
- `AgentConfigScreen` 按 §1.3 用 `snapshot()`，别持有活引用；用一条回归用例钉住「reset 后展示值 = 新实例值」
- 新测试优先 `commonTest`（平台无关）；`PlaylistViewModel` 有 Robolectric 构造坑，按 D3-06 抽纯函数处理

提交信息前缀：`refactor(udf): <屏> — <动作>`。

### 4.3 最后收口（1~2 笔提交）

30 屏过完、存量清零后，**门禁直接全量开**，不需要增量模式 / 范围清单 / 基线快照：

1. **模板定稿**：把 4.2 过程中补的规则整理成正式约定，同步 `DEVELOP.md` / `AGENTS.md`
2. **`scripts/check-udf.py` 全量扫**，挂进 `pr-check.yml` 的 `lint` job（7.4 已建 lint job 与 Verdict 汇总）。**按注入类型判定，不按关键字**：实测 `koinInject` 41 处里 18 处是 `PlatformServices` / `PlaybackController` / `DialogManager` / 各 Controller 等平台门面，`scope.launch` 48 处里约 16 处是纯 UI 用途 —— 关键字一刀切会误报 33~44%
3. `cmd //c "gradlew-smart.bat preflight"` 全绿；30 屏判定记录归档
4. bump 7.5.0（只标注不 release）

**这一版唯一的代价**：各屏写法在演进中可能不一致，收口时要统一一遍。比前置设计整套管线的成本小得多。

---

## 五、验收与缺口

| 层级 | 命令 |
|---|---|
| 每笔 | `./ktlint check`（0 处）+ `./gradlew compileAll` |
| 收口 | `checkDesignTokens` + `gradlew-smart.bat preflight` + `git diff --stat` 复核 |

如实登记的缺口，不假装闭环：

- **iOS**：本机 Windows 不编译 iOS target、无 `iosTest` 源集，`commonMain` 改动对 iOS **零机器反馈**；三端 Koin 对称性靠人工 + 两个 GraphVerificationTest（Android/Desktop），发行前用 macOS 验
- **CI**：`pr-check.yml` 不编译不跑测（X-01 暂缓），本机是唯一守门人
- **测试**：21 屏无测试，`preflight` 绿不等于「行为不变」；重屏与状态机屏补 VM 级用例，其余靠每笔 review

---

## 六、范围外

P2（Agent 运行时，7.6）、注释清理、设计校准、X-01 CI 编译测试、R31 的 `Test` 任务挂钟超时（仅剩防护网，属 X-01）、实机池签收、`storybook/` 沙盒、7.3 其余残余、ktlint 规则集调整。

---

## 七、决议与取证记录

| 日期 | 事项 |
|---|---|
| 2026-11-05 | 7.4.0 只做 ktlint 并收口；UDF 当时定于 7.4.1，次日因体量改判为独立 7.5 期 |
| 2026-11-05 | UDF 范围定为**应用所有页面**；工作分 **P1 页面体系 / P2 Agent 运行时**两部分 |
| 2026-11-05 | 30 屏逐屏基线、Koin 三端注册、测试源集、验收任务定义均为工作树实测 |
| 2026-11-05 | **R31 结案**：`advanceUntilIdle()` 虚拟时间死循环定案并修复，`testAll` / `preflight` 恢复可用 |
| 2026-11-06 | 版本归属 **P1 7.5.0 / P2 7.6.0**；活跃线迁 `docs/7_5/`，原 7.4.1 / 7.4.2 作废 |
| 2026-11-06 | 页面状态盘点：174 处状态逐条分类（[state-audit.md](state-audit.md)），限度定量为搬 74 / 留 97 |
| 2026-11-06 | **施工方式简化决议**：取消前置契约 / 门禁 / 批次设计，改为「模板 → 逐页改 → 最后收口」；门禁全量开、按类型判定 |
| 2026-11-06 | **状态命名归位决议**：不沿用旧 `UiState<T>` 作内部字段，改名为 `LoadState<T>`；`UiState` 之名让给整屏 `XxxUiState`，项目内不再有两套同名概念。全仓 19 文件 / 210 处机械改名，与模板同一笔提交 |
| 2026-11-07 | **开工第 1 笔（§4.1 定模板）**：实测改名 **19 文件 / 226 处**（规划记 210 处，本口径含 `UiStateContent` / `asUiState` / 测试名等复合标识符）；四文件 `git mv`；新增写法约定与样板屏。样板屏把 3 个 `StateFlow` 出口收成 1 个，`SaveTagsResult` 状态型一次性结果改为 `SharedFlow<EditMusicTagsEffect>`，写权限判定与选图收进 VM。顺手修两处既有缺陷：`loadTags` 重置丢失 `canWriteTags` 首帧能力位、选封面后 `hasChanges` 不重算致保存按钮不亮。验证 `ktlint` 0 处 / `compileAll` / `testUi` 全绿 |
| 2026-11-07 | **bump 口径如实登记**：头部「P1 开工 bump 7.5.0」与 §4.3「收口 bump」冲突，本阶段按 **§4.3 收口时 bump** 执行。依据：7.3.0（`1dc7352c`）与 7.4.0（`eab94fb1`）两次 bump 均在各自收口提交之后，且 `release.toml` 的 notes 需描述已完成内容；开工期版本号保持 7.4.0。头部决议原文不改写，以本记录为准 |

**参考**：[页面状态盘点](state-audit.md)、[7.4 期规划与施工记录](../7_4/plan.md)、[7.3 残余台账](../7_4/residuals-7.3.md)、[ktlint 落地方案](../ktlint-integration.md)、[7.3 架构审查 R48（归档证据）](../archive/7_x/7_3/review-7.3-architecture.md)。
