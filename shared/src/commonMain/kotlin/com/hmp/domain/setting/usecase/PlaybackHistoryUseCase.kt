package com.hmp.domain.setting.usecase

import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow

class PlaybackHistoryUseCase(
    private val musicRepository: MusicRepository,
) {
    suspend fun insertPlayback(history: PlaybackHistory): Long = musicRepository.insertPlayback(history)

    suspend fun startPlaybackSession(musicId: Long, source: String? = null): Long {
        val now = currentTimeMillis()
        musicRepository.updateLastPlayed(musicId, now)
        musicRepository.incrementPlayCount(musicId)
        return musicRepository.insertPlayback(
            PlaybackHistory(
                musicId = musicId,
                playedAt = now,
                playDuration = 0,
                isCompleted = false,
                source = source,
            ),
        )
    }

    suspend fun completePlaybackSession(historyId: Long, musicId: Long, duration: Long) {
        musicRepository.updatePlaybackRecord(historyId, duration, true)
    }

    suspend fun skipPlaybackSession(historyId: Long, musicId: Long, duration: Long, isSkip: Boolean) {
        musicRepository.updatePlaybackRecord(historyId, duration, false)
        if (isSkip) {
            musicRepository.incrementSkippedCount(musicId)
        }
    }

    suspend fun recordPlaybackHistory(musicId: Long) {
        startPlaybackSession(musicId)
    }

    suspend fun recordListeningDuration(duration: Long) {
        musicRepository.recordListeningDuration(duration)
    }

    fun getPlaybackHistory(musicId: Long, limit: Int = 5): Flow<List<PlaybackHistory>> = musicRepository.getPlaybackHistory(musicId, limit)
}
