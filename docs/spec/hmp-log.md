# 统一日志规范（Logging Convention）—— 三端唯一日志出口的 tag / 级别 / 格式契约

> **约束**：运行时日志只能出自两个门面 —— Kotlin `com.hmp.log.HmpLog`（tag 取自 `LogTag`）与 Swift `ios/HMP/HMP/Common/HmpLog.swift` 的 `HmpLog`（tag 取自 `HmpTag`）；消息必须以 §4 的域 emoji token 开头。
> **适用范围**：`shared` / `shared-ui` / `android` / `desktop` / `ios` / `shared-ios` 的全部运行时代码（Kotlin + Swift）；豁免见 §2「作用范围」 ｜ **最后核对**：2026-09-28（仓库根 Git Bash 实跑 §6 命令与 5 个 `build/*.js` 脚本：非豁免违规 0、裸输出 0、硬编码 tag 0、ERROR 误用 0；未跑 gradle）
> **判据**：① `node build/verify-logs.js` → 尾行「非豁免违规 0 处」 ② `grep -cE '^[[:space:]]+[A-Za-z][A-Za-z0-9]*\("' shared/src/commonMain/kotlin/com/hmp/log/LogTag.kt` = **38**（Swift 同类 = **28**） ③ `grep -rE 'HmpLog\.[diwe]\(' --include='*.kt' shared shared-ui android desktop | grep -v /build/ | wc -l` = **464** ④ `node build/check-emoji.js` → 缺 emoji **5**（D-1） ⑤ §6 C8 = **4** 行，release 走 `Warn` / `true`

底层框架：[Touchlab Kermit]（Android→Logcat / iOS→OSLog / Desktop→stdout+ANSI）。沿革见文末「修订记录」。

## 1. 原则

1. **一个模块一个 tag**，禁止平行 tag。
2. **ERROR 永不用于可恢复情况**。
3. **消息一律结构化**，便于 grep 与机器解析。
4. **只经统一入口 `HmpLog` 打日志**，禁止散落 `print*` 或裸调其他日志通道。
5. **保持极简**：不引文件落盘（`kermit-io`）、不引崩溃上报、不做链路 ID、不改 `HmpLog` 四个签名。

## 2. Tag 命名体系

统一格式 `{域}.{组件}{.子件}`：点分隔、每词首字母大写。规则：

- tag 一律取自集中常量 `LogTag.X` / `HmpTag.x`；**禁止调用点硬编码字符串，禁止另建平行 tag 常量**（`LogTag` 是 enum 参数类型，本身就是编译期护栏）。
- 一个模块一条根 tag，子件用 `.` 展开；子件只写业务细分，**不写行号、不写本次动作**（动作属于消息正文）。
- 新增域**三处同步**：`LogTag.kt` 枚举 + `HmpTag.swift` 常量 + 本节清单。

**双端收敛**：`HmpTag.swift` 只登记 Swift 可达的 tag，判据「Swift 会不会用它打日志」；10 个 Kotlin-only 的 Agent 子 tag（Sub / Enrich / Hello / Radio / ReActLoop / Scheduler / Tool / LlmCall / ContextBudget / Profile）不镜像。**Kotlin 38 ↔ Swift 28，差 10，Swift 是 Kotlin 的真子集**（2026-09-28 实测，复核见 D-4）。

全量 tag 树（38 条 / 7 域，与 `LogTag.kt` 逐字一致）：

```
Agent（14）  Agent.Master / Agent.Sub / Agent.Enrich / Agent.Hello / Agent.Radio / Agent.ReActLoop / Agent.Scheduler / Agent.Tool /
             Agent.LlmCall / Agent.ContextBudget / Agent.Profile / Agent.Gateway / Agent.Chat / Agent.Port
UI（4）      UI.Chat / UI.Settings / UI.Navigation / UI.Common
Data（5）    Data.Repository / Data.Room / Data.Net / Data.MusicRepo / Data.Backup
Player（7）  Player.Core / Player.Ffmpeg / Player.Media3 / Player.AudioEffect / Player.Service / Player.Ios / Player.AudioSession
System（4）  System.Init / System.Di / System.Window / System.Lifecycle
Library（3） Library.Scan / Library.Metadata / Library.Artwork
Media（1）   Media.NowPlaying
```

### 作用范围

| 范围 | 适用? | 说明 |
|---|---|---|
| 应用运行时代码 / Swift·iOS 桥接层 | ✅ 适用 | 唯一出口 `HmpLog`（Swift 侧 `HmpLog.swift`，内部委托 `platformLog`） |
| 构建脚本 `build.gradle.kts` | ❌ 不适用 | Gradle 任务输出用 `println`，不进 Kermit 通道 |
| 单元测试 `*Test.kt` | ⚠️ 豁免 | 诊断输出可用 `println`；断言日志请用 Fake writer |
| `com.hmp.log` 包自身、`PlatformLog.kt`（expect + `severityFromInt`）、三端 actual、`KermitInit.kt`、`HmpLog.swift` 文件自身 | ❌ 豁免 | 门面与桥接实现，本就直连 Kermit（`KermitInit.kt` 是唯一允许 `setMinSeverity` / `addLogWriter` 的宿主；`HmpLog.swift` 内含 4 处 `PlatformLogKt.platformLog`，属桥接实现而非调用点） |

## 3. Level 语义

| Level | 语义 | 该用 | 严禁 |
|-------|------|------|------|
| `DEBUG` | 内部细节，仅逐行排查 | 中间变量、解析中间态、每次 tick、去重检查 | 用户路径关键节点 |
| `INFO` | 生命周期 / 关键动作发生与结果 | 模块创建、启停、配置生效、批次完成、状态流转 | 循环体内高频动作 |
| `WARN` | 可恢复异常 / 降级但继续 / 非预期输入 | 捕获后降级的失败、覆盖率未达标、跳过某次刷新 | 曲名、歌词、听歌数据、模型响应原文（release 门槛即 Warn） |
| `ERROR` | 不可恢复、需人介入 | 依赖缺失无法启动、LLM 连续失败无降级、db 写失败 | **non-fatal** |

纪律：`ERROR` 永不用于可恢复情况；`"... failed (non-fatal)"` 一律 `WARN`；高频循环内只用 `DEBUG`；**降级必须留痕** —— `catch` 要么 `HmpLog.w(tag, e) { "… non-fatal …" }` 要么复抛，禁止空 catch 体（现存 26 处待清，见 D-3）。

## 4. 消息格式

模板：`[域Token] 事件名 | key=value | key=value`。事件名动词开头；key 用 snake_case；含空格/特殊字符的字符串值加双引号（`title="夜曲"`）；敏感值截断（`apiKey=sk-ab12…`）。

**域 Token 对照表**（硬要求，不是装饰 —— 没有它，按域过滤与 `check-emoji.js` 判据都失效；`HmpLog.swift:67` 的注释即引用本节）：

| 域 Token | 域 Token | 域 Token | 域 Token |
|---|---|---|---|
| Agent.Master 🤖 | Agent.Sub 🧩 | Agent.Enrich 📚 | Agent.Hello 👋 |
| Agent.Radio 📻 | Agent.ReActLoop 🔁 | Agent.Scheduler ⏱️ | Agent.Tool 🛠️ |
| Agent.LlmCall 🧠 | Agent.ContextBudget 📐 | Agent.Profile 🫀 | Agent.Gateway 🌉 |
| Agent.Chat / UI.Chat 💬 | Agent.Port 🔌 | UI.Settings ⚙️ | UI.Navigation 🧭 |
| UI.Common 🎨 | Data（全部）🎵 曲库 ／ 🌐 网络 ／ 💾 备份 ／ 🗄️ 其他 | Player（Core / Ffmpeg / Media3）🎬 ／ ▶️ | Player.AudioEffect 🎛️ |
| Player.Service 📡 | Player.AudioSession 🔊 | Player.Ios ▶️ | System（Init / Di / Lifecycle）🚀 |
| System.Window 🪟 | Library.Scan 📂 | Library.Metadata 🏷️ | Library.Artwork 🖼️ |
| Media.NowPlaying 📺 | | | |

```kotlin
HmpLog.d(LogTag.AgentRadio) { "📻 extractSeedLabels | matched=3 | from=nowPlaying" }
HmpLog.i(LogTag.AgentRadio) { "📻 RadioSubAgent created | targetCount=12 | temp=0.2 | hasLLM=true" }
HmpLog.w(LogTag.AgentMaster, e) { "🤖 startHello failed | source=Greet | reason=timeout | non-fatal" }
HmpLog.e(LogTag.AgentEnrich) { "📚 chunk write failed | chunk=001 | retryExhausted" }
```

```swift
HmpLog.i(HmpTag.playerIos, "▶️ Engine ready | seekMs=\(pendingSeekPosition)")
HmpLog.w(HmpTag.libraryArtwork, "🖼️ save failed | non-fatal | reason=\(error)")
```

## 5. 统一入口

三端各有一个唯一出口，除此之外不得直连 Kermit / `print` / `NSLog`。

### Kotlin（`shared/src/commonMain/kotlin/com/hmp/log/`）

`LogTag`（tag 常量，编译期护栏）+ `HmpLog`（唯一门面，零新依赖，底层委托 Kermit）：

```kotlin
enum class LogTag(val v: String) {          // 一域一条，共 38 条（见 §2）
    AgentMaster("Agent.Master"), /* … */ MediaNowPlaying("Media.NowPlaying");
}
object HmpLog {
    fun d(tag: LogTag, msg: () -> String) = Logger.d(tag.v) { msg() }
    fun i(tag: LogTag, msg: () -> String) = Logger.i(tag.v) { msg() }
    fun w(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.w(e, tag.v) { msg() }
    fun e(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.e(e, tag.v) { msg() }
}
```

用法一律 `HmpLog.level(LogTag.X) { "…" }`，带异常时 `HmpLog.w(LogTag.X, e) { "…" }`。**四个签名冻结**（改了，§6 全部判据与 5 个脚本的正则一起失效）。

### Swift / iOS（`ios/HMP/HMP/Common/HmpLog.swift`）

`HmpTag`（与 `LogTag` 对应的字符串常量，28 条）+ `HmpLog`（Swift 门面，委托 `platformLog`）：

```swift
enum HmpTag { static let playerIos = "Player.Ios"; /* … 28 条，与 LogTag 同步 … */ }
enum HmpLevel { static let debug = 0; static let info = 1; static let warn = 2; static let error = 3 }
enum HmpLog {   // d / w / e 同理
    static func i(_ tag: String, _ message: @autoclosure () -> String) { PlatformLogKt.platformLog(severity: Int32(HmpLevel.info), tag: tag, message: message()) }
}
```

调用点一律 `HmpLog.i(HmpTag.playerIos, "▶️ …")`；**禁止直接 `PlatformLogKt.platformLog(...)`**（那是桥接实现，不是门面）。桥接：`expect fun platformLog(severity, tag, message)` 定义在 `shared/src/commonMain/kotlin/com/hmp/PlatformLog.kt`，三端 actual 委托 Kermit；severity 码两端一致：0=Debug / 1=Info / 2=Warn / 3=Error（`severityFromInt`）。

### 初始化

`initKermit(minSeverity)` / `initKermitForIos(isReleaseBuild)` 在各端入口调用一次，设定最低级别并挂载 `MemLogWriter`（内存环形缓冲，默认 200 条，供 Agent 监控看板订阅）；**Release 构建屏蔽 DEBUG/INFO**。实测 4 处调用：`android/app/.../MusicApplication.kt:32`（`BuildConfig.DEBUG ? Debug : Warn`）、`desktop/app/.../Main.kt:59`（`releaseBuild ? Warn : Debug`）、`ios/HMP/HMP/AppDelegate.swift:12,14`（`#if DEBUG` → false / true）。

## 6. 检查手段

约束：新增或修改日志代码后，在仓库根用 Git Bash 跑下面**这一个**代码块，期望值见其后表格；装了 ripgrep 时各条可用 `rg` 等价形态。5 个配套脚本在 `build/`（未入库，本地工具）。

```bash
# C1 裸输出通道（println / printStackTrace / System.out / NSLog / android Log）——期望 1 行（测试豁免）
grep -rnE --include=*.kt --include=*.swift --exclude-dir=build '\b(println|printStackTrace|NSLog)\(|System\.(out|err)\.|\bLog\.[dviwe]\(' shared shared-ui android desktop ios shared-ios
# C2 裸调 Kermit（门面 / 桥接 / 初始化三处豁免之外）——期望 0 行
grep -rnE --include=*.kt --exclude-dir=build '\bLogger\.(d|i|w|e)\(' shared shared-ui android desktop ios shared-ios | grep -vE 'com/hmp/log/|/PlatformLog\.|KermitInit\.kt'
# C3 Swift 裸桥接 / 裸 print（门面文件之外）——期望 0 行
grep -rnE --include=*.swift --exclude-dir=build 'PlatformLogKt\.platformLog|\bprint\(|NSLog\(' ios shared-ios | grep -v 'Common/HmpLog.swift'
# C4 硬编码 tag / 平行 TAG 常量——各期望 0 行
grep -rnE --include=*.kt --include=*.swift --exclude-dir=build 'HmpLog\.[diwe]\(\s*"' shared shared-ui android desktop ios shared-ios
grep -rnE --include=*.kt --exclude-dir=build '(private[[:space:]]+)?(const[[:space:]]+)?val[[:space:]]+TAG[[:space:]]*=' shared shared-ui android desktop shared-ios
# C5 tag 计数与两端对齐（Swift 字符类必须含 0-9，否则漏 playerMedia3）——期望 38 / 28
grep -cE '^[[:space:]]+[A-Za-z][A-Za-z0-9]*\("' shared/src/commonMain/kotlin/com/hmp/log/LogTag.kt
grep -cE '^[[:space:]]*static let [A-Za-z0-9]+ = "' ios/HMP/HMP/Common/HmpLog.swift
node build/check-tag-sync.js
# C6 调用点总数（本文唯一引用口径）——期望 464
grep -rE 'HmpLog\.[diwe]\(' --include='*.kt' shared shared-ui android desktop | grep -v /build/ | wc -l
# C7 ERROR 用于可恢复语义——期望 0 行
grep -rnE --include=*.kt --include=*.swift --exclude-dir=build 'HmpLog\.e\(.*(non-fatal|nonFatal|降级|ignored)' shared shared-ui android desktop ios shared-ios
# C8 三端初始化——期望 4 行，release 分支为 Severity.Warn / true
grep -rnE --include=*.kt --include=*.swift --exclude-dir=build 'initKermit(ForIos)?\(' shared shared-ui android desktop ios shared-ios | grep -v 'KermitInit.kt'
# C9 空 catch 体（降级零留痕）——当前 26 处，基线只降不升
grep -rnE --include=*.kt --exclude-dir=build 'catch[[:space:]]*\([^)]*\)[[:space:]]*\{[[:space:]]*\}' shared shared-ui android desktop shared-ios
node build/verify-logs.js    # 总闸：非豁免违规 0 + 「✅ 全仓日志调用点合规」
node build/check-emoji.js    # 缺域 emoji——期望 0，当前 5（D-1）
node build/audit-logs.js     # 语义审计——期望全 0
node build/count-hmplog.js   # 模块分布（raw 口径，≠ C6）
```

| 检查 | 期望（2026-09-28 实测） | 失守含义 |
|---|---|---|
| C1 | **1 行**：`shared/src/commonTest/.../OpenAiLlmTransportTest.kt:73`（测试豁免） | 出现非测试的裸输出通道 |
| C2 / C3 / C4 / C7 | 各 **0 行** | 绕过门面 / 硬编码或平行 tag / ERROR 记降级 |
| C5 | Kotlin **38** · Swift **28** · `check-tag-sync.js`「✅ 对齐」 | 与 §2 清单不符 = 三处同步漏一处 |
| C6 | **464 处**（引用总数不带本口径，就是 R21 式漂移） | 数字与代码脱节 |
| C8 / C9 | **4 行**（release 走 `Warn` / `true`）· **26 处**（基线只降不升） | 漏初始化 → DEBUG 不被屏蔽；新增静默吞异常 |
| `verify-logs.js` | 非豁免违规 **0**（含硬编码 tag 0 / 平行常量 0） | 出现第二日志通道 |
| `check-emoji.js` / `audit-logs.js` / `count-hmplog.js` | 缺 **5**（含 Swift 的门面调用点口径，≠ C6）· 语义层全 **0** · 模块分布（raw 口径） | 漏 token / tag 漂移 / 数与表脱节 |

**同步纪律**：新增域三处同步（§2）；动了日志代码，同批更新本表期望值与「偏差登记」——过期计数本身就是失真（R21）。

## 偏差登记

口径：只登记、不静默改写。证据行号以 2026-09-28 工作树为坐标，动手前按符号名复验。

| 编号 | 与代码不符处 | 证据（file:line） | 归属 |
|---|---|---|---|
| D-1 | **5 处调用点缺域 emoji token**（违反 §4 硬要求） | `shared-ui/.../ui/startup/DefaultPlaylistGuard.kt:72`（WARN）、`:74`（ERROR）；`android/core-player/.../controller/MusicController.kt:881`；`desktop/core-player/.../DesktopMusicController.kt:506`；`desktop/core-player/.../FFmpegAudioEngine.kt:94` | R21 |
| D-2 | **8 处 WARN 携带曲名或模型响应原文**，而三端 release 门槛正是 Warn → 这些行照常进 Logcat / OSLog / stdout 与 `MemLogWriter`（200 条）看板 | `shared/.../agent/profile/UserMemory.kt:180`（`$rawPredicate`）；`.../enrich/EnrichResponseParser.kt:85`（`${song.music.title}`）、`:283`（`${cleaned.take(200)}`）；`.../enrich/EnrichSubAgent.kt:510`（`title.take(20)`）；`.../radio/RadioSession.kt:434`（`${raw.take(120)}`）；`.../radio/RadioSubAgent.kt:426`、`:966`（`${track.title}`）、`:1416`（`${lastFailure?.take(120)}`） | R9 |
| D-3 | **26 处空 catch 体只吞不打日志**（违反 §3 降级留痕），待清、基线只降不升 | 代表：`shared/src/desktopMain/.../SettingsRepositoryImpl.desktop.kt:376`、`shared/src/iosMain/.../SettingsRepositoryImpl.ios.kt:381`、`shared-ui/.../AppRoot.kt:209`、`.../FloatingLyricsService.kt:98`、`.../FloatingLyricsOverlay.kt:78,107`、`desktop/app/.../Main.kt:113`、`.../SingleInstanceGuard.kt:52,55`、`desktop/core-player/.../DesktopMusicController.kt`（12 处）、`FFmpegAudioEngine.kt`（4 处）；全量以 C9 现跑为准 | R10 / R28 |
| D-4 | **计数陷阱**：tag 值里有数字（`Player.Media3`），用 `[A-Za-z]+` 之类字符类去数会**两侧各漏 1**（曾据此得出 37 / 27 的错误结论）。正确口径见 §6 C5 的两条命令：Kotlin **38** / Swift **28** | `shared/src/commonMain/kotlin/com/hmp/log/LogTag.kt`、`ios/HMP/HMP/Common/HmpLog.swift:36` | 本文 §6 C5 || D-5 | 「门槛 Warn 后 DEBUG/INFO 真被屏蔽」只有代码级证据，**无三端实机核验**；`preflight` 与 `pr-check.yml` 均不含日志检查（§6 全靠人跑） | `KermitInit.kt:19,28`；`MusicApplication.kt:32`；`desktop/.../Main.kt:59`；`AppDelegate.swift:12,14`；闸门侧 grep 0 命中 | R29 / R50 |

## 修订记录

- **2026-09-28**：整篇重写为精简统一格式（原 243 行 → 本版 189 行）：删去速查表、按检查项逐条的长命令块、`rg` 命令对照表与历史覆盖状态存档；§1–§6 编号与事实不变，新增「偏差登记」「修订记录」。
- **2026-09-28**：数字更正 —— 调用点统一为 **464 处**（口径 = §6 C6，旧稿 502 / 512 / 507 一律作废）；tag 计数经实跑复核为 **38 / 28**（审查口径 37 / 27 未复现，见 D-4）。2026-09-18 版覆盖数据（502 处 / 45 文件 / 100% emoji）见 git 历史。
- **沿革**（原「附」并入）：f5 引入 Kermit 定框架（`docs/archive/7_x/7_2/taskbook/f5-体系重塑.md`）；2026-09-18 全仓推广 —— `LogTag` 11 → 38 条 / 7 域、新增 Swift 门面并迁移 51 处桥接调用、agent 域 302 处补域 emoji token，执行记录见 `TODO.md` 与 `docs/archive/7_x/7_2/taskbook/f9-报告与设置.md`（⑭⑮）。
