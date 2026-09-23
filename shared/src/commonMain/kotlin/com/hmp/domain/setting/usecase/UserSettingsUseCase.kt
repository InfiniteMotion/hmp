package com.hmp.domain.setting.usecase

import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.Flow

class UserSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    val isFirstLaunch: Flow<Boolean> = settingsRepository.isFirstLaunch

    suspend fun saveIsFirstLaunch(status: Boolean) {
        settingsRepository.saveIsFirstLaunch(status)
    }

    val userName: Flow<String> = settingsRepository.userName
    suspend fun saveUserName(name: String) {
        settingsRepository.saveUserName(name)
    }

    val customMode: Flow<String> = settingsRepository.themeMode
    suspend fun saveThemeMode(mode: String) {
        settingsRepository.saveThemeMode(mode)
    }

    val backgroundStyle: Flow<String> = settingsRepository.backgroundStyle
    suspend fun saveBackgroundStyle(style: String) {
        settingsRepository.saveBackgroundStyle(style)
    }

    val hazeMode: Flow<String> = settingsRepository.hazeMode
    suspend fun saveHazeMode(mode: String) {
        settingsRepository.saveHazeMode(mode)
    }

    val hazeMaterialPreset: Flow<String> = settingsRepository.hazeMaterialPreset
    suspend fun saveHazeMaterialPreset(preset: String) {
        settingsRepository.saveHazeMaterialPreset(preset)
    }

    val hazeBlurRadius: Flow<Float> = settingsRepository.hazeBlurRadius
    suspend fun saveHazeBlurRadius(radius: Float) {
        settingsRepository.saveHazeBlurRadius(radius)
    }

    val hazeNoiseFactor: Flow<Float> = settingsRepository.hazeNoiseFactor
    suspend fun saveHazeNoiseFactor(noiseFactor: Float) {
        settingsRepository.saveHazeNoiseFactor(noiseFactor)
    }

    val hazeTintAlpha: Flow<Float> = settingsRepository.hazeTintAlpha
    suspend fun saveHazeTintAlpha(alpha: Float) {
        settingsRepository.saveHazeTintAlpha(alpha)
    }

    val hazeIntensity: Flow<Float> = settingsRepository.hazeIntensity
    suspend fun saveHazeIntensity(intensity: Float) {
        settingsRepository.saveHazeIntensity(intensity)
    }

    suspend fun getAvatarUri(): String? {
        return settingsRepository.getAvatarUri()
    }

    suspend fun saveAvatarUri(uri: String) {
        settingsRepository.saveAvatarUri(uri)
    }

    val isLoadMusic: Flow<Boolean> = settingsRepository.isLoadMusic

    suspend fun saveIsLoadMusic(status: Boolean) {
        settingsRepository.saveIsLoadMusic(status)
    }

    // AI Access Mode
    val aiAccessMode: Flow<AiAccessMode> = settingsRepository.aiAccessMode

    suspend fun getAiAccessMode(): AiAccessMode {
        return settingsRepository.getAiAccessMode()
    }

    suspend fun saveAiAccessMode(mode: AiAccessMode) {
        settingsRepository.saveAiAccessMode(mode)
    }

    suspend fun getCustomAiConfig(): AiEndpointConfig {
        return settingsRepository.getCustomAiConfig()
    }

    suspend fun saveCustomAiConfig(config: AiEndpointConfig) {
        settingsRepository.saveCustomAiConfig(config)
    }

    suspend fun getActiveAiConfig(): AiEndpointConfig {
        return settingsRepository.getActiveAiConfig()
    }

    val aiFreeTrialRemainingCount: Flow<Int> = settingsRepository.aiFreeTrialRemainingCount

    suspend fun getAiFreeTrialRemainingCount(): Int {
        return settingsRepository.getAiFreeTrialRemainingCount()
    }

    suspend fun decrementAiFreeTrialCount() {
        settingsRepository.decrementAiFreeTrialCount()
    }

    val autoBatchProcess: Flow<Boolean> = settingsRepository.autoBatchProcess

    suspend fun saveAutoBatchProcess(enabled: Boolean) {
        settingsRepository.saveAutoBatchProcess(enabled)
    }

}
