package com.towerbound.laststand.combat

import com.towerbound.laststand.core.Rng

/**
 * Anything that can deal or receive damage. Team 0 = defenders (player +
 * tower), team 1 = attackers (enemies, bosses).
 */
interface Combatant {
    val x: Float
    val y: Float
    val radius: Float
    val team: Int

    fun stats(): StatBlock

    /**
     * Apply final damage. Returns damage actually taken (post-shield etc.).
     * Implementations must be safe against NaN/negative inputs.
     */
    fun applyDamage(damage: Float, isCrit: Boolean, source: String): Float

    fun isAlive(): Boolean
}

const val TEAM_PLAYER = 0
const val TEAM_ENEMY = 1

/**
 * THE damage formula (design: one consistent formula).
 *
 *   raw      = attacker.finalAttackDamage * multiplier
 *   mitig    = raw * 300 / (300 + defender.armor)      // smooth armor curve
 *   crit     = chance roll, multiplier = attacker.critDamage
 *   final    = mitig * critMultiplier, clamped to [0, 1e9]
 *
 * All inputs are sanitized: NaN/Infinity/negative become 0 — combat can
 * never produce impossible stats.
 */
object DamageSystem {

    data class Result(
        val damage: Float,
        val isCrit: Boolean,
        val rawDamage: Float,
        val armorMitigated: Float
    )

    fun compute(
        attacker: StatBlock,
        defender: StatBlock,
        rng: Rng,
        multiplier: Float = 1f,
        canCrit: Boolean = true
    ): Result {
        var raw = safe(attacker.finalAttackDamage) * safe(multiplier)
        raw = safe(raw)

        val armor = safe(defender.finalArmor)
        val mitigated = raw * (300f / (300f + armor))

        val isCrit = canCrit && rng.chance(attacker.finalCritChance)
        val critMult = if (isCrit) safe(attacker.finalCritDamage, fallback = 1f).coerceAtLeast(1f) else 1f

        var dmg = safe(mitigated * critMult)
        dmg = dmg.coerceIn(0f, 1e9f)

        return Result(
            damage = dmg,
            isCrit = isCrit,
            rawDamage = raw.coerceIn(0f, 1e9f),
            armorMitigated = (raw - mitigated).coerceAtLeast(0f)
        )
    }

    /** Lifesteal amount for a damage instance. */
    fun lifestealFor(attacker: StatBlock, damage: Float): Float =
        safe(damage) * attacker.finalLifesteal

    private fun safe(v: Float, fallback: Float = 0f): Float =
        if (v.isNaN() || v.isInfinite()) fallback else if (v < 0f) 0f else v
}

/* ================================================================== */
/* Status effects                                                      */
/* ================================================================== */

enum class StatusKind(val isDamageOverTime: Boolean) {
    STUN(false),
    SLOW(false),
    BURN(true),
    POISON(true),
    FREEZE(false),
    KNOCKBACK(false)
}

/**
 * A status instance on a target. Supports stacking with a per-kind limit,
 * refresh behavior and interaction rules (see [StatusEffectSystem]).
 */
class StatusInstance(
    val kind: StatusKind,
    var duration: Float,
    val magnitude: Float,      // slow: 0..0.8 fraction; DoT: dps; knockback: impulse
    val tickRate: Float,       // DoT tick interval
    var stacks: Int = 1
) {
    var tickTimer: Float = 0f   // time accumulated toward the next tick
    var ticksFired: Int = 0
    var elapsed: Float = 0f
    var originalDuration: Float = duration
}

/** Target contract for status effects. */
interface StatusTarget {
    fun statsBlock(): StatBlock
    fun onStatusDamage(damage: Float, kind: StatusKind)
    fun onStatusApplied(kind: StatusKind)   // visual/sound hook
    fun onStatusRemoved(kind: StatusKind)
}

/**
 * Dedicated status-effect framework. Duration, tick rate, stacking, stack
 * limits, refresh, immunity (stunResist), resistance (slowResist /
 * elementalResist) and removal all supported. Pure Kotlin, unit tested.
 *
 * Interactions (design: implement interactions between effects):
 *  - FREEZE extinguishes BURN
 *  - BURN halves remaining FREEZE (thaw)
 *  - FREEZE overrides SLOW while active
 */
class StatusEffectSystem {

    companion object {
        const val DEFAULT_STACK_LIMIT = 5
        const val DEFAULT_TICK = 0.5f
    }

    private val statuses = HashMap<StatusTarget, MutableList<StatusInstance>>()

    /** Listener for VFX spawning (set by the fx layer). */
    var onApplyVfx: ((target: StatusTarget, kind: StatusKind) -> Unit)? = null

    fun apply(target: StatusTarget, kind: StatusKind, duration: Float,
              magnitude: Float, stacks: Int = 1, tickRate: Float = DEFAULT_TICK) {
        val stats = target.statsBlock()
        var dur = duration
        var mag = magnitude

        when (kind) {
            StatusKind.STUN -> {
                if (stats.stunResist >= 1f) return
                dur *= (1f - stats.stunResist)
            }
            StatusKind.SLOW -> {
                if (stats.slowResist >= 1f) return
                mag *= (1f - stats.slowResist)
            }
            StatusKind.BURN, StatusKind.POISON -> mag *= (1f - stats.elementalResist)
            else -> {}
        }
        if (dur <= 0f || mag <= 0f) return

        val list = statuses.getOrPut(target) { mutableListOf() }

        // interactions
        if (kind == StatusKind.FREEZE) {
            list.removeAll { it.kind == StatusKind.BURN }
        }
        if (kind == StatusKind.BURN) {
            list.firstOrNull { it.kind == StatusKind.FREEZE }?.let { it.duration *= 0.5f }
        }

        val existing = list.firstOrNull { it.kind == kind }
        if (existing != null) {
            existing.duration = maxOf(existing.duration, dur)   // refresh
            existing.originalDuration = existing.duration
            existing.stacks = (existing.stacks + stacks).coerceAtMost(DEFAULT_STACK_LIMIT)
        } else {
            list.add(StatusInstance(kind, dur, mag, tickRate, stacks.coerceIn(1, DEFAULT_STACK_LIMIT)))
        }
        target.onStatusApplied(kind)
        onApplyVfx?.invoke(target, kind)
    }

    fun remove(target: StatusTarget, kind: StatusKind) {
        val list = statuses[target] ?: return
        val removed = list.removeAll { it.kind == kind }
        if (removed) target.onStatusRemoved(kind)
        if (list.isEmpty()) statuses.remove(target)
    }

    fun clearAll(target: StatusTarget) {
        val list = statuses.remove(target) ?: return
        for (s in list) target.onStatusRemoved(s.kind)
    }

    fun has(target: StatusTarget, kind: StatusKind): Boolean =
        statuses[target]?.any { it.kind == kind } == true

    fun stacksOf(target: StatusTarget, kind: StatusKind): Int =
        statuses[target]?.firstOrNull { it.kind == kind }?.stacks ?: 0

    /** Combined slow factor 0..0.95 (freeze counts as 0.95). */
    fun slowFactor(target: StatusTarget): Float {
        val list = statuses[target] ?: return 0f
        var factor = 0f
        for (s in list) {
            when (s.kind) {
                StatusKind.FREEZE -> return 0.95f
                StatusKind.SLOW -> factor = maxOf(factor, s.magnitude)
                else -> {}
            }
        }
        return factor.coerceIn(0f, 0.95f)
    }

    fun isStunned(target: StatusTarget): Boolean =
        statuses[target]?.any { it.kind == StatusKind.STUN || it.kind == StatusKind.FREEZE } == true

    /** Fixed-step update: durations + DoT ticks. */
    fun update(dt: Float) {
        val emptyKeys = mutableListOf<StatusTarget>()
        for ((target, list) in statuses) {
            val it = list.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.elapsed += dt
                s.duration -= dt
                if (s.kind.isDamageOverTime) {
                    s.tickTimer += dt
                    val allowed = (s.originalDuration / s.tickRate + 1e-4).toInt()
                    while (s.tickTimer >= s.tickRate) {
                        s.tickTimer -= s.tickRate
                        if (s.ticksFired < allowed) {
                            s.ticksFired++
                            val dmg = s.magnitude * s.stacks * s.tickRate
                            target.onStatusDamage(dmg, s.kind)
                        }
                    }
                }
                if (s.duration <= 0f) {
                    it.remove()
                    target.onStatusRemoved(s.kind)
                }
            }
            if (list.isEmpty()) emptyKeys.add(target)
        }
        for (k in emptyKeys) statuses.remove(k)
    }

    fun activeCount(): Int = statuses.values.sumOf { it.size }

    /** Clear all state (world exit). */
    fun reset() = statuses.clear()
}

/* ================================================================== */
/* Buffs / debuffs                                                     */
/* ================================================================== */

/**
 * Temporary stat modifier, separate from status effects (design: BUFF /
 * DEBUFF SYSTEM). Carries source, duration, magnitude and a stacking rule.
 * Effects are closures over the flat/pct modifier aggregates.
 */
class Buff(
    val id: String,
    val source: String,
    val duration: Float,
    val stackRule: StackRule = StackRule.REFRESH,
    val maxStacks: Int = 1,
    val flatEffect: (StatBlock.Modifiers, Int) -> Unit = { _, _ -> },
    val pctEffect: (StatBlock.Modifiers, Int) -> Unit = { _, _ -> }
) {
    enum class StackRule { REFRESH, STACK, IGNORE }
    var stacks: Int = 1
    var remaining: Float = duration
}

/**
 * Manages buffs per StatBlock owner. Buffs write into the StatBlock's flat
 * and pct modifier aggregates; the aggregate is recomputed from scratch on
 * every change (no drift), then buffs expire over time.
 */
class BuffSystem {

    private val owners = HashMap<Any, OwnerState>()

    class OwnerState(val stats: StatBlock) {
        val buffs = mutableListOf<Buff>()
        var dirty = true
    }

    fun add(owner: Any, stats: StatBlock, buff: Buff) {
        val state = owners.getOrPut(owner) { OwnerState(stats) }
        val existing = state.buffs.firstOrNull { it.id == buff.id && it.source == buff.source }
        when {
            existing != null && buff.stackRule == Buff.StackRule.REFRESH -> {
                existing.remaining = buff.duration
                existing.stacks = buff.maxStacks.coerceAtLeast(existing.stacks)
            }
            existing != null && buff.stackRule == Buff.StackRule.STACK -> {
                existing.stacks = (existing.stacks + 1).coerceAtMost(buff.maxStacks)
                existing.remaining = buff.duration
            }
            existing != null -> { /* IGNORE: keep current */ }
            else -> state.buffs.add(buff)
        }
        state.dirty = true
    }

    fun remove(owner: Any, buffId: String) {
        val state = owners[owner] ?: return
        state.buffs.removeAll { it.id == buffId }
        state.dirty = true
    }

    fun clear(owner: Any) {
        val state = owners[owner] ?: return
        state.buffs.clear()
        state.dirty = true
    }

    fun stacksOf(owner: Any, buffId: String): Int =
        owners[owner]?.buffs?.firstOrNull { it.id == buffId }?.stacks ?: 0

    fun activeBuffs(owner: Any): List<Buff> = owners[owner]?.buffs?.toList() ?: emptyList()

    /** Clear all owners (world exit). */
    fun reset() {
        for (state in owners.values) {
            state.stats.flat.reset()
            state.stats.pct.reset()
            state.buffs.clear()
        }
        owners.clear()
    }

    /** Fixed-step update; recomputes modifiers when anything changed/expired. */
    fun update(dt: Float) {
        for ((_, state) in owners) {
            var expired = false
            val it = state.buffs.iterator()
            while (it.hasNext()) {
                val b = it.next()
                b.remaining -= dt
                if (b.remaining <= 0f) { it.remove(); expired = true }
            }
            if (expired) state.dirty = true
            if (state.dirty) recompute(state)
        }
    }

    private fun recompute(state: OwnerState) {
        val f = state.stats.flat
        val p = state.stats.pct
        f.reset(); p.reset()
        for (b in state.buffs) {
            b.flatEffect(f, b.stacks)
            b.pctEffect(p, b.stacks)
        }
        state.dirty = false
    }
}
