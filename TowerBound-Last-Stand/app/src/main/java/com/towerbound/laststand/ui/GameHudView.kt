package com.towerbound.laststand.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.towerbound.laststand.R
import com.towerbound.laststand.assets.AssetRepository
import com.towerbound.laststand.env.TimeOfDay
import kotlin.math.min
import kotlin.math.sin

/**
 * In-game HUD rendered as one custom pixel-art view (no Android widgets):
 *
 *   TOP LEFT     tower icon + HP bar + shield bar
 *   TOP CENTER   wave number + enemy count (chunky panel)
 *   TOP RIGHT    time-of-day dial (sun/moon arc)
 *   BOTTOM LEFT  hero portrait + hero HP bar + XP bar + level badge
 *
 * Action buttons (attack/ability/ultimate/dash) and the joystick remain
 * interactive sibling views positioned by GameFlow.
 *
 * All bars use stepped pixel segments with dark outlines — consistent with
 * the game's pixel-art visual language.
 */
@SuppressLint("ViewConstructor")
class GameHudView(context: Context) : View(context) {

    /* ---- live snapshot (written from the HUD tick, read on draw) ---- */
    @Volatile var towerHp = 1f
    @Volatile var towerShield = 0f
    @Volatile var wave = 1
    @Volatile var enemyCount = 0
    @Volatile var heroHp = 1f
    @Volatile var heroXp = 0f
    @Volatile var heroLevel = 1
    @Volatile var timeOfDay: TimeOfDay = TimeOfDay.DAY
    @Volatile var clockFraction = 0.12f
    @Volatile var heroId: String = "ARIC"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var portrait: Bitmap? = null
    private var portraitFor: String? = null
    private var time = 0f

    /* palette */
    private val panelBg = Color.argb(200, 13, 16, 34)
    private val panelBorder = Color.argb(255, 44, 50, 92)
    private val panelEdge = Color.argb(255, 76, 84, 148)
    private val hpFill = Color.rgb(88, 214, 96)
    private val hpDark = Color.rgb(30, 110, 44)
    private val shieldFill = Color.rgb(77, 166, 255)
    private val shieldDark = Color.rgb(26, 80, 150)
    private val xpFill = Color.rgb(124, 215, 255)
    private val xpDark = Color.rgb(40, 110, 160)
    private val warnFill = Color.rgb(255, 110, 84)
    private val textColor = Color.rgb(244, 246, 255)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        time += 0.016f
        val w = width.toFloat()
        val h = height.toFloat()

        drawTowerPanel(canvas, dp(10f), dp(10f))
        drawWavePanel(canvas, w / 2f, dp(10f))
        drawClock(canvas, w - dp(34f), dp(34f))
        drawHeroPanel(canvas, dp(10f), h - dp(10f))
    }

    /* ================= top-left: tower HP + shield ================= */

    private fun drawTowerPanel(canvas: Canvas, x: Float, y: Float) {
        val pw = dp(190f)
        val ph = dp(52f)
        pixelPanel(canvas, x, y, pw, ph)

        // tower glyph (mini pixel tower icon drawn procedurally)
        val gx = x + dp(12f)
        val gy = y + ph / 2f
        paint.color = Color.rgb(150, 158, 190)
        fun px(px: Float, py: Float, s: Float, col: Int) {
            paint.color = col
            canvas.drawRect(gx + px, gy + py, gx + px + s, gy + py + s, paint)
        }
        val s = dp(5f)
        // battlements
        px(-s * 1.5f, -s * 2.5f, s, Color.rgb(120, 128, 164)); px(0.5f * s, -s * 2.5f, s, Color.rgb(150, 158, 190)); px(s * 2.5f, -s * 2.5f, s, Color.rgb(120, 128, 164))
        // body
        for (row in -1..2) px(-s * 1.5f, -s * 1.5f + row * s, s * 4.1f, if (row == 0) Color.rgb(96, 104, 142) else Color.rgb(72, 78, 112))
        // glowing core
        px(-s * 0.4f, -s * 0.2f, s * 0.8f, Color.rgb(120, 220, 255))
        // base
        px(-s * 2f, s * 2.5f, s * 4.5f, Color.rgb(52, 58, 88))

        // HP bar
        val barX = x + dp(40f)
        val barW = pw - dp(52f)
        bar(canvas, barX, y + dp(14f), barW, dp(11f), towerHp, hpFill, hpDark,
            if (towerHp < 0.3f) warnFill else null, segment = dp(9f))

        // shield bar (thin, below HP)
        if (towerShield > 0.003f) {
            bar(canvas, barX, y + dp(30f), barW * 0.7f, dp(6f), towerShield, shieldFill, shieldDark,
                null, segment = dp(9f))
        }

        // label
        paint.textSize = dp(10f)
        paint.isFakeBoldText = true
        paint.color = Color.argb(210, 190, 198, 235)
        canvas.drawText(context.getString(R.string.hud_tower), barX, y + dp(46f), paint)
    }

    /* ================= top-center: wave + enemies ================= */

    private fun drawWavePanel(canvas: Canvas, cx: Float, y: Float) {
        val pw = dp(170f)
        val ph = dp(44f)
        val x = cx - pw / 2f
        pixelPanel(canvas, x, y, pw, ph)

        paint.textSize = dp(17f)
        paint.isFakeBoldText = true
        paint.color = textColor
        paint.setShadowLayer(dp(3f), 0f, dp(1f), Color.argb(200, 0, 0, 0))
        val waveText = context.getString(R.string.hud_wave, wave)
        canvas.drawText(waveText, cx - dp(34f), y + ph / 2f + dp(2f), paint)

        paint.textSize = dp(12f)
        paint.color = Color.argb(230, 255, 170, 120)
        val enemyText = context.getString(R.string.hud_enemies, enemyCount)
        canvas.drawText(enemyText, cx + dp(20f), y + ph / 2f + dp(2f), paint)
        paint.clearShadowLayer()

        // enemy skull glyph
        drawSkull(canvas, cx - dp(42f), y + ph / 2f, dp(4f))
    }

    /* ================= top-right: time-of-day dial ================= */

    private fun drawClock(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(22f)
        // dial backdrop
        paint.color = panelBg
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.color = panelBorder
        canvas.drawCircle(cx, cy, r - dp(1.5f), paint)
        paint.style = Paint.Style.FILL

        // sun / moon by phase
        val pulse = 0.85f + 0.15f * sin(time * 2f)
        when (timeOfDay) {
            TimeOfDay.DAWN -> {
                paint.color = Color.argb((255 * pulse).toInt(), 255, 170, 90)
                canvas.drawCircle(cx, cy, r * 0.42f, paint)
                paint.color = Color.argb(140, 255, 220, 160)
                canvas.drawCircle(cx, cy, r * 0.6f, paint)
            }
            TimeOfDay.DAY -> {
                paint.color = Color.argb((255 * pulse).toInt(), 255, 214, 64)
                canvas.drawCircle(cx, cy, r * 0.4f, paint)
                // rays
                paint.strokeWidth = dp(2.5f)
                paint.color = Color.argb(220, 255, 214, 64)
                for (i in 0 until 8) {
                    val a = i / 8f * 6.28318f
                    canvas.drawLine(
                        cx + kotlin.math.cos(a) * r * 0.55f, cy + kotlin.math.sin(a) * r * 0.55f,
                        cx + kotlin.math.cos(a) * r * 0.8f, cy + kotlin.math.sin(a) * r * 0.8f, paint
                    )
                }
            }
            TimeOfDay.DUSK -> {
                paint.color = Color.argb((255 * pulse).toInt(), 255, 120, 70)
                canvas.drawCircle(cx, cy, r * 0.42f, paint)
                paint.color = Color.argb(120, 255, 170, 120)
                canvas.drawCircle(cx, cy, r * 0.62f, paint)
            }
            TimeOfDay.NIGHT, TimeOfDay.MIDNIGHT -> {
                // crescent moon
                paint.color = if (timeOfDay == TimeOfDay.MIDNIGHT) Color.rgb(150, 170, 255) else Color.rgb(220, 228, 255)
                canvas.drawCircle(cx, cy, r * 0.42f, paint)
                paint.color = panelBg
                canvas.drawCircle(cx + r * 0.2f, cy - r * 0.14f, r * 0.36f, paint)
                if (timeOfDay == TimeOfDay.MIDNIGHT) {
                    // twinkle stars
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx - r * 0.5f, cy - r * 0.4f, dp(1.6f), paint)
                    canvas.drawCircle(cx - r * 0.3f, cy + r * 0.5f, dp(1.2f), paint)
                }
            }
        }
    }

    /* ================= bottom-left: hero portrait + bars ================= */

    private fun drawHeroPanel(canvas: Canvas, x: Float, bottomY: Float) {
        val ph = dp(78f)
        val y = bottomY - ph

        // portrait chip
        val chip = dp(64f)
        pixelPanel(canvas, x, y + (ph - chip) / 2f, chip, chip)

        if (portraitFor != heroId || portrait == null) {
            portraitFor = heroId
            portrait = try { AssetRepository.portrait(heroId) } catch (_: Throwable) { null }
        }
        portrait?.let { p ->
            val pad = dp(5f)
            val scale = min((chip - pad * 2) / p.width, (chip - pad * 2) / p.height)
            val dw = p.width * scale
            val dh = p.height * scale
            paint.isFilterBitmap = false
            canvas.save()
            canvas.translate(x + (chip - dw) / 2f, y + (ph - chip) / 2f + (chip - dh) / 2f)
            canvas.scale(scale, scale)
            canvas.drawBitmap(p, 0f, 0f, paint)
            canvas.restore()
            paint.isFilterBitmap = true
        }

        // bars right of portrait
        val barX = x + chip + dp(8f)
        val barW = dp(150f)
        // hero HP
        bar(canvas, barX, y + dp(12f), barW, dp(13f), heroHp, hpFill, hpDark,
            if (heroHp < 0.3f) warnFill else null, segment = dp(11f))
        // hero XP
        bar(canvas, barX, y + dp(32f), barW, dp(7f), heroXp, xpFill, xpDark, null, segment = dp(11f))

        // level badge
        val badgeCx = barX + dp(16f)
        val badgeCy = y + dp(58f)
        paint.color = Color.rgb(255, 179, 0)
        canvas.drawCircle(badgeCx, badgeCy, dp(11f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.rgb(120, 76, 0)
        canvas.drawCircle(badgeCx, badgeCy, dp(11f), paint)
        paint.style = Paint.Style.FILL
        paint.textSize = dp(11f)
        paint.isFakeBoldText = true
        paint.color = Color.rgb(60, 36, 0)
        val lvl = heroLevel.toString()
        canvas.drawText(lvl, badgeCx - paint.measureText(lvl) / 2f, badgeCy + dp(4f), paint)
    }

    /* ================= shared pixel-art primitives ================= */

    /** Chunky panel: dark fill, stepped border, light top edge. */
    private fun pixelPanel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        paint.color = panelBorder
        canvas.drawRect(x, y, x + w, y + h, paint)
        paint.color = panelBg
        canvas.drawRect(x + dp(3f), y + dp(3f), x + w - dp(3f), y + h - dp(3f), paint)
        // top-left light edge / bottom-right dark edge (bevel)
        paint.color = panelEdge
        canvas.drawRect(x + dp(3f), y + dp(3f), x + w - dp(3f), y + dp(6f), paint)
        paint.color = Color.argb(140, 6, 8, 18)
        canvas.drawRect(x + dp(3f), y + h - dp(6f), x + w - dp(3f), y + h - dp(3f), paint)
    }

    /**
     * Stepped pixel bar: dark outline, empty track, segmented fill. Optionally
     * switches to [warn] color when low.
     */
    private fun bar(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, fraction: Float,
        fill: Int, fillDark: Int, warn: Int?, segment: Float
    ) {
        val f = fraction.coerceIn(0f, 1f)
        // outline
        paint.color = Color.argb(230, 10, 12, 24)
        canvas.drawRect(x - dp(1.5f), y - dp(1.5f), x + w + dp(1.5f), y + h + dp(1.5f), paint)
        // track
        paint.color = Color.argb(220, 26, 30, 52)
        canvas.drawRect(x, y, x + w, y + h, paint)

        if (f <= 0.001f) return
        val fillW = w * f
        val useWarn = warn != null && f < 0.3f
        val base = if (useWarn) warn!! else fill
        val dark = if (useWarn) Color.rgb(150, 50, 30) else fillDark

        // segment the fill into chunky blocks with 1px gaps
        var segX = x
        while (segX < x + fillW - segment * 0.25f) {
            val segEnd = min(segX + segment - dp(1f), x + fillW)
            if (segEnd > segX) {
                paint.color = base
                canvas.drawRect(segX, y + dp(1f), segEnd, y + h - dp(1f), paint)
                paint.color = dark
                canvas.drawRect(segX, y + h * 0.62f, segEnd, y + h - dp(1f), paint)
            }
            segX += segment
        }
        // leading bright cap
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawRect(x + fillW - dp(2f), y + dp(1f), x + fillW, y + h - dp(1f), paint)
    }

    /** Tiny pixel skull for the enemy counter. */
    private fun drawSkull(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        fun px(dx: Float, dy: Float, col: Int) {
            paint.color = col
            canvas.drawRect(cx + dx * s, cy + dy * s, cx + (dx + 1f) * s, cy + (dy + 1f) * s, paint)
        }
        // cranium
        for (ry in -2..0) for (rx in -1..1) px(rx.toFloat(), ry.toFloat(), Color.rgb(226, 230, 240))
        // jaw
        px(-0.5f, 1f, Color.rgb(200, 205, 218)); px(0.5f, 1f, Color.rgb(200, 205, 218))
        // eyes
        px(-0.6f, -0.9f, Color.rgb(30, 34, 60)); px(0.6f, -0.9f, Color.rgb(30, 34, 60))
    }
}
