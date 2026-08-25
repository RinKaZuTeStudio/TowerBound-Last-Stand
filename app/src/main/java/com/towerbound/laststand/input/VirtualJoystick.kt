package com.towerbound.laststand.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Floating virtual joystick: appears where the finger lands on the left half
 * of the screen (multi-touch safe), with dead-zone, clamp radius and animated
 * knob return. Size and side are configurable (accessibility).
 */
@SuppressLint("ViewConstructor")
class VirtualJoystick(
    context: Context,
    private val input: InputState,
    private val config: Config = Config()
) : View(context) {

    data class Config(
        val baseRadiusDp: Float = 78f,
        val knobRadiusDp: Float = 34f,
        val deadZone: Float = 0.12f,
        val followDrag: Boolean = true
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Active pointer id, -1 when idle. */
    private var pointerId = -1
    private var baseX = 0f
    private var baseY = 0f
    private var knobX = 0f
    private var knobY = 0f
    private var active = false

    /** Smoothed knob return animation. */
    private var releaseAnim = 0f

    var onMoveListener: ((x: Float, y: Float) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    val isActive: Boolean get() = active

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // world-visible debug position defaults
        baseX = config.baseRadiusDp * 2.2f * resources.displayMetrics.density
        baseY = height - config.baseRadiusDp * 2.2f * resources.displayMetrics.density
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                // accept new pointer only when idle and in the movement half
                // (left half in LTR, right half in RTL layouts)
                val movementSideStart = if (layoutDirection == LAYOUT_DIRECTION_RTL)
                    x > width * 0.45f else x < width * 0.55f
                if (pointerId == -1 && movementSideStart) {
                    pointerId = event.getPointerId(idx)
                    baseX = x; baseY = y
                    knobX = x; knobY = y
                    active = true
                    releaseAnim = 1f
                    updateInput()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(pointerId)
                if (idx >= 0) {
                    val x = event.getX(idx)
                    val y = event.getY(idx)
                    var dx = x - baseX
                    var dy = y - baseY
                    val len = sqrt(dx * dx + dy * dy)
                    val maxR = baseRadiusPx()
                    if (len > maxR) {
                        if (config.followDrag && len > maxR * 1.8f) {
                            // drag the base along for long swipes
                            val excess = len - maxR * 1.2f
                            baseX += dx / len * excess
                            baseY += dy / len * excess
                            dx = x - baseX; dy = y - baseY
                        } else {
                            dx = dx / len * maxR
                            dy = dy / len * maxR
                        }
                    }
                    knobX = baseX + dx
                    knobY = baseY + dy
                    updateInput()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == pointerId) {
                    release()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return super.onTouchEvent(event)
    }

    private fun release() {
        pointerId = -1
        active = false
        input.moveX = 0f
        input.moveY = 0f
        onMoveListener?.invoke(0f, 0f)
        releaseAnim = 0f
        invalidate()
    }

    private fun updateInput() {
        val maxR = baseRadiusPx()
        var dx = (knobX - baseX) / maxR
        var dy = (knobY - baseY) / maxR
        val len = sqrt(dx * dx + dy * dy)
        if (len < config.deadZone) {
            input.moveX = 0f; input.moveY = 0f
        } else {
            val scaled = (len - config.deadZone) / (1f - config.deadZone)
            val norm = scaled / len
            dx *= norm; dy *= norm
            val m = min(1f, sqrt(dx * dx + dy * dy) / 0.98f)
            val ang = atan2(dy, dx)
            input.moveX = cos(ang) * m
            input.moveY = sin(ang) * m
        }
        onMoveListener?.invoke(input.moveX, input.moveY)
    }

    private fun baseRadiusPx(): Float = config.baseRadiusDp * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (!active && releaseAnim <= 0.99f && !alwaysShowGhost) {
            postInvalidateOnAnimation()
            return
        }
        val maxR = baseRadiusPx()
        val knobR = config.knobRadiusDp * resources.displayMetrics.density
        val alpha = if (active) 1f else 0.25f

        // base ring glow
        paint.shader = RadialGradient(baseX, baseY, maxR,
            Color.argb((40 * alpha).toInt(), 124, 77, 255),
            Color.argb(0, 124, 77, 255), Shader.TileMode.CLAMP)
        paint.color = Color.WHITE
        canvas.drawCircle(baseX, baseY, maxR, paint)
        paint.shader = null

        // base ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb((150 * alpha).toInt(), 200, 208, 255)
        canvas.drawCircle(baseX, baseY, maxR, paint)
        paint.style = Paint.Style.FILL

        // direction hint arrows when active
        if (active) {
            val ang = atan2(knobY - baseY, knobX - baseX)
            paint.color = Color.argb((200 * alpha).toInt(), 0, 229, 255)
            for (r in floatArrayOf(maxR * 0.55f, maxR * 0.78f)) {
                val ax = baseX + cos(ang) * r
                val ay = baseY + sin(ang) * r
                val s = 5f
                val path = android.graphics.Path()
                path.moveTo(ax + cos(ang) * s, ay + sin(ang) * s)
                path.lineTo(ax + cos(ang + 2.5f) * s, ay + sin(ang + 2.5f) * s)
                path.lineTo(ax + cos(ang - 2.5f) * s, ay + sin(ang - 2.5f) * s)
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        // knob
        paint.shader = RadialGradient(knobX - knobR * 0.3f, knobY - knobR * 0.35f, knobR * 1.4f,
            Color.argb((255 * alpha).toInt(), 240, 244, 255),
            Color.argb((220 * alpha).toInt(), 90, 100, 180), Shader.TileMode.CLAMP)
        canvas.drawCircle(knobX, knobY, knobR, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.argb((180 * alpha).toInt(), 0, 229, 255)
        canvas.drawCircle(knobX, knobY, knobR, paint)
        paint.style = Paint.Style.FILL

        if (!active) {
            releaseAnim = (releaseAnim + 0.06f).coerceAtMost(1f)
            postInvalidateOnAnimation()
        }
    }

    private var alwaysShowGhost = true

    /** Show a hint ghost when idle (first-run discoverability). */
    fun showGhostHint(show: Boolean) {
        alwaysShowGhost = show
        invalidate()
    }
}
