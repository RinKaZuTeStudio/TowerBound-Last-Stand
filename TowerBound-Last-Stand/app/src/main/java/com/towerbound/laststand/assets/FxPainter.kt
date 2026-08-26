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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * FX & particle textures: soft glows, sparks, smoke, rings, slashes,
 * explosion frame sequences, status effect icons, orbs.
 */
object FxPainter {

    /* ---------------- pixel-art projectile sprites ---------------- */

    /**
     * Visually distinct pixel-art projectiles per element, all facing RIGHT
     * (rotation aligns with travel direction in the renderer).
     * No generic circles — each has a unique silhouette:
     *   impact  -> feathered arrow (wood shaft, steel head, fletching)
     *   frost   -> faceted ice shard
     *   poison  -> bubbling venom glob
     *   magic   -> zigzag lightning bolt
     *   fire    -> molten ember core with trailing flame tongues
     *   void    -> shadow kunai
     *   shield  -> spinning energy disc
     */
    fun projectileSprite(kind: String, size: Int = 64): Bitmap {
        val hi = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(hi)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = size.toFloat()
        val cx = s / 2f
        val cy = s / 2f

        fun px(x: Float, y: Float, w: Float, h: Float, col: Int) {
            p.color = col
            c.drawRect(x, y, x + w, y + h, p)
        }

        when (kind) {
            "impact" -> { // arrow
                val shaft = Color.parseColor("#8a6a3f")
                val shaftDark = Color.parseColor("#6b4f2c")
                val steel = Color.parseColor("#dfe6f2")
                val feather = Color.parseColor("#e8e2d0")
                val featherDark = Color.parseColor("#b8b09a")
                // shaft
                px(cx - s * 0.30f, cy - s * 0.045f, s * 0.55f, s * 0.09f, shaft)
                px(cx - s * 0.30f, cy + s * 0.01f, s * 0.55f, s * 0.03f, shaftDark)
                // steel head (triangle built from steps)
                px(cx + s * 0.24f, cy - s * 0.06f, s * 0.08f, s * 0.12f, steel)
                px(cx + s * 0.32f, cy - s * 0.04f, s * 0.05f, s * 0.08f, steel)
                px(cx + s * 0.37f, cy - s * 0.02f, s * 0.03f, s * 0.04f, steel)
                // fletching
                px(cx - s * 0.34f, cy - s * 0.10f, s * 0.07f, s * 0.08f, feather)
                px(cx - s * 0.34f, cy + s * 0.02f, s * 0.07f, s * 0.08f, featherDark)
                px(cx - s * 0.27f, cy - s * 0.08f, s * 0.05f, s * 0.06f, featherDark)
                px(cx - s * 0.27f, cy + s * 0.02f, s * 0.05f, s * 0.06f, feather)
            }
            "frost" -> { // ice shard
                val ice = Color.parseColor("#bfeaff")
                val iceMid = Color.parseColor("#7cd0f5")
                val iceDark = Color.parseColor("#3d9fd8")
                // faceted diamond built from steps, pointing right
                px(cx + s * 0.30f, cy - s * 0.03f, s * 0.06f, s * 0.06f, ice)
                px(cx + s * 0.20f, cy - s * 0.09f, s * 0.11f, s * 0.18f, iceMid)
                px(cx + s * 0.08f, cy - s * 0.14f, s * 0.13f, s * 0.28f, ice)
                px(cx - s * 0.06f, cy - s * 0.10f, s * 0.15f, s * 0.20f, iceMid)
                px(cx - s * 0.18f, cy - s * 0.06f, s * 0.13f, s * 0.12f, iceDark)
                // facet highlight
                px(cx + s * 0.08f, cy - s * 0.10f, s * 0.04f, s * 0.10f, Color.WHITE)
            }
            "poison" -> { // venom glob
                val venom = Color.parseColor("#7CFF5A")
                val venomMid = Color.parseColor("#4CAF3D")
                val venomDark = Color.parseColor("#2E6B24")
                px(cx - s * 0.14f, cy - s * 0.11f, s * 0.30f, s * 0.22f, venomMid)
                px(cx - s * 0.06f, cy - s * 0.15f, s * 0.18f, s * 0.30f, venom)
                px(cx - s * 0.02f, cy - s * 0.08f, s * 0.08f, s * 0.08f, Color.WHITE)
                px(cx + s * 0.14f, cy - s * 0.04f, s * 0.06f, s * 0.06f, venomDark)
                // trailing drips
                px(cx - s * 0.24f, cy - s * 0.03f, s * 0.06f, s * 0.06f, venomMid)
                px(cx - s * 0.32f, cy - s * 0.015f, s * 0.04f, s * 0.04f, venomDark)
            }
            "magic" -> { // lightning bolt
                val bolt = Color.parseColor("#FFEB3B")
                val boltCore = Color.parseColor("#FFFFFF")
                val boltDark = Color.parseColor("#C77F00")
                // zigzag from lower-left to upper-right
                px(cx - s * 0.36f, cy + s * 0.10f, s * 0.14f, s * 0.07f, bolt)
                px(cx - s * 0.24f, cy + s * 0.02f, s * 0.13f, s * 0.10f, boltDark)
                px(cx - s * 0.13f, cy - s * 0.06f, s * 0.13f, s * 0.10f, bolt)
                px(cx - s * 0.01f, cy - s * 0.02f, s * 0.13f, s * 0.10f, boltDark)
                px(cx + s * 0.10f, cy - s * 0.10f, s * 0.13f, s * 0.08f, bolt)
                px(cx + s * 0.20f, cy - s * 0.05f, s * 0.13f, s * 0.07f, boltCore)
                px(cx + s * 0.30f, cy - s * 0.02f, s * 0.07f, s * 0.05f, bolt)
            }
            "fire" -> { // molten ember
                val ember = Color.parseColor("#FFD54F")
                val emberMid = Color.parseColor("#FF6D00")
                val emberDark = Color.parseColor("#BF360C")
                px(cx - s * 0.05f, cy - s * 0.12f, s * 0.20f, s * 0.24f, emberMid)
                px(cx + s * 0.01f, cy - s * 0.07f, s * 0.12f, s * 0.14f, ember)
                px(cx + s * 0.04f, cy - s * 0.03f, s * 0.05f, s * 0.06f, Color.WHITE)
                // trailing flame tongues
                px(cx - s * 0.15f, cy - s * 0.06f, s * 0.09f, s * 0.12f, emberMid)
                px(cx - s * 0.24f, cy - s * 0.03f, s * 0.08f, s * 0.07f, emberDark)
                px(cx - s * 0.31f, cy - s * 0.015f, s * 0.06f, s * 0.035f, emberDark)
            }
            "void" -> { // shadow kunai
                val kunai = Color.parseColor("#6a5acd")
                val kunaiMid = Color.parseColor("#4527a0")
                val kunaiDark = Color.parseColor("#1a1035")
                // blade
                px(cx + s * 0.05f, cy - s * 0.035f, s * 0.30f, s * 0.07f, kunai)
                px(cx + s * 0.30f, cy - s * 0.015f, s * 0.06f, s * 0.03f, kunaiDark)
                // grip + ring
                px(cx - s * 0.18f, cy - s * 0.02f, s * 0.20f, s * 0.04f, kunaiMid)
                px(cx - s * 0.26f, cy - s * 0.06f, s * 0.06f, s * 0.12f, kunaiDark)
                // smoky trail
                px(cx - s * 0.34f, cy - s * 0.02f, s * 0.06f, s * 0.04f, kunaiMid)
            }
            "shield" -> { // energy disc
                val disc = Color.parseColor("#4DA6FF")
                val discLight = Color.parseColor("#B3D9FF")
                val discDark = Color.parseColor("#1a5fb0")
                px(cx - s * 0.16f, cy - s * 0.13f, s * 0.30f, s * 0.26f, disc)
                px(cx - s * 0.08f, cy - s * 0.09f, s * 0.16f, s * 0.18f, discLight)
                px(cx + s * 0.10f, cy - s * 0.05f, s * 0.04f, s * 0.10f, discDark)
                px(cx - s * 0.26f, cy - s * 0.05f, s * 0.07f, s * 0.10f, discDark)
            }
            else -> { // fallback orb
                val col = Color.parseColor("#B388FF")
                px(cx - s * 0.08f, cy - s * 0.10f, s * 0.20f, s * 0.20f, col)
                px(cx - s * 0.02f, cy - s * 0.06f, s * 0.08f, s * 0.08f, Color.WHITE)
            }
        }

        // pixelize with per-element palette derived from the drawing itself
        return PixelArtGenerator.pixelize(hi, pixelSize = 4, outline = true, dither = false)
    }

    /** Soft radial particle (fire, magic, frost, poison, healing...). */
    fun softGlow(color: Int, size: Int = 64, hardness: Float = 0.25f): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(size / 2f, size / 2f, size / 2f,
            Color.argb(255, 255, 255, 255), color, Shader.TileMode.CLAMP)
        // radial with white core fading to colored edge
        p.shader = RadialGradient(size / 2f, size / 2f, size / 2f,
            intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(240, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))),
            floatArrayOf(0f, hardness, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        return bmp
    }

    /** Elongated spark/streak particle. */
    fun spark(color: Int, w: Int = 64, h: Int = 16): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), color, Shader.TileMode.CLAMP)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(230, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(255, 255, 255, 255)),
            floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
        val path = Path()
        path.moveTo(0f, h / 2f)
        path.quadTo(w * 0.6f, 1f, w.toFloat(), h / 2f)
        path.quadTo(w * 0.6f, h - 1f, 0f, h / 2f)
        c.drawPath(path, p)
        return bmp
    }

    /** Smoke puff with noisy alpha. */
    fun smoke(size: Int = 64, seed: Long = 21L): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        for (i in 0 until 6) {
            val bx = size / 2f + (rng.nextFloat() - 0.5f) * size * 0.3f
            val by = size / 2f + (rng.nextFloat() - 0.5f) * size * 0.3f
            val br = size * (0.18f + rng.nextFloat() * 0.16f)
            p.shader = RadialGradient(bx, by, br, Color.argb(70, 130, 132, 150), Color.argb(0, 130, 132, 150), Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, br, p)
        }
        return bmp
    }

    /** Ring (AoE telegraphs, shockwaves). */
    fun ring(color: Int, size: Int = 128, thickness: Float = 0.12f): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        val r = size * 0.44f
        p.strokeWidth = size * thickness
        p.shader = RadialGradient(size / 2f, size / 2f, r,
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), color, Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, r - p.strokeWidth / 2f, p)
        return bmp
    }

    /** Melee slash arc (crescent). */
    fun slash(color: Int, size: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = size * 0.10f
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
            Color.argb(0, 255, 255, 255), color, Shader.TileMode.CLAMP)
        c.drawArc(RectF(size * 0.08f, size * 0.08f, size * 0.92f, size * 0.92f), -60f, 120f, false, p)
        p.shader = null
        p.color = Color.argb(230, 255, 255, 255)
        p.strokeWidth = size * 0.035f
        c.drawArc(RectF(size * 0.14f, size * 0.14f, size * 0.86f, size * 0.86f), -45f, 90f, false, p)
        return bmp
    }

    /** 4-point star (crit spark / XP glints). */
    fun star(color: Int, size: Int = 64): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val r = size * 0.48f
        val path = Path()
        for (i in 0 until 8) {
            val a = i / 8f * 6.28318f
            val rr = if (i % 2 == 0) r else r * 0.22f
            val x = cx + cos(a) * rr
            val y = cx + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.shader = RadialGradient(cx, cx, r, Color.argb(255, 255, 255, 255), color, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        return bmp
    }

    /** Explosion animation frames: fireball expand + smoke + ring. */
    fun explosionFrames(frames: Int = 8, size: Int = 192): List<Bitmap> {
        return (0 until frames).map { i ->
            val t = i / (frames - 1).toFloat()
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = size / 2f
            val cy = size / 2f
            val fireR = size * (0.14f + 0.34f * (1f - (1f - t) * (1f - t)))
            if (t < 0.85f) {
                val fireColors = intArrayOf(
                    Color.argb((255 * (1f - t * 0.7f)).toInt(), 255, 250, 205),
                    Color.argb((230 * (1f - t * 0.5f)).toInt(), 255, 152, 40),
                    Color.argb((180 * (1f - t)).toInt(), 200, 60, 20),
                    Color.argb(0, 120, 40, 20)
                )
                p.shader = RadialGradient(cx, cy, fireR, fireColors, floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, fireR, p)
                p.shader = null
            }
            // shock ring expands late
            if (t > 0.3f) {
                val rr = size * (0.2f + 0.28f * t)
                p.style = Paint.Style.STROKE
                p.strokeWidth = size * 0.05f * (1f - t)
                p.color = Color.argb((170 * (1f - t)).toInt(), 255, 220, 150)
                c.drawCircle(cx, cy, rr, p)
                p.style = Paint.Style.FILL
            }
            // smoke late
            if (t > 0.5f) {
                val rng = Random(i * 17 + 3)
                for (j in 0 until 5) {
                    val sx = cx + (rng.nextFloat() - 0.5f) * fireR * 1.5f
                    val sy = cy + (rng.nextFloat() - 0.5f) * fireR * 1.5f - t * 14f
                    p.shader = RadialGradient(sx, sy, fireR * 0.5f,
                        Color.argb((90 * (t - 0.5f) * 2f).toInt().coerceIn(0, 90), 110, 110, 125),
                        Color.argb(0, 110, 110, 125), Shader.TileMode.CLAMP)
                    c.drawCircle(sx, sy, fireR * 0.5f, p)
                    p.shader = null
                }
            }
            bmp
        }
    }

    /* ---------- status effect icons (48x48) ---------- */

    fun statusIcon(kind: StatusIconKind, size: Int = 48): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        when (kind) {
            StatusIconKind.STUN -> {
                // spiral stars
                p.color = Color.parseColor("#FFEB3B")
                for (i in 0 until 3) {
                    val a = i / 3f * 6.28318f
                    val x = cx + cos(a) * size * 0.24f
                    val y = cx + sin(a) * size * 0.24f
                    drawMiniStar(c, p, x, y, size * 0.14f, Color.parseColor("#FFEB3B"))
                }
            }
            StatusIconKind.SLOW -> {
                p.color = Color.parseColor("#4FC3F7")
                p.style = Paint.Style.STROKE
                p.strokeWidth = size * 0.08f
                c.drawArc(RectF(size * 0.2f, size * 0.2f, size * 0.8f, size * 0.8f), 100f, 300f, false, p)
                p.style = Paint.Style.FILL
                // droplet
                val path = Path()
                path.moveTo(cx, size * 0.75f)
                path.quadTo(size * 0.3f, size * 0.5f, cx, size * 0.28f)
                path.quadTo(size * 0.7f, size * 0.5f, cx, size * 0.75f)
                c.drawPath(path, p)
            }
            StatusIconKind.BURN -> {
                val path = Path()
                path.moveTo(cx, size * 0.15f)
                path.quadTo(size * 0.82f, size * 0.5f, cx + size * 0.14f, size * 0.72f)
                path.quadTo(cx, size * 0.9f, cx - size * 0.14f, size * 0.72f)
                path.quadTo(size * 0.18f, size * 0.5f, cx, size * 0.15f)
                p.shader = LinearGradient(cx, size * 0.15f, cx, size * 0.9f,
                    Color.parseColor("#FFF176"), Color.parseColor("#E53935"), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
                p.shader = null
            }
            StatusIconKind.POISON -> {
                val path = Path()
                path.moveTo(cx, size * 0.15f)
                path.quadTo(size * 0.78f, size * 0.52f, cx + size * 0.13f, size * 0.7f)
                path.quadTo(cx, size * 0.88f, cx - size * 0.13f, size * 0.7f)
                path.quadTo(size * 0.22f, size * 0.52f, cx, size * 0.15f)
                p.shader = LinearGradient(cx, 0f, cx, size.toFloat(),
                    Color.parseColor("#8BC34A"), Color.parseColor("#33691E"), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
                p.shader = null
                p.color = Color.argb(160, 210, 245, 160)
                c.drawCircle(cx - size * 0.06f, size * 0.6f, size * 0.05f, p)
            }
            StatusIconKind.FREEZE -> {
                p.color = Color.parseColor("#B3E5FC")
                p.strokeWidth = size * 0.07f
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 3) {
                    val a = i / 3f * 3.14159f
                    val x1 = cx + cos(a) * size * 0.36f
                    val y1 = cx + sin(a) * size * 0.36f
                    c.drawLine(cx - x1 + cx, cx - y1 + cx, x1, y1, p)
                }
            }
            StatusIconKind.KNOCKBACK -> {
                p.color = Color.parseColor("#FFAB40")
                p.strokeWidth = size * 0.09f
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(size * 0.18f, cx, size * 0.78f, cx, p)
                // arrow head
                val path = Path()
                path.moveTo(size * 0.7f, size * 0.28f)
                path.lineTo(size * 0.88f, cx)
                path.lineTo(size * 0.7f, size * 0.72f)
                p.style = Paint.Style.STROKE
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
            }
        }
        return bmp
    }

    private fun drawMiniStar(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        val path = Path()
        for (i in 0 until 10) {
            val a = i / 10f * 6.28318f
            val rr = if (i % 2 == 0) r else r * 0.4f
            val px = x + cos(a) * rr
            val py = y + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.color = color
        c.drawPath(path, p)
    }

    /** XP orb with pulsing core (2 frames). */
    fun xpOrb(size: Int = 32): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        p.shader = RadialGradient(cx, cx, size * 0.5f,
            intArrayOf(Color.argb(255, 240, 255, 240), Color.argb(255, 124, 255, 107), Color.argb(0, 60, 200, 60)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, size * 0.5f, p)
        return bmp
    }

    /** Gold coin drop. */
    fun goldCoin(size: Int = 32): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        p.shader = RadialGradient(cx - size * 0.1f, cx - size * 0.1f, size * 0.55f,
            Color.parseColor("#FFE082"), Color.parseColor("#F57F17"), Shader.TileMode.CLAMP)
        p.setShadowLayer(4f, 0f, 0f, Color.argb(150, 255, 200, 60))
        c.drawCircle(cx, cx, size * 0.42f, p)
        p.clearShadowLayer()
        p.color = Color.argb(200, 255, 245, 200)
        p.textSize = size * 0.5f
        p.textAlign = Paint.Align.CENTER
        c.drawText("G", cx, cx + size * 0.17f, p)
        return bmp
    }

    /** Hero shard drop. */
    fun shard(color: Int, size: Int = 32): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val path = Path()
        path.moveTo(cx, size * 0.08f)
        path.lineTo(cx + size * 0.32f, cx)
        path.lineTo(cx, size * 0.92f)
        path.lineTo(cx - size * 0.32f, cx)
        path.close()
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), Color.WHITE, color, Shader.TileMode.CLAMP)
        p.setShadowLayer(6f, 0f, 0f, color)
        c.drawPath(path, p)
        p.clearShadowLayer()
        return bmp
    }
}

enum class StatusIconKind { STUN, SLOW, BURN, POISON, FREEZE, KNOCKBACK }
