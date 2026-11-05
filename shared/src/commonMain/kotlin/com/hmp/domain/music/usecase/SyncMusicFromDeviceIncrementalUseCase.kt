package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * 增量扫描设备音乐文件并同步到数据库
 */
class SyncMusicFromDeviceIncrementalUseCase(

    private val musicRepository: MusicRepository,
) {
    @Throws(CancellationException::class)
    suspend operator fun invoke(): Result<Unit> = musicRepository.syncMusicFromDeviceIncremental()

    fun isScanning() = musicRepository.isScanning
}
