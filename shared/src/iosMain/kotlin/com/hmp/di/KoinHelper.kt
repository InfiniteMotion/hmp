package com.hmp.di

import com.hmp.domain.music.MusicRepository
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.LoadMusicFromDeviceUseCase
import com.hmp.domain.music.usecase.SyncMusicFromDeviceIncrementalUseCase
import com.hmp.domain.music.usecase.SearchMusicUseCase
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.music.usecase.MusicLabelUseCase
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.playlist.usecase.GeneratePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.model.UserUsageAnalytics
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import com.hmp.domain.setting.usecase.GetUserUsageDataUseCase
import com.hmp.domain.backup.usecase.ExportUserDataBackupUseCase
import com.hmp.domain.backup.usecase.ImportUserDataBackupUseCase
import com.hmp.domain.backup.usecase.GetBackupsUseCase
import com.hmp.domain.backup.usecase.DeleteBackupUseCase
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import kotlinx.coroutines.flow.first
import org.koin.mp.KoinPlatform

// Music
fun getGetAllMusicUseCase(): GetAllMusicUseCase =
    KoinPlatform.getKoin().get()

fun getLoadMusicFromDeviceUseCase(): LoadMusicFromDeviceUseCase =
    KoinPlatform.getKoin().get()

fun getSyncMusicFromDeviceIncrementalUseCase(): SyncMusicFromDeviceIncrementalUseCase =
    KoinPlatform.getKoin().get()

fun getSearchMusicUseCase(): SearchMusicUseCase =
    KoinPlatform.getKoin().get()

fun getGetDailyMusicRecommendationUseCase(): GetDailyMusicRecommendationUseCase =
    KoinPlatform.getKoin().get()

fun getRemoveFromLibraryUseCase(): RemoveFromLibraryUseCase =
    KoinPlatform.getKoin().get()

fun getRestoreToLibraryUseCase(): RestoreToLibraryUseCase =
    KoinPlatform.getKoin().get()

fun getGetDeletedMusicIdsGroupedByFolderUseCase(): GetDeletedMusicIdsGroupedByFolderUseCase =
    KoinPlatform.getKoin().get()

fun getMusicLabelUseCase(): MusicLabelUseCase =
    KoinPlatform.getKoin().get()

fun getMusicRepository(): MusicRepository =
    KoinPlatform.getKoin().get()

// Playlist
fun getManagePlaylistUseCase(): ManagePlaylistUseCase =
    KoinPlatform.getKoin().get()

fun getGeneratePlaylistUseCase(): GeneratePlaylistUseCase =
    KoinPlatform.getKoin().get()

// Settings / Playback
fun getCurrentPlaybackUseCase(): CurrentPlaybackUseCase =
    KoinPlatform.getKoin().get()

fun getPlaybackHistoryUseCase(): PlaybackHistoryUseCase =
    KoinPlatform.getKoin().get()

fun getTimerUseCase(): TimerUseCase =
    KoinPlatform.getKoin().get()

fun getSettingsRepository(): SettingsRepository =
    KoinPlatform.getKoin().get()

fun getUserSettingsUseCase(): UserSettingsUseCase =
    KoinPlatform.getKoin().get()

fun getLyricsSettingsUseCase(): LyricsSettingsUseCase =
    KoinPlatform.getKoin().get()

fun getGetUserUsageDataUseCase(): GetUserUsageDataUseCase =
    KoinPlatform.getKoin().get()

// Backup
fun getExportUserDataBackupUseCase(): ExportUserDataBackupUseCase =
    KoinPlatform.getKoin().get()

fun getImportUserDataBackupUseCase(): ImportUserDataBackupUseCase =
    KoinPlatform.getKoin().get()

fun getGetBackupsUseCase(): GetBackupsUseCase =
    KoinPlatform.getKoin().get()

fun getDeleteBackupUseCase(): DeleteBackupUseCase =
    KoinPlatform.getKoin().get()

// Flow → suspend helpers for iOS
suspend fun getSettingsEqualizerPreset(): Int =
    getSettingsRepository().equalizerPreset.first()

suspend fun getSettingsCustomEqualizerLevels(): FloatArray =
    getSettingsRepository().customEqualizerLevels.first()

suspend fun saveSettingsCustomEqualizerLevels(levels: FloatArray) =
    getSettingsRepository().saveCustomEqualizerLevels(levels)

suspend fun getSettingsGalleryOrderBy(): String =
    getSettingsRepository().galleryOrderBy.first()

suspend fun saveSettingsGalleryOrderBy(orderBy: String) =
    getSettingsRepository().saveGalleryOrderBy(orderBy)

suspend fun getSettingsGalleryOrderType(): String =
    getSettingsRepository().galleryOrderType.first()

suspend fun saveSettingsGalleryOrderType(orderType: String) =
    getSettingsRepository().saveGalleryOrderType(orderType)

suspend fun getSettingsBassBoostLevel(): Int =
    getSettingsRepository().bassBoostLevel.first()

suspend fun getSettingsIsSurroundSoundEnabled(): Boolean =
    getSettingsRepository().isSurroundSoundEnabled.first()

suspend fun getSettingsReverbPreset(): Int =
    getSettingsRepository().reverbPreset.first()

suspend fun getSettingsUserName(): String =
    getUserSettingsUseCase().userName.first()

suspend fun getSettingsCustomMode(): String =
    getUserSettingsUseCase().customMode.first()

suspend fun getSettingsBackgroundStyle(): String =
    getUserSettingsUseCase().backgroundStyle.first()

suspend fun getSettingsAutoBatchProcess(): Boolean =
    getUserSettingsUseCase().autoBatchProcess.first()

suspend fun getLabelNamesByTypeFirst(category: LabelCategory): List<LabelName> {
    return getMusicLabelUseCase().getLabelNamesByType(category).first()
}

suspend fun getMusicWithExtraCount(): Int =
    getGetAllMusicUseCase().getMusicWithExtraCount().first()

suspend fun getMusicWithMissingExtraCount(): Int =
    getGetAllMusicUseCase().getMusicWithMissingExtraCount().first()

// === 富化生命周期已迁移到 MasterAgent/EnrichSubAgent，旧版 autoProcess 方法已删除 ===
// iOS 侧如果需要桥接 MasterAgent，需要先从 shared-ui 的 chatGatewayModule 获取

suspend fun autoProcessMissingExtra() {
    // TODO(iOS): 桥接到 MasterAgent.startEnrich() — 需要引入 MasterAgent 依赖
}

fun pauseAutoProcess() { /* TODO(iOS): 桥接到 MasterAgent.pauseEnrich() */ }
fun resumeAutoProcess() { /* TODO(iOS): 桥接到 MasterAgent.resumeEnrich() */ }
fun cancelAutoProcess() { /* TODO(iOS): 桥接到 MasterAgent.stopEnrich() */ }
fun resetAutoProcessState() { /* no-op: 旧版状态管理已删除 */ }
fun getAutoProcessIsPaused(): Boolean = false
fun getAutoProcessIsCancelled(): Boolean = false

fun getPinyinInitial(title: String): String {
    if (title.isEmpty()) return "#"
    val firstChar = title[0]
    if (firstChar.code in 65..90 || firstChar.code in 97..122) {
        return firstChar.uppercaseChar().toString()
    }
    val pinyin = com.hmp.data.util.PinyinLookupTable.getPinyin(firstChar)
    if (pinyin != null && pinyin.isNotEmpty()) {
        return pinyin[0].uppercaseChar().toString()
    }
    return "#"
}

suspend fun getSettingsDailyRefreshMode(): String =
    getUserSettingsUseCase().dailyRefreshMode.first()

suspend fun getSettingsDailyRefreshHours(): Int =
    getUserSettingsUseCase().dailyRefreshHours.first()

suspend fun getSettingsDailyRefreshStartupCount(): Int =
    getUserSettingsUseCase().dailyRefreshStartupCount.first()

suspend fun getSettingsCurrentPosition(): Long =
    getSettingsRepository().currentPosition.first()

suspend fun getCurrentMusicId(): Long? =
    getCurrentPlaybackUseCase().getCurrentMusicId().first()

suspend fun getPlaybackHistory(musicId: Long, limit: Int = 5): List<PlaybackHistory> =
    getPlaybackHistoryUseCase().getPlaybackHistory(musicId, limit).first()

suspend fun getUserUsageAnalytics(): UserUsageAnalytics =
    getGetUserUsageDataUseCase().getAnalytics()

suspend fun exportBackup(): String =
    getExportUserDataBackupUseCase().invoke().getOrThrow()

suspend fun importBackup(filePath: String) {
    getImportUserDataBackupUseCase().invoke(filePath).getOrThrow()
}

suspend fun getBackupFiles(): List<String> =
    getGetBackupsUseCase().invoke().getOrThrow()

suspend fun deleteBackupFile(filePath: String) {
    getDeleteBackupUseCase().invoke(filePath).getOrThrow()
}
