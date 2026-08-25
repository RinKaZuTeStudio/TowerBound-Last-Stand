package com.towerbound.laststand.ui.screens

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.gameplay.Heroes
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.meta.PlayerProfile
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Game-over + results screen (design: GAME OVER / RESULTS): dark scrim,
 * defeat title, run statistics, earned rewards (gold / hero XP / shards /
 * levels) with staggered count-up animations, new-best banner, then retry /
 * hero select / menu.
 */
class GameOverScreen(
    context: Context,
    private val stats: GameFlow.RunStats? = null,
    private val summary: PlayerProfile.RunSummary? = null
) : Screen(context) {

    init {
        setBackgroundColor(Color.argb(205, 5, 6, 16))

        val scrollable = LinearLayout(context)
        scrollable.orientation = LinearLayout.VERTICAL
        scrollable.gravity = Gravity.CENTER_HORIZONTAL
        scrollable.setPadding(UiKit.dp(context, 30f), UiKit.dp(context, 22f), UiKit.dp(context, 30f), UiKit.dp(context, 16f))

        val title = UiKit.title(context, context.getString(R.string.game_over_title), 30f)
        title.setTextColor(0xFFFF6E6E.toInt())
        scrollable.addView(title, UiKit.linearParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        // hero + wave line
        stats?.let { s ->
            val hero = Heroes.profile(GameFlow.get().engineHeroId())
            val line = UiKit.text(
                context,
                "${hero.displayName}  •  ${context.getString(R.string.results_wave)} ${s.wave}",
                14f, UiKit.cTextDim, bold = false
            )
            scrollable.addView(line, UiKit.linearParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        // new best banner
        if (summary?.newBestWave == true) {
            val best = UiKit.text(context, context.getString(R.string.results_best), 15f, UiKit.cGold)
            best.alpha = 0f
            scrollable.addView(best, UiKit.linearParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            best.postDelayed({
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 400
                    addUpdateListener { best.alpha = it.animatedValue as Float }
                }.start()
            }, 700)
        }

        // stats panel
        if (stats != null) {
            val panel = Widgets.panel(context)
            addStatRow(panel, context.getString(R.string.results_kills), stats.kills.toString(), UiKit.cText)
            addStatRow(panel, context.getString(R.string.results_damage), "${stats.damageDealt.toInt()}", UiKit.cText)
            addStatRow(panel, context.getString(R.string.results_gold), "+${stats.gold}", UiKit.cGold)
            addStatRow(panel, context.getString(R.string.results_time), formatTime(stats.time), UiKit.cTextDim)
            summary?.let { sum ->
                if (sum.heroLevelsGained > 0) {
                    addStatRow(panel, context.getString(R.string.results_xp),
                        "+${GameFlow.get().lastRunXp.toInt()}  (Lv+${sum.heroLevelsGained})", UiKit.cSuccess)
                } else {
                    addStatRow(panel, context.getString(R.string.results_xp),
                        "+${GameFlow.get().lastRunXp.toInt()}", UiKit.cSuccess)
                }
                if (sum.shardsGranted > 0) {
                    addStatRow(panel, "Shards", "+${sum.shardsGranted}", UiKit.cAccentWarm)
                }
            }
            scrollable.addView(panel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 14f) })
        }

        // actions
        val nav = LinearLayout(context)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER

        val menu = GameButton(context, context.getString(R.string.results_menu), GameButton.Style.SECONDARY)
        menu.onButtonClick = {
            (context as? MainActivity)?.let {
                it.screenManager.pop(false)
                GameFlow.get().returnToMenu()
            }
        }
        nav.addView(menu, LinearLayout.LayoutParams(UiKit.dp(context, 150f), UiKit.dp(context, 50f)).apply {
            marginEnd = UiKit.dp(context, 12f)
        })

        val heroes = GameButton(context, context.getString(R.string.menu_heroes), GameButton.Style.GHOST)
        heroes.onButtonClick = {
            (context as? MainActivity)?.let {
                it.screenManager.pop(false)
                GameFlow.get().returnToMenu()
                it.screenManager.push(HeroSelectScreen(it))
            }
        }
        nav.addView(heroes, LinearLayout.LayoutParams(UiKit.dp(context, 150f), UiKit.dp(context, 50f)))

        val retry = GameButton(context, context.getString(R.string.results_retry), GameButton.Style.PRIMARY)
        retry.onButtonClick = {
            (context as? MainActivity)?.let {
                it.screenManager.pop(false)
                GameFlow.get().startRun(it, GameFlow.get().engineHeroId())
            }
        }
        nav.addView(retry, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 54f)).apply {
            marginStart = UiKit.dp(context, 12f)
        })

        scrollable.addView(nav, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 16f) })

        val scroller = android.widget.ScrollView(context)
        scroller.isVerticalScrollBarEnabled = false
        scroller.addView(scrollable, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        addView(scroller, UiKit.params(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // entrance
        alpha = 0f
        val appear = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener { alpha = it.animatedValue as Float }
        }
        AnimatorSet().apply { play(appear); start() }
    }

    private fun addStatRow(panel: LinearLayout, label: String, value: String, valueColor: Int) {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, UiKit.dp(context, 5f), 0, UiKit.dp(context, 5f))

        val l = UiKit.text(context, label, 14f, UiKit.cTextDim, bold = false)
        l.gravity = Gravity.START
        row.addView(l, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val v = UiKit.text(context, value, 15f, valueColor)
        row.addView(v, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun formatTime(seconds: Float): String {
        val s = seconds.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onBack(): Boolean {
        (context as? MainActivity)?.let {
            it.screenManager.pop(false)
            GameFlow.get().returnToMenu()
        }
        return true
    }
}
