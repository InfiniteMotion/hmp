package com.hmp.test.fakes

import com.hmp.domain.backup.AppSettingsSnapshot
import com.hmp.domain.backup.DailyRecommendationSnapshot
import com.hmp.domain.config.DailyRefreshConfig
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ScanDirectoryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository : SettingsRepository {

    private val _isFirstLaunch = MutableStateFlow(true)
    override val isFirstLaunch: Flow<Boolean> = _isFirstLaunch.asStateFlow()
    override suspend fun saveIsFirstLaunch(isFirstLaunch: Boolean) { _isFirstLaunch.value = isFirstLaunch }

    private val _userName = MutableStateFlow("")
    override val userName: Flow<String> = _userName.asStateFlow()
    override suspend fun saveUserName(name: String) { _userName.value = name }

    private val _themeMode = MutableStateFlow("default")
    override val themeMode: Flow<String> = _themeMode.asStateFlow()
    override suspend fun saveThemeMode(themeMode: String) { _themeMode.value = themeMode }

    private val _backgroundStyle = MutableStateFlow("FLUID")
    override val backgroundStyle: Flow<String> = _backgroundStyle.asStateFlow()
    override suspend fun saveBackgroundStyle(style: String) { _backgroundStyle.value = style }

    private val _hazeMode = MutableStateFlow("custom")
    override val hazeMode: Flow<String> = _hazeMode.asStateFlow()
    override suspend fun saveHazeMode(mode: String) { _hazeMode.value = mode }

    private val _hazeMaterialPreset = MutableStateFlow("regular")
    override val hazeMaterialPreset: Flow<String> = _hazeMaterialPreset.asStateFlow()
    override suspend fun saveHazeMaterialPreset(preset: String) { _hazeMaterialPreset.value = preset }

    private val _hazeBlurRadius = MutableStateFlow(20f)
    override val hazeBlurRadius: Flow<Float> = _hazeBlurRadius.asStateFlow()
    override suspend fun saveHazeBlurRadius(radius: Float) { _hazeBlurRadius.value = radius }

    private val _hazeNoiseFactor = MutableStateFlow(0.15f)
    override val hazeNoiseFactor: Flow<Float> = _hazeNoiseFactor.asStateFlow()
    override suspend fun saveHazeNoiseFactor(noiseFactor: Float) { _hazeNoiseFactor.value = noiseFactor }

    private val _hazeTintAlpha = MutableStateFlow(0.22f)
    override val hazeTintAlpha: Flow<Float> = _hazeTintAlpha.asStateFlow()
    override suspend fun saveHazeTintAlpha(alpha: Float) { _hazeTintAlpha.value = alpha }

    private val _hazeIntensity = MutableStateFlow(0f)
    override val hazeIntensity: Flow<Float> = _hazeIntensity.asStateFlow()
    override suspend fun saveHazeIntensity(intensity: Float) { _hazeIntensity.value = intensity }

    private val _isLoadMusic = MutableStateFlow(false)
    override val isLoadMusic: Flow<Boolean> = _isLoadMusic.asStateFlow()
    override suspend fun saveIsLoadMusic(isLoadMusic: Boolean) { _isLoadMusic.value = isLoadMusic }

    private val _scanDirectoryConfig = MutableStateFlow(ScanDirectoryConfig())
    override val scanDirectoryConfig: Flow<ScanDirectoryConfig> = _scanDirectoryConfig.asStateFlow()
    override suspend fun saveScanDirectoryConfig(config: ScanDirectoryConfig) { _scanDirectoryConfig.value = config }

    private var avatarUri: String? = null
    override suspend fun saveAvatarUri(uri: String) { avatarUri = uri }
    override suspend fun getAvatarUri(): String? = avatarUri

    private val _currentMusicId = MutableStateFlow<Long?>(null)
    override val currentMusicId: Flow<Long?> = _currentMusicId.asStateFlow()
    override suspend fun saveCurrentMusicId(id: Long) { _currentMusicId.value = id }

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: Flow<Long> = _currentPosition.asStateFlow()
    override suspend fun saveCurrentPosition(position: Long) { _currentPosition.value = position }

    private val _currentPlaylistId = MutableStateFlow<Long?>(null)
    override val currentPlaylistId: Flow<Long?> = _currentPlaylistId.asStateFlow()
    override suspend fun saveCurrentPlaylistId(playlistId: Long) { _currentPlaylistId.value = playlistId }

    private val _likedPlaylistId = MutableStateFlow<Long?>(null)
    override val likedPlaylistId: Flow<Long?> = _likedPlaylistId.asStateFlow()
    override suspend fun saveLikedPlaylistId(playlistId: Long) { _likedPlaylistId.value = playlistId }
    override suspend fun getLikedPlaylistId(): Long? = _likedPlaylistId.value

    private val _recentPlaylistId = MutableStateFlow<Long?>(null)
    override val recentPlaylistId: Flow<Long?> = _recentPlaylistId.asStateFlow()
    override suspend fun saveRecentPlaylistId(playlistId: Long) { _recentPlaylistId.value = playlistId }
    override suspend fun getRecentPlaylistId(): Long? = _recentPlaylistId.value

    override suspend fun getCurrentPlaylistId(): Long? = _currentPlaylistId.value

    private val _aiAccessMode = MutableStateFlow(AiAccessMode.FREE)
    override val aiAccessMode: Flow<AiAccessMode> = _aiAccessMode.asStateFlow()
    override suspend fun getAiAccessMode(): AiAccessMode = _aiAccessMode.value
    override suspend fun saveAiAccessMode(mode: AiAccessMode) { _aiAccessMode.value = mode }

    private var customAiConfig = AiEndpointConfig()
    override suspend fun getCustomAiConfig(): AiEndpointConfig = customAiConfig
    override suspend fun saveCustomAiConfig(config: AiEndpointConfig) { customAiConfig = config }

    override suspend fun getActiveAiConfig(): AiEndpointConfig = when (_aiAccessMode.value) {
        AiAccessMode.FREE -> AiEndpointConfig(isConfigured = true)
        AiAccessMode.CUSTOM -> customAiConfig
        AiAccessMode.PAID -> AiEndpointConfig()
    }

    private val _aiFreeTrialRemainingCount = MutableStateFlow(100)
    override val aiFreeTrialRemainingCount: Flow<Int> = _aiFreeTrialRemainingCount.asStateFlow()
    override suspend fun getAiFreeTrialRemainingCount(): Int = _aiFreeTrialRemainingCount.value
    override suspend fun decrementAiFreeTrialCount() { _aiFreeTrialRemainingCount.value = (_aiFreeTrialRemainingCount.value - 1).coerceAtLeast(0) }

    private val _equalizerPreset = MutableStateFlow(0)
    override val equalizerPreset: Flow<Int> = _equalizerPreset.asStateFlow()
    override suspend fun saveEqualizerPreset(preset: Int) { _equalizerPreset.value = preset }

    private val _bassBoostLevel = MutableStateFlow(0)
    override val bassBoostLevel: Flow<Int> = _bassBoostLevel.asStateFlow()
    override suspend fun saveBassBoostLevel(level: Int) { _bassBoostLevel.value = level }

    private val _isSurroundSoundEnabled = MutableStateFlow(false)
    override val isSurroundSoundEnabled: Flow<Boolean> = _isSurroundSoundEnabled.asStateFlow()
    override suspend fun saveSurroundSoundEnabled(enabled: Boolean) { _isSurroundSoundEnabled.value = enabled }

    private val _reverbPreset = MutableStateFlow(0)
    override val reverbPreset: Flow<Int> = _reverbPreset.asStateFlow()
    override suspend fun saveReverbPreset(preset: Int) { _reverbPreset.value = preset }

    private val _customEqualizerLevels = MutableStateFlow(FloatArray(0))
    override val customEqualizerLevels: Flow<FloatArray> = _customEqualizerLevels.asStateFlow()
    override suspend fun saveCustomEqualizerLevels(levels: FloatArray) { _customEqualizerLevels.value = levels }

    private val _autoBatchProcess = MutableStateFlow(true)
    override val autoBatchProcess: Flow<Boolean> = _autoBatchProcess.asStateFlow()
    override suspend fun saveAutoBatchProcess(enabled: Boolean) { _autoBatchProcess.value = enabled }

    private val _dailyRefreshMode = MutableStateFlow("off")
    override val dailyRefreshMode: Flow<String> = _dailyRefreshMode.asStateFlow()
    override suspend fun saveDailyRefreshMode(mode: String) { _dailyRefreshMode.value = mode }

    private val _lyricsPlayerConfig = MutableStateFlow("{}")
    override val lyricsPlayerConfig: Flow<String> = _lyricsPlayerConfig.asStateFlow()
    override suspend fun saveLyricsPlayerConfig(json: String) { _lyricsPlayerConfig.value = json }
    override suspend fun getLyricsPlayerConfig(): String = _lyricsPlayerConfig.value

    private val _lyricsFullscreenConfig = MutableStateFlow("{}")
    override val lyricsFullscreenConfig: Flow<String> = _lyricsFullscreenConfig.asStateFlow()
    override suspend fun saveLyricsFullscreenConfig(json: String) { _lyricsFullscreenConfig.value = json }
    override suspend fun getLyricsFullscreenConfig(): String = _lyricsFullscreenConfig.value

    private val _lyricsFloatingConfig = MutableStateFlow("{}")
    override val lyricsFloatingConfig: Flow<String> = _lyricsFloatingConfig.asStateFlow()
    override suspend fun saveLyricsFloatingConfig(json: String) { _lyricsFloatingConfig.value = json }
    override suspend fun getLyricsFloatingConfig(): String = _lyricsFloatingConfig.value

    private val _floatingLyricsEnabled = MutableStateFlow(false)
    override val floatingLyricsEnabled: Flow<Boolean> = _floatingLyricsEnabled.asStateFlow()
    override suspend fun saveFloatingLyricsEnabled(enabled: Boolean) { _floatingLyricsEnabled.value = enabled }

    // Deprecated properties
    @Suppress("DEPRECATION")
    private val _lyricsOriginalTextSize = MutableStateFlow(16)
    @Suppress("DEPRECATION")
    override val lyricsOriginalTextSize: Flow<Int> = _lyricsOriginalTextSize.asStateFlow()
    @Suppress("DEPRECATION")
    override suspend fun saveLyricsOriginalTextSize(size: Int) { _lyricsOriginalTextSize.value = size }
    @Suppress("DEPRECATION")
    override suspend fun getLyricsOriginalTextSize(): Int = _lyricsOriginalTextSize.value

    @Suppress("DEPRECATION")
    private val _lyricsTranslatedTextSize = MutableStateFlow(14)
    @Suppress("DEPRECATION")
    override val lyricsTranslatedTextSize: Flow<Int> = _lyricsTranslatedTextSize.asStateFlow()
    @Suppress("DEPRECATION")
    override suspend fun saveLyricsTranslatedTextSize(size: Int) { _lyricsTranslatedTextSize.value = size }
    @Suppress("DEPRECATION")
    override suspend fun getLyricsTranslatedTextSize(): Int = _lyricsTranslatedTextSize.value

    @Suppress("DEPRECATION")
    private val _lyricsCurrentTimeTextSize = MutableStateFlow(12)
    @Suppress("DEPRECATION")
    override val lyricsCurrentTimeTextSize: Flow<Int> = _lyricsCurrentTimeTextSize.asStateFlow()
    @Suppress("DEPRECATION")
    override suspend fun saveLyricsCurrentTimeTextSize(size: Int) { _lyricsCurrentTimeTextSize.value = size }
    @Suppress("DEPRECATION")
    override suspend fun getLyricsCurrentTimeTextSize(): Int = _lyricsCurrentTimeTextSize.value

    @Suppress("DEPRECATION")
    private val _lyricsLineSpacing = MutableStateFlow(8)
    @Suppress("DEPRECATION")
    override val lyricsLineSpacing: Flow<Int> = _lyricsLineSpacing.asStateFlow()
    @Suppress("DEPRECATION")
    override suspend fun saveLyricsLineSpacing(spacing: Int) { _lyricsLineSpacing.value = spacing }
    @Suppress("DEPRECATION")
    override suspend fun getLyricsLineSpacing(): Int = _lyricsLineSpacing.value

    @Suppress("DEPRECATION")
    private val _lyricsDisplayMode = MutableStateFlow(DisplayMode.DUAL)
    @Suppress("DEPRECATION")
    override val lyricsDisplayMode: Flow<DisplayMode> = _lyricsDisplayMode.asStateFlow()
    @Suppress("DEPRECATION")
    override suspend fun saveLyricsDisplayMode(mode: DisplayMode) { _lyricsDisplayMode.value = mode }
    @Suppress("DEPRECATION")
    override suspend fun getLyricsDisplayMode(): DisplayMode = _lyricsDisplayMode.value

    @Suppress("DEPRECATION")
    private val _lyricsAlignment = MutableStateFlow(LyricsAlignment.CENTER)
    @Suppress("DEPRECATION")
    override val lyricsAlignment: Flow<LyricsAlignment> = _lyricsAlignment.asStateFlow()
    @Suppress("DEPRECATION")
    override suspend fun saveLyricsAlignment(alignment: LyricsAlignment) { _lyricsAlignment.value = alignment }
    @Suppress("DEPRECATION")
    override suspend fun getLyricsAlignment(): LyricsAlignment = _lyricsAlignment.value

    private val _lyricsKaraokeEnabled = MutableStateFlow(true)
    override val lyricsKaraokeEnabled: Flow<Boolean> = _lyricsKaraokeEnabled.asStateFlow()
    override suspend fun saveLyricsKaraokeEnabled(enabled: Boolean) { _lyricsKaraokeEnabled.value = enabled }
    override suspend fun getLyricsKaraokeEnabled(): Boolean = _lyricsKaraokeEnabled.value

    private val _dailyRefreshHours = MutableStateFlow(8)
    override val dailyRefreshHours: Flow<Int> = _dailyRefreshHours.asStateFlow()
    override suspend fun saveDailyRefreshHours(hours: Int) { _dailyRefreshHours.value = hours }

    private val _dailyRefreshStartupCount = MutableStateFlow(5)
    override val dailyRefreshStartupCount: Flow<Int> = _dailyRefreshStartupCount.asStateFlow()
    override suspend fun saveDailyRefreshStartupCount(count: Int) { _dailyRefreshStartupCount.value = count }

    private val _lastDailyRefreshTimestamp = MutableStateFlow(0L)
    override val lastDailyRefreshTimestamp: Flow<Long> = _lastDailyRefreshTimestamp.asStateFlow()
    override suspend fun updateLastDailyRefreshTimestamp() { _lastDailyRefreshTimestamp.value = com.hmp.data.database.currentTimeMillis() }

    private val _appLaunchCountSinceRefresh = MutableStateFlow(0)
    override val appLaunchCountSinceRefresh: Flow<Int> = _appLaunchCountSinceRefresh.asStateFlow()
    override suspend fun incrementAppLaunchCount() { _appLaunchCountSinceRefresh.value += 1 }

    override suspend fun getDailyRefreshConfig(): DailyRefreshConfig = DailyRefreshConfig(
        mode = _dailyRefreshMode.value,
        refreshHours = _dailyRefreshHours.value,
        startupCount = _dailyRefreshStartupCount.value,
        lastRefreshTimestamp = _lastDailyRefreshTimestamp.value,
        launchCountSinceRefresh = _appLaunchCountSinceRefresh.value
    )

    private var currentDailyMusicId: Long? = null
    override suspend fun saveCurrentDailyMusicId(musicId: Long) { currentDailyMusicId = musicId }
    override suspend fun getCurrentDailyMusicId(): Long? = currentDailyMusicId

    private val _galleryOrderBy = MutableStateFlow("title")
    override val galleryOrderBy: Flow<String> = _galleryOrderBy.asStateFlow()
    override suspend fun saveGalleryOrderBy(orderBy: String) { _galleryOrderBy.value = orderBy }

    private val _galleryOrderType = MutableStateFlow("ASC")
    override val galleryOrderType: Flow<String> = _galleryOrderType.asStateFlow()
    override suspend fun saveGalleryOrderType(orderType: String) { _galleryOrderType.value = orderType }

    private val _defaultAlgorithmType = MutableStateFlow("OPTIMIZED_SIMILARITY")
    override val defaultAlgorithmType: Flow<String> = _defaultAlgorithmType.asStateFlow()
    override suspend fun saveDefaultAlgorithmType(type: String) { _defaultAlgorithmType.value = type }
    override suspend fun getDefaultAlgorithmType(): String = _defaultAlgorithmType.value

    private val _defaultWeightTemplate = MutableStateFlow("BALANCED")
    override val defaultWeightTemplate: Flow<String> = _defaultWeightTemplate.asStateFlow()
    override suspend fun saveDefaultWeightTemplate(template: String) { _defaultWeightTemplate.value = template }
    override suspend fun getDefaultWeightTemplate(): String = _defaultWeightTemplate.value

    private val _defaultExtensionConfig = MutableStateFlow("{}")
    override val defaultExtensionConfig: Flow<String> = _defaultExtensionConfig.asStateFlow()
    override suspend fun saveDefaultExtensionConfig(configJson: String) { _defaultExtensionConfig.value = configJson }
    override suspend fun getDefaultExtensionConfig(): String = _defaultExtensionConfig.value

    override suspend fun exportAppSettingsSnapshot(): AppSettingsSnapshot = AppSettingsSnapshot()
    override suspend fun restoreFromSnapshot(snapshot: AppSettingsSnapshot) {}

    override suspend fun exportDailyRecommendationSnapshot(): DailyRecommendationSnapshot? = null
    override suspend fun restoreDailyRecommendationSnapshot(snapshot: DailyRecommendationSnapshot) {}

    override suspend fun backupSettings(): Result<String> = Result.success("/backup.json")
    override suspend fun restoreSettings(backupFilePath: String): Result<Unit> = Result.success(Unit)
    override suspend fun cleanOldBackups(keepCount: Int): Result<Unit> = Result.success(Unit)

    override suspend fun getAgentPolicyConfig(agentRole: String): com.hmp.domain.agent.policy.AgentPolicyConfig =
        com.hmp.domain.agent.policy.AgentPolicyConfig()
    override suspend fun saveAgentPolicyConfig(agentRole: String, config: com.hmp.domain.agent.policy.AgentPolicyConfig) {}
}
