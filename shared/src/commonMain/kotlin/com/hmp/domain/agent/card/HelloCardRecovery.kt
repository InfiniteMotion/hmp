package com.hmp.domain.agent.card

import com.hmp.data.database.HelloCardCache

/**
 * 可从缓存恢复的卡型。
 *
 * GREETING 每次启动重生成；ANCHOR / RADIO_STATUS / NARRATIVE 另有数据源，不走卡池缓存。
 */
private val RESTORABLE_TYPES = setOf(
    SlideType.RECOMMEND,
    SlideType.DISCOVER,
    SlideType.FORGOTTEN,
    SlideType.ANNIVERSARY,
)

/**
 * 缓存条目 → 卡片。
 *
 * 解析失败或类型不可恢复时返回 null —— 缓存是「尽力而为」的展示素材，不是事实源。
 * 2026-11-11：供 `HelloCardsViewModel` 在 Hello 未启动（没有可用 LLM）时直接读 DAO 使用。
 */
fun HelloCardCache.toSlideCard(): SlideCard? {
    val type = SlideType.entries.firstOrNull { it.name == cardType } ?: return null
    if (type !in RESTORABLE_TYPES) return null
    val content = HelloCardCodec.decode(type, cardContentJson) ?: return null
    return SlideCard(SlideCard.newId(), type, content)
}
