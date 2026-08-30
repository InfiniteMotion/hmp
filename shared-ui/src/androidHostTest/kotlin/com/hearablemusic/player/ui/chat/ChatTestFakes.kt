package com.hearablemusic.player.ui.chat

import com.hmp.domain.agent.engine.RunContextInput
import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.backup.AppSettingsSnapshot
import com.hmp.domain.backup.DailyRecommendationSnapshot
import com.hmp.domain.config.DailyRefreshConfig
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ScanDirectoryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** 可控事件流的 Fake 网关：测试手动 [emitEvent] 驱动 ChatViewModel。 */
class FakeChatAgentGateway : ChatAgentGateway {
    val events = MutableSharedFlow<ChatAgentEvent>(extraBufferCapacity = 256)

    override fun run(
        input: String,
        config: AiEndpointConfig,
        bridge: ConfirmBridge,
        ctx: RunContextInput,
    ): Flow<ChatAgentEvent> = events

    suspend fun emitEvent(event: ChatAgentEvent) = events.emit(event)
}

/** 精简 SettingsRepository 桩：ChatViewModel 仅经 UserSettingsUseCase 触碰 getActiveAiConfig。 */
class MinimalSettingsRepository : SettingsRepository {
    override val isFirstLaunch: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    override suspend fun saveIsFirstLaunch(isFirstLaunch: Boolean) = unused()

    override val userName: Flow<String> get() = kotlinx.coroutines.flow.flowOf("")
    override suspend fun saveUserName(name: String) = unused()

    override val themeMode: Flow<String> get() = kotlinx.coroutines.flow.flowOf("default")
    override suspend fun saveThemeMode(themeMode: String) = unused()

    override val backgroundStyle: Flow<String> get() = kotlinx.coroutines.flow.flowOf("FLUID")
    override suspend fun saveBackgroundStyle(style: String) = unused()

    override val hazeMode: Flow<String> get() = kotlinx.coroutines.flow.flowOf("custom")
    override suspend fun saveHazeMode(mode: String) = unused()

    override val hazeMaterialPreset: Flow<String> get() = kotlinx.coroutines.flow.flowOf("regular")
    override suspend fun saveHazeMaterialPreset(preset: String) = unused()

    override val hazeBlurRadius: Flow<Float> get() = kotlinx.coroutines.flow.flowOf(20f)
    override suspend fun saveHazeBlurRadius(radius: Float) = unused()

    override val hazeNoiseFactor: Flow<Float> get() = kotlinx.coroutines.flow.flowOf(0.15f)
    override suspend fun saveHazeNoiseFactor(noiseFactor: Float) = unused()

    override val hazeTintAlpha: Flow<Float> get() = kotlinx.coroutines.flow.flowOf(0.22f)
    override suspend fun saveHazeTintAlpha(alpha: Float) = unused()

    override val hazeIntensity: Flow<Float> get() = kotlinx.coroutines.flow.flowOf(0f)
    override suspend fun saveHazeIntensity(intensity: Float) = unused()

    override val isLoadMusic: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun saveIsLoadMusic(isLoadMusic: Boolean) = unused()

    override val scanDirectoryConfig: Flow<ScanDirectoryConfig> get() = kotlinx.coroutines.flow.flowOf(ScanDirectoryConfig())
    override suspend fun saveScanDirectoryConfig(config: ScanDirectoryConfig) = unused()

    override suspend fun saveAvatarUri(uri: String) = unused()
    override suspend fun getAvatarUri(): String? = unused()

    override val currentMusicId: Flow<Long?> get() = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun saveCurrentMusicId(id: Long) = unused()

    override val currentPosition: Flow<Long> get() = kotlinx.coroutines.flow.flowOf(0L)
    override suspend fun saveCurrentPosition(position: Long) = unused()

    override val currentPlaylistId: Flow<Long?> get() = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun saveCurrentPlaylistId(playlistId: Long) = unused()

    override val likedPlaylistId: Flow<Long?> get() = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun saveLikedPlaylistId(playlistId: Long) = unused()
    override suspend fun getLikedPlaylistId(): Long? = unused()

    override val recentPlaylistId: Flow<Long?> get() = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun saveRecentPlaylistId(playlistId: Long) = unused()
    override suspend fun getRecentPlaylistId(): Long? = unused()
    override suspend fun getCurrentPlaylistId(): Long? = unused()

    override val aiAccessMode: Flow<AiAccessMode> get() = kotlinx.coroutines.flow.flowOf(AiAccessMode.FREE)
    override suspend fun getAiAccessMode(): AiAccessMode = AiAccessMode.FREE
    override suspend fun saveAiAccessMode(mode: AiAccessMode) = unused()

    override suspend fun getCustomAiConfig(): AiEndpointConfig = AiEndpointConfig(isConfigured = true)
    override suspend fun saveCustomAiConfig(config: AiEndpointConfig) = unused()
    override suspend fun getActiveAiConfig(): AiEndpointConfig = AiEndpointConfig(isConfigured = true, selectedModel = "test")

    override val aiFreeTrialRemainingCount: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(100)
    override suspend fun getAiFreeTrialRemainingCount(): Int = 100
    override suspend fun decrementAiFreeTrialCount() = unused()

    override val equalizerPreset: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    override suspend fun saveEqualizerPreset(preset: Int) = unused()

    override val bassBoostLevel: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    override suspend fun saveBassBoostLevel(level: Int) = unused()

    override val isSurroundSoundEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun saveSurroundSoundEnabled(enabled: Boolean) = unused()

    override val reverbPreset: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    override suspend fun saveReverbPreset(preset: Int) = unused()

    override val customEqualizerLevels: Flow<FloatArray> get() = kotlinx.coroutines.flow.flowOf(FloatArray(0))
    override suspend fun saveCustomEqualizerLevels(levels: FloatArray) = unused()

    override val autoBatchProcess: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    override suspend fun saveAutoBatchProcess(enabled: Boolean) = unused()

    override val dailyRefreshMode: Flow<String> get() = kotlinx.coroutines.flow.flowOf("off")
    override suspend fun saveDailyRefreshMode(mode: String) = unused()

    override val lyricsPlayerConfig: Flow<String> get() = kotlinx.coroutines.flow.flowOf("{}")
    override suspend fun saveLyricsPlayerConfig(json: String) = unused()
    override suspend fun getLyricsPlayerConfig(): String = "{}"

    override val lyricsFullscreenConfig: Flow<String> get() = kotlinx.coroutines.flow.flowOf("{}")
    override suspend fun saveLyricsFullscreenConfig(json: String) = unused()
    override suspend fun getLyricsFullscreenConfig(): String = "{}"

    override val lyricsFloatingConfig: Flow<String> get() = kotlinx.coroutines.flow.flowOf("{}")
    override suspend fun saveLyricsFloatingConfig(json: String) = unused()
    override suspend fun getLyricsFloatingConfig(): String = "{}"

    override val floatingLyricsEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun saveFloatingLyricsEnabled(enabled: Boolean) = unused()

    @Deprecated("legacy")
    override val lyricsOriginalTextSize: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(16)
    @Deprecated("legacy")
    override suspend fun saveLyricsOriginalTextSize(size: Int) = unused()
    @Deprecated("legacy")
    override suspend fun getLyricsOriginalTextSize(): Int = 16

    @Deprecated("legacy")
    override val lyricsTranslatedTextSize: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(14)
    @Deprecated("legacy")
    override suspend fun saveLyricsTranslatedTextSize(size: Int) = unused()
    @Deprecated("legacy")
    override suspend fun getLyricsTranslatedTextSize(): Int = 14

    @Deprecated("legacy")
    override val lyricsCurrentTimeTextSize: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(12)
    @Deprecated("legacy")
    override suspend fun saveLyricsCurrentTimeTextSize(size: Int) = unused()
    @Deprecated("legacy")
    override suspend fun getLyricsCurrentTimeTextSize(): Int = 12

    @Deprecated("legacy")
    override val lyricsLineSpacing: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(8)
    @Deprecated("legacy")
    override suspend fun saveLyricsLineSpacing(spacing: Int) = unused()
    @Deprecated("legacy")
    override suspend fun getLyricsLineSpacing(): Int = 8

    @Deprecated("legacy")
    override val lyricsDisplayMode: Flow<com.hmp.domain.config.DisplayMode> get() = kotlinx.coroutines.flow.flowOf(com.hmp.domain.config.DisplayMode.DUAL)
    @Deprecated("legacy")
    override suspend fun saveLyricsDisplayMode(mode: com.hmp.domain.config.DisplayMode) = unused()
    @Deprecated("legacy")
    override suspend fun getLyricsDisplayMode(): com.hmp.domain.config.DisplayMode = com.hmp.domain.config.DisplayMode.DUAL

    @Deprecated("legacy")
    override val lyricsAlignment: Flow<com.hmp.domain.config.LyricsAlignment> get() = kotlinx.coroutines.flow.flowOf(com.hmp.domain.config.LyricsAlignment.CENTER)
    @Deprecated("legacy")
    override suspend fun saveLyricsAlignment(alignment: com.hmp.domain.config.LyricsAlignment) = unused()
    @Deprecated("legacy")
    override suspend fun getLyricsAlignment(): com.hmp.domain.config.LyricsAlignment = com.hmp.domain.config.LyricsAlignment.CENTER

    override val lyricsKaraokeEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    override suspend fun saveLyricsKaraokeEnabled(enabled: Boolean) = unused()
    override suspend fun getLyricsKaraokeEnabled(): Boolean = true

    override val dailyRefreshHours: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(8)
    override suspend fun saveDailyRefreshHours(hours: Int) = unused()

    override val dailyRefreshStartupCount: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(5)
    override suspend fun saveDailyRefreshStartupCount(count: Int) = unused()

    override val lastDailyRefreshTimestamp: Flow<Long> get() = kotlinx.coroutines.flow.flowOf(0L)
    override suspend fun updateLastDailyRefreshTimestamp() = unused()

    override val appLaunchCountSinceRefresh: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    override suspend fun incrementAppLaunchCount() = unused()

    override suspend fun getDailyRefreshConfig(): DailyRefreshConfig =
        DailyRefreshConfig("off", 8, 5, 0L, 0)
    override suspend fun saveCurrentDailyMusicId(musicId: Long) = unused()
    override suspend fun getCurrentDailyMusicId(): Long? = unused()

    override val galleryOrderBy: Flow<String> get() = kotlinx.coroutines.flow.flowOf("title")
    override suspend fun saveGalleryOrderBy(orderBy: String) = unused()

    override val galleryOrderType: Flow<String> get() = kotlinx.coroutines.flow.flowOf("ASC")
    override suspend fun saveGalleryOrderType(orderType: String) = unused()

    override val defaultAlgorithmType: Flow<String> get() = kotlinx.coroutines.flow.flowOf("OPTIMIZED_SIMILARITY")
    override suspend fun saveDefaultAlgorithmType(type: String) = unused()
    override suspend fun getDefaultAlgorithmType(): String = "OPTIMIZED_SIMILARITY"

    override val defaultWeightTemplate: Flow<String> get() = kotlinx.coroutines.flow.flowOf("BALANCED")
    override suspend fun saveDefaultWeightTemplate(template: String) = unused()
    override suspend fun getDefaultWeightTemplate(): String = "BALANCED"

    override val defaultExtensionConfig: Flow<String> get() = kotlinx.coroutines.flow.flowOf("{}")
    override suspend fun saveDefaultExtensionConfig(configJson: String) = unused()
    override suspend fun getDefaultExtensionConfig(): String = "{}"

    override suspend fun exportAppSettingsSnapshot(): AppSettingsSnapshot = unused()
    override suspend fun restoreFromSnapshot(snapshot: AppSettingsSnapshot) = unused()
    override suspend fun exportDailyRecommendationSnapshot(): DailyRecommendationSnapshot? = unused()
    override suspend fun restoreDailyRecommendationSnapshot(snapshot: DailyRecommendationSnapshot) = unused()
    override suspend fun backupSettings(): Result<String> = unused()
    override suspend fun restoreSettings(backupFilePath: String): Result<Unit> = unused()
    override suspend fun cleanOldBackups(keepCount: Int): Result<Unit> = unused()

    @Suppress("unused")
    private fun unused(): Nothing = throw UnsupportedOperationException("Unused in ChatViewModel test")
}