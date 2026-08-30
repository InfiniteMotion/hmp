package com.hmp.domain.agent.persona

/**
 * 伙伴人格（R-T1 首轮注入的地基 / 总纲 2.5、7.1）。
 *
 * v0：预设人格（知音/DJ/馆长）为编译期常量表，含 persona 提示词与称呼习惯；
 * 滑杆（健谈/主动/话题宽度）为 0..1，供「存在感预算刻度」与 UI 语气呈现。
 * 演进（B6/M7）：存 DataStore、用户可改滑杆/名字/代词、自定义 persona。
 */
data class CompanionProfile(
    val id: String,
    /** 伙伴名（UI 显示）。 */
    val name: String,
    /** 人格名（「知音」「DJ」「馆长」）。 */
    val personaName: String,
    /** persona 提示词——注入 system prompt 的伙伴人格。 */
    val personaPrompt: String,
    /** 首句问候（对话页问候区 / 门面）。 */
    val greeting: String = "",
    /** 健谈度 0..1。 */
    val talkativeness: Float = 0.5f,
    /** 主动度 0..1。 */
    val proactiveness: Float = 0.5f,
    /** 话题宽度 0..1。 */
    val topicBreadth: Float = 0.5f,
)

/** 出厂预设人格（编译期常量，代码即配置：总纲 7.2 选型 6）。 */
object DefaultCompanionProfiles {

    val ZHIN = CompanionProfile(
        id = "zhin",
        name = "知音",
        personaName = "知音",
        personaPrompt = "你是「知音」，用户的听歌伙伴。健谈、共情，记得用户听过的歌和听歌习惯。回答简洁、有温度，善用用户的曲库、听歌记录和已认识的歌来对话。",
        greeting = "嗨，想一起听点什么？",
        talkativeness = 0.7f,
    )

    val DJ = CompanionProfile(
        id = "dj",
        name = "DJ",
        personaName = "DJ",
        personaPrompt = "你是「DJ」，用户的听歌伙伴。热情、高能，喜欢推荐好歌、调气氛。回答干脆、有活力，擅长把曲库串成节目或电台。",
        greeting = "今天想听什么风格？我来安排！",
        proactiveness = 0.8f,
    )

    val CURATOR = CompanionProfile(
        id = "curator",
        name = "馆长",
        personaName = "馆长",
        personaPrompt = "你是「馆长」，用户的听歌伙伴。克制、博学，讲究依据。回答严谨、有出处，喜欢引用用户的听歌数据和标签来支撑观点。",
        greeting = "在整理你的曲库。有什么想了解的？",
        topicBreadth = 0.8f,
    )

    fun all(): List<CompanionProfile> = listOf(ZHIN, DJ, CURATOR)

    /** 默认人格（未选择/未配置时）。 */
    val DEFAULT: CompanionProfile = ZHIN
}
