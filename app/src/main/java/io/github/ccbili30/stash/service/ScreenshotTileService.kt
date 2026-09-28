package io.github.ccbili30.stash.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.ccbili30.stash.MainActivity
import io.github.ccbili30.stash.R
import io.github.ccbili30.stash.data.StashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 快捷设置磁贴：点一下截当前屏，只进 Stash 不进相册。
 * 无障碍服务未开启时，点击跳引导页。
 */
class ScreenshotTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onClick() {
        super.onClick()
        val service = StashAccessibilityService.instance
        if (service == null) {
            openGuide()
            return
        }
        // 点磁贴后快捷面板在收起，等它收完再截，避免截到收起动画面
        Handler(Looper.getMainLooper()).postDelayed({
            scope.launch {
                val bitmap = service.takeScreen()
                if (bitmap == null) {
                    Toast.makeText(this@ScreenshotTileService, R.string.screenshot_failed_toast, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                StashRepository.get(this@ScreenshotTileService).addBitmap(bitmap)
                bitmap.recycle()
                vibrate()
                Toast.makeText(this@ScreenshotTileService, R.string.screenshot_toast, Toast.LENGTH_SHORT).show()
            }
        }, PANEL_COLLAPSE_DELAY_MS)
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

    private fun vibrate() {
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java) ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        private const val PANEL_COLLAPSE_DELAY_MS = 600L
    }
}
