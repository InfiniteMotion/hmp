package com.hmp.data.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File

actual object DataStoreFactory {
    actual fun create(): DataStore<Preferences> {
        val appDir = File(System.getProperty("user.home"), ".hmp")
        if (!appDir.exists()) appDir.mkdirs()
        val filePath = File(appDir, "player_preferences.preferences_pb").absolutePath
        return PreferenceDataStoreFactory.createWithPath(
            produceFile = { filePath.toPath() },
        )
    }
}
