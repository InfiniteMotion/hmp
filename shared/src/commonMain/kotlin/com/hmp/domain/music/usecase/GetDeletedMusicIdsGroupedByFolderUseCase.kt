package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.flow.Flow

/**
 * 已软删除的歌曲按父文件夹路径分组，用于设置页「已隐藏文件夹」列表与恢复。
 * 返回 (文件夹路径, 该文件夹下已删除歌曲的 id 列表)。
 */
class GetDeletedMusicIdsGroupedByFolderUseCase(

    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(): List<Pair<String, List<Long>>> = musicRepository.getDeletedMusicIdsGroupedByFolder()

    /** 可观察版本：删除 / 恢复（含其他入口的删除）都会让列表自己回流。 */
    fun observe(): Flow<List<Pair<String, List<Long>>>> = musicRepository.observeDeletedMusicIdsGroupedByFolder()
}
