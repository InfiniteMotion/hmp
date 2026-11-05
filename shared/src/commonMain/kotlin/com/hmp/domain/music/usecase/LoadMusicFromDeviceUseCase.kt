package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * 从设备扫描音乐文件并加载到数据库
 * Use Case: 业务逻辑层,封装音乐扫描的完整流程
 */
class LoadMusicFromDeviceUseCase(

    private val musicRepository: MusicRepository,
) {
    /**
     * 执行音乐扫描
     * @return Result<Unit> 扫描结果
     */
    @Throws(CancellationException::class)
    suspend operator fun invoke(): Result<Unit> = musicRepository.loadMusicFromDevice()

    /**
     * 获取扫描状态
     */
    fun isScanning() = musicRepository.isScanning
}
