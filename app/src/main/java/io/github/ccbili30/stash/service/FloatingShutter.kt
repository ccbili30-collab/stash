package io.github.ccbili30.stash.service

import android.annotation.SuppressLint
import android.content.Context
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
import io.github.ccbili30.stash.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 悬浮截屏球（挂在无障碍 overlay 层，无需额外权限）。
 *
 * 交互：磁贴唤起小灰球（贴边高透明待命、可拖）→ 点球展开截屏按钮（球变红×）→
 * 点按钮截图（截图前隐藏全部悬浮窗，截图中不含自己；截完按钮变暗、展开态保持、
 * 无冷却可连点）→ 点空白处收回成贴边球 → 点红×彻底关闭。
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
    private val btnSize = dp(58)
    private val gap = dp(12)
    private val touchSlop = dp(8)

    private var screenW = 0
    private var screenH = 0

    private var state = State.HIDDEN
    private var capturing = false
    private var ballX = 0
    private var ballY = 0

    // ---- 悬浮窗组件 ----

    private val ballIcon = ImageView(service).apply {
        layoutParams = FrameLayout.LayoutParams(ballSize, ballSize)
        setImageResource(R.drawable.ic_hi_scan01)
        setColorFilter(Color.WHITE)
    }

    private val ballView = FrameLayout(service).apply {
        layoutParams = LinearLayout.LayoutParams(ballSize, ballSize)
        background = circle(0xB3808080.toInt())
        addView(ballIcon)
    }

    private val shutterIcon = ImageView(service).apply {
        layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
        setImageResource(R.drawable.ic_hi_scan01)
        setColorFilter(Color.WHITE)
    }

    private val shutterBtn = FrameLayout(service).apply {
        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
        background = circle(accent())
        addView(shutterIcon)
        visibility = View.GONE
    }

    private val box = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = gap })
        addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize))
    }

    private val controlRoot = FrameLayout(service).apply { addView(box) }

    private val maskView = View(service)

    private var controlParams = controlParams(ballSize, ballSize, 0, 0)
    private var maskParams: WindowManager.LayoutParams? = null

    init {
        val bounds = wm.maximumWindowMetrics.bounds
        screenW = bounds.width()
        screenH = bounds.height()
        // 默认待命位：右边缘垂直居中
        ballX = screenW - ballSize - dp(4)
        ballY = screenH / 2
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
        state = State.HIDDEN
    }

    // ---- 状态流转 ----

    private fun showBall() {
        ballIcon.setImageResource(R.drawable.ic_hi_scan01)
        ballView.background = circle(0xB3808080.toInt())
        shutterBtn.visibility = View.GONE
        controlParams = controlParams(ballSize, ballSize, ballX, ballY)
        runCatching { wm.addView(controlRoot, controlParams) }
        snapToEdge(animate = false, dim = true)
        state = State.BALL
    }

    private fun expand() {
        ballIcon.setImageResource(R.drawable.ic_hi_cancel01)
        ballView.background = circle(0xE6E53935.toInt())
        setShutterDimmed(false)
        shutterBtn.visibility = View.VISIBLE

        // 按钮默认在球上方；球太靠上则翻到下方
        val flipDown = ballY < btnSize + gap + ballSize + dp(24)
        box.removeAllViews()
        if (flipDown) {
            box.addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize).apply { bottomMargin = gap })
            box.addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize))
        } else {
            box.addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = gap })
            box.addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize))
        }

        val h = btnSize + gap + ballSize
        val x = (ballX + ballSize / 2 - btnSize / 2).coerceIn(0, screenW - btnSize)
        val y = if (flipDown) ballY else ballY - btnSize - gap
        controlParams = controlParams(btnSize, h, x, y)
        runCatching { wm.updateViewLayout(controlRoot, controlParams) }

        // 全屏透明遮罩：点空白处收回
        maskParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        maskView.setOnClickListener { collapse() }
        runCatching { wm.addView(maskView, maskParams) }

        shutterBtn.scaleX = 0.4f
        shutterBtn.scaleY = 0.4f
        shutterBtn.animate().scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
            .start()

        ballView.animate().alpha(1f).setDuration(0).start()
        state = State.EXPANDED
    }

    private fun collapse() {
        runCatching { wm.removeViewImmediate(maskView) }
        maskParams = null
        ballIcon.setImageResource(R.drawable.ic_hi_scan01)
        ballView.background = circle(0xB3808080.toInt())
        shutterBtn.visibility = View.GONE
        box.removeAllViews()
        box.addView(shutterBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = gap })
        box.addView(ballView, LinearLayout.LayoutParams(ballSize, ballSize))
        controlParams = controlParams(ballSize, ballSize, ballX, ballY)
        runCatching { wm.updateViewLayout(controlRoot, controlParams) }
        snapToEdge(animate = true, dim = true)
        state = State.BALL
    }

    // ---- 截屏 ----

    @SuppressLint("ClickableViewAccessibility")
    private fun performShutter() {
        if (capturing) return
        capturing = true
        setShutterDimmed(false) // 按下变亮
        vibrate(15)

        // 隐藏全部悬浮窗，等合成一帧后再截，截图里不含悬浮窗自己
        controlRoot.visibility = View.INVISIBLE
        maskView.visibility = View.INVISIBLE
        main.postDelayed({
            scope.launch(Dispatchers.Main) {
                val bitmap = runCatching { service.takeScreen() }.getOrNull()
                main.post {
                    controlRoot.visibility = View.VISIBLE
                    maskView.visibility = View.VISIBLE
                    if (bitmap != null) {
                        setShutterDimmed(true) // 截完变暗
                        vibrate(25)
                        onShot(bitmap)
                    }
                    capturing = false
                }
            }
        }, 120)
    }

    // ---- 触摸与拖动 ----

    @SuppressLint("ClickableViewAccessibility")
    private fun wireEvents() {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        ballView.setOnTouchListener { _, e ->
            if (state == State.EXPANDED) {
                if (e.action == MotionEvent.ACTION_UP) hide()
                return@setOnTouchListener true
            }
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = controlParams.x; startY = controlParams.y
                    dragging = false
                    ballView.animate().alpha(0.95f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (abs(e.rawX - downX) > touchSlop || abs(e.rawY - downY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        ballX = (startX + e.rawX - downX).toInt().coerceIn(0, screenW - ballSize)
                        ballY = (startY + e.rawY - downY).toInt().coerceIn(0, screenH - ballSize)
                        controlParams.x = ballX
                        controlParams.y = ballY
                        runCatching { wm.updateViewLayout(controlRoot, controlParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        snapToEdge(animate = true, dim = false)
                    } else if (e.action == MotionEvent.ACTION_UP) {
                        expand()
                    }
                    true
                }
                else -> false
            }
        }

        shutterBtn.setOnClickListener { performShutter() }
    }

    /** 吸附最近边缘并压低透明度（待命态） */
    private fun snapToEdge(animate: Boolean, dim: Boolean) {
        val targetX = if (ballX + ballSize / 2 < screenW / 2) 0 else screenW - ballSize
        val targetAlpha = if (dim) 0.35f else 0.95f
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
        anim.duration = 260
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        anim.addUpdateListener { a ->
            val t = a.animatedValue as Float
            ballX = (fromX + (targetX - fromX) * t).toInt()
            controlParams.x = ballX
            controlParams.y = ballY
            runCatching { wm.updateViewLayout(controlRoot, controlParams) }
            ballView.alpha = 0.95f + (targetAlpha - 0.95f) * t
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }
}
