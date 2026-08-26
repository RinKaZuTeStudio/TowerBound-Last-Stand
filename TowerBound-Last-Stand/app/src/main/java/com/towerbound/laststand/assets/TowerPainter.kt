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
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural tower sprite with five visual damage stages:
 * 0 pristine, 1 cracks, 2 sparks+smoke vents, 3 fire, 4 critical (heavy fire, tilting crystal).
 */
object TowerPainter {

    const val W = 256
    const val H = 320

    fun generate(stage: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(9001 + stage)

        val cx = W / 2f
        val baseY = H - 14f
        val stoneLight = Color.parseColor("#B7BFD4")
        val stoneMid = Color.parseColor("#8791AD")
        val stoneDark = Color.parseColor("#5A6380")

        // ---- glow behind crystal ----
        val crystalColor = when {
            stage >= 4 -> Color.parseColor("#FF5252")
            stage == 3 -> Color.parseColor("#FFB300")
            else -> Color.parseColor("#00E5FF")
        }
        p.shader = RadialGradient(cx, 74f, 150f, crystalColor, Color.argb(0, Color.red(crystalColor), Color.green(crystalColor), Color.blue(crystalColor)), Shader.TileMode.CLAMP)
        p.alpha = 60
        c.drawCircle(cx, 74f, 150f, p)
        p.alpha = 255
        p.shader = null

        // ---- base platform ----
        val basePath = Path()
        basePath.moveTo(cx - 108f, baseY)
        basePath.lineTo(cx - 84f, baseY - 26f)
        basePath.lineTo(cx + 84f, baseY - 26f)
        basePath.lineTo(cx + 108f, baseY)
        basePath.close()
        p.color = stoneDark
        c.drawPath(basePath, p)
        p.shader = LinearGradient(cx - 84f, 0f, cx + 84f, 0f, stoneLight, stoneDark, Shader.TileMode.CLAMP)
        c.drawRect(cx - 84f, baseY - 32f, cx + 84f, baseY - 24f, p)
        p.shader = null

        // ---- main keep ----
        val bodyTop = 96f
        val bodyBot = baseY - 26f
        val halfW = 58f
        p.shader = LinearGradient(cx - halfW, bodyTop, cx + halfW, bodyBot, stoneLight, stoneDark, Shader.TileMode.CLAMP)
        c.drawRect(cx - halfW, bodyTop, cx + halfW, bodyBot, p)
        p.shader = null
        // stone block lines
        p.color = Color.argb(60, 30, 34, 54)
        p.strokeWidth = 2f
        var row = 0
        var y = bodyTop + 18f
        while (y < bodyBot) {
            c.drawLine(cx - halfW + 3f, y, cx + halfW - 3f, y, p)
            val off = if (row % 2 == 0) 0f else 24f
            var x = cx - halfW + off
            while (x < cx + halfW - 6f) {
                c.drawLine(x, y, x, y - 18f, p)
                x += 48f
            }
            y += 18f; row++
        }

        // damage cracks
        if (stage >= 1) {
            p.color = Color.argb(200, 24, 22, 34)
            p.strokeWidth = 3f
            p.style = Paint.Style.STROKE
            repeat(stage * 3) {
                val sx = cx + rng.nextFloat() * (halfW * 1.6f) - halfW * 0.8f
                val sy = bodyTop + rng.nextFloat() * (bodyBot - bodyTop - 20f) + 10f
                val crack = Path()
                crack.moveTo(sx, sy)
                var dx = sx; var dy = sy
                repeat(4) {
                    dx += rng.nextFloat() * 22f - 11f
                    dy += rng.nextFloat() * 18f + 4f
                    crack.lineTo(dx, dy)
                }
                c.drawPath(crack, p)
            }
            p.style = Paint.Style.FILL
        }

        // windows
        p.color = Color.argb(230, 18, 20, 40)
        for (wy in listOf(bodyTop + 34f, bodyTop + 78f)) {
            c.drawRoundRect(RectF(cx - 10f, wy, cx + 10f, wy + 26f), 10f, 10f, p)
        }
        // door
        c.drawRoundRect(RectF(cx - 20f, bodyBot - 44f, cx + 20f, bodyBot), 14f, 14f, p)
        p.color = Color.parseColor("#4E342E")
        c.drawRoundRect(RectF(cx - 17f, bodyBot - 41f, cx + 17f, bodyBot - 2f), 12f, 12f, p)

        // ---- battlements ----
        p.shader = LinearGradient(cx - halfW - 8f, 0f, cx + halfW + 8f, 0f, stoneLight, stoneDark, Shader.TileMode.CLAMP)
        for (i in 0..4) {
            if (i % 2 == 0) {
                c.drawRect(cx - halfW - 8f + i * (halfW + 8f) / 2f, bodyTop - 22f, cx - halfW - 8f + (i + 1) * (halfW + 8f) / 2f - 4f, bodyTop + 2f, p)
            }
        }
        p.shader = null

        // ---- crystal spire ----
        val spireTop = 20f
        val crystalPath = Path()
        crystalPath.moveTo(cx, spireTop)
        crystalPath.lineTo(cx + 26f, 74f)
        crystalPath.lineTo(cx + 12f, 106f)
        crystalPath.lineTo(cx - 12f, 106f)
        crystalPath.lineTo(cx - 26f, 74f)
        crystalPath.close()
        p.color = Color.argb(255, 20, 24, 44)
        p.strokeWidth = 5f
        p.style = Paint.Style.STROKE
        c.drawPath(crystalPath, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx - 26f, spireTop, cx + 26f, 106f,
            crystalColor, Color.parseColor("#1A237E"), Shader.TileMode.CLAMP)
        c.drawPath(crystalPath, p)
        p.shader = null
        // inner shine
        p.color = Color.argb(150, 255, 255, 255)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        c.drawLine(cx - 8f, 44f, cx - 3f, 88f, p)
        p.style = Paint.Style.FILL

        // ---- damage effects ----
        if (stage >= 2) {
            // spark vents
            p.color = Color.parseColor("#FFEE58")
            p.setShadowLayer(8f, 0f, 0f, Color.parseColor("#FFEE58"))
            repeat(stage) {
                val sx = cx + rng.nextFloat() * 70f - 35f
                val sy = bodyTop + 30f + rng.nextFloat() * 60f
                c.drawCircle(sx, sy, 3f + rng.nextFloat() * 2f, p)
            }
            p.clearShadowLayer()
        }
        if (stage >= 3) {
            // fire tongues
            for (i in 0 until stage * 2) {
                val fx = cx - 40f + rng.nextFloat() * 80f
                val fy = bodyTop + 20f + rng.nextFloat() * (bodyBot - bodyTop - 40f)
                drawFlame(c, p, fx, fy, 14f + rng.nextFloat() * 12f, rng)
            }
        }
        if (stage >= 4) {
            // heavy smoke column
            for (i in 0 until 5) {
                p.color = Color.argb(90, 60, 62, 80)
                val sx = cx - 30f + rng.nextFloat() * 60f
                c.drawOval(RectF(sx - 18f, 40f + i * 22f, sx + 18f, 58f + i * 22f), p)
            }
        }
        return bmp
    }

    private fun drawFlame(c: Canvas, p: Paint, x: Float, y: Float, size: Float, rng: Random) {
        val flame = Path()
        flame.moveTo(x, y - size)
        flame.quadTo(x + size * 0.7f, y - size * 0.3f, x + size * 0.35f, y + size * 0.3f)
        flame.quadTo(x, y + size * 0.55f, x - size * 0.35f, y + size * 0.3f)
        flame.quadTo(x - size * 0.7f, y - size * 0.3f, x, y - size)
        p.shader = RadialGradient(x, y - size * 0.2f, size, Color.parseColor("#FFF176"), Color.parseColor("#E53935"), Shader.TileMode.CLAMP)
        c.drawPath(flame, p)
        p.shader = null
        p.color = Color.argb(160, 255, 240, 150)
        c.drawCircle(x, y - size * 0.15f, size * 0.22f, p)
    }

    /** Rotatable auto-turret sprite (drawn on top of the tower). */
    fun generateTurret(): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        // base dome
        p.shader = RadialGradient(cx - 8f, 38f, 34f, Color.parseColor("#C5CAE9"), Color.parseColor("#5C6BC0"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, 48f, 30f, p)
        p.shader = null
        p.color = Color.argb(255, 20, 24, 44)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        c.drawCircle(cx, 48f, 30f, p)
        p.style = Paint.Style.FILL
        // barrel pointing up
        p.shader = LinearGradient(cx - 8f, 0f, cx + 8f, 0f, Color.parseColor("#9FA8DA"), Color.parseColor("#3949AB"), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(cx - 8f, 4f, cx + 8f, 44f), 6f, 6f, p)
        p.shader = null
        // energy muzzle
        p.color = Color.parseColor("#00E5FF")
        p.setShadowLayer(10f, 0f, 0f, Color.parseColor("#00E5FF"))
        c.drawCircle(cx, 8f, 6f, p)
        p.clearShadowLayer()
        return bmp
    }
}
