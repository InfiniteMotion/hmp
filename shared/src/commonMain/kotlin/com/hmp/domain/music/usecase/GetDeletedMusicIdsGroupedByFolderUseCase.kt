package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicRepository

/**
 * 已软删除的歌曲按父文件夹路径分组，用于设置页「已隐藏文件夹」列表与恢复。
 * 返回 (文件夹路径, 该文件夹下已删除歌曲的 id 列表)。
 */
class GetDeletedMusicIdsGroupedByFolderUseCase(

    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(): List<Pair<String, List<Long>>> = musicRepository.getDeletedMusicIdsGroupedByFolder()
}
