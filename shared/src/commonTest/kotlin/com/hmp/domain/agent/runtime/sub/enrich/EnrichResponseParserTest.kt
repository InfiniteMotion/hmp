package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 测试用曲目构造。 */
private fun song(id: Long, title: String = "曲目$id") =
    MusicInfo(Music(id, title, "歌手$id", "专辑", 180_000, "/$id.mp3", ""), null, null)

/** 测试用富化结果构造（全字段可注入，默认值是"空富化"形态）。 */
private fun draft(
    genre: List<String> = emptyList(),
    mood: List<String> = emptyList(),
    scenario: List<String> = emptyList(),
    language: String = "UNKNOWN",
    era: String = "UNKNOWN",
    rewards: String = "",
    lyric: String = "",
    singerIntroduce: String = "",
    backgroundIntroduce: String = "",
    description: String = "",
    relevantMusic: String = "",
) = EnrichDraft(
    genre = genre, mood = mood, scenario = scenario, language = language, era = era,
    rewards = rewards, lyric = lyric, singerIntroduce = singerIntroduce,
    backgroundIntroduce = backgroundIntroduce, description = description,
    relevantMusic = relevantMusic,
)

/**
 * [EnrichResponseParser] 的解析契约测试。
 *
 * 这层代码的存在意义就是**吃畸形输入**：模型偶尔少回一条、多回一条、忘了数组包装、
 * 把"没有"写成 `-暂无`。原来它埋在 1179 行的 `EnrichSubAgent` 里，无从单独验证；
 * F13-3c 把它抽成无状态 `object` 之后，才第一次可以喂样例文本进来。
 */
class EnrichResponseParserTest {

    private val agentId = "test"

    // ═══ Round 1：元素 ↔ 歌曲按**顺序**映射 ═══

    @Test
    fun parseEnumBatch_mapsElementsToSongsByOrder() {
        val songs = listOf(song(1), song(2))
        val text = """[{"genre":["摇滚"],"mood":["躁"],"scenario":["开车"],"language":"华语","era":"90s"},""" +
            """{"genre":["爵士"],"mood":["放松"],"scenario":["深夜"],"language":"英语","era":"60s"}]"""
        val r = EnrichResponseParser.parseEnumBatch(text, songs, agentId)
        assertEquals(setOf(1L, 2L), r.keys)
        assertEquals(listOf("摇滚"), r.getValue(1L).genre)
        assertEquals("英语", r.getValue(2L).language)
    }

    @Test
    fun parseEnumBatch_truncatesExtraElements() {
        // 模型多回一条时，多余元素必须丢弃 —— 否则会挂到别的歌上（错位比丢失更糟）
        val songs = listOf(song(1))
        val text = """[{"genre":["A"],"mood":[],"scenario":[]},{"genre":["B"],"mood":[],"scenario":[]}]"""
        val r = EnrichResponseParser.parseEnumBatch(text, songs, agentId)
        assertEquals(1, r.size)
        assertEquals(listOf("A"), r.getValue(1L).genre)
    }

    @Test
    fun parseEnumBatch_partialResultWhenFewerElementsThanSongs() {
        val songs = listOf(song(1), song(2), song(3))
        val text = """[{"genre":["A"],"mood":[],"scenario":[]},{"genre":["B"],"mood":[],"scenario":[]}]"""
        val r = EnrichResponseParser.parseEnumBatch(text, songs, agentId)
        assertEquals(setOf(1L, 2L), r.keys)  // 第 3 首没有结果，交给上层重试
    }

    @Test
    fun parseEnumBatch_skipsMalformedElementAndKeepsTheRest() {
        // 第 1 条缺必填 genre ⇒ 反序列化失败，但**不能连带丢掉后面的**
        val songs = listOf(song(1), song(2))
        val text = """[{"mood":[],"scenario":[]},{"genre":["爵士"],"mood":[],"scenario":[]}]"""
        val r = EnrichResponseParser.parseEnumBatch(text, songs, agentId)
        assertEquals(setOf(2L), r.keys)
    }

    @Test
    fun parseEnumBatch_unwrapsMarkdownFenceWithProse() {
        val songs = listOf(song(1))
        val text = """
            好的，这是结果：
            ```json
            [{"genre":["摇滚"],"mood":[],"scenario":[]}]
            ```
            希望对你有帮助
        """.trimIndent()
        val r = EnrichResponseParser.parseEnumBatch(text, songs, agentId)
        assertEquals(listOf("摇滚"), r.getValue(1L).genre)
    }

    @Test
    fun parseEnumBatch_acceptsBareObjectWithoutArrayWrapper() {
        // 忘了数组包装是模型输出的常见形态，单对象也要能读出来。
        // ⚠️ 这条曾长期失效：判定只看 `indexOf('[') >= 0`，而 round 1 的三个字段都是数组，
        // 裸对象里必然含 `[` ⇒ 被误判成"有数组包装" → 切出 0 个对象、兜底分支永不执行。
        val songs = listOf(song(1))
        val r = EnrichResponseParser.parseEnumBatch(
            """{"genre":["民谣"],"mood":[],"scenario":[]}""", songs, agentId,
        )
        assertEquals(listOf("民谣"), r.getValue(1L).genre)
    }

    @Test
    fun parseEnumBatch_acceptsObjectSequenceWithoutArrayWrapper() {
        // 另一种漏包装形态：`{...},{...}` —— 无方括号，但确实是多个对象
        val songs = listOf(song(1), song(2))
        val text = """{"genre":["民谣"],"mood":[],"scenario":[]},""" +
            """{"genre":["摇滚"],"mood":[],"scenario":[]}"""
        val r = EnrichResponseParser.parseEnumBatch(text, songs, agentId)
        assertEquals(setOf(1L, 2L), r.keys)
        assertEquals(listOf("摇滚"), r.getValue(2L).genre)
    }

    // ═══ Round 1.5：全量 replace 只在 patch 非空时覆盖 ═══

    @Test
    fun applyEnumReplacements_overwritesOnlyNonEmptyPatchFields() {
        // 枚举自检只该修正"有问题的字段"，patch 里空着的位置必须保留原值
        val songs = listOf(song(1))
        val original = mutableMapOf(
            1L to EnumOnlyResult(
                genre = listOf("摇滚"), mood = listOf("躁"), scenario = listOf("开车"),
                language = "华语", era = "90s",
            ),
        )
        val patch = mapOf(
            1L to EnumOnlyResult(
                genre = listOf("后摇"), mood = emptyList(), scenario = emptyList(),
                language = "", era = "00s",
            ),
        )
        EnrichResponseParser.applyEnumReplacements(original, patch, songs)

        val merged = original.getValue(1L)
        assertEquals(listOf("后摇"), merged.genre)   // patch 给了 → 覆盖
        assertEquals(listOf("躁"), merged.mood)      // patch 空 → 保留
        assertEquals(listOf("开车"), merged.scenario)
        assertEquals("华语", merged.language)
        assertEquals("00s", merged.era)
    }

    @Test
    fun applyEnumReplacements_ignoresPatchForUnknownSong() {
        val songs = listOf(song(1))
        val original = mutableMapOf(1L to EnumOnlyResult(listOf("摇滚"), listOf("躁"), listOf("开车")))
        val patch = mapOf(99L to EnumOnlyResult(listOf("后摇"), emptyList(), emptyList()))
        EnrichResponseParser.applyEnumReplacements(original, patch, songs)
        assertEquals(listOf("摇滚"), original.getValue(1L).genre)
        assertEquals(1, original.size)
    }

    // ═══ mergeAll：缺轮次时的默认值与事实字段归一化 ═══

    @Test
    fun mergeAll_fallsBackToDefaultsWhenAllRoundsMissing() {
        val info = EnrichResponseParser.mergeAll(null, null, null)
        assertEquals(emptyList(), info.genre)
        assertEquals("UNKNOWN", info.language)
        assertEquals("UNKNOWN", info.era)
        assertEquals("", info.description)
        assertEquals("", info.rewards)
        assertEquals("", info.lyric)
    }

    @Test
    fun mergeAll_blankLanguageAndEraBecomeUnknown() {
        val info = EnrichResponseParser.mergeAll(
            EnumOnlyResult(listOf("摇滚"), emptyList(), emptyList(), language = "  ", era = ""),
            null,
            null,
        )
        assertEquals("UNKNOWN", info.language)
        assertEquals("UNKNOWN", info.era)
    }

    @Test
    fun mergeAll_normalizesEmptyFactMarkers() {
        // 模型用 "-暂无"/"NONE"/"null" 表示"没有"是常态 —— 不归一化就会把它当正文写进库里
        val info = EnrichResponseParser.mergeAll(
            null,
            null,
            FreeTextFactsResult(
                rewards = "-暂无",
                lyric = "NONE",
                backgroundIntroduce = "null",
                relevantMusic = "  一段真实的背景  ",
            ),
        )
        assertEquals("", info.rewards)
        assertEquals("", info.lyric)
        assertEquals("", info.backgroundIntroduce)
        assertEquals("一段真实的背景", info.relevantMusic)
    }

    // ═══ Round 3：patch 索引规整与越界丢弃 ═══

    @Test
    fun parseReflectionPatches_normalizesOneBasedIndexToZeroBased() {
        // 模型按 prompt 里的 1-based 序号回，这里必须规整成 0-based
        val songs = listOf(song(1), song(2))
        val patches = EnrichResponseParser.parseReflectionPatches(
            """[{"index":2,"field":"lyric","fix":"新歌词"}]""", songs, agentId,
        )
        assertEquals(1, patches.size)
        assertEquals(1, patches.first().index)
    }

    @Test
    fun parseReflectionPatches_dropsOutOfRangeIndex() {
        val songs = listOf(song(1))
        val patches = EnrichResponseParser.parseReflectionPatches(
            """[{"index":5,"field":"lyric","fix":"x"}]""", songs, agentId,
        )
        assertTrue(patches.isEmpty())
    }

    @Test
    fun parseReflectionPatches_skipsMalformedElement() {
        val songs = listOf(song(1))
        val patches = EnrichResponseParser.parseReflectionPatches(
            """[{"index":1,"field":"lyric","fix":"好的"},{"nonsense":true}]""", songs, agentId,
        )
        assertEquals(1, patches.size)
        assertEquals("好的", patches.first().fix)
    }

    // ═══ applyPatch：字段分支、空值标记、大小写 ═══

    @Test
    fun applyPatch_splitsCommaSeparatedListFields() {
        val patched = EnrichResponseParser.applyPatch(
            draft(),
            ReflectionPatch(index = 0, field = "genre", fix = "摇滚, 后摇 ,"),
            agentId,
        )
        assertEquals(listOf("摇滚", "后摇"), patched.genre)
    }

    @Test
    fun applyPatch_emptyMarkerClearsField() {
        // 字符串字段 → 空串
        val clearedText = EnrichResponseParser.applyPatch(
            draft(lyric = "旧歌词"),
            ReflectionPatch(index = 0, field = "lyric", fix = "-暂无"),
            agentId,
        )
        assertEquals("", clearedText.lyric)
        // 列表字段 → 空列表（注意 "unknown" 也在空值标记表里）
        val clearedList = EnrichResponseParser.applyPatch(
            draft(genre = listOf("摇滚")),
            ReflectionPatch(index = 0, field = "genre", fix = "unknown"),
            agentId,
        )
        assertEquals(emptyList(), clearedList.genre)
    }

    @Test
    fun applyPatch_fieldNameIsCaseInsensitive() {
        // prompt 里字段名全小写，但模型爱写 "LYRIC" / "Genre"
        val patched = EnrichResponseParser.applyPatch(
            draft(),
            ReflectionPatch(index = 0, field = "LYRIC", fix = "新词"),
            agentId,
        )
        assertEquals("新词", patched.lyric)
    }

    @Test
    fun applyPatch_unknownFieldLeavesRecordUntouched() {
        val original = draft(genre = listOf("摇滚"))
        val patched = EnrichResponseParser.applyPatch(
            original,
            ReflectionPatch(index = 0, field = "unknownField", fix = "x"),
            agentId,
        )
        assertEquals(original, patched)
    }

    @Test
    fun applyReflectionPatches_resolvesSongByIndexAndSkipsMissingDraft() {
        val songs = listOf(song(1), song(2))
        val drafts = mutableMapOf(1L to draft(lyric = "旧"))
        EnrichResponseParser.applyReflectionPatches(
            drafts,
            listOf(
                ReflectionPatch(index = 0, field = "lyric", fix = "新"),
                ReflectionPatch(index = 1, field = "lyric", fix = "不该出现"),  // 第 2 首没有 draft
            ),
            songs,
        )
        assertEquals("新", drafts.getValue(1L).lyric)
        assertEquals(1, drafts.size)
    }
}
