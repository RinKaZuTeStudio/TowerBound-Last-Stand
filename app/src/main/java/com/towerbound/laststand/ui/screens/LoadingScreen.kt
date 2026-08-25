package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.towerbound.laststand.R
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import kotlin.math.sin
import kotlin.random.Random

/**
 * Loading screen with animated progress bar, rotating status text and a
 * breathing rune circle. Progress is driven by [LoadingPipeline] callbacks.
 */
class LoadingScreen(context: Context) : Screen(context) {

    private val progressBar = ProgressBarView(context)
    private val statusText: TextView = UiKit.text(context, context.getString(R.string.loading_assets), 16f, UiKit.cTextDim, bold = false)
    private val runeView = RuneSpinnerView(context)

    /** Set from the loading pipeline listener. */
    fun setProgress(fraction: Float, text: String) {
        post {
            progressBar.progress = fraction.coerceIn(0f, 1f)
            if (text.isNotBlank()) statusText.text = text
        }
    }

    init {
        applyBackdrop(Color.parseColor("#070A18"), Color.parseColor("#151b3d"))
        val wrap = FrameLayout(context)

        wrap.addView(runeView, UiKit.params(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val center = UiKit.vertical(context, 12f)
        val title = UiKit.title(context, context.getString(R.string.splash_title), 30f)
        center.addView(title, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        center.addView(progressBar, UiKit.linearParams(UiKit.dp(context, 340f), UiKit.dp(context, 22f)))
        center.addView(statusText, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        wrap.addView(center, UiKit.params(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(wrap, UiKit.params(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun onEnter() { runeView.start() }
    override fun onExit() { runeView.stop() }

    /** Animated horizontal progress bar with glow tip. */
    private class ProgressBarView(context: Context) : View(context) {
        var progress: Float = 0f
            set(value) { field = value.coerceIn(0f, 1f); invalidate() }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val w = width.toFloat()
            val r = h / 2f
            // track
            paint.color = Color.argb(255, 30, 36, 66)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            // fill
            if (progress > 0.01f) {
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, w * progress, 0f,
                    UiKit.cPrimary, UiKit.cAccent, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(0f, 0f, w * progress, h, r, r, paint)
                paint.shader = null
                // glow tip
                paint.color = Color.argb(200, 0, 229, 255)
                canvas.drawCircle(w * progress, h / 2f, r * 0.8f, paint)
            }
            // border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(120, 124, 77, 255)
            canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, paint)
            paint.style = Paint.Style.FILL
        }
    }

    /** Slowly rotating summoning circle behind the text. */
    private class RuneSpinnerView(context: Context) : View(context) {
        private var running = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rnd = Random(4242)
        private val dots = Array(60) { Pair(rnd.nextFloat() * 6.28f, 0.4f + rnd.nextFloat() * 1.2f) }

        fun start() { running = true; postInvalidateOnAnimation() }
        fun stop() { running = false }

        override fun onDraw(canvas: Canvas) {
            val t = System.currentTimeMillis() % 100000 / 1000f
            val cx = width / 2f
            val cy = height / 2f
            val rad = minOf(width, height) * 0.42f

            paint.shader = RadialGradient(cx, cy, rad,
                Color.argb(46, 124, 77, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, rad, paint)
            paint.shader = null

            canvas.save()
            canvas.rotate(t * 9f % 360f, cx, cy)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb(70, 0, 229, 255)
            canvas.drawCircle(cx, cy, rad * 0.92f, paint)
            canvas.drawCircle(cx, cy, rad * 0.7f, paint)
            paint.style = Paint.Style.FILL
            for ((a, s) in dots) {
                val ang = a + t * 0.2f
                val x = cx + kotlin.math.cos(ang) * rad * 0.92f
                val y = cy + kotlin.math.sin(ang) * rad * 0.92f
                paint.color = Color.argb((120 + 80 * sin(t + a)).toInt().coerceIn(0, 255), 0, 229, 255)
                canvas.drawCircle(x, y, s, paint)
            }
            canvas.restore()

            if (running) postInvalidateOnAnimation()
        }
    }
}
