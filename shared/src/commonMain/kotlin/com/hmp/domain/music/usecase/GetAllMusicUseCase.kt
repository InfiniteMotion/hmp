package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * 获取所有音乐
 * Use Case: 封装获取音乐列表的逻辑,支持排序
 */
class GetAllMusicUseCase(
    private val musicRepository: MusicRepository
) {
    /**
     * 获取所有音乐列表
     * @param orderBy 排序字段 (title, artist, album, duration等)
     * @param orderType 排序方式 (ASC, DESC)
     * @return 音乐列表
     */
    @Throws(CancellationException::class)
    suspend operator fun invoke(
        orderBy: MusicSortKey = MusicSortKey.TITLE,
        orderType: SortDirection = SortDirection.ASC,
    ): List<MusicInfo> {
        return musicRepository.getAllMusicInfoAsList(orderBy, orderType)
    }

    /**
     * 获取音乐总数
     */
    fun getMusicCount(): Flow<Int> = musicRepository.getMusicCount()

    /**
     * 获取已处理额外信息的音乐数量
     */
    fun getMusicWithExtraCount(): Flow<Int> = musicRepository.getMusicWithExtraCount()

    /**
     * 获取待处理音乐数量（未获得额外信息的数量）
     */
    fun getMusicWithMissingExtraCount(): Flow<Int> = musicRepository.getMusicWithMissingExtraCount()

    /**
     * 根据歌手名获取音乐列表
     * @param artistName 歌手名称
     * @return 该歌手的所有音乐列表
     */
    suspend fun getMusicListByArtist(artistName: String): List<MusicInfo> {
        return musicRepository.getMusicListByArtist(artistName)
    }

    /**
     * 根据专辑名获取音乐列表
     * @param albumName 专辑名称
     * @return 该专辑的所有音乐列表
     */
    suspend fun getMusicListByAlbum(albumName: String): List<MusicInfo> {
        return musicRepository.getMusicListByAlbum(albumName)
    }

    /**
     * 根据ID获取音乐
     * @param musicId 音乐ID
     * @return 音乐信息，如果不存在则返回null
     */
    suspend fun getMusicById(musicId: Long): MusicInfo? {
        return musicRepository.getMusicInfoById(musicId).first()
    }
}