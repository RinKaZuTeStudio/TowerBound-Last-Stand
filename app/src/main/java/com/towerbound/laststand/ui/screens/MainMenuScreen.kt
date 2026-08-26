package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Main menu (design: MAIN MENU): hero quick-select label, best wave, gold /
 * gems balance and the full entry set — PLAY, HEROES, SHOP, QUESTS,
 * COLLECTION, SETTINGS. The PLAY button starts a run with the selected hero.
 */
class MainMenuScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()

    init {
        // translucent scrim so the animated GL world scene (sky, hills,
        // tower, hero, fireflies) shows through behind the menu UI
        applyBackdrop(
            Color.parseColor("#B2070A18"),
            Color.parseColor("#90151B3D"),
            Color.parseColor("#B2070A18")
        )

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 16f), UiKit.dp(context, 26f), UiKit.dp(context, 16f))

        // --- header ---
        root.addView(UiKit.title(context, context.getString(R.string.splash_title), 42f))
        val sub = UiKit.text(context, context.getString(R.string.splash_subtitle), 18f, UiKit.cAccent)
        root.addView(sub)

        val info = UiKit.text(context, bestWaveLabel(), 14f, UiKit.cTextDim, bold = false)
        root.addView(info, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 4f) })

        // --- currencies ---
        val currency = Widgets.currencyBar(context)
        Widgets.updateCurrency(currency, MetaSession.balanceGold(), MetaSession.balanceGems())
        root.addView(currency, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 10f) })

        // --- selected hero ---
        val heroLine = UiKit.text(
            context,
            context.getString(R.string.heroes_title) + ": " + selectedHeroName(),
            13f, UiKit.cText, bold = false
        )
        root.addView(heroLine, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 8f) })

        // --- primary play ---
        val play = GameButton(context, context.getString(R.string.menu_play), GameButton.Style.GOLD)
        play.onButtonClick = {
            val heroId = MetaSession.profile.selectedHero
            (context as? MainActivity)?.let { GameFlow.get().startRun(it, heroId) }
        }
        root.addView(play, UiKit.linearParams(UiKit.dp(context, 220f), UiKit.dp(context, 58f)).apply {
            topMargin = UiKit.dp(context, 14f)
        })

        // --- resume interrupted run (crash recovery, phase 22) ---
        val snapshot = try { GameFlow.get().pendingResume() } catch (_: Throwable) { null }
        if (snapshot != null) {
            val resumeBtn = GameButton(context, context.getString(R.string.menu_resume_run), GameButton.Style.PRIMARY)
            resumeBtn.onButtonClick = {
                val heroId = snapshot.optString("hero", MetaSession.profile.selectedHero)
                (context as? MainActivity)?.let {
                    MetaSession.save.clearRunSnapshot()
                    GameFlow.get().startRun(it, heroId, resume = snapshot)
                }
            }
            root.addView(resumeBtn, UiKit.linearParams(UiKit.dp(context, 220f), UiKit.dp(context, 48f)).apply {
                topMargin = UiKit.dp(context, 8f)
            })
        }

        // --- menu grid ---
        val grid = LinearLayout(context)
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER_HORIZONTAL

        fun menuRow(vararg entries: Pair<String, GameButton.Style>): LinearLayout {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER
            for ((label, style) in entries) {
                val btn = GameButton(context, label, style)
                btn.onButtonClick = { onMenuClick(label) }
                row.addView(btn, LinearLayout.LayoutParams(UiKit.dp(context, 168f), UiKit.dp(context, 46f)).apply {
                    marginEnd = UiKit.dp(context, 10f)
                    marginStart = UiKit.dp(context, 10f)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 8f) })
            return row
        }

        menuRow(
            context.getString(R.string.menu_heroes) to GameButton.Style.PRIMARY,
            context.getString(R.string.menu_gear) to GameButton.Style.PRIMARY
        )
        menuRow(
            context.getString(R.string.menu_shop) to GameButton.Style.SECONDARY,
            context.getString(R.string.menu_collection) to GameButton.Style.SECONDARY
        )
        menuRow(
            context.getString(R.string.quests_title) to GameButton.Style.GHOST,
            context.getString(R.string.achievements_title) to GameButton.Style.GHOST
        )
        menuRow(
            context.getString(R.string.menu_settings) to GameButton.Style.GHOST
        )

        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 12f) })

        addView(root, UiKit.params(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun onMenuClick(label: String) {
        val act = context as? MainActivity ?: return
        when (label) {
            act.getString(R.string.menu_heroes) -> act.screenManager.push(HeroSelectScreen(act))
            act.getString(R.string.menu_shop) -> act.screenManager.push(ShopScreen(act))
            act.getString(R.string.quests_title) -> act.screenManager.push(QuestsScreen(act))
            act.getString(R.string.achievements_title) -> act.screenManager.push(QuestsScreen(act))
            act.getString(R.string.menu_collection) -> act.screenManager.push(CollectionScreen(act))
            act.getString(R.string.menu_gear) -> act.screenManager.push(GearScreen(act))
            act.getString(R.string.menu_settings) -> act.screenManager.push(SettingsScreen(act))
        }
    }

    private fun bestWaveLabel(): String {
        return context.getString(R.string.best_wave_label) + ": " + MetaSession.profile.bestWave +
            "   •   " + MetaSession.formatAmount(MetaSession.profile.totalKills) + " kills"
    }

    private fun selectedHeroName(): String {
        val id = MetaSession.profile.selectedHero
        val progress = MetaSession.profile.heroProgress(id)
        return com.towerbound.laststand.gameplay.Heroes.profile(id).displayName +
            " (Lv.${progress.level})"
    }

    override fun onBack(): Boolean {
        // Top-level screen: let the activity handle exit.
        return false
    }
}
