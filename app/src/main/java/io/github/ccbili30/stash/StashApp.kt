package io.github.ccbili30.stash

import android.app.Application
import io.github.ccbili30.stash.data.SettingsStore
import io.github.ccbili30.stash.service.MediaScreenshotWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StashApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 自动收截屏：开关打开且权限在手时开始监测
        appScope.launch {
            SettingsStore(this@StashApp).autoScreenshotFlow.collect { enabled ->
                if (enabled) {
                    MediaScreenshotWatcher.startIfPermitted(this@StashApp)
                } else {
                    MediaScreenshotWatcher.stop()
                }
            }
        }
    }
}
