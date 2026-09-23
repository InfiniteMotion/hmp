package com.hmp.domain.agent.runtime.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Agent 运行时 i18n 测试：Lang 解析 + resolvePrompt 优先级 + 出厂词表完整性。
 */
class LangTest {

    // ───────────────────── Lang.fromCode ─────────────────────

    @Test
    fun langCodes_areStable() {
        assertEquals("zh", Lang.ZH.code)
        assertEquals("en", Lang.EN.code)
        assertEquals("auto", Lang.AUTO.code)
    }

    @Test
    fun fromCode_mapsMainVariants() {
        assertEquals(Lang.ZH, Lang.fromCode("zh"))
        assertEquals(Lang.ZH, Lang.fromCode("zh-cn"))
        assertEquals(Lang.ZH, Lang.fromCode("zh-tw"))
        assertEquals(Lang.EN, Lang.fromCode("en"))
        assertEquals(Lang.EN, Lang.fromCode("en-us"))
        assertEquals(Lang.EN, Lang.fromCode("en-gb"))
        assertEquals(Lang.AUTO, Lang.fromCode("auto"))
    }

    @Test
    fun fromCode_isCaseInsensitive() {
        assertEquals(Lang.ZH, Lang.fromCode("ZH"))
        assertEquals(Lang.EN, Lang.fromCode("EN-US"))
        assertEquals(Lang.AUTO, Lang.fromCode("Auto"))
    }

    @Test
    fun fromCode_unknownFallsBackToChinese() {
        assertEquals(Lang.ZH, Lang.fromCode("fr"), "未支持语言回落中文")
        assertEquals(Lang.ZH, Lang.fromCode(""), "空串回落中文")
    }

    // ───────────────────── resolvePrompt ─────────────────────

    @Test
    fun userOverride_beatsEverything() {
        assertEquals(
            "我的自定义提示",
            resolvePrompt("chat.system", "en", "zh", mapOf("chat.system" to "我的自定义提示")),
            "用户覆盖优先于语言与出厂默认",
        )
    }

    @Test
    fun blankOverride_isIgnored() {
        val withBlank = resolvePrompt("chat.system", "zh", "zh", mapOf("chat.system" to "   "))
        val without = resolvePrompt("chat.system", "zh", "zh", emptyMap())
        assertEquals(without, withBlank, "空白覆盖视同未覆盖，回落出厂默认")
    }

    @Test
    fun overrideForOtherKey_doesNotLeak() {
        val result = resolvePrompt("chat.system", "zh", "zh", mapOf("enrich.system" to "污染源"))
        assertEquals(
            resolvePrompt("chat.system", "zh", "zh", emptyMap()),
            result,
            "其他 key 的覆盖不得影响本 key",
        )
    }

    @Test
    fun preferredLang_selectsLanguage() {
        assertTrue(
            resolvePrompt("chat.persona.dj", "en", "zh", emptyMap()).contains("You are"),
            "en 偏好 → 英文出厂 prompt",
        )
        assertTrue(
            resolvePrompt("chat.persona.dj", "zh", "en", emptyMap()).contains("你是「DJ」"),
            "zh 偏好 → 中文出厂 prompt",
        )
    }

    @Test
    fun preferredLangGlobal_delegatesToGlobalReplyLanguage() {
        assertTrue(
            resolvePrompt("chat.persona.dj", "global", "en", emptyMap()).contains("You are"),
            "global + 全局 en → 英文",
        )
        assertTrue(
            resolvePrompt("chat.persona.dj", "global", "zh", emptyMap()).contains("你是「DJ」"),
            "global + 全局 zh → 中文",
        )
    }

    @Test
    fun unknownPreferredLang_fallsBackToChinese() {
        assertTrue(
            resolvePrompt("chat.persona.dj", "fr", "en", emptyMap()).contains("你是「DJ」"),
            "无法识别的偏好回落中文，而非落空",
        )
    }

    @Test
    fun auto_followsDetectedSystemLang() {
        val zh = resolvePrompt("chat.persona.dj", "zh", "zh", emptyMap())
        val en = resolvePrompt("chat.persona.dj", "en", "zh", emptyMap())
        val auto = resolvePrompt("chat.persona.dj", "auto", "zh", emptyMap())
        assertTrue(
            auto == zh || auto == en,
            "auto 由 detectSystemLang 判定，结果必为 ZH 或 EN 之一",
        )
    }

    @Test
    fun unknownKey_returnsEmptyString() {
        assertEquals("", resolvePrompt("no.such.key", "zh", "zh", emptyMap()), "未知 key 返回空串，不抛异常")
    }

    // ───────────────────── 出厂词表完整性 ─────────────────────

    @Test
    fun l10nCatalog_everyKeyHasBothLanguagesNonBlank() {
        assertTrue(L10N_PROMPTS.isNotEmpty(), "出厂词表不得为空")
        L10N_PROMPTS.forEach { (key, texts) ->
            val zh = texts[Lang.ZH]
            val en = texts[Lang.EN]
            assertNotNull(zh, "key=$key 缺中文出厂 prompt")
            assertNotNull(en, "key=$key 缺英文出厂 prompt")
            assertTrue(zh.isNotBlank(), "key=$key 中文 prompt 为空")
            assertTrue(en.isNotBlank(), "key=$key 英文 prompt 为空")
        }
    }

    @Test
    fun l10nCatalog_containsContractKeys() {
        listOf(
            "chat.system",
            "chat.persona.zhin", "chat.persona.dj", "chat.persona.curator",
            "radio.host",
            "enrich.system",
            "enrich.preheat", "enrich.enum", "enrich.enum_self_check",
            "enrich.free_text_easy", "enrich.free_text_facts", "enrich.reflection",
            "hello.greeting.quote", "hello.greeting.lyric_gold", "hello.greeting.fact",
            "hello.greeting.story", "hello.greeting.artist", "hello.greeting.listen",
            "hello.greeting.history",
            "hello.recommend.full", "hello.recommend.short", "hello.recommend.list",
            "hello.forgotten.essay", "hello.forgotten.card",
        ).forEach { key ->
            assertTrue(L10N_PROMPTS.containsKey(key), "缺少契约 key: $key")
        }
    }
}
