import Foundation

/// 统一日志 tag 常量（Swift 侧）—— 与 Kotlin `com.hmp.log.LogTag` 枚举对应。
///
/// 规范见 `docs/LOGGING.md`：一个模块一个 tag，禁止在调用点硬编码字符串。
/// 新增域时先在该文件与 Kotlin `LogTag` 同步登记，再使用。
///
/// **范围说明**：只登记 Swift 侧**可达**的 tag。Kotlin 侧另有 10 个 Agent 内部
/// 子标签（`Agent.Sub` / `Agent.Enrich` / `Agent.Hello` / `Agent.Radio` /
/// `Agent.ReActLoop` / `Agent.Scheduler` / `Agent.Tool` / `Agent.LlmCall` /
/// `Agent.ContextBudget` / `Agent.Profile`）只由 Kotlin 实现产生日志，Swift 无从调用，
/// 故此处不重复声明（避免死常量）。若 iOS 将来需要，再从 Kotlin 侧补登记。
enum HmpTag {
    // Agent 域
    static let agentMaster = "Agent.Master"
    static let agentGateway = "Agent.Gateway"
    static let agentChat = "Agent.Chat"
    static let agentPort = "Agent.Port"

    // UI 域
    static let uiChat = "UI.Chat"
    static let uiSettings = "UI.Settings"
    static let uiNavigation = "UI.Navigation"
    static let uiCommon = "UI.Common"

    // Data 域
    static let dataRepository = "Data.Repository"
    static let dataRoom = "Data.Room"
    static let dataNet = "Data.Net"
    static let dataMusicRepo = "Data.MusicRepo"
    static let dataBackup = "Data.Backup"

    // Player 域
    static let playerCore = "Player.Core"
    static let playerFfmpeg = "Player.Ffmpeg"
    static let playerMedia3 = "Player.Media3"
    static let playerAudioEffect = "Player.AudioEffect"
    static let playerService = "Player.Service"
    static let playerIos = "Player.Ios"
    static let playerAudioSession = "Player.AudioSession"

    // System 域
    static let systemInit = "System.Init"
    static let systemDi = "System.Di"
    static let systemWindow = "System.Window"
    static let systemLifecycle = "System.Lifecycle"

    // Library 域
    static let libraryScan = "Library.Scan"
    static let libraryMetadata = "Library.Metadata"
    static let libraryArtwork = "Library.Artwork"

    // Media 域
    static let mediaNowPlaying = "Media.NowPlaying"
}

/// 日志级别（与 Kotlin `com.hmp.severityFromInt` 的 severity 码一致：0=Debug 1=Info 2=Warn 3=Error）。
enum HmpLevel {
    static let debug = 0
    static let info = 1
    static let warn = 2
    static let error = 3
}

/// 统一日志入口（Swift 侧）—— 唯一出口，禁止直接 `print` 或 `NSLog`。
///
/// `tag` 一律取自 `HmpTag`；消息格式遵循 `docs/LOGGING.md` §4：
/// `[域Token] 事件名 | key=value | key=value`
enum HmpLog {
    static func d(_ tag: String, _ message: @autoclosure () -> String) {
        PlatformLogKt.platformLog(severity: Int32(HmpLevel.debug), tag: tag, message: message())
    }

    static func i(_ tag: String, _ message: @autoclosure () -> String) {
        PlatformLogKt.platformLog(severity: Int32(HmpLevel.info), tag: tag, message: message())
    }

    static func w(_ tag: String, _ message: @autoclosure () -> String) {
        PlatformLogKt.platformLog(severity: Int32(HmpLevel.warn), tag: tag, message: message())
    }

    static func e(_ tag: String, _ message: @autoclosure () -> String) {
        PlatformLogKt.platformLog(severity: Int32(HmpLevel.error), tag: tag, message: message())
    }
}
