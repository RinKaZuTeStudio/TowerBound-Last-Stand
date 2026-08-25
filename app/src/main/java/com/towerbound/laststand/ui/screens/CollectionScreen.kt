package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.gameplay.Heroes
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.meta.Skins
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Collection screen (design: INVENTORY/COSMETICS): per-hero skins with
 * rarity tint swatches, equip/unequip, and wave-unlock status. Owned skins
 * equip through the inventory; wave-gated skins show their requirement.
 */
class CollectionScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()

    init {
        applyBackdrop(Color.parseColor("#070A18"), Color.parseColor("#111834"), Color.parseColor("#070A18"))

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 18f), UiKit.dp(context, 26f), UiKit.dp(context, 12f))

        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = UiKit.title(context, context.getString(R.string.collection_title), 30f)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // per-hero sections
        for (heroId in Heroes.allIds()) {
            val hero = Heroes.profile(heroId)
            val unlocked = MetaSession.profile.heroProgress(heroId).unlocked
            val panel = Widgets.panel(context)

            val head = UiKit.text(
                context,
                hero.displayName + if (unlocked) "" else "  (${context.getString(R.string.hero_locked)})",
                16f, if (unlocked) UiKit.cAccent else UiKit.cTextDim
            )
            head.gravity = Gravity.START
            panel.addView(head)

            for (skin in Skins.forHero(heroId)) {
                if (skin.id == "default") continue
                val owned = MetaSession.profile.inventory.ownsSkin(skin.id)
                val equipped = MetaSession.profile.inventory.equippedSkinFor(heroId).id == skin.id
                val waveOk = MetaSession.profile.bestWave >= skin.unlockWave

                val row = LinearLayout(context)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))

                // tint swatch
                val swatch = android.view.View(context)
                swatch.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.argb(255,
                        (skin.tint[0] * 180).toInt().coerceIn(0, 255),
                        (skin.tint[1] * 180).toInt().coerceIn(0, 255),
                        (skin.tint[2] * 180).toInt().coerceIn(0, 255)))
                    cornerRadius = UiKit.dp(context, 8f).toFloat()
                    setStroke(UiKit.dp(context, 2f), skin.rarity.color)
                }
                row.addView(swatch, LinearLayout.LayoutParams(UiKit.dp(context, 28f), UiKit.dp(context, 28f)).apply {
                    marginEnd = UiKit.dp(context, 10f)
                })

                val labelCol = LinearLayout(context)
                labelCol.orientation = LinearLayout.VERTICAL
                val name = UiKit.text(context, "${skin.name} (${skin.rarity.label})", 14f, UiKit.cText, bold = false)
                name.gravity = Gravity.START
                labelCol.addView(name)
                val status = UiKit.text(
                    context,
                    when {
                        owned -> ""
                        skin.unlockWave > 0 -> "Reach wave ${skin.unlockWave}"
                        else -> context.getString(R.string.shop_cosmetics)
                    },
                    11f, UiKit.cTextDim, bold = false
                )
                status.gravity = Gravity.START
                labelCol.addView(status)
                row.addView(labelCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                when {
                    equipped -> {
                        val eq = UiKit.text(context, context.getString(R.string.equipped), 12f, UiKit.cSuccess)
                        row.addView(eq, LinearLayout.LayoutParams(UiKit.dp(context, 90f), LinearLayout.LayoutParams.WRAP_CONTENT))
                    }
                    owned -> {
                        val equip = GameButton(context, context.getString(R.string.equip), GameButton.Style.PRIMARY)
                        equip.onButtonClick = {
                            MetaSession.profile.inventory.equipSkin(heroId, skin.id)
                            MetaSession.persist()
                            rebuildScreen(root, header)
                        }
                        row.addView(equip, LinearLayout.LayoutParams(UiKit.dp(context, 110f), UiKit.dp(context, 40f)))
                    }
                    else -> {
                        val lock = UiKit.text(
                            context,
                            if (skin.unlockWave > 0) "W${skin.unlockWave}" else context.getString(R.string.hero_locked),
                            12f, UiKit.cTextDim
                        )
                        row.addView(lock, LinearLayout.LayoutParams(UiKit.dp(context, 90f), LinearLayout.LayoutParams.WRAP_CONTENT))
                    }
                }
                panel.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }

            root.addView(panel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 12f) })
        }

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
    }

    /** Rebuild the whole screen after an equip action. */
    private fun rebuildScreen(root: LinearLayout, header: LinearLayout) {
        (context as? MainActivity)?.screenManager?.let { mgr ->
            mgr.replace(CollectionScreen(context), animated = false)
        }
    }

    override fun onBack(): Boolean {
        (context as? MainActivity)?.screenManager?.pop(false)
        return true
    }
}
