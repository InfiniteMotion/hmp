package com.hmp.domain.agent.tool

import com.hmp.domain.enum.LabelName
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.FakePlaybackCommandPort
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeAgentPlaylistRepository
import com.hmp.test.fakes.FakeAiExtraEnrichPort
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AgentToolsTest {
    private fun song(id: Long, title: String, artist: String, path: String = "/$id.mp3") =
        MusicInfo(Music(id, title, artist, "Album", 180_000, path, ""), null, null)

    private fun jsonArgs(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                is Long -> put(k, JsonPrimitive(v))
                is Int -> put(k, JsonPrimitive(v))
                is String -> put(k, JsonPrimitive(v))
                is List<*> -> put(k, kotlinx.serialization.json.buildJsonArray {
                    v.forEach { add(JsonPrimitive((it as Long).toString())) }
                })
            }
        }
    }

    private class Fixture {
        val musicRepo = FakeAgentMusicRepository()
        val playlistRepo = FakeAgentPlaylistRepository()
        val settingsRepo = com.hmp.test.fakes.FakeSettingsRepository()
        val enrich = FakeAiExtraEnrichPort()
        val deps = ToolDependencies(
            musicRepository = musicRepo,
            playlistRepository = playlistRepo,
            settingsRepository = settingsRepo,
            nowPlayingContextProvider = FakeNowPlayingContextProvider,
            playbackCommandPort = FakePlaybackCommandPort,
            enrichPort = enrich,
        )
        val registry = ToolRegistry.create(deps)
    }

    // ---------- searchLibrary ----------
    @Test
    fun searchLibrary_hitsAndMisses() = runTest {
        val fx = Fixture()
        fx.musicRepo.songs[1L] = song(1, "The Rock Anthem", "BB")
        fx.musicRepo.songs[2L] = song(2, "Slow Ballad", "CC")

        val hit = fx.registry.executeTool(ToolNames.LIBRARY_SEARCH, jsonArgs("query" to "rock"))
        assertTrue(hit.success)
        assertTrue(hit.summary.contains("Rock"), "应命中标题含 rock 曲目，实际: ${hit.summary}")
        assertTrue(hit.summary.contains("(id=1)"), "检索结果应带 music_id 供 play_by_id 使用，实际: ${hit.summary}")

        val miss = fx.registry.executeTool(ToolNames.LIBRARY_SEARCH, jsonArgs("query" to "不存在"))
        assertTrue(miss.success)
        assertTrue(miss.summary.contains("未在曲库中匹配到"), "空结果应返回未命中摘要而非报错，实际: ${miss.summary}")
    }

    @Test
    fun searchLibrary_matchesByLabelWithId() = runTest {
        val fx = Fixture()
        fx.musicRepo.songs[7L] = song(7, "Take Five", "Dave Brubeck")
        fx.musicRepo.musicIdsByLabel[LabelName.JAZZ] = listOf(7L)
        val r = fx.registry.executeTool(ToolNames.LIBRARY_SEARCH, jsonArgs("query" to "爵士"))
        assertTrue(r.success)
        assertTrue(r.summary.contains("Take Five"), "查询词命中标签应返回该类曲目，实际: ${r.summary}")
        assertTrue(r.summary.contains("(id=7)"), "带 id 供 play_by_id，实际: ${r.summary}")
    }

    // ---------- getListenStats ----------
    @Test
    fun getListenStats_summarizes() = runTest {
        val fx = Fixture()
        val r = fx.registry.executeTool(ToolNames.LIBRARY_STATS, jsonArgs())
        assertTrue(r.success)
        assertTrue(r.summary.contains("120"), "应含总播放数，实际: ${r.summary}")
        assertTrue(r.detail.orEmpty().contains("topGenres"), "detail 应含分类分布")
    }

    // ---------- getRecentHistory ----------
    @Test
    fun getRecentHistory_emptyAndPopulated() = runTest {
        val fx = Fixture()
        val empty = fx.registry.executeTool(ToolNames.LIBRARY_RECENT_HISTORY, jsonArgs())
        assertTrue(empty.success)
        assertTrue(empty.summary.contains("暂无播放记录"))

        fx.musicRepo.songs[1L] = song(1, "SongA", "ArtistA")
        fx.musicRepo.recentHistoryResult += PlaybackHistory(id = 1, musicId = 1, playedAt = 1000, playDuration = 60_000)
        val filled = fx.registry.executeTool(ToolNames.LIBRARY_RECENT_HISTORY, jsonArgs("limit" to 10))
        assertTrue(filled.success)
        assertTrue(filled.summary.contains("SongA"), "历史应带曲名，实际: ${filled.summary}")
    }

    // ---------- getNowPlayingContext ----------
    @Test
    fun getNowPlayingContext_noCurrent() = runTest {
        val fx = Fixture()
        val r = fx.registry.executeTool(ToolNames.PLAYBACK_STATE, jsonArgs())
        assertTrue(r.success)
        assertTrue(r.summary.contains("无播放曲目"))
    }

    // ---------- getSimilarSongs ----------
    @Test
    fun getSimilarSongs_missingAnchor_requiresMusicId() = runTest {
        val fx = Fixture()
        val r = fx.registry.executeTool(ToolNames.LIBRARY_SIMILAR, jsonArgs())
        assertTrue(!r.success, "无当前播放且未提供 musicId 应失败")
        assertNotNull(r.failureReason)
    }

    // ---------- getMusicExtra ----------
    @Test
    fun getMusicExtra_enrichedAndNotReady() = runTest {
        val fx = Fixture()
        fx.musicRepo.songs[1L] = song(1, "Title", "Artist")

        val ok = fx.registry.executeTool(ToolNames.SONG_TAGS_GET, jsonArgs("music_id" to 1L))
        assertTrue(ok.success)
        assertTrue(ok.summary.contains("风格"))

        // 未富化：errorInfo 非空 → 失败
        fx.musicRepo.dailyMusicExtra[1L] = DailyMusicInfo(
            genre = emptyList(), mood = emptyList(), scenario = emptyList(),
            language = "", era = "", rewards = "", lyric = "", singerIntroduce = "",
            backgroundIntroduce = "", description = "", relevantMusic = "", errorInfo = "NOT_ENOUGH_DATA",
        )
        val notReady = fx.registry.executeTool(ToolNames.SONG_TAGS_GET, jsonArgs("music_id" to 1L))
        assertFalse(notReady.success)
        assertNotNull(notReady.failureReason)
    }

    // ---------- enrichSong ----------
    @Test
    fun enrichSong_successAndFailure() = runTest {
        val fx = Fixture()
        val ok = fx.registry.executeTool(ToolNames.SONG_ENRICH_LLM, jsonArgs("title" to "Deep Focus", "artist" to "Ambient"))
        assertTrue(ok.success)
        assertTrue(ok.summary.contains("电子"), "成功应含风格标签")

        fx.enrich.result = Result.failure(IllegalStateException("云端超时"))
        val fail = fx.registry.executeTool(ToolNames.SONG_ENRICH_LLM, jsonArgs("title" to "Deep Focus"))
        assertFalse(fail.success)
        assertNotNull(fail.failureReason)
    }

    // ---------- createPlaylist ----------
    @Test
    fun createPlaylist_success() = runTest {
        val fx = Fixture()
        val r = fx.registry.executeTool(ToolNames.PLAYLIST_CREATE, jsonArgs("name" to "跑步歌单"))
        assertTrue(r.success)
        assertTrue(r.summary.contains("跑步歌单"))
        assertEquals(1, fx.playlistRepo.playlists.size)
    }

    // ---------- addToPlaylist ----------
    @Test
    fun addToPlaylist_success_playlistMissing_musicMissing() = runTest {
        val fx = Fixture()
        fx.musicRepo.songs[1L] = song(1, "Title", "Artist", "/1.mp3")
        val pid = fx.playlistRepo.createPlaylist("MyList")

        val ok = fx.registry.executeTool(ToolNames.PLAYLIST_ADD_SONG, jsonArgs("playlist_id" to pid, "music_id" to 1L))
        assertTrue(ok.success)
        assertEquals(listOf(1L), fx.playlistRepo.playlistItems[pid]!!.toList())

        // 歌单不存在
        val noList = fx.registry.executeTool(ToolNames.PLAYLIST_ADD_SONG, jsonArgs("playlist_id" to 999L, "music_id" to 1L))
        assertFalse(noList.success)
        // 歌曲不存在
        val noSong = fx.registry.executeTool(ToolNames.PLAYLIST_ADD_SONG, jsonArgs("playlist_id" to pid, "music_id" to 888L))
        assertFalse(noSong.success)
    }

    // ---------- reorderPlaylist / controlPlayback ----------
    @Test
    fun reorderPlaylist_success() = runTest {
        val fx = Fixture()
        val pid = fx.playlistRepo.createPlaylist("List")
        fx.playlistRepo.reorderPlaylistItems(pid, listOf(3L, 1L, 2L))
        val r = fx.registry.executeTool(
            ToolNames.PLAYLIST_REORDER,
            jsonArgs("playlist_id" to pid, "ordered_music_ids" to listOf(2L, 1L, 3L)),
        )
        assertTrue(r.success)
        assertEquals(listOf(2L, 1L, 3L), fx.playlistRepo.playlistItems[pid]!!.toList())
    }

    @Test
    fun controlPlayback_success_andBadEnum() = runTest {
        val fx = Fixture()
        val ok = fx.registry.executeTool(ToolNames.PLAYBACK_CONTROL, jsonArgs("command" to "next"))
        assertTrue(ok.success, "Fake port 恒成功，实际: ${ok.summary}")

        // 枚举越界 → registry 转失败结果
        val bad = fx.registry.executeTool(ToolNames.PLAYBACK_CONTROL, jsonArgs("command" to "fastforward"))
        assertFalse(bad.success)
        assertNotNull(bad.failureReason)
    }

    // 直接调用（不经 registry）：越界抛 ToolParamError
    @Test
    fun directToolParamError_thrown() = runTest {
        val fx = Fixture()
        assertFailsWith<ToolParamError> {
            fx.registry.find(ToolNames.PLAYBACK_CONTROL)!!.execute(jsonArgs("command" to "no_op"))
            Unit
        }
    }

    // ---------- registry 边界 ----------
    @Test
    fun registry_unknownTool_throws() = runTest {
        val fx = Fixture()
        assertFailsWith<ToolNotFoundException> {
            fx.registry.executeTool("no_such_tool", jsonArgs())
            Unit
        }
    }

    @Test
    fun registry_allNames_matchToolNamesConstant() = runTest {
        val fx = Fixture()
        assertEquals(ToolNames.ALL.toSet(), fx.registry.all().map { it.name }.toSet())
    }

    // ---------- 回填语义：每个工具成功 summary 必须非空（防幻觉型假成功） ----------
    @Test
    fun everyTool_successSummary_nonBlank() = runTest {
        val fx = Fixture()
        fx.musicRepo.songs[1L] = song(1, "Title", "Artist")
        fx.musicRepo.songs[2L] = song(2, "Title2", "Artist")
        // 对每个工具注入最简合法参数执行（成功或失败均可），但成功时必须非空摘要
        val cases: Map<String, JsonObject> = mapOf(
            ToolNames.LIBRARY_SEARCH to jsonArgs("query" to "Title"),
            ToolNames.LIBRARY_STATS to jsonArgs(),
            ToolNames.LIBRARY_RECENT_HISTORY to jsonArgs(),
            ToolNames.PLAYBACK_STATE to jsonArgs(),
            ToolNames.LIBRARY_SIMILAR to jsonArgs("music_id" to 1L),
            ToolNames.SONG_TAGS_GET to jsonArgs("music_id" to 1L),
            ToolNames.SONG_ENRICH_LLM to jsonArgs("title" to "Title"),
            ToolNames.PLAYLIST_CREATE to jsonArgs("name" to "T"),
            ToolNames.PLAYLIST_ADD_SONG to jsonArgs("playlist_id" to fx.playlistRepo.createPlaylist("L"), "music_id" to 1L),
            ToolNames.PLAYLIST_REORDER to jsonArgs("playlist_id" to 1L, "ordered_music_ids" to listOf(1L, 2L)),
            ToolNames.PLAYBACK_CONTROL to jsonArgs("command" to "play"),
        )
        for ((name, args) in cases) {
            val r = fx.registry.executeTool(name, args)
            if (r.success) {
                assertTrue(r.summary.isNotBlank(), "工具 $name 成功摘要不得为空（M3-T3 强制回填）")
            } else {
                assertNotNull(r.failureReason, "工具 $name 失败应带 failureReason（供审计）")
            }
        }
    }
}