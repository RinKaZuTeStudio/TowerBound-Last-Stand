package com.towerbound.laststand.heroes

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.Combatant
import com.towerbound.laststand.combat.DamageSystem
import com.towerbound.laststand.combat.StatBlock
import com.towerbound.laststand.combat.StatusKind
import com.towerbound.laststand.combat.StatusTarget
import com.towerbound.laststand.combat.TEAM_PLAYER
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Summoned Shade ally (Morvain's ultimate). A temporary spectral warrior
 * that chases and attacks enemies. Uses the enemy visual system in reverse
 * (team 0 combatant) so player projectiles never hit it.
 */
class ShadeEntity : Combatant, StatusTarget {

    override var x: Float = 0f
    override var y: Float = 0f
    override val radius: Float = 28f
    override val team: Int get() = TEAM_PLAYER

    var alive: Boolean = false
        internal set
    var hp: Float = 0f
        private set
    var lifetime: Float = 0f
    var attackCooldown: Float = 0f
    var facing: Float = 0f
    var facingLeft: Boolean = false

    val anim = com.towerbound.laststand.anim.AnimationController(AnimState.IDLE)
    val stats = StatBlock()

    private val rng = Rng(System.nanoTime())

    fun summon(x: Float, y: Float, ownerStats: StatBlock) {
        this.x = x; this.y = y
        stats.copyFrom(StatBlock().apply {
            maxHp = ownerStats.finalMaxHp * 0.7f
            attackDamage = ownerStats.finalAttackDamage * 0.55f
            moveSpeed = 300f
            attackRange = 60f
            attackSpeed = 1.4f
        })
        hp = stats.finalMaxHp
        lifetime = 22f
        attackCooldown = 0.4f
        alive = true
        anim.play(AnimState.IDLE, force = true)
        anim.addEvent(2, AnimEvent.ON_ATTACK_IMPACT)
    }

    override fun stats(): StatBlock = stats
    override fun statsBlock(): StatBlock = stats
    override fun isAlive(): Boolean = alive

    override fun applyDamage(damage: Float, isCrit: Boolean, source: String): Float {
        // shades are untouchable (spectral)
        return 0f
    }

    override fun onStatusDamage(damage: Float, kind: StatusKind) {}
    override fun onStatusApplied(kind: StatusKind) {}
    override fun onStatusRemoved(kind: StatusKind) {}

    fun update(engine: GameEngine, dt: Float, combat: com.towerbound.laststand.combat.CombatSystem) {
        if (!alive) return
        lifetime -= dt
        if (lifetime <= 0f) {
            alive = false
            return
        }

        // find nearest enemy
        var best: Combatant? = null
        var bestD = Float.MAX_VALUE
        for (t in engine.targetProvider?.invoke(0) ?: emptyList()) {
            if (!t.isAlive()) continue
            val dx = t.x - x; val dy = t.y - y
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = t }
        }
        val target = best
        if (target == null) {
            anim.play(AnimState.IDLE)
            anim.update(dt)
            return
        }

        val dx = target.x - x
        val dy = target.y - y
        val d = sqrt(dx * dx + dy * dy)

        if (attackCooldown > 0f) attackCooldown -= dt

        if (d > radius + target.radius + 8f) {
            // move toward target
            facing = atan2(dy, dx)
            facingLeft = cos(facing) < -0.15f
            x += dx / d * stats.finalMoveSpeed * dt
            y += dy / d * stats.finalMoveSpeed * dt
            anim.play(AnimState.RUN)
        } else if (attackCooldown <= 0f) {
            attackCooldown = 1f / stats.finalAttackSpeed
            facing = atan2(dy, dx)
            facingLeft = cos(facing) < -0.15f
            anim.play(AnimState.ATTACK, force = true)
        }
        anim.update(dt)

        // melee impact on anim event
        for ((event, _) in anim.drainEvents()) {
            if (event == AnimEvent.ON_ATTACK_IMPACT && target.isAlive()) {
                val r = DamageSystem.compute(stats, target.stats(), rng)
                val applied = target.applyDamage(r.damage, r.isCrit, "shade")
                combat.reportHit(target, applied, r.isCrit, "shade")
            }
        }
    }
}

/**
 * ShadowSystem: owns up to 2 simultaneous shades (re-summon replaces oldest).
 */
class ShadowSystem : SimSystem {

    override val systemName: String = "ShadowSystem"

    private val shades = Array(2) { ShadeEntity() }
    val activeShades: List<ShadeEntity> get() = shades.filter { it.alive }

    var fx: com.towerbound.laststand.fx.ParticleManager? = null

    fun summon(x: Float, y: Float, ownerStats: StatBlock) {
        // replace the oldest / a free slot
        val slot = shades.firstOrNull { !it.alive }
            ?: shades.minByOrNull { it.lifetime }
            ?: return
        slot.summon(x, y, ownerStats)
        GameLog.i("Shadows", "shade summoned at (${x.toInt()}, ${y.toInt()})")
    }

    override fun onExitWorld(engine: GameEngine) {
        for (s in shades) s.alive = false
    }

    override fun update(engine: GameEngine, dt: Float) {
        val combat = engine.system<com.towerbound.laststand.combat.CombatSystem>() ?: return
        for (s in shades) {
            if (!s.alive) continue
            s.update(engine, dt, combat)
            if (s.lifetime < 2f) {
                // fading flicker
                if (engine.simRng.chance(0.15f)) fx?.emitMagic(s.x, s.y, 3, "void")
            }
        }
    }
}
