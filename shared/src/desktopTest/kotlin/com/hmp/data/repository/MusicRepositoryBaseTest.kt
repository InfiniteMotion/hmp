package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.test.db.createTestDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 用真实 in-memory Room DAO 装配的 [MusicRepositoryBase] 测试替身（平台分叉方法置为桩）。 */
private class InMemoryMusicRepository(db: AppDatabase) : MusicRepositoryBase(
    musicDao = db.musicDao(),
    musicExtraDao = db.musicExtraDao(),
    userInfoDao = db.userInfoDao(),
    musicAllDao = db.musicAllDao(),
    musicLabelDao = db.musicLabelDao(),
    playbackHistoryDao = db.playbackHistoryDao(),
    listeningDurationDao = db.listeningDurationDao(),
    playlistDao = db.playlistDao(),
    playlistItemDao = db.playlistItemDao(),
    openAiCompatibleAdapter = OpenAiCompatibleAdapter(HttpClient(), Json),
    json = Json,
    agentAuditLogDao = db.agentAuditLogDao(),
) {
    override val isScanning: Flow<Boolean> = flowOf(false)
    override suspend fun loadMusicFromDevice(): Result<Unit> = Result.success(Unit)
    override suspend fun syncMusicFromDeviceIncremental(): Result<Unit> = Result.success(Unit)
    override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> = emptyList()
    override suspend fun getRandomMusicInfoWithExtra(): MusicInfo? = null
    override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> = emptyList()
}

/**
 * M0-T3 溯源语义接线验证（设计总纲 3.2 裁判规则）：
 * - 模型认识（addMusicLabel）写 source=LLM + 初始 confidence + createdAt/updatedAt；
 * - 用户修正（addUserMusicLabel）写 source=USER，且 LLM 永不能覆盖（规则 ①）。
 */
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
}