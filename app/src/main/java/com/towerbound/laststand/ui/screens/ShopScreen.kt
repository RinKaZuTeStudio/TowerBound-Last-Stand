package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.gameplay.Heroes
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.meta.Skins
import com.towerbound.laststand.meta.TowerUpgrades
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Shop screen (design: ECONOMY/SHOP): daily free reward, hero unlocks with
 * gold, permanent tower upgrades and cosmetics (skins). All purchases route
 * through PlayerProfile validation + persist immediately.
 */
class ShopScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()
    private lateinit var body: LinearLayout
    private lateinit var currencyBar: LinearLayout

    init {
        applyBackdrop(Color.parseColor("#070A18"), Color.parseColor("#121935"), Color.parseColor("#070A18"))

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 18f), UiKit.dp(context, 26f), UiKit.dp(context, 12f))

        // header
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = UiKit.title(context, context.getString(R.string.shop_title), 30f)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        currencyBar = Widgets.currencyBar(context)
        header.addView(currencyBar)
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val close = GameButton(context, context.getString(R.string.close), GameButton.Style.SECONDARY)
        close.onButtonClick = {
            (context as? MainActivity)?.screenManager?.pop(false)
        }
        root.addView(close, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 48f)).apply {
            topMargin = UiKit.dp(context, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        addView(Widgets.scroll(context, root), UiKit.params(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        rebuild()
    }

    private fun rebuild() {
        body.removeAllViews()
        Widgets.updateCurrency(currencyBar, MetaSession.balanceGold(), MetaSession.balanceGems())

        buildDailySection()
        buildHeroSection()
        buildUpgradeSection()
        buildCosmeticsSection()
    }

    /* ---------------- daily free reward ---------------- */

    private fun buildDailySection() {
        val panel = Widgets.panel(context)
        panel.addView(Widgets.sectionHeader(context, context.getString(R.string.shop_daily), UiKit.cAccentWarm))

        val available = MetaSession.dailyRewardAvailable()
        val info = UiKit.text(
            context,
            if (available) "+120 ${context.getString(R.string.gold)}  +1 ${context.getString(R.string.gems)}"
            else context.getString(R.string.shop_claimed),
            13f, UiKit.cTextDim, bold = false
        )
        panel.addView(info)

        val claim = GameButton(
            context,
            if (available) context.getString(R.string.shop_claim) else context.getString(R.string.shop_claimed),
            GameButton.Style.GOLD
        )
        claim.enabledState = available
        claim.onButtonClick = {
            MetaSession.claimDailyReward()?.let { rebuild() }
        }
        panel.addView(claim, LinearLayout.LayoutParams(UiKit.dp(context, 150f), UiKit.dp(context, 44f)).apply {
            topMargin = UiKit.dp(context, 6f)
        })

        body.addView(panel, sectionParams())
    }

    /* ---------------- hero unlocks ---------------- */

    private fun buildHeroSection() {
        val panel = Widgets.panel(context)
        panel.addView(Widgets.sectionHeader(context, context.getString(R.string.shop_heroes)))

        for (heroId in Heroes.allIds()) {
            val progress = MetaSession.profile.heroProgress(heroId)
            if (progress.unlocked) continue
            val hero = Heroes.profile(heroId)

            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))

            val label = UiKit.text(context, "${hero.displayName} — ${hero.role}", 14f, UiKit.cText, bold = false)
            label.gravity = Gravity.START
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val cost = com.towerbound.laststand.meta.HeroProgress.UNLOCK_GOLD
            val buy = GameButton(
                context,
                "${Widgets.formatGold(cost)} ${context.getString(R.string.gold)}",
                GameButton.Style.PRIMARY
            )
            buy.enabledState = MetaSession.balanceGold() >= cost
            buy.onButtonClick = {
                if (MetaSession.profile.unlockHeroWithGold(heroId)) {
                    MetaSession.persist()
                    rebuild()
                }
            }
            row.addView(buy, LinearLayout.LayoutParams(UiKit.dp(context, 150f), UiKit.dp(context, 42f)))
            panel.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        if (panel.childCount == 1) {
            val all = UiKit.text(context, "All heroes unlocked", 13f, UiKit.cSuccess, bold = false)
            panel.addView(all)
        }

        body.addView(panel, sectionParams())
    }

    /* ---------------- permanent tower upgrades ---------------- */

    private fun buildUpgradeSection() {
        val panel = Widgets.panel(context)
        panel.addView(Widgets.sectionHeader(context, context.getString(R.string.shop_upgrades)))

        for (kind in TowerUpgrades.Kind.entries) {
            val level = MetaSession.profile.towerLevels[kind] ?: 0
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))

            val labelCol = LinearLayout(context)
            labelCol.orientation = LinearLayout.VERTICAL
            val name = UiKit.text(context, "${kind.label}  Lv.$level/${TowerUpgrades.MAX_LEVEL}", 14f, UiKit.cText, bold = false)
            name.gravity = Gravity.START
            labelCol.addView(name)
            val effect = UiKit.text(
                context,
                if (level > 0) "+${kind.perLevel * level}" else "+${kind.perLevel} per level",
                11f, UiKit.cTextDim, bold = false
            )
            effect.gravity = Gravity.START
            labelCol.addView(effect)
            row.addView(labelCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (level >= TowerUpgrades.MAX_LEVEL) {
                val max = UiKit.text(context, context.getString(R.string.upgrade_max), 13f, UiKit.cSuccess)
                row.addView(max, LinearLayout.LayoutParams(UiKit.dp(context, 70f), LinearLayout.LayoutParams.WRAP_CONTENT))
            } else {
                val cost = TowerUpgrades.cost(kind, level)
                val buy = GameButton(
                    context,
                    "${Widgets.formatGold(cost.toLong())} ${context.getString(R.string.gold)}",
                    GameButton.Style.PRIMARY
                )
                buy.enabledState = MetaSession.balanceGold() >= cost
                buy.onButtonClick = {
                    if (MetaSession.profile.purchaseTowerUpgrade(kind)) {
                        MetaSession.persist()
                        rebuild()
                    }
                }
                row.addView(buy, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 42f)))
            }
            panel.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        body.addView(panel, sectionParams())
    }

    /* ---------------- cosmetics ---------------- */

    private fun buildCosmeticsSection() {
        val panel = Widgets.panel(context)
        panel.addView(Widgets.sectionHeader(context, context.getString(R.string.shop_cosmetics)))

        val purchasable = Skins.all.filter { it.unlockGold > 0 && it.heroId.isNotEmpty() }
        for (skin in purchasable) {
            val owned = MetaSession.profile.inventory.ownsSkin(skin.id)
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))

            val swatch = android.view.View(context)
            swatch.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(255,
                    (skin.tint[0] * 180).toInt().coerceIn(0, 255),
                    (skin.tint[1] * 180).toInt().coerceIn(0, 255),
                    (skin.tint[2] * 180).toInt().coerceIn(0, 255)))
                cornerRadius = UiKit.dp(context, 8f).toFloat()
                setStroke(UiKit.dp(context, 2f), skin.rarity.color)
            }
            row.addView(swatch, LinearLayout.LayoutParams(UiKit.dp(context, 30f), UiKit.dp(context, 30f)).apply {
                marginEnd = UiKit.dp(context, 10f)
            })

            val label = UiKit.text(context, "${skin.name} (${skin.rarity.label})", 14f, UiKit.cText, bold = false)
            label.gravity = Gravity.START
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (owned) {
                val own = UiKit.text(context, context.getString(R.string.equipped_owns), 12f, UiKit.cSuccess)
                row.addView(own, LinearLayout.LayoutParams(UiKit.dp(context, 80f), LinearLayout.LayoutParams.WRAP_CONTENT))
            } else {
                val buy = GameButton(
                    context,
                    "${Widgets.formatGold(skin.unlockGold.toLong())} ${context.getString(R.string.gold)}",
                    GameButton.Style.PRIMARY
                )
                buy.enabledState = MetaSession.balanceGold() >= skin.unlockGold
                buy.onButtonClick = {
                    if (MetaSession.profile.purchaseSkin(skin)) {
                        MetaSession.persist()
                        rebuild()
                    }
                }
                row.addView(buy, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 42f)))
            }
            panel.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val note = UiKit.text(context, "Wave-unlock skins appear in COLLECTION", 11f, UiKit.cTextDim, bold = false)
        panel.addView(note)
        body.addView(panel, sectionParams())
    }

    private fun sectionParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UiKit.dp(context, 12f)
        return lp
    }

    override fun onBack(): Boolean {
        (context as? MainActivity)?.screenManager?.pop(false)
        return true
    }
}
