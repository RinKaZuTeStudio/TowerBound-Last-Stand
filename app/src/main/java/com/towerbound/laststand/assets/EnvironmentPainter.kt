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
 * Environment asset generation: terrain tiles, trees, bushes, rocks, water,
 * grass tufts, clouds. Everything seeded for procedural variation.
 */
object EnvironmentPainter {

    /* ---------- seamless pixel-art terrain ---------- */

    /**
     * Tileable pixel-art grass tile: the pattern wraps on both axes so any
     * number of adjacent tiles form one continuous meadow with no visible
     * grid seams (fixes the rectangular tile seam problem at the root).
     *
     * Painted at high resolution first, then converted to a controlled
     * pixel-art grid (32x32 art pixels) with wrapped decoration clusters.
     */
    fun seamlessTerrain(outSize: Int = 256, seed: Long = 1337L): Bitmap {
        val grid = 32 // art-pixel grid
        val hi = outSize
        val hiBmp = Bitmap.createBitmap(hi, hi, Bitmap.Config.ARGB_8888)
        val c = Canvas(hiBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        val s = hi.toFloat()

        // base meadow: soft vertical banding
        p.shader = LinearGradient(0f, 0f, s * 0.3f, s,
            Color.parseColor("#417F3C"), Color.parseColor("" + "#336630"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        p.shader = null

        // wrapped large tonal blobs (draw 9x for toroidal wrap)
        fun wrapBlob(bx: Float, by: Float, br: Float, col: Int, alpha: Int) {
            for (ox in intArrayOf(-1, 0, 1)) {
                for (oy in intArrayOf(-1, 0, 1)) {
                    val x = bx + ox * s
                    val y = by + oy * s
                    if (x + br < 0 || x - br > s || y + br < 0 || y - br > s) continue
                    p.shader = RadialGradient(x, y, br,
                        Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)),
                        Color.argb(0, Color.red(col), Color.green(col), Color.blue(col)), Shader.TileMode.CLAMP)
                    c.drawCircle(x, y, br, p)
                    p.shader = null
                }
            }
        }
        repeat(7) {
            wrapBlob(rng.nextFloat() * s, rng.nextFloat() * s, s * (0.16f + rng.nextFloat() * 0.2f),
                Color.parseColor("#4E9245"), 70)
        }
        repeat(5) {
            wrapBlob(rng.nextFloat() * s, rng.nextFloat() * s, s * (0.12f + rng.nextFloat() * 0.16f),
                Color.parseColor("#2C572A"), 80)
        }
        // soil patches (wrapped)
        repeat(6) {
            wrapBlob(rng.nextFloat() * s, rng.nextFloat() * s, s * (0.05f + rng.nextFloat() * 0.06f),
                Color.parseColor("#6B5236"), 60)
        }

        // wrapped grass blade clusters (tiny strokes)
        p.strokeWidth = s / grid * 0.9f
        p.strokeCap = Paint.Cap.ROUND
        repeat(150) {
            val bx = rng.nextFloat() * s
            val by = rng.nextFloat() * s
            val len = s / grid * (1f + rng.nextFloat() * 1.6f)
            val shade = 0.72f + rng.nextFloat() * 0.55f
            p.color = Color.rgb((62 * shade).toInt().coerceIn(0, 255), (130 * shade).toInt().coerceIn(0, 255), (58 * shade).toInt().coerceIn(0, 255))
            val dx = (rng.nextFloat() - 0.5f) * len
            c.drawLine(bx, by, bx + dx, by - len, p)
            // wrap copies when near edges
            if (bx < len) c.drawLine(bx + s, by, bx + s + dx, by - len, p)
            if (bx > s - len) c.drawLine(bx - s, by, bx - s + dx, by - len, p)
            if (by < len) c.drawLine(bx, by + s, bx + dx, by + s - len, p)
            if (by > s - len) c.drawLine(bx, by - s, bx + dx, by - s - len, p)
        }
        // tiny flowers (wrapped)
        repeat(10) {
            val fx = rng.nextFloat() * s
            val fy = rng.nextFloat() * s
            p.color = if (rng.nextBoolean()) Color.argb(220, 255, 238, 130) else Color.argb(200, 255, 255, 255)
            val r = s / grid * 0.55f
            c.drawCircle(fx, fy, r, p)
            if (fx < r) c.drawCircle(fx + s, fy, r, p)
            if (fx > s - r) c.drawCircle(fx - s, fy, r, p)
            if (fy < r) c.drawCircle(fx, fy + s, r, p)
            if (fy > s - r) c.drawCircle(fx, fy - s, r, p)
        }

        // pixel-art conversion: art pixels = outSize/grid
        return PixelArtGenerator.pixelize(
            hiBmp,
            pixelSize = outSize / grid,
            outline = false,
            dither = true,
            paletteColors = intArrayOf(
                Color.parseColor("#294d27"), Color.parseColor("#2f5c2c"),
                Color.parseColor("#336630"), Color.parseColor("#3a7335"),
                Color.parseColor("#417f3c"), Color.parseColor("#4a8c42"),
                Color.parseColor("#559a4c"), Color.parseColor("#5fa955"),
                Color.parseColor("#6b5236"), Color.parseColor("#7d6244"),
                Color.parseColor("#fff4b0"), Color.parseColor("#ffffff")
            )
        )
    }

    /* ---------- menu scene sky + distant hills ---------- */

    /**
     * Cinematic dusk sky: deep indigo zenith -> violet -> warm amber horizon
     * with scattered pixel stars. Rendered as chunky pixel bands (stylized
     * sky, not a smooth vector gradient).
     */
    fun skyGradient(w: Int = 64, h: Int = 512): Bitmap {
        val hi = Bitmap.createBitmap(w * 4, h * 4, Bitmap.Config.ARGB_8888)
        val c = Canvas(hi)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = hi.height.toFloat()
        p.shader = LinearGradient(
            0f, 0f, 0f, s,
            intArrayOf(
                Color.parseColor("#0b0f2a"),
                Color.parseColor("#1a1f4d"),
                Color.parseColor("#3c2a6e"),
                Color.parseColor("#7a3b6e"),
                Color.parseColor("#c96a4a"),
                Color.parseColor("#f2a65a")
            ),
            floatArrayOf(0f, 0.34f, 0.55f, 0.72f, 0.88f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, hi.width.toFloat(), s, p)
        p.shader = null
        // pixel stars in the upper half
        val rng = Random(99L)
        for (i in 0 until 46) {
            val sx = rng.nextFloat() * hi.width
            val sy = rng.nextFloat() * s * 0.5f
            val star = 255 - (rng.nextFloat() * 90).toInt()
            p.color = Color.argb(star, 255, 250, 235)
            val r = if (rng.nextFloat() < 0.2f) hi.width / 26f else hi.width / 40f
            c.drawCircle(sx, sy, r, p)
        }
        return PixelArtGenerator.pixelize(hi, pixelSize = 16, outline = false, dither = true)
    }

    /** Rolling silhouette hills for the parallax horizon. */
    fun hills(seed: Long, tint: String, w: Int = 720, h: Int = 220): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        val col = Color.parseColor(tint)
        val path = Path()
        path.moveTo(0f, h.toFloat())
        var x = 0f
        var y = h * (0.45f + rng.nextFloat() * 0.15f)
        path.lineTo(0f, y)
        val peaks = 6
        for (i in 1..peaks) {
            val nx = i / peaks.toFloat() * w
            val ny = h * (0.3f + rng.nextFloat() * 0.4f)
            path.quadTo((x + nx) / 2f, kotlin.math.min(y, ny) - h * 0.14f, nx, ny)
            x = nx; y = ny
        }
        path.lineTo(w.toFloat(), h.toFloat())
        path.close()
        p.color = col
        c.drawPath(path, p)
        // subtle top rim light
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = PixelArtGenerator.lighten(col, 0.25f)
        c.drawPath(path, p)
        return PixelArtGenerator.pixelize(bmp, pixelSize = 6, outline = false, dither = true)
    }

    /* ---------- terrain tile (seamless-ish grass) ---------- */

    fun terrainTile(seed: Long = 1L, size: Int = 256): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)

        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
            Color.parseColor("#3E7C3A"), Color.parseColor("#2E5C2C"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null

        // grass blade noise
        for (i in 0 until 620) {
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            val h = 2f + rng.nextFloat() * 5f
            val shade = 0.75f + rng.nextFloat() * 0.5f
            p.color = Color.rgb((62 * shade).toInt().coerceIn(0, 255), (124 * shade).toInt().coerceIn(0, 255), (58 * shade).toInt().coerceIn(0, 255))
            p.strokeWidth = 1.6f
            c.drawLine(x, y, x + rng.nextFloat() * 2f - 1f, y - h, p)
        }
        // soil patches
        for (i in 0 until 10) {
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            p.color = Color.argb(60, 96, 74, 48)
            c.drawOval(RectF(x - 10f - rng.nextFloat() * 8f, y - 4f, x + 10f + rng.nextFloat() * 8f, y + 4f), p)
        }
        // tiny flowers
        for (i in 0 until 14) {
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            p.color = if (rng.nextBoolean()) Color.argb(220, 255, 238, 130) else Color.argb(200, 255, 255, 255)
            c.drawCircle(x, y, 1.8f, p)
        }
        return bmp
    }

    /* ---------- tree ---------- */

    fun tree(seed: Long = 7L, w: Int = 160, h: Int = 224): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)

        // trunk
        val trunkW = w * 0.16f
        p.shader = LinearGradient(w / 2f - trunkW / 2f, 0f, w / 2f + trunkW / 2f, 0f,
            Color.parseColor("#6D4C33"), Color.parseColor("#3E2A1B"), Shader.TileMode.CLAMP)
        c.drawRect(w / 2f - trunkW / 2f, h * 0.52f, w / 2f + trunkW / 2f, h - 6f, p)
        p.shader = null
        // roots
        p.color = Color.parseColor("#3E2A1B")
        val root = Path()
        root.moveTo(w / 2f - trunkW / 2f, h - 20f)
        root.quadTo(w / 2f - trunkW, h - 2f, w / 2f - trunkW * 1.6f, h - 4f)
        root.quadTo(w / 2f - trunkW * 0.6f, h - 8f, w / 2f - trunkW / 2f, h - 12f)
        c.drawPath(root, p)

        // canopy blobs
        val blobs = 7
        val canopyCx = w / 2f
        val canopyCy = h * 0.34f
        for (i in 0 until blobs) {
            val bx = canopyCx + (rng.nextFloat() - 0.5f) * w * 0.42f
            val by = canopyCy + (rng.nextFloat() - 0.5f) * h * 0.18f
            val br = w * (0.16f + rng.nextFloat() * 0.12f)
            p.shader = RadialGradient(bx - br * 0.3f, by - br * 0.3f, br,
                Color.parseColor("#5BA14E"), Color.parseColor("#2E5C2C"), Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, br, p)
            p.shader = null
        }
        // top highlight
        p.color = Color.argb(70, 190, 240, 150)
        c.drawCircle(canopyCx - w * 0.05f, canopyCy - h * 0.08f, w * 0.16f, p)
        return bmp
    }

    /* ---------- bush ---------- */

    fun bush(seed: Long = 3L, size: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        for (i in 0 until 5) {
            val bx = size / 2f + (rng.nextFloat() - 0.5f) * size * 0.4f
            val by = size * 0.62f + (rng.nextFloat() - 0.5f) * size * 0.18f
            val br = size * (0.2f + rng.nextFloat() * 0.14f)
            p.shader = RadialGradient(bx - br * 0.3f, by - br * 0.3f, br,
                Color.parseColor("#4E9244"), Color.parseColor("#2C572A"), Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, br, p)
            p.shader = null
        }
        return bmp
    }

    /* ---------- rock ---------- */

    fun rock(seed: Long = 5L, size: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        val rock = Path()
        val pts = 8
        for (i in 0 until pts) {
            val a = i / pts.toFloat() * 6.28318f
            val r = size * (0.28f + rng.nextFloat() * 0.14f)
            val x = size / 2f + kotlin.math.cos(a) * r * 1.25f
            val y = size * 0.58f + kotlin.math.sin(a) * r
            if (i == 0) rock.moveTo(x, y) else rock.lineTo(x, y)
        }
        rock.close()
        p.shader = LinearGradient(0f, size * 0.2f, 0f, size * 0.8f,
            Color.parseColor("#9EA7BB"), Color.parseColor("#5A6380"), Shader.TileMode.CLAMP)
        c.drawPath(rock, p)
        p.shader = null
        p.color = Color.argb(90, 40, 44, 66)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        c.drawPath(rock, p)
        p.style = Paint.Style.FILL
        // ground shadow
        p.color = Color.argb(60, 0, 0, 0)
        c.drawOval(RectF(size * 0.2f, size * 0.86f, size * 0.8f, size * 0.94f), p)
        return bmp
    }

    /* ---------- water ripple frames (4) ---------- */

    fun waterFrames(size: Int = 128, frames: Int = 4): List<Bitmap> {
        return (0 until frames).map { f ->
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(),
                Color.parseColor("#1E6EA0"), Color.parseColor("#12507C"), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
            p.shader = null
            val phase = f / frames.toFloat() * 6.28318f
            for (i in 0 until 7) {
                val y = (i + 0.5f) / 7f * size
                p.color = Color.argb(46, 160, 220, 255)
                p.strokeWidth = 2.4f
                val path = Path()
                path.moveTo(0f, y)
                var x = 0f
                while (x < size) {
                    path.lineTo(x, y + sin(x / size * 12.566f + phase + i) * 3f)
                    x += 8f
                }
                c.drawPath(path, p)
            }
            // sparkles
            val rng = Random(f * 31 + 7)
            for (i in 0 until 5) {
                val sx = rng.nextFloat() * size
                val sy = rng.nextFloat() * size
                p.color = Color.argb(140, 200, 240, 255)
                c.drawCircle(sx, sy, 1.6f, p)
            }
            bmp
        }
    }

    /* ---------- grass tuft (animated sway by renderer) ---------- */

    fun grassTuft(seed: Long = 11L, w: Int = 64, h: Int = 48): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 9) {
            val bx = w * 0.3f + rng.nextFloat() * w * 0.4f
            val tipX = bx + (rng.nextFloat() - 0.5f) * w * 0.3f
            val tipY = h * (0.1f + rng.nextFloat() * 0.25f)
            val green = 0.7f + rng.nextFloat() * 0.55f
            p.color = Color.rgb((70 * green).toInt().coerceIn(0, 255), (140 * green).toInt().coerceIn(0, 255), (60 * green).toInt().coerceIn(0, 255))
            p.strokeWidth = 3.4f
            c.drawLine(bx, h - 2f, tipX, tipY, p)
        }
        return bmp
    }

    /* ---------- cloud ---------- */

    fun cloud(seed: Long = 13L, w: Int = 256, h: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(seed)
        for (i in 0 until 8) {
            val bx = w * 0.2f + rng.nextFloat() * w * 0.6f
            val by = h * 0.55f + (rng.nextFloat() - 0.5f) * h * 0.25f
            val br = h * (0.18f + rng.nextFloat() * 0.2f)
            p.shader = RadialGradient(bx, by - br * 0.2f, br,
                Color.argb(235, 250, 252, 255), Color.argb(200, 205, 216, 235), Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, br, p)
            p.shader = null
        }
        return bmp
    }
}
