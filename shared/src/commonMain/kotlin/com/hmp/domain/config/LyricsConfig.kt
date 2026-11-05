package com.hmp.domain.config

import kotlinx.serialization.Serializable

/**
 * 歌词配置数据模型
 * 包含用户可自定义的歌词显示参数
 */
data class LyricsConfig(
    // 文本大小配置
    val originalTextSize: Int = 14,
    val translatedTextSize: Int = 14,
    val currentTimeTextSize: Int = 16,

    // 间距配置
    val lineSpacing: Int = 6,

    // 逐字（卡拉 OK）显示开关
    val karaokeEnabled: Boolean = true,

    // 显示模式配置
    val displayMode: DisplayMode = DisplayMode.DUAL,

    // 对齐配置
    val alignment: LyricsAlignment = LyricsAlignment.CENTER,
)

/**
 * 显示模式枚举
 */
@Serializable
enum class DisplayMode {
    LANG1, // 只显示语言一
    LANG2, // 只显示语言二
    DUAL, // 双语显示
}

/**
 * 歌词对齐方式枚举
 */
@Serializable
enum class LyricsAlignment {
    LEFT, // 左对齐
    CENTER, // 居中对齐
    RIGHT, // 右对齐
}
