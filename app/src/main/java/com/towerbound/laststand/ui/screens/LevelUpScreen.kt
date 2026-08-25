package com.towerbound.laststand.ui.screens

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.progression.UpgradeDef
import com.towerbound.laststand.ui.UiKit

/**
 * Level-up card overlay: pauses the game, shows three randomized upgrade
 * cards with appear/flip/hover/select/disappear animations (design:
 * ROGUELITE LEVEL SYSTEM). Choosing a card resumes play.
 */
class LevelUpScreen(
    context: Context,
    private val level: Int,
    private val offers: List<UpgradeDef>,
    private val onChosen: (String) -> Unit
) : com.towerbound.laststand.ui.Screen(context) {

    private val engine = GameEngine.get()

    init {
        setBackgroundColor(Color.argb(190, 6, 8, 20))

        val col = UiKit.vertical(context, 14f)
        col.gravity = Gravity.CENTER

        val title = UiKit.title(context, context.getString(R.string.level_up_title), 38f)
        title.setTextColor(0xFFFFD54F.toInt())
        col.addView(title, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val hint = UiKit.text(context, context.getString(R.string.level_up_hint), 15f, UiKit.cTextDim, bold = false)
        col.addView(hint, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER

        offers.forEachIndexed { i, def ->
            val card = UpgradeCardView(context, def) {
                onChosen(def.id)
            }
            // staggered entrance
            card.alpha = 0f
            card.translationY = 80f
            card.scaleX = 0.7f; card.scaleY = 0.7f
            card.postDelayed({
                val appear = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 360
                    interpolator = OvershootInterpolator(1.4f)
                    addUpdateListener {
                        val t = it.animatedValue as Float
                        card.alpha = t
                        card.translationY = 80f * (1f - t)
                        card.scaleX = 0.7f + 0.3f * t
                        card.scaleY = 0.7f + 0.3f * t
                    }
                }
                AnimatorSet().apply { play(appear); start() }
            }, 120L + i * 130L)
            row.addView(card, LinearLayout.LayoutParams(UiKit.dp(context, 210f), UiKit.dp(context, 300f)).apply {
                marginEnd = UiKit.dp(context, 14f)
                marginStart = UiKit.dp(context, 14f)
            })
        }
        col.addView(row)

        addView(col, UiKit.params(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    override fun onEnter() {
        engine.stateManager.transition(GameState.LEVEL_UP)
    }

    override fun onExit() {
        if (engine.stateManager.current == GameState.LEVEL_UP) {
            engine.stateManager.transition(GameState.GAMEPLAY)
        }
    }

    override fun onBack(): Boolean = true // must choose a card

    /**
     * A single upgrade card: rarity-colored border + glow, symbol, name,
     * description, stack pips. Press animates select + burst.
     */
    private class UpgradeCardView(
        context: Context,
        val def: UpgradeDef,
        private val onSelect: () -> Unit
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val path = Path()
        private var pressT = 0f
        private var hoverT = 0f
        private var time = 0f
        private var selected = false

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            isClickable = true
            contentDescription = def.name
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressT = 1f; invalidate(); return true }
                MotionEvent.ACTION_UP -> {
                    val inside = event.x >= 0 && event.y >= 0 && event.x <= width && event.y <= height
                    if (inside && !selected) {
                        selected = true
                        // select flash then notify
                        postDelayed({ onSelect() }, 140)
                    }
                    pressT = 0f
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { pressT = 0f; invalidate(); return true }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            time += 0.016f
            val w = width.toFloat()
            val h = height.toFloat()
            val r = UiKit.dp(context, 16f).toFloat()

            val hoverBob = kotlin.math.sin(time * 2.2f + def.id.hashCode() % 7) * 5f * hoverT
            canvas.save()
            canvas.translate(0f, hoverBob)

            val rarityColor = def.rarity.color
            val glow = def.rarity.glowStrength

            // rarity glow aura
            if (glow > 0f) {
                paint.color = Color.argb((70 * glow).toInt(), Color.red(rarityColor), Color.green(rarityColor), Color.blue(rarityColor))
                canvas.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), r + 6f, r + 6f, paint)
            }

            // card body
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
                UiKit.cBgPanelLight, UiKit.cBgPanel, android.graphics.Shader.TileMode.CLAMP)
            rect.set(4f, 4f, w - 4f, h - 4f)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.shader = null

            // border (double line for high rarity)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = rarityColor
            canvas.drawRoundRect(rect, r, r, paint)
            if (def.rarity.ordinal >= 3) {
                paint.strokeWidth = 1.6f
                paint.color = Color.argb(150, 255, 255, 255)
                canvas.drawRoundRect(RectF(10f, 10f, w - 10f, h - 10f), r - 6f, r - 6f, paint)
            }
            paint.style = Paint.Style.FILL

            // top rarity banner
            paint.color = rarityColor
            canvas.drawRoundRect(RectF(4f, 4f, w - 4f, 44f), r, r, paint)
            val bannerText = Paint(paint).apply {
                color = Color.WHITE; textSize = 15f; textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(def.rarity.label.uppercase(), w / 2f, 30f, bannerText)

            // symbol medallion
            val cy = h * 0.36f
            paint.color = Color.argb(30, 255, 255, 255)
            canvas.drawCircle(w / 2f, cy, UiKit.dp(context, 40f).toFloat(), paint)
            paint.color = rarityColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(w / 2f, cy, UiKit.dp(context, 40f).toFloat(), paint)
            paint.style = Paint.Style.FILL

            // name
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = UiKit.cText; textSize = 19f; textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(def.name, w / 2f, h * 0.58f, namePaint)

            // description (simple wrap)
            val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = UiKit.cTextDim; textSize = 14f; textAlign = Paint.Align.CENTER
            }
            val words = def.description.split(" ")
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in words) {
                if (descPaint.measureText(line.toString() + word) > w - 40f) {
                    lines.add(line.toString().trim()); line = StringBuilder()
                }
                line.append(word).append(" ")
            }
            lines.add(line.toString().trim())
            var ly = h * 0.65f
            for (l in lines.take(3)) {
                canvas.drawText(l, w / 2f, ly, descPaint)
                ly += 19f
            }

            // stack pips
            val pipCount = def.maxStacks
            val pipW = 10f
            val totalW = pipCount * (pipW + 4f)
            var px = w / 2f - totalW / 2f + pipW / 2f
            for (i in 0 until pipCount.coerceAtMost(8)) {
                paint.color = if (i < 8) Color.argb(120, 255, 255, 255) else Color.TRANSPARENT
                canvas.drawCircle(px, h - 26f, 3.4f, paint)
                px += pipW + 4f
            }

            // select burst
            if (selected) {
                paint.color = Color.argb((255 * (0.5f + 0.5f * kotlin.math.sin(time * 20f))).toInt(), 255, 255, 255)
                canvas.drawRoundRect(rect, r, r, paint)
            }

            // press squash
            if (pressT > 0f) {
                canvas.scale(1f - pressT * 0.05f, 1f - pressT * 0.05f, w / 2f, h / 2f)
            }

            canvas.restore()
            postInvalidateOnAnimation()
        }
    }
}
