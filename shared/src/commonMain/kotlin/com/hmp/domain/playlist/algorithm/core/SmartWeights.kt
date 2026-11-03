package com.hmp.domain.playlist.algorithm.core

import com.hmp.domain.enum.LabelCategory
import kotlin.random.Random

/**
 * 一次智能生成使用的权重快照（`D1`，内部黑盒）。
 *
 * 用户不再选择模板；每次生成由 [SmartWeights] 随机抽一个**偏重维度**（[emphasis]），
 * 其余维度做小幅扰动。同一首歌在不同次生成里会得到不同权重、不同偏重 —— 这是设计意图，
 * 不是不确定性缺陷。
 */
data class SmartWeightSet(
    val seed: Long,
    /** 本次刻意加权的维度（每次生成随机，尽量不与上一次重复）。 */
    val emphasis: LabelCategory,
    val weights: Map<LabelCategory, Double>,
)

/**
 * 智能推荐的内部权重（`D1`）。
 *
 * 三条纪律：
 * 1. **黑盒**：没有任何 UI 读取或写入；参数只在这里定。
 * 2. **每次不同**：seed 由调用方每次生成时现取（[randomSeed]），
 *    因此同一首歌、同一天、连续两次生成也会得到不同的偏重。
 * 3. **可复现**：给定 seed 与 `avoidEmphasis`，结果逐位一致；seed 落进 payload 供排查。
 *
 * 算法：内部先验（轻微偏好 MOOD/GENRE）→ 随机抽一个维度做 [EMPHASIS_BOOST_MIN]–[EMPHASIS_BOOST_MAX] 倍加权
 * → 其余维度 ±[JITTER_RATIO] 抖动 → 归一化。先验只保证"没有维度被压到 0"，
 * 真正的偏重由每次随机抽的 emphasis 决定。
 */
object SmartWeights {

    /** 非主维度的扰动幅度。 */
    const val JITTER_RATIO: Double = 0.15

    /** 主维度的加权区间。 */
    const val EMPHASIS_BOOST_MIN: Double = 1.8
    const val EMPHASIS_BOOST_MAX: Double = 2.6

    /**
     * 内部先验：只做轻微倾斜，避免某个维度被压没。
     *
     * 不用原 BALANCED 模板的 4:3:2:1 差距——
     * 那个差距太大，随机主维度很难翻过 MOOD，达不到"每次偏重不同"。
     */
    val PRIOR: Map<LabelCategory, Double> = mapOf(
        LabelCategory.GENRE to 1.1,
        LabelCategory.MOOD to 1.2,
        LabelCategory.SCENARIO to 1.0,
        LabelCategory.ERA to 0.9,
    )

    /** 每次生成现取一个 seed（不要按歌/按日期派生）。 */
    fun randomSeed(): Long = Random.Default.nextLong()

    /**
     * 按 seed 生成一组权重。
     *
     * @param avoidEmphasis 上一次的偏重维度；尽量避开，避免连续两次推同一维度
     *   （只有 4 个维度，避不开时允许重复）。
     */
    fun forSeed(seed: Long, avoidEmphasis: LabelCategory? = null): SmartWeightSet {
        val rng = Random(seed)
        val pool = PRIOR.keys.filter { it != avoidEmphasis }.ifEmpty { PRIOR.keys.toList() }
        val emphasis = pool[rng.nextInt(pool.size)]
        val boost = rng.nextDouble(EMPHASIS_BOOST_MIN, EMPHASIS_BOOST_MAX)

        val weighted = PRIOR.mapValues { (category, prior) ->
            val factor = if (category == emphasis) {
                boost
            } else {
                1.0 + rng.nextDouble(-JITTER_RATIO, JITTER_RATIO)
            }
            (prior * factor).coerceAtLeast(0.05)
        }
        val sum = weighted.values.sum().takeIf { it > 0.0 } ?: 1.0

        return SmartWeightSet(
            seed = seed,
            emphasis = emphasis,
            weights = weighted.mapValues { (_, w) -> w / sum },
        )
    }

    /** 电台一档的 seed：同一档内补歌沿用同一套权重，新一档换 seed。 */
    fun seedForRadioSession(sessionId: String): Long = stableHash("radio:$sessionId")

    /** FNV-1a 64 位；跨平台逐位一致。 */
    internal fun stableHash(text: String): Long {
        // 用十进制常量而不是十六进制负字面量：FNV offset basis = 14695981039346656037，
        // 作为有符号 Long 是 -3750763034362895579（写成 -0x… 很容易抄错一位）。
        var hash = -3750763034362895579L
        for (ch in text) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        return hash
    }
}
