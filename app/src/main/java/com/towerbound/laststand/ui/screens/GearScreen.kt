package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.meta.GearInstance
import com.towerbound.laststand.meta.GearSlot
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

class GearScreen(context: Context) : Screen(context) {

    init {
        applyBackdrop(Color.parseColor("#C4070A18"), Color.parseColor("#A0111834"), Color.parseColor("#C4070A18"))
        
        val scrollable = LinearLayout(context)
        scrollable.orientation = LinearLayout.VERTICAL
        scrollable.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 18f), UiKit.dp(context, 26f), UiKit.dp(context, 12f))
        
        val title = UiKit.title(context, context.getString(R.string.menu_gear), 30f)
        scrollable.addView(title)
        
        val inv = MetaSession.profile.inventory

        // --- Equipped Loadout ---
        val equippedPanel = Widgets.panel(context)
        val eqTitle = UiKit.text(context, "EQUIPPED GEAR", 16f, UiKit.cAccent)
        eqTitle.gravity = Gravity.START
        equippedPanel.addView(eqTitle)
        
        val slots = GearSlot.entries.toTypedArray()
        for (slot in slots) {
            val slotRow = LinearLayout(context)
            slotRow.orientation = LinearLayout.HORIZONTAL
            slotRow.gravity = Gravity.CENTER_VERTICAL
            slotRow.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))
            
            val slotLabel = UiKit.text(context, slot.label, 14f, UiKit.cTextDim)
            slotLabel.gravity = Gravity.START
            slotRow.addView(slotLabel, LinearLayout.LayoutParams(UiKit.dp(context, 80f), LinearLayout.LayoutParams.WRAP_CONTENT))
            
            val equippedId = inv.gearLoadout[slot.name]
            if (equippedId != null) {
                val g = inv.gear.find { it.id == equippedId }
                if (g != null) {
                    val gearInfo = LinearLayout(context)
                    gearInfo.orientation = LinearLayout.VERTICAL
                    
                    val name = UiKit.text(context, "${g.rarity.label} ${g.type.name} Lv.${g.level}", 14f, g.rarity.color)
                    name.gravity = Gravity.START
                    gearInfo.addView(name)
                    
                    val stats = UiKit.text(context, formatStats(g), 11f, UiKit.cText)
                    stats.gravity = Gravity.START
                    gearInfo.addView(stats)
                    
                    slotRow.addView(gearInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    
                    val unequip = GameButton(context, context.getString(R.string.unequip), GameButton.Style.SECONDARY)
                    unequip.onButtonClick = {
                        inv.unequipGear(slot.name)
                        MetaSession.persist()
                        rebuild()
                    }
                    slotRow.addView(unequip, LinearLayout.LayoutParams(UiKit.dp(context, 80f), UiKit.dp(context, 40f)).apply { marginEnd = UiKit.dp(context, 8f) })

                    val upgrade = GameButton(context, "Upgr (${g.getUpgradeCost()})", GameButton.Style.GOLD)
                    upgrade.onButtonClick = {
                        if (inv.upgradeGear(g.id)) {
                            MetaSession.persist()
                            rebuild()
                        }
                    }
                    slotRow.addView(upgrade, LinearLayout.LayoutParams(UiKit.dp(context, 90f), UiKit.dp(context, 40f)))
                }
            } else {
                val empty = UiKit.text(context, "Empty", 14f, UiKit.cTextDim)
                empty.gravity = Gravity.START
                slotRow.addView(empty, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            equippedPanel.addView(slotRow)
        }
        scrollable.addView(equippedPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 12f) })

        // --- Inventory ---
        val invPanel = Widgets.panel(context)
        val invTitle = UiKit.text(context, "INVENTORY", 16f, UiKit.cAccent)
        invTitle.gravity = Gravity.START
        invPanel.addView(invTitle)

        val unequippedGear = inv.gear.filter { !inv.gearLoadout.containsValue(it.id) }
        
        if (unequippedGear.isEmpty()) {
            val empty = UiKit.text(context, "No unequipped gear.", 14f, UiKit.cTextDim)
            empty.gravity = Gravity.START
            invPanel.addView(empty, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 8f) })
        } else {
            for (g in unequippedGear) {
                val row = LinearLayout(context)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, UiKit.dp(context, 8f), 0, UiKit.dp(context, 8f))
                
                val gearInfo = LinearLayout(context)
                gearInfo.orientation = LinearLayout.VERTICAL
                
                val name = UiKit.text(context, "${g.rarity.label} ${g.type.name} Lv.${g.level}", 14f, g.rarity.color)
                name.gravity = Gravity.START
                gearInfo.addView(name)
                
                val stats = UiKit.text(context, formatStats(g), 11f, UiKit.cText)
                stats.gravity = Gravity.START
                gearInfo.addView(stats)
                
                row.addView(gearInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                
                val equip = GameButton(context, context.getString(R.string.equip), GameButton.Style.PRIMARY)
                equip.onButtonClick = {
                    inv.equipGear(g.id)
                    MetaSession.persist()
                    rebuild()
                }
                row.addView(equip, LinearLayout.LayoutParams(UiKit.dp(context, 80f), UiKit.dp(context, 40f)).apply { marginEnd = UiKit.dp(context, 8f) })
                
                val sell = GameButton(context, "Sell", GameButton.Style.SECONDARY)
                sell.onButtonClick = {
                    val value = inv.sellGear(g.id)
                    if (value > 0) {
                        MetaSession.profile.economy.grant(goldAmount = value)
                        MetaSession.persist()
                        rebuild()
                    }
                }
                row.addView(sell, LinearLayout.LayoutParams(UiKit.dp(context, 80f), UiKit.dp(context, 40f)))
                
                invPanel.addView(row)
            }
        }
        scrollable.addView(invPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 12f) })

        val close = GameButton(context, context.getString(R.string.close), GameButton.Style.SECONDARY)
        close.onButtonClick = { (context as? MainActivity)?.screenManager?.pop(false) }
        scrollable.addView(close, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 48f)).apply {
            topMargin = UiKit.dp(context, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        addView(Widgets.scroll(context, scrollable), UiKit.params(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun formatStats(g: GearInstance): String {
        val s = mutableListOf<String>()
        if (g.flatAtk > 0) s.add("ATK +${g.flatAtk.toInt()}")
        if (g.pctAtk > 0) s.add("ATK +${(g.pctAtk * 100).toInt()}%")
        if (g.flatHp > 0) s.add("HP +${g.flatHp.toInt()}")
        if (g.pctHp > 0) s.add("HP +${(g.pctHp * 100).toInt()}%")
        if (g.flatArmor > 0) s.add("Armor +${g.flatArmor.toInt()}")
        if (g.critChance > 0) s.add("Crit +${(g.critChance * 100).toInt()}%")
        if (g.critDmg > 0) s.add("CritDmg +${(g.critDmg * 100).toInt()}%")
        if (g.attackSpeed > 0) s.add("ASpd +${(g.attackSpeed * 100).toInt()}%")
        if (g.moveSpeed > 0) s.add("Spd +${(g.moveSpeed * 100).toInt()}%")
        if (g.lifesteal > 0) s.add("LifeStl +${(g.lifesteal * 100).toInt()}%")
        if (g.cooldownReduction > 0) s.add("CDR +${(g.cooldownReduction * 100).toInt()}%")
        if (g.aoeSize > 0) s.add("AoE +${(g.aoeSize * 100).toInt()}%")
        return s.joinToString(", ")
    }

    private fun rebuild() {
        (context as? MainActivity)?.screenManager?.replace(GearScreen(context), animated = false)
    }

    override fun onBack(): Boolean {
        (context as? MainActivity)?.screenManager?.pop(false)
        return true
    }
}
