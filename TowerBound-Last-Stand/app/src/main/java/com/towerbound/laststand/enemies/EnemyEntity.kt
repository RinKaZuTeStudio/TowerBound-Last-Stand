package com.towerbound.laststand.enemies

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.anim.AnimationController
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.Combatant
import com.towerbound.laststand.combat.DamageSystem
import com.towerbound.laststand.combat.Knockbackable
import com.towerbound.laststand.combat.StatBlock
import com.towerbound.laststand.combat.StatusKind
import com.towerbound.laststand.combat.StatusTarget
import com.towerbound.laststand.combat.TEAM_ENEMY
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.Rng
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AI behavior profile — what makes each archetype distinct (design:
 * "Do not use the same generic enemy for every enemy type").
 */
enum class TargetPriority { TOWER_FIRST, PLAYER_FIRST, CLOSEST, TOWER_ONLY }

enum class AttackStyle { MELEE, RANGED_ARROW, RANGED_BOLT, RANGED_FIRE, SUICIDE }

data class EnemyDefinition(
    val id: String,
    val displayName: String,
    val targetPriority: TargetPriority,
    val attackStyle: AttackStyle,
    val attackRange: Float,
    val preferredRange: Float,      // kiting distance for ranged types
    val baseHp: Float,
    val baseDamage: Float,
    val baseSpeed: Float,
    val baseArmor: Float,
    val attackInterval: Float,
    val radius: Float,
    val visualElement: String,      // fx color key
    val xpValue: Int,
    val goldValue: Int
) {
    val isRanged: Boolean get() = attackStyle != AttackStyle.MELEE && attackStyle != AttackStyle.SUICIDE
}

/**
 * Data-driven enemy table. Wave scaling multiplies HP/damage at spawn time.
 */
object EnemyDefs {

    val GRUNT = EnemyDefinition(
        "GRUNT", "Grunt", TargetPriority.CLOSEST, AttackStyle.MELEE,
        attackRange = 60f, preferredRange = 55f, baseHp = 30f, baseDamage = 8f,
        baseSpeed = 92f, baseArmor = 0f, attackInterval = 1.1f, radius = 30f,
        visualElement = "poison", xpValue = 3, goldValue = 2
    )

    val RUNNER = EnemyDefinition(
        "RUNNER", "Runner", TargetPriority.PLAYER_FIRST, AttackStyle.MELEE,
        attackRange = 50f, preferredRange = 46f, baseHp = 18f, baseDamage = 6f,
        baseSpeed = 175f, baseArmor = 0f, attackInterval = 0.8f, radius = 24f,
        visualElement = "fire", xpValue = 4, goldValue = 2
    )

    val ARCHER = EnemyDefinition(
        "ARCHER", "Archer", TargetPriority.TOWER_FIRST, AttackStyle.RANGED_ARROW,
        attackRange = 330f, preferredRange = 260f, baseHp = 22f, baseDamage = 9f,
        baseSpeed = 80f, baseArmor = 0f, attackInterval = 1.7f, radius = 26f,
        visualElement = "impact", xpValue = 5, goldValue = 3
    )

    val TANK = EnemyDefinition(
        "TANK", "Siegebreaker", TargetPriority.TOWER_ONLY, AttackStyle.MELEE,
        attackRange = 70f, preferredRange = 66f, baseHp = 130f, baseDamage = 16f,
        baseSpeed = 58f, baseArmor = 12f, attackInterval = 1.6f, radius = 42f,
        visualElement = "impact", xpValue = 10, goldValue = 6
    )

    val SWARMER = EnemyDefinition(
        "SWARMER", "Swarmling", TargetPriority.CLOSEST, AttackStyle.MELEE,
        attackRange = 38f, preferredRange = 34f, baseHp = 9f, baseDamage = 4f,
        baseSpeed = 135f, baseArmor = 0f, attackInterval = 0.7f, radius = 18f,
        visualElement = "void", xpValue = 2, goldValue = 1
    )

    val MAGE = EnemyDefinition(
        "MAGE", "Cult Mage", TargetPriority.TOWER_FIRST, AttackStyle.RANGED_FIRE,
        attackRange = 300f, preferredRange = 240f, baseHp = 26f, baseDamage = 11f,
        baseSpeed = 72f, baseArmor = 2f, attackInterval = 2.1f, radius = 27f,
        visualElement = "fire", xpValue = 7, goldValue = 4
    )

    val ASSASSIN = EnemyDefinition(
        "ASSASSIN", "Assassin", TargetPriority.PLAYER_FIRST, AttackStyle.MELEE,
        attackRange = 55f, preferredRange = 50f, baseHp = 34f, baseDamage = 14f,
        baseSpeed = 155f, baseArmor = 4f, attackInterval = 1.0f, radius = 25f,
        visualElement = "void", xpValue = 8, goldValue = 5
    )

    val SIEGE = EnemyDefinition(
        "SIEGE", "Siege Construct", TargetPriority.TOWER_ONLY, AttackStyle.RANGED_BOLT,
        attackRange = 240f, preferredRange = 210f, baseHp = 160f, baseDamage = 26f,
        baseSpeed = 42f, baseArmor = 10f, attackInterval = 2.6f, radius = 40f,
        visualElement = "fire", xpValue = 14, goldValue = 9
    )

    val ELITE = EnemyDefinition(
        "ELITE", "Elite Champion", TargetPriority.CLOSEST, AttackStyle.MELEE,
        attackRange = 75f, preferredRange = 70f, baseHp = 240f, baseDamage = 20f,
        baseSpeed = 95f, baseArmor = 14f, attackInterval = 1.3f, radius = 36f,
        visualElement = "impact", xpValue = 25, goldValue = 16
    )

    /** Boss uses its own system; the shared def powers base stats. */
    val BOSS = EnemyDefinition(
        "BOSS", "Warlord", TargetPriority.CLOSEST, AttackStyle.MELEE,
        attackRange = 95f, preferredRange = 90f, baseHp = 1400f, baseDamage = 30f,
        baseSpeed = 74f, baseArmor = 20f, attackInterval = 1.5f, radius = 58f,
        visualElement = "fire", xpValue = 120, goldValue = 80
    )

    val all: Map<String, EnemyDefinition> = listOf(
        GRUNT, RUNNER, ARCHER, TANK, SWARMER, MAGE, ASSASSIN, SIEGE, ELITE, BOSS
    ).associateBy { it.id }

    fun byId(id: String): EnemyDefinition = all[id] ?: GRUNT
}

/**
 * AI states (design: AI SYSTEM).
 */
enum class AIState { IDLE, SEARCH, CHASE, ATTACK, RETREAT, RECOVER, DEAD }

/**
 * One enemy instance. Pooled — [reset] reinitializes for a new spawn.
 */
class EnemyEntity : Combatant, StatusTarget, Knockbackable {

    override var x: Float = 0f
    override var y: Float = 0f
    override var radius: Float = 30f
    override val team: Int get() = TEAM_ENEMY

    var def: EnemyDefinition = EnemyDefs.GRUNT
        private set

    val stats = StatBlock()

    var hp: Float = 1f
        private set
    var alive: Boolean = false
        private set
    var aiState: AIState = AIState.IDLE
        private set

    val anim = AnimationController(AnimState.IDLE)

    /** Current target reference (tower or player combatants). */
    var target: Combatant? = null

    var attackCooldown: Float = 0f
    var stateTimer: Float = 0f
    var facingLeft: Boolean = false
    var facing: Float = 0f

    /** Knockback velocity decays over time. */
    private var kbX: Float = 0f
    private var kbY: Float = 0f

    /** DoT accumulator. */
    var pendingDotDamage: Float = 0f

    /** Engine reference for cross-system lookups (set by EnemyManager). */
    var engineRef: GameEngine? = null

    /** Visual hit flash. */
    var hitFlashTimer: Float = 0f

    /** Death animation completion -> despawn. */
    var deathTimer: Float = -1f

    /** Rewards fired flag (single death payout per entity). */
    var deathNotified: Boolean = false

    /** Elite flag (scaled stats + gold aura). */
    var isElite: Boolean = false

    /** Crowd-separation force accumulator (reset each frame). */
    var sepX: Float = 0f
    var sepY: Float = 0f

    val isBoss: Boolean get() = def.id == "BOSS"

    /** Ranged projectile spawn callback (wired to ProjectileManager). */
    var rangedAttack: ((enemy: EnemyEntity, target: Combatant) -> Unit)? = null

    fun reset(def: EnemyDefinition, x: Float, y: Float, hpScale: Float, dmgScale: Float, elite: Boolean) {
        this.def = def
        this.x = x
        this.y = y
        this.radius = def.radius
        this.isElite = elite
        stats.copyFrom(StatBlock().apply {
            maxHp = def.baseHp * hpScale
            attackDamage = def.baseDamage * dmgScale
            moveSpeed = def.baseSpeed
            attackRange = def.attackRange
            attackSpeed = 1f / def.attackInterval
            armor = def.baseArmor
        })
        hp = stats.finalMaxHp
        alive = true
        aiState = AIState.SEARCH
        attackCooldown = 0.3f
        stateTimer = 0f
        kbX = 0f; kbY = 0f
        pendingDotDamage = 0f
        hitFlashTimer = 0f
        deathTimer = -1f
        target = null
        deathNotified = false
        anim.play(AnimState.IDLE, force = true)
        anim.addEvent(2, AnimEvent.ON_ATTACK_IMPACT)
    }

    fun maxHp(): Float = stats.finalMaxHp
    fun hpFraction(): Float = (hp / maxHp()).coerceIn(0f, 1f)

    /** Force-dead without death payout (pool reset). */
    fun forceDead() {
        alive = false
        deathNotified = true
        aiState = AIState.DEAD
    }

    override fun stats(): StatBlock = stats
    override fun statsBlock(): StatBlock = stats
    override fun isAlive(): Boolean = alive

    override fun applyDamage(damage: Float, isCrit: Boolean, source: String): Float {
        if (!alive) return 0f
        var d = damage
        if (d.isNaN() || d.isInfinite() || d < 0f) d = 0f
        hp -= d
        hitFlashTimer = 0.12f
        if (hp <= 0f) {
            hp = 0f
            alive = false
            aiState = AIState.DEAD
            deathTimer = 0f
            anim.play(AnimState.DEATH, force = true)
        } else {
            anim.play(AnimState.HIT, force = true)
        }
        return d
    }

    override fun onStatusDamage(damage: Float, kind: StatusKind) {
        if (damage.isNaN() || damage < 0f) return
        pendingDotDamage += damage
    }
    override fun onStatusApplied(kind: StatusKind) {}
    override fun onStatusRemoved(kind: StatusKind) {}

    override fun applyKnockback(ix: Float, iy: Float) {
        kbX += ix
        kbY += iy
    }

    fun consumePendingDot(): Float {
        val v = pendingDotDamage
        pendingDotDamage = 0f
        return v
    }

    /**
     * AI + movement update for one fixed step.
     */
    fun update(
        dt: Float,
        world: com.towerbound.laststand.world.GameWorld,
        statuses: com.towerbound.laststand.combat.StatusEffectSystem,
        player: Combatant?,
        tower: Combatant?,
        neighbors: List<EnemyEntity>,
        rng: Rng
    ) {
        if (!alive) {
            deathTimer += dt
            anim.update(dt)
            return
        }

        if (hitFlashTimer > 0f) hitFlashTimer -= dt
        if (attackCooldown > 0f) attackCooldown -= dt
        stateTimer += dt

        // route DoT
        val dot = consumePendingDot()
        if (dot > 0f) {
            hp -= dot
            if (hp <= 0f) {
                hp = 0f
                alive = false
                aiState = AIState.DEAD
                deathTimer = 0f
                anim.play(AnimState.DEATH, force = true)
                return
            }
        }

        val slow = statuses.slowFactor(this)
        val stunned = statuses.isStunned(this)
        // midnight aggression: global speed multiplier from the environment system
        val aggression = engineRef?.system<com.towerbound.laststand.env.EnvironmentSystem>()?.enemyAggressionMult ?: 1f
        val speed = stats.moveSpeedWithSlow(slow) * aggression

        // knockback decay
        if (abs(kbX) > 1f || abs(kbY) > 1f) {
            x += kbX * dt
            y += kbY * dt
            kbX *= (1f - 8f * dt).coerceIn(0f, 1f)
            kbY *= (1f - 8f * dt).coerceIn(0f, 1f)
        }

        if (stunned) {
            anim.play(AnimState.HIT)
            anim.update(dt)
            return
        }

        // ---- target selection ----
        if (target == null || !target!!.isAlive()) {
            target = selectTarget(player, tower)
            aiState = AIState.SEARCH
        }

        val t = target
        if (t == null) {
            anim.play(AnimState.IDLE)
            anim.update(dt)
            return
        }

        val dx = t.x - x
        val dy = t.y - y
        val dist = sqrt(dx * dx + dy * dy)
        val reach = def.attackRange + t.radius + radius * 0.4f

        // ---- state transitions ----
        when (aiState) {
            AIState.SEARCH, AIState.IDLE -> {
                aiState = AIState.CHASE
            }
            AIState.CHASE -> {
                if (dist <= reach) {
                    aiState = AIState.ATTACK
                    stateTimer = 0f
                } else if (def.isRanged && dist < def.preferredRange * 0.55f && t.team == TEAM_ENEMY_SIDE_PLAYER) {
                    // too close for comfort: back off
                    aiState = AIState.RETREAT
                    stateTimer = 0f
                }
            }
            AIState.ATTACK -> {
                if (dist > reach * 1.25f) {
                    aiState = AIState.CHASE
                } else if (attackCooldown <= 0f) {
                    performAttack(t, rng)
                }
            }
            AIState.RETREAT -> {
                if (dist >= def.preferredRange * 0.9f || stateTimer > 2.2f) {
                    aiState = AIState.CHASE
                }
            }
            AIState.RECOVER -> {
                if (stateTimer > 0.5f) aiState = AIState.CHASE
            }
            AIState.DEAD -> {}
        }

        // ---- movement ----
        var mvx = 0f
        var mvy = 0f
        when (aiState) {
            AIState.CHASE -> {
                if (dist > 1f) {
                    mvx = dx / dist
                    mvy = dy / dist
                }
            }
            AIState.RETREAT -> {
                if (dist > 1f) {
                    mvx = -dx / dist
                    mvy = -dy / dist
                }
            }
            else -> {}
        }

        // crowd separation: push away from overlapping neighbors
        var sx = 0f
        var sy = 0f
        for (n in neighbors) {
            if (n === this || !n.alive) continue
            val ndx = x - n.x
            val ndy = y - n.y
            val nd2 = ndx * ndx + ndy * ndy
            val minD = (radius + n.radius) * 0.9f
            if (nd2 < minD * minD && nd2 > 0.001f) {
                val nd = sqrt(nd2)
                sx += ndx / nd * (1f - nd / minD)
                sy += ndy / nd * (1f - nd / minD)
            }
        }
        sepX = sx
        sepY = sy
        mvx += sx * 0.85f
        mvy += sy * 0.85f

        // normalize movement
        val mlen = sqrt(mvx * mvx + mvy * mvy)
        if (mlen > 0.01f) {
            val dirx = mvx / mlen
            val diry = mvy / mlen
            facing = atan2(diry, dirx)
            facingLeft = cos(facing) < -0.15f
            val nx = x + dirx * speed * dt
            val ny = y + diry * speed * dt
            val resolved = world.resolveCollision(nx, ny, radius)
            x = resolved[0]
            y = resolved[1]
            anim.play(AnimState.WALK)
        } else {
            anim.play(AnimState.IDLE)
        }
        anim.update(dt)
    }

    private fun performAttack(t: Combatant, rng: Rng) {
        attackCooldown = def.attackInterval * (1f / stats.finalAttackSpeed)
        facing = atan2(t.y - y, t.x - x)
        facingLeft = cos(facing) < -0.15f
        anim.play(AnimState.ATTACK, force = true)

        when (def.attackStyle) {
            AttackStyle.MELEE -> {
                // damage lands on impact frame via anim event; simplest correct
                // approach: resolve immediately at impact frame through callback
                pendingMeleeTarget = t
            }
            AttackStyle.SUICIDE -> {
                // explode on contact
                pendingMeleeTarget = t
            }
            else -> {
                rangedAttack?.invoke(this, t)
            }
        }
    }

    /** Melee damage resolution hook consumed by EnemyManager at impact frame. */
    var pendingMeleeTarget: Combatant? = null
        private set

    fun consumeMeleeTarget(): Combatant? {
        val t = pendingMeleeTarget
        pendingMeleeTarget = null
        return t
    }

    private fun selectTarget(player: Combatant?, tower: Combatant?): Combatant? {
        val playerAlive = player?.isAlive() == true
        val towerAlive = tower?.isAlive() == true
        return when (def.targetPriority) {
            TargetPriority.TOWER_ONLY -> if (towerAlive) tower else null
            TargetPriority.TOWER_FIRST -> if (towerAlive) tower else player
            TargetPriority.PLAYER_FIRST -> if (playerAlive) player else tower
            TargetPriority.CLOSEST -> {
                if (playerAlive && towerAlive) {
                    val dP = sqrt((player!!.x - x).pow2() + (player.y - y).pow2())
                    val dT = sqrt((tower!!.x - x).pow2() + (tower.y - y).pow2())
                    if (dP < dT * 0.8f) player else tower
                } else if (playerAlive) player else tower
            }
        }
    }

    private fun Float.pow2(): Float = this * this

    companion object {
        const val TEAM_ENEMY_SIDE_PLAYER = 0 // player/tower team id
    }
}
