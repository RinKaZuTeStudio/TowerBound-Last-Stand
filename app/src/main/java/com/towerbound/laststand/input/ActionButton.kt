package com.towerbound.laststand.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import com.towerbound.laststand.assets.SymbolPainter
import kotlin.math.min

/**
 * Circular gameplay action button (ability / ultimate / dash) with procedural
 * icon, cooldown sweep, glow when ready and press feedback.
 */
@SuppressLint("ViewConstructor")
class ActionButton(
    context: Context,
    private val symbol: SymbolPainter.Symbol,
    private val color: Int,
    private val onPress: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var pressT = 0f

    /** 0..1 remaining cooldown fraction; 0 = ready. Set from HUD updates. */
    @Volatile var cooldownFraction: Float = 0f

    /** Extra glow pulse when ready. */
    @Volatile var readyPulse: Boolean = true

    private var time = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = symbol.name
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressT = 1f
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressT = 0f
                val inside = event.x >= 0 && event.y >= 0 && event.x <= width && event.y <= height
                if (inside) {
                    performClick()
                    onPress()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressT = 0f; invalidate(); return true }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        time += 0.016f
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f * (1f - pressT * 0.08f)
        val ready = cooldownFraction <= 0.001f

        // outer glow when ready
        if (ready && readyPulse) {
            paint.color = Color.argb((60 + 40 * kotlin.math.sin(time * 3f)).toInt().coerceIn(0, 255),
                Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(cx, cy, r * 1.18f, paint)
        }

        // face
        paint.shader = SweepGradient(cx, cy,
            intArrayOf(darken(color, 0.35f), lighten(color, 0.15f), darken(color, 0.35f)),
            floatArrayOf(0f, 0.5f, 1f))
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = if (ready) lighten(color, 0.3f) else Color.argb(120, 120, 126, 160)
        canvas.drawCircle(cx, cy, r - 2f, paint)
        paint.style = Paint.Style.FILL

        // cooldown sweep overlay
        if (cooldownFraction > 0.001f) {
            paint.color = Color.argb(170, 12, 14, 30)
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, -90f, -360f * cooldownFraction, true, paint)
        }

        // icon
        val iconSize = (r * 1.35f)
        canvas.save()
        canvas.scale(iconSize / 96f, iconSize / 96f, cx, cy)
        paint.alpha = if (ready) 255 else 110
        SymbolPainter.drawSymbol(canvas, paint, symbol, Color.WHITE, 96)
        paint.alpha = 255
        canvas.restore()
    }

    private fun darken(c: Int, f: Float): Int {
        val k = 1f - f
        return Color.rgb((Color.red(c) * k).toInt().coerceIn(0, 255),
            (Color.green(c) * k).toInt().coerceIn(0, 255),
            (Color.blue(c) * k).toInt().coerceIn(0, 255))
    }

    private fun lighten(c: Int, f: Float): Int {
        return Color.rgb((Color.red(c) + (255 - Color.red(c)) * f).toInt().coerceIn(0, 255),
            (Color.green(c) + (255 - Color.green(c)) * f).toInt().coerceIn(0, 255),
            (Color.blue(c) + (255 - Color.blue(c)) * f).toInt().coerceIn(0, 255))
    }
}
