package io.github.ccbili30.stash.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.ccbili30.stash.MainActivity
import io.github.ccbili30.stash.R

/**
 * 快捷设置磁贴：点击唤起 / 关闭悬浮截屏球。
 * 服务没在跑、悬浮窗被拦截都明确 Toast 告知，不再静默失败。
 */
class ScreenshotTileService : TileService() {

    override fun onClick() {
        super.onClick()
        when (val result = StashAccessibilityService.toggleShutter()) {
            true -> Unit // 已执行
            false -> {
                // 进程没在跑：开关可能还开着（被 ROM 杀），也可能从没开过
                if (StashAccessibilityService.isEnabled(this)) {
                    Toast.makeText(
                        this,
                        "截图服务被系统停住了：到无障碍设置里把它关掉再重新打开即可",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    openGuide()
                }
            }
            null -> Toast.makeText(
                this,
                "悬浮窗被系统拦截：请到系统设置给 Stash 开启「显示悬浮窗」权限",
                Toast.LENGTH_LONG,
            ).show()
        }
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
