package com.hmp.test.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hmp.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Creates an in-memory Room database for testing.
 * Uses the bundled SQLite driver with in-memory storage (no file on disk).
 */
fun createTestDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
