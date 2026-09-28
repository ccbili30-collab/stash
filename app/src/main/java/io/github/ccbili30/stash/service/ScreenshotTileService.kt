package io.github.ccbili30.stash.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.ccbili30.stash.MainActivity

/**
 * 快捷设置磁贴：点击唤起 / 关闭悬浮截屏球。
 * 真正的截屏时机由悬浮球的截屏按钮控制（截图不含悬浮窗自己，可连截）。
 */
class ScreenshotTileService : TileService() {

    override fun onClick() {
        super.onClick()
        if (StashAccessibilityService.toggleShutter()) return
        openGuide()
    }

    private fun openGuide() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_A11Y_GUIDE)
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
