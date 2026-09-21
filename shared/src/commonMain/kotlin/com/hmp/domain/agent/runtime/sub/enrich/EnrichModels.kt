package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.music.MusicExtraTexts

/**
 * Enrich 5 轮的**中间态草稿** —— Round 1 枚举 + Round 2a/2b 自由文本合并后的结果，
 * 供 Round 3 反思轮看到"完整状态"再下 patch。
 *
 * 前 5 个结构化字段的归宿是 **labels 表**（`EnrichSubAgent.writeLabelsFromDraft` →
 * `addMusicLabel`），后 6 个文本字段的归宿是 **musicExtra 表的 6 列**
 * （[toExtraTexts]）。两路分开落库，所以这个类型**不代表任何一张表**，
 * 它只是"一次富化产出"的内存形态 —— 别把它当持久化模型去对齐字段。
 *
 * 取代了原先复用的 `DailyMusicInfo`：那个类是旧富化管道的遗留物，12 个字段里
 * 有 1 个（`errorInfo`）从来不落库，且名字里的 "Daily" 与每日推荐早已无关。
 */
internal data class EnrichDraft(
    val genre: List<String> = emptyList(),
    val mood: List<String> = emptyList(),
    val scenario: List<String> = emptyList(),
    val language: String = "UNKNOWN",
    val era: String = "UNKNOWN",
    val description: String = "",
    val singerIntroduce: String = "",
    val rewards: String = "",
    val lyric: String = "",
    val backgroundIntroduce: String = "",
    val relevantMusic: String = "",
) {
    /** 6 个文本字段 → musicExtra 表的 6 列（注意 `lyric` 在该表上叫 `popLyric`）。 */
    fun toExtraTexts(): MusicExtraTexts = MusicExtraTexts(
        rewards = rewards,
        popLyric = lyric,
        singerIntroduce = singerIntroduce,
        backgroundIntroduce = backgroundIntroduce,
        description = description,
        relevantMusic = relevantMusic,
    )
}
