package com.hmp.domain.music.usecase

import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

class GetDailyMusicRecommendationUseCase(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * 富化文案（创作背景/简介/歌手介绍/奖项/相似歌曲/精选歌词）随 `MusicInfo.extra` 一起返回，
     * 不再单独回查——那是旧 DailyMusicInfo（已删除）读的同一批列。
     */
    data class MusicRecommendation(
        val musicInfo: MusicInfo?,
        val labels: List<MusicLabel?>
    )

    suspend fun getMusicWithExtraById(musicId: Long): MusicRecommendation? {
        try {
            val musicInfo = withTimeoutOrNull(2000) {
                musicRepository.getMusicInfoById(musicId).firstOrNull()
            } ?: run {
                HmpLog.w(LogTag.DataMusicRepo) { "🎵 getMusicWithExtraById | timeout or null | musicId=$musicId | timeoutMs=2000" }
                return null
            }

            val labels = musicRepository.getMusicLabels(musicId)
            return MusicRecommendation(musicInfo, labels)
        } catch (e: Exception) {
            HmpLog.e(LogTag.DataMusicRepo, e) { "🎵 getMusicWithExtraById failed | musicId=$musicId | reason=${e.message}" }
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
