package com.hmp.domain.music

import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName

data class Music(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumArtUri: String,
)

data class MusicExtra(
    val id: Long,
    val lyrics: String? = null,
    val bitRate: Int? = null,           // 比特率 kbps
    val sampleRate: Int? = null,        // 采样率 Hz
    val fileSize: Long? = null,         // 文件大小 Byte
    val format: String? = null,         // 文件格式 mp3/flac
    val language: String? = null,       // 语言
    /**  app 首次读取该歌曲的时间（毫秒时间戳），用于排序与年月索引 */
    val date: Long? = null,
    val recommendationIds: String? = null,  // 推荐关联的音乐ID列表
    // 其他额外信息
    val isGetExtraInfo : Boolean,
    val rewards : String? = null,
    val popLyric : String? = null,
    val singerIntroduce : String? = null,
    val backgroundIntroduce : String? = null,
    val description : String? = null,
    val relevantMusic : String? = null
)

/**
 * 富化写入的 6 个文本列（musicExtra 表），**写侧专用**。
 *
 * 与 [MusicExtra] 的关系：[MusicExtra] 是**读模型**（含技术信息 lyrics/bitRate/format/…
 * 与首次读取时间），这 6 列只是其中一部分；写侧单独成类型，是为了避免调用方为了改 6 个
 * 文本列而去构造一个完整 [MusicExtra] —— 那样容易顺手覆盖掉技术字段。
 *
 * 写入会把 `isGetExtraInfo` 一并置 true（见 `MusicExtraDao.updateExtraFieldsById` 的 SQL），
 * 即「写入富化文案」同时意味着「标记该曲已富化」。
 */
data class MusicExtraTexts(
    val rewards: String? = null,
    val popLyric: String? = null,
    val singerIntroduce: String? = null,
    val backgroundIntroduce: String? = null,
    val description: String? = null,
    val relevantMusic: String? = null,
)

data class MusicInfo(
    val music: Music,
    val extra: MusicExtra?,
    val userInfo: UserInfo?
)

data class MusicLabel(
    val musicId: Long,
    val type: LabelCategory,
    val label: LabelName
)

/**
 * 可编辑的单曲标签（ID3 元数据）。
 * 为 null 的字段表示该项保持不变。
 */
data class EditableMusicTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val track: String? = null,
    val lyrics: String? = null,
    /**
     * 新专辑封面（JPEG/PNG 字节）。null 表示不修改；
     * 空数组表示移除封面；非空数组表示替换封面。
     */
    val albumArt: ByteArray? = null,
) {
    val hasChanges: Boolean
        get() = title != null || artist != null || album != null ||
            year != null || genre != null || track != null ||
            lyrics != null || albumArt != null
}

data class UserInfo(
    val id: Long,
    val liked: Boolean = false,
    val disLiked: Boolean = false,
    val lastPlayed: Long? = null,
    val playCount: Int? = null,
    val skippedCount: Int? = null,
    val userRating: Int? = null,
    val inCustomPlaylistCount: Int? = null,
)
