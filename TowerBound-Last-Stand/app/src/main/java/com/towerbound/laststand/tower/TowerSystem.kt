package com.towerbound.laststand.tower

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
 * Central tower entity. Combatant on team 0 so enemy attacks can hit it.
 * Owns HP, shield (absorbs damage first), regeneration, armor, turret stats
 * and the visual damage stage. Tower death triggers the game-over sequence.
 */
class TowerEntity(
    override var x: Float,
    override var y: Float,
    val stats: StatBlock
) : Combatant, StatusTarget {

    override val radius: Float = 78f
    override val team: Int get() = TEAM_PLAYER

    var hp: Float = stats.finalMaxHp
        private set
    var shield: Float = 0f
        internal set

    var alive: Boolean = true
        private set

    /** Death cinematic state. */
    var deathTimer: Float = -1f

    /** Pending DoT accumulator. */
    var pendingDotDamage: Float = 0f
        private set

    val turret: Turret = Turret()

    override fun stats(): StatBlock = stats
    override fun statsBlock(): StatBlock = stats
    override fun isAlive(): Boolean = alive

    override fun onStatusDamage(damage: Float, kind: StatusKind) {
        if (damage.isNaN() || damage < 0f) return
        pendingDotDamage += damage
    }
    override fun onStatusApplied(kind: StatusKind) {}
    override fun onStatusRemoved(kind: StatusKind) {}

    fun maxHp(): Float = stats.finalMaxHp
    fun maxShield(): Float = towerMaxShield
    fun hpFraction(): Float = (hp / maxHp()).coerceIn(0f, 1f)
    fun shieldFraction(): Float = if (towerMaxShield > 0f) (shield / towerMaxShield).coerceIn(0f, 1f) else 0f

    /** Tower shield capacity comes from the upgradeable tower meta. */
    var towerMaxShield: Float = 0f

    /** Damage stage 0..4 for rendering. */
    fun damageStage(): Int = when {
        hpFraction() > 0.8f -> 0
        hpFraction() > 0.6f -> 1
        hpFraction() > 0.4f -> 2
        hpFraction() > 0.2f -> 3
        else -> 4
    }

    /**
     * Damage application: shield absorbs first, then HP. Returns the damage
     * actually applied to HP+shield combined (for numbers).
     */
    override fun applyDamage(damage: Float, isCrit: Boolean, source: String): Float {
        if (!alive) return 0f
        var d = damage
        if (d.isNaN() || d.isInfinite() || d < 0f) d = 0f
        if (d <= 0f) return 0f

        var remaining = d
        if (shield > 0f) {
            val absorbed = shield.coerceAtMost(remaining)
            shield -= absorbed
            remaining -= absorbed
        }
        if (remaining > 0f) {
            hp = (hp - remaining).coerceAtLeast(0f)
            if (hp <= 0f && alive) {
                alive = false
                deathTimer = 0f
                GameLog.w("Tower", "TOWER DESTROYED by $source")
            }
        }
        return d
    }

    fun heal(amount: Float) {
        if (!alive) return
        hp = (hp + amount.coerceAtLeast(0f)).coerceAtMost(maxHp())
    }

    fun restoreShield(amount: Float) {
        if (!alive) return
        shield = (shield + amount.coerceAtLeast(0f)).coerceAtMost(towerMaxShield)
    }

    /** Between-wave repair: percentage of max HP + full shield. */
    fun repairBetweenWaves(fraction: Float) {
        if (!alive) return
        heal(maxHp() * fraction.coerceIn(0f, 1f))
        shield = towerMaxShield
    }

    fun consumePendingDot(): Float {
        val v = pendingDotDamage
        pendingDotDamage = 0f
        return v
    }

    /** Fixed-step regen/shield recharge. */
    fun update(dt: Float) {
        if (!alive) {
            if (deathTimer >= 0f) deathTimer += dt
            return
        }
        if (stats.finalHpRegen > 0f) heal(stats.finalHpRegen * dt)
        if (towerMaxShield > 0f && shield < towerMaxShield) {
            shield = (shield + shieldRegenRate * dt).coerceAtMost(towerMaxShield)
        }
    }

    var shieldRegenRate: Float = 8f

    /** Auto-turret sub-weapon. */
    inner class Turret {
        var cooldown: Float = 0f
        var fireInterval: Float = 1.4f
        var damageMult: Float = 1f
        var range: Float = 380f
        var enabled: Boolean = true
        var angle: Float = 0f

        fun update(dt: Float, engine: GameEngine, combat: com.towerbound.laststand.combat.CombatSystem) {
            if (!enabled || !this@TowerEntity.alive) return
            if (cooldown > 0f) cooldown -= dt
            // find nearest enemy in range
            val targets = engine.targetProvider?.invoke(TEAM_PLAYER) ?: return
            var best: Combatant? = null
            var bestD = Float.MAX_VALUE
            for (t in targets) {
                if (!t.isAlive()) continue
                val dx = t.x - x
                val dy = t.y - y
                val d = dx * dx + dy * dy
                if (d <= range * range && d < bestD) { bestD = d; best = t }
            }
            val target = best ?: return
            angle = atan2(target.y - y, target.x - x)
            if (cooldown <= 0f) {
                cooldown = fireInterval
                val stats = this@TowerEntity.stats
                combat.projectiles.ownerStats = stats
                combat.projectiles.spawn(
                    x = x + cos(angle) * 60f,
                    y = y + sin(angle) * 60f - 30f,
                    dirX = cos(angle), dirY = sin(angle),
                    speed = 700f,
                    team = TEAM_PLAYER,
                    stats = stats.clone().apply {
                        attackDamage = stats.attackDamage * damageMult
                        critChance = 0.05f
                    },
                    source = "turret",
                    visual = "shield",
                    homing = target
                )
            }
        }
    }
}

/**
 * TowerSystem: owns the tower entity lifecycle, wires it into the engine
 * target provider (enemy shots can hit it), routes DoT and emits the
 * dramatic death sequence (explosion + shake + flash + slow motion).
 */
class TowerSystem : SimSystem {

    override val systemName: String = "TowerSystem"

    var tower: TowerEntity? = null
        private set

    /** Set by GameFlow for game-feel hooks on tower damage/death. */
    var onTowerDamaged: ((fraction: Float) -> Unit)? = null
    var onTowerDestroyed: (() -> Unit)? = null
    var fx: com.towerbound.laststand.fx.ParticleManager? = null
    var combat: com.towerbound.laststand.combat.CombatSystem? = null

    private var smokeTimer = 0f
    private var deathNotified = false

    override fun onEnterWorld(engine: GameEngine) {
        val world = engine.world ?: return
        val stats = towerStats()
        val t = TowerEntity(world.centerX, world.centerY, stats)
        t.towerMaxShield = metaShieldCapacity()
        t.shield = t.towerMaxShield
        // permanent turret upgrade: faster fire interval (1.4s base, -12%/level, floor 0.5s)
        t.turret.fireInterval = (1.4f * (1f - metaTurretBonus())).coerceAtLeast(0.5f)
        tower = t
        deathNotified = false
        GameLog.i("Tower", "spawned with ${stats.finalMaxHp.toInt()} hp (shield ${t.towerMaxShield.toInt()})")
    }

    override fun onExitWorld(engine: GameEngine) {
        tower = null
    }

    override fun update(engine: GameEngine, dt: Float) {
        val t = tower ?: return
        t.update(dt)

        // route DoT
        val dot = t.consumePendingDot()
        if (dot > 0f) {
            t.applyDamage(dot, false, "dot")
        }

        // turret
        combat?.let { t.turret.update(dt, engine, it) }

        // continuous damage-stage smoke/fire ambience
        val stage = t.damageStage()
        if (t.alive && stage >= 2) {
            smokeTimer -= dt
            if (smokeTimer <= 0f) {
                smokeTimer = if (stage >= 3) 0.35f else 0.8f
                fx?.emitSmoke(t.x + (Math.random().toFloat() - 0.5f) * 90f, t.y - 60f, if (stage >= 3) 3 else 1)
                if (stage >= 3) fx?.emitFire(t.x + (Math.random().toFloat() - 0.5f) * 70f, t.y - 30f, 2)
            }
        }

        // death cinematic timeline
        if (!t.alive && t.deathTimer >= 0f) {
            val elapsed = t.deathTimer
            when {
                elapsed < 1.6f -> {
                    // rumble: shake + sparks + fire
                    if (elapsed % 0.2f < dt) {
                        fx?.emitExplosion(t.x + (Math.random().toFloat() - 0.5f) * 120f,
                            t.y + (Math.random().toFloat() - 0.5f) * 140f, 60f)
                    }
                }
                elapsed < 2.2f && !deathNotified -> {
                    deathNotified = true
                    // final detonation
                    fx?.emitExplosion(t.x, t.y, 240f)
                    fx?.emitConfetti(t.x, t.y - 40f)
                    onTowerDestroyed?.invoke()
                }
            }
        }

        // feed the visual readout used by WorldRenderer before combat exists
        engine.towerHpFraction = t.hpFraction()
    }

    /** Base tower stats with permanent meta upgrades applied (phase 16). */
    private fun towerStats(): StatBlock = StatBlock().apply {
        val levels = metaTowerLevels()
        maxHp = 1500f + levels.getValue(MetaTowerKind.MAX_HP) * 160f
        armor = 20f
        attackDamage = 14f + levels.getValue(MetaTowerKind.DAMAGE) * 3f
        hpRegen = 2f + levels.getValue(MetaTowerKind.REGEN) * 0.8f
    }

    /** Permanent shield capacity from meta upgrades. */
    fun metaShieldCapacity(): Float = 60f + metaTowerLevels().getValue(MetaTowerKind.SHIELD) * 22f

    /** Turret fire-rate multiplier from meta upgrades. */
    fun metaTurretBonus(): Float = metaTowerLevels().getValue(MetaTowerKind.TURRET) * 0.12f

    private fun metaTowerLevels(): Map<MetaTowerKind, Int> {
        val result = HashMap<MetaTowerKind, Int>()
        for (kind in MetaTowerKind.entries) result[kind] = 0
        try {
            val session = com.towerbound.laststand.meta.MetaSession
            if (session.initialized) {
                for (kind in MetaTowerKind.entries) {
                    result[kind] = session.profile.towerLevels[kind] ?: 0
                }
            }
        } catch (_: Throwable) {
            // meta not ready (tests): base stats
        }
        return result
    }

    fun reportHit(t: TowerEntity, amount: Float) {
        onTowerDamaged?.invoke(t.hpFraction())
    }
}

/** Meta tower upgrade kinds re-exported for gameplay-side reading. */
internal typealias MetaTowerKind = com.towerbound.laststand.meta.TowerUpgrades.Kind
