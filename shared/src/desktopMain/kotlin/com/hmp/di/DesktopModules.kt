package com.hmp.di

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.AgentMessageDao
import com.hmp.data.database.AgentAuditLogDao
import com.hmp.data.database.AgentTaskDao
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
import com.hmp.data.di.sharedModule
import com.hmp.data.network.BuiltInApiKeyProvider
import com.hmp.data.repository.BackupFileRepositoryImpl
import com.hmp.data.repository.MusicRepositoryImpl
import com.hmp.data.repository.PlaylistRepositoryImpl
import com.hmp.data.repository.SettingsRepositoryImpl
import com.hmp.data.util.DataStoreFactory
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val desktopPlatformModule = module {
    single { DataStoreFactory.create() }

    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder()) }

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
single<AuditLogPort> { RoomAuditLogAdapter(get<AgentAuditLogDao>()) }
single<AgentMessageStore> { RoomAgentMessageStore(get<AgentMessageDao>()) }

    single { BuiltInApiKeyProvider() } // Desktop: 占位符
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::PlaylistRepositoryImpl) bind PlaylistRepository::class
    singleOf(::MusicRepositoryImpl) bind MusicRepository::class
    singleOf(::BackupFileRepositoryImpl) bind BackupFileRepository::class
}

fun initKoinDesktop(vararg additionalModules: org.koin.core.module.Module) {
    val t = System.currentTimeMillis()
    val modules = mutableListOf(sharedModule, desktopPlatformModule)
    modules.addAll(additionalModules)
    println("[Startup] +${System.currentTimeMillis() - t}ms — module list assembled")
    val t2 = System.currentTimeMillis()
    startKoin {
        modules(modules)
    }
    println("[Startup] +${System.currentTimeMillis() - t2}ms — startKoin {} execution")
}
