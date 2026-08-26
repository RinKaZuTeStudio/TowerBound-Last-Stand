package com.towerbound.laststand.ui.screens

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit

/**
 * First-run tutorial (design: TUTORIAL): 7-step carousel covering movement,
 * auto-attack, abilities, tower defense, waves, XP orbs and upgrade cards.
 * Runs over the menu (first run) or from settings replay; completion marks
 * profile.tutorialDone and persists.
 */
class TutorialOverlay(
    context: Context,
    private val onDone: () -> Unit
) : Screen(context) {

    private val engine = GameEngine.get()

    private data class Step(val textRes: Int, val accent: Int)

    private val steps = listOf(
        Step(R.string.tutorial_move, UiKit.cAccent),
        Step(R.string.tutorial_attack, UiKit.cSuccess),
        Step(R.string.tutorial_abilities, UiKit.cPrimary),
        Step(R.string.tutorial_tower, UiKit.cDanger),
        Step(R.string.tutorial_waves, UiKit.cAccentWarm),
        Step(R.string.tutorial_xp, UiKit.cGold),
        Step(R.string.tutorial_upgrades, Color.parseColor("#FF8A65"))
    )

    private var index = 0
    private val pager = LinearLayout(context)
    private val body = UiKit.text(context, "", 17f, UiKit.cText, bold = false)
    private val icon = StepIcon(context)
    private val nextLabel: String = context.getString(R.string.tutorial_next)
    private lateinit var next: GameButton

    init {
        setBackgroundColor(Color.argb(215, 5, 6, 16))

        val col = UiKit.vertical(context, 16f)
        col.gravity = Gravity.CENTER

        col.addView(UiKit.title(context, context.getString(R.string.tutorial_title), 30f))

        // animated step icon
        icon.setColor(UiKit.cAccent)
        col.addView(icon, UiKit.linearParams(UiKit.dp(context, 96f), UiKit.dp(context, 96f)))

        body.gravity = Gravity.CENTER
        body.setPadding(UiKit.dp(context, 20f), 0, UiKit.dp(context, 20f), 0)
        col.addView(body, UiKit.linearParams(
            UiKit.dp(context, 460f), FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        // pager dots
        pager.orientation = LinearLayout.HORIZONTAL
        pager.gravity = Gravity.CENTER
        for (i in steps.indices) {
            val dot = View(context)
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (i == 0) UiKit.cAccent else Color.argb(70, 255, 255, 255))
                shape = android.graphics.drawable.GradientDrawable.OVAL
            }
            pager.addView(dot, LinearLayout.LayoutParams(UiKit.dp(context, 9f), UiKit.dp(context, 9f)).apply {
                marginEnd = UiKit.dp(context, 6f)
            })
        }
        col.addView(pager, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        // nav buttons
        val nav = LinearLayout(context)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER

        val skip = GameButton(context, context.getString(R.string.tutorial_skip), GameButton.Style.GHOST)
        skip.onButtonClick = { finish() }
        nav.addView(skip, LinearLayout.LayoutParams(UiKit.dp(context, 120f), UiKit.dp(context, 46f)).apply {
            marginEnd = UiKit.dp(context, 16f)
        })

        nextLabel.let { }
        next = GameButton(context, nextLabel, GameButton.Style.PRIMARY)
        next.onButtonClick = {
            if (index >= steps.size - 1) finish() else showStep(index + 1)
        }
        nav.addView(next, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 50f)))

        col.addView(nav, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        addView(col, UiKit.params(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        showStep(0)
    }

    private fun showStep(i: Int) {
        index = i.coerceIn(0, steps.size - 1)
        val step = steps[index]
        body.text = context.getString(step.textRes)
        icon.setColor(step.accent)

        // pager refresh
        for (d in 0 until pager.childCount) {
            val dot = pager.getChildAt(d)
            (dot.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                if (d == index) step.accent else Color.argb(70, 255, 255, 255)
            )
        }
        next.label = if (index >= steps.size - 1) context.getString(R.string.tutorial_done) else nextLabel
        next.contentDescription = next.label

        // entrance pop
        icon.scaleX = 0.6f; icon.scaleY = 0.6f
        icon.alpha = 0.4f
        val pop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener {
                val t = it.animatedValue as Float
                icon.scaleX = 0.6f + 0.4f * t
                icon.scaleY = 0.6f + 0.4f * t
                icon.alpha = 0.4f + 0.6f * t
            }
        }
        AnimatorSet().apply { play(pop); start() }
    }

    private fun finish() {
        com.towerbound.laststand.meta.MetaSession.profile.tutorialDone = true
        com.towerbound.laststand.meta.MetaSession.persist()
        onDone()
    }

    override fun onEnter() {
        // freeze the simulation while the tutorial is up (first run only;
        // replay-from-settings runs over the menu where nothing simulates)
        when (engine.stateManager.current) {
            GameState.GAMEPLAY, GameState.BOSS_FIGHT, GameState.LEVEL_UP ->
                engine.stateManager.transition(GameState.PAUSED)
            else -> Unit
        }
    }

    override fun onExit() {
        if (engine.stateManager.current == GameState.PAUSED) {
            engine.stateManager.returnToPrevious()
        }
    }

    override fun onBack(): Boolean {
        if (index > 0) showStep(index - 1) else finish()
        return true
    }

    /** Pulsing ring + shield glyph placeholder for each tutorial step. */
    private class StepIcon(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var accent = UiKit.cAccent
        private var time = 0f

        fun setColor(c: Int) { accent = c }

        override fun onDraw(canvas: Canvas) {
            time += 0.016f
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - 8f

            // pulse rings
            for (k in 0 until 3) {
                val phase = (time * 0.5f + k / 3f) % 1f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * (1f - phase)
                paint.color = Color.argb((140 * (1f - phase)).toInt(),
                    Color.red(accent), Color.green(accent), Color.blue(accent))
                canvas.drawCircle(cx, cy, r * (0.55f + 0.45f * phase), paint)
            }

            // core disc
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(cx, cy, r * 0.55f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = accent
            canvas.drawCircle(cx, cy, r * 0.55f, paint)

            // orbiting spark
            val ang = time * 1.6f
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx + (r * 0.8f) * kotlin.math.cos(ang), cy + (r * 0.8f) * kotlin.math.sin(ang), 5f, paint)

            postInvalidateOnAnimation()
        }
    }
}
