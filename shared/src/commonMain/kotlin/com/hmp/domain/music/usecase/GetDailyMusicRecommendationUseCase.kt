package com.hmp.domain.music.usecase

import co.touchlab.kermit.Logger
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

class GetDailyMusicRecommendationUseCase(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) {

    data class MusicRecommendation(
        val musicInfo: MusicInfo?,
        val dailyMusicInfo: DailyMusicInfo?,
        val labels: List<MusicLabel?>
    )

    suspend fun getRandomMusicWithExtra(): MusicRecommendation {
        val musicInfo = musicRepository.getRandomMusicInfoWithExtra()
        val dailyMusicInfo = musicInfo?.music?.id?.let { musicRepository.getMusicExtraById(it) }
        val labels = musicInfo?.music?.id?.let { musicRepository.getMusicLabels(it) } ?: emptyList()
        return MusicRecommendation(musicInfo, dailyMusicInfo, labels)
    }

    suspend fun getMusicWithExtraById(musicId: Long): MusicRecommendation? {
        try {
            val musicInfo = withTimeoutOrNull(2000) {
                musicRepository.getMusicInfoById(musicId).firstOrNull()
            } ?: run {
                Logger.w("UseCase.DailyRec") { "getMusicWithExtraById: Timeout or null for id $musicId" }
                return null
            }

            val dailyMusicInfo = musicRepository.getMusicExtraById(musicId)
            val labels = musicRepository.getMusicLabels(musicId)
            return MusicRecommendation(musicInfo, dailyMusicInfo, labels)
        } catch (e: Exception) {
            Logger.e("UseCase.DailyRec", e) { "Error fetching music by id: $musicId" }
            return null
        }
    }

    suspend fun validateProviderApiKey(): Boolean {
        val config = settingsRepository.getActiveAiConfig()
        return musicRepository.validateProviderApiKey(config).getOrDefault(false)
    }

    suspend fun validateProviderApiKey(config: com.hmp.domain.setting.model.AiEndpointConfig): Boolean {
        return musicRepository.validateProviderApiKey(config).getOrDefault(false)
    }

    suspend fun fetchModels(config: com.hmp.domain.setting.model.AiEndpointConfig): kotlin.Result<List<String>> {
        return musicRepository.fetchAvailableModels(config)
    }

    fun getRecentListeningDurations(): Flow<List<ListeningDuration>> {
        return musicRepository.getRecentListeningDurations(35)
    }
}
