package com.hmp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.network.BuiltInApiKeyProvider
import com.hmp.data.util.SecureStorageHelper
import com.hmp.domain.backup.AppSettingsSnapshot
import com.hmp.domain.backup.DailyRecommendationSnapshot
import com.hmp.domain.config.DailyRefreshConfig
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.lyrics.LyricsComponentConfig
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ScanDirectoryConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding

@OptIn(ExperimentalForeignApi::class)
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val builtInApiKeyProvider: BuiltInApiKeyProvider = BuiltInApiKeyProvider()
) : SettingsRepository {
    private companion object {
        const val HAZE_MODE_CUSTOM = "custom"
        const val HAZE_MODE_PRESET = "preset"
        const val HAZE_MATERIAL_PRESET_REGULAR = "regular"
        const val DEFAULT_HAZE_BLUR_RADIUS = 20f
        const val DEFAULT_HAZE_NOISE_FACTOR = 0.15f
        const val DEFAULT_HAZE_TINT_ALPHA = 0.22f
    }

    private object PreferencesKeys {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val IS_LOAD_MUSIC = booleanPreferencesKey("is_load_music")
        val CURRENT_MUSIC_ID = longPreferencesKey("current_music_id")
        val CURRENT_POSITION = longPreferencesKey("current_position")
        val CURRENT_PLAYLIST_ID = longPreferencesKey("current_playlist_id")
        val LIKED_PLAYLIST_ID = longPreferencesKey("liked_playlist_id")
        val RECENT_PLAYLIST_ID = longPreferencesKey("recent_playlist_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepSeek_api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val HAZE_MODE = stringPreferencesKey("haze_mode")
        val HAZE_MATERIAL_PRESET = stringPreferencesKey("haze_material_preset")
        val HAZE_BLUR_RADIUS = floatPreferencesKey("haze_blur_radius")
        val HAZE_NOISE_FACTOR = floatPreferencesKey("haze_noise_factor")
        val HAZE_TINT_ALPHA = floatPreferencesKey("haze_tint_alpha")
        val HAZE_INTENSITY = floatPreferencesKey("haze_intensity")
        val AI_ACCESS_MODE = stringPreferencesKey("ai_access_mode")
        val CUSTOM_AI_ENDPOINT = stringPreferencesKey("custom_ai_endpoint")
        val CUSTOM_AI_API_KEY = stringPreferencesKey("custom_ai_api_key")
        val CUSTOM_AI_MODEL = stringPreferencesKey("custom_ai_model")
        val AI_FREE_TRIAL_REMAINING = intPreferencesKey("ai_free_trial_remaining")
        fun agentTrustLevelKey(role: String) = intPreferencesKey("agent_policy_${role}_trust_level")
        fun agentAlwaysAllowKey(role: String) = stringSetPreferencesKey("agent_policy_${role}_always_allow")
        val EQUALIZER_PRESET = intPreferencesKey("equalizer_preset")
        val BASS_BOOST_LEVEL = intPreferencesKey("bass_boost_level")
        val IS_SURROUND_SOUND_ENABLED = booleanPreferencesKey("is_surround_sound_enabled")
        val REVERB_PRESET = intPreferencesKey("reverb_preset")
        val CUSTOM_EQUALIZER_LEVELS = stringPreferencesKey("custom_equalizer_levels")
        val AUTO_BATCH_PROCESS = booleanPreferencesKey("auto_batch_process")
        val DAILY_REFRESH_MODE = stringPreferencesKey("daily_refresh_mode")
        val DAILY_REFRESH_HOURS = intPreferencesKey("daily_refresh_hours")
        val DAILY_REFRESH_STARTUP_COUNT = intPreferencesKey("daily_refresh_startup_count")
        val LAST_DAILY_REFRESH_TIMESTAMP = longPreferencesKey("last_daily_refresh_timestamp")
        val APP_LAUNCH_COUNT_SINCE_REFRESH = intPreferencesKey("app_launch_count_since_refresh")
        val CURRENT_DAILY_MUSIC_ID = longPreferencesKey("current_daily_music_id")
        val LYRICS_ORIGINAL_TEXT_SIZE = intPreferencesKey("lyrics_original_text_size")
        val LYRICS_TRANSLATED_TEXT_SIZE = intPreferencesKey("lyrics_translated_text_size")
        val LYRICS_CURRENT_TIME_TEXT_SIZE = intPreferencesKey("lyrics_current_time_text_size")
        val LYRICS_LINE_SPACING = intPreferencesKey("lyrics_line_spacing")
        val LYRICS_DISPLAY_MODE = stringPreferencesKey("lyrics_display_mode")
        val LYRICS_ALIGNMENT = stringPreferencesKey("lyrics_alignment")
        val LYRICS_KARAOKE_ENABLED = booleanPreferencesKey("lyrics_karaoke_enabled")
        val LYRICS_PLAYER_CONFIG = stringPreferencesKey("lyrics_player_config")
        val LYRICS_FULLSCREEN_CONFIG = stringPreferencesKey("lyrics_fullscreen_config")
        val LYRICS_FLOATING_CONFIG = stringPreferencesKey("lyrics_floating_config")
        val FLOATING_LYRICS_ENABLED = booleanPreferencesKey("floating_lyrics_enabled")
        val DEFAULT_ALGORITHM_TYPE = stringPreferencesKey("default_algorithm_type")
        val DEFAULT_WEIGHT_TEMPLATE = stringPreferencesKey("default_weight_template")
        val DEFAULT_EXTENSION_CONFIG = stringPreferencesKey("default_extension_config")
        val GALLERY_ORDER_BY = stringPreferencesKey("gallery_order_by")
        val GALLERY_ORDER_TYPE = stringPreferencesKey("gallery_order_type")
    }

    private fun normalizeHazeMode(mode: String): String {
        return if (mode == HAZE_MODE_PRESET) HAZE_MODE_PRESET else HAZE_MODE_CUSTOM
    }

    private fun normalizeHazeMaterialPreset(preset: String): String {
        return when (preset) {
            "ultra_thin", "thin", "regular", "thick", "ultra_thick" -> preset
            else -> HAZE_MATERIAL_PRESET_REGULAR
        }
    }

    override val isFirstLaunch: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PreferencesKeys.IS_FIRST_LAUNCH] ?: true }
    override val userName: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.USER_NAME] ?: "User" }
    override val themeMode: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.THEME_MODE] ?: "default" }
    override val backgroundStyle: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.BACKGROUND_STYLE] ?: "FLUID" }
    override val hazeMode: Flow<String> = dataStore.data.map { prefs -> normalizeHazeMode(prefs[PreferencesKeys.HAZE_MODE] ?: HAZE_MODE_CUSTOM) }
    override val hazeMaterialPreset: Flow<String> = dataStore.data.map { prefs -> normalizeHazeMaterialPreset(prefs[PreferencesKeys.HAZE_MATERIAL_PRESET] ?: HAZE_MATERIAL_PRESET_REGULAR) }
    override val hazeBlurRadius: Flow<Float> = dataStore.data.map { prefs -> prefs[PreferencesKeys.HAZE_BLUR_RADIUS] ?: DEFAULT_HAZE_BLUR_RADIUS }
    override val hazeNoiseFactor: Flow<Float> = dataStore.data.map { prefs -> (prefs[PreferencesKeys.HAZE_NOISE_FACTOR] ?: DEFAULT_HAZE_NOISE_FACTOR).coerceIn(0f, 1f) }
    override val hazeTintAlpha: Flow<Float> = dataStore.data.map { prefs -> (prefs[PreferencesKeys.HAZE_TINT_ALPHA] ?: DEFAULT_HAZE_TINT_ALPHA).coerceIn(0f, 1f) }
    override val hazeIntensity: Flow<Float> = dataStore.data.map { prefs -> prefs[PreferencesKeys.HAZE_INTENSITY] ?: 0.6f }
    override val isLoadMusic: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PreferencesKeys.IS_LOAD_MUSIC] ?: false }
    override val currentMusicId: Flow<Long?> = dataStore.data.map { prefs -> prefs[PreferencesKeys.CURRENT_MUSIC_ID] }
    override val currentPlaylistId: Flow<Long?> = dataStore.data.map { prefs -> prefs[PreferencesKeys.CURRENT_PLAYLIST_ID] }
    override val likedPlaylistId: Flow<Long?> = dataStore.data.map { prefs -> prefs[PreferencesKeys.LIKED_PLAYLIST_ID] }
    override val recentPlaylistId: Flow<Long?> = dataStore.data.map { prefs -> prefs[PreferencesKeys.RECENT_PLAYLIST_ID] }
    override val aiAccessMode: Flow<AiAccessMode> = dataStore.data.map { prefs ->
        try { AiAccessMode.valueOf(prefs[PreferencesKeys.AI_ACCESS_MODE] ?: "FREE") } catch (e: Exception) { AiAccessMode.FREE }
    }
    override val aiFreeTrialRemainingCount: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.AI_FREE_TRIAL_REMAINING] ?: 100 }
    override val equalizerPreset: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.EQUALIZER_PRESET] ?: 0 }
    override val bassBoostLevel: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.BASS_BOOST_LEVEL] ?: 0 }
    override val isSurroundSoundEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PreferencesKeys.IS_SURROUND_SOUND_ENABLED] ?: false }
    override val reverbPreset: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.REVERB_PRESET] ?: 0 }
    override val customEqualizerLevels: Flow<FloatArray> = dataStore.data.map { prefs -> prefs[PreferencesKeys.CUSTOM_EQUALIZER_LEVELS]?.let { it.split(",").mapNotNull { s -> s.toFloatOrNull() }.toFloatArray() } ?: floatArrayOf() }
    override val autoBatchProcess: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PreferencesKeys.AUTO_BATCH_PROCESS] ?: true }
    override val dailyRefreshMode: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.DAILY_REFRESH_MODE] ?: "time" }
    override val dailyRefreshHours: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.DAILY_REFRESH_HOURS] ?: 24 }
    override val dailyRefreshStartupCount: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] ?: 3 }
    override val lastDailyRefreshTimestamp: Flow<Long> = dataStore.data.map { prefs -> prefs[PreferencesKeys.LAST_DAILY_REFRESH_TIMESTAMP] ?: 0L }
    override val appLaunchCountSinceRefresh: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] ?: 0 }
    override val currentPosition: Flow<Long> = dataStore.data.map { prefs -> prefs[PreferencesKeys.CURRENT_POSITION] ?: 0L }
    override val lyricsOriginalTextSize: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.LYRICS_ORIGINAL_TEXT_SIZE] ?: 14 }
    override val lyricsTranslatedTextSize: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.LYRICS_TRANSLATED_TEXT_SIZE] ?: 14 }
    override val lyricsCurrentTimeTextSize: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.LYRICS_CURRENT_TIME_TEXT_SIZE] ?: 16 }
    override val lyricsLineSpacing: Flow<Int> = dataStore.data.map { prefs -> prefs[PreferencesKeys.LYRICS_LINE_SPACING] ?: 6 }
    override val lyricsDisplayMode: Flow<DisplayMode> = dataStore.data.map {
        val modeStr = it[PreferencesKeys.LYRICS_DISPLAY_MODE] ?: "DUAL"
        try { DisplayMode.valueOf(modeStr) } catch (e: IllegalArgumentException) { DisplayMode.DUAL }
    }
    override val lyricsAlignment: Flow<LyricsAlignment> = dataStore.data.map {
        val alignmentStr = it[PreferencesKeys.LYRICS_ALIGNMENT] ?: "CENTER"
        try { LyricsAlignment.valueOf(alignmentStr) } catch (e: IllegalArgumentException) { LyricsAlignment.CENTER }
    }
    override val lyricsKaraokeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LYRICS_KARAOKE_ENABLED] ?: true
    }
    override val lyricsPlayerConfig: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LYRICS_PLAYER_CONFIG] ?: json.encodeToString(LyricsComponentConfig.DEFAULT)
    }
    override val lyricsFullscreenConfig: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LYRICS_FULLSCREEN_CONFIG] ?: json.encodeToString(LyricsComponentConfig.DEFAULT)
    }
    override val lyricsFloatingConfig: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LYRICS_FLOATING_CONFIG] ?: json.encodeToString(LyricsComponentConfig.DEFAULT)
    }
    override val floatingLyricsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.FLOATING_LYRICS_ENABLED] ?: false
    }
    override val galleryOrderBy: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.GALLERY_ORDER_BY] ?: "title" }
    override val galleryOrderType: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.GALLERY_ORDER_TYPE] ?: "ASC" }
    override suspend fun saveGalleryOrderBy(orderBy: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.GALLERY_ORDER_BY] = orderBy } }
    override suspend fun saveGalleryOrderType(orderType: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.GALLERY_ORDER_TYPE] = orderType } }

    override val defaultAlgorithmType: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.DEFAULT_ALGORITHM_TYPE] ?: "OPTIMIZED_SIMILARITY" }
    override val defaultWeightTemplate: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.DEFAULT_WEIGHT_TEMPLATE] ?: "BALANCED" }
    override val defaultExtensionConfig: Flow<String> = dataStore.data.map { prefs -> prefs[PreferencesKeys.DEFAULT_EXTENSION_CONFIG] ?: "{}" }

    override suspend fun saveIsFirstLaunch(isFirstLaunch: Boolean) { dataStore.edit { prefs -> prefs[PreferencesKeys.IS_FIRST_LAUNCH] = isFirstLaunch } }
    override suspend fun saveIsLoadMusic(isLoadMusic: Boolean) { dataStore.edit { prefs -> prefs[PreferencesKeys.IS_LOAD_MUSIC] = isLoadMusic } }
    override val scanDirectoryConfig: Flow<ScanDirectoryConfig> = flowOf(ScanDirectoryConfig())
    override suspend fun saveScanDirectoryConfig(config: ScanDirectoryConfig) { }
    override suspend fun saveThemeMode(themeMode: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.THEME_MODE] = themeMode } }
    override suspend fun saveBackgroundStyle(style: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.BACKGROUND_STYLE] = style } }
    override suspend fun saveHazeMode(mode: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.HAZE_MODE] = normalizeHazeMode(mode) } }
    override suspend fun saveHazeMaterialPreset(preset: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.HAZE_MATERIAL_PRESET] = normalizeHazeMaterialPreset(preset) } }
    override suspend fun saveHazeBlurRadius(radius: Float) { dataStore.edit { prefs -> prefs[PreferencesKeys.HAZE_BLUR_RADIUS] = radius.coerceAtLeast(0f) } }
    override suspend fun saveHazeNoiseFactor(noiseFactor: Float) { dataStore.edit { prefs -> prefs[PreferencesKeys.HAZE_NOISE_FACTOR] = noiseFactor.coerceIn(0f, 1f) } }
    override suspend fun saveHazeTintAlpha(alpha: Float) { dataStore.edit { prefs -> prefs[PreferencesKeys.HAZE_TINT_ALPHA] = alpha.coerceIn(0f, 1f) } }
    override suspend fun saveHazeIntensity(intensity: Float) { dataStore.edit { prefs -> prefs[PreferencesKeys.HAZE_INTENSITY] = intensity.coerceIn(0f, 1f) } }
    override suspend fun saveUserName(name: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.USER_NAME] = name } }
    override suspend fun saveAvatarUri(uri: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.AVATAR_URI] = uri } }
    override suspend fun getAvatarUri(): String? = dataStore.data.first()[PreferencesKeys.AVATAR_URI]
    override suspend fun saveCurrentMusicId(id: Long) { dataStore.edit { prefs -> prefs[PreferencesKeys.CURRENT_MUSIC_ID] = id } }
    override suspend fun saveCurrentPosition(position: Long) { dataStore.edit { prefs -> prefs[PreferencesKeys.CURRENT_POSITION] = position } }
    override suspend fun getCurrentPlaylistId(): Long? = dataStore.data.first()[PreferencesKeys.CURRENT_PLAYLIST_ID]
    override suspend fun saveCurrentPlaylistId(playlistId: Long) { dataStore.edit { prefs -> prefs[PreferencesKeys.CURRENT_PLAYLIST_ID] = playlistId } }
    override suspend fun getLikedPlaylistId(): Long? = dataStore.data.first()[PreferencesKeys.LIKED_PLAYLIST_ID]
    override suspend fun saveLikedPlaylistId(playlistId: Long) { dataStore.edit { prefs -> prefs[PreferencesKeys.LIKED_PLAYLIST_ID] = playlistId } }
    override suspend fun getRecentPlaylistId(): Long? = dataStore.data.first()[PreferencesKeys.RECENT_PLAYLIST_ID]
    override suspend fun saveRecentPlaylistId(playlistId: Long) { dataStore.edit { prefs -> prefs[PreferencesKeys.RECENT_PLAYLIST_ID] = playlistId } }
    override suspend fun saveAutoBatchProcess(enabled: Boolean) { dataStore.edit { prefs -> prefs[PreferencesKeys.AUTO_BATCH_PROCESS] = enabled } }
    override suspend fun saveDailyRefreshMode(mode: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.DAILY_REFRESH_MODE] = mode } }
    override suspend fun saveDailyRefreshHours(hours: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.DAILY_REFRESH_HOURS] = hours } }
    override suspend fun saveDailyRefreshStartupCount(count: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] = count } }
    override suspend fun updateLastDailyRefreshTimestamp() { dataStore.edit { prefs -> prefs[PreferencesKeys.LAST_DAILY_REFRESH_TIMESTAMP] = currentTimeMillis(); prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] = 0 } }
    override suspend fun saveCurrentDailyMusicId(musicId: Long) { dataStore.edit { prefs -> prefs[PreferencesKeys.CURRENT_DAILY_MUSIC_ID] = musicId } }
    override suspend fun getCurrentDailyMusicId(): Long? = dataStore.data.first()[PreferencesKeys.CURRENT_DAILY_MUSIC_ID]
    override suspend fun incrementAppLaunchCount() { dataStore.edit { prefs -> prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] = (prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] ?: 0) + 1 } }
    override suspend fun getDailyRefreshConfig(): DailyRefreshConfig {
        val prefs = dataStore.data.first()
        return DailyRefreshConfig(
            mode = prefs[PreferencesKeys.DAILY_REFRESH_MODE] ?: "time",
            refreshHours = prefs[PreferencesKeys.DAILY_REFRESH_HOURS] ?: 24,
            startupCount = prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] ?: 3,
            lastRefreshTimestamp = prefs[PreferencesKeys.LAST_DAILY_REFRESH_TIMESTAMP] ?: 0L,
            launchCountSinceRefresh = prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] ?: 0
        )
    }
    override suspend fun getAiAccessMode(): AiAccessMode {
        return try { AiAccessMode.valueOf(dataStore.data.first()[PreferencesKeys.AI_ACCESS_MODE] ?: "FREE") } catch (e: Exception) { AiAccessMode.FREE }
    }
    override suspend fun saveAiAccessMode(mode: AiAccessMode) { dataStore.edit { prefs -> prefs[PreferencesKeys.AI_ACCESS_MODE] = mode.name } }
    override suspend fun getCustomAiConfig(): AiEndpointConfig {
        val prefs = dataStore.data.first()
        val endpoint = prefs[PreferencesKeys.CUSTOM_AI_ENDPOINT] ?: ""
        val encryptedKey = prefs[PreferencesKeys.CUSTOM_AI_API_KEY]
        val apiKey = encryptedKey?.let { SecureStorageHelper.decrypt(it) } ?: ""
        val model = prefs[PreferencesKeys.CUSTOM_AI_MODEL] ?: ""
        return AiEndpointConfig(endpoint = endpoint, apiKey = apiKey, selectedModel = model, isConfigured = endpoint.isNotBlank() && apiKey.isNotBlank())
    }
    override suspend fun saveCustomAiConfig(config: AiEndpointConfig) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.CUSTOM_AI_ENDPOINT] = config.endpoint; prefs[PreferencesKeys.CUSTOM_AI_API_KEY] = SecureStorageHelper.encrypt(config.apiKey); prefs[PreferencesKeys.CUSTOM_AI_MODEL] = config.selectedModel }
    }
    override suspend fun getActiveAiConfig(): AiEndpointConfig {
        return when (getAiAccessMode()) { AiAccessMode.FREE, AiAccessMode.PAID -> builtInApiKeyProvider.getConfig(); AiAccessMode.CUSTOM -> getCustomAiConfig() }
    }
    override suspend fun getAiFreeTrialRemainingCount(): Int = dataStore.data.first()[PreferencesKeys.AI_FREE_TRIAL_REMAINING] ?: 100
    override suspend fun decrementAiFreeTrialCount() { dataStore.edit { prefs -> val current = prefs[PreferencesKeys.AI_FREE_TRIAL_REMAINING] ?: 100; prefs[PreferencesKeys.AI_FREE_TRIAL_REMAINING] = (current - 1).coerceAtLeast(0) } }

    override suspend fun getAgentPolicyConfig(agentRole: String): com.hmp.domain.agent.policy.AgentPolicyConfig {
        val prefs = dataStore.data.first()
        val trustLevel = prefs[PreferencesKeys.agentTrustLevelKey(agentRole)]
            ?: com.hmp.domain.agent.policy.TrustLevel.SUGGEST
        val alwaysAllow = prefs[PreferencesKeys.agentAlwaysAllowKey(agentRole)]
            ?: emptySet()
        return com.hmp.domain.agent.policy.AgentPolicyConfig(
            trustLevel = trustLevel.coerceIn(0, com.hmp.domain.agent.policy.TrustLevel.MAX),
            alwaysAllow = alwaysAllow.toMutableSet(),
        )
    }

    override suspend fun saveAgentPolicyConfig(agentRole: String, config: com.hmp.domain.agent.policy.AgentPolicyConfig) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.agentTrustLevelKey(agentRole)] = config.trustLevel
            prefs[PreferencesKeys.agentAlwaysAllowKey(agentRole)] = config.alwaysAllow.toSet()
        }
    }

    override suspend fun saveEqualizerPreset(preset: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.EQUALIZER_PRESET] = preset } }
    override suspend fun saveBassBoostLevel(level: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.BASS_BOOST_LEVEL] = level } }
    override suspend fun saveSurroundSoundEnabled(enabled: Boolean) { dataStore.edit { prefs -> prefs[PreferencesKeys.IS_SURROUND_SOUND_ENABLED] = enabled } }
    override suspend fun saveReverbPreset(preset: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.REVERB_PRESET] = preset } }
    override suspend fun saveCustomEqualizerLevels(levels: FloatArray) { dataStore.edit { prefs -> prefs[PreferencesKeys.CUSTOM_EQUALIZER_LEVELS] = levels.joinToString(",") } }
    override suspend fun backupSettings(): Result<String> = withContext(Dispatchers.Default) {
        try {
            val prefs = dataStore.data.first()
            val entries = prefs.asMap().entries.mapNotNull { (key, value) ->
                val keyName = key.name
                val valueStr = when (value) {
                    is String -> "\"$value\""
                    is Boolean -> value.toString()
                    is Long -> value.toString()
                    is Int -> value.toString()
                    is Float -> value.toString()
                    else -> null
                }
                valueStr?.let { "\"$keyName\":$it" }
            }
            val jsonString = "{${entries.joinToString(",")}}"

            val backupDir = getBackupDir()
            val filename = "settings_backup_${com.hmp.data.database.currentTimeMillis()}.json"
            val filePath = "$backupDir/$filename"
            val data = (jsonString as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
            NSFileManager.defaultManager.createFileAtPath(filePath, data, null)
            Result.success(filePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreSettings(backupFilePath: String): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (!NSFileManager.defaultManager.fileExistsAtPath(backupFilePath)) {
                return@withContext Result.failure(Exception("Backup file does not exist"))
            }
            val data = NSFileManager.defaultManager.contentsAtPath(backupFilePath)
                ?: return@withContext Result.failure(Exception("Failed to read backup file"))
            val bytes = data.bytes
                ?: return@withContext Result.failure(Exception("Failed to read backup data"))
            val jsonString = bytes.readBytes(data.length.toInt()).decodeToString()
            val regex = """"([^"]+)":\s*([^,}]+)""".toRegex()
            val matches = regex.findAll(jsonString)

            dataStore.edit { prefs ->
                matches.forEach { match ->
                    val key = match.groupValues[1]
                    val value = match.groupValues[2].trim()
                    try {
                        when (key) {
                            PreferencesKeys.IS_FIRST_LAUNCH.name -> prefs[PreferencesKeys.IS_FIRST_LAUNCH] = value.toBoolean()
                            PreferencesKeys.IS_LOAD_MUSIC.name -> prefs[PreferencesKeys.IS_LOAD_MUSIC] = value.toBoolean()
                            PreferencesKeys.CURRENT_MUSIC_ID.name -> prefs[PreferencesKeys.CURRENT_MUSIC_ID] = value.toLong()
                            PreferencesKeys.CURRENT_PLAYLIST_ID.name -> prefs[PreferencesKeys.CURRENT_PLAYLIST_ID] = value.toLong()
                            PreferencesKeys.LIKED_PLAYLIST_ID.name -> prefs[PreferencesKeys.LIKED_PLAYLIST_ID] = value.toLong()
                            PreferencesKeys.RECENT_PLAYLIST_ID.name -> prefs[PreferencesKeys.RECENT_PLAYLIST_ID] = value.toLong()
                            PreferencesKeys.USER_NAME.name -> prefs[PreferencesKeys.USER_NAME] = value.trim('"')
                            PreferencesKeys.AVATAR_URI.name -> prefs[PreferencesKeys.AVATAR_URI] = value.trim('"')
                            PreferencesKeys.HAZE_INTENSITY.name -> prefs[PreferencesKeys.HAZE_INTENSITY] = value.toFloat()
                            PreferencesKeys.HAZE_BLUR_RADIUS.name -> prefs[PreferencesKeys.HAZE_BLUR_RADIUS] = value.toFloat().coerceAtLeast(0f)
                            PreferencesKeys.EQUALIZER_PRESET.name -> prefs[PreferencesKeys.EQUALIZER_PRESET] = value.toInt()
                            PreferencesKeys.BASS_BOOST_LEVEL.name -> prefs[PreferencesKeys.BASS_BOOST_LEVEL] = value.toInt()
                            PreferencesKeys.IS_SURROUND_SOUND_ENABLED.name -> prefs[PreferencesKeys.IS_SURROUND_SOUND_ENABLED] = value.toBoolean()
                            PreferencesKeys.REVERB_PRESET.name -> prefs[PreferencesKeys.REVERB_PRESET] = value.toInt()
                            PreferencesKeys.CUSTOM_EQUALIZER_LEVELS.name -> prefs[PreferencesKeys.CUSTOM_EQUALIZER_LEVELS] = value.trim('"')
                        }
                    } catch (_: Exception) { }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun saveLyricsOriginalTextSize(size: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_ORIGINAL_TEXT_SIZE] = size } }
    override suspend fun saveLyricsTranslatedTextSize(size: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_TRANSLATED_TEXT_SIZE] = size } }
    override suspend fun saveLyricsCurrentTimeTextSize(size: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_CURRENT_TIME_TEXT_SIZE] = size } }
    override suspend fun saveLyricsLineSpacing(spacing: Int) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_LINE_SPACING] = spacing } }
    override suspend fun saveLyricsDisplayMode(mode: DisplayMode) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_DISPLAY_MODE] = mode.name } }
    override suspend fun getLyricsDisplayMode(): DisplayMode {
        val modeStr = dataStore.data.first()[PreferencesKeys.LYRICS_DISPLAY_MODE] ?: "DUAL"
        return try { DisplayMode.valueOf(modeStr) } catch (e: IllegalArgumentException) { DisplayMode.DUAL }
    }
    override suspend fun saveLyricsAlignment(alignment: LyricsAlignment) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_ALIGNMENT] = alignment.name } }
    override suspend fun getLyricsAlignment(): LyricsAlignment {
        val alignmentStr = dataStore.data.first()[PreferencesKeys.LYRICS_ALIGNMENT] ?: "CENTER"
        return try { LyricsAlignment.valueOf(alignmentStr) } catch (e: IllegalArgumentException) { LyricsAlignment.CENTER }
    }
    override suspend fun saveLyricsKaraokeEnabled(enabled: Boolean) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_KARAOKE_ENABLED] = enabled } }
    override suspend fun getLyricsKaraokeEnabled(): Boolean =
        dataStore.data.first()[PreferencesKeys.LYRICS_KARAOKE_ENABLED] ?: true

    override suspend fun saveLyricsPlayerConfig(json: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_PLAYER_CONFIG] = json } }
    override suspend fun getLyricsPlayerConfig(): String = dataStore.data.first()[PreferencesKeys.LYRICS_PLAYER_CONFIG] ?: json.encodeToString(LyricsComponentConfig.DEFAULT)

    override suspend fun saveLyricsFullscreenConfig(json: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_FULLSCREEN_CONFIG] = json } }
    override suspend fun getLyricsFullscreenConfig(): String = dataStore.data.first()[PreferencesKeys.LYRICS_FULLSCREEN_CONFIG] ?: json.encodeToString(LyricsComponentConfig.DEFAULT)

    override suspend fun saveLyricsFloatingConfig(json: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.LYRICS_FLOATING_CONFIG] = json } }
    override suspend fun getLyricsFloatingConfig(): String = dataStore.data.first()[PreferencesKeys.LYRICS_FLOATING_CONFIG] ?: json.encodeToString(LyricsComponentConfig.DEFAULT)

    override suspend fun saveFloatingLyricsEnabled(enabled: Boolean) { dataStore.edit { prefs -> prefs[PreferencesKeys.FLOATING_LYRICS_ENABLED] = enabled } }
    override suspend fun getLyricsOriginalTextSize(): Int = dataStore.data.first()[PreferencesKeys.LYRICS_ORIGINAL_TEXT_SIZE] ?: 14
    override suspend fun getLyricsTranslatedTextSize(): Int = dataStore.data.first()[PreferencesKeys.LYRICS_TRANSLATED_TEXT_SIZE] ?: 14
    override suspend fun getLyricsCurrentTimeTextSize(): Int = dataStore.data.first()[PreferencesKeys.LYRICS_CURRENT_TIME_TEXT_SIZE] ?: 16
    override suspend fun getLyricsLineSpacing(): Int = dataStore.data.first()[PreferencesKeys.LYRICS_LINE_SPACING] ?: 6
    override suspend fun cleanOldBackups(keepCount: Int): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val dir = getBackupDir()
            if (!NSFileManager.defaultManager.fileExistsAtPath(dir)) return@withContext Result.success(Unit)
            val files = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null)
                ?.filterIsInstance<String>()
                ?.filter { it.startsWith("settings_backup_") && it.endsWith(".json") }
                ?.map { "$dir/$it" }
                ?.sortedByDescending { f ->
                    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(f, null)
                    (attrs?.get("NSFileModificationDate") as? NSDate)?.timeIntervalSinceReferenceDate ?: 0.0
                }
                ?: return@withContext Result.success(Unit)

            if (files.size > keepCount) {
                files.drop(keepCount).forEach { path ->
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getBackupDir(): String {
        val docs = NSFileManager.defaultManager.URLForDirectory(
            NSDocumentDirectory, NSUserDomainMask, null, false, null
        )!!
        val dir = "${docs.path}/backups"
        if (!NSFileManager.defaultManager.fileExistsAtPath(dir)) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        }
        return dir
    }
    override suspend fun saveDefaultAlgorithmType(type: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.DEFAULT_ALGORITHM_TYPE] = type } }
    override suspend fun getDefaultAlgorithmType(): String = dataStore.data.first()[PreferencesKeys.DEFAULT_ALGORITHM_TYPE] ?: "OPTIMIZED_SIMILARITY"
    override suspend fun saveDefaultWeightTemplate(template: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.DEFAULT_WEIGHT_TEMPLATE] = template } }
    override suspend fun getDefaultWeightTemplate(): String = dataStore.data.first()[PreferencesKeys.DEFAULT_WEIGHT_TEMPLATE] ?: "BALANCED"
    override suspend fun saveDefaultExtensionConfig(configJson: String) { dataStore.edit { prefs -> prefs[PreferencesKeys.DEFAULT_EXTENSION_CONFIG] = configJson } }
    override suspend fun getDefaultExtensionConfig(): String = dataStore.data.first()[PreferencesKeys.DEFAULT_EXTENSION_CONFIG] ?: "{}"

    override suspend fun exportAppSettingsSnapshot(): AppSettingsSnapshot {
        val prefs = dataStore.data.first()
        val aiAccessMode = try { AiAccessMode.valueOf(prefs[PreferencesKeys.AI_ACCESS_MODE] ?: "FREE") } catch (e: Exception) { AiAccessMode.FREE }
        return AppSettingsSnapshot(
            userName = prefs[PreferencesKeys.USER_NAME],
            avatarUri = prefs[PreferencesKeys.AVATAR_URI],
            themeMode = prefs[PreferencesKeys.THEME_MODE] ?: "default",
            backgroundStyle = prefs[PreferencesKeys.BACKGROUND_STYLE] ?: "FLUID",
            hazeMode = normalizeHazeMode(prefs[PreferencesKeys.HAZE_MODE] ?: HAZE_MODE_CUSTOM),
            hazeMaterialPreset = normalizeHazeMaterialPreset(prefs[PreferencesKeys.HAZE_MATERIAL_PRESET] ?: HAZE_MATERIAL_PRESET_REGULAR),
            hazeBlurRadius = prefs[PreferencesKeys.HAZE_BLUR_RADIUS] ?: DEFAULT_HAZE_BLUR_RADIUS,
            hazeNoiseFactor = (prefs[PreferencesKeys.HAZE_NOISE_FACTOR] ?: DEFAULT_HAZE_NOISE_FACTOR).coerceIn(0f, 1f),
            hazeTintAlpha = (prefs[PreferencesKeys.HAZE_TINT_ALPHA] ?: DEFAULT_HAZE_TINT_ALPHA).coerceIn(0f, 1f),
            hazeIntensity = prefs[PreferencesKeys.HAZE_INTENSITY] ?: 0.6f,
            autoBatchProcess = prefs[PreferencesKeys.AUTO_BATCH_PROCESS] ?: true,
            dailyRefreshMode = prefs[PreferencesKeys.DAILY_REFRESH_MODE] ?: "time",
            dailyRefreshHours = prefs[PreferencesKeys.DAILY_REFRESH_HOURS] ?: 24,
            dailyRefreshStartupCount = prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] ?: 3,
            aiAccessMode = aiAccessMode.name,
            customAiEndpoint = prefs[PreferencesKeys.CUSTOM_AI_ENDPOINT] ?: "",
            customAiModel = prefs[PreferencesKeys.CUSTOM_AI_MODEL] ?: ""
        )
    }

    override suspend fun restoreFromSnapshot(snapshot: AppSettingsSnapshot) {
        dataStore.edit { prefs ->
            snapshot.userName?.let { prefs[PreferencesKeys.USER_NAME] = it }
            snapshot.avatarUri?.let { prefs[PreferencesKeys.AVATAR_URI] = it }
            prefs[PreferencesKeys.THEME_MODE] = snapshot.themeMode
            prefs[PreferencesKeys.BACKGROUND_STYLE] = snapshot.backgroundStyle
            prefs[PreferencesKeys.HAZE_MODE] = normalizeHazeMode(snapshot.hazeMode)
            prefs[PreferencesKeys.HAZE_MATERIAL_PRESET] = normalizeHazeMaterialPreset(snapshot.hazeMaterialPreset)
            prefs[PreferencesKeys.HAZE_BLUR_RADIUS] = snapshot.hazeBlurRadius.coerceAtLeast(0f)
            prefs[PreferencesKeys.HAZE_NOISE_FACTOR] = snapshot.hazeNoiseFactor.coerceIn(0f, 1f)
            prefs[PreferencesKeys.HAZE_TINT_ALPHA] = snapshot.hazeTintAlpha.coerceIn(0f, 1f)
            prefs[PreferencesKeys.HAZE_INTENSITY] = snapshot.hazeIntensity.coerceIn(0f, 1f)
            prefs[PreferencesKeys.AUTO_BATCH_PROCESS] = snapshot.autoBatchProcess
            prefs[PreferencesKeys.DAILY_REFRESH_MODE] = snapshot.dailyRefreshMode
            prefs[PreferencesKeys.DAILY_REFRESH_HOURS] = snapshot.dailyRefreshHours
            prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] = snapshot.dailyRefreshStartupCount
            prefs[PreferencesKeys.AI_ACCESS_MODE] = snapshot.aiAccessMode
            prefs[PreferencesKeys.CUSTOM_AI_ENDPOINT] = snapshot.customAiEndpoint
            prefs[PreferencesKeys.CUSTOM_AI_MODEL] = snapshot.customAiModel
        }
    }

    override suspend fun exportDailyRecommendationSnapshot(): DailyRecommendationSnapshot? {
        val prefs = dataStore.data.first()
        return DailyRecommendationSnapshot(
            currentDailyMusicId = prefs[PreferencesKeys.CURRENT_DAILY_MUSIC_ID],
            lastRefreshTimestamp = prefs[PreferencesKeys.LAST_DAILY_REFRESH_TIMESTAMP] ?: 0L,
            mode = prefs[PreferencesKeys.DAILY_REFRESH_MODE] ?: "time",
            refreshHours = prefs[PreferencesKeys.DAILY_REFRESH_HOURS] ?: 24,
            startupCount = prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] ?: 3,
            launchCountSinceRefresh = prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] ?: 0
        )
    }

    override suspend fun restoreDailyRecommendationSnapshot(snapshot: DailyRecommendationSnapshot) {
        dataStore.edit { prefs ->
            snapshot.currentDailyMusicId?.let { prefs[PreferencesKeys.CURRENT_DAILY_MUSIC_ID] = it }
            prefs[PreferencesKeys.LAST_DAILY_REFRESH_TIMESTAMP] = snapshot.lastRefreshTimestamp
            prefs[PreferencesKeys.DAILY_REFRESH_MODE] = snapshot.mode
            prefs[PreferencesKeys.DAILY_REFRESH_HOURS] = snapshot.refreshHours
            prefs[PreferencesKeys.DAILY_REFRESH_STARTUP_COUNT] = snapshot.startupCount
            prefs[PreferencesKeys.APP_LAUNCH_COUNT_SINCE_REFRESH] = snapshot.launchCountSinceRefresh
        }
    }
}
