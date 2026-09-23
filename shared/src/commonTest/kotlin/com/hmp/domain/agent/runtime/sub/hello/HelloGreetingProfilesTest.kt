package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.domain.agent.card.GreetingType
import com.hmp.domain.agent.card.TimePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HelloGreetingProfiles] 的画像表契约测试。
 *
 * 这张表的存在就是为了**把"漏改"从静默变成报错**（同一份信息原先散在四个平行分支里）。
 * 但 `when` 的穷举只保证"每个类型有画像"，不保证"画像内容完整、权重表不漏项"——
 * 本测试补上后一半。
 */
class HelloGreetingProfilesTest {

    @Test
    fun of_returnsCompleteProfileForEveryType() {
        for (type in GreetingType.entries) {
            val p = HelloGreetingProfiles.of(type)
            assertTrue(
                p.promptKey.startsWith("hello.greeting."),
                "$type 的 promptKey 不符合 hello.greeting.* 约定：${p.promptKey}",
            )
            assertTrue(p.systemFallback.isNotBlank(), "$type 缺 systemFallback")
            assertTrue(p.requirement.isNotBlank(), "$type 缺写作要求")
            assertTrue(
                p.fallbacks.size >= 5,
                "$type 的兜底池只有 ${p.fallbacks.size} 条，游标轮转会很快重复",
            )
            assertTrue(p.fallbacks.none { it.isBlank() }, "$type 的兜底池里有空串")
            assertTrue(p.temperature in 0f..2f, "$type 的温度 ${p.temperature} 超出接口范围")
        }
    }

    @Test
    fun of_promptKeysAreDistinct() {
        // 两个类型共用一个 key ⇒ 拿错画像（温度、文案池串味），且不会编译报错
        val keys = GreetingType.entries.map { HelloGreetingProfiles.of(it).promptKey }
        assertEquals(GreetingType.entries.size, keys.toSet().size, "有类型复用了 promptKey：$keys")
    }

    @Test
    fun temperature_encodesCreativeVsFactualTradeoff() {
        val byTemp = GreetingType.entries.associateWith { HelloGreetingProfiles.of(it).temperature }
        // QUOTE 要文学创造性 → 最高温；FACT 要事实准确 → 最低温。这是设计意图，不是巧合
        assertEquals(GreetingType.QUOTE, byTemp.maxByOrNull { it.value }?.key)
        assertEquals(GreetingType.FACT, byTemp.minByOrNull { it.value }?.key)
    }

    @Test
    fun nowPlayingAndAnniversaryPrefs_coverEveryType() {
        // 表是稀疏的（不列 = 0 分）⇒ 加类型不会编译报错。当前两张表都是全覆盖，
        // 把这一事实固化：若将来刻意让某类型在这两个信号上吃 0 分，请改这条断言以表明是有意的
        assertEquals(GreetingType.entries.toSet(), HelloGreetingProfiles.TYPE_PREFS_NOW_PLAYING.keys)
        assertEquals(GreetingType.entries.toSet(), HelloGreetingProfiles.TYPE_PREFS_ANNIV.keys)
        val weights = HelloGreetingProfiles.TYPE_PREFS_NOW_PLAYING.values +
            HelloGreetingProfiles.TYPE_PREFS_ANNIV.values
        weights.forEach { assertTrue(it in 0.0..1.0, "权重必须在 0..1（调用方乘信号强度）：$it") }
    }

    @Test
    fun phasePrefs_coverRealPhasesAndDeliberatelyOmitUnknown() {
        val covered = HelloGreetingProfiles.TYPE_PREFS_PHASE.keys
        assertEquals(
            TimePhase.entries.toSet() - setOf(TimePhase.UNKNOWN),
            covered,
            "时段偏好表应与 6 个真实时段一一对应",
        )
        // 刻意不给 UNKNOWN 偏好分：算不出时段时该信号不参与打分
        // （routeGreetingType 用 `TYPE_PREFS_PHASE[phase]?.let {}` 取值，缺 key 不会崩）。
        // 若哪天要给 UNKNOWN 加分，先确认打分语义，再连同这条断言一起改。
        assertFalse(TimePhase.UNKNOWN in covered, "给 UNKNOWN 加偏好前请先确认打分语义")

        HelloGreetingProfiles.TYPE_PREFS_PHASE.values.forEach { inner ->
            assertTrue(inner.isNotEmpty(), "空的时段内层表等于该时段没有任何信号")
            inner.forEach { (type, w) ->
                assertTrue(type in GreetingType.entries, "未知问候类型：$type")
                assertTrue(w in 0.0..1.0, "权重必须在 0..1：$type=$w")
            }
        }
    }
}
