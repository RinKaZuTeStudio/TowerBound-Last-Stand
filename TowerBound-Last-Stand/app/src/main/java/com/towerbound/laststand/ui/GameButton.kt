package com.towerbound.laststand.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import kotlin.math.sin

/**
 * Fully procedural game button: gradient face, glow border, press-squash with
 * overshoot bounce, subtle idle shimmer. No image assets.
 *
 * Accessibility: behaves like a button (contentDescription, pressed state).
 */
@SuppressLint("ViewConstructor")
class GameButton(
    context: Context,
    var label: String,
    private val style: Style = Style.PRIMARY
) : android.view.View(context) {

    enum class Style { PRIMARY, SECONDARY, DANGER, GHOST, GOLD }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0))
    }

    /** 0..1 press animation; 1 = fully pressed-in */
    private var pressT = 0f
    private var pressAnimator: ValueAnimator? = null

    /** click hook with haptic/sound handled globally by UiSfx */
    var onButtonClick: (() -> Unit)? = null
    var enabledState: Boolean = true
        set(value) { field = value; alpha = if (value) 1f else 0.45f; invalidate() }

    private var clickTime = 0f
    private val shimmerPhase: Float = (id % 100) * 0.13f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // shadowLayer needs software for text? GL text is separate; safe default
        isClickable = true
        isFocusable = true
        contentDescription = label
        minimumWidth = UiKit.dp(context, 140f)
        minimumHeight = UiKit.dp(context, 48f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledState) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animatePress(1f)
                performClick()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val inside = isWithinBounds(event.x, event.y)
                animatePress(0f)
                if (inside) {
                    UiSfx.click(context)
                    onButtonClick?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animatePress(0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isWithinBounds(x: Float, y: Float): Boolean =
        x >= 0 && y >= 0 && x <= width && y <= height

    private fun animatePress(target: Float) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressT, target).apply {
            duration = if (target > 0f) 70 else 320
            interpolator = if (target > 0f) null else OvershootInterpolator(2.2f)
            addUpdateListener {
                pressT = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    public override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val squash = 1f - pressT * 0.07f
        val cx = w / 2f
        val cy = h / 2f
        val bw = w * squash
        val bh = h * squash
        val left = cx - bw / 2f
        val top = cy - bh / 2f
        val right = cx + bw / 2f
        val bottom = cy + bh / 2f
        val bevel = UiKit.dp(context, 3f).toFloat()

        // colors by style: [face, faceDark, bevelLight, outline, glow]
        val (face, faceDark, bevelLight, outline, glow) = when (style) {
            Style.PRIMARY -> listOf(UiKit.cPrimary, UiKit.cPrimaryDark, 0xFF6A79D2.toInt(), 0xFF101432.toInt(), 0x5900E5FF)
            Style.SECONDARY -> listOf(UiKit.cBgPanelLight, UiKit.cBgPanel, 0xFF5A648F.toInt(), 0xFF0C1024.toInt(), 0x507C4DFF)
            Style.DANGER -> listOf(0xFF8e2626.toInt(), 0xFF6b3a3a.toInt(), 0xFFC25B4A.toInt(), 0xFF2A0C0C.toInt(), 0x59FF5252)
            Style.GHOST -> listOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, UiKit.cTextDim, 0)
            Style.GOLD -> listOf(0xFFD9A521.toInt(), 0xFF9a6d10.toInt(), 0xFFF2CE6B.toInt(), 0xFF33200a.toInt(), 0x66FFC947)
        }

        if (style == Style.GHOST) {
            // ghost keeps the thin outline treatment
            paint.color = Color.argb(40, 255, 255, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = UiKit.dp(context, 1.5f).toFloat()
            canvas.drawRect(left, top, right, bottom, paint)
            paint.style = Paint.Style.FILL
        } else {
            // drop shadow (offset chunky block)
            paint.color = Color.argb(80, 0, 0, 0)
            canvas.drawRect(left + bevel, top + bevel, right + bevel, bottom + bevel, paint)

            // outer outline
            paint.color = outline
            canvas.drawRect(left, top, right, bottom, paint)

            // face: two-tone pixel shading (upper face lighter than lower)
            val innerL = left + bevel
            val innerT = top + bevel
            val innerR = right - bevel
            val innerB = bottom - bevel
            paint.color = face
            canvas.drawRect(innerL, innerT, innerR, innerB, paint)
            paint.color = faceDark
            canvas.drawRect(innerL, innerT + (innerB - innerT) * 0.55f, innerR, innerB, paint)

            // bevel: light top edge, dark bottom edge (chunky pixel bevel)
            paint.color = bevelLight
            canvas.drawRect(innerL, innerT, innerR, innerT + bevel, paint)
            paint.color = Color.argb(120, 6, 8, 18)
            canvas.drawRect(innerL, innerB - bevel, innerR, innerB, paint)

            // moving shimmer highlight
            clickTime += 0.016f
            val sx = cx + sin(clickTime * 1.3f + shimmerPhase) * bw * 0.32f
            paint.color = glow
            paint.alpha = (Color.alpha(glow) * (0.4f + 0.6f * (pressT))).coerceIn(0f, 255f).toInt()
            canvas.drawCircle(sx, cy, bh * 0.42f, paint)
            paint.alpha = 255
        }

        // label
        val textSize = h * 0.34f
        textPaint.textSize = textSize
        textPaint.color = if (style == Style.GHOST) UiKit.cTextDim else Color.WHITE
        val tw = textPaint.measureText(label)
        canvas.drawText(label, cx - tw / 2f, cy + textSize * 0.36f, textPaint)
    }

    override fun onDetachedFromWindow() {
        pressAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Global UI sound/haptic hook. AudioManager (Phase 21) replaces the no-op
 * implementation; the call sites stay stable.
 */
object UiSfx {
    @Volatile var clickHandler: ((Context) -> Unit)? = null
    @Volatile var enabled: Boolean = true

    fun click(ctx: Context) {
        if (enabled) clickHandler?.invoke(ctx)
    }
}
