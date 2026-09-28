package io.github.ccbili30.stash.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val autoScreenshot = booleanPreferencesKey("auto_screenshot")
    private val dynamicColor = booleanPreferencesKey("dynamic_color")
    private val cleanAfterCollect = booleanPreferencesKey("clean_after_collect")
    private val lastScreenshotSeen = longPreferencesKey("last_screenshot_seen")

    val autoScreenshotFlow: Flow<Boolean> = context.dataStore.data.map { it[autoScreenshot] ?: false }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[dynamicColor] ?: true }
    val cleanAfterCollectFlow: Flow<Boolean> =
        context.dataStore.data.map { it[cleanAfterCollect] ?: false }

    /** 已收过的最新截屏时间戳（epoch 秒），补扫只看它之后的 */
    val lastScreenshotSeenFlow: Flow<Long> =
        context.dataStore.data.map { it[lastScreenshotSeen] ?: 0L }

    suspend fun setAutoScreenshot(v: Boolean) {
        context.dataStore.edit { it[autoScreenshot] = v }
    }

    suspend fun setDynamicColor(v: Boolean) {
        context.dataStore.edit { it[dynamicColor] = v }
    }

    suspend fun setCleanAfterCollect(v: Boolean) {
        context.dataStore.edit { it[cleanAfterCollect] = v }
    }

    suspend fun setLastScreenshotSeen(seconds: Long) {
        context.dataStore.edit { prefs ->
            val old = prefs[lastScreenshotSeen] ?: 0L
            if (seconds > old) prefs[lastScreenshotSeen] = seconds
        }
    }
}
