package com.towerbound.laststand.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.towerbound.laststand.R

/**
 * Central UI styling toolkit. All screens are built programmatically so the
 * visual language stays consistent and fully procedural (no image assets).
 */
object UiKit {

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /* Palette (mirror of colors.xml for programmatic use) */
    val cBgDark = Color.parseColor("#0D1021")
    val cBgPanel = Color.parseColor("#1A1F3A")
    val cBgPanelLight = Color.parseColor("#28304F")
    val cPrimary = Color.parseColor("#7C4DFF")
    val cPrimaryDark = Color.parseColor("#4F2E9E")
    val cAccent = Color.parseColor("#00E5FF")
    val cAccentWarm = Color.parseColor("#FFB300")
    val cDanger = Color.parseColor("#FF5252")
    val cSuccess = Color.parseColor("#69F0AE")
    val cGold = Color.parseColor("#FFC947")
    val cGem = Color.parseColor("#4DD0E1")
    val cText = Color.parseColor("#F4F6FF")
    val cTextDim = Color.parseColor("#B9C0DA")

    fun text(ctx: Context, s: String, sizeDp: Float, color: Int = cText, bold: Boolean = true): TextView {
        val tv = TextView(ctx)
        tv.text = s
        tv.setTextColor(color)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
        tv.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        tv.setShadowLayer(4f, 0f, 2f, Color.argb(140, 0, 0, 0))
        tv.gravity = Gravity.CENTER
        tv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        return tv
    }

    fun title(ctx: Context, s: String, sizeDp: Float = 34f): TextView {
        val tv = text(ctx, s, sizeDp, cText, true)
        tv.setShadowLayer(10f, 0f, 4f, Color.argb(200, 0x7C, 0x4D, 0xFF))
        tv.letterSpacing = 0.08f
        return tv
    }

    fun vertical(ctx: Context, spacingDp: Float = 10f): LinearLayout {
        val ll = LinearLayout(ctx)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(0, 0, 0, dp(ctx, spacingDp))
        return ll
    }

    fun params(width: Int, height: Int, gravity: Int = Gravity.CENTER): FrameLayout.LayoutParams {
        val lp = FrameLayout.LayoutParams(width, height)
        lp.gravity = gravity
        return lp
    }

    fun linearParams(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(width, height)
        if (weight > 0f) lp.weight = weight
        lp.gravity = Gravity.CENTER_HORIZONTAL
        return lp
    }
}

/** Rarity tiers shared by heroes, skins, chests and drop tables. */
enum class Rarity(val color: Int, val glowStrength: Float, val label: String) {
    COMMON(Color.parseColor("#9E9E9E"), 0f, "Common"),
    UNCOMMON(Color.parseColor("#4CAF50"), 0.25f, "Uncommon"),
    RARE(Color.parseColor("#2196F3"), 0.45f, "Rare"),
    EPIC(Color.parseColor("#9C27B0"), 0.65f, "Epic"),
    LEGENDARY(Color.parseColor("#FF9800"), 0.85f, "Legendary"),
    MYTHIC(Color.parseColor("#F44336"), 1f, "Mythic");

    val index: Int get() = ordinal

    companion object {
        fun fromIndex(i: Int): Rarity = entries[i.coerceIn(0, entries.size - 1)]
    }
}
