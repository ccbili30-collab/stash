package io.github.ccbili30.stash.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val autoScreenshot = booleanPreferencesKey("auto_screenshot")
    private val dynamicColor = booleanPreferencesKey("dynamic_color")

    val autoScreenshotFlow: Flow<Boolean> = context.dataStore.data.map { it[autoScreenshot] ?: false }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[dynamicColor] ?: true }

    suspend fun setAutoScreenshot(v: Boolean) {
        context.dataStore.edit { it[autoScreenshot] = v }
    }

    suspend fun setDynamicColor(v: Boolean) {
        context.dataStore.edit { it[dynamicColor] = v }
    }
}
