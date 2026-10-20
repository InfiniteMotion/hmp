package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.RoomTransactionRunner
import com.hmp.data.database.UserInfo
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.domain.backup.MusicLabelSnapshot
import com.hmp.domain.backup.MusicUserStateSnapshot
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.test.db.createTestDatabase
import com.hmp.test.repo.InMemoryMusicRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicRepositoryBaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: InMemoryMusicRepository

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = InMemoryMusicRepository(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    /**
     * D3-08：把曲目从曲库"移除"（软删）之后，含它的歌单不能继续显示旧的 `songCount`。
     *
     * 详情页头部的"10 首 / 45 分钟"读的是 `playlist` 上的缓存列，而这个列原先只在
     * **条目**增删时重算；软删 `music.isDeleted` 根本不碰 `playlist_item`，
     * 于是标题与实际列表长期不一致（列表查询是带 `isDeleted = 0` 过滤的）。
     * 取消移除时反向也错，所以两条路径都要断言。
     */
    @Test
    fun softDeleteAndRestore_keepPlaylistDerivedColumnsInSync() = runTest {
        val pid = db.playlistDao().insert(com.hmp.data.database.Playlist(name = "P"))
        listOf(1L, 2L, 3L).forEach { id ->
            db.musicDao().insert(
                com.hmp.data.database.Music(
                    id = id, title = "S$id", artist = "A", album = "B",
                    duration = 60_000L, path = "/$id.mp3", albumArtUri = ""
                )
            )
            db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
            db.userInfoDao().insert(UserInfo(id = id))
            db.playlistItemDao().addSongAndRefresh(pid, id, 0L)
        }
        assertEquals(3, db.playlistDao().getPlaylistById(pid)!!.songCount, "基线：三首")

        repo.removeFromLibrary(listOf(2L))
        val afterRemove = db.playlistDao().getPlaylistById(pid)!!
        assertEquals(2, afterRemove.songCount, "软删一首后计数应跟降（标题与列表要一致）")
        assertEquals(120_000L, afterRemove.totalDurationMs)

        repo.restoreToLibrary(listOf(2L))
        assertEquals(3, db.playlistDao().getPlaylistById(pid)!!.songCount, "取消移除应把计数升回来")
    }

    @Test
    fun modelLabel_writeSetsSourceAndTimestamps() = runTest {
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))

        val entity = db.musicLabelDao().getLabelsById(1L).single()
        assertEquals(MusicRepositoryBase.SOURCE_LLM, entity.source)
        assertEquals(MusicRepositoryBase.DEFAULT_MODEL_CONFIDENCE, entity.confidence)
        assertTrue(entity.createdAt != null && entity.createdAt!! > 0, "createdAt 应写入")
        assertEquals(entity.createdAt, entity.updatedAt, "初次认识的 createdAt == updatedAt")
    }

    @Test
    fun userLabel_isNeverOverwrittenByModel() = runTest {
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))
        repo.addUserMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK), confidence = 1.0)

        // 模型再次认识同一标签 → 规则①拒写，USER 记录保持
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))

        val labels = db.musicLabelDao().getLabelsById(1L)
        assertEquals(1, labels.size, "槽位不应新增")
        assertEquals(MusicRepositoryBase.SOURCE_USER, labels.single().source)

        // USER 覆盖旧模型认识已产生 1 条留痕
        assertEquals(1, db.agentAuditLogDao().getRecent(10).size, "USER 覆盖留痕 1 条")
        // 规则①拒写不再新增审计（什么都没发生）
        assertEquals(1, db.agentAuditLogDao().getRecent(10).size, "被拒写的模型认识不应新增审计")
    }

    @Test
    fun userLabel_overwritesModelLabelOnlyForItsSlot() = runTest {
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.MOOD, LabelName.HAPPY))
        repo.addUserMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK), confidence = 1.0)

        val labels = db.musicLabelDao().getLabelsById(1L)
        assertEquals(2, labels.size)
        assertEquals(MusicRepositoryBase.SOURCE_USER, labels.single { it.label.name == "ROCK" }.source)
        assertEquals(MusicRepositoryBase.SOURCE_LLM, labels.single { it.label.name == "HAPPY" }.source)

        // 规则③留痕：USER 覆盖旧模型认识 → 1 条 label_correction（快照在 reason）
        val audits = db.agentAuditLogDao().getRecent(10)
        assertEquals(1, audits.size)
        assertEquals(MusicRepositoryBase.LABEL_CORRECTION_TOOL, audits.single().tool)
        assertEquals("superseded", audits.single().outcome)
        assertTrue(audits.single().reason!!.contains("T1 用户修正覆盖"))
    }

    @Test
    fun modelRelabel_preservesCreatedAt_updatesUpdatedAt() = runTest {
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))
        val first = db.musicLabelDao().getLabelsById(1L).single()
        Thread.sleep(5)
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))
        val second = db.musicLabelDao().getLabelsById(1L).single()

        assertEquals(first.createdAt, second.createdAt, "T2 被 T2 更新时保留初建时间（规则③留痕基础）")
        assertTrue(second.updatedAt!! >= second.createdAt!!, "updatedAt 应滚动")

        // 规则③留痕：T2 被 T2 更新 → 1 条 label_correction
        val audits = db.agentAuditLogDao().getRecent(10)
        assertEquals(1, audits.size)
        assertTrue(audits.single().reason!!.contains("T2 被 T2 更新"))
    }

    @Test
    fun backupRoundTrip_preservesUserLabelProvenance() = runTest {
        // review 修复 2026-08-28：备份还原后 USER 标签须保留规则①保护（快照携带 source/confidence/时间戳）
        repo.addUserMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK), confidence = 0.9)
        val before = db.musicLabelDao().getLabelsById(1L).single()

        val snapshot = repo.exportMusicUserStateSnapshot()
        val labelSnapshot = snapshot.labels.single()
        assertEquals(MusicRepositoryBase.SOURCE_USER, labelSnapshot.source)
        assertEquals(0.9, labelSnapshot.confidence)
        assertEquals(before.createdAt, labelSnapshot.createdAt)
        assertEquals(before.updatedAt, labelSnapshot.updatedAt)

        // 还原（REPLACE 覆盖同槽位）→ 溯源字段原样落库
        repo.restoreMusicUserState(snapshot)
        val restored = db.musicLabelDao().getLabelsById(1L).single()
        assertEquals(MusicRepositoryBase.SOURCE_USER, restored.source)
        assertEquals(0.9, restored.confidence)
        assertEquals(before.createdAt, restored.createdAt, "还原应保留原认识建立时间")
        assertEquals(before.updatedAt, restored.updatedAt)

        // 规则①在还原后依然生效：模型拒写
        repo.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))
        assertEquals(MusicRepositoryBase.SOURCE_USER, db.musicLabelDao().getLabelsById(1L).single().source)
    }

    @Test
    fun restoreLegacyV1Snapshot_labelsLackProvenance() = runTest {
        // v1 存量备份（无 source/confidence/时间戳字段）→ 还原后 null，按 LLM 旧认识处理
        val legacy = MusicUserStateSnapshot(
            labels = listOf(MusicLabelSnapshot(musicId = 1L, label = LabelName.ROCK, category = LabelCategory.GENRE))
        )
        repo.restoreMusicUserState(legacy)

        val restored = db.musicLabelDao().getLabelsById(1L).single()
        assertNull(restored.source)
        assertNull(restored.confidence)
        assertTrue(restored.createdAt != null && restored.createdAt!! > 0, "时间戳缺失时以还原时刻兜底")
    }

    /**
     * 一-2 / D5-13：在**已有播放历史**的设备上恢复备份，条数不能翻倍。
     *
     * 旧实现只 `insertAll`，而快照里每行的 id 都是 0（自增主键）→ 每次恢复都另起一批新行，
     * 总时长 / 完播率 / Top 歌曲跟着一起失真；`listeningDuration` 因主键是 date 侥幸盖得住，
     * 但快照里没有的旧日期会留在库里 —— 所以两张表都改成"先清后写"。
     */
    @Test
    fun restoreListeningStats_replacesInsteadOfAppending() = runTest {
        val history = db.playbackHistoryDao()
        val durations = db.listeningDurationDao()
        history.insert(com.hmp.data.database.PlaybackHistory(musicId = 1, playedAt = 1000L, playDuration = 50L))
        history.insert(com.hmp.data.database.PlaybackHistory(musicId = 2, playedAt = 2000L, playDuration = 60L))
        durations.insert(com.hmp.data.database.ListeningDuration(date = "2026-01-01", duration = 100L, updatedAt = 1L))

        val snapshot = repo.exportListeningStatsSnapshot()
        assertEquals(2, history.getAllHistory().size, "前置条件：库里已有 2 条历史")

        repo.restoreListeningStats(snapshot)

        assertEquals(2, history.getAllHistory().size, "恢复同一份快照不应把播放历史翻倍")
        assertEquals(1, durations.getAllDurations().size, "日累计同样应是替换结果")
    }
}
