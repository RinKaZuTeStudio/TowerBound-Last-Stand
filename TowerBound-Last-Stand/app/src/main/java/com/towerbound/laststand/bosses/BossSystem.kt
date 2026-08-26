package com.towerbound.laststand.bosses

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.Buff
import com.towerbound.laststand.combat.Combatant
import com.towerbound.laststand.combat.DamageSystem
import com.towerbound.laststand.combat.StatusKind
import com.towerbound.laststand.combat.StatBlock
import com.towerbound.laststand.combat.StatusTarget
import com.towerbound.laststand.combat.TEAM_ENEMY
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.enemies.EnemyEntity
import com.towerbound.laststand.gameplay.PlayerEntity
import com.towerbound.laststand.tower.TowerEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Boss personality: each named boss has distinct attacks and phase
 * behaviors (design: BOSS SYSTEM + CONTENT QUALITY).
 */
enum class BossPhase { ENTRANCE, PHASE_1, PHASE_2, PHASE_3, PHASE_4, DYING }

data class BossAttack(
    val id: String,
    val cooldown: Float,
    val telegraph: Float,       // warning time before the strike lands
    val execute: (boss: BossEntity, engine: GameEngine) -> Unit
)

/**
 * The boss entity. Large HP pool, telegraphed attacks, phase transitions
 * at 75/50/25% HP with transformations, aggression spikes and new VFX.
 */
class BossEntity : Combatant, StatusTarget {

    override var x: Float = 0f
    override var y: Float = 0f
    override var radius: Float = 64f
    override val team: Int get() = TEAM_ENEMY

    var designId: String = "BOSS_GRAULK"
    var displayName: String = "Graulk the Unmaker"

    val stats = StatBlock()
    var hp: Float = 1f
        private set
    var alive: Boolean = false
        private set

    var phase: BossPhase = BossPhase.ENTRANCE
        private set
    var entranceTimer: Float = 0f
    var deathTimer: Float = 0f

    var phaseNumber: Int = 1
        private set

    /** Attack scheduling. */
    private var attackTimer: Float = 3f
    private var currentAttack: BossAttack? = null
    private var telegraphTimer: Float = 0f
    var telegraphTarget: Combatant? = null
        internal set
    private var telegraphX: Float = 0f
    private var telegraphY: Float = 0f
    private var telegraphRadius: Float = 0f

    var facing: Float = 0f
    var facingLeft: Boolean = false

    val anim = com.towerbound.laststand.anim.AnimationController(AnimState.IDLE)

    private fun attackPoolRandom(): BossAttack? =
        if (attackPool.isEmpty()) null else attackPool[rng.nextInt(attackPool.size)]

    private val rng = Rng(System.nanoTime())

    /** Attacks available in the current phase (grows with phases). */
    private val attackPool = mutableListOf<BossAttack>()

    /** UI hooks. */
    var onPhaseChange: ((phase: Int) -> Unit)? = null
    var onTelegraph: ((x: Float, y: Float, radius: Float, t: Float) -> Unit)? = null

    fun spawn(designId: String, displayName: String, x: Float, y: Float, hpScale: Float, dmgScale: Float) {
        this.designId = designId
        this.displayName = displayName
        this.x = x; this.y = y
        radius = 64f
        stats.copyFrom(StatBlock().apply {
            maxHp = 1400f * hpScale
            attackDamage = 30f * dmgScale
            moveSpeed = 74f
            attackRange = 110f
            attackSpeed = 0.7f
            armor = 20f
        })
        hp = stats.finalMaxHp
        alive = true
        phase = BossPhase.ENTRANCE
        entranceTimer = 2.6f
        phaseNumber = 1
        attackTimer = 3f
        currentAttack = null
        attackPool.clear()
        buildPhaseAttacks()
        anim.play(AnimState.IDLE, force = true)
    }

    private fun buildPhaseAttacks() {
        attackPool.clear()
        attackPool.add(ATTACK_SLAM)
        if (phaseNumber >= 2) attackPool.add(ATTACK_CHARGE)
        if (phaseNumber >= 3) attackPool.add(ATTACK_NOVA)
        if (phaseNumber >= 4) attackPool.add(ATTACK_METEOR)
    }

    override fun stats(): StatBlock = stats
    override fun statsBlock(): StatBlock = stats
    override fun isAlive(): Boolean = alive

    override fun applyDamage(damage: Float, isCrit: Boolean, source: String): Float {
        if (!alive || phase == BossPhase.ENTRANCE) return 0f
        var d = damage
        if (d.isNaN() || d.isInfinite() || d < 0f) d = 0f
        hp -= d
        checkPhase()
        if (hp <= 0f) {
            hp = 0f
            alive = false
            phase = BossPhase.DYING
            deathTimer = 0f
            anim.play(AnimState.DEATH, force = true)
        }
        return d
    }

    override fun onStatusDamage(damage: Float, kind: StatusKind) {}
    override fun onStatusApplied(kind: StatusKind) {}
    override fun onStatusRemoved(kind: StatusKind) {}

    fun maxHp(): Float = stats.finalMaxHp
    fun hpFraction(): Float = (hp / maxHp()).coerceIn(0f, 1f)

    private fun checkPhase() {
        val f = hpFraction()
        val newPhase = when {
            f > 0.75f -> 1
            f > 0.5f -> 2
            f > 0.25f -> 3
            f > 0f -> 4
            else -> 4
        }
        if (newPhase != phaseNumber && alive) {
            phaseNumber = newPhase
            buildPhaseAttacks()
            // aggression spike
            stats.pct.moveSpeed += 0.12f
            stats.pct.attackSpeed += 0.15f
            onPhaseChange?.invoke(newPhase)
        }
    }

    /**
     * Boss AI update. Uses the shared enemy movement contract loosely:
     * entrance cinematic, chase + telegraphed attacks, death slow-mo.
     */
    fun update(engine: GameEngine, dt: Float,
               combat: com.towerbound.laststand.combat.CombatSystem,
               fx: com.towerbound.laststand.fx.ParticleManager) {
        if (!alive) {
            deathTimer += dt
            anim.update(dt)
            return
        }

        // entrance cinematic
        if (phase == BossPhase.ENTRANCE) {
            entranceTimer -= dt
            anim.play(AnimState.VICTORY)
            anim.update(dt)
            if (entranceTimer % 0.4f < dt) fx.emitExplosion(x + (rng.nextFloat() - 0.5f) * 160f, y + (rng.nextFloat() - 0.5f) * 160f, 50f)
            if (entranceTimer <= 0f) {
                phase = BossPhase.PHASE_1
            }
            return
        }

        // pick target: closer of player / tower
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player
        val tower = engine.system<com.towerbound.laststand.tower.TowerSystem>()?.tower
        val target: Combatant? = when {
            player?.alive == true && tower?.alive == true ->
                if (dist(player) < dist(tower)) player else tower
            player?.alive == true -> player
            tower?.alive == true -> tower
            else -> null
        }

        if (target == null) {
            anim.play(AnimState.IDLE)
            anim.update(dt)
            return
        }

        // telegraph in progress?
        if (currentAttack != null) {
            telegraphTimer -= dt
            onTelegraph?.invoke(telegraphX, telegraphY, telegraphRadius, telegraphTimer)
            if (telegraphTimer <= 0f) {
                val atk = currentAttack!!
                currentAttack = null
                atk.execute(this, engine)
            }
            anim.update(dt)
            return
        }

        val dx = target.x - x
        val dy = target.y - y
        val d = sqrt(dx * dx + dy * dy)

        if (d > radius + target.radius + 40f) {
            // chase
            facing = atan2(dy, dx)
            facingLeft = cos(facing) < -0.15f
            x += dx / d * stats.finalMoveSpeed * dt
            y += dy / d * stats.finalMoveSpeed * dt
            anim.play(AnimState.WALK)
        } else {
            anim.play(AnimState.IDLE)
        }

        // schedule attacks faster with phases
        attackTimer -= dt * stats.finalAttackSpeed
        if (attackTimer <= 0f) {
            val atk = attackPoolRandom()
            if (atk != null) {
                attackTimer = atk.cooldown * (1f - 0.08f * (phaseNumber - 1))
                // telegraph setup
                currentAttack = atk
                telegraphTimer = atk.telegraph
                telegraphTarget = target
                telegraphX = target.x
                telegraphY = target.y
                telegraphRadius = if (atk.id == "nova") 240f else 120f
            }
        }
        anim.update(dt)
    }

    private fun dist(c: Combatant): Float {
        val dx = c.x - x; val dy = c.y - y
        return sqrt(dx * dx + dy * dy)
    }

    /** Public AoE wrapper for companion attack definitions. */
    fun dealAoEPublic(engine: GameEngine, cx: Float, cy: Float, radius: Float, mult: Float, source: String) =
        dealAoE(engine, cx, cy, radius, mult, source)

    /* ---------------- shared attacks ---------------- */

    private fun dealAoE(engine: GameEngine, cx: Float, cy: Float, radius: Float, mult: Float, source: String) {
        val combat = engine.system<com.towerbound.laststand.combat.CombatSystem>() ?: return
        val fx = engine.system<com.towerbound.laststand.fx.ParticleManager>() ?: return
        fx.emitExplosion(cx, cy, radius * 0.5f)
        val victims = mutableListOf<Combatant>()
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player
        val tower = engine.system<com.towerbound.laststand.tower.TowerSystem>()?.tower
        if (player != null && player.alive) victims.add(player)
        if (tower != null && tower.alive) victims.add(tower)
        for (v in victims) {
            val dx = v.x - cx; val dy = v.y - cy
            if (dx * dx + dy * dy <= (radius + v.radius) * (radius + v.radius)) {
                val r = DamageSystem.compute(stats, v.stats(), rng, mult)
                val applied = v.applyDamage(r.damage, r.isCrit, source)
                combat.reportHit(v, applied, r.isCrit, source)
                if (v is PlayerEntity) combat.onPlayerHit?.invoke(applied)
                if (v is TowerEntity) combat.onTowerDamage?.invoke(v.hpFraction())
            }
        }
    }

    companion object {
        val ATTACK_SLAM = BossAttack("slam", cooldown = 4.2f, telegraph = 0.7f) { boss, engine ->
            boss.dealAoEPublic(engine, boss.x, boss.y, 150f, 1.6f, "boss_slam")
            engine.clock.hitStop(0.08f)
        }
        val ATTACK_CHARGE = BossAttack("charge", cooldown = 6f, telegraph = 0.9f) { boss, engine ->
            boss.chargeTo(engine, boss.x + (boss.facingDirection().first) * 420f, boss.y + boss.facingDirection().second * 420f)
        }
        val ATTACK_NOVA = BossAttack("nova", cooldown = 8f, telegraph = 1.1f) { boss, engine ->
            boss.dealAoEPublic(engine, boss.x, boss.y, 250f, 1.3f, "boss_nova")
        }
        val ATTACK_METEOR = BossAttack("meteor", cooldown = 7f, telegraph = 1.2f) { boss, engine ->
            repeat(3) { i ->
                val a = i / 3f * 6.28318f
                val mx = boss.x + cos(a) * 200f
                val my = boss.y + sin(a) * 200f
                boss.dealAoEPublic(engine, mx, my, 110f, 1.5f, "boss_meteor")
            }
        }

        private fun BossEntity.facingDirection(): Pair<Float, Float> {
            val dx = telegraphTarget?.x ?: x + cos(facing) * 100f
            val dy = telegraphTarget?.y ?: y + sin(facing) * 100f
            val ddx = dx - x; val ddy = dy - y
            val d = sqrt(ddx * ddx + ddy * ddy).coerceAtLeast(1f)
            return (ddx / d) to (ddy / d)
        }

        private fun BossEntity.chargeTo(engine: GameEngine, tx: Float, ty: Float) {
            val fx = engine.system<com.towerbound.laststand.fx.ParticleManager>()
            val steps = 8
            for (i in 0..steps) {
                val px = x + (tx - x) * i / steps
                val py = y + (ty - y) * i / steps
                fx?.emitImpact(px, py, 4, "fire")
            }
            x = tx.coerceIn(80f, (engine.world?.width ?: 2400f) - 80f)
            y = ty.coerceIn(80f, (engine.world?.height ?: 1600f) - 80f)
            dealAoE(engine, x, y, 130f, 1.8f, "boss_charge")
        }
    }
}

/** (Removed extension property placeholder.) */

/**
 * BossSystem: spawns named bosses on boss waves, drives phases, banner UI
 * events, boss death rewards and slow-motion.
 */
class BossSystem : SimSystem {

    override val systemName: String = "BossSystem"

    val boss: BossEntity = BossEntity()
    val active: Boolean get() = boss.alive

    /** UI hooks. */
    var onBossSpawn: ((name: String) -> Unit)? = null
    var onBossPhase: ((phase: Int) -> Unit)? = null
    var onBossDefeated: ((boss: BossEntity) -> Unit)? = null
    var onTelegraph: ((x: Float, y: Float, radius: Float, t: Float) -> Unit)? = null

    private var deathHandled = false

    override fun onEnterWorld(engine: GameEngine) {
        deathHandled = false
        boss.onPhaseChange = { p ->
            onBossPhase?.invoke(p)
            engine.eventBus.post(com.towerbound.laststand.core.LevelUpEvent(-1)) // unused; banner uses hook
        }
        boss.onTelegraph = { x, y, r, t -> onTelegraph?.invoke(x, y, r, t) }
    }

    /** Boss roster cycles every 10 waves. */
    fun spawnForWave(wave: Int, engine: GameEngine) {
        val roster = listOf(
            Triple("BOSS_GRAULK", "Graulk the Unmaker", 1f),
            Triple("BOSS_SOVEREIGN", "Sovereign of Ash", 1.15f),
            Triple("BOSS_VOIDMAW", "The Voidmaw", 1.3f)
        )
        val idx = ((wave / 10) - 1).mod(roster.size)
        val (id, name, scale) = roster[idx]
        val cycle = (wave / 10 - 1) / roster.size + 1
        val hpScale = scale * (1f + (cycle - 1) * 0.6f)
        val dmgScale = 1f + (cycle - 1) * 0.25f

        val world = engine.world ?: return
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player
        // spawn opposite the player relative to the tower
        val px = player?.x ?: world.centerX
        val py = player?.y ?: world.centerY
        val dx = world.centerX - px
        val dy = world.centerY - py
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val bx = (world.centerX + dx / d * 520f).coerceIn(120f, world.width - 120f)
        val by = (world.centerY + dy / d * 520f).coerceIn(120f, world.height - 120f)

        boss.spawn(id, name, bx, by, hpScale, dmgScale)
        boss.radius = 64f
        deathHandled = false
        onBossSpawn?.invoke(name)
        GameLog.i("Boss", "spawned $name (wave $wave) at ($bx, $by)")
    }

    override fun update(engine: GameEngine, dt: Float) {
        if (!boss.alive) {
            if (!deathHandled && boss.phase == BossPhase.DYING) {
                deathHandled = true
                // dramatic death: slow-mo + explosion + confetti
                engine.clock.slowMotion(0.25f, 1.4f)
                val fx = engine.system<com.towerbound.laststand.fx.ParticleManager>()
                fx?.emitExplosion(boss.x, boss.y, 220f)
                fx?.emitConfetti(boss.x, boss.y)
                fx?.emitConfetti(boss.x + 120f, boss.y - 60f)
                fx?.emitConfetti(boss.x - 120f, boss.y - 60f)
                onBossDefeated?.invoke(boss)
                engine.eventBus.post(com.towerbound.laststand.core.BossDefeatedEvent(boss.designId, 0))
            }
            return
        }

        val combat = engine.system<com.towerbound.laststand.combat.CombatSystem>() ?: return
        val fx = engine.system<com.towerbound.laststand.fx.ParticleManager>() ?: return
        boss.update(engine, dt, combat, fx)

        // expose the boss as a team-1 target for player shots through the provider
        val prev = engine.targetProvider
        engine.targetProvider = { team ->
            if (team == 0) (prev?.invoke(0) ?: emptyList()) + boss
            else prev?.invoke(team) ?: emptyList()
        }
    }
}
