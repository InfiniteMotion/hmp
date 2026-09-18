package com.hmp.log

/**
 * 统一日志 tag 常量（编译期护栏，配合 docs/LOGGING.md 规范）。
 *
 * 命名格式 `{域}.{组件}{.子件}`，点分隔，每词首字母大写。
 * 一个模块一条根 tag；子路径用 `.` 展开，**禁止在调用点硬编码字符串、禁止另建平行 tag**。
 *
 * 新增域时仅在枚举追加条目，不改 [HmpLog] 签名。
 */
enum class LogTag(val v: String) {

    // ── Agent 域：agent 运行时 ──────────────────────────────────────────
    AgentMaster("Agent.Master"),
    AgentSub("Agent.Sub"),
    AgentEnrich("Agent.Enrich"),
    AgentHello("Agent.Hello"),
    AgentRadio("Agent.Radio"),
    AgentReActLoop("Agent.ReActLoop"),
    AgentScheduler("Agent.Scheduler"),
    AgentTool("Agent.Tool"),
    AgentLlmCall("Agent.LlmCall"),
    AgentContext("Agent.ContextBudget"),
    AgentProfile("Agent.Profile"),

    /** 对话侧网关（原平行 tag `Agent.Gateway`） */
    AgentGateway("Agent.Gateway"),

    /** 对话页 ViewModel 与气泡流（原平行 tag `Agent.Chat`） */
    AgentChat("Agent.Chat"),

    /** 决策面端口实现：播放控制 / 跳过事件 / DJ 衔接（原平行 tag `Agent.Port`） */
    AgentPort("Agent.Port"),

    // ── UI 域：shared-ui Compose ────────────────────────────────────────
    UiChat("UI.Chat"),
    UiSettings("UI.Settings"),
    UiNavigation("UI.Navigation"),
    UiCommon("UI.Common"),

    // ── Data 域：repository / db / 网络 ─────────────────────────────────
    DataRepository("Data.Repository"),
    DataRoom("Data.Room"),
    DataNet("Data.Net"),

    /** 曲库仓库（原平行 tag `Repo.Music`） */
    DataMusicRepo("Data.MusicRepo"),

    /** 后台备份文件读写（原平行 tag `BackupFileRepository`） */
    DataBackup("Data.Backup"),

    // ── Player 域：三端播放引擎 ─────────────────────────────────────────
    PlayerCore("Player.Core"),
    PlayerFfmpeg("Player.Ffmpeg"),
    PlayerMedia3("Player.Media3"),

    /** Android 音效链（Equalizer / BassBoost / Virtualizer / Reverb） */
    PlayerAudioEffect("Player.AudioEffect"),

    /** Android MediaSession / 前台服务 / 媒体通知 */
    PlayerService("Player.Service"),

    /** iOS 播放控制器与媒体会话（Swift 侧 `MusicPlayerController` 等） */
    PlayerIos("Player.Ios"),

    /** iOS 音频焦点与会话管理（Swift 侧 `AudioSessionManager`） */
    PlayerAudioSession("Player.AudioSession"),

    // ── System 域：初始化 / DI / 生命周期 ───────────────────────────────
    SystemInit("System.Init"),
    SystemDi("System.Di"),

    /** 桌面端窗口与主题（原平行 tag `DwmHelper` / `WindowHelper`） */
    SystemWindow("System.Window"),

    /** 应用入口与单实例（原平行 tag `Main` / `Startup`） */
    SystemLifecycle("System.Lifecycle"),

    // ── Library 域：曲库扫描与元数据 ────────────────────────────────────
    LibraryScan("Library.Scan"),

    /** iOS Swift 侧元数据解析（`MusicMetadataParserBridge`） */
    LibraryMetadata("Library.Metadata"),

    /** iOS Swift 侧封面抽取与缓存（`ArtworkExtractor`） */
    LibraryArtwork("Library.Artwork"),

    // ── Media 域：系统媒体集成 ──────────────────────────────────────────
    /** iOS Live Activity / 锁屏 Now Playing */
    MediaNowPlaying("Media.NowPlaying"),
    ;
}
