package com.hmp.domain.music.usecase

import co.touchlab.kermit.Logger
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

class GetDailyMusicRecommendationUseCase(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val musicLabelUseCase: MusicLabelUseCase
) {

    private var _isPaused = false
    private var _isCancelled = false

    fun pauseProcessing() {
        _isPaused = true
        Logger.d("UseCase.DailyRec") { "Processing paused" }
    }

    fun resumeProcessing() {
        _isPaused = false
        Logger.d("UseCase.DailyRec") { "Processing resumed" }
    }

    fun cancelProcessing() {
        _isCancelled = true
        _isPaused = false
        Logger.d("UseCase.DailyRec") { "Processing cancelled" }
    }

    fun resetProcessingState() {
        _isPaused = false
        _isCancelled = false
    }

    fun isPaused(): Boolean = _isPaused

    fun isCancelled(): Boolean = _isCancelled

    data class ProcessingResult(
        val totalProcessed: Int = 0,
        val successCount: Int = 0,
        val skippedCount: Int = 0,
        val failedCount: Int = 0,
        val errors: List<String> = emptyList(),
        val wasCancelled: Boolean = false
    ) {
        val isAllSuccess: Boolean
            get() = totalProcessed > 0 && failedCount == 0 && skippedCount == 0
    }

    sealed class ExtraInfoResult {
        data class Success(val intro: DailyMusicInfo) : ExtraInfoResult()
        data object Skipped : ExtraInfoResult()
        data class Error(val message: String) : ExtraInfoResult()
    }

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

    private suspend fun saveMusicLabels(musicId: Long, dailyMusicInfo: DailyMusicInfo) {
        val labels = MusicLabels(
            genres = dailyMusicInfo.genre,
            moods = dailyMusicInfo.mood,
            scenarios = dailyMusicInfo.scenario,
            language = dailyMusicInfo.language,
            era = dailyMusicInfo.era
        )
        musicLabelUseCase.addMusicLabels(musicId, labels)
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

    suspend fun autoProcessMissingExtraInfoWithCurrentProvider(
        onProgress: suspend (MusicInfo) -> Unit = {},
        onComplete: suspend (ProcessingResult) -> Unit = {},
        delayMillis: Long = 500
    ) {
        resetProcessingState()

        val activeConfig = settingsRepository.getActiveAiConfig()

        if (!activeConfig.isConfigured) {
            Logger.w("UseCase.DailyRec") { "No AI provider configured, skipping auto process" }
            onComplete(ProcessingResult())
            return
        }

        var successCount = 0
        var skippedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()

        while (true) {
            if (isCancelled()) {
                Logger.d("UseCase.DailyRec") { "Processing cancelled by user" }
                break
            }

            while (isPaused()) {
                delay(100)
                if (isCancelled()) break
            }

            if (isCancelled()) break

            val music = musicRepository.getRandomMusicInfoWithMissingExtra() ?: break

            onProgress(music)

            when (val result = getMusicExtraInfoWithCurrentProviderAndResult(music)) {
                is ExtraInfoResult.Success -> successCount++
                is ExtraInfoResult.Skipped -> skippedCount++
                is ExtraInfoResult.Error -> {
                    failedCount++
                    errors.add("${music.music.title}: ${result.message}")
                }
            }

            delay(delayMillis)
        }

        val processingResult = ProcessingResult(
            totalProcessed = successCount + skippedCount + failedCount,
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            errors = errors,
            wasCancelled = isCancelled()
        )

        onComplete(processingResult)
        Logger.d("UseCase.DailyRec") { "Processing completed: $processingResult" }
    }

    private suspend fun getMusicExtraInfoWithCurrentProviderAndResult(input: MusicInfo): ExtraInfoResult {
        val activeConfig = settingsRepository.getActiveAiConfig()

        if (!activeConfig.isConfigured) {
            return ExtraInfoResult.Skipped
        }

        // 检查免费额度
        val mode = settingsRepository.getAiAccessMode()
        if (mode == AiAccessMode.FREE) {
            val remaining = settingsRepository.getAiFreeTrialRemainingCount()
            if (remaining <= 0) {
                return ExtraInfoResult.Error("免费体验次数已用完，请配置 API Key 继续使用")
            }
        }

        val result = musicRepository.fetchMusicExtraInfoWithProvider(
            activeConfig,
            input.music.title,
            input.music.artist
        )

        return result.fold(
            onSuccess = { intro ->
                musicRepository.insertMusicExtra(input.music.id, intro)
                saveMusicLabels(input.music.id, intro)
                // 免费模式下递减计数
                if (mode == AiAccessMode.FREE) {
                    settingsRepository.decrementAiFreeTrialCount()
                }
                Logger.d("UseCase.DailyRec") { "Successfully processed music via ${activeConfig.endpoint}" }
                ExtraInfoResult.Success(intro)
            },
            onFailure = { exception ->
                Logger.e("UseCase.DailyRec", exception) { "Fetch extra info failed: ${exception.message}" }
                ExtraInfoResult.Error(exception.message ?: "Unknown error")
            }
        )
    }

    fun getRecentListeningDurations(): Flow<List<ListeningDuration>> {
        return musicRepository.getRecentListeningDurations(35)
    }
}
