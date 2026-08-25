package com.towerbound.laststand.assets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * PixelArtGenerator — the pixel-art production pipeline (visual direction:
 * "premium modern 2D pixel art, deliberate pixel clusters, controlled
 * palettes, outlines, shadows, highlights, dithering, strong silhouettes").
 *
 * Strategy: every asset is first painted at HIGH source resolution by the
 * existing Canvas painters (rigs, gradients, glow), then converted into a
 * controlled pixel-art version:
 *
 *   1. downsample to a low-res pixel grid (box filtering),
 *   2. snap colors to a controlled palette (quantization with clustering),
 *   3. ordered Bayer dithering between the two nearest palette entries,
 *   4. dark outline pass around the silhouette,
 *   5. nearest-neighbor upscale back to gameplay resolution.
 *
 * The result is genuine pixel art — crisp pixel clusters, no blur, no smooth
 * vector look — derived from the detailed high-res masters.
 */
object PixelArtGenerator {

    /** Ordered 4x4 Bayer matrix (threshold 0..15). */
    private val BAYER = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    )

    /* ================================================================ */
    /* Public pipeline                                                    */
    /* ================================================================ */

    /**
     * Convert a high-res ARGB bitmap into pixel art.
     *
     * @param pixelSize source pixels per art-pixel (e.g. 4 -> 256px master
     *                  becomes a 64x64 pixel-art grid).
     * @param outline   add a dark outline around the silhouette.
     * @param dither    enable ordered dithering between palette entries.
     * @param paletteColors optional explicit palette; when null a palette is
     *                  extracted from the image by color clustering.
     */
    fun pixelize(
        src: Bitmap,
        pixelSize: Int = 4,
        outline: Boolean = true,
        dither: Boolean = true,
        paletteColors: IntArray? = null,
        outlineColor: Int = Color.argb(255, 16, 12, 28)
    ): Bitmap {
        val w = src.width
        val h = src.height
        val gw = max(1, w / pixelSize)
        val gh = max(1, h / pixelSize)

        // 1) box-filter downsample to grid (bulk pixel read for speed)
        val srcPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val grid = IntArray(gw * gh)
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                var r = 0; var g = 0; var b = 0; var a = 0; var n = 0
                for (py in 0 until pixelSize) {
                    val sy = gy * pixelSize + py
                    if (sy >= h) continue
                    val rowOff = sy * w + gx * pixelSize
                    for (px in 0 until pixelSize) {
                        val sx = gx * pixelSize + px
                        if (sx >= w) continue
                        val c = srcPixels[rowOff + px]
                        val ca = (c ushr 24) and 0xFF
                        r += ((c ushr 16) and 0xFF) * ca
                        g += ((c ushr 8) and 0xFF) * ca
                        b += (c and 0xFF) * ca
                        a += ca
                        n++
                    }
                }
                grid[gy * gw + gx] = if (n == 0 || a == 0) Color.TRANSPARENT else Color.argb(
                    (a / n).coerceIn(0, 255),
                    (r / a).coerceIn(0, 255),
                    (g / a).coerceIn(0, 255),
                    (b / a).coerceIn(0, 255)
                )
            }
        }

        // 2) palette
        val palette = paletteColors ?: extractPalette(grid, maxColors = 28)

        // 3) quantize + dither (pure grid logic — JVM unit-testable)
        quantizeGrid(grid, gw, gh, palette, dither)

        // 4) outline pass — dark rim around opaque silhouette
        if (outline) {
            addOutline(grid, gw, gh, outlineColor)
        }

        // 5) nearest-neighbor upscale back to original size
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cnv = Canvas(out)
        val gridBmp = Bitmap.createBitmap(grid, gw, gh, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = false // nearest neighbor
        }
        // draw scaled grid; bilinear OFF via filterBitmap=false on software canvas scale
        val matrix = android.graphics.Matrix().apply {
            setScale(w.toFloat() / gw, h.toFloat() / gh)
        }
        cnv.drawBitmap(gridBmp, matrix, paint)
        return out
    }

    /* ================================================================ */
    /* Pure grid algorithms (no android.graphics — JVM unit-testable)     */
    /* ================================================================ */

    /** Quantize an ARGB grid in place onto [palette] with optional Bayer dithering. */
    fun quantizeGrid(grid: IntArray, gw: Int, gh: Int, palette: IntArray, dither: Boolean) {
        for (i in grid.indices) {
            val c = grid[i]
            if ((c ushr 24) < 12) { grid[i] = TRANSPARENT; continue }
            val best = nearest(palette, c)
            val second = nearestExcluding(palette, c, best)
            val d = if (dither && second != best) {
                val gx = i % gw
                val gy = i / gw
                val threshold = (BAYER[(gy and 3) * 4 + (gx and 3)] + 1) / 16f
                // distance-weighted dithering choice
                val dBest = dist2(best, c)
                val dSecond = dist2(second, c)
                val total = dBest + dSecond
                if (total <= 0f) best
                else if (dBest / total < threshold) best else second
            } else best
            grid[i] = d
        }
    }

    /** Add a dark outline around the opaque silhouette (in place). */
    fun addOutline(grid: IntArray, gw: Int, gh: Int, outlineColor: Int) {
        val outlined = grid.copyOf()
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                val i = gy * gw + gx
                if ((grid[i] ushr 24) >= 12) continue
                val neighborOpaque = (gy > 0 && (grid[i - gw] ushr 24) >= 12) ||
                        (gy < gh - 1 && (grid[i + gw] ushr 24) >= 12) ||
                        (gx > 0 && (grid[i - 1] ushr 24) >= 12) ||
                        (gx < gw - 1 && (grid[i + 1] ushr 24) >= 12)
                if (neighborOpaque) outlined[i] = outlineColor
            }
        }
        System.arraycopy(outlined, 0, grid, 0, grid.size)
    }

    /** Pixel color of the beveled panel chrome at (x,y). Pure function. */
    fun panelColorAt(
        x: Int, y: Int, w: Int, h: Int, step: Int,
        base: Int, border: Int, highlight: Int, shadow: Int
    ): Int {
        val edgeDist = min(min(x, y), min(w - 1 - x, h - 1 - y))
        return when {
            edgeDist == 0 -> border
            edgeDist <= step -> {
                val topOrLeft = (y <= step && x > edgeDist && (w - 1 - x) > edgeDist) ||
                        (x <= step && y > edgeDist && (h - 1 - y) > edgeDist)
                val bottomOrRight = (h - 1 - y <= step && x > edgeDist && (w - 1 - x) > edgeDist) ||
                        (w - 1 - x <= step && y > edgeDist && (h - 1 - y) > edgeDist)
                when {
                    topOrLeft && !bottomOrRight -> highlight
                    bottomOrRight && !topOrLeft -> shadow
                    else -> base
                }
            }
            else -> base
        }
    }

    /** Pixel color of the rarity frame at (x,y). Pure function. */
    fun rarityColorAt(x: Int, y: Int, size: Int, rarityColor: Int): Int {
        val dark = darken(rarityColor, 0.45f)
        val light = lighten(rarityColor, 0.35f)
        val d = min(min(x, y), min(size - 1 - x, size - 1 - y))
        return when (d) {
            0 -> dark
            1 -> rarityColor
            2 -> light
            3 -> rarityColor
            else -> TRANSPARENT
        }
    }

    /* ================================================================ */
    /* Palette extraction (simple k-means-ish median cut)                 */
    /* ================================================================ */

    /** Median-cut style palette extraction from opaque pixels. */
    fun extractPalette(pixels: IntArray, maxColors: Int): IntArray {
        val opaque = ArrayList<Int>(pixels.size / 3)
        for (c in pixels) {
            if ((c ushr 24) >= 12) opaque.add(c and 0xFFFFFF or (0xFF shl 24))
        }
        if (opaque.isEmpty()) return intArrayOf(0xFFFF00FF.toInt())
        var buckets: MutableList<List<Int>> = mutableListOf(opaque)
        while (buckets.size < maxColors) {
            // split the bucket with the largest channel range
            var bestIdx = -1
            var bestRange = -1
            var bestChannel = -1
            for (i in buckets.indices) {
                val (range, channel) = channelRange(buckets[i])
                if (range > bestRange) { bestRange = range; bestIdx = i; bestChannel = channel }
            }
            if (bestRange < 8 || bestIdx < 0) break // converged
            val bucket = buckets[bestIdx]
            val ch = bestChannel
            val sorted = bucket.sortedBy { c ->
                when (ch) { 0 -> (c ushr 16) and 0xFF; 1 -> (c ushr 8) and 0xFF; else -> c and 0xFF }
            }
            val mid = sorted.size / 2
            buckets = buckets.toMutableList().apply {
                removeAt(bestIdx)
                add(sorted.subList(0, mid))
                add(sorted.subList(mid, sorted.size))
            }
        }
        val pal = IntArray(buckets.size)
        for (i in buckets.indices) {
            var r = 0L; var g = 0L; var b = 0L
            for (c in buckets[i]) { r += (c ushr 16) and 0xFF; g += (c ushr 8) and 0xFF; b += c and 0xFF }
            val n = buckets[i].size.toLong()
            pal[i] = rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }
        return pal
    }

    private fun channelRange(list: List<Int>): Pair<Int, Int> {
        var rmin = 255; var rmax = 0; var gmin = 255; var gmax = 0; var bmin = 255; var bmax = 0
        for (c in list) {
            val r = (c ushr 16) and 0xFF; val g = (c ushr 8) and 0xFF; val b = c and 0xFF
            if (r < rmin) rmin = r; if (r > rmax) rmax = r
            if (g < gmin) gmin = g; if (g > gmax) gmax = g
            if (b < bmin) bmin = b; if (b > bmax) bmax = b
        }
        val rr = rmax - rmin; val gr = gmax - gmin; val br = bmax - bmin
        return if (rr >= gr && rr >= br) Pair(rr, 0)
        else if (gr >= br) Pair(gr, 1)
        else Pair(br, 2)
    }

    private fun nearest(palette: IntArray, c: Int): Int {
        var best = palette[0]
        var bd = Float.MAX_VALUE
        for (p in palette) {
            val d = dist2(p, c)
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    private fun nearestExcluding(palette: IntArray, c: Int, exclude: Int): Int {
        var best = exclude
        var bd = Float.MAX_VALUE
        for (p in palette) {
            if (p == exclude) continue
            val d = dist2(p, c)
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    private fun dist2(a: Int, b: Int): Float {
        val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
        val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return (dr * dr + dg * dg + db * db).toFloat()
    }

    /* ================================================================ */
    /* Pixel-art UI chrome                                                */
    /* ================================================================ */

    /**
     * Generate a pixel-art panel/button background bitmap with beveled edges
     * and a stepped border (menu/UI chrome in the game's visual language).
     *
     * @param w,h output size in pixels
     * @param base fill color
     * @param border border color
     * @param highlight top-left light edge color
     * @param shadow bottom-right dark edge color
     * @param step  bevel thickness in art pixels
     */
    fun panel(
        w: Int, h: Int,
        base: Int,
        border: Int,
        highlight: Int,
        shadow: Int,
        step: Int = 3
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                bmp.setPixel(x, y, panelColorAt(x, y, w, h, step, base, border, highlight, shadow))
            }
        }
        return bmp
    }

    /**
     * Pixel-art frame with rarity coloring (collection cards, hero portraits).
     */
    fun rarityFrame(size: Int, rarityColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val c = rarityColorAt(x, y, size, rarityColor)
                if ((c ushr 24) != 0) bmp.setPixel(x, y, c)
            }
        }
        return bmp
    }

    /** Darken channel-wise (pure int math, no framework dependency). */
    fun darken(c: Int, f: Float): Int = rgb(
        (((c ushr 16) and 0xFF) * (1f - f)).toInt().coerceIn(0, 255),
        (((c ushr 8) and 0xFF) * (1f - f)).toInt().coerceIn(0, 255),
        ((c and 0xFF) * (1f - f)).toInt().coerceIn(0, 255)
    )

    /** Lighten channel-wise (pure int math, no framework dependency). */
    fun lighten(c: Int, f: Float): Int = rgb(
        (((c ushr 16) and 0xFF) + (255 - ((c ushr 16) and 0xFF)) * f).toInt().coerceIn(0, 255),
        (((c ushr 8) and 0xFF) + (255 - ((c ushr 8) and 0xFF)) * f).toInt().coerceIn(0, 255),
        ((c and 0xFF) + (255 - (c and 0xFF)) * f).toInt().coerceIn(0, 255)
    )

    /** Pure ARGB compose (framework-free for JVM tests). */
    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Fully transparent ARGB constant (framework-free). */
    const val TRANSPARENT: Int = 0x00000000

    /** Check distance for tests. */
    fun colorDistance(a: Int, b: Int): Float = sqrt(dist2(a, b))

    /** Absolute diff helper used by tests. */
    fun absDiff(a: Int, b: Int): Int = abs(a - b)
}
