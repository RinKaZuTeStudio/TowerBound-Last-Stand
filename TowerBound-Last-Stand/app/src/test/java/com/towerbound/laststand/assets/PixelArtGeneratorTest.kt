package com.towerbound.laststand.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PixelArtGenerator pipeline tests — pure grid algorithms (framework-free,
 * JVM-runnable): palette extraction, quantization + Bayer dithering,
 * outline pass, panel chrome and rarity frames.
 */
class PixelArtGeneratorTest {

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /* ---------------- palette extraction ---------------- */

    @Test
    fun `flat input extracts a single color palette`() {
        val px = IntArray(16) { argb(200, 40, 90) }
        val pal = PixelArtGenerator.extractPalette(px, maxColors = 8)
        assertEquals(1, pal.size)
        assertEquals(argb(200, 40, 90), pal[0])
    }

    @Test
    fun `transparent pixels are ignored by palette extraction`() {
        val px = IntArray(8) { if (it < 4) argb(10, 200, 30) else 0x00000000 }
        val pal = PixelArtGenerator.extractPalette(px, maxColors = 8)
        assertEquals(1, pal.size)
        assertEquals(argb(10, 200, 30), pal[0])
    }

    @Test
    fun `two distinct clusters split into two palette entries`() {
        val px = IntArray(16) { if (it % 2 == 0) argb(10, 10, 240) else argb(240, 10, 10) }
        val pal = PixelArtGenerator.extractPalette(px, maxColors = 8)
        assertEquals(2, pal.size)
    }

    /* ---------------- quantization + dithering ---------------- */

    @Test
    fun `quantize snaps to nearest palette entry`() {
        val grid = IntArray(16) { argb(250, 250, 250) } // near white
        val palette = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        PixelArtGenerator.quantizeGrid(grid, 4, 4, palette, dither = false)
        assertEquals(0xFFFFFFFF.toInt(), grid[0])
    }

    @Test
    fun `quantize preserves transparency`() {
        val grid = IntArray(16) { 0x00000000 }
        val palette = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        PixelArtGenerator.quantizeGrid(grid, 4, 4, palette, dither = true)
        assertEquals(0x00000000, grid[5])
    }

    @Test
    fun `dithered pixels always come from the palette`() {
        // gradient exercises the dither path
        val grid = IntArray(64) { i ->
            argb((i * 4) % 256, (i * 2) % 256, (i * 8) % 256)
        }
        val palette = intArrayOf(
            argb(0, 0, 0), argb(64, 64, 64), argb(128, 128, 128),
            argb(192, 192, 192), argb(255, 255, 255)
        )
        PixelArtGenerator.quantizeGrid(grid, 8, 8, palette, dither = true)
        for (p in grid) {
            assertTrue("pixel must be from palette", palette.contains(p))
        }
    }

    /* ---------------- outline pass ---------------- */

    @Test
    fun `outline adds rim around opaque silhouette`() {
        // 8x8 grid, opaque square in the middle
        val grid = IntArray(64) { i ->
            val x = i % 8; val y = i / 8
            if (x in 2..5 && y in 2..5) argb(255, 0, 0) else 0x00000000
        }
        val outlineColor = 0xFF100C1C.toInt()
        PixelArtGenerator.addOutline(grid, 8, 8, outlineColor)
        // pixel at (1,4) is outside the square next to (2,4) -> outline
        assertEquals(outlineColor, grid[4 * 8 + 1])
        // corner pixel far away stays transparent
        assertEquals(0x00000000, grid[0])
        // interior pixel keeps its color
        assertEquals(argb(255, 0, 0), grid[4 * 8 + 4])
    }

    @Test
    fun `outline does not touch fully opaque grid`() {
        val grid = IntArray(16) { argb(10, 20, 30) }
        PixelArtGenerator.addOutline(grid, 4, 4, 0xFF000000.toInt())
        // no transparent pixels -> nothing to outline
        for (p in grid) assertEquals(argb(10, 20, 30), p)
    }

    /* ---------------- panel chrome ---------------- */

    @Test
    fun `panel border and fill are correct`() {
        val base = argb(40, 44, 80)
        val border = argb(20, 22, 44)
        val highlight = argb(90, 96, 150)
        val shadow = argb(14, 15, 30)
        // corner = border
        assertEquals(border, PixelArtGenerator.panelColorAt(0, 0, 32, 24, 3, base, border, highlight, shadow))
        // center = base fill
        assertEquals(base, PixelArtGenerator.panelColorAt(16, 12, 32, 24, 3, base, border, highlight, shadow))
        // top edge (inside border, not corner) = highlight
        assertEquals(highlight, PixelArtGenerator.panelColorAt(16, 1, 32, 24, 3, base, border, highlight, shadow))
        // bottom edge = shadow
        assertEquals(shadow, PixelArtGenerator.panelColorAt(16, 22, 32, 24, 3, base, border, highlight, shadow))
    }

    /* ---------------- rarity frame ---------------- */

    @Test
    fun `rarity frame has layered border and hollow center`() {
        val rarity = argb(255, 180, 0)
        assertEquals(PixelArtGenerator.darken(rarity, 0.45f), PixelArtGenerator.rarityColorAt(0, 0, 24, rarity))
        assertEquals(rarity, PixelArtGenerator.rarityColorAt(1, 1, 24, rarity))
        assertEquals(PixelArtGenerator.lighten(rarity, 0.35f), PixelArtGenerator.rarityColorAt(2, 2, 24, rarity))
        assertEquals(PixelArtGenerator.TRANSPARENT, PixelArtGenerator.rarityColorAt(12, 12, 24, rarity))
    }

    /* ---------------- color helpers ---------------- */

    @Test
    fun `color helpers clamp and compose correctly`() {
        assertEquals(argb(127, 127, 127), PixelArtGenerator.darken(0xFFFFFFFF.toInt(), 0.5f))
        // 255 * 0.25 = 63.75 -> truncates to 63
        assertEquals(argb(63, 63, 63), PixelArtGenerator.lighten(0xFF000000.toInt(), 0.25f))
        // rgb composes channels
        assertEquals(argb(1, 2, 3), PixelArtGenerator.rgb(1, 2, 3))
        // clamping
        assertEquals(argb(255, 255, 255), PixelArtGenerator.rgb(300, 300, 300))
    }
}
