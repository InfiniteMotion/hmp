package com.hmp.domain.playlist.algorithm

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.playlist.AlgorithmType

import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.ExtensionConfig
import kotlinx.coroutines.flow.firstOrNull
import com.hmp.domain.playlist.algorithm.strategies.SimilarityStrategy
import com.hmp.domain.playlist.algorithm.strategies.OptimizedStrategy
import com.hmp.domain.playlist.algorithm.strategies.ChainStrategy

/**
 * 统一的播放列表生成算法类
 * 通过组合模式整合所有算法策略
 */
class PlaylistAlgorithms(
    private val musicRepository: MusicRepository
) {
    private val strategies = mapOf(
        AlgorithmType.OPTIMIZED_SIMILARITY to OptimizedStrategy(),
        AlgorithmType.CHAIN_SIMILARITY to ChainStrategy()
    )
    
    val algorithmName: String = "统一播放列表算法"
    val description: String = "支持多种相似度计算策略的统一推荐算法"
    
    /**
     * 根据算法类型获取对应的策略
     */
    private fun getStrategy(type: AlgorithmType): SimilarityStrategy {
        return strategies[type] ?: throw IllegalArgumentException("不支持的算法类型: $type")
    }
    
    /**
     * 生成推荐列表
     */
    suspend fun generate(seedMusicId: Long, algorithmType: AlgorithmType, vararg params: Any): List<MusicInfo> {
        val strategy = getStrategy(algorithmType)
        val processedParams = strategy.processParameters(*params)
        
        // 获取种子音乐和标签
        val seedMusic = musicRepository.getMusicInfoById(seedMusicId).firstOrNull() ?: return emptyList()
        val seedLabels = musicRepository.getMusicLabels(seedMusicId)
        
        if (seedLabels.isEmpty()) return emptyList()
        
        // 获取所有候选音乐
        val allMusic = musicRepository.getAllMusicInfoAsList(MusicSortKey.ID, SortDirection.ASC)
            .filter { it.music.id != seedMusicId }
        
        // 根据不同策略执行不同的生成逻辑
        return when (strategy) {
            is OptimizedStrategy -> {
                generateOptimizedRecommendation(allMusic, seedLabels, processedParams, strategy)
            }
            
            is ChainStrategy -> {
                generateChainRecommendation(
                    seedMusicId, seedLabels, processedParams, strategy
                )
            }
            
            else -> throw IllegalArgumentException("未知策略类型")
        }
    }
    
    /**
     * 优化推荐生成逻辑
     */
    private suspend fun generateOptimizedRecommendation(
        allMusic: List<MusicInfo>,
        seedLabels: List<MusicLabel>,
        processedParams: Map<String, Any>,
        strategy: OptimizedStrategy
    ): List<MusicInfo> {
        val limit = processedParams["limit"] as Int

        
        return allMusic.map { candidate ->
            val candidateLabels = musicRepository.getMusicLabels(candidate.music.id)
            val similarity = strategy.calculateSimilarity(
                seedLabels, candidateLabels
            )
            candidate to similarity
        }.filter { it.second > 0.0 }
         .sortedByDescending { it.second }
         .take(limit)
         .map { it.first }
    }
    
    /**
     * 链式推荐生成逻辑
     */
    private suspend fun generateChainRecommendation(
        seedMusicId: Long,
        seedLabels: List<MusicLabel>,
        processedParams: Map<String, Any>,
        strategy: ChainStrategy
    ): List<MusicInfo> {
        val limit = processedParams["limit"] as Int
        val maxChainLength = processedParams["maxChainLength"] as Int

        val excludeSeed = processedParams["excludeSeed"] as Boolean
        
        val result = mutableListOf<MusicInfo>()
        val visited = mutableSetOf<Long>()
        var currentMusicId = seedMusicId
        var chainLength = 0
        
        // 添加种子音乐作为起点
        val seedMusic = musicRepository.getMusicInfoById(seedMusicId).firstOrNull() ?: return emptyList()
        result.add(seedMusic)
        visited.add(seedMusicId)
        chainLength++
        
        // 链式搜索
        while (chainLength < maxChainLength) {
            val currentLabels = musicRepository.getMusicLabels(currentMusicId)
            if (currentLabels.isEmpty()) break
            
            // 寻找下一个最相似且未访问的音乐
            val nextMusic = findNextMostSimilarMusic(
                currentLabels, visited, strategy
            )
            
            if (nextMusic != null) {
                result.add(nextMusic)
                visited.add(nextMusic.music.id)
                currentMusicId = nextMusic.music.id
                chainLength++
            } else {
                // 无法找到合适的下一首音乐，终止链式搜索
                break
            }
        }
        
        // 根据需要移除种子音乐
        return if (excludeSeed) {
            result.drop(1)
        } else {
            result
        }.take(limit)
    }
    
    /**
     * 寻找下一个最相似的音乐
     */
    private suspend fun findNextMostSimilarMusic(
        currentLabels: List<MusicLabel>,
        visited: Set<Long>,
        strategy: ChainStrategy,

    ): MusicInfo? {
        val allMusic = musicRepository.getAllMusicInfoAsList(MusicSortKey.ID, SortDirection.ASC)
        
        return allMusic
            .filter { it.music.id !in visited }
            .map { candidate ->
                val candidateLabels = musicRepository.getMusicLabels(candidate.music.id)
                val similarity = strategy.calculateSimilarity(
                    currentLabels, candidateLabels
                )
                candidate to similarity
            }
            .filter { it.second >= 0.1 } // 使用策略的阈值
            .maxByOrNull { it.second }
            ?.first
    }
    
    /**
     * 获取带相似度分数的结果（用于自适应截断）
     */
    suspend fun generateWithScores(
        seedMusicId: Long, 
        algorithmType: AlgorithmType, 
        limit: Int = 50
    ): List<Pair<MusicInfo, Double>> {
        val strategy = getStrategy(algorithmType)
        
        val seedMusic = musicRepository.getMusicInfoById(seedMusicId) ?: return emptyList()
        val seedLabels = musicRepository.getMusicLabels(seedMusicId)
        
        if (seedLabels.isEmpty()) return emptyList()
        
        val allMusic = musicRepository.getAllMusicInfoAsList(MusicSortKey.ID, SortDirection.ASC)
            .filter { it.music.id != seedMusicId }
        
        return allMusic.map { candidate ->
            val candidateLabels = musicRepository.getMusicLabels(candidate.music.id)
            val similarity = strategy.calculateSimilarity(
                seedLabels, candidateLabels
            )
            candidate to similarity
        }.filter { it.second > 0.0 }
         .sortedByDescending { it.second }
         .take(limit)
    }
}

/**
 * 自适应扩展截断核心逻辑
 */
class AdaptiveExtensionTruncation {
    
    fun calculateOptimalExtensionPoint(
        similarityScores: List<Pair<MusicInfo, Double>>,
        config: ExtensionConfig
    ): Int {
        if (similarityScores.isEmpty()) return 0
        if (similarityScores.size <= config.minLength) return similarityScores.size
        
        val minLengthIndex = config.minLength - 1
        val maxExtendedIndex = minOf(
            config.getMaxExtendedLength() - 1,
            similarityScores.size - 1
        )
        
        if (maxExtendedIndex == similarityScores.size - 1) {
            return similarityScores.size
        }
        
        val baselineScore = similarityScores[minLengthIndex].second
        var optimalIndex = minLengthIndex
        
        for (i in minLengthIndex + 1..maxExtendedIndex) {
            val currentScore = similarityScores[i].second
            val dropRatio = (baselineScore - currentScore) / baselineScore
            
            if (dropRatio > config.qualityDropThreshold) {
                optimalIndex = i - 1
                break
            }
            
            optimalIndex = i
        }
        
        return optimalIndex + 1
    }
    
    fun truncateWithAdaptiveExtension(
        musicList: List<MusicInfo>,
        similarityScores: List<Double>,
        config: ExtensionConfig
    ): List<MusicInfo> {
        if (musicList.isEmpty() || musicList.size != similarityScores.size) {
            return emptyList()
        }
        
        val pairedList = musicList.zip(similarityScores) { music, score ->
            music to score
        }.sortedByDescending { it.second }
        
        val optimalLength = calculateOptimalExtensionPoint(pairedList, config)
        return pairedList.take(optimalLength).map { it.first }
    }
    
    fun analyzeQualityMetrics(
        similarityScores: List<Double>,
        config: ExtensionConfig
    ): QualityMetrics {
        if (similarityScores.isEmpty()) {
            return QualityMetrics.EMPTY
        }
        
        val sortedScores = similarityScores.sortedDescending()
        val baselineScore = if (sortedScores.size >= config.minLength) {
            sortedScores[config.minLength - 1]
        } else {
            sortedScores.lastOrNull() ?: 0.0
        }
        
        val maxLength = minOf(config.getMaxExtendedLength(), sortedScores.size)
        var qualityDropPoint = maxLength
        
        for (i in config.minLength.coerceAtMost(sortedScores.size) until maxLength) {
            val dropRatio = (baselineScore - sortedScores[i]) / baselineScore
            if (dropRatio > config.qualityDropThreshold) {
                qualityDropPoint = i
                break
            }
        }
        
        return QualityMetrics(
            totalCandidates = sortedScores.size,
            baselineScore = baselineScore,
            qualityDropPoint = qualityDropPoint,
            maxRecommendedLength = maxLength,
            averageScoreInExtendedRange = if (qualityDropPoint > config.minLength) {
                sortedScores.subList(config.minLength, qualityDropPoint).average()
            } else 0.0
        )
    }
}

/**
 * 推荐质量分析指标数据类
 */
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
    
    val extensionRate: Double
        get() = if (totalCandidates > 0) {
            qualityDropPoint.toDouble() / totalCandidates
        } else 0.0
    
    val qualityRetentionRate: Double
        get() = if (baselineScore > 0 && averageScoreInExtendedRange > 0) {
            averageScoreInExtendedRange / baselineScore
        } else 0.0
}