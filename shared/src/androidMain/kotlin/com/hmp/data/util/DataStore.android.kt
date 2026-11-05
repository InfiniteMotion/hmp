package com.hmp.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "player_preferences")

actual object DataStoreFactory {
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    actual fun create(): DataStore<Preferences> = context?.dataStore
        ?: throw IllegalStateException("DataStoreFactory not initialized. Call init() first.")
}
