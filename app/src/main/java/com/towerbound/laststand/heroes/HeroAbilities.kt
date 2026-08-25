package com.towerbound.laststand.heroes

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.Buff
import com.towerbound.laststand.combat.CombatSystem
import com.towerbound.laststand.combat.Combatant
import com.towerbound.laststand.combat.DamageSystem
import com.towerbound.laststand.combat.StatusKind
import com.towerbound.laststand.combat.StatBlock
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.gameplay.PlayerEntity
import com.towerbound.laststand.input.InputState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ability descriptor: cooldown, cast behavior and VFX identity.
 * Every hero has one Active + one Ultimate, each with distinct mechanics
 * (design: HERO SYSTEM / ABILITIES).
 */
class Ability(
    val id: String,
    val name: String,
    val isUltimate: Boolean,
    val baseCooldown: Float,
    val requiresTarget: Boolean = false,
    val cast: (ctx: CastContext) -> Unit
) {
    class CastContext(
        val engine: GameEngine,
        val player: PlayerEntity,
        val combat: CombatSystem,
        val fx: com.towerbound.laststand.fx.ParticleManager,
        val target: Combatant?,
        val rng: Rng
    )
}

/**
 * All 8 hero kits. Behaviors genuinely differ:
 *  ARIC    - Shield Slam (AoE stun) / Battle Cry (buff + heal)
 *  KAEL    - Piercing Shot (line nuke) / Rain of Arrows (zone barrage)
 *  LYRA    - Frost Nova (freeze AoE) / Healing Aura (heal + regen)
 *  DARIUS  - Whirlwind (spin DoT) / Colossus Stomp (leap slam)
 *  NYX     - Blade Dash (dash strike) / Execution (nuke low-HP target)
 *  MORVAIN - Poison Nova (poison AoE) / Summon Shadow (ally Shade)
 *  VOLT    - Chain Lightning (chain nuke) / Thunderstorm (random strikes)
 *  BRONN   - Shield Wall (shield + taunt aura) / Guardian Protocol (tower buff)
 */
object HeroAbilities {

    private fun enemiesOf(ctx: Ability.CastContext): List<Combatant> =
        ctx.engine.targetProvider?.invoke(0)?.filter { it.isAlive() } ?: emptyList()

    private fun nearestEnemy(ctx: Ability.CastContext, range: Float): Combatant? {
        var best: Combatant? = null
        var bestD = Float.MAX_VALUE
        for (t in enemiesOf(ctx)) {
            val dx = t.x - ctx.player.x
            val dy = t.y - ctx.player.y
            val d = dx * dx + dy * dy
            if (d <= range * range && d < bestD) { bestD = d; best = t }
        }
        return best
    }

    /* ---------------- ARIC ---------------- */

    val ARIC_ACTIVE = Ability("shield_slam", "Shield Slam", false, 7f, requiresTarget = false) { ctx ->
        val p = ctx.player
        val radius = 170f + p.stats.finalAoeSize
        ctx.fx.emitShield(p.x, p.y, 20)
        for (t in enemiesOf(ctx)) {
            val dx = t.x - p.x; val dy = t.y - p.y
            if (dx * dx + dy * dy <= radius * radius) {
                val r = DamageSystem.compute(p.stats, t.stats(), ctx.rng, 1.9f)
                val applied = t.applyDamage(r.damage, r.isCrit, "shield_slam")
                ctx.combat.reportHit(t, applied, r.isCrit, "shield_slam")
                ctx.combat.statuses.apply(t as? com.towerbound.laststand.combat.StatusTarget ?: continue, StatusKind.STUN, 1.2f, 1f)
                (t as? com.towerbound.laststand.combat.Knockbackable)?.applyKnockback(dx, dy)
            }
        }
        ctx.engine.clock.hitStop(0.05f)
    }

    val ARIC_ULT = Ability("battle_cry", "Battle Cry", true, 32f) { ctx ->
        val p = ctx.player
        ctx.fx.emitMagic(p.x, p.y, 24, "impact")
        ctx.combat.buffs.add(p, p.stats, Buff("battle_cry", "aric", 8f, Buff.StackRule.REFRESH,
            pctEffect = { m, _ -> m.attackDamage += 0.35f; m.armor += 0.3f }))
        p.heal(p.maxHp() * 0.25f)
        // shockwave push
        for (t in enemiesOf(ctx)) {
            val dx = t.x - p.x; val dy = t.y - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < 260f) {
                (t as? com.towerbound.laststand.combat.Knockbackable)?.applyKnockback(dx / d * 300f, dy / d * 300f)
            }
        }
        ctx.engine.clock.slowMotion(0.55f, 0.6f)
    }

    /* ---------------- KAEL ---------------- */

    val KAEL_ACTIVE = Ability("piercing_shot", "Piercing Shot", false, 6f, requiresTarget = true) { ctx ->
        val p = ctx.player
        val target = ctx.target ?: nearestEnemy(ctx, 900f) ?: return@Ability
        val dx = target.x - p.x; val dy = target.y - p.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f) return@Ability
        ctx.combat.projectiles.ownerStats = p.stats
        ctx.combat.projectiles.spawn(
            p.x + dx / d * 44f, p.y + dy / d * 44f - 12f,
            dx / d, dy / d,
            speed = 1100f, team = 0, stats = p.stats,
            source = "piercing_shot", pierce = 24, aoe = 30f + p.stats.finalAoeSize,
            visual = "impact", radius = 16f, damageMult = 2.4f
        )
    }

    val KAEL_ULT = Ability("rain_of_arrows", "Rain of Arrows", true, 34f) { ctx ->
        val p = ctx.player
        val targets = enemiesOf(ctx).sortedBy { -it.x } // prefer clustered by proximity to player
        val cluster = targets.sortedBy {
            val dx = it.x - p.x; val dy = it.y - p.y
            dx * dx + dy * dy
        }.take(4)
        val anchors = if (cluster.isNotEmpty()) cluster else listOf(p)
        val rng = ctx.rng
        // 14 volleys over ~1.4s via telegraphed projectile spam
        repeat(18) { i ->
            val anchor = anchors[i % anchors.size]
            val ax = anchor.x + rng.range(-130f, 130f)
            val ay = anchor.y + rng.range(-130f, 130f)
            ctx.combat.projectiles.ownerStats = p.stats
            ctx.combat.projectiles.spawn(
                ax, ay - 600f, 0f, 1f,
                speed = 900f, team = 0, stats = p.stats,
                source = "rain_of_arrows", pierce = 2, aoe = 60f + p.stats.finalAoeSize,
                visual = "impact", radius = 11f, damageMult = 0.75f
            )
        }
    }

    /* ---------------- LYRA ---------------- */

    val LYRA_ACTIVE = Ability("frost_nova", "Frost Nova", false, 8f) { ctx ->
        val p = ctx.player
        val radius = 210f + p.stats.finalAoeSize
        ctx.fx.emitFrost(p.x, p.y, 26)
        for (t in enemiesOf(ctx)) {
            val dx = t.x - p.x; val dy = t.y - p.y
            if (dx * dx + dy * dy <= radius * radius) {
                val r = DamageSystem.compute(p.stats, t.stats(), ctx.rng, 1.1f)
                val applied = t.applyDamage(r.damage, r.isCrit, "frost_nova")
                ctx.combat.reportHit(t, applied, r.isCrit, "frost_nova")
                ctx.combat.statuses.apply(t as? com.towerbound.laststand.combat.StatusTarget ?: continue, StatusKind.FREEZE, 1.6f, 1f)
                ctx.combat.statuses.apply(t as? com.towerbound.laststand.combat.StatusTarget ?: continue, StatusKind.SLOW, 3f, 0.5f)
            }
        }
        ctx.engine.clock.hitStop(0.04f)
    }

    val LYRA_ULT = Ability("healing_aura", "Healing Aura", true, 36f) { ctx ->
        val p = ctx.player
        ctx.fx.emitHeal(p.x, p.y, 30)
        p.heal(p.maxHp() * 0.55f)
        ctx.combat.buffs.add(p, p.stats, Buff("healing_aura", "lyra", 10f,
            flatEffect = { m, _ -> m.hpRegen += 8f },
            pctEffect = { m, _ -> m.moveSpeed += 0.15f }))
        // also heals the tower
        val tower = ctx.engine.system<com.towerbound.laststand.tower.TowerSystem>()?.tower
        tower?.heal(tower.maxHp() * 0.18f)
    }

    /* ---------------- DARIUS ---------------- */

    val DARIUS_ACTIVE = Ability("whirlwind", "Whirlwind", false, 7f) { ctx ->
        val p = ctx.player
        // 5 rapid spin ticks
        repeat(5) {
            val radius = 150f + p.stats.finalAoeSize
            ctx.fx.emitImpact(p.x, p.y, 6, "impact")
            for (t in enemiesOf(ctx)) {
                val dx = t.x - p.x; val dy = t.y - p.y
                if (dx * dx + dy * dy <= radius * radius) {
                    val r = DamageSystem.compute(p.stats, t.stats(), ctx.rng, 0.65f)
                    val applied = t.applyDamage(r.damage, r.isCrit, "whirlwind")
                    ctx.combat.reportHit(t, applied, r.isCrit, "whirlwind")
                }
            }
        }
    }

    val DARIUS_ULT = Ability("colossus_stomp", "Colossus Stomp", true, 38f) { ctx ->
        val p = ctx.player
        val radius = 300f + p.stats.finalAoeSize
        ctx.engine.clock.hitStop(0.12f)
        ctx.engine.clock.slowMotion(0.4f, 0.5f)
        ctx.fx.emitExplosion(p.x, p.y, radius * 0.5f)
        for (t in enemiesOf(ctx)) {
            val dx = t.x - p.x; val dy = t.y - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d <= radius) {
                val falloff = 1f - (d / radius) * 0.5f
                val r = DamageSystem.compute(p.stats, t.stats(), ctx.rng, 3.2f * falloff)
                val applied = t.applyDamage(r.damage, r.isCrit, "colossus_stomp")
                ctx.combat.reportHit(t, applied, r.isCrit, "colossus_stomp")
                ctx.combat.statuses.apply(t as? com.towerbound.laststand.combat.StatusTarget ?: continue, StatusKind.STUN, 1.8f, 1f)
                if (d > 1f) (t as? com.towerbound.laststand.combat.Knockbackable)?.applyKnockback(dx / d * 420f, dy / d * 420f)
            }
        }
    }

    /* ---------------- NYX ---------------- */

    val NYX_ACTIVE = Ability("blade_dash", "Blade Dash", false, 5f) { ctx ->
        val p = ctx.player
        // dash forward dealing damage along the path
        val dx = cos(p.facing) * 340f
        val dy = sin(p.facing) * 340f
        p.tryDash(cos(p.facing), sin(p.facing))
        ctx.fx.emitMagic(p.x + dx * 0.5f, p.y + dy * 0.5f, 16, "void")
        for (t in enemiesOf(ctx)) {
            // damage enemies near the dash line (approx: near either endpoint or midpoint)
            for (sample in listOf(0.25f, 0.5f, 0.75f)) {
                val sx = p.x + dx * sample
                val sy = p.y + dy * sample
                val ex = t.x - sx; val ey = t.y - sy
                if (ex * ex + ey * ey <= 90f * 90f) {
                    val r = DamageSystem.compute(p.stats, t.stats(), ctx.rng, 1.6f)
                    val applied = t.applyDamage(r.damage, r.isCrit, "blade_dash")
                    ctx.combat.reportHit(t, applied, r.isCrit, "blade_dash")
                    break
                }
            }
        }
    }

    val NYX_ULT = Ability("execution", "Execution", true, 30f, requiresTarget = true) { ctx ->
        val p = ctx.player
        val target = ctx.target ?: nearestEnemy(ctx, 400f) ?: return@Ability
        // bonus damage vs low HP: execute threshold
        val hpFrac = if (target is com.towerbound.laststand.enemies.EnemyEntity) target.hpFraction() else 1f
        val mult = if (hpFrac < 0.35f) 6f else 2.8f
        val r = DamageSystem.compute(p.stats, target.stats(), ctx.rng, mult, canCrit = true)
        val applied = target.applyDamage(r.damage, r.isCrit, "execution")
        ctx.combat.reportHit(target, applied, r.isCrit, "execution")
        ctx.fx.emitMagic(target.x, target.y, 22, "void")
        ctx.engine.clock.hitStop(0.1f)
        ctx.engine.clock.slowMotion(0.45f, 0.4f)
        if (applied > 0) p.heal(applied * 0.3f)
    }

    /* ---------------- MORVAIN ---------------- */

    val MORVAIN_ACTIVE = Ability("poison_nova", "Poison Nova", false, 7.5f) { ctx ->
        val p = ctx.player
        val radius = 230f + p.stats.finalAoeSize
        ctx.fx.emitPoison(p.x, p.y, 26)
        for (t in enemiesOf(ctx)) {
            val dx = t.x - p.x; val dy = t.y - p.y
            if (dx * dx + dy * dy <= radius * radius) {
                val st = t as? com.towerbound.laststand.combat.StatusTarget ?: continue
                ctx.combat.statuses.apply(st, StatusKind.POISON, 5f, p.stats.finalAttackDamage * 0.22f, stacks = 3)
                val r = DamageSystem.compute(p.stats, t.stats(), ctx.rng, 0.7f)
                val applied = t.applyDamage(r.damage, r.isCrit, "poison_nova")
                ctx.combat.reportHit(t, applied, r.isCrit, "poison_nova")
            }
        }
    }

    val MORVAIN_ULT = Ability("summon_shadow", "Summon Shadow", true, 40f) { ctx ->
        val p = ctx.player
        val shade = ctx.engine.system<com.towerbound.laststand.heroes.ShadowSystem>()
        shade?.summon(p.x, p.y, p.stats)
        ctx.fx.emitMagic(p.x, p.y, 24, "void")
    }

    /* ---------------- VOLT ---------------- */

    val VOLT_ACTIVE = Ability("chain_lightning", "Chain Lightning", false, 6f, requiresTarget = true) { ctx ->
        val p = ctx.player
        var current = ctx.target ?: nearestEnemy(ctx, 420f) ?: return@Ability
        val hit = mutableSetOf<Combatant>()
        ctx.fx.emitLightning(p.x, p.y, 10)
        var damageMult = 1.8f
        repeat(5) {
            if (current !in hit) {
                hit.add(current)
                val r = DamageSystem.compute(p.stats, current.stats(), ctx.rng, damageMult)
                val applied = current.applyDamage(r.damage, r.isCrit, "chain_lightning")
                ctx.combat.reportHit(current, applied, r.isCrit, "chain_lightning")
                ctx.fx.emitLightning(current.x, current.y, 8)
                damageMult *= 0.75f
                // hop to nearest unhit
                var next: Combatant? = null
                var bestD = 360f * 360f
                for (t in enemiesOf(ctx)) {
                    if (t in hit || !t.isAlive()) continue
                    val dx = t.x - current.x; val dy = t.y - current.y
                    val d = dx * dx + dy * dy
                    if (d < bestD) { bestD = d; next = t }
                }
                current = next ?: return@repeat
            }
        }
    }

    val VOLT_ULT = Ability("thunderstorm", "Thunderstorm", true, 36f) { ctx ->
        val p = ctx.player
        val enemies = enemiesOf(ctx)
        val strikes = 16.coerceAtMost(enemies.size * 2 + 4)
        repeat(strikes) { i ->
            val t = if (enemies.isEmpty()) null else enemies[i % enemies.size]
            val tx = t?.x ?: (p.x + ctx.rng.range(-350f, 350f))
            val ty = t?.y ?: (p.y + ctx.rng.range(-350f, 350f))
            ctx.combat.projectiles.ownerStats = p.stats
            ctx.combat.projectiles.spawn(
                tx, ty - 700f, 0f, 1f, speed = 1400f, team = 0,
                stats = p.stats, source = "thunderstorm",
                aoe = 70f + p.stats.finalAoeSize, visual = "magic",
                radius = 9f, damageMult = 1.3f
            )
        }
        ctx.engine.clock.slowMotion(0.6f, 0.4f)
    }

    /* ---------------- BRONN ---------------- */

    val BRONN_ACTIVE = Ability("shield_wall", "Shield Wall", false, 9f) { ctx ->
        val p = ctx.player
        ctx.combat.buffs.add(p, p.stats, Buff("shield_wall", "bronn", 6f,
            pctEffect = { m, _ -> m.armor += 0.8f }))
        ctx.fx.emitShield(p.x, p.y, 24)
        // taunt: nearby enemies retarget the player
        val mgr = ctx.engine.system<com.towerbound.laststand.enemies.EnemyManager>()
        mgr?.active?.forEach { e ->
            val dx = e.x - p.x; val dy = e.y - p.y
            if (dx * dx + dy * dy < 300f * 300f) e.target = p
        }
    }

    val BRONN_ULT = Ability("guardian_protocol", "Guardian Protocol", true, 42f) { ctx ->
        val towerSystem = ctx.engine.system<com.towerbound.laststand.tower.TowerSystem>()
        val tower = towerSystem?.tower
        if (tower != null) {
            tower.towerMaxShield += 260f
            tower.restoreShield(260f)
            tower.heal(tower.maxHp() * 0.15f)
        }
        ctx.combat.buffs.add(ctx.player, ctx.player.stats, Buff("guardian_self", "bronn", 10f,
            pctEffect = { m, _ -> m.armor += 0.5f; m.attackDamage += 0.2f }))
        ctx.fx.emitShield(ctx.player.x, ctx.player.y, 30)
        tower?.let { ctx.fx.emitTowerRepair(it.x, it.y) }
    }

    /** Kit lookup: heroId -> (active, ultimate). */
    val kits = mapOf(
        "ARIC" to (ARIC_ACTIVE to ARIC_ULT),
        "KAEL" to (KAEL_ACTIVE to KAEL_ULT),
        "LYRA" to (LYRA_ACTIVE to LYRA_ULT),
        "DARIUS" to (DARIUS_ACTIVE to DARIUS_ULT),
        "NYX" to (NYX_ACTIVE to NYX_ULT),
        "MORVAIN" to (MORVAIN_ACTIVE to MORVAIN_ULT),
        "VOLT" to (VOLT_ACTIVE to VOLT_ULT),
        "BRONN" to (BRONN_ACTIVE to BRONN_ULT)
    )

    fun kit(heroId: String): Pair<Ability, Ability> = kits[heroId] ?: kits.getValue("ARIC")
}

/**
 * AbilitySystem: consumes queued input, tracks cooldowns, casts hero kit
 * abilities with VFX/SFX/game-feel hooks.
 */
class AbilitySystem : SimSystem {

    override val systemName: String = "AbilitySystem"

    var activeCooldown: Float = 0f
        private set
    var ultimateCooldown: Float = 0f
        private set
    var activeAbility: Ability? = null
        private set
    var ultimateAbility: Ability? = null
        private set

    /** UI hooks for cooldown sweeps + cast feedback. */
    var onAbilityCast: ((ability: Ability) -> Unit)? = null
    var onCastFailed: ((ability: Ability) -> Unit)? = null

    /** Cooldown readouts for HUD buttons. */
    fun activeCooldownFraction(): Float {
        val a = activeAbility ?: return 0f
        return (activeCooldown / effectiveCooldown(a)).coerceIn(0f, 1f)
    }
    fun ultimateCooldownFraction(): Float {
        val u = ultimateAbility ?: return 0f
        return (ultimateCooldown / effectiveCooldown(u)).coerceIn(0f, 1f)
    }

    private fun effectiveCooldown(a: Ability): Float {
        val player = playerRef ?: return a.baseCooldown
        return a.baseCooldown * (1f - player.stats.finalCdr)
    }

    private var playerRef: PlayerEntity? = null
    private var combatRef: CombatSystem? = null
    private var fxRef: com.towerbound.laststand.fx.ParticleManager? = null

    override fun onEnterWorld(engine: GameEngine) {
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player
        playerRef = player
        val (active, ult) = HeroAbilities.kit(engine.currentHeroId)
        activeAbility = active
        ultimateAbility = ult
        activeCooldown = 1.5f   // small opening delay
        ultimateCooldown = 6f
    }

    override fun update(engine: GameEngine, dt: Float) {
        if (playerRef == null) {
            playerRef = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player
            if (playerRef == null) return
            val (a, u) = HeroAbilities.kit(engine.currentHeroId)
            activeAbility = a; ultimateAbility = u
        }
        combatRef = combatRef ?: engine.system<CombatSystem>()
        fxRef = fxRef ?: engine.system<com.towerbound.laststand.fx.ParticleManager>()

        if (activeCooldown > 0f) activeCooldown -= dt
        if (ultimateCooldown > 0f) ultimateCooldown -= dt

        val player = playerRef ?: return
        if (!player.alive) return

        // consume queued inputs
        if (engine.input.consumeAbility()) tryCast(activeAbility, engine, player)
        if (engine.input.consumeUltimate()) tryCast(ultimateAbility, engine, player)
    }

    private fun tryCast(ability: Ability?, engine: GameEngine, player: PlayerEntity) {
        if (ability == null) return
        val cd = if (ability.isUltimate) ultimateCooldown else activeCooldown
        if (cd > 0f) {
            onCastFailed?.invoke(ability)
            return
        }
        val combat = combatRef ?: return
        val fx = fxRef ?: return

        // target acquisition when required
        var target: Combatant? = null
        if (ability.requiresTarget) {
            target = engine.targetProvider?.invoke(0)
                ?.filter { it.isAlive() }
                ?.minByOrNull {
                    val dx = it.x - player.x; val dy = it.y - player.y
                    dx * dx + dy * dy
                }
        }

        val ctx = Ability.CastContext(engine, player, combat, fx, target, engine.simRng)
        player.anim.play(AnimState.CAST, force = true)

        try {
            ability.cast(ctx)
        } catch (t: Throwable) {
            GameLog.e("Ability", "cast failed for ${ability.id}", t)
        }

        if (ability.isUltimate) {
            ultimateCooldown = effectiveCooldown(ability)
            engine.clock.slowMotion(0.7f, 0.25f) // brief ultimate cinematic beat
        } else {
            activeCooldown = effectiveCooldown(ability)
        }
        engine.eventBus.post(com.towerbound.laststand.core.AbilityUsedEvent(player.heroId, ability.id, ability.isUltimate))
        onAbilityCast?.invoke(ability)
    }

    override fun onExitWorld(engine: GameEngine) {
        activeCooldown = 0f
        ultimateCooldown = 0f
    }
}
