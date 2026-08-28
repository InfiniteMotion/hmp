package com.hmp.domain.agent.port

import com.hmp.domain.setting.model.DailyMusicInfo

/**
 * 单曲 AI 富化端口（M3 enrichSong 工具依赖）。
 *
 * 富化需要真实 AI 配置（[DailyMusicInfo] 的 genre/mood/scenario 等来自大模型）。
 * 出于安全（AiEndpointConfig 含密钥）与可测性，端口层不暴露 config，由接线方用真实
 * MusicRepository.fetchMusicExtraInfoWithProvider + 注入的 config 实现，测试用 Fake。
 */
interface AiExtraEnrichPort {
    /** 富化指定（title, artist）单曲；返回富化结果或失败摘要。 */
    suspend fun enrich(title: String, artist: String): Result<DailyMusicInfo>
}