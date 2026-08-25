package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.assets.AssetRepository
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.gameplay.Heroes
import com.towerbound.laststand.meta.GameSettings
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.meta.PlayerProfile
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Hero selection screen (design: HERO SYSTEM — 8 heroes, unlock via shards
 * or gold, per-hero level shown, selected hero drives the next run).
 * Grid of portrait cards; locked heroes show shard progress and an unlock
 * button (shards primary, gold fallback).
 */
class HeroSelectScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()
    private lateinit var cardsRow1: LinearLayout
    private lateinit var cardsRow2: LinearLayout
    private var detailPanel: LinearLayout? = null
    private var selectedHero: String = MetaSession.profile.selectedHero

    init {
        applyBackdrop(Color.parseColor("#070A18"), Color.parseColor("#131a38"), Color.parseColor("#070A18"))

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 20f), UiKit.dp(context, 26f), UiKit.dp(context, 12f))

        // header: title + currencies
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = UiKit.title(context, context.getString(R.string.heroes_title), 30f)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val currency = Widgets.currencyBar(context)
        header.addView(currency)
        Widgets.updateCurrency(currency, MetaSession.balanceGold(), MetaSession.balanceGems())
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // hero grid (2 rows x 4)
        cardsRow1 = heroRow()
        cardsRow2 = heroRow()
        root.addView(cardsRow1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(cardsRow2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // detail panel (selected hero info + actions)
        detailPanel = Widgets.panel(context)
        root.addView(detailPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 10f) })

        // bottom actions
        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        val back = GameButton(context, context.getString(R.string.close), GameButton.Style.SECONDARY)
        back.onButtonClick = { popToMenu() }
        actions.addView(back, LinearLayout.LayoutParams(UiKit.dp(context, 130f), UiKit.dp(context, 50f)).apply {
            marginEnd = UiKit.dp(context, 12f)
        })
        val play = GameButton(context, context.getString(R.string.menu_play), GameButton.Style.GOLD)
        play.onButtonClick = {
            MetaSession.profile.selectedHero = selectedHero
            MetaSession.persist()
            (context as? MainActivity)?.let { GameFlow.get().startRun(it, selectedHero) }
        }
        actions.addView(play, LinearLayout.LayoutParams(UiKit.dp(context, 180f), UiKit.dp(context, 54f)))
        root.addView(actions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 12f) })

        addView(Widgets.scroll(context, root), UiKit.params(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        buildCards()
        refreshDetail()
    }

    override fun onEnter() {
        engine.stateManager.let {
            if (it.current == GameState.MAIN_MENU) it.transition(GameState.HERO_SELECT)
        }
    }

    override fun onExit() {
        if (engine.stateManager.current == GameState.HERO_SELECT) {
            engine.stateManager.transition(GameState.MAIN_MENU)
        }
    }

    override fun onBack(): Boolean {
        popToMenu()
        return true
    }

    private fun popToMenu() {
        (context as? MainActivity)?.screenManager?.pop(false)
    }

    private fun heroRow(): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(0, UiKit.dp(context, 8f), 0, 0)
        return row
    }

    private fun buildCards() {
        cardsRow1.removeAllViews()
        cardsRow2.removeAllViews()
        val ids = Heroes.allIds()
        ids.take(4).forEach { addCard(cardsRow1, it) }
        ids.drop(4).forEach { addCard(cardsRow2, it) }
    }

    private fun addCard(row: LinearLayout, heroId: String) {
        val profile = MetaSession.profile.heroProgress(heroId)
        val hero = Heroes.profile(heroId)
        val isSelected = heroId == selectedHero

        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(UiKit.dp(context, 6f), UiKit.dp(context, 8f), UiKit.dp(context, 6f), UiKit.dp(context, 6f))
        card.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (isSelected) Color.argb(80, 124, 77, 255) else UiKit.cBgPanel)
            cornerRadius = UiKit.dp(context, 12f).toFloat()
            setStroke(
                UiKit.dp(context, if (isSelected) 2f else 1f),
                if (isSelected) UiKit.cAccent else if (!profile.unlocked) Color.argb(60, 255, 255, 255) else UiKit.cPrimary
            )
        }
        card.isClickable = true
        card.setOnClickListener {
            if (profile.unlocked) {
                selectedHero = heroId
                MetaSession.profile.selectedHero = heroId
                buildCards()
                refreshDetail()
            } else {
                refreshDetail(heroId)
            }
        }
        card.setTag(R.id.tag_hero_id, heroId)

        // portrait
        val portrait = Widgets.PortraitView(context)
        portrait.bitmap = AssetRepository.portrait(heroId)
        portrait.ringColor = when {
            !profile.unlocked -> Color.argb(90, 120, 120, 130)
            isSelected -> UiKit.cAccent
            else -> UiKit.cPrimary
        }
        card.addView(portrait, LinearLayout.LayoutParams(UiKit.dp(context, 72f), UiKit.dp(context, 72f)))

        // name
        val name = UiKit.text(context, hero.displayName, 13f, if (profile.unlocked) UiKit.cText else UiKit.cTextDim)
        card.addView(name)

        // level or lock
        val status = UiKit.text(
            context,
            if (profile.unlocked) context.getString(R.string.hero_level, profile.level)
            else context.getString(R.string.hero_locked),
            11f, if (profile.unlocked) UiKit.cAccentWarm else Color.parseColor("#8A8FA3"),
            bold = false
        )
        card.addView(status)

        row.addView(card, LinearLayout.LayoutParams(UiKit.dp(context, 84f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = UiKit.dp(context, 6f)
        })
    }

    /** Refresh the detail panel; null shows the selected hero. */
    private fun refreshDetail(focusHeroId: String? = null) {
        val panel = detailPanel ?: return
        panel.removeAllViews()
        val heroId = focusHeroId ?: selectedHero
        val hero = Heroes.profile(heroId)
        val progress = MetaSession.profile.heroProgress(heroId)

        val title = UiKit.text(context, hero.displayName + " — " + hero.role, 16f, UiKit.cAccent)
        panel.addView(title)

        if (progress.unlocked) {
            val info = UiKit.text(
                context,
                context.getString(R.string.hero_level, progress.level) + "   •   " + hero.element.uppercase() +
                    if (hero.ranged) "   •   RANGED" else "   •   MELEE",
                13f, UiKit.cTextDim, bold = false
            )
            panel.addView(info)

            // xp bar
            val barRow = LinearLayout(context)
            barRow.orientation = LinearLayout.VERTICAL
            val bar = Widgets.BarView(context)
            bar.fraction = (progress.xp.toFloat() / progress.xpToNext).coerceIn(0f, 1f)
            bar.fillColor = UiKit.cSuccess
            barRow.addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 10f)
            ))
            panel.addView(barRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 8f) })

            if (heroId != selectedHero) {
                val select = GameButton(context, context.getString(R.string.hero_select), GameButton.Style.PRIMARY)
                select.onButtonClick = {
                    selectedHero = heroId
                    MetaSession.profile.selectedHero = heroId
                    buildCards()
                    refreshDetail()
                }
                panel.addView(select, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 44f)).apply {
                    topMargin = UiKit.dp(context, 8f)
                })
            } else {
                val sel = UiKit.text(context, context.getString(R.string.hero_selected), 13f, UiKit.cSuccess)
                panel.addView(sel, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dp(context, 8f) })
            }
        } else {
            // locked: shard progress + unlock buttons
            val shards = MetaSession.profile.inventory.count("shard_$heroId")
            val shardText = UiKit.text(
                context,
                context.getString(R.string.hero_shards, shards, PlayerProgress.UNLOCK_SHARDS),
                13f, UiKit.cTextDim, bold = false
            )
            panel.addView(shardText)

            val bar = Widgets.BarView(context)
            bar.fraction = (shards.toFloat() / PlayerProgress.UNLOCK_SHARDS).coerceIn(0f, 1f)
            bar.fillColor = UiKit.cAccentWarm
            panel.addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 10f)
            ).apply { topMargin = UiKit.dp(context, 6f) })

            val actions = LinearLayout(context)
            actions.orientation = LinearLayout.HORIZONTAL
            actions.gravity = Gravity.CENTER

            val unlockShards = GameButton(context, context.getString(R.string.hero_unlock), GameButton.Style.PRIMARY)
            unlockShards.enabledState = shards >= PlayerProgress.UNLOCK_SHARDS
            unlockShards.onButtonClick = {
                if (MetaSession.profile.unlockHero(heroId)) {
                    MetaSession.persist()
                    if (selectedHero == "ARIC" || MetaSession.profile.heroProgress(selectedHero).unlocked.not()) {
                        // keep current selection valid
                    }
                    buildCards()
                    refreshDetail(heroId)
                }
            }
            actions.addView(unlockShards, LinearLayout.LayoutParams(UiKit.dp(context, 140f), UiKit.dp(context, 44f)).apply {
                marginEnd = UiKit.dp(context, 10f)
            })

            val unlockGold = GameButton(
                context,
                context.getString(R.string.buy) + " " + Widgets.formatGold(PlayerProgress.UNLOCK_GOLD) + " " + context.getString(R.string.gold),
                GameButton.Style.GOLD
            )
            unlockGold.enabledState = MetaSession.balanceGold() >= PlayerProgress.UNLOCK_GOLD
            unlockGold.onButtonClick = {
                if (MetaSession.profile.unlockHeroWithGold(heroId)) {
                    MetaSession.persist()
                    buildCards()
                    refreshDetail(heroId)
                }
            }
            actions.addView(unlockGold, LinearLayout.LayoutParams(UiKit.dp(context, 180f), UiKit.dp(context, 44f)))

            panel.addView(actions, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 8f) })
        }
    }
}

/** Alias so call sites read naturally. */
private typealias PlayerProgress = com.towerbound.laststand.meta.HeroProgress
