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
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
