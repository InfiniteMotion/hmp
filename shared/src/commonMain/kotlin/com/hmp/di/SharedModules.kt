package com.hmp.data.di

import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.network.OpenAiLlmTransport
import com.hmp.data.network.createHttpClient
import com.hmp.data.network.createJson
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.PlaybackObservationBus
import com.hmp.domain.agent.runtime.GlobalTokenCounter
import com.hmp.domain.agent.runtime.TokenMeter
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.music.usecase.LoadMusicFromDeviceUseCase
import com.hmp.domain.music.usecase.EditMusicTagsUseCase
import com.hmp.domain.music.usecase.MusicLabelUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.music.usecase.SearchMusicUseCase
import com.hmp.domain.music.usecase.SyncMusicFromDeviceIncrementalUseCase
import com.hmp.domain.playlist.usecase.GeneratePlaylistUseCase
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.GetUserUsageDataUseCase
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hmp.domain.backup.usecase.DeleteBackupUseCase
import com.hmp.domain.backup.usecase.ExportUserDataBackupUseCase
import com.hmp.domain.backup.usecase.GetBackupsUseCase
import com.hmp.domain.backup.usecase.ImportUserDataBackupUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Agent 身份标识——按身份创建独立 Transport 实例（共享同一个 HttpClient 连接池） */
const val AGENT_CHAT = "chat"
const val AGENT_ENRICH = "enrich"
const val AGENT_HELLO = "hello"
const val AGENT_RADIO = "radio"

/**
 * 跨平台共享 Koin 模块 — 包含所有平台通用的依赖。
 * 平台特定模块（Database、Repository）在 androidMain/iosMain 分别提供。
 *
 * LLM 传输层设计（方案 B）：
 *  1 个 HttpClient（共享连接池，默认 16 连接，四 Agent 并发足够）
 *  → 4 个独立 OpenAiCompatibleAdapter 实例
 *  → 4 个命名 OpenAiLlmTransport 实例（每个 Agent 一个）
 *  好处：Agent 间逻辑隔离（独立 AgentContextBudget / 独立 error 路径），
 *  物理上共用连接池不浪费资源。
 */
val sharedModule = module {
    single { createJson() }

    // 观测面总线：三端播放控制器往里报事实（TrackSettled / Pause），agent 侧订阅。
    // 必须单例 —— 控制器与 MasterAgent 看到的是同一个实例，否则事件收不到。
    // 控制器侧拿 Sink（只写），agent 侧拿 Bus（订阅 Flow）。
    singleOf(::PlaybackObservationBus)

    // ── F12-T1：Token 计量基础设施 ──
    // 全局日配额计数器：MasterAgent 与 TokenMeter 共享同一实例，
    // 保证"计量累加"和"配额熔断"读的是同一份数据（否则闸门看不见自己的消费）。
    single { GlobalTokenCounter(timeProvider = { currentTimeMillis() }) }

    // 唯一记账口：一次 LLM 调用写三处 —— 当日累加（GlobalTokenCounter）·
    // 窗口占用（各 AgentContextBudget 自行更新）· 明细账本（token_ledger）。
    // ledgerDao 由平台模块提供（Room），跨模块解析。
    single {
        TokenMeter(
            counter = get(),
            ledgerDao = get(),
            timeProvider = { currentTimeMillis() },
        )
    }

    // 用户认识模块（画像）自 v3.6 起**归属 MasterAgent**（`masterAgent.userMemory`）：
    // 不再单独注册 Koin 单例 —— 装配方只需注入三个 DAO，Master 负责构建与暴露。
    // 注入路径由三条收敛为一条：`masterAgent.userMemory`。

    // ① 1 个 HttpClient 实例 + 1 个 Adapter（所有 Agent 共享底层连接池和 Adapter）
    single { createHttpClient(get()) }
    single { OpenAiCompatibleAdapter(get(), get()) }

    // ② 4 个独立 Transport 实例，每个 Agent 一个（共享同一个 HttpClient + Adapter）
    single<LlmTransport>(named(AGENT_CHAT)) { OpenAiLlmTransport(adapter = get(), json = get()) }
    single<LlmTransport>(named(AGENT_ENRICH)) { OpenAiLlmTransport(adapter = get(), json = get()) }
    single<LlmTransport>(named(AGENT_HELLO)) { OpenAiLlmTransport(adapter = get(), json = get()) }
    single<LlmTransport>(named(AGENT_RADIO)) { OpenAiLlmTransport(adapter = get(), json = get()) }

    // 保留无命名 LlmTransport 供遗留代码兜底（逐步迁移中）
    single<LlmTransport> { get(named(AGENT_CHAT)) }

    // Use Cases
    single { GetAllMusicUseCase(get()) }
    single { SearchMusicUseCase(get()) }
    single { LoadMusicFromDeviceUseCase(get()) }
    single { SyncMusicFromDeviceIncrementalUseCase(get()) }
    single { RemoveFromLibraryUseCase(get()) }
    single { RestoreToLibraryUseCase(get()) }
    single { GetDeletedMusicIdsGroupedByFolderUseCase(get()) }
    single { MusicLabelUseCase(get(), get()) }
    single { EditMusicTagsUseCase(get()) }
    single { GetDailyMusicRecommendationUseCase(get(), get()) }
    single { ManagePlaylistUseCase(get(), get()) }
    single { GeneratePlaylistUseCase(get(), get()) }
    single { UserSettingsUseCase(get()) }
    single { LyricsSettingsUseCase(get()) }
    single { CurrentPlaybackUseCase(get(), get(), get()) }
    single { PlaybackHistoryUseCase(get()) }
    single { GetUserUsageDataUseCase(get()) }
    single { TimerUseCase(get()) }
    single { ExportUserDataBackupUseCase(get(), get(), get(), get()) }
    single { ImportUserDataBackupUseCase(get(), get(), get(), get()) }
    single { GetBackupsUseCase(get()) }
    single { DeleteBackupUseCase(get()) }
}
