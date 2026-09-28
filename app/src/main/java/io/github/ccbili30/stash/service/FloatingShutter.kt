package io.github.ccbili30.stash.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.ccbili30.stash.MainActivity
import io.github.ccbili30.stash.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 悬浮截屏球 v2（挂无障碍 overlay 层，无需额外权限）。
 *
 * 交互：
 * - 默认只有小球；点小球「分裂」出更大的截屏按钮（横排，贴右缘时按钮在左），球变红×
 * - 展开态与收回态均可拖动（展开态整组一起拖）
 * - 点截屏按钮：隐藏悬浮窗→截图→恢复，并像系统截屏一样在右下角弹缩略图预览（快速自动消失，可点进 Stash）
 * - 点空白处收回小球
 * - 收回态贴近边缘松手 → 折叠（半嵌进屏幕边）；点折叠球弹回并直接展开
 * - 点红×彻底关闭
 */
class FloatingShutter(
    private val service: StashAccessibilityService,
    private val onShot: (Bitmap) -> Unit,
) {

    private enum class State { HIDDEN, BALL, EXPANDED }

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()

    private val density = service.resources.displayMetrics.density
    private val ballSize = dp(46)
    private val btnSize = dp(62)
    private val gap = dp(12)
    private val touchSlop = dp(10)
    private val foldMargin = dp(28)      // 距边缘多近松手才吸附折叠

    private var screenW = 0
    private var screenH = 0

    private var state = State.HIDDEN
    private var capturing = false
    private var folded = false           // 收回态是否处于折叠（半嵌边缘）
    private var ballX = 0                // 球（窗口内容锚点）位置
    private var ballY = 0

    // ---- 悬浮窗组件 ----

    private val ballIcon = ImageView(service).apply {
        setImageResource(R.drawable.ic_hi_scan01)
        setColorFilter(Color.WHITE)
    }

    private val ballView = FrameLayout(service).apply {
        background = circle(0xB3808080.toInt())
        addView(ballIcon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }

    private val shutterIcon = ImageView(service).apply {
        setImageResource(R.drawable.ic_hi_scan01)
        setColorFilter(Color.WHITE)
    }

    private val shutterBtn = FrameLayout(service).apply {
        background = circle(accent())
        addView(shutterIcon, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        visibility = View.GONE
    }

    /** 控件容器：收回时只装球；展开时横排 [按钮, 球]（或 [球, 按钮]） */
    private val box = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { marginEnd = gap })
        addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize))
    }

    private val controlRoot = FrameLayout(service).apply { addView(box) }
    private val maskView = View(service)

    /** 截图成功后的预览缩略图窗 */
    private val previewCard = FrameLayout(service).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(0xF2FFFFFF.toInt())
            setStroke(dp(1), 0x1A000000)
        }
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }
    private val previewImage = ImageView(service).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    }

    private var controlParams = controlParams(ballSize, ballSize, 0, 0)
    private var maskParams: WindowManager.LayoutParams? = null
    private var previewShown = false

    init {
        val bounds = wm.maximumWindowMetrics.bounds
        screenW = bounds.width()
        screenH = bounds.height()
        ballX = screenW - ballSize - dp(4)
        ballY = screenH * 2 / 3
        previewCard.addView(
            previewImage,
            FrameLayout.LayoutParams(dp(96), dp(170), Gravity.CENTER),
        )
        previewCard.setOnClickListener {
            hidePreview()
            runCatching {
                service.startActivity(
                    Intent(service, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        wireEvents()
    }

    // ---- 对外 ----

    /** 磁贴点击：显示 / 彻底关闭 */
    fun toggle() {
        when (state) {
            State.HIDDEN -> showBall()
            else -> hide()
        }
    }

    fun hide() {
        if (state == State.HIDDEN) return
        runCatching {
            wm.removeViewImmediate(controlRoot)
            maskParams?.let { runCatching { wm.removeViewImmediate(maskView) } }
        }
        maskParams = null
        shutterBtn.visibility = View.GONE
        hidePreview()
        state = State.HIDDEN
    }

    // ---- 状态流转 ----

    private fun showBall() {
        state = State.BALL
        folded = false
        setBallLook()
        shutterBtn.visibility = View.GONE
        controlParams = controlParams(ballSize, ballSize, ballX, ballY)
        runCatching { wm.addView(controlRoot, controlParams) }
        // 出现即贴边折叠待命
        snapAndFold(animate = false)
    }

    /** 点球：分裂出截屏按钮，球变红× */
    private fun expand() {
        state = State.EXPANDED
        folded = false
        setBallLook(red = true)
        setShutterDimmed(false)
        shutterBtn.visibility = View.VISIBLE

        // 横排：贴右半屏时按钮在球左侧，贴左半屏时按钮在右侧（避免出屏）
        val buttonLeft = ballX + ballSize / 2 >= screenW / 2
        box.removeAllViews()
        if (buttonLeft) {
            box.addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { marginEnd = gap })
            box.addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize))
        } else {
            box.addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize).apply { marginEnd = gap })
            box.addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize))
        }

        val h = btnSize
        val w = btnSize + gap + ballSize
        val x = if (buttonLeft) (ballX + ballSize + gap / 2 - w).coerceAtLeast(0) else ballX - gap / 2
        val y = (ballY + ballSize / 2 - h / 2).coerceIn(0, screenH - h)
        controlParams = controlParams(w, h, x, y)
        runCatching { wm.updateViewLayout(controlRoot, controlParams) }

        maskParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        maskView.setOnClickListener { collapse() }
        runCatching { wm.addView(maskView, maskParams) }

        // 按钮弹入
        shutterBtn.scaleX = 0.3f
        shutterBtn.scaleY = 0.3f
        shutterBtn.alpha = 0f
        shutterBtn.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.35f))
            .start()
    }

    /** 点空白处：收回成小球 */
    private fun collapse() {
        runCatching { wm.removeViewImmediate(maskView) }
        maskParams = null
        state = State.BALL
        setBallLook()
        shutterBtn.visibility = View.GONE
        ballView.animate().alpha(1f).setDuration(0).start()
        controlParams = controlParams(ballSize, ballSize, ballX, ballY)
        runCatching { wm.updateViewLayout(controlRoot, controlParams) }
    }

    private fun setBallLook(red: Boolean = false) {
        ballIcon.setImageResource(if (red) R.drawable.ic_hi_cancel01 else R.drawable.ic_hi_scan01)
        ballView.background = circle(if (red) 0xE6E53935.toInt() else 0xB3808080.toInt())
        ballView.alpha = if (red) 1f else 0.9f
    }

    // ---- 截屏 ----

    private fun performShutter() {
        if (capturing) return
        capturing = true
        setShutterDimmed(false)
        vibrate(15)

        controlRoot.visibility = View.INVISIBLE
        maskView.visibility = View.INVISIBLE
        if (previewShown) previewCard.visibility = View.INVISIBLE
        main.postDelayed({
            scope.launch(Dispatchers.Main) {
                val bitmap = runCatching { service.takeScreen() }.getOrNull()
                main.post {
                    controlRoot.visibility = View.VISIBLE
                    maskView.visibility = View.VISIBLE
                    if (bitmap != null) {
                        setShutterDimmed(true)
                        vibrate(25)
                        showPreview(bitmap)
                        onShot(bitmap)
                    }
                    capturing = false
                }
            }
        }, 120)
    }

    // ---- 预览窗（像系统截屏那样） ----

    private fun showPreview(bitmap: Bitmap) {
        hidePreview()
        val thumb = scaleDown(bitmap, 480)
        previewImage.setImageBitmap(thumb)
        val ratio = thumb.height.toFloat() / thumb.width.toFloat()
        val w = dp(96)
        previewImage.layoutParams = FrameLayout.LayoutParams(w, (w * ratio).toInt(), Gravity.CENTER)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(20)
            y = dp(96)
        }
        runCatching {
            wm.addView(previewCard, params)
            previewShown = true
            previewCard.alpha = 0f
            previewCard.scaleX = 0.75f
            previewCard.scaleY = 0.75f
            previewCard.translationX = dp(40).toFloat()
            previewCard.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationX(0f)
                .setDuration(180)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        // 快速消掉
        schedulePreviewHide()
    }

    private var previewHideRunnable: Runnable? = null

    private fun schedulePreviewHide() {
        previewHideRunnable?.let { main.removeCallbacks(it) }
        val r = Runnable {
            previewCard.animate().alpha(0f)
                .setDuration(220)
                .withEndAction { hidePreview() }
                .start()
        }
        previewHideRunnable = r
        main.postDelayed(r, 1300)
    }

    private fun hidePreview() {
        previewHideRunnable?.let { main.removeCallbacks(it) }
        previewHideRunnable = null
        runCatching { wm.removeViewImmediate(previewCard) }
        previewShown = false
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= maxEdge) return src
        val s = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
    }

    // ---- 触摸：拖动 + 点击统一处理（展开/收回都可拖） ----

    @SuppressLint("ClickableViewAccessibility")
    private fun wireEvents() {
        controlRoot.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    draggingOccurred = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleDrag(e)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (draggingOccurred) finishDrag() else handleTap(e)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishDrag()
                    true
                }
                else -> false
            }
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startWinX = 0
    private var startWinY = 0
    private var draggingOccurred = false

    private fun handleDrag(e: MotionEvent) {
        if (!draggingOccurred) {
            if (abs(e.rawX - downRawX) <= touchSlop && abs(e.rawY - downRawY) <= touchSlop) return
            draggingOccurred = true
            startWinX = controlParams.x
            startWinY = controlParams.y
            vibrate(8)
        }
        val nx = (startWinX + e.rawX - downRawX).toInt()
        val ny = (startWinY + e.rawY - downRawY).toInt()
        controlParams.x = nx.coerceIn(-ballSize / 2, screenW - ballSize / 2)
        controlParams.y = ny.coerceIn(0, screenH - controlParams.height)
        runCatching { wm.updateViewLayout(controlRoot, controlParams) }
        // 展开态：同步球心记录，供收回时定位
        if (state == State.EXPANDED) {
            val w = controlParams.width
            ballX = (controlParams.x + w - ballSize).coerceIn(0, screenW - ballSize)
            ballY = (controlParams.y + (controlParams.height - ballSize) / 2).coerceIn(0, screenH - ballSize)
        } else {
            ballX = controlParams.x
            ballY = controlParams.y
        }
    }

    private fun finishDrag() {
        if (state == State.BALL) {
            val nearEdge = ballX < foldMargin || ballX > screenW - ballSize - foldMargin
            if (nearEdge) snapAndFold(animate = true) else ballView.animate().alpha(0.9f).setDuration(120).start()
        }
        draggingOccurred = false
    }

    /** 点击分发：按落点区域判定点的是球还是截屏按钮 */
    private fun handleTap(e: MotionEvent) {
        when (state) {
            State.BALL -> {
                if (folded) unfoldThenExpand() else expand()
            }
            State.EXPANDED -> {
                val localX = e.rawX - controlParams.x
                val localY = e.rawY - controlParams.y
                val buttonLeft = box.indexOfChild(shutterBtn) == 0
                val btnStart = if (buttonLeft) 0 else controlParams.width - btnSize
                val inBtn = localX >= btnStart && localX <= btnStart + btnSize &&
                    localY in 0f..btnSize.toFloat()
                if (inBtn) performShutter() else hide() // 点到球（红×）：彻底关闭
            }
            State.HIDDEN -> Unit
        }
    }

    private fun unfoldThenExpand() {
        // 先弹回屏幕内，再展开
        folded = false
        val targetX = (ballX + ballSize / 2).coerceIn(0, screenW - ballSize)
        val targetY = ballY.coerceIn(0, screenH - ballSize)
        ballX = targetX
        ballY = targetY
        controlParams = controlParams(ballSize, ballSize, ballX, targetY)
        runCatching { wm.updateViewLayout(controlRoot, controlParams) }
        expand()
    }

    /** 吸附最近边缘并折叠（半嵌进屏幕边） */
    private fun snapAndFold(animate: Boolean) {
        folded = true
        val left = ballX + ballSize / 2 < screenW / 2
        val targetX = if (left) -ballSize / 2 else screenW - ballSize / 2
        val targetAlpha = 0.55f
        if (!animate) {
            ballX = targetX
            controlParams.x = ballX
            controlParams.y = ballY
            runCatching { wm.updateViewLayout(controlRoot, controlParams) }
            ballView.alpha = targetAlpha
            return
        }
        val fromX = ballX
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 240
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        anim.addUpdateListener { a ->
            val t = a.animatedValue as Float
            ballX = (fromX + (targetX - fromX) * t).toInt()
            controlParams.x = ballX
            controlParams.y = ballY
            runCatching { wm.updateViewLayout(controlRoot, controlParams) }
            ballView.alpha = 0.9f + (targetAlpha - 0.9f) * t
        }
        anim.start()
    }

    private fun setShutterDimmed(dim: Boolean) {
        shutterBtn.alpha = if (dim) 0.35f else 1f
        shutterIcon.alpha = if (dim) 0.6f else 1f
    }

    private fun vibrate(ms: Long) {
        runCatching {
            service.getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }

    // ---- 工具 ----

    private fun dp(v: Int): Int = (v * density).toInt()

    private fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    /** 截屏按钮取系统动态色，Android 11 回落 Ocean 种子色 */
    private fun accent(): Int = runCatching {
        service.getColor(android.R.color.system_accent1_600)
    }.getOrDefault(0xFF116682.toInt())

    private fun controlParams(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }
}
