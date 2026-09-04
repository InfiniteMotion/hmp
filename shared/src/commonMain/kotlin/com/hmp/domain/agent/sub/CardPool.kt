package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.data.database.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * HelloSubAgent 卡片池（W0）。
 *
 * 设计：
 * - 同类型只保留一张（replace 语义，原位置替换）
 * - push 到栈顶（列表头）
 * - 所有卡常驻，无自动 pop；可见性由 SlideCard.visible 控制
 * - replace 时引擎层决定要不要 setFocus=true → UI 聚焦展示并暂停轮播
 *
 * 线程安全：
 * - cards 通过 StateFlow.update {} 原子化
 */
class CardPool {
    private val _cards = MutableStateFlow<List<SlideCard>>(emptyList())
    val cards: StateFlow<List<SlideCard>> = _cards.asStateFlow()

    // ═══ 核心操作 ═══

    /**
     * 同类型只保留一张——在原位置替换（不是 push 到栈顶）。
     * 没找到同类型 → push 到栈顶（新卡）。
     *
     * setFocus=true 时新卡 focusedAt 设为当前时间 → UI 聚焦展示。
     * setFocus=false 时 focusedAt 保持 0（ANCHOR 每秒刷新用这个，不抢焦点）。
     *
     * 原位置语义：RECOMMEND 时段变化时内容变了但位置不变 → 轮播稳定。
     */
    fun replace(type: SlideType, card: SlideCard, setFocus: Boolean = true) {
        val focusedCard = card.copy(focusedAt = if (setFocus) currentTimeMillis() else 0L)
        val oldExist = _cards.value.any { it.type == type }
        if (oldExist) {
            _cards.update { it.map { c -> if (c.type == type) focusedCard else c } }
            Logger.d("Agent.Hello") {
                "CardPool replace in-place: type=$type id=${focusedCard.cardId} setFocus=$setFocus"
            }
        } else {
            _cards.update { listOf(focusedCard) + it }
            Logger.d("Agent.Hello") {
                "CardPool replace (new): type=$type id=${focusedCard.cardId} setFocus=$setFocus"
            }
        }
    }

    /** push 到栈顶（列表头） */
    fun push(card: SlideCard) {
        _cards.update { listOf(card) + it }
        Logger.d("Agent.Hello") { "CardPool push: type=${card.type} id=${card.cardId}" }
    }

    /** 按类型设置 visible（今日无数据时 setVisible(type, false) 即可，不用 pop） */
    fun setVisible(type: SlideType, visible: Boolean) {
        _cards.update { it.map { c -> if (c.type == type) c.copy(visible = visible) else c } }
        Logger.d("Agent.Hello") { "CardPool setVisible: type=$type visible=$visible" }
    }

    /** 按类型 pop（彻底从 CardPool 移除，很少用——大多用 setVisible(false)） */
    fun popByType(type: SlideType) {
        val removed = _cards.value.filter { it.type == type }
        _cards.update { it.filter { c -> c.type != type } }
        if (removed.isNotEmpty()) {
            Logger.d("Agent.Hello") { "CardPool popByType: type=$type removed=${removed.size}" }
        }
    }

    /** 某类型是否存在（缺失重试守卫用） */
    fun containsType(type: SlideType): Boolean = _cards.value.any { it.type == type && it.visible }

    /** 清空所有卡（shutdown 用） */
    fun clear() {
        _cards.value = emptyList()
    }
}
