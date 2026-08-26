package com.towerbound.laststand.enemies

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.CombatSystem
import com.towerbound.laststand.combat.Combatant
import com.towerbound.laststand.combat.DamageSystem
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.gameplay.PlayerController
import com.towerbound.laststand.tower.TowerSystem
import kotlin.math.sqrt

/**
 * EnemyManager: pooled enemy lifecycle + AI driver + ranged attack
 * execution + death rewards. Spawn validation prevents enemies inside the
 * player, tower, obstacles or outside the playable area.
 */
class EnemyManager : SimSystem {

    override val systemName: String = "EnemyManager"

    companion object {
        const val MAX_ENEMIES = 200          // design: 200 active enemies max
    }

    private val pool = Array(MAX_ENEMIES) { EnemyEntity() }
    val active = mutableListOf<EnemyEntity>()

    /** Death listeners for XP/gold drops + kill counting (progression phase). */
    var onEnemyDeath: ((enemy: EnemyEntity) -> Unit)? = null

    /** FX + game-feel hooks. */
    var fx: com.towerbound.laststand.fx.ParticleManager? = null
    var combat: CombatSystem? = null
    var onSpawnVfx: ((x: Float, y: Float, enemy: EnemyEntity) -> Unit)? = null

    private val spawnOccupied = mutableListOf<Pair<Float, Float>>()
    private val rng = Rng(System.nanoTime())

    fun activeCount(): Int = active.size
    fun poolSize(): Int = MAX_ENEMIES

    override fun onExitWorld(engine: GameEngine) {
        active.clear()
        for (e in pool) { e.forceDead(); e.target = null; e.rangedAttack = null }
    }

    /**
     * Spawn one enemy of [defId] with wave scaling. Returns the entity or
     * null when the pool is full / position invalid (spawn validation).
     */
    fun spawn(defId: String, x: Float, y: Float, hpScale: Float, dmgScale: Float,
              elite: Boolean = false): EnemyEntity? {
        val world = engineRef?.world ?: return null
        val def = EnemyDefs.byId(defId)

        if (!world.inPlayableArea(x, y, def.radius)) return null
        if (world.dist(x, y, world.centerX, world.centerY) < world.towerRadius + def.radius + 30f) return null

        val e = pool.firstOrNull { !it.alive } ?: return null
        e.reset(def, x, y, hpScale, dmgScale, elite)
        active.add(e)
        onSpawnVfx?.invoke(x, y, e)
        return e
    }

    /** Validated spawn using the world's spawn ring. */
    fun spawnOnRing(defId: String, hpScale: Float, dmgScale: Float, elite: Boolean = false,
                    avoidX: Float = -99999f, avoidY: Float = -99999f): EnemyEntity? {
        val engine = engineRef ?: return null
        val world = engine.world ?: return null
        spawnOccupied.clear()
        for (e in active) spawnOccupied.add(e.x to e.y)
        val pos = world.findSpawnPoint(rng, spawnOccupied, 60f, avoidX, avoidY, 240f)
        return spawn(defId, pos[0], pos[1], hpScale, dmgScale, elite)
    }

    /** Direct access used by the debug tools. */
    fun killAll() {
        for (e in active.toList()) {
            if (e.alive) e.applyDamage(1e9f, false, "debug")
        }
    }

    private var engineRef: GameEngine? = null

    override fun onEnterWorld(engine: GameEngine) {
        engineRef = engine
    }

    override fun update(engine: GameEngine, dt: Float) {
        engineRef = engine
        for (e in pool) if (e.engineRef == null) e.engineRef = engine
        val world = engine.world ?: return
        val player = engine.system<PlayerController>()?.player
        val tower = engine.system<TowerSystem>()?.tower
        val c = combat ?: engine.system<CombatSystem>()
        if (c != null) combat = c
        val statuses = combat?.statuses ?: return

        // wire ranged attacks lazily (combat may attach after enemies)
        for (e in pool) {
            if (e.rangedAttack == null && e.alive) {
                e.rangedAttack = { enemy, target -> fireRanged(enemy, target) }
            }
        }

        val it = active.iterator()
        while (it.hasNext()) {
            val e = it.next()

            e.update(dt, world, statuses, player, tower, active, rng)

            // animation event routing (melee impact frames)
            for ((event, _) in e.anim.drainEvents()) {
                if (event == AnimEvent.ON_ATTACK_IMPACT) {
                    resolveMelee(e)
                }
            }

            // death handling: rewards once, despawn after death anim
            if (!e.alive) {
                if (!e.deathNotified) {
                    e.deathNotified = true
                    notifyDeath(e)
                }
                if (e.deathTimer > 0.55f) {
                    it.remove()
                    continue
                }
            }
        }

        // Sole owner of the combatant provider: team param = shooter team.
        engine.targetProvider = { team ->
            when (team) {
                0 -> active.toList()
                else -> listOfNotNull(
                    player?.takeIf { it.isAlive() },
                    tower?.takeIf { it.isAlive() }
                )
            }
        }
    }

    private fun resolveMelee(e: EnemyEntity) {
        val c = combat ?: return
        val target = e.consumeMeleeTarget() ?: return
        if (!target.isAlive()) return
        val result = DamageSystem.compute(e.stats, target.stats(), rng)
        val applied = target.applyDamage(result.damage, result.isCrit, "enemy_${e.def.id}")
        if (applied > 0f) {
            c.reportHit(target, applied, result.isCrit, "enemy_${e.def.id}")
            if (target is com.towerbound.laststand.tower.TowerEntity) {
                c.onTowerDamage?.invoke(target.hpFraction())
            }
            if (target is com.towerbound.laststand.gameplay.PlayerEntity) {
                c.onPlayerHit?.invoke(applied)
            }
        }
    }

    private fun fireRanged(e: EnemyEntity, target: Combatant) {
        val c = combat ?: return
        val dx = target.x - e.x
        val dy = target.y - e.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f) return
        c.projectiles.ownerStats = e.stats
        c.projectiles.spawn(
            x = e.x + dx / d * (e.radius + 8f),
            y = e.y + dy / d * (e.radius + 8f) - 10f,
            dirX = dx / d, dirY = dy / d,
            speed = if (e.def.attackStyle == AttackStyle.RANGED_ARROW) 460f else 340f,
            team = 1,
            stats = e.stats,
            source = "enemy_${e.def.id}",
            visual = e.def.visualElement,
            radius = 13f
        )
    }

    /** Death rewards + VFX. */
    private fun notifyDeath(e: EnemyEntity) {
        fx?.emitImpact(e.x, e.y, 10, e.def.visualElement)
        if (e.isBoss) {
            fx?.emitExplosion(e.x, e.y, 160f)
        }
        onEnemyDeath?.invoke(e)
        GameLog.d("EnemyMgr", "${e.def.id} died at (${e.x.toInt()},${e.y.toInt()})")
    }
}
