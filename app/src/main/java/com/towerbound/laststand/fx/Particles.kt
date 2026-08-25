package com.towerbound.laststand.fx

import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pooled particle engine (design: PARTICLE SYSTEM + GAME FEEL sections).
 *
 * Effects: fire, lightning, smoke, sparks, explosion, magic, frost, poison,
 * impact, healing, shield, confetti, tower repair. Maximum particle count
 * configurable (default 300); dynamic quality scales spawn rates.
 */
class ParticleManager(private val maxParticles: Int = 300) : SimSystem {

    override val systemName: String = "ParticleManager"

    class Particle {
        var alive = false
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f
        var size = 10f; var endSize = 4f
        var drag = 1f
        var gravity = 0f
        var spin = 0f; var angle = 0f
        var texture = "glow_impact"
        var additive = true
        var alpha = 1f
    }

    private val pool = Array(maxParticles) { Particle() }
    val activeList = mutableListOf<Particle>()
    private val rng = Rng(System.nanoTime())

    /** Global spawn multiplier for dynamic quality (0.25..1). */
    @Volatile var spawnScale: Float = 1f

    /** Reduced-VFX accessibility setting. */
    @Volatile var reducedVfx: Boolean = false

    fun activeCount(): Int = activeList.size
    fun capacity(): Int = maxParticles

    private fun obtain(): Particle? {
        if (activeList.size >= maxParticles) return null
        val p = pool.firstOrNull { !it.alive } ?: return null
        p.alive = true
        p.alpha = 1f
        p.gravity = 0f
        p.spin = 0f
        p.angle = 0f
        p.additive = true
        p.texture = "glow_impact"
        activeList.add(p)
        return p
    }

    override fun onExitWorld(engine: GameEngine) {
        activeList.clear()
        for (p in pool) p.alive = false
    }

    override fun update(engine: GameEngine, dt: Float) {
        if (activeList.isEmpty()) return
        val it = activeList.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                it.remove()
                continue
            }
            p.vy -= p.gravity * dt
            p.vx *= p.drag
            p.vy *= p.drag
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
        }
    }

    /** Normalized life 1..0 for rendering. */
    fun lifeT(p: Particle): Float = (p.life / p.maxLife).coerceIn(0f, 1f)

    fun sizeOf(p: Particle): Float {
        val t = 1f - lifeT(p) // 0 at spawn -> 1 at death
        return p.size + (p.endSize - p.size) * t
    }

    private fun countScale(): Float = (if (reducedVfx) 0.4f else 1f) * spawnScale

    private fun Int.scaled(): Int = (this * countScale()).toInt().coerceAtLeast(1)

    /* ---------------- emitters ---------------- */

    fun emitImpact(x: Float, y: Float, count: Int = 10, color: String = "impact") {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val sp = rng.range(90f, 320f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = rng.range(0.18f, 0.4f); p.life = p.maxLife
            p.size = rng.range(10f, 20f); p.endSize = 2f
            p.drag = 0.86f
            p.texture = "spark_$color"
            p.spin = rng.nextFloat() * 360f
        }
    }

    fun emitHitSpark(x: Float, y: Float, dirX: Float, dirY: Float) {
        repeat(6.scaled()) {
            val p = obtain() ?: return
            val spread = rng.range(-0.7f, 0.7f)
            val a = kotlin.math.atan2(dirY, dirX) + spread
            val sp = rng.range(180f, 420f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = rng.range(0.12f, 0.25f); p.life = p.maxLife
            p.size = rng.range(12f, 22f); p.endSize = 2f
            p.drag = 0.82f
            p.texture = "spark_impact"
            p.angle = Math.toDegrees(a.toDouble()).toFloat()
        }
    }

    fun emitExplosion(x: Float, y: Float, radius: Float) {
        repeat(26.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val sp = rng.range(60f, radius * 3.2f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = rng.range(0.3f, 0.7f); p.life = p.maxLife
            p.size = rng.range(radius * 0.22f, radius * 0.5f); p.endSize = radius * 0.08f
            p.drag = 0.9f
            p.texture = "glow_fire"
        }
        repeat(8.scaled()) {
            val p = obtain() ?: return
            p.x = x + rng.range(-radius * 0.3f, radius * 0.3f)
            p.y = y + rng.range(-radius * 0.3f, radius * 0.3f)
            p.vx = rng.range(-30f, 30f); p.vy = rng.range(30f, 90f)
            p.maxLife = rng.range(0.7f, 1.3f); p.life = p.maxLife
            p.size = radius * 0.4f; p.endSize = radius * 0.9f
            p.drag = 0.96f; p.gravity = -20f
            p.texture = "smoke"
            p.additive = false
            p.alpha = 0.5f
        }
    }

    fun emitFire(x: Float, y: Float, count: Int = 6) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            p.x = x + rng.range(-8f, 8f); p.y = y + rng.range(-8f, 8f)
            p.vx = rng.range(-20f, 20f); p.vy = rng.range(40f, 110f)
            p.maxLife = rng.range(0.3f, 0.6f); p.life = p.maxLife
            p.size = rng.range(12f, 24f); p.endSize = 3f
            p.drag = 0.95f; p.gravity = -30f
            p.texture = "glow_fire"
        }
    }

    fun emitSmoke(x: Float, y: Float, count: Int = 4) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            p.x = x + rng.range(-10f, 10f); p.y = y
            p.vx = rng.range(-15f, 15f); p.vy = rng.range(30f, 70f)
            p.maxLife = rng.range(0.8f, 1.6f); p.life = p.maxLife
            p.size = 14f; p.endSize = 44f
            p.drag = 0.97f; p.gravity = -12f
            p.texture = "smoke"
            p.additive = false
            p.alpha = 0.45f
        }
    }

    fun emitLightning(x: Float, y: Float, count: Int = 10) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val sp = rng.range(150f, 500f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = rng.range(0.08f, 0.2f); p.life = p.maxLife
            p.size = rng.range(14f, 30f); p.endSize = 2f
            p.drag = 0.8f
            p.texture = "spark_magic"
        }
    }

    fun emitMagic(x: Float, y: Float, count: Int = 10, color: String = "magic") {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val sp = rng.range(30f, 140f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = rng.range(0.4f, 0.9f); p.life = p.maxLife
            p.size = rng.range(10f, 22f); p.endSize = 2f
            p.drag = 0.94f
            p.texture = "glow_$color"
        }
    }

    fun emitFrost(x: Float, y: Float, count: Int = 12) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val sp = rng.range(50f, 190f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = rng.range(0.4f, 0.8f); p.life = p.maxLife
            p.size = rng.range(8f, 16f); p.endSize = 3f
            p.drag = 0.9f
            p.texture = "glow_frost"
        }
    }

    fun emitPoison(x: Float, y: Float, count: Int = 10) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            p.x = x + rng.range(-14f, 14f); p.y = y + rng.range(-6f, 6f)
            p.vx = rng.range(-24f, 24f); p.vy = rng.range(10f, 50f)
            p.maxLife = rng.range(0.6f, 1.2f); p.life = p.maxLife
            p.size = rng.range(10f, 20f); p.endSize = 4f
            p.drag = 0.96f
            p.texture = "glow_poison"
        }
    }

    fun emitHeal(x: Float, y: Float, count: Int = 8) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            p.x = x + rng.range(-20f, 20f); p.y = y + rng.range(-6f, 6f)
            p.vx = rng.range(-8f, 8f); p.vy = rng.range(50f, 120f)
            p.maxLife = rng.range(0.5f, 1f); p.life = p.maxLife
            p.size = rng.range(8f, 14f); p.endSize = 3f
            p.drag = 0.97f
            p.texture = "glow_heal"
        }
    }

    fun emitShield(x: Float, y: Float, count: Int = 12) {
        repeat(count.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val r = 40f
            p.x = x + cos(a) * r; p.y = y + sin(a) * r
            p.vx = cos(a) * 60f; p.vy = sin(a) * 60f
            p.maxLife = rng.range(0.3f, 0.6f); p.life = p.maxLife
            p.size = 12f; p.endSize = 4f
            p.drag = 0.92f
            p.texture = "glow_shield"
        }
    }

    fun emitTowerRepair(x: Float, y: Float) {
        repeat(12.scaled()) {
            val p = obtain() ?: return
            p.x = x + rng.range(-50f, 50f); p.y = y + rng.range(-60f, 60f)
            p.vx = rng.range(-10f, 10f); p.vy = rng.range(30f, 90f)
            p.maxLife = rng.range(0.5f, 1f); p.life = p.maxLife
            p.size = rng.range(8f, 14f); p.endSize = 2f
            p.drag = 0.96f
            p.texture = "star_gold"
            p.spin = rng.range(-180f, 180f)
        }
    }

    fun emitConfetti(x: Float, y: Float) {
        repeat(30.scaled()) {
            val p = obtain() ?: return
            val a = rng.nextFloat() * 6.28318f
            val sp = rng.range(120f, 420f)
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = kotlin.math.abs(sin(a)) * sp + 120f
            p.maxLife = rng.range(0.8f, 1.6f); p.life = p.maxLife
            p.size = rng.range(6f, 12f); p.endSize = 6f
            p.drag = 0.99f; p.gravity = 320f
            p.texture = if (rng.chance(0.5f)) "star_gold" else "glow_impact"
            p.spin = rng.range(-360f, 360f)
        }
    }

    /** Ambient dust (falling) or fireflies (rising glow). */
    fun emitAmbient(x: Float, y: Float, firefly: Boolean) {
        val p = obtain() ?: return
        p.x = x; p.y = y
        p.vx = rng.range(-12f, 12f)
        p.vy = if (firefly) rng.range(6f, 28f) else rng.range(-18f, -6f)
        p.maxLife = rng.range(1.5f, 3.5f); p.life = p.maxLife
        p.size = if (firefly) rng.range(4f, 8f) else rng.range(3f, 6f)
        p.endSize = p.size * 0.6f
        p.drag = 0.995f
        p.texture = if (firefly) "glow_impact" else "smoke"
        p.additive = firefly
        p.alpha = if (firefly) 0.8f else 0.18f
    }
}
