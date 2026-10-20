package com.hmp.domain.playlist

import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import kotlinx.serialization.Serializable

/**
 * 播放列表生成相关数据模型集合
 */

/**
 * 播放列表生成算法类型枚举
 */
@Serializable
enum class AlgorithmType {
    /**
     * 优化相似推荐算法
     * 基于多维度标签权重的智能相似度匹配
     */
    OPTIMIZED_SIMILARITY,
    
    /**
     * 链式相似推荐算法
     * DFS风格的连续相似音乐发现
     */
    CHAIN_SIMILARITY
}

/**
 * 标签优先级配置数据模型
 * 用于标签优先级推荐算法的配置
 */
/**
 * 权重模板枚举
 * 预设的不同场景下的标签权重配置
 */
@Serializable
enum class WeightTemplate {
    /**
     * 平衡权重配置
     * 各类别标签权重相对均衡
     */
    BALANCED,
    
    /**
     * 流派优先配置
     * GENRE权重最高，强调音乐风格的一致性
     */
    GENRE_FOCUS,
    
    /**
     * 情绪优先配置
     * MOOD权重最高，注重情感氛围的连贯性
     */
    MOOD_FOCUS,
    
    /**
     * 场景优先配置
     * SCENARIO权重最高，关注使用场景的匹配度
     */
    SCENARIO_FOCUS,
    
    /**
     * 年代优先配置
     * ERA权重最高，强调时间背景的一致性
     */
    ERA_FOCUS
}

/**
 * 自适应扩展配置数据模型
 * 控制播放列表自适应截断的行为参数
 */
@Serializable
data class ExtensionConfig(
    /**
     * 最小推荐数量
     * 保证至少返回的音乐数量
     */
    val minLength: Int = 10,
    
    /**
     * 质量下降阈值
     * 当相似度下降超过此比例时考虑截断
     */
    val qualityDropThreshold: Double = 0.25,
    
    /**
     * 最大扩展倍数
     * 相对于最小长度的最大扩展倍数
     */
    val maxExtensionFactor: Double = 2.5
) {
    
    init {
        require(minLength > 0) { "最小长度必须大于0" }
        require(qualityDropThreshold in 0.0..1.0) { "质量下降阈值必须在0.0-1.0之间" }
        require(maxExtensionFactor >= 1.0) { "最大扩展倍数必须大于等于1.0" }
    }
    
    /**
     * 计算最大扩展长度
     */
    fun getMaxExtendedLength(): Int {
        return (minLength * maxExtensionFactor).toInt()
    }
    
    /**
     * 将配置序列化为JSON字符串
     */
    fun toJson(): String {
        return "{\"minLength\":$minLength,\"qualityDropThreshold\":$qualityDropThreshold,\"maxExtensionFactor\":$maxExtensionFactor}"
    }
    
    companion object {
        /**
         * 保守配置 - 较短但高质量的推荐列表
         */
        val CONSERVATIVE = ExtensionConfig(
            minLength = 8,
            qualityDropThreshold = 0.2,
            maxExtensionFactor = 1.8
        )
        
        /**
         * 平衡配置 - 默认推荐设置
         */
        val BALANCED = ExtensionConfig(
            minLength = 10,
            qualityDropThreshold = 0.25,
            maxExtensionFactor = 2.5
        )
        
        /**
         * 激进配置 - 更长的推荐列表
         */
        val AGGRESSIVE = ExtensionConfig(
            minLength = 15,
            qualityDropThreshold = 0.3,
            maxExtensionFactor = 3.0
        )
    }
}

/**
 * 推荐质量分析指标数据类
 */
@Serializable
data class QualityMetrics(
    val totalCandidates: Int,
    val baselineScore: Double,
    val qualityDropPoint: Int,
    val maxRecommendedLength: Int,
    val averageScoreInExtendedRange: Double
) {
    companion object {
        val EMPTY = QualityMetrics(0, 0.0, 0, 0, 0.0)
    }
    
    /**
     * 扩展率
     */
    val extensionRate: Double
        get() = if (totalCandidates > 0) {
            qualityDropPoint.toDouble() / totalCandidates
        } else 0.0
    
    /**
     * 质量保持率
     */
    val qualityRetentionRate: Double
        get() = if (baselineScore > 0 && averageScoreInExtendedRange > 0) {
            averageScoreInExtendedRange / baselineScore
        } else 0.0
}

@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val coverUri: String? = null,
    val playCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastPlayedAt: Long? = null,
    val description: String? = null,
    val songCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val isPinned: Boolean = false
)

@Serializable
data class PlaylistItem(
    val songId: Long,
    val playlistId: Long,
)