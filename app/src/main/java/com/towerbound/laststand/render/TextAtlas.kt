package com.towerbound.laststand.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.towerbound.laststand.assets.AssetRepository

/**
 * GL text rendering via a pre-baked glyph atlas. Used for floating damage
 * numbers, crit popups and in-world labels. HUD text uses Android Views.
 *
 * Glyphs: digits, +, %, !, ., :, letters A-Z (upper) — enough for damage
 * popups like "124" / "CRIT +320!".
 */
class TextAtlas {

    companion object {
        const val GLYPH_H = 56f
        const val ATLAS_SIZE = 512
        val CHARSET = "0123456789+%.!:ABCDEGKMNPRSTWY- " // curated set used by popups
    }

    private val glyphU = HashMap<Char, Float>()
    private val glyphV = HashMap<Char, Float>()
    private val glyphW = HashMap<Char, Float>()
    private var atlasTexId = 0
    private var glyphAdvance = 0f

    /** Bake the atlas; call with a GL context current. */
    fun build(texManager: TextureManager) {
        val bmp = Bitmap.createBitmap(ATLAS_SIZE, ATLAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = GLYPH_H * 0.78f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
        var x = 4f
        var y = 4f
        glyphAdvance = paint.measureText("0")
        for (ch in CHARSET) {
            val w = paint.measureText(ch.toString()).coerceAtLeast(1f)
            if (x + w + 8f > ATLAS_SIZE) { x = 4f; y += GLYPH_H + 8f }
            canvas.drawText(ch.toString(), x, y + GLYPH_H * 0.72f, paint)
            glyphU[ch] = x / ATLAS_SIZE
            glyphV[ch] = y / ATLAS_SIZE
            glyphW[ch] = w
            x += w + 10f
        }
        atlasTexId = texManager.get("text_atlas", bmp, nearest = false)
    }

    fun texId(): Int = atlasTexId

    /** Draw [text] centered at (x, y) in world units, height [height]. */
    fun drawText(batch: SpriteBatch, text: String, x: Float, y: Float, height: Float,
                  r: Float, g: Float, b: Float, a: Float) {
        if (atlasTexId == 0) return
        val scale = height / GLYPH_H
        var totalW = 0f
        for (ch in text) totalW += (glyphW[ch] ?: glyphAdvance) * scale + 4f * scale
        var cx = x - totalW / 2f
        val top = y - height / 2f
        batch.setTint(r, g, b, a)
        for (ch in text) {
            val u = glyphU[ch] ?: continue
            val v = glyphV[ch] ?: 0f
            val w = glyphW[ch] ?: glyphAdvance
            val u0 = u
            val u1 = u + w / ATLAS_SIZE
            val v0 = v / ATLAS_SIZE
            val v1 = (v + GLYPH_H) / ATLAS_SIZE
            batch.draw(atlasTexId, cx + w * scale / 2f, top + height / 2f, w * scale, height, 0f, u0, v0, u1, v1)
            cx += w * scale + 4f * scale
        }
        batch.setTint(1f, 1f, 1f, 1f)
    }

    fun drawNumber(batch: SpriteBatch, value: Int, x: Float, y: Float, height: Float,
                   crit: Boolean, color: Int) {
        val text = if (crit) "$value!" else value.toString()
        drawText(batch, text, x, y, height,
            Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, 1f)
    }
}
