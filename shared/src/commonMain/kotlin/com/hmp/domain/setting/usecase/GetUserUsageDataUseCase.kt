package com.hmp.domain.setting.usecase

import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.UserUsageAnalytics

/**
 * 获取用户使用数据分析结果，供「用户使用数据」页展示。
 */
class GetUserUsageDataUseCase(

    private val musicRepository: MusicRepository,
) {
    suspend fun getAnalytics(): UserUsageAnalytics = musicRepository.getUserUsageAnalytics()
}
