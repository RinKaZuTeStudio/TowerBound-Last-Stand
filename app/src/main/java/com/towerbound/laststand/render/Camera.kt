package com.towerbound.laststand.render

import android.opengl.Matrix
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Orthographic 2D camera with smooth follow, zoom, directional shake and
 * world clamping. World units are abstract; sprites define their draw size.
 */
class Camera {

    /** Camera center in world units. */
    var x: Float = 0f
    var y: Float = 0f

    /** Pixels per world unit — zoom > 1 means closer. */
    var zoom: Float = 1f

    /** Design reference: this many world units are visible vertically at zoom 1. */
    var viewHeight: Float = 780f

    var viewportW: Int = 1920
    var viewportH: Int = 1080

    /** World bounds used for clamping; null = free camera. */
    var worldW: Float = 0f
    var worldH: Float = 0f

    /** Shake state. */
    private var shakeMagnitude = 0f
    private var shakeDecay = 2.2f
    private var shakeTime = 0f
    private var shakeDirX = 0f
    private var shakeDirY = 0f
    private var directional = false
    private val shakeRng = Random(System.nanoTime())

    /** Screen-space reduction factor for accessibility (reduced shake). */
    var shakeScale: Float = 1f

    val viewWidth: Float get() = viewHeight * viewportW / viewportH.toFloat()

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    val mvp = FloatArray(16)

    fun setViewport(w: Int, h: Int) {
        viewportW = w
        viewportH = h
    }

    fun follow(targetX: Float, targetY: Float, dt: Float, stiffness: Float = 6f) {
        val k = 1f - kotlin.math.exp(-stiffness * dt)
        x += (targetX - x) * k
        y += (targetY - y) * k
        clampToWorld()
    }

    fun snapTo(targetX: Float, targetY: Float) {
        x = targetX; y = targetY
        clampToWorld()
    }

    private fun clampToWorld() {
        if (worldW <= 0f || worldH <= 0f) return
        val halfVw = viewWidth / 2f
        val halfVh = viewHeight / 2f
        if (worldW > halfVw * 2f) x = x.coerceIn(halfVw, worldW - halfVw) else x = worldW / 2f
        if (worldH > halfVh * 2f) y = y.coerceIn(halfVh, worldH - halfVh) else y = worldH / 2f
    }

    /** Trigger isotropic shake. */
    fun shake(magnitude: Float, duration: Float = 0.3f) {
        if (magnitude * shakeScale <= shakeMagnitude) {
            shakeMagnitude = min(shakeMagnitude + magnitude * shakeScale, 40f)
        } else {
            shakeMagnitude = magnitude * shakeScale
        }
        shakeDecay = if (duration <= 0f) 40f else magnitude / duration
        directional = false
        shakeTime = 0f
    }

    /** Trigger a directional impact shake (hit direction emphasis). */
    fun shakeDirectional(dirX: Float, dirY: Float, magnitude: Float, duration: Float = 0.25f) {
        shake(magnitude, duration)
        directional = true
        val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY)
        if (len > 0.001f) {
            shakeDirX = dirX / len; shakeDirY = dirY / len
        }
    }

    fun update(dt: Float) {
        if (shakeMagnitude > 0.01f) {
            shakeTime += dt
            shakeMagnitude -= shakeDecay * dt
            if (shakeMagnitude < 0f) shakeMagnitude = 0f
        }
    }

    /** Build the combined MVP for this frame. */
    fun computeMvp(): FloatArray {
        var ox = 0f
        var oy = 0f
        if (shakeMagnitude > 0.01f) {
            if (directional) {
                val pulse = shakeMagnitude * (0.6f + 0.4f * sin(shakeTime * 55f))
                ox = shakeDirX * pulse
                oy = shakeDirY * pulse
            } else {
                ox = (shakeRng.nextFloat() * 2f - 1f) * shakeMagnitude
                oy = (shakeRng.nextFloat() * 2f - 1f) * shakeMagnitude
            }
        }
        val left = x + ox - viewWidth / 2f
        val right = x + ox + viewWidth / 2f
        val bottom = y + oy - viewHeight / 2f
        val top = y + oy + viewHeight / 2f
        Matrix.orthoM(projection, 0, left, right, bottom, top, -10f, 10f)
        Matrix.setIdentityM(view, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        return mvp
    }

    /** Convert screen (pixel) coordinates to world coordinates. */
    fun screenToWorld(screenX: Float, screenY: Float): FloatArray {
        val wx = x - viewWidth / 2f + screenX / viewportW * viewWidth
        val wy = y + viewHeight / 2f - screenY / viewportH * viewHeight
        return floatArrayOf(wx, wy)
    }
}
