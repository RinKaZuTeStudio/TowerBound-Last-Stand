package com.towerbound.laststand.gameplay

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.CombatSystem
import com.towerbound.laststand.combat.Combatant
import com.towerbound.laststand.combat.DamageSystem
import com.towerbound.laststand.combat.StatBlock
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.input.InputState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hero identity beyond stats: melee vs ranged and the visual element of
 * their auto-attacks.
 */
data class HeroProfile(
    val id: String,
    val displayName: String,
    val ranged: Boolean,
    val element: String,   // fx key: frost/poison/magic/impact/fire
    val role: String
)

/** Static hero table — single source of truth for hero templates. */
object Heroes {
    val profiles = mapOf(
        "ARIC" to HeroProfile("ARIC", "Aric", false, "impact", "Balanced Warrior"),
        "KAEL" to HeroProfile("KAEL", "Kael", true, "impact", "Fast Ranged Fighter"),
        "LYRA" to HeroProfile("LYRA", "Lyra", true, "frost", "Magic Support"),
        "DARIUS" to HeroProfile("DARIUS", "Darius", false, "fire", "Heavy Warrior"),
        "NYX" to HeroProfile("NYX", "Nyx", false, "void", "Assassin"),
        "MORVAIN" to HeroProfile("MORVAIN", "Morvain", true, "poison", "Dark Mage"),
        "VOLT" to HeroProfile("VOLT", "Volt", true, "magic", "Lightning Technomancer"),
        "BRONN" to HeroProfile("BRONN", "Bronn", false, "shield", "Defensive Guardian")
    )

    fun profile(id: String): HeroProfile = profiles[id] ?: profiles.getValue("ARIC")
    fun allIds(): List<String> = profiles.keys.toList()
}

/**
 * PlayerController: bridges input -> player entity, runs the auto-attack
 * combat cycle (melee arcs or projectiles), camera follow and HUD refresh.
 */
class PlayerController(val input: InputState) : SimSystem {

    var player: PlayerEntity? = null
        private set

    /** Permanent meta stat multiplier from hero level (set by GameFlow). */
    @Volatile var metaStatScale: Float = 1f

    /** Equipped skin tint [r,g,b] applied to the hero sprite (set by GameFlow). */
    @Volatile var skinTint: FloatArray? = null

    private var attackTimer: Float = 0f
    private var attackWindupActive = false

    /** Attack target for the current swing (for facing + homing shots). */
    private var swingTarget: Combatant? = null

    /** Combat system reference, injected by GameFlow at registration order. */
    private lateinit var combat: CombatSystem

    /** Haptics/audio hooks (attached in later phases). */
    var onAttackFired: ((profile: HeroProfile) -> Unit)? = null
    var onMeleeHit: (() -> Unit)? = null

    override val systemName: String = "PlayerController"

    override fun onEnterWorld(engine: GameEngine) {
        val world = engine.world ?: return
        val heroId = engine.currentHeroId
        val stats = heroStats(heroId)
        // permanent hero-level progression multiplies the base template
        val scale = metaStatScale
        if (scale != 1f) {
            stats.maxHp *= scale
            stats.attackDamage *= scale
            stats.armor *= scale
        }
        val p = PlayerEntity(heroId, stats, world.playerSpawnX, world.playerSpawnY)
        p.facing = -kotlin.math.PI.toFloat() / 2f
        skinTint?.let { p.skinTint = it.copyOf() }
        player = p
        attackTimer = 0.4f
        attackWindupActive = false
        swingTarget = null

        combat = engine.system<CombatSystem>() ?: CombatSystem().also {
            engine.registerSystem(it)
        }

        // attack animation events -> damage
        p.anim.addEvent(2, AnimEvent.ON_ATTACK_IMPACT) // frame 2 of 5 = strike
        p.anim.onEvent { event ->
            if (event == AnimEvent.ON_ATTACK_IMPACT && attackWindupActive) {
                attackWindupActive = false
                resolveAttackImpact(engine, p)
            }
        }
        GameLog.i("Player", "spawned hero=$heroId at (${p.x}, ${p.y})")
    }

    override fun onExitWorld(engine: GameEngine) {
        player = null
    }

    override fun update(engine: GameEngine, dt: Float) {
        val p = player ?: return
        val world = engine.world ?: return

        if (input.consumeDash()) {
            p.tryDash(input.moveX, input.moveY)
        }
        // Ability/ultimate queues are consumed by AbilitySystem.

        p.update(dt, input.moveX, input.moveY, world)

        // auto-attack cycle
        updateAutoAttack(engine, p, dt)

        engine.cameraHook?.invoke(p.x, p.y, dt)
        engine.hudUpdateHook?.invoke()
    }

    private fun updateAutoAttack(engine: GameEngine, p: PlayerEntity, dt: Float) {
        if (!p.alive) return
        if (attackTimer > 0f) attackTimer -= dt

        // finish attack pose when the swing completes
        if (attackWindupActive && p.anim.state == AnimState.ATTACK && p.anim.finished) {
            attackWindupActive = false
        }

        val profile = Heroes.profile(p.heroId)
        val canStart = attackTimer <= 0f && !attackWindupActive && input.autoAttack && p.alive
        if (canStart) {
            val target = findTarget(engine, p, p.stats.finalAttackRange)
            if (target != null) {
                startAttack(p, target)
            }
        }
    }

    private fun startAttack(p: PlayerEntity, target: Combatant) {
        attackWindupActive = true
        attackTimer = 1f / p.stats.finalAttackSpeed
        swingTarget = target
        // face the target
        p.facing = atan2(target.y - p.y, target.x - p.x)
        p.facingLeft = cos(p.facing) < -0.15f
        p.anim.play(AnimState.ATTACK, force = true)
        onAttackFired?.invoke(Heroes.profile(p.heroId))
    }

    /** Called at the ATTACK anim impact frame. */
    private fun resolveAttackImpact(engine: GameEngine, p: PlayerEntity) {
        val profile = Heroes.profile(p.heroId)
        val stats = p.stats

        if (profile.ranged) {
            val target = swingTarget?.takeIf { it.isAlive() } ?: findTarget(engine, p, stats.finalAttackRange + 160f)
            val dirX: Float
            val dirY: Float
            if (target != null) {
                dirX = target.x - p.x
                dirY = target.y - p.y
            } else {
                dirX = cos(p.facing); dirY = sin(p.facing)
            }
            val count = stats.finalProjectileCount
            val spread = if (count > 1) 0.22f else 0f
            val baseAngle = atan2(dirY, dirX)
            for (i in 0 until count) {
                val a = baseAngle + spread * (i - (count - 1) / 2f)
                combat.projectiles.ownerStats = stats
                combat.projectiles.spawn(
                    x = p.x + cos(a) * 40f,
                    y = p.y + sin(a) * 40f - 14f,
                    dirX = cos(a), dirY = sin(a),
                    speed = stats.finalProjectileSpeed,
                    team = p.team,
                    stats = stats,
                    source = "auto_${p.heroId}",
                    pierce = if (p.heroId == "KAEL") 1 else 0,
                    aoe = stats.finalAoeSize,
                    visual = profile.element,
                    homing = null
                )
            }
        } else {
            // melee arc: ±60 degrees from facing within range
            val range = stats.finalAttackRange + 20f
            val targets = enemiesOf(engine, p.team)
            var hits = 0
            for (t in targets) {
                if (!t.isAlive()) continue
                val dx = t.x - p.x
                val dy = t.y - p.y
                val d = sqrt(dx * dx + dy * dy)
                if (d > range + t.radius) continue
                val ang = atan2(dy, dx)
                var diff = ang - p.facing
                while (diff > Math.PI.toFloat()) diff -= 2f * Math.PI.toFloat()
                while (diff < -Math.PI.toFloat()) diff += 2f * Math.PI.toFloat()
                if (abs(diff) > 1.05f) continue

                val result = DamageSystem.compute(stats, t.stats(), rngFor(engine))
                val applied = t.applyDamage(result.damage, result.isCrit, "auto_${p.heroId}")
                if (applied > 0f) {
                    combat.reportHit(t, applied, result.isCrit, "auto_${p.heroId}")
                    // lifesteal
                    val ls = DamageSystem.lifestealFor(stats, applied)
                    if (ls > 0f) p.heal(ls)
                    hits++
                }
            }
            if (hits > 0) {
                onMeleeHit?.invoke()
                // melee swing VFX
                combat.fx?.emitHitSpark(
                    p.x + cos(p.facing) * range * 0.6f,
                    p.y + sin(p.facing) * range * 0.6f,
                    cos(p.facing), sin(p.facing)
                )
            }
        }
        swingTarget = null
    }

    /** Nearest opposing combatant within [range]. */
    private fun findTarget(engine: GameEngine, p: PlayerEntity, range: Float): Combatant? {
        var best: Combatant? = null
        var bestD = Float.MAX_VALUE
        for (t in enemiesOf(engine, p.team)) {
            if (!t.isAlive()) continue
            val dx = t.x - p.x
            val dy = t.y - p.y
            val d = dx * dx + dy * dy
            if (d <= (range + t.radius) * (range + t.radius) && d < bestD) {
                bestD = d
                best = t
            }
        }
        return best
    }

    private fun enemiesOf(engine: GameEngine, team: Int): List<Combatant> =
        engine.targetProvider?.invoke(team) ?: emptyList()

    private fun rngFor(engine: GameEngine) = engine.simRng

    /** Base stat templates per hero (full hero definitions arrive with the hero phase). */
    private fun heroStats(heroId: String): StatBlock {
        val s = StatBlock()
        when (heroId) {
            "ARIC" -> { s.maxHp = 120f; s.moveSpeed = 230f; s.attackDamage = 12f; s.attackSpeed = 1.1f; s.attackRange = 110f; s.armor = 4f }
            "KAEL" -> { s.maxHp = 85f; s.moveSpeed = 265f; s.attackDamage = 9f; s.attackSpeed = 1.6f; s.attackRange = 320f; s.projectileSpeed = 760f }
            "LYRA" -> { s.maxHp = 80f; s.moveSpeed = 240f; s.attackDamage = 10f; s.attackSpeed = 1.0f; s.attackRange = 300f; s.projectileSpeed = 560f; s.aoeSize = 40f }
            "DARIUS" -> { s.maxHp = 160f; s.moveSpeed = 200f; s.attackDamage = 16f; s.attackSpeed = 0.8f; s.attackRange = 120f; s.armor = 8f }
            "NYX" -> { s.maxHp = 75f; s.moveSpeed = 290f; s.attackDamage = 8f; s.attackSpeed = 2.0f; s.attackRange = 95f; s.critChance = 0.15f; s.critDamage = 1.9f }
            "MORVAIN" -> { s.maxHp = 85f; s.moveSpeed = 235f; s.attackDamage = 9f; s.attackSpeed = 1.0f; s.attackRange = 290f; s.projectileSpeed = 520f }
            "VOLT" -> { s.maxHp = 90f; s.moveSpeed = 250f; s.attackDamage = 8f; s.attackSpeed = 1.8f; s.attackRange = 260f; s.projectileSpeed = 820f }
            "BRONN" -> { s.maxHp = 140f; s.moveSpeed = 215f; s.attackDamage = 11f; s.attackSpeed = 0.9f; s.attackRange = 110f; s.armor = 6f }
        }
        return s
    }
}
