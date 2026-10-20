package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.RoomTransactionRunner
import com.hmp.data.database.UserInfo
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.repository.toLikeContainsPattern
import com.hmp.domain.backup.MusicLabelSnapshot
import com.hmp.domain.backup.MusicUserStateSnapshot
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.data.database.myenum.LabelCategory as DbLabelCategory
import com.hmp.data.database.myenum.LabelName as DbLabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
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
    /**
     * D2-05 / D2-06：搜索的**转义**与**匹配列**必须在真 Room 上验。
     *
     * 为什么不走 commonTest：那条路只有 `FakeMusicRepository`（它匹配 title+artist+album），
     * 而生产 SQL 原先只匹配 title+artist —— 替身比真身更强，`invoke_matchesAlbum` 一直绿，
     * 把"搜专辑恒为空"这个真缺陷掩盖得毫无痕迹（D2-06 记的就是这件事）。
     * 契约测试因此落在这里：真 DAO + 真 LIKE + 真 ESCAPE。
     */
    @Test
    fun searchMusic_treatsWildcardsLiterallyAndMatchesAlbum() = runTest {
        insertSearchable(1L, title = "A_B", artist = "x", album = "Thriller")
        insertSearchable(2L, title = "AXB", artist = "y", album = "z")

        // `_` 是 LIKE 的单字符通配符：不转义时 "_" 会把 AXB 也捞出来
        assertEquals(listOf(1L), repo.searchMusic("_").map { it.music.id }.sorted())
        // `%` 是多字符通配符：字面搜 "%" 应当什么都没有
        assertEquals(emptyList<Long>(), repo.searchMusic("%").map { it.music.id })
        // 专辑名不在 title/artist 里，只能靠 album 列命中（D2-06 修前这里是 0 条）
        assertEquals(listOf(1L), repo.searchMusic("Thriller").map { it.music.id })
        // 软删的曲目不该被搜出来（与 isDeleted = 0 谓词同语义）
        repo.removeFromLibrary(listOf(1L))
        assertEquals(emptyList<Long>(), repo.searchMusic("Thriller").map { it.music.id })
    }

    /**
     * 转义次序是这个函数唯一的正确性所在，纯函数层面也钉一次。
     *
     * 下面每一对都是"Kotlin 字面量 → 期望的字符串内容"，注释里写的是**实际字符**（不是转义后）：
     * - `a\b`（一个反斜杠）→ `%a\\b%`（两个反斜杠）
     * - `100%` → `%100\%%`
     * - `a_b` → `%a\_b%`
     * - `a\_b`（反斜杠 + 下划线）→ `%a\\\_b%`（三个反斜杠：前两个来自反斜杠自身，第三个是下划线的转义）
     */
    @Test
    fun toLikeContainsPattern_escapesBackslashFirst() {
        assertEquals("%plain%", "plain".toLikeContainsPattern())
        assertEquals("%a\\\\b%", "a\\b".toLikeContainsPattern())
        assertEquals("%100\\%%", "100%".toLikeContainsPattern())
        assertEquals("%a\\_b%", "a_b".toLikeContainsPattern())
        assertEquals("%a\\\\\\_b%", "a\\_b".toLikeContainsPattern())
        // 次序错了（先转义 %/_、再转义反斜杠）的话，第三条会变成 "%a\\\\_b%" —— 上面这两条就是它的正反面
    }

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

    private suspend fun insertSearchable(id: Long, title: String, artist: String, album: String) {
        db.musicDao().insert(
            com.hmp.data.database.Music(
                id = id, title = title, artist = artist, album = album,
                duration = 100L, path = "/$id.mp3", albumArtUri = ""
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id))
    }

    /**
     * D2-08：排序键换成枚举之后，每个键都必须**真的**按那个键排。
     *
     * 修前三端各自 `when (orderBy) { … else -> 按 id }`，于是：
     * - 列表页排序菜单里的 `"fileSize"` / `"date"` 仓库根本不认 —— 点"按文件大小排序"实际按 id 排；
     * - `RadioSubAgent` 传的 `"play_count"`（下划线）同样落进 `else`；
     * - Android 的 ASC 分支对 duration/playCount 干脆返回未排序列表。
     * 这些都不会报错，只会安静地给出另一个顺序 —— 所以下面每个键都断言一次具体的序列。
     *
     * `DATE` 那条还钉住"没有日期的行恒在末尾"：整列反转的话，索引条点「未知」会跳到列表头部。
     */
    @Test
    fun getAllMusicInfoAsList_sortsByEveryKey() = runTest {
        insertSortable(id = 1, title = "Beta",  duration = 300L, playCount = 5, fileSize = 200L, date = 2_000L)
        insertSortable(id = 2, title = "Alpha", duration = 100L, playCount = 1, fileSize = 300L, date = 1_000L)
        insertSortable(id = 3, title = "Gamma", duration = 200L, playCount = 9, fileSize = 100L, date = null)

        suspend fun idsOf(key: MusicSortKey, direction: SortDirection): List<Long> =
            repo.getAllMusicInfoAsList(key, direction).map { it.music.id }

        assertEquals(listOf(2L, 1L, 3L), idsOf(MusicSortKey.TITLE, SortDirection.ASC))
        assertEquals(listOf(3L, 1L, 2L), idsOf(MusicSortKey.TITLE, SortDirection.DESC))

        assertEquals(listOf(2L, 3L, 1L), idsOf(MusicSortKey.DURATION, SortDirection.ASC))
        assertEquals(listOf(1L, 3L, 2L), idsOf(MusicSortKey.DURATION, SortDirection.DESC))

        // 播放次数 5 / 1 / 9
        assertEquals(listOf(2L, 1L, 3L), idsOf(MusicSortKey.PLAY_COUNT, SortDirection.ASC))
        assertEquals(listOf(3L, 1L, 2L), idsOf(MusicSortKey.PLAY_COUNT, SortDirection.DESC))

        // 修前这个键在 Android/Desktop/iOS 上都等于"按 id 排"
        assertEquals(listOf(3L, 1L, 2L), idsOf(MusicSortKey.FILE_SIZE, SortDirection.ASC))
        assertEquals(listOf(2L, 1L, 3L), idsOf(MusicSortKey.FILE_SIZE, SortDirection.DESC))

        // 无日期的 id=3：两个方向都在末尾
        assertEquals(listOf(2L, 1L, 3L), idsOf(MusicSortKey.DATE, SortDirection.ASC))
        assertEquals(listOf(1L, 2L, 3L), idsOf(MusicSortKey.DATE, SortDirection.DESC))

        assertEquals(listOf(1L, 2L, 3L), idsOf(MusicSortKey.ID, SortDirection.ASC))
        assertEquals(listOf(3L, 2L, 1L), idsOf(MusicSortKey.ID, SortDirection.DESC))
    }

    private suspend fun insertSortable(
        id: Long,
        title: String,
        duration: Long,
        playCount: Int,
        fileSize: Long,
        date: Long?,
    ) {
        db.musicDao().insert(
            com.hmp.data.database.Music(
                id = id, title = title, artist = "A", album = "B",
                duration = duration, path = "/$id.mp3", albumArtUri = ""
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = id, fileSize = fileSize, date = date, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id, playCount = playCount))
    }

    /**
     * D2-13：设置页「已隐藏文件夹」的分组要按**本端分隔符**切父目录。
     *
     * 修前三端各写一份，Desktop 那份写死 `substringBeforeLast("/")` —— 而 Windows 上库里的 path 是
     * `C:\Music\Pop\a.mp3`，切出来全是 "Unknown"：不同目录的隐藏曲目聚成一个错分组，
     * 点那一组的"取消隐藏"会一次恢复全部。
     *
     * 分组逻辑现在只有基类一份，分隔符是各端**声明**进去的参数 —— 正因如此这条断言能在
     * Linux / macOS 的 runner 上真验反斜杠（若实现走 `java.io.File.parent`，在这里会退化成一团，
     * 看着绿、其实什么都没验）。
     */
    @Test
    fun deletedMusicGroupedByFolder_groupsWindowsPathsByTheirOwnFolder() = runTest {
        val windowsRepo = InMemoryMusicRepository(db, separators = "\\/")
        insertHiddenAt(windowsRepo, 1L, """C:\Music\Pop\a.mp3""")
        insertHiddenAt(windowsRepo, 2L, """C:\Music\Pop\b.mp3""")
        insertHiddenAt(windowsRepo, 3L, """C:\Music\Rock\c.mp3""")

        val groups = windowsRepo.getDeletedMusicIdsGroupedByFolder()

        assertEquals(2, groups.size, "两个目录就该是两个分组，而不是全挤进一个 Unknown")
        assertEquals("""C:\Music\Pop""", groups[0].first, "按曲目数降序：Pop 有 2 首，排在前")
        assertEquals(listOf(1L, 2L), groups[0].second.sorted())
        assertEquals("""C:\Music\Rock""", groups[1].first)
        assertEquals(listOf(3L), groups[1].second)
    }

    /**
     * 同一份分组逻辑在 POSIX 分隔符下的对照：只认 `/`，路径里的反斜杠是文件名的一部分。
     *
     * 这一条与上一条是配对 —— 只验 Windows 那侧，很容易把修法写成"两种分隔符都认"，
     * 那 Android / iOS 上含 `\` 的文件名就会被切错位置。
     */
    @Test
    fun deletedMusicGroupedByFolder_posixPathsGroupBySlashOnly() = runTest {
        insertHiddenAt(repo, 1L, "/Music/Pop/a.mp3")
        insertHiddenAt(repo, 2L, "/Music/Rock/b\\c.mp3")

        val groups = repo.getDeletedMusicIdsGroupedByFolder()

        assertEquals(setOf("/Music/Pop", "/Music/Rock"), groups.map { it.first }.toSet())
    }

    private suspend fun insertHiddenAt(target: InMemoryMusicRepository, id: Long, path: String) {
        db.musicDao().insert(
            com.hmp.data.database.Music(
                id = id, title = "H$id", artist = "A", album = "B",
                duration = 60_000L, path = path, albumArtUri = ""
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id))
        target.removeFromLibrary(listOf(id))
    }

    /**
     * D2-15：相似歌**不得再逐行查标签**，且分数与顺序要和改造前一致。
     *
     * 修前这是全仓最典型的 N+1：全库载入之后再对每一行 `getMusicLabels(id)` 一次，
     * 3000 首 = 3001 次查询，而它的入口是用户主动按下的「心动模式」与 agent 的推荐扩列。
     * 两条断言各管一头：`ids` 那条管"改成批量没把语义改坏"，计数那条管"真的是批量"——
     * 只断言结果的话，退回逐行查照样是绿的。
     */
    @Test
    fun similarSongs_fetchLabelsInOneBatch_andRankByWeightedOverlap() = runTest {
        val counter = CountingLabelDao(db.musicLabelDao())
        val batchedRepo = InMemoryMusicRepository(db, labelDao = counter)

        // 基准曲 1：GENRE=POP（权重 3）+ MOOD=HAPPY（权重 4）
        insertLabeled(1L, listOf(DbLabelCategory.GENRE to DbLabelName.POP, DbLabelCategory.MOOD to DbLabelName.HAPPY))
        insertLabeled(2L, listOf(DbLabelCategory.GENRE to DbLabelName.POP))                                        // 3
        insertLabeled(3L, listOf(DbLabelCategory.MOOD to DbLabelName.HAPPY))                                       // 4
        insertLabeled(4L, listOf(DbLabelCategory.GENRE to DbLabelName.POP, DbLabelCategory.MOOD to DbLabelName.HAPPY)) // 7
        insertLabeled(5L, listOf(DbLabelCategory.GENRE to DbLabelName.ROCK))                                       // 0 → 不该出现

        val ids = batchedRepo.getSimilarSongsByWeightedLabels(1L, limit = 10).map { it.music.id }

        assertEquals(listOf(4L, 3L, 2L), ids, "按权重降序 7 > 4 > 3，零重叠的第 5 首被滤掉")
        assertEquals(1, counter.byIdCalls, "只该为基准那一首查一次标签")
        assertEquals(1, counter.allLabelsCalls, "全部标签一次取完，不是每行一次")
    }

    /**
     * D2-15：艺术家总览改走 SQL `GROUP BY` 后的口径 —— 计数、空白艺名不算分组、软删的不算，
     * **并列按名字破序**（修前并列顺序等于"按 playCount 降序的遍历顺序"，换一份播放历史名单就变）。
     */
    @Test
    fun artistsSummary_countsByGroupBy_andBreaksTiesByName() = runTest {
        insertArtist(id = 1L, artist = "Beatles")
        insertArtist(id = 2L, artist = "Beatles")
        insertArtist(id = 3L, artist = "Beatles")
        // 刻意先插 Mando 再插 Abba：两者计数并列，于是"按名字破序"与"按插入/分组顺序"给出不同答案
        insertArtist(id = 4L, artist = "Mando")
        insertArtist(id = 5L, artist = "Abba")
        insertArtist(id = 6L, artist = "   ") // isBlank：不该成为一个分组
        insertArtist(id = 7L, artist = "Zelda")
        repo.removeFromLibrary(listOf(7L))    // 软删的不该被数进去

        assertEquals(
            listOf("Beatles" to 3, "Abba" to 1, "Mando" to 1),
            repo.getAllArtistsSummary(limit = 5),
            "计数降序 + 并列按名字（不是插入顺序）；空白艺名与已移除的曲目都不在里面",
        )
    }

    /**
     * 五个类别的权重必须**真的生效**（D2-15 顺带查出的那处 bug 的钉）。
     *
     * `labelCategoryWeight` 原先四项键用的是 DAO 实体的枚举、查的时候用的是 domain 的枚举，
     * 键类型被推成 `Any` → 除 SCENARIO 外一律查不中、全部按 1 计，"加权"从来没起过作用。
     * `Map<Any, Int>` 是完全合法的写法，编译器不会响 —— 只有这条断言能拦住它。
     *
     * 期望顺序就是权重的顺序：MOOD(4) > GENRE(3) > SCENARIO(2) > LANGUAGE(1) = ERA(1)，
     * 并列的两个按 ID 升序（`sortedByDescending` 稳定 + 取数是 ID ASC）。
     */
    @Test
    fun similarityWeights_areAppliedPerCategory() = runTest {
        insertLabeled(
            1L,
            listOf(
                DbLabelCategory.GENRE to DbLabelName.POP,
                DbLabelCategory.MOOD to DbLabelName.HAPPY,
                DbLabelCategory.SCENARIO to DbLabelName.WORKOUT,
                DbLabelCategory.LANGUAGE to DbLabelName.CHINESE,
                DbLabelCategory.ERA to DbLabelName.NINETIES,
            )
        )
        // 每首候选只与基准重叠一个类别 → 它的分数就等于那个类别的权重
        insertLabeled(2L, listOf(DbLabelCategory.ERA to DbLabelName.NINETIES))        // 1
        insertLabeled(3L, listOf(DbLabelCategory.LANGUAGE to DbLabelName.CHINESE))    // 1
        insertLabeled(4L, listOf(DbLabelCategory.SCENARIO to DbLabelName.WORKOUT))    // 2
        insertLabeled(5L, listOf(DbLabelCategory.GENRE to DbLabelName.POP))           // 3
        insertLabeled(6L, listOf(DbLabelCategory.MOOD to DbLabelName.HAPPY))          // 4

        val ids = repo.getSimilarSongsByWeightedLabels(1L, limit = 10).map { it.music.id }

        assertEquals(listOf(6L, 5L, 4L, 2L, 3L), ids, "顺序必须按类别权重，而不是全部按 1 计后的 ID 序")
    }

    /** 只拦这两个方法，其余全部委托给真 DAO（D2-15 判据要问的就是"调用了几次"）。 */
    private class CountingLabelDao(
        private val delegate: com.hmp.data.database.MusicLabelDao,
    ) : com.hmp.data.database.MusicLabelDao by delegate {
        var byIdCalls = 0
        var allLabelsCalls = 0

        override suspend fun getLabelsById(musicId: Long): List<com.hmp.data.database.MusicLabel> {
            byIdCalls++
            return delegate.getLabelsById(musicId)
        }

        override suspend fun getAllLabels(): List<com.hmp.data.database.MusicLabel> {
            allLabelsCalls++
            return delegate.getAllLabels()
        }
    }

    private suspend fun insertLabeled(
        id: Long,
        labels: List<Pair<com.hmp.data.database.myenum.LabelCategory, com.hmp.data.database.myenum.LabelName>>,
    ) {
        db.musicDao().insert(
            com.hmp.data.database.Music(
                id = id, title = "L$id", artist = "A", album = "B",
                duration = 60_000L, path = "/$id.mp3", albumArtUri = ""
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id))
        labels.forEach { (type, name) ->
            db.musicLabelDao().insert(
                com.hmp.data.database.MusicLabel(musicId = id, type = type, label = name)
            )
        }
    }

    private suspend fun insertArtist(id: Long, artist: String) {
        db.musicDao().insert(
            com.hmp.data.database.Music(
                id = id, title = "T$id", artist = artist, album = "B$id",
                duration = 60_000L, path = "/$id.mp3", albumArtUri = ""
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id))
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
