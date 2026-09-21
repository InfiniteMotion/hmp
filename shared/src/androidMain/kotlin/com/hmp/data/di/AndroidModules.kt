package com.hmp.data.di

import android.content.Context
import com.hmp.data.database.AppDatabase
import com.hmp.data.database.AgentMessageDao
import com.hmp.data.database.AgentAuditLogDao
import com.hmp.data.database.HelloCardCacheDao
import com.hmp.data.database.HelloReportNarrativeDao
import com.hmp.data.database.UserProfileEvidenceDao
import com.hmp.data.database.UserProfileNarrativeDao
import com.hmp.data.database.UserProfilePortraitDao
import com.hmp.data.database.ForgottenDeliveryDao
import com.hmp.data.database.AgentTaskDao
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.ListeningDurationDao
import com.hmp.data.database.MusicAllDao
import com.hmp.data.database.MusicDao
import com.hmp.data.database.MusicExtraDao
import com.hmp.data.database.MusicLabelDao
import com.hmp.data.database.PlaybackHistoryDao
import com.hmp.data.database.PlaylistDao
import com.hmp.data.database.PlaylistItemDao
import com.hmp.data.database.RoomAgentMessageStore
import com.hmp.data.database.RoomAuditLogAdapter
import com.hmp.data.database.UserInfoDao
import com.hmp.data.database.getDatabaseBuilder
import com.hmp.data.database.getRoomDatabase
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.network.createHttpClient
import com.hmp.data.repository.BackupFileRepositoryImpl
import com.hmp.data.repository.MusicRepositoryImpl
import com.hmp.data.repository.PlaylistRepositoryImpl
import com.hmp.data.repository.SettingsRepositoryImpl
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val androidPlatformModule = module {
    single<AppDatabase> {
        val context: Context = get()
        getRoomDatabase(getDatabaseBuilder(context))
    }

    single<MusicDao> { get<AppDatabase>().musicDao() }
    single<MusicExtraDao> { get<AppDatabase>().musicExtraDao() }
    single<UserInfoDao> { get<AppDatabase>().userInfoDao() }
    single<MusicAllDao> { get<AppDatabase>().musicAllDao() }
    single<MusicLabelDao> { get<AppDatabase>().musicLabelDao() }
    single<PlaylistDao> { get<AppDatabase>().playlistDao() }
    single<PlaylistItemDao> { get<AppDatabase>().playlistItemDao() }
    single<PlaybackHistoryDao> { get<AppDatabase>().playbackHistoryDao() }
    single<ListeningDurationDao> { get<AppDatabase>().listeningDurationDao() }
    single<AgentTaskDao> { get<AppDatabase>().agentTaskDao() }
    single<AgentAuditLogDao> { get<AppDatabase>().agentAuditLogDao() }
    single<AgentMessageDao> { get<AppDatabase>().agentMessageDao() }
    single<HelloCardCacheDao> { get<AppDatabase>().helloCardCacheDao() }
    single<HelloReportNarrativeDao> { get<AppDatabase>().helloReportNarrativeDao() }
    single<UserProfileEvidenceDao> { get<AppDatabase>().userProfileEvidenceDao() }
    single<UserProfilePortraitDao> { get<AppDatabase>().userProfilePortraitDao() }
    single<UserProfileNarrativeDao> { get<AppDatabase>().userProfileNarrativeDao() }
    single<ForgottenDeliveryDao> { get<AppDatabase>().forgottenDeliveryDao() }
    single<TokenLedgerDao> { get<AppDatabase>().tokenLedgerDao() }
    single<AuditLogPort> { RoomAuditLogAdapter(get<AgentAuditLogDao>()) }
    single<AgentMessageStore> { RoomAgentMessageStore(get<AgentMessageDao>()) }

    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::PlaylistRepositoryImpl) bind PlaylistRepository::class
    singleOf(::MusicRepositoryImpl) bind MusicRepository::class
    singleOf(::BackupFileRepositoryImpl) bind BackupFileRepository::class

    // 注：`Json` 单例由 sharedModule 的 `single { createJson() }` 提供（三端统一），
    // 本模块曾再注册一份**内容逐字相同**的 `single<Json>` —— 属重复定义：
    // Koin 默认 override=true 使后者静默覆盖前者，今天无害，但一旦两处漂移
    // （改一处忘了另一处）就会让 Android 与 Desktop/iOS 用上不同的 Json 配置，
    // 且不会有任何编译或运行期报错。F13 DI 图核对时删除（2026-09-21）。
}
