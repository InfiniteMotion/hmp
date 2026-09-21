package com.hmp.domain.agent.runtime.sub.radio

import com.hmp.domain.enum.LabelName

/**
 * 用户自由文本（「来点蓝调」/「Jazz 放松」）→ [LabelName] 的关键词词表。
 *
 * 从 `RadioSubAgent` 抽出：这是一张**同义词表 + 匹配规则**，
 * 与电台的编排流程无关，且是"不依赖 LLM 的确定性匹配"这一策略的**唯一落点**。
 * 独立出来后，「支持了哪些说法」可以直接读这一处，不必在 Agent 里翻找。
 */
internal object RadioSeedKeywords {

    /** 关键词 → LabelName 列表（不依赖 LLM 的简单匹配）。 */
    fun fromKeyword(keyword: String): List<LabelName> {
        val lower = keyword.lowercase()
        return LabelName.entries.filter { label ->
            label.name.lowercase() in lower || matches(label, lower)
        }.take(5)
    }


    /** 单个标签的同义词匹配。未列出的标签永不通过关键词命中。 */
    private fun matches(label: LabelName, lower: String): Boolean = when (label) {
        LabelName.BLUES -> "blues" in lower || "蓝调" in lower
        LabelName.JAZZ -> "jazz" in lower || "爵士" in lower
        LabelName.CLASSICAL -> "classical" in lower || "古典" in lower || "交响" in lower
        LabelName.ROCK -> "rock" in lower || "摇滚" in lower
        LabelName.POP -> "pop" in lower || "流行" in lower
        LabelName.HIPHOP -> "hiphop" in lower || "hip-hop" in lower || "说唱" in lower
        LabelName.ELECTRONIC -> "electronic" in lower || "电子" in lower || "edm" in lower
        LabelName.FOLK -> "folk" in lower || "民谣" in lower
        LabelName.RNB -> "r&b" in lower || "rnb" in lower || "节奏" in lower
        LabelName.CALM -> "calm" in lower || "放松" in lower || "安静" in lower
        LabelName.ENERGETIC -> "energetic" in lower || "活力" in lower
        LabelName.WORKOUT -> "workout" in lower || "运动" in lower || "健身" in lower
        LabelName.SLEEP -> "sleep" in lower || "睡前" in lower || "助眠" in lower
        LabelName.DRIVING -> "driving" in lower || "开车" in lower || "驾驶" in lower
        LabelName.STUDY -> "study" in lower || "学习" in lower || "专注" in lower
        LabelName.RELAX -> "relax" in lower || "休闲" in lower || "休息" in lower
        LabelName.PARTY -> "party" in lower || "派对" in lower || "蹦迪" in lower
        LabelName.MORNING -> "morning" in lower || "早晨" in lower || "清晨" in lower
        LabelName.NIGHT -> "night" in lower || "夜晚" in lower || "深夜" in lower
        LabelName.SAD -> "sad" in lower || "悲伤" in lower || "伤感" in lower
        LabelName.HAPPY -> "happy" in lower || "开心" in lower || "欢快" in lower
        LabelName.ROMANTIC -> "romantic" in lower || "浪漫" in lower || "情歌" in lower
        else -> false
    }
}
