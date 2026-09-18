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

UI                             # shared-ui Compose 域
  UI.Chat  /  UI.Settings  /  UI.Navigation  /  UI.Common

Data                           # repository / db / 网络
  Data.Repository  /  Data.Room  /  Data.Net

Player                         # 三端播放引擎
  Player.Core  /  Player.Ffmpeg  /  Player.Media3

System                         # 初始化 / DI / 生命周期
  System.Init  /  System.Di
```

规则：
- tag 一律来自集中常量 `LogTag`，**禁止在调用点硬编码字符串**。
- 一个模块只允许一个根 tag；子路径用 `.` 展开，**禁止另建平行 tag 常量**。
- 子件只写业务细分，不写行号、不写本次动作（动作属于消息正文）。

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
| 域Token | 每域固定 emoji，一眼可辨来源 | 📻=Radio ／ 🤖=Master ／ 📚=Enrich ／ 👋=Hello ／ 🎙️=DJ ／ 🛠️=Tool |
| 事件名 | 动词开头，概括本动作 | `RadioSubAgent created` ／ `updateAiConfig` ／ `startRadio` |
| key=value | 以 ` \| ` 分隔；key 用 snake_case | `targetCount=12 \| temp=0.2 \| hasLLM=true` |
| 字符串值 | 含空格/特殊字符用双引号 | `title="夜曲" ／ endpoint="https://..."` |
| 敏感值 | 截断展示 | `apiKey=sk-ab12...(前6位)` |

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

## 5. 统一入口

`LogTag`（tag 常量，编译期护栏）+ `HmpLog`（唯一门面，零新依赖，底层委托 Kermit）。

新增 `shared/src/commonMain/kotlin/com/hmp/log/`：

```kotlin
// LogTag.kt
enum class LogTag(val v: String) {
    AgentMaster("Agent.Master"), AgentSub("Agent.Sub"),
    AgentEnrich("Agent.Enrich"), AgentHello("Agent.Hello"), AgentRadio("Agent.Radio"),
    AgentReActLoop("Agent.ReActLoop"), AgentScheduler("Agent.Scheduler"),
    AgentTool("Agent.Tool"), AgentLlmCall("Agent.LlmCall"), AgentContext("Agent.ContextBudget"),
    // 后续域：UiChat("UI.Chat"), DataRepo("Data.Repository"), PlayerCore("Player.Core"), SystemInit("System.Init")
}
```

```kotlin
// HmpLog.kt
import co.touchlab.kermit.Logger

object HmpLog {
    fun d(tag: LogTag, msg: () -> String) = Logger.d(tag.v) { msg() }
    fun i(tag: LogTag, msg: () -> String) = Logger.i(tag.v) { msg() }
    fun w(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.w(e, tag.v) { msg() }
    fun e(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.e(e, tag.v) { msg() }
}
```

用法：一律 `HmpLog.level(LogTag.X) { "..." }`；带异常时 `HmpLog.w(LogTag.X, e) { "..." }`。Swift/平台侧沿用 `platformLog(severity, tag, message)`，tag 取同一 `LogTag` 的字符串值。

## 6. 检查手段

- 全仓 `HmpLog\.` 覆盖所有日志；`print*` / `Logger.` 直调应为零。
- tag 无重复声明的平行常量；一模块一根 tag。
- `ERROR` 消息内不含 `non-fatal`、`nonFatal` 等降级语义字样。
- 新增域时仅在 `LogTag` 枚举登记，不改 `HmpLog` 签名。

## 附

- 底层框架引入背景见 `docs/7_x/B agent-build/taskbook/f5-体系重塑.md`：f5 解决"用什么框架"，本规范解决"怎么统一打日志"。
- 存量代码迁移状态在 `TODO.md` 跟踪，不属于本文档职责。