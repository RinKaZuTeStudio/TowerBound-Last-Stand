package com.towerbound.laststand.assets

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Animation states every character supports. */
enum class AnimState(val frameCount: Int, val fps: Float, val loop: Boolean) {
    IDLE(4, 5f, true),
    WALK(6, 10f, true),
    RUN(6, 14f, true),
    ATTACK(5, 16f, false),
    CAST(4, 12f, false),
    HIT(2, 12f, false),
    DEATH(5, 10f, false),
    DASH(2, 14f, false),
    DODGE(3, 14f, false),
    VICTORY(2, 6f, true);

    val duration: Float get() = frameCount / fps
}

/**
 * Generates frame pose sequences per animation state. Poses drive the
 * parametric [CharacterPainter]; frames are rendered into a horizontal strip.
 */
object Poses {

    fun frames(state: AnimState): List<Pose> {
        val n = state.frameCount
        return when (state) {
            AnimState.IDLE -> (0 until n).map { i ->
                val t = i / n.toFloat()
                Pose(walkPhase = 0f, bob = sin(t * 2f * PI.toFloat()) * 2f,
                    weaponAngle = sin(t * 2f * PI.toFloat()) * 0.08f, armPhase = 0f)
            }
            AnimState.WALK -> (0 until n).map { i ->
                val t = i / n.toFloat()
                Pose(walkPhase = t * 2f * PI.toFloat(), bob = kotlin.math.abs(sin(t * 2f * PI.toFloat())) * 4f,
                    armPhase = t * 2f * PI.toFloat() + PI.toFloat(), lean = 0.06f,
                    weaponAngle = sin(t * 2f * PI.toFloat()) * 0.12f)
            }
            AnimState.RUN -> (0 until n).map { i ->
                val t = i / n.toFloat()
                Pose(walkPhase = t * 2f * PI.toFloat(), bob = kotlin.math.abs(sin(t * 2f * PI.toFloat())) * 7f,
                    armPhase = t * 2f * PI.toFloat() + PI.toFloat(), lean = 0.22f,
                    weaponAngle = sin(t * 2f * PI.toFloat()) * 0.18f, airborne = kotlin.math.abs(sin(t * PI.toFloat())) * 0.3f)
            }
            AnimState.ATTACK -> (0 until n).map { i ->
                val t = i / (n - 1).toFloat()
                // windup -> strike -> recover
                val swing = when {
                    t < 0.35f -> -0.9f * (t / 0.35f)          // wind back
                    t < 0.6f -> -0.9f + 2.1f * ((t - 0.35f) / 0.25f) // fast strike
                    else -> 1.2f - 1.0f * ((t - 0.6f) / 0.4f)  // follow through
                }
                Pose(weaponAngle = swing, weaponExtend = if (t in 0.35f..0.6f) 1f else 0.2f,
                    lean = if (t in 0.35f..0.6f) 0.18f else 0.02f,
                    bob = if (t in 0.35f..0.6f) 3f else 0f, walkPhase = 0.2f)
            }
            AnimState.CAST -> (0 until n).map { i ->
                val t = i / (n - 1).toFloat()
                Pose(weaponAngle = -1.3f + t * 0.25f, weaponExtend = 0.5f + t * 0.3f,
                    castGlow = t, armPhase = -0.5f, bob = -t * 3f, walkPhase = 0f)
            }
            AnimState.HIT -> (0 until n).map { i ->
                val t = i / (n - 1).toFloat()
                Pose(squash = (1f - t) * 0.8f, lean = -0.12f * (1f - t), tilt = -6f * (1f - t), walkPhase = 0f)
            }
            AnimState.DEATH -> (0 until n).map { i ->
                val t = i / (n - 1).toFloat()
                Pose(tilt = -70f * t, alpha = 1f - 0.75f * t, squash = 0.35f * t,
                    bob = 6f * t, walkPhase = 0.4f)
            }
            AnimState.DASH -> (0 until n).map { i ->
                val t = i / (n - 1).toFloat()
                Pose(lean = 0.42f, speedLines = 1f - t * 0.4f, walkPhase = 1.2f,
                    bob = 2f, airborne = 0.25f - 0.2f * t, weaponAngle = -0.5f)
            }
            AnimState.DODGE -> (0 until n).map { i ->
                val t = i / (n - 1).toFloat()
                Pose(tilt = 14f * sin(t * PI.toFloat()), lean = -0.2f * sin(t * PI.toFloat()),
                    airborne = 0.5f * sin(t * PI.toFloat()), walkPhase = 1.8f)
            }
            AnimState.VICTORY -> (0 until n).map { i ->
                val t = i / n.toFloat()
                Pose(weaponAngle = -1.8f + sin(t * 2f * PI.toFloat()) * 0.1f, weaponExtend = 0.85f,
                    bob = sin(t * 2f * PI.toFloat()) * 3f, castGlow = 0.4f)
            }
        }
    }
}

/**
 * A horizontal frame strip for one character. Frames are uniform-width cells;
 * the renderer samples cells by animation progress.
 */
class SpriteSheet(
    val designId: String,
    val state: AnimState,
    val bitmap: Bitmap,
    val frameCount: Int,
    val frameWidth: Int,
    val frameHeight: Int
) {
    fun frameU(i: Float): Float {
        val idx = if (state.loop) ((i % frameCount) + frameCount) % frameCount else i.coerceIn(0f, (frameCount - 1).toFloat())
        return idx / frameCount.toFloat()
    }

    companion object {
        /**
         * Render all frames of [state] for [design] into a strip bitmap.
         * Master render size 256 (spec minimum); [size] can downscale for
         * minor enemies to save memory.
         */
        fun generate(design: CharacterDesign, state: AnimState, size: Int = 256): SpriteSheet {
            val frames = Poses.frames(state)
            val strip = Bitmap.createBitmap(size * frames.size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(strip)
            val painter = CharacterPainter(design)
            val scale = size / CharacterPainter.SIZE.toFloat()
            for (i in frames.indices) {
                canvas.save()
                canvas.scale(scale, scale)
                canvas.translate((CharacterPainter.SIZE * i).toFloat(), 0f)
                painter.paint(canvas, frames[i])
                canvas.restore()
            }
            return SpriteSheet(design.id, state, strip, frames.size, size, size)
        }
    }
}
