package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository

/**
 * 搜索音乐
 * Use Case: 封装音乐搜索逻辑
 */
class SearchMusicUseCase(

    private val musicRepository: MusicRepository,
) {
    /**
     * 根据关键词搜索音乐
     * @param query 搜索关键词
     * @return 匹配的音乐列表
     */
    suspend operator fun invoke(query: String): List<MusicInfo> = musicRepository.searchMusic(query)
}
