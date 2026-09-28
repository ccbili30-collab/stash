package io.github.ccbili30.stash.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 临时截图引擎：无障碍服务的 takeScreenshot 能力。
 * 配置声明 canRetrieveWindowContent=false —— 不读屏幕内容，只截屏。
 */
class StashAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: StashAccessibilityService? = null
            private set

        /** 服务是否已在系统设置中开启 */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, StashAccessibilityService::class.java)
                .flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (name in splitter) {
                if (name.equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}

/** 挂起式截屏：成功返回软件位图（可直接压缩落盘），失败返回 null。 */
suspend fun AccessibilityService.takeScreen(): Bitmap? =
    suspendCancellableCoroutine { cont ->
        val displayId = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.displayId ?: Display.DEFAULT_DISPLAY
        takeScreenshot(
            displayId,
            Runnable::run,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        val srgb = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                        // colorSpace 访问器是 API 34，拿到就用（截屏 buffer 多为 P3），否则按 sRGB 解
                        val hw = if (Build.VERSION.SDK_INT >= 34) {
                            runCatching {
                                Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            }.getOrNull() ?: Bitmap.wrapHardwareBuffer(buffer, srgb)
                        } else {
                            Bitmap.wrapHardwareBuffer(buffer, srgb)
                        }
                        // 硬件位图不能压缩写文件，转软拷贝
                        hw?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        buffer.close()
                    }
                    if (cont.isActive) cont.resumeWith(Result.success(bitmap))
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
            },
        )
    }
