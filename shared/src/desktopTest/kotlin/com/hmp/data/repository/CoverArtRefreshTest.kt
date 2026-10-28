package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.UserInfo
import com.hmp.data.util.CoverArtStore
import com.hmp.data.util.FileCoverArtWriter
import com.hmp.domain.music.EditableMusicTags
import com.hmp.test.db.createTestDatabase
import com.hmp.test.repo.InMemoryMusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * D2-17 判据：换过封面的文件，第二次保存之后**看到的必须是新封面**。
 *
 * 修前是两处各自"没毛病"凑出来的 bug：缓存侧按**音频路径**命名 + "存在即跳过"（新版字节根本没落盘），
 * 仓库侧 `updateMusicTags` / `refreshMusicTags` **根本不碰 `music.albumArtUri`**。
 * 用户点保存 → 标签写进文件成功 → 曲库与播放页仍是旧图，除非手工删 `~/.hmp/covers/`。
 *
 * 所以这里两头都要断言：新文件内容与 uri 都指向新字节。
 * 只断言文件内容的话，"缓存写对了但 uri 没跟过去"这半边照样绿。
 */
class CoverArtRefreshTest {

    private lateinit var db: AppDatabase
    private lateinit var coversDir: File
    private lateinit var repo: InMemoryMusicRepository

    private val oldCover = byteArrayOf(1, 2, 3, 4)
    private val newCover = byteArrayOf(9, 8, 7, 6, 5)

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        coversDir = Files.createTempDirectory("hmp-covers-").toFile()
        repo = InMemoryMusicRepository(db, coverArtWriter = FileCoverArtWriter(coversDir))
    }

    @AfterTest
    fun teardown() {
        db.close()
        coversDir.deleteRecursively()
    }

    /** 种子数据要挂起，所以放在每个用例开头而不是 `@BeforeTest`。 */
    private suspend fun seedMusic() {
        db.musicDao().insert(
            com.hmp.data.database.Music(
                id = 1L, title = "T", artist = "A", album = "B",
                duration = 60_000L, path = "/Music/t.mp3", albumArtUri = ""
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = 1L, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = 1L))
    }

    private suspend fun uriInDb(): String =
        db.musicDao().getMusicById(1L).first()?.albumArtUri ?: "<null>"

    @Test
    fun secondCoverReplacesTheFirst_inFileAndInDb() = runTest {
        seedMusic()
        repo.refreshMusicTags(1L, EditableMusicTags(albumArt = oldCover))
        val firstUri = uriInDb()
        assertEquals(File(coversDir, CoverArtStore.fileNameFor(oldCover)).absolutePath, firstUri)

        // 同一个音频文件，换一版封面
        repo.refreshMusicTags(1L, EditableMusicTags(albumArt = newCover))
        val secondUri = uriInDb()

        assertNotEquals(firstUri, secondUri, "uri 必须跟到新文件，否则界面永远读第一张")
        assertEquals(File(coversDir, CoverArtStore.fileNameFor(newCover)).absolutePath, secondUri)
        assertEquals(
            newCover.toList(),
            File(secondUri).readBytes().toList(),
            "缓存里那份的字节必须是新的（修前「存在即跳过」把它吞了）",
        )
    }

    @Test
    fun emptyAlbumArtMeansRemoveTheCover() = runTest {
        seedMusic()
        repo.refreshMusicTags(1L, EditableMusicTags(albumArt = oldCover))
        assertTrue(uriInDb().isNotEmpty(), "基线：先有一张封面")

        repo.refreshMusicTags(1L, EditableMusicTags(albumArt = ByteArray(0)))

        assertEquals("", uriInDb(), "空数组是「移除封面」的既定语义（EditableMusicTags.albumArt 的注释）")
    }

    /**
     * 本端没有封面缓存（Android：`content://media/.../albumart`）时**不能把 uri 抹成空**。
     *
     * 这一条是反向的护栏：实现里若把"写盘返回 null"当成"清除封面"，
     * 现在能显示的封面会被这次保存弄没。
     */
    @Test
    fun noWriterOnThisPlatform_leavesTheExistingUriAlone() = runTest {
        seedMusic()
        val repoWithoutCache = InMemoryMusicRepository(db)   // coverArtWriter = null
        db.musicDao().updateAlbumArtUriById(1L, "/old/cover_x.jpg")

        repoWithoutCache.refreshMusicTags(1L, EditableMusicTags(albumArt = newCover))

        assertEquals("/old/cover_x.jpg", uriInDb(), "落不了盘就保留原 uri")
    }
}
