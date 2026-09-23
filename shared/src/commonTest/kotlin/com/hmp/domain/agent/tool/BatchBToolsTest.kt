package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.tool.spec.ToolParamError
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeAgentPlaylistRepository
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batch B 工具切片测试（`AgentToolsTest` 未覆盖的 10 个工具的行为面）：
 * 聚合查询（artists/albums/tags）、按歌手/专辑/标签取歌、队列追加入队、配额占位、用户标签写删。
 *
 * 调用统一走 `tool.execute(JsonObject)`——参数校验 / clamp / 错误抛出都在该入口内，
 * 与生产链路（ToolCallExecutor → execute）一致。
 */
class BatchBToolsTest {

    /** 记录型播放端口：入队指令留痕 + 可脚本化应答。 */
    private class RecordingPlaybackPort : PlaybackCommandPort {
        val commands = mutableListOf<PlaybackCommand>()
        var respond: (PlaybackCommand) -> Pair<Boolean, String> = { true to "ok" }

        override suspend fun execute(command: PlaybackCommand, source: CommandSource): Pair<Boolean, String> {
            commands += command
            return respond(command)
        }
    }

    private class Fixture {
        val repo = FakeAgentMusicRepository()
        val port = RecordingPlaybackPort()
        val deps = ToolDependencies(
            musicRepository = repo,
            playlistRepository = FakeAgentPlaylistRepository(),
            settingsRepository = FakeSettingsRepository(),
            nowPlayingContextProvider = FakeNowPlayingContextProvider,
            playbackCommandPort = port,
        )

        fun seed(vararg infos: MusicInfo) {
            infos.forEach { repo.songs[it.music.id] = it }
        }
    }

    private fun song(id: Long, title: String, artist: String, album: String = "Album$id") =
        MusicInfo(Music(id, title, artist, album, 180_000, "/$id.mp3", ""), null, null)

    private fun jsonArgs(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value)
                else -> error("不支持的测试参数类型: $value")
            }
        }
    }

    // ───────────── library_artists / library_albums（聚合概览） ─────────────

    @Test
    fun libraryArtists_listsArtistsWithCounts() = runTest {
        val fx = Fixture().apply {
            seed(song(1, "A", "甲"), song(2, "B", "甲"), song(3, "C", "乙"))
        }

        val result = LibraryArtistsTool(fx.deps).execute(jsonArgs())

        assertTrue(result.success)
        assertTrue("甲 × 2" in result.summary, "按作品数降序聚合：${result.summary}")
        assertTrue("乙 × 1" in result.summary)
    }

    @Test
    fun libraryArtists_emptyLibrary_reportsEmpty() = runTest {
        val result = LibraryArtistsTool(Fixture().deps).execute(jsonArgs())

        assertTrue(result.success)
        assertTrue("曲库暂无歌手数据" in result.summary)
    }

    @Test
    fun libraryAlbums_listsAlbumsWithCounts() = runTest {
        val fx = Fixture().apply {
            seed(song(1, "A", "甲", album = "专辑一"), song(2, "B", "乙", album = "专辑一"), song(3, "C", "丙", album = "专辑二"))
        }

        val result = LibraryAlbumsTool(fx.deps).execute(jsonArgs())

        assertTrue(result.success)
        assertTrue("专辑一 × 2" in result.summary)
        assertTrue("专辑二 × 1" in result.summary)
    }

    @Test
    fun libraryAlbums_emptyLibrary_reportsEmpty() = runTest {
        val result = LibraryAlbumsTool(Fixture().deps).execute(jsonArgs())

        assertTrue(result.success)
        assertTrue("曲库暂无专辑数据" in result.summary)
    }

    // ───────────── library_songs_by_artist / by_album ─────────────

    @Test
    fun librarySongsByArtist_hitsAndEmpty() = runTest {
        val fx = Fixture().apply { seed(song(1, "晴天", "周杰伦"), song(2, "七里香", "周杰伦")) }
        val tool = LibrarySongsByArtistTool(fx.deps)

        val hit = tool.execute(jsonArgs("artist" to "周杰伦"))
        assertTrue(hit.success)
        assertTrue("共 2 首" in hit.summary)
        assertTrue("晴天" in hit.summary)

        val miss = tool.execute(jsonArgs("artist" to "查无此人"))
        assertTrue(miss.success, "空结果也是成功应答，不报错")
        assertTrue("曲库中暂无「查无此人」的歌曲" in miss.summary)
    }

    @Test
    fun librarySongsByAlbum_hitsAndEmpty() = runTest {
        val fx = Fixture().apply { seed(song(1, "晴天", "周杰伦", album = "叶惠美")) }
        val tool = LibrarySongsByAlbumTool(fx.deps)

        val hit = tool.execute(jsonArgs("album" to "叶惠美"))
        assertTrue(hit.success)
        assertTrue("晴天" in hit.summary)

        val miss = tool.execute(jsonArgs("album" to "不存在的专辑"))
        assertTrue(miss.success)
        assertTrue("曲库中暂无专辑「不存在的专辑」" in miss.summary)
    }

    // ───────────── library_tags / library_songs_by_tag ─────────────

    @Test
    fun libraryTags_allCategoryShowsThreeGroups() = runTest {
        val result = LibraryTagsTool(Fixture().deps).execute(jsonArgs())

        assertTrue(result.success)
        assertEquals(3, Regex("暂无").findAll(result.summary).count(), "缺省 all 应报风格/情绪/场景三类")
    }

    @Test
    fun libraryTags_genreCategoryShowsSingleGroup() = runTest {
        val result = LibraryTagsTool(Fixture().deps).execute(jsonArgs("category" to "genre"))

        assertTrue(result.success)
        assertEquals(1, Regex("暂无").findAll(result.summary).count(), "genre 只报风格一类")
    }

    @Test
    fun librarySongsByTag_chineseAliasResolvesToIds() = runTest {
        val fx = Fixture().apply {
            seed(song(1, "夜航", "甲"), song(2, "轰鸣", "乙"))
            repo.musicIdsByLabel[LabelName.ROCK] = listOf(1L, 2L)
        }

        val result = LibrarySongsByTagTool(fx.deps).execute(jsonArgs("tag_name" to "摇滚"))

        assertTrue(result.success)
        assertTrue("夜航" in result.summary && "轰鸣" in result.summary, "中文别名 → ROCK → 曲目")
    }

    @Test
    fun librarySongsByTag_unknownTagFails() = runTest {
        val fx = Fixture()

        val result = LibrarySongsByTagTool(fx.deps).execute(jsonArgs("tag_name" to "量子纠缠"))

        assertFalse(result.success, "闭集外标签明确失败，不静默空返回")
        assertTrue("未识别标签" in result.summary)
    }

    // ───────────── playback_enqueue ─────────────

    @Test
    fun playbackEnqueue_appendsViaPort() = runTest {
        val fx = Fixture()

        val result = PlaybackEnqueueTool(fx.deps).execute(jsonArgs("music_id" to 7L))

        assertTrue(result.success)
        val cmd = fx.port.commands.single()
        assertTrue(cmd is PlaybackCommand.ADD_TO_QUEUE, "只追加，不切换当前播放")
        assertEquals(7L, (cmd as PlaybackCommand.ADD_TO_QUEUE).musicId)
    }

    @Test
    fun playbackEnqueue_portFailure_propagatesReason() = runTest {
        val fx = Fixture()
        fx.port.respond = { false to "播放器忙" }

        val result = PlaybackEnqueueTool(fx.deps).execute(jsonArgs("music_id" to 7L))

        assertFalse(result.success)
        assertTrue("播放器忙" in result.summary, "端口失败原因回传给 LLM")
    }

    @Test
    fun playbackEnqueue_nonPositiveMusicId_throwsParamError() = runTest {
        val fx = Fixture()

        assertFailsWith<ToolParamError>("music_id min=1 应在参数层拒绝") {
            PlaybackEnqueueTool(fx.deps).execute(jsonArgs("music_id" to 0L))
        }
    }

    // ───────────── agent_budget ─────────────

    @Test
    fun agentBudget_reportsPlaceholderSnapshot() = runTest {
        val result = AgentBudgetTool(Fixture().deps).execute(jsonArgs())

        assertTrue(result.success)
        assertTrue("Agent 配额" in result.summary, "占位实现也要给出可读快照")
    }

    // ───────────── song_tag_user_add / song_tag_user_remove ─────────────

    @Test
    fun songTagUserAdd_writesUserLabel() = runTest {
        val fx = Fixture().apply { seed(song(1, "晴天", "周杰伦")) }

        val result = SongTagUserAddTool(fx.deps).execute(
            jsonArgs("music_id" to 1L, "tag_name" to "爵士"),
        )

        assertTrue(result.success)
        assertTrue("晴天" in result.summary, "成功文案点名歌曲")
        assertTrue("爵士" in result.summary, "标签以中文别名回显")
    }

    @Test
    fun songTagUserAdd_unknownTagFails() = runTest {
        val fx = Fixture().apply { seed(song(1, "晴天", "周杰伦")) }

        val result = SongTagUserAddTool(fx.deps).execute(
            jsonArgs("music_id" to 1L, "tag_name" to "量子纠缠"),
        )

        assertFalse(result.success, "闭集外标签写不进去，明确失败")
        assertTrue("未识别标签" in result.summary)
    }

    @Test
    fun songTagUserAdd_missingSongFails() = runTest {
        val result = SongTagUserAddTool(Fixture().deps).execute(
            jsonArgs("music_id" to 999L, "tag_name" to "爵士"),
        )

        assertFalse(result.success)
        assertTrue("歌曲 999 不存在" in result.summary)
    }

    @Test
    fun songTagUserRemove_removesUserLabel() = runTest {
        val fx = Fixture().apply { seed(song(1, "晴天", "周杰伦")) }

        val result = SongTagUserRemoveTool(fx.deps).execute(
            jsonArgs("music_id" to 1L, "tag_name" to "爵士"),
        )

        assertTrue(result.success)
        assertTrue("晴天" in result.summary)
    }

    @Test
    fun songTagUserRemove_missingSongFails() = runTest {
        val result = SongTagUserRemoveTool(Fixture().deps).execute(
            jsonArgs("music_id" to 999L, "tag_name" to "爵士"),
        )

        assertFalse(result.success)
        assertTrue("歌曲 999 不存在" in result.summary)
    }
}
