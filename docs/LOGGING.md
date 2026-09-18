# 统一日志规范（Logging Convention）

> 长期指导文档 ｜ 底层框架：[Touchlab Kermit]（KMP 三端原生桥接：Android→Logcat / iOS→OSLog / Desktop→stdout+ANSI）
> 本规范定义项目日志的**统一做法**：tag 命名、级别语义、消息格式、统一入口。所有新老代码一律遵守。

## 1. 原则

统一框架 ≠ 统一规范。Kermit 只是底层通道，真正让日志可用的是大家用同一套 tag、级别、格式打日志。因此：

1. **一个模块一个 tag**，禁止平行 tag。
2. **ERROR 永不用于可恢复情况**。
3. **消息一律结构化**，便于 grep 与机器解析。
4. **只经统一入口 `HmpLog` 打日志**，禁止散落 `print*` 或直接裸调其他通道。
5. **保持极简**：不引文件落盘、不引崩溃上报、不做链路 ID、不做高阶 wrapper。

## 2. Tag 命名体系

统一格式：`{域}.{组件}{.子件}`，点分隔，每词首字母大写。

```
Agent                          # agent 运行时域
  Agent.Master  /  Agent.Sub  /  Agent.Enrich  /  Agent.Hello  /  Agent.Radio
  Agent.ReActLoop  /  Agent.Scheduler  /  Agent.Tool  /  Agent.LlmCall  /  Agent.ContextBudget
  Agent.Profile  /  Agent.Gateway  /  Agent.Chat  /  Agent.Port

UI                             # shared-ui Compose 域
  UI.Chat  /  UI.Settings  /  UI.Navigation  /  UI.Common

Data                           # repository / db / 网络
  Data.Repository  /  Data.Room  /  Data.Net  /  Data.MusicRepo  /  Data.Backup

Player                         # 三端播放引擎
  Player.Core  /  Player.Ffmpeg  /  Player.Media3  /  Player.AudioEffect
  Player.Service  /  Player.Ios  /  Player.AudioSession

System                         # 初始化 / DI / 生命周期 / 窗口
  System.Init  /  System.Di  /  System.Lifecycle  /  System.Window

Library                        # 曲库扫描与元数据
  Library.Scan  /  Library.Metadata  /  Library.Artwork

Media                          # 系统媒体集成
  Media.NowPlaying
```

规则：
- tag 一律来自集中常量 `LogTag`（Kotlin）/ `HmpTag`（Swift），**禁止在调用点硬编码字符串**。
- 一个模块只允许一个根 tag；子路径用 `.` 展开，**禁止另建平行 tag 常量**。
- 子件只写业务细分，不写行号、不写本次动作（动作属于消息正文）。
- 新增域时**三处同步**：`LogTag.kt` 枚举 + `HmpTag.swift` 常量 + 本节清单。

**双端登记的收敛原则**：`HmpTag.swift` 只登记 **Swift 侧可达** 的 tag，不逐条镜像 `LogTag`。纯 Kotlin 内部产生的 tag（当前为 10 个 Agent 子标签：`Agent.Sub` / `Agent.Enrich` / `Agent.Hello` / `Agent.Radio` / `Agent.ReActLoop` / `Agent.Scheduler` / `Agent.Tool` / `Agent.LlmCall` / `Agent.ContextBudget` / `Agent.Profile`）不在 Swift 侧重复声明，避免死常量。**Kotlin 38 条 ↔ Swift 28 条**（差 10 条 = 上述 Kotlin-only；其余 28 条两端逐字相同，Swift 是 Kotlin 的真子集）。新增 tag 时判断依据：「Swift 会不会用它打日志」。

### 作用范围

| 范围 | 是否适用 | 说明 |
|------|---------|------|
| 应用运行时代码（`shared` / `shared-ui` / 三端壳与引擎） | ✅ 一律适用 | 唯一出口 `HmpLog` |
| Swift / iOS 桥接层 | ✅ 一律适用 | 唯一出口 `HmpLog.swift`（内部委托 `platformLog`） |
| 构建脚本（`build.gradle.kts`） | ❌ 不适用 | Gradle 任务输出用 `println`，不进 Kermit 通道 |
| 单元测试（`*Test.kt`） | ⚠️ 豁免 | 诊断性输出可用 `println`；测试内如需断言日志请用 Fake writer |
| `com.hmp.log` 包自身 与 `PlatformLog.*.kt` actual | ❌ 豁免 | 它们是门面与桥接实现，本就直连 Kermit |


## 3. Level 语义

| Level | 语义 | 该用 | 严禁 |
|-------|------|------|------|
| `DEBUG` | 内部细节，仅逐行排查用 | 中间变量、解析中间态、每次 tick、去重检查 | 用户路径关键节点 |
| `INFO` | 生命周期 / 关键动作发生与结果 | 模块创建、启停、配置生效、批次完成、状态流转 | 循环体内高频动作 |
| `WARN` | 可恢复异常 / 降级但继续 / 非预期输入 | 捕获后降级的失败、覆盖率未达标、跳过某次刷新 | — |
| `ERROR` | 不可恢复、需人介入 | 依赖缺失无法启动、LLM 连续失败无降级、db 写失败 | **non-fatal** |

纪律：
- `ERROR` 永不用于可恢复情况。
- `"... failed (non-fatal)"` 一律 `WARN`。
- 高频循环内用 `DEBUG`，避免刷屏淹没关键信息。

## 4. 消息格式

模板：`[域Token] 事件名 | key=value | key=value`

| 元素 | 约束 | 示例 |
|------|------|------|
| 域Token | 每域固定 emoji，一眼可辨来源 | 见下表 |
| 事件名 | 动词开头，概括本动作 | `RadioSubAgent created` ／ `updateAiConfig` ／ `startRadio` |
| key=value | 以 ` \| ` 分隔；key 用 snake_case | `targetCount=12 \| temp=0.2 \| hasLLM=true` |
| 字符串值 | 含空格/特殊字符用双引号 | `title="夜曲" ／ endpoint="https://..."` |
| 敏感值 | 截断展示 | `apiKey=sk-ab12...(前6位)` |

**域 Token 对照表**：

| 域 | Token | 域 | Token |
|---|---|---|---|
| Agent.Master | 🤖 | Data（全部） | 🎵 曲库 ／ 🌐 网络 ／ 💾 备份 ／ 🗄️ 其他 |
| Agent.Sub | 🧩 | Player（FFmpeg/Media3/Core） | 🎬 ／ ▶️ |
| Agent.Enrich | 📚 | Player.AudioEffect | 🎛️ |
| Agent.Hello | 👋 | Player.Service | 📡 |
| Agent.Radio | 📻 | Player.AudioSession | 🔊 |
| Agent.ReActLoop | 🔁 | Player.Ios | ▶️ |
| Agent.Scheduler | ⏱️ | System（Init/Di/Lifecycle） | 🚀 |
| Agent.Tool | 🛠️ | System.Window | 🪟 |
| Agent.LlmCall | 🧠 | Library.Scan | 📂 |
| Agent.ContextBudget | 📐 | Library.Metadata | 🏷️ |
| Agent.Profile | 🫀 | Library.Artwork | 🖼️ |
| Agent.Gateway | 🌉 | Media.NowPlaying | 📺 |
| Agent.Chat / UI.Chat | 💬 | UI.Settings | ⚙️ |
| Agent.Port | 🔌 | UI.Navigation | 🧭 |
| UI.Common | 🎨 | | |

```kotlin
// DEBUG —— 内部中间态
HmpLog.d(LogTag.AgentRadio) { "📻 extractSeedLabels | matched=3 | from=nowPlaying" }

// INFO —— 生命周期
HmpLog.i(LogTag.AgentRadio) {
    "📻 RadioSubAgent created | targetCount=12 | temp=0.2 | hasLLM=true | hasKey=true"
}

// WARN —— 可恢复失败
HmpLog.w(LogTag.AgentMaster) { "🤖 startHello failed | source=Greet | reason=timeout" }

// ERROR —— 不可恢复
HmpLog.e(LogTag.AgentEnrich) { "📚 chunk write failed | chunk=001 | retryExhausted" }
```

```swift
// Swift / iOS —— 唯一出口 HmpLog（tag 取自 HmpTag）
HmpLog.i(HmpTag.playerIos, "▶️ Engine ready, seeking to: \(pendingSeekPosition)ms")
HmpLog.w(HmpTag.libraryArtwork, "🖼️ save failed | non-fatal | reason=\(error)")
```

## 5. 统一入口

三端各有一个唯一出口，除此之外不得直连 Kermit / `print` / `NSLog`。

### Kotlin（`shared/src/commonMain/kotlin/com/hmp/log/`）

`LogTag`（tag 常量，编译期护栏）+ `HmpLog`（唯一门面，零新依赖，底层委托 Kermit）。

```kotlin
// LogTag.kt —— 一域一条，覆盖 7 个域（见 §2）
enum class LogTag(val v: String) {
    AgentMaster("Agent.Master"), /* … 共 38 条，此处略 … */ MediaNowPlaying("Media.NowPlaying");
}
```

```kotlin
// HmpLog.kt
object HmpLog {
    fun d(tag: LogTag, msg: () -> String) = Logger.d(tag.v) { msg() }
    fun i(tag: LogTag, msg: () -> String) = Logger.i(tag.v) { msg() }
    fun w(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.w(e, tag.v) { msg() }
    fun e(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.e(e, tag.v) { msg() }
}
```

用法：一律 `HmpLog.level(LogTag.X) { "..." }`；带异常时 `HmpLog.w(LogTag.X, e) { "..." }`。

### Swift / iOS（`ios/HMP/HMP/Common/HmpLog.swift`）

`HmpTag`（与 `LogTag` 逐条对应的字符串常量）+ `HmpLog`（Swift 门面，委托 `platformLog`）。

```swift
enum HmpTag { static let playerIos = "Player.Ios"; /* … 与 LogTag 同步 … */ }
enum HmpLevel { static let debug = 0; static let info = 1; static let warn = 2; static let error = 3 }

enum HmpLog {
    static func i(_ tag: String, _ message: @autoclosure () -> String) {
        PlatformLogKt.platformLog(severity: Int32(HmpLevel.info), tag: tag, message: message())
    }
    // d / w / e 同理
}
```

Swift 调用点一律 `HmpLog.i(HmpTag.playerIos, "▶️ …")`，**禁止直接 `PlatformLogKt.platformLog(...)`**（那是桥接实现，不是门面）。

底层桥接：`expect fun platformLog(severity, tag, message)`，三端 actual 委托 Kermit；`severity` 码两端一致（0=Debug / 1=Info / 2=Warn / 3=Error，定义在 `PlatformLog.kt` 的 `severityFromInt`）。

### 初始化

`initKermit(minSeverity)` / `initKermitForIos(isReleaseBuild)` 在各端入口调用一次，负责设定最低级别并挂载 `MemLogWriter`（内存环形缓冲，供 Agent 监控看板订阅）。Release 构建屏蔽 DEBUG/INFO。


## 6. 检查手段

自检命令（在仓库根执行）：

| 检查项 | 命令 |
|--------|------|
| Kotlin 侧无裸调 Kermit | `rg 'Logger\.' --glob '*.kt' --glob '!**/com/hmp/log/**' --glob '!**/PlatformLog.*.kt' --glob '!**/KermitInit.kt' --glob '!**/build/**'` |
| Kotlin 侧无 Android 原生 Log | `rg '\bLog\.[dviwe]\(' --glob '*.kt' --glob '!**/build/**'` |
| Kotlin 侧无 `printStackTrace` / `System.out` | `rg 'printStackTrace|System\.(out\|err)\.' --glob '*.kt' --glob '!**/build/**'` |
| Swift 侧无裸桥接调用 | `rg 'PlatformLogKt\.platformLog' --glob '*.swift'` |
| Swift 侧无裸 `print` / `NSLog` | `rg '\bprint\(|NSLog\(' --glob '*.swift'` |
| `ERROR` 不用于降级语义 | `rg 'HmpLog\.e\(.*(non-fatal\|nonFatal\|降级)' --glob '*.kt'` |
| 调用点 tag 来自集中常量 | `rg 'HmpLog\.[diwe]\(\s*"' --glob '*.kt' --glob '*.swift'` 应为空 |
| 消息带域 emoji token | `node build/check-emoji.js`（应报 0 缺失） |
| tag 无平行常量 | 检查 `enum class LogTag` 外是否还有别的 tag 常量声明 |

**配套脚本**（`build/` 下，跨平台、不依赖 rg）：

| 脚本 | 用途 |
|---|---|
| `build/verify-logs.js` | 全仓日志调用点合规扫描（含豁免判定） |
| `build/check-emoji.js` | 门面调用点的域 emoji token 覆盖率 |
| `build/audit-logs.js` | 语义层审计（tag 漂移 / ERROR 误用 / 裸 print） |
| `build/count-hmplog.js` | 按模块统计调用点分布 |
| `build/check-tag-sync.js` | Kotlin `LogTag` ↔ Swift `HmpTag` 对齐校验 |

纪律：
- 新增域**三处同步**：`LogTag.kt` 枚举、`HmpTag.swift` 常量、本文档 §2 清单。不改 `HmpLog` 签名。
- 例外范围见 §2「作用范围」表：构建脚本、测试代码、`com.hmp.log` 包自身与 `PlatformLog` actual 豁免。
- **消息必须带域 emoji token**（§4）——这是硬要求，不是可选装饰，用上面的脚本自查。

### 当前覆盖状态（2026-09-18）

全仓推广已完成：`HmpLog` 调用点 **502 处 / 45 文件**，覆盖 7 域 **38 个 tag（Kotlin）/ 28 个 tag（Swift）**。**域 emoji token 覆盖率 100%（502/502）**。

| 检查项 | 结果 |
|---|---|
| 非豁免违规（Kermit 直调 / android Log / printStackTrace / NSLog / 裸桥接） | **0** |
| 硬编码 tag 字符串 | **0** |
| 平行 tag 常量 | **0** |
| 缺域 emoji token 的调用点 | **0** |
| ERROR 用于可恢复语义 | **0** |

| 侧 | 状态 |
|---|---|
| `shared`（commonMain / 三端 actual 仓库） | ✅ 全部迁至 `HmpLog` |
| `shared-ui`（含 chat 包 / Android 平台服务） | ✅ 全部迁至 `HmpLog` |
| `android/core-player`（音效 / 播放服务 / 通知） | ✅ 全部迁至 `HmpLog` |
| `desktop/app` + `desktop/core-player` | ✅ 全部迁至 `HmpLog` |
| iOS Swift（21 个文件） | ✅ 全部迁至 `HmpLog.swift` |
| 构建脚本 `build.gradle.kts` | ⚪ 豁免（用 `println`） |
| 测试代码 | ⚪ 豁免 |


## 附

- 底层框架引入背景见 `docs/7_x/B agent-build/taskbook/f5-体系重塑.md`：f5 解决"用什么框架"，本规范解决"怎么统一打日志"。
- **全仓推广**（2026-09-18，原仅在 agent 模块应用）：新增 UI / Data / Player / System / Library / Media 六域共 27 个 tag（枚举 11 → 38 条），Kotlin 侧 `HmpLog` 调用点增至 502 处，Swift 侧新增 `HmpLog.swift` 门面并迁移 51 处桥接调用。执行记录见 `TODO.md` 与 `docs/7_x/B agent-build/taskbook/f9-报告与设置.md`。
- **emoji token 补齐**（2026-09-18）：agent 域 302 处调用点原缺域 emoji token（首版试点时未加，推广脚本只处理了字符串字面量形态，漏掉变量 tag 形态），已按 §4 对照表全量补齐，覆盖率 100%。
