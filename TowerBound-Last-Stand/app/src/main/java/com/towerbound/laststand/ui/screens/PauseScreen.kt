package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit

/**
 * Pause overlay (design: PAUSE): resume / restart / settings / quit. Pushes
 * the game state to PAUSED (sim frozen by GameState.simulates == false) and
 * persists meta data so nothing is lost if the player backgrounds the app.
 */
class PauseScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()

    init {
        setBackgroundColor(Color.argb(205, 5, 6, 16))

        val col = UiKit.vertical(context, 12f)
        col.gravity = Gravity.CENTER

        col.addView(UiKit.title(context, context.getString(R.string.pause_title), 34f))

        val resume = GameButton(context, context.getString(R.string.pause_resume), GameButton.Style.PRIMARY)
        resume.onButtonClick = { close() }
        col.addView(resume, UiKit.linearParams(UiKit.dp(context, 210f), UiKit.dp(context, 54f)))

        val settings = GameButton(context, context.getString(R.string.settings_title), GameButton.Style.SECONDARY)
        settings.onButtonClick = {
            (context as? MainActivity)?.screenManager?.push(SettingsScreen(context))
        }
        col.addView(settings, UiKit.linearParams(UiKit.dp(context, 210f), UiKit.dp(context, 48f)))

        val restart = GameButton(context, context.getString(R.string.pause_restart), GameButton.Style.DANGER)
        restart.onButtonClick = {
            (context as? MainActivity)?.let { act ->
                act.screenManager.pop(false)
                GameFlow.get().startRun(act, GameFlow.get().engineHeroId())
            }
        }
        col.addView(restart, UiKit.linearParams(UiKit.dp(context, 210f), UiKit.dp(context, 48f)))

        val quit = GameButton(context, context.getString(R.string.pause_quit), GameButton.Style.GHOST)
        quit.onButtonClick = {
            // bank current run progress before leaving
            MetaSession.persist()
            (context as? MainActivity)?.screenManager?.pop(false)
            GameFlow.get().returnToMenu()
        }
        col.addView(quit, UiKit.linearParams(UiKit.dp(context, 210f), UiKit.dp(context, 48f)))

        addView(col, UiKit.params(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun close() {
        (context as? MainActivity)?.screenManager?.pop(false)
    }

    override fun onEnter() {
        when (engine.stateManager.current) {
            GameState.GAMEPLAY, GameState.BOSS_FIGHT, GameState.LEVEL_UP ->
                engine.stateManager.transition(GameState.PAUSED)
            else -> Unit
        }
    }

    override fun onExit() {
        if (engine.stateManager.current == GameState.PAUSED) {
            // restore the exact pre-pause state (gameplay / boss / level-up)
            engine.stateManager.returnToPrevious()
        }
    }

    override fun onBack(): Boolean {
        close()
        return true
    }
}
