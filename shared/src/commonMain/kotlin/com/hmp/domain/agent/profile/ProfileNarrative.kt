package com.hmp.domain.agent.profile

import com.hmp.domain.agent.port.LlmMessage

/**
 * 画像叙事（契约 `agent-profile.md` v3.5 §7.4）—— LLM 依据**侧写渲染**生成的描述性文本。
 *
 * **两面共用同一份文本**（用户拍板，2026-09-16）：消费面作人格卡叙事段，认知面随画像块
 * 注入。共用之所以安全，靠的是这条铁律：
 *
 * > **叙事只能是侧写渲染的复述，一个新事实都不能有。**
 *
 * 因此生成输入只有槽位事实句（不含称号、不含证据原文）；[validate] 再拦
 * 人格命名与类型学专名（称号回流决策面是 §6.1 明令禁止的）。语言层的语义越界
 * （比如从 moodMix 编出"你最近压力大"）靠 prompt 约束 + 上游不喂情绪/处境类输入
 * 双重压制 —— 生成输入里根本没有能支撑这类断言的事实。
 *
 * 失败回退链：无端点 / 超时 / 校验不过 → 保留上一份叙事（宁旧勿假）；
 * 从未生成过 → 消费面退回模板句（[PersonalityCardComposer]），认知面块照常。
 */
object ProfileNarrative {

    /** 叙事长度上下限：一段话，不是一篇文章 */
    const val MIN_CHARS = 20
    const val MAX_CHARS = 200

    /** 类型学专名与混淆性近似（契约 §6.3）—— 叙事里出现即整篇作废 */
    private val TYPOLOGY_WORDS = listOf(
        "MBTI", "Myers", "myers", "九型", "大五", "16型", "16 型",
        "INFP", "ENFP", "INTJ", "ENTJ", "INTP", "ENTP", "ISFJ", "ESFJ",
        "ISTJ", "ESTJ", "ISFP", "ESFP", "ISTP", "ESTP", "INFJ", "ENFJ",
        "E人", "I人", "P人", "J人",
    )

    /** 生成调用的消息组。[factsText] 是**槽位事实句**（渲染正文，无头无尾无叙事）。 */
    fun buildMessages(factsText: String): List<LlmMessage> = listOf(
        LlmMessage(
            role = "system",
            content = """
                你是画像叙事撰写器。根据给定的「数据印象」写一段 3~4 句的中文描述，规则：
                1. **只能复述给定印象里已有的事实**，一个新的事实、一个新的数字都不许加；
                2. 只说「你怎么听音乐」，**禁止推断性格、情绪、处境、人格类型**；
                3. 不给用户起任何称号、代号或类型名；
                4. 用第二人称「你」，口吻自然、具体、可追问，不用排比和感叹号；
                5. 全文不超过 $MAX_CHARS 字；只输出正文，不要标题、不要解释。
            """.trimIndent(),
        ),
        LlmMessage(role = "user", content = factsText),
    )

    /**
     * 写入前的最后一道闸：长度、单段、禁词。通过返回清洗后的文本，否则返回 null
     * （调用方保留上一份叙事 —— **宁可不更新，不可写入越界文本**）。
     */
    fun validate(raw: String): String? {
        val cleaned = raw.trim().lines().filter { it.isNotBlank() }.joinToString(" ").trim()
        if (cleaned.length < MIN_CHARS || cleaned.length > MAX_CHARS) return null
        val forbidden = TYPOLOGY_WORDS + PersonalityCardComposer.allNames
        if (forbidden.any { cleaned.contains(it) }) return null
        return cleaned
    }

    /**
     * 侧写内容的**指纹**：任一槽位增删改值都会变化，是叙事"过期没有"的唯一判据。
     * 只做缓存失效用（非安全哈希），确定性来自"槽位按类型声明序排列 + 类型按 ordinal 排序"。
     */
    fun factsFingerprint(portraits: List<PortraitDraft>): Int = portraits
        .sortedBy { it.type.ordinal }
        .joinToString("|") { p ->
            p.type.id + ":" + p.slots.entries.joinToString(",") { "${it.key}=${it.value}" }
        }.hashCode()
}

/** 落库叙事的现状快照（[UserMemory.narrativeState] 返回值）。 */
data class ProfileNarrativeState(
    val text: String,
    /** 生成时侧写的指纹 —— 与当前 [factsFingerprint] 不同即过期 */
    val factsHash: Int,
    val generatedAt: Long,
)
