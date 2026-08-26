package com.towerbound.laststand.assets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Icon symbol painter. Ability icons, currency icons and UI glyphs are
 * composed from geometric symbols on rounded panels — fully procedural.
 */
object SymbolPainter {

    enum class Symbol {
        SWORD, SHIELD, BOW, ARROW_RAIN, FROST_NOVA, HEAL, THUNDER, CHAIN_BOLT,
        WHIRLWIND, STOMP, DASH, EXECUTE, POISON_NOVA, SUMMON, SHIELD_WALL,
        AEGIS, BATTLE_CRY, SWIFT, MANA_SURGE, UNSTOPPABLE, CURSE, STATIC,
        CHEST, GEM, COIN, LOCK, PLAY, PAUSE, SETTINGS, SOUND, STAR, SKULL, TURRET, ARROW_UP
    }

    /** Draw [symbol] centered on a rounded panel. Size 96 recommended. */
    fun icon(symbol: Symbol, color: Int, bg: Int, size: Int = 96, border: Int = Color.argb(200, 255, 255, 255)): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // panel
        val r = size * 0.22f
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(),
            lighten(bg, 0.25f), darken(bg, 0.35f), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(2f, 2f, size - 2f, size - 2f), r, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.045f
        p.color = border
        c.drawRoundRect(RectF(3f, 3f, size - 3f, size - 3f), r, r, p)
        p.style = Paint.Style.FILL

        drawSymbol(c, p, symbol, color, size)
        return bmp
    }

    fun drawSymbol(c: Canvas, p: Paint, symbol: Symbol, color: Int, size: Int) {
        val cx = size / 2f
        val s = size / 96f // scale factor vs reference 96 grid
        p.color = color
        p.setShadowLayer(6f * s, 0f, 0f, color)
        val path = Path()

        when (symbol) {
            Symbol.SWORD -> {
                p.strokeWidth = 7f * s; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                c.drawLine(cx + 10f * s, cx - 34f * s, cx - 10f * s, cx + 18f * s, p)
                c.drawLine(cx - 16f * s, cx + 2f * s, cx + 2f * s, cx + 20f * s, p)
                c.drawLine(cx - 6f * s, cx + 14f * s, cx - 16f * s, cx + 30f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.SHIELD -> {
                path.moveTo(cx, cx - 34f * s)
                path.lineTo(cx + 26f * s, cx - 24f * s)
                path.quadTo(cx + 24f * s, cx + 12f * s, cx, cx + 34f * s)
                path.quadTo(cx - 24f * s, cx + 12f * s, cx - 26f * s, cx - 24f * s)
                path.close()
                c.drawPath(path, p)
                p.color = Color.argb(160, 255, 255, 255)
                c.drawCircle(cx, cx - 6f * s, 6f * s, p)
            }
            Symbol.BOW -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 7f * s
                c.drawArc(RectF(cx - 18f * s, cx - 32f * s, cx + 22f * s, cx + 32f * s), -65f, 130f, false, p)
                c.drawLine(cx + 14f * s, cx - 28f * s, cx + 14f * s, cx + 28f * s, p)
                c.drawLine(cx - 26f * s, cx, cx + 26f * s, cx, p)
                p.style = Paint.Style.FILL
            }
            Symbol.ARROW_RAIN -> {
                p.strokeWidth = 5f * s; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                for (i in 0 until 3) {
                    val ox = (i - 1) * 22f * s
                    c.drawLine(cx + ox + 8f * s, cx - 30f * s + kotlin.math.abs(i - 1) * 6f * s, cx + ox - 8f * s, cx + 26f * s - kotlin.math.abs(i - 1) * 6f * s, p)
                }
                p.style = Paint.Style.FILL
            }
            Symbol.FROST_NOVA -> {
                p.strokeWidth = 6f * s; p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 8) {
                    val a = i / 8f * 6.28318f
                    val x1 = cx + kotlin.math.cos(a) * 12f * s
                    val y1 = cx + kotlin.math.sin(a) * 12f * s
                    val x2 = cx + kotlin.math.cos(a) * 32f * s
                    val y2 = cx + kotlin.math.sin(a) * 32f * s
                    c.drawLine(x1, y1, x2, y2, p)
                }
                c.drawCircle(cx, cx, 8f * s, p)
            }
            Symbol.HEAL -> {
                c.drawRoundRect(RectF(cx - 8f * s, cx - 28f * s, cx + 8f * s, cx + 28f * s), 5f * s, 5f * s, p)
                c.drawRoundRect(RectF(cx - 28f * s, cx - 8f * s, cx + 28f * s, cx + 8f * s), 5f * s, 5f * s, p)
            }
            Symbol.THUNDER -> {
                path.moveTo(cx + 14f * s, cx - 34f * s)
                path.lineTo(cx - 14f * s, cx + 4f * s)
                path.lineTo(cx - 1f * s, cx + 6f * s)
                path.lineTo(cx - 14f * s, cx + 34f * s)
                path.lineTo(cx + 15f * s, cx - 6f * s)
                path.lineTo(cx + 2f * s, cx - 8f * s)
                path.close()
                c.drawPath(path, p)
            }
            Symbol.CHAIN_BOLT -> {
                drawSymbol(c, p, Symbol.THUNDER, color, size)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f * s
                for (i in 0 until 3) {
                    c.drawCircle(cx - 30f * s + i * 8f * s, cx + 26f * s - i * 14f * s, 4f * s, p)
                }
                p.style = Paint.Style.FILL
            }
            Symbol.WHIRLWIND -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 7f * s; p.strokeCap = Paint.Cap.ROUND
                c.drawArc(RectF(cx - 26f * s, cx - 26f * s, cx + 26f * s, cx + 26f * s), 0f, 300f, false, p)
                p.strokeWidth = 5f * s
                c.drawArc(RectF(cx - 14f * s, cx - 14f * s, cx + 14f * s, cx + 14f * s), 120f, 300f, false, p)
                p.style = Paint.Style.FILL
            }
            Symbol.STOMP -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 6f * s
                c.drawCircle(cx, cx - 6f * s, 12f * s, p)
                c.drawLine(cx - 12f * s, cx + 8f * s, cx - 30f * s, cx + 22f * s, p)
                c.drawLine(cx + 12f * s, cx + 8f * s, cx + 30f * s, cx + 22f * s, p)
                c.drawLine(cx, cx + 10f * s, cx, cx + 30f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.DASH -> {
                p.strokeWidth = 7f * s; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                c.drawLine(cx - 30f * s, cx, cx + 20f * s, cx, p)
                path.moveTo(cx + 8f * s, cx - 16f * s)
                path.lineTo(cx + 28f * s, cx)
                path.lineTo(cx + 8f * s, cx + 16f * s)
                p.style = Paint.Style.FILL
                c.drawPath(path, p)
            }
            Symbol.EXECUTE -> {
                drawSymbol(c, p, Symbol.SWORD, color, size)
                p.color = Color.argb(200, 255, 60, 60)
                c.drawCircle(cx + 24f * s, cx - 24f * s, 8f * s, p)
            }
            Symbol.POISON_NOVA -> {
                c.drawCircle(cx, cx, 14f * s, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f * s
                c.drawCircle(cx, cx, 24f * s, p)
                c.drawCircle(cx, cx, 33f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.SUMMON -> {
                // shadow silhouette
                p.color = darken(color, 0.3f)
                c.drawCircle(cx, cx + 6f * s, 16f * s, p)
                c.drawCircle(cx, cx - 18f * s, 10f * s, p)
                p.color = color
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f * s
                c.drawCircle(cx, cx, 30f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.SHIELD_WALL -> {
                for (i in 0 until 3) {
                    val ox = (i - 1) * 26f * s
                    path.reset()
                    path.moveTo(cx + ox, cx - 30f * s)
                    path.lineTo(cx + ox + 16f * s, cx - 20f * s)
                    path.quadTo(cx + ox + 15f * s, cx + 10f * s, cx + ox, cx + 28f * s)
                    path.quadTo(cx + ox - 15f * s, cx + 10f * s, cx + ox - 16f * s, cx - 20f * s)
                    path.close()
                    c.drawPath(path, p)
                }
            }
            Symbol.AEGIS -> {
                drawSymbol(c, p, Symbol.SHIELD, color, size)
                p.color = Color.WHITE
                c.drawCircle(cx, cx, 10f * s, p)
            }
            Symbol.BATTLE_CRY -> {
                path.moveTo(cx - 12f * s, cx - 30f * s)
                path.quadTo(cx, cx - 8f * s, cx + 12f * s, cx - 30f * s)
                path.lineTo(cx + 6f * s, cx + 6f * s)
                path.quadTo(cx, cx + 12f * s, cx - 6f * s, cx + 6f * s)
                path.close()
                c.drawPath(path, p)
                p.strokeWidth = 4f * s; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                c.drawLine(cx, cx + 16f * s, cx, cx + 30f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.SWIFT -> {
                p.strokeWidth = 6f * s; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                for (i in 0 until 3) {
                    val oy = (i - 1) * 16f * s
                    c.drawLine(cx - 30f * s + kotlin.math.abs(i - 1) * 10f * s, cx + oy, cx + 24f * s - kotlin.math.abs(i - 1) * 10f * s, cx + oy, p)
                }
                p.style = Paint.Style.FILL
            }
            Symbol.MANA_SURGE -> {
                p.shader = RadialGradient(cx, cx, 30f * s, Color.WHITE, color, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cx, 18f * s, p)
                p.shader = null
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f * s
                c.drawCircle(cx, cx, 28f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.UNSTOPPABLE -> {
                drawSymbol(c, p, Symbol.SWORD, color, size)
                p.strokeWidth = 5f * s; p.style = Paint.Style.STROKE
                c.drawCircle(cx, cx, 34f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.CURSE -> {
                path.moveTo(cx, cx - 32f * s)
                path.lineTo(cx + 12f * s, cx - 4f * s)
                path.lineTo(cx + 26f * s, cx + 20f * s)
                path.lineTo(cx - 26f * s, cx + 20f * s)
                path.lineTo(cx - 12f * s, cx - 4f * s)
                path.close()
                c.drawPath(path, p)
                p.color = Color.argb(220, 20, 18, 28)
                c.drawCircle(cx - 7f * s, cx + 6f * s, 4f * s, p)
                c.drawCircle(cx + 7f * s, cx + 6f * s, 4f * s, p)
            }
            Symbol.STATIC -> {
                p.strokeWidth = 4f * s; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                for (i in 0 until 4) {
                    val a = i / 4f * 6.28318f + 0.4f
                    val x1 = cx + kotlin.math.cos(a) * 14f * s
                    val y1 = cx + kotlin.math.sin(a) * 14f * s
                    val x2 = cx + kotlin.math.cos(a + 0.5f) * 32f * s
                    val y2 = cx + kotlin.math.sin(a + 0.5f) * 32f * s
                    c.drawLine(x1, y1, x2, y2, p)
                }
                c.drawCircle(cx, cx, 9f * s, p)
                p.style = Paint.Style.FILL
            }
            Symbol.CHEST -> {
                c.drawRoundRect(RectF(cx - 30f * s, cx - 16f * s, cx + 30f * s, cx + 30f * s), 8f * s, 8f * s, p)
                p.color = Color.argb(180, 20, 16, 30)
                c.drawRoundRect(RectF(cx - 30f * s, cx - 16f * s, cx + 30f * s, cx - 2f * s), 8f * s, 8f * s, p)
                p.color = Color.argb(230, 255, 215, 90)
                c.drawRoundRect(RectF(cx - 5f * s, cx - 14f * s, cx + 5f * s, cx + 14f * s), 3f * s, 3f * s, p)
            }
            Symbol.GEM -> {
                path.moveTo(cx, cx - 30f * s)
                path.lineTo(cx + 24f * s, cx - 8f * s)
                path.lineTo(cx, cx + 32f * s)
                path.lineTo(cx - 24f * s, cx - 8f * s)
                path.close()
                c.drawPath(path, p)
                p.color = Color.argb(140, 255, 255, 255)
                path.reset()
                path.moveTo(cx, cx - 30f * s)
                path.lineTo(cx + 10f * s, cx - 8f * s)
                path.lineTo(cx, cx + 10f * s)
                path.close()
                c.drawPath(path, p)
            }
            Symbol.COIN -> {
                p.shader = RadialGradient(cx - 6f * s, cx - 6f * s, 34f * s, Color.parseColor("#FFE082"), Color.parseColor("#F57F17"), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cx, 30f * s, p)
                p.shader = null
            }
            Symbol.LOCK -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 7f * s
                c.drawArc(RectF(cx - 14f * s, cx - 30f * s, cx + 14f * s, cx - 2f * s), 180f, 180f, false, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(cx - 22f * s, cx - 4f * s, cx + 22f * s, cx + 30f * s), 6f * s, 6f * s, p)
            }
            Symbol.PLAY -> {
                path.moveTo(cx - 14f * s, cx - 24f * s)
                path.lineTo(cx + 24f * s, cx)
                path.lineTo(cx - 14f * s, cx + 24f * s)
                path.close()
                c.drawPath(path, p)
            }
            Symbol.PAUSE -> {
                c.drawRoundRect(RectF(cx - 18f * s, cx - 22f * s, cx - 4f * s, cx + 22f * s), 3f * s, 3f * s, p)
                c.drawRoundRect(RectF(cx + 4f * s, cx - 22f * s, cx + 18f * s, cx + 22f * s), 3f * s, 3f * s, p)
            }
            Symbol.SETTINGS -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 8f * s
                c.drawCircle(cx, cx, 14f * s, p)
                p.strokeWidth = 6f * s
                for (i in 0 until 8) {
                    val a = i / 8f * 6.28318f
                    c.drawLine(cx + kotlin.math.cos(a) * 20f * s, cx + kotlin.math.sin(a) * 20f * s,
                        cx + kotlin.math.cos(a) * 30f * s, cx + kotlin.math.sin(a) * 30f * s, p)
                }
                p.style = Paint.Style.FILL
            }
            Symbol.SOUND -> {
                path.moveTo(cx - 26f * s, cx - 10f * s)
                path.lineTo(cx - 10f * s, cx - 10f * s)
                path.lineTo(cx + 6f * s, cx - 28f * s)
                path.lineTo(cx + 6f * s, cx + 28f * s)
                path.lineTo(cx - 10f * s, cx + 10f * s)
                path.lineTo(cx - 26f * s, cx + 10f * s)
                path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 5f * s; p.strokeCap = Paint.Cap.ROUND
                c.drawArc(RectF(cx + 8f * s, cx - 16f * s, cx + 30f * s, cx + 16f * s), -50f, 100f, false, p)
                p.style = Paint.Style.FILL
            }
            Symbol.STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val a = i / 10f * 6.28318f - 1.5708f
                    val rr = if (i % 2 == 0) 32f * s else 13f * s
                    val x = cx + kotlin.math.cos(a) * rr
                    val y = cx + kotlin.math.sin(a) * rr
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, p)
            }
            Symbol.SKULL -> {
                c.drawCircle(cx, cx - 6f * s, 20f * s, p)
                c.drawRoundRect(RectF(cx - 12f * s, cx + 8f * s, cx + 12f * s, cx + 26f * s), 5f * s, 5f * s, p)
                p.color = Color.argb(230, 18, 16, 28)
                c.drawCircle(cx - 8f * s, cx - 8f * s, 5.5f * s, p)
                c.drawCircle(cx + 8f * s, cx - 8f * s, 5.5f * s, p)
            }
            Symbol.TURRET -> {
                c.drawCircle(cx, cx + 8f * s, 16f * s, p)
                c.drawRoundRect(RectF(cx - 6f * s, cx - 30f * s, cx + 6f * s, cx + 8f * s), 4f * s, 4f * s, p)
                c.drawCircle(cx, cx - 30f * s, 7f * s, p)
            }
            Symbol.ARROW_UP -> {
                path.moveTo(cx, cx - 30f * s)
                path.lineTo(cx + 22f * s, cx - 4f * s)
                path.lineTo(cx + 8f * s, cx - 4f * s)
                path.lineTo(cx + 8f * s, cx + 30f * s)
                path.lineTo(cx - 8f * s, cx + 30f * s)
                path.lineTo(cx - 8f * s, cx - 4f * s)
                path.lineTo(cx - 22f * s, cx - 4f * s)
                path.close()
                c.drawPath(path, p)
            }
        }
        p.clearShadowLayer()
    }

    private fun lighten(c: Int, f: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return Color.rgb((r + (255 - r) * f).toInt().coerceIn(0, 255),
            (g + (255 - g) * f).toInt().coerceIn(0, 255),
            (b + (255 - b) * f).toInt().coerceIn(0, 255))
    }

    private fun darken(c: Int, f: Float): Int {
        val k = 1f - f
        return Color.rgb((Color.red(c) * k).toInt().coerceIn(0, 255),
            (Color.green(c) * k).toInt().coerceIn(0, 255),
            (Color.blue(c) * k).toInt().coerceIn(0, 255))
    }
}
