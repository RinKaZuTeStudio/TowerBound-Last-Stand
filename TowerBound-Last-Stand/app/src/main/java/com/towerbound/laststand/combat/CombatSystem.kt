package com.towerbound.laststand.combat

import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.gameplay.PlayerController

/**
 * Central combat coordinator: owns the status-effect framework, buff system,
 * projectile manager and damage numbers, and routes DoT damage to the player.
 * Registered as the FIRST gameplay system (before controllers).
 */
class CombatSystem : SimSystem {

    override val systemName: String = "CombatSystem"

    val statuses = StatusEffectSystem()
    val buffs = BuffSystem()
    val projectiles = ProjectileManager()
    val damageNumbers = DamageNumberManager()

    /** FX layer hook (ParticleManager, attached by GameFlow). */
    var fx: com.towerbound.laststand.fx.ParticleManager? = null

    /** Camera hooks for game-feel (set by GameFlow). */
    var onPlayerHit: ((damage: Float) -> Unit)? = null
    var onTowerDamage: ((fraction: Float) -> Unit)? = null

    /** Damage color scheme (plain ARGB ints — no Android dependency). */
    val colorNormal = 0xFFF4F6FF.toInt()
    val colorCrit = 0xFFFFB300.toInt()
    val colorDot = 0xFF9CCC65.toInt()
    val colorHeal = 0xFF69F0AE.toInt()

    override fun onExitWorld(engine: GameEngine) {
        statuses.reset()
        buffs.reset()
    }

    override fun update(engine: GameEngine, dt: Float) {
        statuses.update(dt)
        buffs.update(dt)
        projectiles.update(engine, dt)
        damageNumbers.update(engine, dt)

        // Route pending DoT damage on the player (applied post-mitigation).
        val player = engine.system<PlayerController>()?.player
        if (player != null) {
            val dot = player.consumePendingDot()
            if (dot > 0f) {
                val applied = player.takeDamage(dot)
                if (applied > 0f) {
                    damageNumbers.spawn(player.x, player.y - 40f, applied.toInt(), false, colorDot)
                    onPlayerHit?.invoke(applied)
                }
            }
        }
    }

    /** Shared helper: spawn a damage number + impact particles for a hit. */
    fun reportHit(target: Combatant, amount: Float, isCrit: Boolean, source: String) {
        damageNumbers.spawn(
            target.x, target.y - target.radius - 6f,
            amount.toInt().coerceAtLeast(1), isCrit,
            if (isCrit) colorCrit else colorNormal
        )
        if (isCrit) {
            fx?.emitImpact(target.x, target.y, 14, "impact")
            fx?.emitMagic(target.x, target.y, 6, "magic")
        } else {
            fx?.emitHitSpark(target.x, target.y, 1f, 0f)
        }
        GameLog.d("Combat", "hit $source -> ${amount.toInt()} (crit=$isCrit)")
    }
}
