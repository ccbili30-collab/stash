package io.github.ccbili30.stash.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.ccbili30.stash.MainActivity
import io.github.ccbili30.stash.R
import io.github.ccbili30.stash.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 「Stash 收集」磁贴：亮 = 截图自动进 Stash（收藏模式）；
 * 灭 = 正常截图，只进相册。正常截图和收藏截图靠它随手切换。
 */
class CollectToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            render(SettingsStore(applicationContext).autoScreenshotFlow.first())
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val app = applicationContext
            val settings = SettingsStore(app)
            val next = !settings.autoScreenshotFlow.first()
            if (next && !MediaScreenshotWatcher.hasPermission(app)) {
                // 没有相册权限：跳到 app 设置页完成授权，磁贴保持灭
                render(false)
                openSettings()
                return@launch
            }
            settings.setAutoScreenshot(next)
            if (next) {
                MediaScreenshotWatcher.startIfPermitted(app)
            } else {
                MediaScreenshotWatcher.stop(app)
            }
            render(next)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (on) "Stash 收集中" else "Stash 收集"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_hi_scan01)
        tile.updateTile()
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_ROUTE, "settings")
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
