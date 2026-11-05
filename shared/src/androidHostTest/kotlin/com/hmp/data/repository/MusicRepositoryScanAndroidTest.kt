package com.hmp.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import com.hmp.data.database.AppDatabase
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.RoomTransactionRunner
import com.hmp.data.database.getRoomDatabase
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.domain.music.AudioFormats
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * X-06：`MusicRepositoryImpl.android` 的 `performMusicScan`（338 行里真正只属于 Android 的那段）
 * 此前零测试。Desktop / iOS 扫的是文件系统，Android 走 **MediaStore** —— 这条管道没有
 * Robolectric 的假 ContentProvider 就一步都测不了，所以它一直裸奔。
 *
 * 做法：注册一个挂在 `media` authority 上的假 provider，喂任意行；
 * `loadMusicFromDevice()`（基类公共入口）会真的跑完「MediaStore 查询 → 白名单过滤 →
 * 标签解析 → 三表落库」，于是断言落在**数据库行**上，不落在中间产物上。
 *
 * 已知的测不到（如实记）：
 * - **目录 include / exclude 的 SQL**（`DATA LIKE ? ESCAPE '\'`）：过滤发生在 MediaStore 进程内，
 *   假 provider 收到 selection 但不执行它；这条的转义正确性由 `MusicRepositoryBaseTest` 的
 *   `toLikeContainsPattern` 系列与真 SQL 判据背书，这里不复钉；
 * - 逐文件标签解析的 jaudiotagger / MediaMetadataRetriever 回退：夹具行指向不存在的文件，
 *   `parseFileExtra` 走"文件不存在 → 字段留空"分支，回退链要真音频文件才测得了（desktopTest 已有同类覆盖）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicRepositoryScanAndroidTest {

    private class FakeMediaStoreProvider : ContentProvider() {

        data class Row(
            val id: Long,
            val title: String,
            val artist: String,
            val album: String,
            val duration: Long,
            val path: String,
            val albumId: Long,
            val mimeType: String?,
            val size: Long,
        )

        var rows: List<Row> = emptyList()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cols = projection ?: arrayOf(MediaStore.Audio.Media._ID)
            val cursor = MatrixCursor(cols)
            rows.forEach { r ->
                cursor.addRow(
                    cols.map { col ->
                        when (col) {
                            MediaStore.Audio.Media._ID -> r.id
                            MediaStore.Audio.Media.TITLE -> r.title
                            MediaStore.Audio.Media.ARTIST -> r.artist
                            MediaStore.Audio.Media.ALBUM -> r.album
                            MediaStore.Audio.Media.DURATION -> r.duration
                            MediaStore.Audio.Media.DATA -> r.path
                            MediaStore.Audio.Media.ALBUM_ID -> r.albumId
                            MediaStore.Audio.Media.MIME_TYPE -> r.mimeType
                            MediaStore.Audio.Media.SIZE -> r.size
                            else -> null
                        }
                    }
                )
            }
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String = "vnd.android.cursor.dir/audio"
    }

    private val provider = FakeMediaStoreProvider()
    private lateinit var app: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: MusicRepositoryImpl

    @BeforeTest
    fun setup() {
        app = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal("media", provider)
        db = createInMemoryDatabase()
        repo = MusicRepositoryImpl(
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
            forgottenDeliveryDao = db.forgottenDeliveryDao(),
            transactionRunner = RoomTransactionRunner(db),
            settingsRepository = SettingsRepositoryImpl(app),
            context = app,
        )
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    private fun createInMemoryDatabase(): AppDatabase =
        // 不设 BundledSQLiteDriver：它的 JVM 原生库（sqliteJni）在 android 变体的单测 classpath 里
        // 没有（那批 .so/.dll 只随 desktop 的 -jvm 构件分发），会 UnsatisfiedLinkError。
        // 不设驱动时 Room 在 Android 目标用默认的框架驱动，Robolectric 在 JVM 上原生模拟
        // android.database.sqlite —— 测试跑的是真的 SQL，只是由 Robolectric 的 SQLite 接管。
        Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    private fun row(
        id: Long,
        path: String,
        duration: Long = 30_000L,
        title: String = "Song $id",
        albumId: Long = 1L,
        mimeType: String? = "audio/mpeg",
        size: Long = 1_000L,
    ) = FakeMediaStoreProvider.Row(
        id = id, title = title, artist = "Artist", album = "Album",
        duration = duration, path = path, albumId = albumId, mimeType = mimeType, size = size,
    )

    /**
     * D2-09 在 Android 端的判据：白名单 + 5 秒下限在 MediaStore 查询**之后**由 Kotlin 施加。
     *
     * 修前 Android 的 SQL 写死 `DURATION > 60000`（一分钟！），40 秒的前奏/采样在 Android 上
     * 根本看不见。修后下限是 5 秒、由三端共用的 [AudioFormats] 判。
     *
     * **探针边界（如实记）**：假 provider 不执行 selection —— 所以"有人把 60 秒下限写回 SQL"这种
     * 回归这条**测不到**（selection 被忽略，行照样回来，5 秒闸门照样放行）。这条钉住的是
     * Kotlin 侧那道闸门的语义：白名单外的扩展名进不来、下限是 5 秒而不是 60 秒、
     * 边界是 `>=`、扩展名按小写比。SQL 侧的转义与目录过滤见类头"已知测不到"。
     */
    @Test
    fun loadMusicFromDevice_appliesWhitelistAndFiveSecondFloor() = runTest {
        provider.rows = listOf(
            row(id = 1, path = "/music/six-seconds.mp3", duration = 6_000L),      // 进
            row(id = 2, path = "/music/four-nine.flac", duration = 4_900L),       // 挡：下限
            row(id = 3, path = "/music/boundary.m4a", duration = 5_000L),         // 进：压线
            row(id = 4, path = "/music/not-audio.txt", duration = 60_000L),       // 挡：白名单
            row(id = 5, path = "/music/uppercase.WMA", duration = 20_000L),       // 进：扩展名按小写比较
            row(id = 6, path = "/music/long.aiff", duration = 120_000L),          // 挡：名单外（修前 Android 能看见）
        )

        repo.loadMusicFromDevice().getOrThrow()

        assertEquals(
            listOf(1L, 3L, 5L),
            db.musicDao().getAllActiveIds().sorted(),
            "白名单 + 5 秒下限过滤后的可见曲目应当恰好是这三首",
        )
    }

    /** MediaStore 一行要变成 `music` / `musicExtra` / `userInfo` 三行，映射字段逐个核对。 */
    @Test
    fun loadMusicFromDevice_mapsMediaStoreRowIntoThreeTables() = runTest {
        provider.rows = listOf(
            row(
                id = 7, path = "/music/seven.mp3", duration = 30_000L,
                title = "Song Seven", albumId = 42, mimeType = "audio/mpeg", size = 123_456L,
            )
        )

        repo.loadMusicFromDevice().getOrThrow()

        val music = db.musicDao().getMusicById(7).first()
        assertNotNull(music, "music 行应已落库")
        assertEquals("Song Seven", music.title)
        assertEquals("/music/seven.mp3", music.path)
        assertEquals(30_000L, music.duration)
        assertTrue(music.albumArtUri.endsWith("/42"), "albumArtUri 应由 albumId 拼出，实际 ${music.albumArtUri}")

        val extra = db.musicExtraDao().getByIds(listOf(7)).firstOrNull()
        assertNotNull(extra, "musicExtra 行应已落库")
        assertEquals("audio/mpeg", extra.format, "format 来自 MIME_TYPE")
        assertEquals(123_456L, extra.fileSize, "fileSize 来自 SIZE")
        assertEquals(false, extra.isGetExtraInfo, "扫描落库的行尚未富化")
        assertNotNull(extra.date, "首次扫描应给 date 打上当前时间")

        assertEquals(listOf(7L), db.userInfoDao().getAllIds(), "应为新曲目建 userInfo 默认行")
    }

    /** 已有的 `musicExtra.date` 是用户资产口径（首次入库时间），重扫不许刷新它。 */
    @Test
    fun rescan_preservesExistingExtraDate() = runTest {
        db.musicExtraDao().insert(MusicExtra(id = 9, date = 999L, isGetExtraInfo = true))
        provider.rows = listOf(row(id = 9, path = "/music/nine.mp3"))

        repo.loadMusicFromDevice().getOrThrow()

        val extra = db.musicExtraDao().getByIds(listOf(9)).firstOrNull()
        assertNotNull(extra)
        assertEquals(999L, extra.date, "重扫后 date 应保留首次入库时间")
        assertEquals(true, extra.isGetExtraInfo, "已富化标记不能被扫描冲掉")
    }
}
