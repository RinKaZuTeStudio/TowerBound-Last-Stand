package com.towerbound.laststand.combat

import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.core.Rng
import kotlin.math.sqrt

/**
 * Projectile framework (design: PROJECTILES section).
 *
 * Supports: speed, direction, homing target, damage snapshot, lifetime,
 * collision, piercing, chaining, AoE impact, trail VFX and impact events.
 * Pooled — no allocation per shot in the steady state.
 */
class Projectile(
    var x: Float, var y: Float,
    var dirX: Float, var dirY: Float,
    var speed: Float,
    var radius: Float,
    var team: Int,
    var damageMult: Float,
    var pierceRemaining: Int,
    var chainRemaining: Int,
    var aoeRadius: Float,
    var lifetime: Float,
    var knockback: Float,
    var visual: String,            // fx key for trail/impact color
    var homingTarget: Combatant?,
    var source: String
) {
    var alive: Boolean = false
    var traveled: Float = 0f
    val hitIds = mutableSetOf<Any>()
}

/**
 * Pooling projectile manager + collision + damage resolution.
 * Targets come from the engine target provider (enemies for team 0 shots,
 * defenders for team 1 shots).
 */
class ProjectileManager : SimSystem {

    companion object {
        const val POOL_SIZE = 320
        const val MAX_LIFETIME = 6f   // projectile infinite-lifetime guard
    }

    override val systemName: String = "ProjectileManager"

    private val pool = Array(POOL_SIZE) {
        Projectile(0f, 0f, 1f, 0f, 0f, 10f, 0, 1f, 0, 0, 0f, 0f, 0f, "magic", null, "")
    }
    val active = mutableListOf<Projectile>()

    /** Injected by the engine: provides current combatants per team. */
    var targetProvider: ((team: Int) -> List<Combatant>)? = null

    /** VFX hooks (fx layer subscribes). */
    var onProjectileImpact: ((x: Float, y: Float, visual: String, isCrit: Boolean) -> Unit)? = null
    var onDamageDealt: ((target: Combatant, amount: Float, isCrit: Boolean, source: String) -> Unit)? = null

    private val rng = Rng(System.nanoTime())

    override fun onExitWorld(engine: GameEngine) {
        active.clear()
        for (p in pool) p.alive = false
    }

    fun spawn(
        x: Float, y: Float, dirX: Float, dirY: Float, speed: Float,
        team: Int, stats: StatBlock, source: String,
        pierce: Int = 0, chain: Int = 0, aoe: Float = 0f,
        visual: String = "magic", homing: Combatant? = null,
        radius: Float = 12f, damageMult: Float = 1f
    ): Projectile? {
        val p = pool.firstOrNull { !it.alive } ?: return null
        val len = sqrt(dirX * dirX + dirY * dirY)
        if (len < 0.0001f) return null
        p.x = x; p.y = y
        p.dirX = dirX / len; p.dirY = dirY / len
        p.speed = speed.coerceIn(60f, 2400f)
        p.radius = radius
        p.team = team
        p.damageMult = damageMult
        p.pierceRemaining = pierce.coerceIn(0, 24)
        p.chainRemaining = chain.coerceIn(0, 12)
        p.aoeRadius = aoe.coerceIn(0f, 500f)
        p.lifetime = MAX_LIFETIME.coerceAtMost(0.6f + 2600f / speed.coerceAtLeast(60f))
        p.knockback = stats.finalKnockback
        p.visual = visual
        p.homingTarget = homing
        p.source = source
        p.alive = true
        p.traveled = 0f
        p.hitIds.clear()
        active.add(p)
        return p
    }

    override fun update(engine: GameEngine, dt: Float) {
        if (active.isEmpty()) return
        // cache target lists per team per frame; team param = shooter's team
        val cache = HashMap<Int, List<Combatant>>()
        val provider = targetProvider ?: { team: Int ->
            engine.targetProvider?.invoke(team) ?: emptyList()
        }

        val it = active.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.lifetime -= dt
            if (p.lifetime <= 0f) {
                p.alive = false; it.remove(); continue
            }

            // homing correction
            val homing = p.homingTarget
            if (homing != null && homing.isAlive()) {
                val dx = homing.x - p.x
                val dy = homing.y - p.y
                val d = sqrt(dx * dx + dy * dy)
                if (d > 1f) {
                    val steer = (dt * 6f).coerceAtMost(1f)
                    p.dirX += (dx / d - p.dirX) * steer
                    p.dirY += (dy / d - p.dirY) * steer
                    val l = sqrt(p.dirX * p.dirX + p.dirY * p.dirY)
                    p.dirX /= l; p.dirY /= l
                }
            }

            val step = p.speed * dt
            p.x += p.dirX * step
            p.y += p.dirY * step
            p.traveled += step

            // collision vs opposing team
            val targets = cache.getOrPut(p.team) { provider(p.team) }
            var consumed = false
            for (t in targets) {
                if (!t.isAlive() || t.team == p.team) continue
                if (p.hitIds.contains(t)) continue
                val dx = t.x - p.x
                val dy = t.y - p.y
                val rr = t.radius + p.radius
                if (dx * dx + dy * dy <= rr * rr) {
                    impact(engine, p, t, targets)
                    if (p.pierceRemaining > 0) {
                        p.pierceRemaining--
                        p.hitIds.add(t)
                    } else {
                        consumed = true
                    }
                    break
                }
            }
            if (consumed) {
                p.alive = false
                it.remove()
            }
        }
    }

    private fun impact(engine: GameEngine, p: Projectile, target: Combatant, allTargets: List<Combatant>) {
        val attacker = ownerStats ?: defaultStats
        val result = DamageSystem.compute(attacker, target.stats(), rng, p.damageMult)
        val applied = target.applyDamage(result.damage, result.isCrit, p.source)
        onDamageDealt?.invoke(target, applied, result.isCrit, p.source)
        onProjectileImpact?.invoke(p.x, p.y, p.visual, result.isCrit)

        // knockback impulse
        if (p.knockback > 0f) {
            (target as? Knockbackable)?.applyKnockback(p.dirX * p.knockback, p.dirY * p.knockback)
        }

        // AoE splash (excludes direct target)
        if (p.aoeRadius > 0f) {
            for (t in allTargets) {
                if (t === target || !t.isAlive() || t.team == p.team) continue
                val dx = t.x - p.x
                val dy = t.y - p.y
                if (dx * dx + dy * dy <= p.aoeRadius * p.aoeRadius) {
                    val splash = DamageSystem.compute(attacker, t.stats(), rng, p.damageMult * 0.6f)
                    val appliedS = t.applyDamage(splash.damage, splash.isCrit, p.source + "_aoe")
                    onDamageDealt?.invoke(t, appliedS, splash.isCrit, p.source + "_aoe")
                }
            }
        }

        // chain lightning hop
        if (p.chainRemaining > 0) {
            p.chainRemaining--
            val next = allTargets.filter {
                it.isAlive() && it.team != p.team && !p.hitIds.contains(it) && it !== target
            }.minByOrNull {
                val dx = it.x - p.x; val dy = it.y - p.y
                dx * dx + dy * dy
            }
            if (next != null) {
                val dx = next.x - p.x
                val dy = next.y - p.y
                val d = sqrt(dx * dx + dy * dy)
                if (d > 1f) {
                    p.dirX = dx / d; p.dirY = dy / d
                    p.hitIds.add(target)
                    // reset small lifetime so chains can land
                    p.lifetime = p.lifetime.coerceAtLeast(0.4f)
                }
            }
        }
    }

    /** Stats snapshot of the shooter (set by whoever spawns). */
    @Volatile var ownerStats: StatBlock? = null
    private val defaultStats = StatBlock()

    fun activeCount(): Int = active.size
    fun poolSize(): Int = POOL_SIZE
}

/** Optional knockback receiver. */
interface Knockbackable {
    fun applyKnockback(ix: Float, iy: Float)
}

/* ================================================================== */
/* Floating damage numbers                                             */
/* ================================================================== */

/**
 * Damage number with pop-scale, rise and fade (design: DAMAGE NUMBERS).
 * Crits are larger with a stronger pop. Rendered via the GL text atlas.
 */
class DamageNumber(
    var x: Float, var y: Float,
    val value: Int,
    val isCrit: Boolean,
    val color: Int
) {
    var age: Float = 0f
    val life: Float = if (isCrit) 1.1f else 0.8f
    var alive: Boolean = true

    fun update(dt: Float) {
        age += dt
        if (age >= life) alive = false
    }

    /** Render helpers: rising offset (+Y = up) + scale envelope + alpha. */
    fun offsetY(): Float = (age * 90f) * (if (isCrit) 1.3f else 1f)
    fun scale(): Float {
        val pop = if (isCrit) 1.7f else 1.25f
        val t = (age / life).coerceIn(0f, 1f)
        val attack = if (age < 0.12f) (age / 0.12f) else 1f
        return (1f + (pop - 1f) * (1f - t) * attack)
    }
    fun alpha(): Float = (1f - (age / life)).coerceIn(0f, 1f).let { if (it > 0.3f) it else it / 0.3f * 0.3f }
}

class DamageNumberManager : SimSystem {
    override val systemName: String = "DamageNumberManager"

    companion object { const val CAP = 120 }

    private val numbers = mutableListOf<DamageNumber>()

    fun spawn(x: Float, y: Float, value: Int, isCrit: Boolean, color: Int) {
        if (numbers.size >= CAP) numbers.removeAt(0)
        numbers.add(DamageNumber(x, y, value, isCrit, color))
    }

    override fun update(engine: GameEngine, dt: Float) {
        if (numbers.isEmpty()) return
        numbers.removeAll { it.update(dt); !it.alive }
    }

    override fun onExitWorld(engine: GameEngine) = numbers.clear()

    fun forEach(action: (DamageNumber) -> Unit) = numbers.forEach(action)
    fun count(): Int = numbers.size
}
