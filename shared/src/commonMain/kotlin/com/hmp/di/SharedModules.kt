package com.hmp.data.di

import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.network.OpenAiLlmTransport
import com.hmp.data.network.createHttpClient
import com.hmp.data.network.createJson
import com.hmp.domain.agent.port.LlmTransport
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
