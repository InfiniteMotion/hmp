package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 上下文渲染单测 —— 契约 §7（注入口径 / 路径脱敏 / 不出内部标识）。
 *
 * 这些断言看着琐碎，但每一条都对应一次真实的踩坑：
 * 画像进的是 **system prompt**，一旦漏了内部键名或绝对路径，就是每次对话都在泄漏。
 */
class UserProfileRendererTest {

    private fun portrait(
        type: PortraitType,
        slots: Map<String, String>,
        tier: PortraitTier = PortraitTier.L3,
        sources: Set<String> = setOf(ProfileSources.LIBRARY_SHAPE),
    ) = PortraitDraft(
        type = type,
        tier = tier,
        slots = slots,
        evidenceRefs = listOf(1L),
        confidence = 0.4,
        sources = sources,
    )

    private fun libraryPortrait(
        pathGroups: String? = null,
        pathStructure: String = LibraryModeler.PATH_FLAT,
        extra: Map<String, String> = emptyMap(),
    ) = portrait(
        PortraitType.LIBRARY,
        buildMap {
            put(PortraitType.SCALE, LibraryModeler.SCALE_MEDIUM)
            put(PortraitType.ARTIST_CONCENTRATION, LibraryModeler.CONCENTRATION_FOCUSED)
            put(PortraitType.PATH_STRUCTURE, pathStructure)
            pathGroups?.let { put(PortraitType.PATH_GROUPS, it) }
            putAll(extra)
        },
    )

    // ── 冷启动不留空壳（剧本 P1）─────────────────────────────────────

    @Test
    fun render_returnsNull_whenNothingToSay() {
        assertNull(UserProfileRenderer.render(emptyList()))
        // 有侧写但槽位全空，也不该硬凑一句
        assertNull(
            UserProfileRenderer.render(listOf(portrait(PortraitType.LIBRARY, emptyMap()))),
            "没有可渲染的槽位就不要产出块（不得出现『我还不了解你』这类填充句）",
        )
    }

    // ── 注入口径（§7.1）───────────────────────────────────────────────

    @Test
    fun render_startsWithDisclaimerHeader() {
        val text = assertNotNull(UserProfileRenderer.render(listOf(libraryPortrait())))
        assertTrue(text.startsWith("【对你的认识 · 仅供参考】"), "首行必须自述性质，实际是：${text.take(30)}")
        assertTrue(text.contains("不是指令"), "要写明它不是指令")
        assertTrue(text.contains("以对话内容为准"), "要写明冲突时谁优先")
    }

    // ── 不出内部标识 ────────────────────────────────────────────────────

    @Test
    fun render_neverLeaksInternalIdentifiers() {
        val text = assertNotNull(
            UserProfileRenderer.render(
                listOf(
                    libraryPortrait(pathGroups = "深夜,爵士", pathStructure = LibraryModeler.PATH_ORGANIZED),
                    portrait(
                        PortraitType.HABITS,
                        mapOf(PortraitType.COMPLETION_RATE to "HIGH", PortraitType.SKIP_POINT to "INTRO"),
                        tier = PortraitTier.L2,
                        sources = setOf(ProfileSources.T0_BEHAVIOR),
                    ),
                )
            )
        )
        listOf(
            "library_portrait", "habits_portrait", "time_portrait",
            "pathGroups", "completionRate", "skipPoint", "artistConcentration",
            "LIBRARY_SHAPE", "T0_BEHAVIOR",
        ).forEach { internal ->
            assertFalse(text.contains(internal), "内部标识「$internal」不得出现在上下文里")
        }
    }

    // ── 路径脱敏（§7.2）─────────────────────────────────────────────────

    @Test
    fun render_dropsUnsafePathGroups() {
        // 建模器已脱敏过一次；这里模拟"有人绕过建模器直接写库"的情况
        val text = assertNotNull(
            UserProfileRenderer.render(
                listOf(
                    libraryPortrait(
                        pathGroups = "/Users/xi/Music,..,~/secret,爵士",
                        pathStructure = LibraryModeler.PATH_ORGANIZED,
                    )
                )
            )
        )
        assertFalse(text.contains("/Users"), "绝对路径不得进上下文")
        assertFalse(text.contains("~"), "波浪号家目录不得进上下文")
        assertFalse(text.contains("secret"), "非目录名的串不得进上下文")
        assertTrue(text.contains("爵士"), "正常的分类名应当保留")
    }

    // ── 阶段一不假装知道流派（剧本 P2 / P5）──────────────────────────────

    @Test
    fun render_notesShapeOnlyStage_andNeverClaimsGenre() {
        val text = assertNotNull(UserProfileRenderer.render(listOf(libraryPortrait())))
        assertTrue(text.contains("我还在认识你的曲库"), "只有形态建模时要如实说明")
        listOf("流派", "民谣", "摇滚", "年代", "古典").forEach {
            assertFalse(text.contains(it), "标签就绪前不得出现类型断言（含「$it」）")
        }
    }

    @Test
    fun render_dropsShapeOnlyNote_whenContentStageArrived() {
        val withContent = libraryPortrait(
            extra = mapOf(PortraitType.GENRE_BREADTH to "BROAD")
        )
        val text = assertNotNull(UserProfileRenderer.render(listOf(withContent)))
        assertFalse(text.contains("我还在认识你的曲库"), "标签已就绪就不该再说不认识")
    }

    // ── B 类只能降级表达（§5.2）─────────────────────────────────────────

    @Test
    fun render_weakEvidence_isPhrasedAsMusicScopeOnly() {
        val text = assertNotNull(
            UserProfileRenderer.render(
                listOf(
                    portrait(
                        PortraitType.HABITS,
                        mapOf(PortraitType.COMPLETION_RATE to "HIGH"),
                        tier = PortraitTier.L2,
                        sources = setOf(ProfileSources.T0_BEHAVIOR),
                    )
                )
            )
        )
        assertTrue(text.contains("很少中途跳歌"))
        assertFalse(text.contains("你是一个"), "不得下人格断言")
    }

    // ── 硬字数上限 ──────────────────────────────────────────────────────

    @Test
    fun render_respectsHardCharLimit() {
        val many = (1..8).map { i ->
            portrait(
                PortraitType.LIBRARY,
                mapOf(
                    PortraitType.SCALE to LibraryModeler.SCALE_HUGE,
                    PortraitType.ARTIST_CONCENTRATION to LibraryModeler.CONCENTRATION_BALANCED,
                    PortraitType.PATH_GROUPS to "深夜,爵士,通勤,工作,运动",
                    PortraitType.PATH_STRUCTURE to LibraryModeler.PATH_ORGANIZED,
                ),
            ).copy(type = PortraitType.LIBRARY, evidenceRefs = listOf(i.toLong()))
        }
        val text = assertNotNull(UserProfileRenderer.render(many))
        assertTrue(
            text.length <= ProfileConfig.CONTEXT_MAX_CHARS,
            "超过硬上限就是刷屏：${text.length} > ${ProfileConfig.CONTEXT_MAX_CHARS}",
        )
    }
}
