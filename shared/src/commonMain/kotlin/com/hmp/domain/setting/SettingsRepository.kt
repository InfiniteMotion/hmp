package com.hmp.domain.setting

import com.hmp.domain.config.DailyRefreshConfig
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ScanDirectoryConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Basic Settings
    val isFirstLaunch: Flow<Boolean>
    suspend fun saveIsFirstLaunch(isFirstLaunch: Boolean)

    val userName: Flow<String>
    suspend fun saveUserName(name: String)

    val themeMode: Flow<String>
    suspend fun saveThemeMode(themeMode: String)

    val backgroundStyle: Flow<String>
    suspend fun saveBackgroundStyle(style: String)

    val hazeMode: Flow<String>
    suspend fun saveHazeMode(mode: String)

    val hazeMaterialPreset: Flow<String>
    suspend fun saveHazeMaterialPreset(preset: String)

    val hazeBlurRadius: Flow<Float>
    suspend fun saveHazeBlurRadius(radius: Float)

    val hazeNoiseFactor: Flow<Float>
    suspend fun saveHazeNoiseFactor(noiseFactor: Float)

    val hazeTintAlpha: Flow<Float>
    suspend fun saveHazeTintAlpha(alpha: Float)

    val hazeIntensity: Flow<Float>
    suspend fun saveHazeIntensity(intensity: Float)

    val isLoadMusic: Flow<Boolean>
    suspend fun saveIsLoadMusic(isLoadMusic: Boolean)

    // Scan Directory Config
    val scanDirectoryConfig: Flow<ScanDirectoryConfig>
    suspend fun saveScanDirectoryConfig(config: ScanDirectoryConfig)

    suspend fun saveAvatarUri(uri: String)
    suspend fun getAvatarUri(): String?

    // Playback State
    val currentMusicId: Flow<Long?>
    suspend fun saveCurrentMusicId(id: Long)
    
    val currentPosition: Flow<Long>
    suspend fun saveCurrentPosition(position: Long)

    val currentPlaylistId: Flow<Long?>
    suspend fun saveCurrentPlaylistId(playlistId: Long)

    // Special Playlists
    val likedPlaylistId: Flow<Long?>
    suspend fun saveLikedPlaylistId(playlistId: Long)
    suspend fun getLikedPlaylistId(): Long?

    val recentPlaylistId: Flow<Long?>
    suspend fun saveRecentPlaylistId(playlistId: Long)
    suspend fun getRecentPlaylistId(): Long?

    suspend fun getCurrentPlaylistId(): Long?

    // AI Access Mode
    val aiAccessMode: Flow<AiAccessMode>
    suspend fun getAiAccessMode(): AiAccessMode
    suspend fun saveAiAccessMode(mode: AiAccessMode)

    // Custom AI Config (user-provided endpoint + key + model)
    suspend fun getCustomAiConfig(): AiEndpointConfig
    suspend fun saveCustomAiConfig(config: AiEndpointConfig)

    // Active AI Config (returns config based on current mode)
    suspend fun getActiveAiConfig(): AiEndpointConfig

    // Agent Policy Config — per-Agent 信任档位 + 永远允许白名单
    // role: "master" | "enrich" | "radio"（与 AgentRole.name 对齐）
    suspend fun getAgentPolicyConfig(agentRole: String): com.hmp.domain.agent.policy.AgentPolicyConfig
    suspend fun saveAgentPolicyConfig(agentRole: String, config: com.hmp.domain.agent.policy.AgentPolicyConfig)

    // Free Trial Quota
    val aiFreeTrialRemainingCount: Flow<Int>
    suspend fun getAiFreeTrialRemainingCount(): Int
    suspend fun decrementAiFreeTrialCount()

    // Audio Effects
    val equalizerPreset: Flow<Int>
    suspend fun saveEqualizerPreset(preset: Int)

    val bassBoostLevel: Flow<Int>
    suspend fun saveBassBoostLevel(level: Int)

    val isSurroundSoundEnabled: Flow<Boolean>
    suspend fun saveSurroundSoundEnabled(enabled: Boolean)

    val reverbPreset: Flow<Int>
    suspend fun saveReverbPreset(preset: Int)

    val customEqualizerLevels: Flow<FloatArray>
    suspend fun saveCustomEqualizerLevels(levels: FloatArray)

    // AI Batch Process
    val autoBatchProcess: Flow<Boolean>
    suspend fun saveAutoBatchProcess(enabled: Boolean)

    // Daily Refresh Strategy
    val dailyRefreshMode: Flow<String>
    suspend fun saveDailyRefreshMode(mode: String)

    // Lyrics Configuration (per-component)
    val lyricsPlayerConfig: Flow<String>
    suspend fun saveLyricsPlayerConfig(json: String)
    suspend fun getLyricsPlayerConfig(): String

    val lyricsFullscreenConfig: Flow<String>
    suspend fun saveLyricsFullscreenConfig(json: String)
    suspend fun getLyricsFullscreenConfig(): String

    val lyricsFloatingConfig: Flow<String>
    suspend fun saveLyricsFloatingConfig(json: String)
    suspend fun getLyricsFloatingConfig(): String

    // Floating Lyrics toggle
    val floatingLyricsEnabled: Flow<Boolean>
    suspend fun saveFloatingLyricsEnabled(enabled: Boolean)

    // Lyrics Configuration (legacy - deprecated, use per-component configs above)
    @Deprecated("Use lyricsPlayerConfig instead")
    val lyricsOriginalTextSize: Flow<Int>
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun saveLyricsOriginalTextSize(size: Int)
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun getLyricsOriginalTextSize(): Int

    @Deprecated("Use lyricsPlayerConfig instead")
    val lyricsTranslatedTextSize: Flow<Int>
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun saveLyricsTranslatedTextSize(size: Int)
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun getLyricsTranslatedTextSize(): Int

    @Deprecated("Use lyricsPlayerConfig instead")
    val lyricsCurrentTimeTextSize: Flow<Int>
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun saveLyricsCurrentTimeTextSize(size: Int)
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun getLyricsCurrentTimeTextSize(): Int

    @Deprecated("Use lyricsPlayerConfig instead")
    val lyricsLineSpacing: Flow<Int>
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun saveLyricsLineSpacing(spacing: Int)
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun getLyricsLineSpacing(): Int

    @Deprecated("Use lyricsPlayerConfig instead")
    val lyricsDisplayMode: Flow<DisplayMode>
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun saveLyricsDisplayMode(mode: DisplayMode)
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun getLyricsDisplayMode(): DisplayMode

    @Deprecated("Use lyricsPlayerConfig instead")
    val lyricsAlignment: Flow<LyricsAlignment>
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun saveLyricsAlignment(alignment: LyricsAlignment)
    @Deprecated("Use lyricsPlayerConfig instead")
    suspend fun getLyricsAlignment(): LyricsAlignment

    val lyricsKaraokeEnabled: Flow<Boolean>
    suspend fun saveLyricsKaraokeEnabled(enabled: Boolean)
    suspend fun getLyricsKaraokeEnabled(): Boolean

    val dailyRefreshHours: Flow<Int>
    suspend fun saveDailyRefreshHours(hours: Int)

    val dailyRefreshStartupCount: Flow<Int>
    suspend fun saveDailyRefreshStartupCount(count: Int)

    val lastDailyRefreshTimestamp: Flow<Long>
    suspend fun updateLastDailyRefreshTimestamp()

    val appLaunchCountSinceRefresh: Flow<Int>
    suspend fun incrementAppLaunchCount()

    suspend fun getDailyRefreshConfig(): DailyRefreshConfig

    suspend fun saveCurrentDailyMusicId(musicId: Long)
    suspend fun getCurrentDailyMusicId(): Long?

    // Gallery Sort
    val galleryOrderBy: Flow<String>
    suspend fun saveGalleryOrderBy(orderBy: String)

    val galleryOrderType: Flow<String>
    suspend fun saveGalleryOrderType(orderType: String)

    // Backup / Restore
    // Playlist Algorithm Configuration
    val defaultAlgorithmType: Flow<String>
    suspend fun saveDefaultAlgorithmType(type: String)
    suspend fun getDefaultAlgorithmType(): String
    
    val defaultWeightTemplate: Flow<String>
    suspend fun saveDefaultWeightTemplate(template: String)
    suspend fun getDefaultWeightTemplate(): String
    
    val defaultExtensionConfig: Flow<String>
    suspend fun saveDefaultExtensionConfig(configJson: String)
    suspend fun getDefaultExtensionConfig(): String
    
    // Snapshot Export/Import
    suspend fun exportAppSettingsSnapshot(): com.hmp.domain.backup.AppSettingsSnapshot
    suspend fun restoreFromSnapshot(snapshot: com.hmp.domain.backup.AppSettingsSnapshot)
    
    suspend fun exportDailyRecommendationSnapshot(): com.hmp.domain.backup.DailyRecommendationSnapshot?
    suspend fun restoreDailyRecommendationSnapshot(snapshot: com.hmp.domain.backup.DailyRecommendationSnapshot)

    suspend fun backupSettings(): Result<String>
    suspend fun restoreSettings(backupFilePath: String): Result<Unit>
    suspend fun cleanOldBackups(keepCount: Int = 3): Result<Unit>
}
