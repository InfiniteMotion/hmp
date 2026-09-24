package com.hmp.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

fun getDatabaseBuilder(): androidx.room.RoomDatabase.Builder<AppDatabase> {
    val appDir = File(System.getProperty("user.home"), ".hmp")
    if (!appDir.exists()) appDir.mkdirs()
    val dbFile = File(appDir, "music_database.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
}

actual fun getRoomDatabase(builder: androidx.room.RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
        )
        // 1→9 迁移链完整，故不再挂破坏式重建兜底。
        // fallbackToDestructiveMigration(dropAllTables = true) 会连「降级」与「未知版本」一起放过：
        // 用户的库版本只要高于本应用（beta 回退、新设备备份恢复、新库拷回旧安装），
        // 整库就被静默 drop —— 播放列表/听歌统计/Agent 记忆全没且无提示。
        // 宁可打不开（数据仍在，升回新版本即可恢复），也不静默清库。
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
