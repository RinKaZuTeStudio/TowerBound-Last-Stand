package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated splash: procedural tower rises from silhouette, crystal ignites,
 * title letters fly in with glow, ambient particles drift. Pure Canvas — no
 * assets. Auto-advances after [FULL_DURATION_MS]; tap after [MIN_DURATION_MS]
 * skips early.
 */
class SplashScreen(context: Context) : Screen(context) {

    companion object {
        const val MIN_DURATION_MS = 2400L
        const val FULL_DURATION_MS = 3600L
    }

    private var startTime = 0L
    var onFinished: (() -> Unit)? = null
    private var finished = false

    private val canvasView = SplashCanvasView(context).apply {
        onEarlySkip = { tryFinish(true) }
    }

    init {
        applyBackdrop(UiKit.cBgDark, Color.parseColor("#141a35"), UiKit.cBgDark)
        addView(canvasView, UiKit.params(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun onEnter() {
        startTime = System.currentTimeMillis()
        canvasView.start()
        postDelayed({ tryFinish(false) }, FULL_DURATION_MS)
    }

    private fun tryFinish(early: Boolean) {
        if (finished) return
        if (!early || System.currentTimeMillis() - startTime >= MIN_DURATION_MS) {
            finished = true
            onFinished?.invoke()
        }
    }

    override fun onExit() {
        canvasView.stop()
    }
}

/** The animated splash scene itself. */
private class SplashCanvasView(context: Context) : View(context) {

    var onEarlySkip: (() -> Unit)? = null
    private var running = false
    private val startNano = System.nanoTime()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mountainPath = Path()

    // Star field + drifting embers
    private val rnd = Random(20260825)
    private val stars = Array(90) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat() * 1.5f, rnd.nextFloat() * 6.28f)
    }
    private val embers = Array(26) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 2f + rnd.nextFloat() * 4f, 0.2f + rnd.nextFloat() * 0.8f)
    }

    fun start() {
        running = true
        postInvalidateOnAnimation()
    }

    fun stop() { running = false }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (System.nanoTime() - startNano) / 1_000_000_000f

        // night sky gradient
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#070A18"),
            Color.parseColor("#1b2145"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // stars twinkle
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(t * 1.7f + s[3])
            paint.color = Color.argb((140 * tw + 40).toInt(), 255, 255, 255)
            canvas.drawCircle(s[0] * w, s[1] * h * 0.7f, s[2], paint)
        }

        val cx = w / 2f
        val groundY = h * 0.62f

        // tower rise animation: 0..1 over 0.9s with ease-out
        val rise = easeOutClamped(t / 0.9f)
        val towerH = h * 0.34f * rise
        val towerW = min(w, h) * 0.14f

        // ground glow
        paint.shader = RadialGradient(cx, groundY, w * 0.5f,
            Color.argb((90 * rise).toInt(), 124, 77, 255),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, groundY, w * 0.5f, paint)
        paint.shader = null

        // distant mountains
        paint.color = Color.parseColor("#11152c")
        mountainPath.reset()
        mountainPath.moveTo(0f, groundY)
        val peaks = floatArrayOf(0.12f, 0.3f, 0.44f, 0.62f, 0.78f, 0.93f)
        for (i in peaks.indices) {
            val px = peaks[i] * w
            val ph = (if (i % 2 == 0) 0.16f else 0.24f) * h * (0.8f + 0.2f * sin(i * 2.3f))
            mountainPath.lineTo(px - w * 0.07f, groundY)
            mountainPath.lineTo(px, groundY - ph)
            mountainPath.lineTo(px + w * 0.07f, groundY)
        }
        mountainPath.lineTo(w, groundY)
        mountainPath.lineTo(w, h)
        mountainPath.lineTo(0f, h)
        mountainPath.close()
        canvas.drawPath(mountainPath, paint)

        // tower body
        val topY = groundY - towerH
        paint.color = Color.parseColor("#3d2a86")
        val inset = towerW * 0.12f
        canvas.drawRect(cx - towerW / 2f + inset, topY, cx + towerW / 2f - inset, groundY, paint)
        // battlements
        paint.color = Color.parseColor("#5836b8")
        val bw = (towerW - inset * 2f) / 5f
        for (i in 0 until 5 step 2) {
            canvas.drawRect(cx - towerW / 2f + inset + i * bw, topY - towerW * 0.14f,
                cx - towerW / 2f + inset + (i + 1) * bw, topY, paint)
        }
        // crystal
        val crystalT = easeOutClamped((t - 0.8f) / 0.6f)
        if (crystalT > 0f) {
            val cs = towerW * 0.38f * crystalT
            val ccy = topY - towerW * 0.25f
            paint.color = Color.argb(90, 0, 229, 255)
            canvas.drawCircle(cx, ccy, cs * 2.4f * (1f + 0.06f * sin(t * 3f)), paint)
            paint.color = Color.parseColor("#00E5FF")
            val path = Path()
            path.moveTo(cx, ccy - cs)
            path.lineTo(cx + cs * 0.62f, ccy)
            path.lineTo(cx, ccy + cs)
            path.lineTo(cx - cs * 0.62f, ccy)
            path.close()
            canvas.drawPath(path, paint)
        }

        // embers rising
        for (e in embers) {
            val ex = (e[0] + 0.03f * sin(t * e[3] * 2f + e[1] * 9f)) * w
            val ey = ((e[1] - (t * 0.03f * e[3])) % 1f + 1f) % 1f * groundY
            paint.color = Color.argb(150, 255, 180, 80)
            canvas.drawCircle(ex, ey, e[2] * 0.7f, paint)
        }

        // titles
        val titleT = easeOutClamped((t - 0.5f) / 0.8f)
        if (titleT > 0f) {
            textPaint.textSize = min(w * 0.085f, h * 0.16f)
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.setShadowLayer(18f, 0f, 0f, Color.argb(200, 124, 77, 255))
            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * titleT).toInt()
            val s1 = "TOWERBOUND"
            val tw = textPaint.measureText(s1)
            canvas.drawText(s1, cx - tw / 2f + (1f - titleT) * w * 0.12f, h * 0.20f, textPaint)

            val subT = easeOutClamped((t - 0.9f) / 0.8f)
            if (subT > 0f) {
                textPaint.textSize = min(w * 0.045f, h * 0.09f)
                textPaint.color = Color.parseColor("#00E5FF")
                textPaint.alpha = (255 * subT).toInt()
                textPaint.setShadowLayer(12f, 0f, 0f, Color.argb(160, 0, 229, 255))
                val s2 = "LAST STAND"
                val tw2 = textPaint.measureText(s2)
                canvas.drawText(s2, cx - tw2 / 2f - (1f - subT) * w * 0.1f, h * 0.28f, textPaint)
                textPaint.clearShadowLayer()
            }
        }

        if (running) postInvalidateOnAnimation()
    }

    override fun performClick(): Boolean {
        onEarlySkip?.invoke()
        return super.performClick()
    }

    private fun easeOutClamped(x: Float): Float =
        if (x <= 0f) 0f else if (x >= 1f) 1f else 1f - (1f - x) * (1f - x)
}
