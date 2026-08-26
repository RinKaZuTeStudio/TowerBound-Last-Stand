package com.towerbound.laststand.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Batched textured-quad renderer for the whole game world.
 *
 * One shared shader, per-vertex tint color, texture switching flushes the
 * batch. Supports normal (alpha) and additive blending passes. All game
 * drawing goes through this single class to keep draw calls low.
 */
class SpriteBatch(maxQuads: Int = 2048) {

    companion object {
        const val FLOATS_PER_VERTEX = 8 // x, y, u, v, r, g, b, a
    }

    private val maxVertices = maxQuads * 4
    private val vertexData = FloatArray(maxVertices * FLOATS_PER_VERTEX)
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(maxVertices * FLOATS_PER_VERTEX * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val indexData = ShortArray(maxQuads * 6)
    private val indexBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(maxQuads * 6 * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
        .apply {
            for (q in 0 until maxQuads) {
                val v = (q * 4).toShort()
                val i = q * 6
                indexData[i] = v
                indexData[i + 1] = (v + 1).toShort()
                indexData[i + 2] = (v + 2).toShort()
                indexData[i + 3] = v
                indexData[i + 4] = (v + 2).toShort()
                indexData[i + 5] = (v + 3).toShort()
            }
            put(indexData)
            position(0)
        }

    private var program = 0
    private var programAdd = 0
    private var uMVP = 0
    private var uTex = 0
    private var aPos = 0
    private var aUV = 0
    private var aColor = 0
    // additive variant locations
    private var uMVPAdd = 0
    private var uTexAdd = 0
    private var aPosAdd = 0
    private var aUVAdd = 0
    private var aColorAdd = 0

    private var quadCount = 0
    private var currentTex = -1
    private var additive = false
    private var drawing = false

    /** Current transform tint (multiplied into vertices). */
    var red = 1f; private set
    var green = 1f; private set
    var blue = 1f; private set
    var alpha = 1f; private set

    fun compileShaders() {
        releasePrograms()
        program = ShaderUtil.link(ShaderUtil.SPRITE_VS, ShaderUtil.SPRITE_FS)
        programAdd = ShaderUtil.link(ShaderUtil.SPRITE_VS, ShaderUtil.SPRITE_FS_ADD)
        if (program == 0 || programAdd == 0) {
            throw IllegalStateException("sprite batch shaders failed to compile")
        }
        fun loc(p: Int, n: String) = GLES20.glGetUniformLocation(p, n)
        uMVP = loc(program, "uMVP"); uTex = loc(program, "uTex")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUV = GLES20.glGetAttribLocation(program, "aUV")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMVPAdd = loc(programAdd, "uMVP"); uTexAdd = loc(programAdd, "uTex")
        aPosAdd = GLES20.glGetAttribLocation(programAdd, "aPos")
        aUVAdd = GLES20.glGetAttribLocation(programAdd, "aUV")
        aColorAdd = GLES20.glGetAttribLocation(programAdd, "aColor")
    }

    fun begin(mvp: FloatArray) {
        check(!drawing) { "begin() called twice" }
        drawing = true
        currentTex = -1
        quadCount = 0
        additive = false
        System.arraycopy(mvp, 0, lastMvp, 0, 16)
        setTint(1f, 1f, 1f, 1f)
        useProgram(mvp, additive = false)
    }

    private fun useProgram(mvp: FloatArray, additive: Boolean) {
        val p = if (additive) programAdd else program
        GLES20.glUseProgram(p)
        val mvpLoc = if (additive) uMVPAdd else uMVP
        val texLoc = if (additive) uTexAdd else uTex
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
        GLES20.glUniform1i(texLoc, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    fun setTint(r: Float, g: Float, b: Float, a: Float) {
        red = r.coerceIn(0f, 1f); green = g.coerceIn(0f, 1f)
        blue = b.coerceIn(0f, 1f); alpha = a.coerceIn(0f, 1f)
    }

    fun setAlpha(a: Float) { alpha = a.coerceIn(0f, 1f) }

    /**
     * Draw a textured quad with center position, size, rotation and
     * sub-rectangle (u/v window into the texture, e.g. animation frames).
     */
    fun draw(
        texId: Int,
        cx: Float, cy: Float,
        w: Float, h: Float,
        rotDeg: Float = 0f,
        u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f
    ) {
        if (texId != currentTex) {
            if (quadCount > 0) flush()
            currentTex = texId
        }
        if (quadCount * 4 >= maxVertices) flush()

        val i = quadCount * 4 * FLOATS_PER_VERTEX
        val hw = w / 2f
        val hh = h / 2f
        val rad = Math.toRadians(rotDeg.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val r = red; val g = green; val b = blue; val a = alpha

        // four rotated corners: (-hw,-hh) (+hw,-hh) (+hw,+hh) (-hw,+hh)
        fun corner(sx: Float, sy: Float, u: Float, v: Float, index: Int) {
            val x = cx + (sx * cos - sy * sin)
            val y = cy + (sx * sin + sy * cos)
            val o = i + index * FLOATS_PER_VERTEX
            vertexData[o] = x; vertexData[o + 1] = y
            vertexData[o + 2] = u; vertexData[o + 3] = v
            vertexData[o + 4] = r; vertexData[o + 5] = g; vertexData[o + 6] = b; vertexData[o + 7] = a
        }
        // Canonical coordinate system: WORLD +Y = UP, SCREEN +Y = DOWN.
        // Android bitmaps have row 0 at the top (V=0 = top of image), so the
        // quad's TOP vertices must sample v0 (image top) and BOTTOM vertices
        // v1 (image bottom) for sprites to render upright in a Y-up world.
        corner(-hw, -hh, u0, v1, 0)
        corner(hw, -hh, u1, v1, 1)
        corner(hw, hh, u1, v0, 2)
        corner(-hw, hh, u0, v0, 3)
        quadCount++
    }

    /** Set additive blending for subsequent draws until [normalBlending]. */
    fun additiveBlending() {
        if (additive) return
        if (quadCount > 0) flush()
        additive = true
        // re-issue program with stored MVP
        useProgram(lastMvp, true)
    }

    fun normalBlending() {
        if (!additive) return
        if (quadCount > 0) flush()
        additive = false
        useProgram(lastMvp, false)
    }

    private val lastMvp = FloatArray(16)

    fun end() {
        check(drawing) { "end() without begin()" }
        if (quadCount > 0) flush()
        drawing = false
    }

    private fun flush() {
        if (quadCount == 0) return
        vertexBuffer.clear()
        vertexBuffer.put(vertexData, 0, quadCount * 4 * FLOATS_PER_VERTEX)
        vertexBuffer.position(0)

        val p = if (additive) programAdd else program
        val posLoc = if (additive) aPosAdd else aPos
        val uvLoc = if (additive) aUVAdd else aUV
        val colorLoc = if (additive) aColorAdd else aColor

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTex)

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glEnableVertexAttribArray(colorLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, vertexBuffer)
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, vertexBuffer)
        vertexBuffer.position(4)
        GLES20.glVertexAttribPointer(colorLoc, 4, GLES20.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, vertexBuffer)
        vertexBuffer.position(0)

        GLES20.glBlendFunc(
            GLES20.GL_SRC_ALPHA,
            if (additive) GLES20.GL_ONE else GLES20.GL_ONE_MINUS_SRC_ALPHA
        )

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, quadCount * 6,
            GLES20.GL_UNSIGNED_SHORT, indexBuffer
        )

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
        GLES20.glDisableVertexAttribArray(colorLoc)

        quadCount = 0
    }

    /** Capture current MVP (called internally by begin). */
    fun captureMvp(mvp: FloatArray) {
        System.arraycopy(mvp, 0, lastMvp, 0, 16)
    }

    fun releasePrograms() {
        if (program != 0) { GLES20.glDeleteProgram(program); program = 0 }
        if (programAdd != 0) { GLES20.glDeleteProgram(programAdd); programAdd = 0 }
    }
}

/**
 * Bitmap -> GL texture cache. Handles context loss by re-uploading from the
 * source bitmaps (kept referenced) on [reloadAll].
 */
class TextureManager {

    class Entry(val bitmap: Bitmap, val nearest: Boolean) {
        var texId = 0
    }

    private val entries = HashMap<String, Entry>()
    private var bound = -1

    /**
     * @param nearest true = pixel-art crisp look (NEAREST filtering);
     *                false = smooth (glows, text atlas).
     */
    fun get(key: String, bitmap: Bitmap, nearest: Boolean = true): Int {
        var e = entries[key]
        if (e == null) {
            e = Entry(bitmap, nearest)
            entries[key] = e
            e.texId = upload(bitmap, nearest)
        } else if (e.texId == 0) {
            e.texId = upload(bitmap, nearest)
        }
        return e.texId
    }

    fun idOf(key: String): Int = entries[key]?.texId ?: 0

    private fun upload(bitmap: Bitmap, nearest: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        if (id == 0) return 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        val filter = if (nearest) GLES20.GL_NEAREST else GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        ShaderUtil.checkGlError("texImage2D ${bitmap.width}x${bitmap.height}")
        return id
    }

    /** Called on context recreation: re-upload everything. */
    fun reloadAll() {
        for (e in entries.values) {
            if (e.texId != 0) GLES20.glDeleteTextures(1, intArrayOf(e.texId), 0)
            e.texId = upload(e.bitmap, e.nearest)
        }
        bound = -1
        com.towerbound.laststand.core.GameLog.i("TexMgr", "reloaded ${entries.size} textures after context loss")
    }

    fun deleteAll() {
        for (e in entries.values) {
            if (e.texId != 0) GLES20.glDeleteTextures(1, intArrayOf(e.texId), 0)
            e.texId = 0
        }
        bound = -1
    }

    fun count(): Int = entries.size
}
