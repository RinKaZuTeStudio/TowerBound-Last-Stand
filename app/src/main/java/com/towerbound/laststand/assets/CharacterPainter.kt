package com.towerbound.laststand.assets

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

/**
 * Paints a [CharacterDesign] in a specific [Pose] onto a 256x256 canvas.
 * All heroes/enemies/bosses share this painter; identity comes from the
 * design parameters. Every shape gets a dark outline for readable
 * silhouettes, vertical gradients for volume and glow accents.
 */
class CharacterPainter(private val design: CharacterDesign) {

    companion object {
        const val SIZE = 256
        const val CX = 128f
        const val FOOT_Y = 228f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    /** Draw the character into [canvas] (expected 256x256). */
    fun paint(canvas: Canvas, pose: Pose) {
        val d = design
        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = (pose.alpha * 255).toInt().coerceIn(0, 255)

        val s = d.scale
        val sq = pose.squash
        val bodyScaleX = s * (1f + sq * 0.10f)
        val bodyScaleY = s * (1f - sq * 0.12f)

        canvas.save()
        // death tilt around the feet
        if (pose.tilt != 0f) {
            canvas.rotate(pose.tilt, CX, FOOT_Y)
        }
        canvas.translate(0f, -pose.airborne * 26f * s)

        // ---- ground shadow ----
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((70 * pose.alpha).toInt(), 0, 0, 0)
        canvas.drawOval(RectF(CX - 44f * s, FOOT_Y - 9f, CX + 44f * s, FOOT_Y + 9f), paint)

        val bob = pose.bob * s
        val lean = pose.lean

        // ---- rig positions ----
        val hipY = (FOOT_Y - 60f * s) * 1f + bob
        val shoulderY = hipY - 58f * bodyScaleY
        val headR = 21f * s
        val headY = shoulderY - headR - 6f * s
        val torsoHalfW = 20f * s * d.bulk
        val legLen = FOOT_Y - 8f - hipY
        val armLen = 46f * s

        // leg swing
        val legA = sin(pose.walkPhase) * 0.55f
        val legB = sin(pose.walkPhase + Math.PI.toFloat()) * 0.55f
        val legLift = { ph: Float -> kotlin.math.abs(cos(ph)) * 10f * s }

        // ---- aura ----
        if (d.auraColor != 0) {
            paint.color = d.auraColor
            paint.shader = RadialGradient(CX, (hipY + shoulderY) / 2f, 80f * s,
                d.auraColor, Color.argb(0, Color.red(d.auraColor), Color.green(d.auraColor), Color.blue(d.auraColor)),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(CX, (hipY + shoulderY) / 2f, 80f * s, paint)
            paint.shader = null
        }

        // ---- cape (behind body) ----
        drawCape(canvas, shoulderY, torsoHalfW, bob, pose)

        // ---- legs ----
        if (d.floating) {
            // hovering robe: no legs, wavy hem
            drawRobe(canvas, hipY, torsoHalfW, bob)
        } else {
            drawLeg(canvas, CX, hipY, legLen, legA, legLift(pose.walkPhase), d.secondary, s, lean, true)
            drawLeg(canvas, CX, hipY, legLen, legB, legLift(pose.walkPhase + Math.PI.toFloat()), d.primary, s, lean, false)
        }

        // ---- torso ----
        val torsoTop = shoulderY
        val torsoBot = hipY + 6f * s
        canvas.save()
        canvas.rotate(lean * 30f, CX, hipY)
        path.reset()
        path.moveTo(CX - torsoHalfW, torsoBot)
        path.lineTo(CX - torsoHalfW * 0.96f, torsoTop + 8f)
        path.quadTo(CX, torsoTop - 14f * s, CX + torsoHalfW * 0.96f, torsoTop + 8f)
        path.lineTo(CX + torsoHalfW, torsoBot)
        path.quadTo(CX, torsoBot + 8f, CX - torsoHalfW, torsoBot)
        path.close()
        outlinePath(canvas, path, d.outline, 5f)
        paint.shader = LinearGradient(CX - torsoHalfW, torsoTop, CX + torsoHalfW, torsoBot,
            lighten(d.primary, 0.25f), darken(d.primary, 0.35f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        // chest emblem / belt
        paint.color = d.accent
        canvas.drawCircle(CX, torsoTop + (torsoBot - torsoTop) * 0.34f, 5f * s, paint)
        paint.color = darken(d.secondary, 0.1f)
        canvas.drawRect(RectF(CX - torsoHalfW, torsoBot - 12f * s, CX + torsoHalfW, torsoBot - 5f * s), paint)
        paint.color = d.accent
        canvas.drawRect(RectF(CX - 4f * s, torsoBot - 12.5f * s, CX + 4f * s, torsoBot - 4.5f * s), paint)
        canvas.restore()

        // ---- arms ----
        val armSwing = sin(pose.armPhase) * 0.5f
        // back arm
        drawArm(canvas, CX - torsoHalfW * 0.9f, shoulderY + 6f, armLen, armSwing + 0.35f, darken(d.primary, 0.28f), s, lean)
        // front arm reaches along weapon direction
        val reach = pose.weaponExtend
        val wAng = pose.weaponAngle
        val fx = CX + cos(wAng) * (18f + reach * 30f) * s
        val fy = shoulderY + 10f + sin(wAng) * (6f + reach * 14f) * s
        drawArmTo(canvas, CX + torsoHalfW * 0.8f, shoulderY + 6f, fx, fy, lighten(d.primary, 0.1f), s)

        // ---- head ----
        drawHead(canvas, CX + sin(lean) * 8f, headY, headR, bob, pose)

        // ---- weapon in front hand ----
        drawWeapon(canvas, fx, fy, wAng, s, pose)

        // ---- effects ----
        if (pose.castGlow > 0f) {
            val gx = fx + cos(wAng) * 30f * s
            val gy = fy + sin(wAng) * 30f * s
            paint.color = Color.WHITE
            paint.shader = RadialGradient(gx, gy, 26f * s * pose.castGlow,
                Color.argb((230 * pose.castGlow).toInt(), Color.red(d.accent), Color.green(d.accent), Color.blue(d.accent)),
                Color.argb(0, Color.red(d.accent), Color.green(d.accent), Color.blue(d.accent)),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(gx, gy, 26f * s * pose.castGlow, paint)
            paint.shader = null
        }

        if (pose.speedLines > 0f) {
            paint.strokeWidth = 3f * s
            paint.style = Paint.Style.STROKE
            for (i in 0..4) {
                val ly = headY + i * 28f * s
                paint.color = Color.argb((110 * pose.speedLines).toInt(), 255, 255, 255)
                canvas.drawLine(CX - 78f * s, ly, CX - 40f * s - i * 6f, ly, paint)
                canvas.drawLine(CX + 40f * s + i * 6f, ly + 14f, CX + 78f * s, ly + 14f, paint)
            }
            paint.style = Paint.Style.FILL
        }

        canvas.restore()
    }

    /* ---------------- parts ---------------- */

    private fun drawLeg(canvas: Canvas, x: Float, hipY: Float, len: Float, angle: Float, lift: Float, color: Int, s: Float, lean: Float, back: Boolean) {
        val kneeX = x + sin(angle) * len * 0.45f
        val kneeY = hipY + len * 0.5f - lift * 0.4f
        val footX = x + sin(angle * 1.4f) * len * 0.5f
        val footY = FOOT_Y - lift
        val w = 9f * s * design.bulk
        val c = if (back) darken(color, 0.25f) else color
        capsule(canvas, x, hipY + 2f, kneeX, kneeY, w + 2f, design.outline)
        capsule(canvas, kneeX, kneeY, footX, footY, w + 2f, design.outline)
        capsule(canvas, x, hipY + 2f, kneeX, kneeY, w, c)
        capsule(canvas, kneeX, kneeY, footX, footY, w, c)
        // boot
        paint.color = darken(design.secondary, 0.15f)
        outlineOval(canvas, footX - w * 0.9f, footY - w * 0.5f, footX + w * 1.4f, footY + w * 0.6f, design.outline, 3f)
        paint.color = darken(design.secondary, 0.15f)
        canvas.drawOval(RectF(footX - w * 0.9f, footY - w * 0.5f, footX + w * 1.4f, footY + w * 0.6f), paint)
    }

    private fun drawRobe(canvas: Canvas, hipY: Float, halfW: Float, bob: Float) {
        path.reset()
        path.moveTo(CX - halfW, hipY - 10f)
        val hemY = FOOT_Y - 14f + sin(bob * 0.2f) * 2f
        path.quadTo(CX - halfW * 1.5f, (hipY + hemY) / 2f, CX - halfW * 0.9f, hemY)
        path.quadTo(CX, hemY + 12f, CX + halfW * 0.9f, hemY)
        path.quadTo(CX + halfW * 1.5f, (hipY + hemY) / 2f, CX + halfW, hipY - 10f)
        path.close()
        outlinePath(canvas, path, design.outline, 5f)
        paint.shader = LinearGradient(CX, hipY, CX, hemY, design.primary, darken(design.primary, 0.45f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawCape(canvas: Canvas, shoulderY: Float, halfW: Float, bob: Float, pose: Pose) {
        val style = design.capeStyle
        if (style == CapeStyle.NONE) return
        val len = when (style) { CapeStyle.SHORT -> 46f; CapeStyle.LONG -> 86f; else -> 60f } * design.scale
        val sway = sin(pose.walkPhase * 0.5f) * 8f
        path.reset()
        path.moveTo(CX - halfW * 0.9f, shoulderY + 2f)
        path.quadTo(CX - halfW * 1.9f + sway, shoulderY + len * 0.55f, CX - halfW * 0.7f + sway * 1.4f, shoulderY + len)
        if (style == CapeStyle.TATTERED) {
            path.lineTo(CX - halfW * 0.2f + sway, shoulderY + len * 0.8f)
            path.lineTo(CX + halfW * 0.2f + sway, shoulderY + len)
        }
        path.lineTo(CX + halfW * 0.7f + sway * 1.4f, shoulderY + len)
        path.quadTo(CX + halfW * 1.9f + sway, shoulderY + len * 0.55f, CX + halfW * 0.9f, shoulderY + 2f)
        path.close()
        outlinePath(canvas, path, design.outline, 5f)
        paint.shader = LinearGradient(CX, shoulderY, CX, shoulderY + len,
            darken(design.secondary, 0.05f), darken(design.secondary, 0.5f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawArm(canvas: Canvas, x: Float, y: Float, len: Float, angle: Float, color: Int, s: Float, lean: Float) {
        val ex = x + sin(angle) * len * 0.8f
        val ey = y + cos(angle) * len * 0.8f
        capsule(canvas, x, y, ex, ey, 8f * s + 1.5f, design.outline)
        capsule(canvas, x, y, ex, ey, 8f * s, color)
    }

    private fun drawArmTo(canvas: Canvas, x: Float, y: Float, tx: Float, ty: Float, color: Int, s: Float) {
        capsule(canvas, x, y, tx, ty, 8.5f * s + 1.5f, design.outline)
        capsule(canvas, x, y, tx, ty, 8.5f * s, color)
        // hand
        paint.color = design.skin
        outlineCircle(canvas, tx, ty, 6f * s, design.outline, 3f)
        paint.color = design.skin
        canvas.drawCircle(tx, ty, 6f * s, paint)
    }

    private fun drawHead(canvas: Canvas, cx: Float, cy: Float, r: Float, bob: Float, pose: Pose) {
        val d = design
        // neck
        capsule(canvas, CX, cy + r * 0.8f, CX, cy + r * 1.3f, 8f * d.scale, darken(d.primary, 0.2f))

        // face/skull base
        outlineCircle(canvas, cx, cy, r, d.outline, 5f)
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.5f,
            lighten(d.skin, 0.15f), darken(d.skin, 0.25f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        when (d.headStyle) {
            HeadStyle.HELM_KITE -> {
                path.reset()
                path.addOval(RectF(cx - r * 1.08f, cy - r * 1.15f, cx + r * 1.08f, cy + r * 0.5f), Path.Direction.CW)
                outlinePath(canvas, path, d.outline, 4f)
                paint.shader = LinearGradient(cx, cy - r, cx, cy + r * 0.5f, lighten(d.primary, 0.3f), darken(d.primary, 0.2f), Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint); paint.shader = null
                // visor slit
                paint.color = d.accent
                canvas.drawRect(RectF(cx - r * 0.7f, cy - r * 0.25f, cx + r * 0.7f, cy - r * 0.05f), paint)
            }
            HeadStyle.HOOD -> {
                path.reset()
                path.moveTo(cx - r * 1.25f, cy + r * 0.9f)
                path.quadTo(cx - r * 1.5f, cy - r * 1.2f, cx, cy - r * 1.35f)
                path.quadTo(cx + r * 1.5f, cy - r * 1.2f, cx + r * 1.25f, cy + r * 0.9f)
                path.quadTo(cx, cy + r * 0.45f, cx - r * 1.25f, cy + r * 0.9f)
                path.close()
                outlinePath(canvas, path, d.outline, 4f)
                paint.shader = LinearGradient(cx, cy - r * 1.3f, cx, cy + r, darken(d.secondary, 0.1f), darken(d.secondary, 0.5f), Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint); paint.shader = null
                drawEyes(canvas, cx, cy, r, true)
            }
            HeadStyle.HELM_HORNS -> {
                paint.color = darken(d.primary, 0.1f)
                outlineCircle(canvas, cx, cy, r * 1.05f, d.outline, 4f)
                paint.shader = LinearGradient(cx, cy - r, cx, cy + r, lighten(d.primary, 0.25f), darken(d.primary, 0.3f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, r * 1.02f, paint); paint.shader = null
                // horns
                for (sign in floatArrayOf(-1f, 1f)) {
                    path.reset()
                    path.moveTo(cx + sign * r * 0.8f, cy - r * 0.5f)
                    path.quadTo(cx + sign * r * 1.7f, cy - r * 1.2f, cx + sign * r * 1.25f, cy - r * 1.9f)
                    path.quadTo(cx + sign * r * 1.1f, cy - r * 0.9f, cx + sign * r * 0.55f, cy - r * 0.35f)
                    path.close()
                    outlinePath(canvas, path, d.outline, 3f)
                    paint.color = Color.parseColor("#E8D9B0")
                    canvas.drawPath(path, paint)
                }
                paint.color = d.accent
                canvas.drawRect(RectF(cx - r * 0.65f, cy - r * 0.15f, cx + r * 0.65f, cy + r * 0.05f), paint)
            }
            HeadStyle.MASK_NINJA -> {
                paint.color = darken(d.primary, 0.3f)
                outlineCircle(canvas, cx, cy, r * 1.04f, d.outline, 4f)
                paint.color = darken(d.primary, 0.3f)
                canvas.drawCircle(cx, cy, r * 1.02f, paint)
                paint.color = Color.argb(255, 24, 24, 34)
                canvas.drawRect(RectF(cx - r * 0.8f, cy - r * 0.35f, cx + r * 0.8f, cy + r * 0.3f), paint)
                drawEyes(canvas, cx, cy, r, true)
                // headband tail
                paint.color = d.accent
                path.reset()
                path.moveTo(cx + r * 0.9f, cy - r * 0.2f)
                path.quadTo(cx + r * 1.9f, cy - r * 0.6f + sin(bob * 0.3f) * 3f, cx + r * 2.2f, cy - r * 0.1f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }
            HeadStyle.WIZARD_HAT -> {
                path.reset()
                path.moveTo(cx - r * 1.35f, cy - r * 0.3f)
                path.quadTo(cx - r * 0.2f, cy - r * 2.6f, cx + r * 0.7f, cy - r * 2.2f)
                path.quadTo(cx + r * 0.5f, cy - r * 1.1f, cx + r * 1.35f, cy - r * 0.3f)
                path.close()
                outlinePath(canvas, path, d.outline, 4f)
                paint.shader = LinearGradient(cx - r, cy - r * 2.4f, cx + r, cy, design.primary, darken(design.primary, 0.35f), Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint); paint.shader = null
                paint.color = design.accent
                canvas.drawRect(RectF(cx - r * 1.3f, cy - r * 0.45f, cx + r * 1.3f, cy - r * 0.15f), paint)
                drawEyes(canvas, cx, cy, r * 0.9f, false)
            }
            HeadStyle.VISOR_TECH -> {
                outlineCircle(canvas, cx, cy, r * 1.05f, d.outline, 4f)
                paint.shader = LinearGradient(cx, cy - r, cx, cy + r, lighten(d.primary, 0.4f), darken(d.primary, 0.25f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, r * 1.02f, paint); paint.shader = null
                paint.color = Color.argb(255, 16, 20, 30)
                canvas.drawRect(RectF(cx - r * 0.85f, cy - r * 0.4f, cx + r * 0.85f, cy + r * 0.25f), paint)
                // glowing visor line
                paint.color = d.accent
                paint.setShadowLayer(10f, 0f, 0f, d.accent)
                canvas.drawRect(RectF(cx - r * 0.7f, cy - r * 0.28f, cx + r * 0.7f, cy - r * 0.12f), paint)
                paint.clearShadowLayer()
            }
            HeadStyle.SKULL, HeadStyle.IMPSKULL -> {
                // eye sockets
                paint.color = Color.argb(255, 22, 18, 26)
                canvas.drawOval(RectF(cx - r * 0.55f, cy - r * 0.3f, cx - r * 0.1f, cy + r * 0.1f), paint)
                canvas.drawOval(RectF(cx + r * 0.1f, cy - r * 0.3f, cx + r * 0.55f, cy + r * 0.1f), paint)
                if (design.eyeGlow != 0) {
                    paint.color = design.eyeGlow
                    paint.setShadowLayer(8f, 0f, 0f, design.eyeGlow)
                    canvas.drawCircle(cx - r * 0.32f, cy - r * 0.1f, r * 0.16f, paint)
                    canvas.drawCircle(cx + r * 0.32f, cy - r * 0.1f, r * 0.16f, paint)
                    paint.clearShadowLayer()
                }
                if (d.headStyle == HeadStyle.IMPSKULL) {
                    // horns on imp
                    for (sign in floatArrayOf(-1f, 1f)) {
                        path.reset()
                        path.moveTo(cx + sign * r * 0.6f, cy - r * 0.7f)
                        path.quadTo(cx + sign * r * 1.3f, cy - r * 1.4f, cx + sign * r * 0.85f, cy - r * 1.7f)
                        path.quadTo(cx + sign * r * 0.75f, cy - r * 0.95f, cx + sign * r * 0.4f, cy - r * 0.55f)
                        path.close()
                        outlinePath(canvas, path, d.outline, 3f)
                        paint.color = darken(d.skin, 0.3f)
                        canvas.drawPath(path, paint)
                    }
                }
            }
            HeadStyle.CROWN -> {
                paint.color = Color.parseColor("#F5C542")
                path.reset()
                path.moveTo(cx - r, cy - r * 0.75f)
                for (i in 0..4) {
                    val px = cx - r + (2 * r) * i / 4f
                    path.lineTo(px + r / 4f, cy - r * (if (i % 2 == 0) 1.5f else 1.1f))
                    path.lineTo(px + r / 4f, cy - r * 0.75f)
                }
                path.close()
                outlinePath(canvas, path, d.outline, 3f)
                paint.color = Color.parseColor("#F5C542")
                canvas.drawPath(path, paint)
                drawEyes(canvas, cx, cy, r, false)
            }
            HeadStyle.HUMAN -> drawEyes(canvas, cx, cy, r, false)
        }
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, r: Float, shadowed: Boolean) {
        if (design.eyeGlow != 0) {
            paint.color = design.eyeGlow
            paint.setShadowLayer(9f, 0f, 0f, design.eyeGlow)
            canvas.drawCircle(cx - r * 0.35f, cy - r * 0.05f, r * 0.18f, paint)
            canvas.drawCircle(cx + r * 0.35f, cy - r * 0.05f, r * 0.18f, paint)
            paint.clearShadowLayer()
        } else {
            paint.color = if (shadowed) Color.argb(240, 255, 250, 220) else Color.argb(255, 30, 26, 40)
            canvas.drawCircle(cx - r * 0.35f, cy - r * 0.02f, r * 0.15f, paint)
            canvas.drawCircle(cx + r * 0.35f, cy - r * 0.02f, r * 0.15f, paint)
        }
    }

    private fun drawWeapon(canvas: Canvas, hx: Float, hy: Float, angle: Float, s: Float, pose: Pose) {
        val d = design
        canvas.save()
        canvas.translate(hx, hy)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat() * 0.35f)
        val L = 74f * s
        when (d.weapon) {
            WeaponStyle.SWORD -> {
                blade(canvas, 8f, -L, 10f * s, d.accent, d.outline)
                crossguard(canvas, 0f, -6f, 30f * s, d.accent)
            }
            WeaponStyle.SHIELD_SWORD -> {
                blade(canvas, 6f, -L * 0.8f, 9f * s, d.accent, d.outline)
                crossguard(canvas, 0f, -4f, 26f * s, d.accent)
                // shield on other side
                canvas.save()
                canvas.rotate(-25f)
                val sh = 34f * s
                path.reset()
                path.moveTo(-sh * 1.6f, -sh)
                path.lineTo(sh * 0.4f, -sh)
                path.quadTo(sh * 0.5f, sh * 0.2f, -sh * 0.6f, sh * 1.1f)
                path.quadTo(-sh * 1.6f, sh * 0.2f, -sh * 1.6f, -sh)
                path.close()
                outlinePath(canvas, path, d.outline, 4f)
                paint.shader = LinearGradient(-sh, -sh, sh * 0.4f, sh, lighten(d.primary, 0.2f), darken(d.primary, 0.3f), Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint); paint.shader = null
                paint.color = d.accent
                canvas.drawCircle(-sh * 0.55f, -sh * 0.1f, sh * 0.28f, paint)
                canvas.restore()
            }
            WeaponStyle.GREATSWORD -> {
                blade(canvas, 10f, -L * 1.25f, 16f * s, d.accent, d.outline)
                crossguard(canvas, 0f, -8f, 44f * s, d.secondary)
            }
            WeaponStyle.BOW -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f * s
                paint.color = Color.parseColor("#8B5A2B")
                path.reset()
                path.arcTo(RectF(-30f * s, -L * 0.75f, 30f * s, L * 0.05f), -70f, 140f)
                canvas.drawPath(path, paint)
                paint.color = d.outline
                paint.strokeWidth = 9f * s
                canvas.drawArc(RectF(-30f * s, -L * 0.75f, 30f * s, L * 0.05f), -70f, 140f, false, paint)
                paint.style = Paint.Style.FILL
                // string
                paint.color = Color.argb(200, 240, 240, 220)
                paint.strokeWidth = 2.5f
                canvas.drawLine(6f * s, -L * 0.72f, 6f * s, 0f, paint)
                canvas.drawLine(6f * s, 0f, -6f * s, -L * 0.72f, paint)
                paint.style = Paint.Style.FILL
            }
            WeaponStyle.STAFF -> {
                capsule(canvas, 4f, 12f, 0f, -L, 7f * s, d.outline)
                capsule(canvas, 4f, 12f, 0f, -L, 5f * s, Color.parseColor("#6B4226"))
                paint.color = d.accent
                paint.setShadowLayer(12f, 0f, 0f, d.accent)
                canvas.drawCircle(0f, -L - 8f * s, 11f * s, paint)
                paint.clearShadowLayer()
                paint.color = lighten(d.accent, 0.4f)
                canvas.drawCircle(0f, -L - 8f * s, 6f * s, paint)
            }
            WeaponStyle.DAGGERS -> {
                blade(canvas, 6f, -L * 0.55f, 7f * s, d.accent, d.outline)
                canvas.save()
                canvas.translate(-16f * s, 8f)
                canvas.rotate(140f)
                blade(canvas, 6f, -L * 0.55f, 7f * s, d.accent, d.outline)
                canvas.restore()
            }
            WeaponStyle.HAMMER -> {
                capsule(canvas, 4f, 10f, 0f, -L * 0.8f, 8f * s, d.outline)
                capsule(canvas, 4f, 10f, 0f, -L * 0.8f, 6f * s, Color.parseColor("#5B4636"))
                outlineRect(canvas, -20f * s, -L * 0.8f - 20f * s, 20f * s, -L * 0.8f + 4f * s, d.outline, 4f)
                paint.shader = LinearGradient(-20f * s, 0f, 20f * s, 0f, lighten(d.primary, 0.35f), darken(d.primary, 0.25f), Shader.TileMode.CLAMP)
                canvas.drawRect(RectF(-19f * s, -L * 0.8f - 19f * s, 19f * s, -L * 0.8f + 3f * s), paint)
                paint.shader = null
            }
            WeaponStyle.GAUNTLET -> {
                outlineCircle(canvas, 8f, -6f, 16f * s, d.outline, 4f)
                paint.color = d.primary
                canvas.drawCircle(8f, -6f, 15f * s, paint)
                paint.color = d.accent
                paint.setShadowLayer(10f, 0f, 0f, d.accent)
                canvas.drawCircle(8f, -6f, 7f * s, paint)
                paint.clearShadowLayer()
            }
            WeaponStyle.CANNON -> {
                outlineRect(canvas, -6f, -L * 0.85f, 26f * s, 6f, d.outline, 4f)
                paint.shader = LinearGradient(-6f, 0f, 26f * s, 0f, darken(d.secondary, 0.1f), darken(d.secondary, 0.4f), Shader.TileMode.CLAMP)
                canvas.drawRect(RectF(-5f, -L * 0.85f, 25f * s, 5f), paint)
                paint.shader = null
                paint.color = d.accent
                canvas.drawCircle(20f * s, -L * 0.8f, 4f * s, paint)
            }
            WeaponStyle.SCYTHE -> {
                capsule(canvas, 4f, 14f, 0f, -L, 7f * s, d.outline)
                capsule(canvas, 4f, 14f, 0f, -L, 5f * s, Color.parseColor("#3A2C28"))
                path.reset()
                path.moveTo(0f, -L)
                path.quadTo(42f * s, -L - 6f, 50f * s, -L + 34f * s)
                path.quadTo(30f * s, -L + 10f, 0f, -L + 12f)
                path.close()
                outlinePath(canvas, path, d.outline, 4f)
                paint.shader = LinearGradient(0f, -L, 50f * s, -L + 30f, lighten(d.accent, 0.3f), darken(d.accent, 0.15f), Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint)
                paint.shader = null
            }
            WeaponStyle.ORB -> {
                paint.color = d.accent
                paint.setShadowLayer(16f, 0f, 0f, d.accent)
                canvas.drawCircle(10f, -18f * s, 15f * s, paint)
                paint.clearShadowLayer()
                paint.color = lighten(d.accent, 0.5f)
                canvas.drawCircle(10f, -18f * s, 8f * s, paint)
            }
            WeaponStyle.SPEAR -> {
                capsule(canvas, 3f, 20f, -2f, -L * 1.1f, 6f * s, d.outline)
                capsule(canvas, 3f, 20f, -2f, -L * 1.1f, 4f * s, Color.parseColor("#6B4226"))
                path.reset()
                path.moveTo(-2f, -L * 1.1f - 20f * s)
                path.lineTo(6f * s, -L * 1.1f + 2f)
                path.lineTo(-10f * s, -L * 1.1f + 2f)
                path.close()
                outlinePath(canvas, path, d.outline, 3f)
                paint.color = lighten(d.accent, 0.2f)
                canvas.drawPath(path, paint)
            }
            WeaponStyle.CLAWS -> {
                for (i in 0..2) {
                    path.reset()
                    val bx = -6f + i * 10f * s
                    path.moveTo(bx, -4f)
                    path.quadTo(bx + 6f * s, -22f * s, bx - 2f, -34f * s)
                    path.quadTo(bx - 6f * s, -20f * s, bx - 6f, -4f)
                    path.close()
                    outlinePath(canvas, path, d.outline, 3f)
                    paint.color = d.accent
                    canvas.drawPath(path, paint)
                }
            }
            WeaponStyle.CLUB -> {
                capsule(canvas, 4f, 12f, 0f, -L * 0.7f, 8f * s, d.outline)
                capsule(canvas, 4f, 12f, 0f, -L * 0.7f, 6f * s, Color.parseColor("#5D4037"))
                outlineOval(canvas, -14f * s, -L * 0.7f - 16f * s, 14f * s, -L * 0.7f + 6f, d.outline, 4f)
                paint.shader = LinearGradient(-14f * s, 0f, 14f * s, 0f, Color.parseColor("#8D6E63"), Color.parseColor("#5D4037"), Shader.TileMode.CLAMP)
                canvas.drawOval(RectF(-13f * s, -L * 0.7f - 15f * s, 13f * s, -L * 0.7f + 5f), paint)
                paint.shader = null
            }
            WeaponStyle.NONE -> {}
        }
        canvas.restore()
    }

    /* ---------------- primitive helpers ---------------- */

    private fun blade(canvas: Canvas, x: Float, tipY: Float, w: Float, color: Int, outlineColor: Int) {
        val h = -tipY
        path.reset()
        path.moveTo(x - w / 2f, 0f)
        path.lineTo(x - w / 2f - 1f, -h + w * 1.6f)
        path.lineTo(x, -h - 2f)
        path.lineTo(x + w / 2f + 1f, -h + w * 1.6f)
        path.lineTo(x + w / 2f, 0f)
        path.close()
        outlinePath(canvas, path, outlineColor, 4f)
        paint.shader = LinearGradient(x - w, 0f, x + w, 0f, lighten(color, 0.35f), darken(color, 0.15f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = Color.argb(200, 255, 255, 255)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x - w * 0.15f, -w, x - w * 0.15f, -h + w * 2f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun crossguard(canvas: Canvas, x: Float, y: Float, w: Float, color: Int) {
        paint.color = color
        outlineRect(canvas, x - w / 2f, y - 4f, x + w / 2f, y + 4f, design.outline, 3f)
        paint.color = color
        canvas.drawRect(RectF(x - w / 2f, y - 3f, x + w / 2f, y + 3f), paint)
    }

    private fun capsule(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        paint.color = color
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.style = Paint.Style.FILL
    }

    private fun outlinePath(canvas: Canvas, p: Path, color: Int, width: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(p, paint)
        paint.style = Paint.Style.FILL
        paint.strokeJoin = Paint.Join.MITER
    }

    private fun outlineCircle(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, width: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL
    }

    private fun outlineRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, width: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        canvas.drawRect(RectF(l, t, r, b), paint)
        paint.style = Paint.Style.FILL
    }

    private fun outlineOval(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, width: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        canvas.drawOval(RectF(l, t, r, b), paint)
        paint.style = Paint.Style.FILL
    }

    /* ---------------- color utils ---------------- */

    private fun lighten(c: Int, f: Float): Int = shift(c, f)
    private fun darken(c: Int, f: Float): Int = shift(c, -f)

    private fun shift(c: Int, f: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return if (f >= 0) {
            Color.rgb(
                r + ((255 - r) * f).toInt().coerceIn(0, 255),
                g + ((255 - g) * f).toInt().coerceIn(0, 255),
                b + ((255 - b) * f).toInt().coerceIn(0, 255)
            )
        } else {
            val k = 1f + f
            Color.rgb((r * k).toInt().coerceIn(0, 255), (g * k).toInt().coerceIn(0, 255), (b * k).toInt().coerceIn(0, 255))
        }
    }
}
