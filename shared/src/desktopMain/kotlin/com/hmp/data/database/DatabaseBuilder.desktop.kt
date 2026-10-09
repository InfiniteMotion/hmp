package com.hmp.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** 桌面端库文件的固定位置（守卫与启动探测都读这一个来源，别再各拼一遍路径）。 */
fun hmpDatabasePath(): String =
    File(File(System.getProperty("user.home"), ".hmp"), "music_database.db").absolutePath

fun getDatabaseBuilder(): androidx.room.RoomDatabase.Builder<AppDatabase> {
    val path = hmpDatabasePath()
    File(path).parentFile?.mkdirs()
    // 降级守卫（D7-10）：库比代码新就在这里以类型化异常拦下，不交给 Room 抛 "Migration not found"
    AppDatabaseGuard.enforce(path)
    return Room.databaseBuilder<AppDatabase>(
        name = path
    )
}

actual fun getRoomDatabase(builder: androidx.room.RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        // 1→9 迁移链完整，故不再挂破坏式重建兜底。
        // fallbackToDestructiveMigration(dropAllTables = true) 会连「降级」与「未知版本」一起放过：
        // 用户的库版本只要高于本应用（beta 回退、新设备备份恢复、新库拷回旧安装），
        // 整库就被静默 drop —— 播放列表/听歌统计/Agent 记忆全没且无提示。
        // 宁可打不开（数据仍在，升回新版本即可恢复），也不静默清库。
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
