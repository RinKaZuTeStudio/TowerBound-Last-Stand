package com.towerbound.laststand.combat

/**
 * Complete stat block for any combatant (hero, enemy, boss, tower).
 * Base values plus run-time modifiers (roguelite upgrades, buffs).
 *
 * Final value = (base + flatBonus) * (1 + pctBonus) clamped to sane ranges —
 * one consistent formula for every stat (design: one damage formula).
 */
class StatBlock {

    // ---- base values ----
    var maxHp: Float = 100f
    var moveSpeed: Float = 240f
    var attackDamage: Float = 10f
    var attackSpeed: Float = 1.1f        // attacks per second
    var attackRange: Float = 120f
    var projectileSpeed: Float = 640f
    var projectileCount: Int = 1
    var armor: Float = 0f                // flat reduction, see DamageSystem
    var critChance: Float = 0.05f        // 0..1
    var critDamage: Float = 1.5f         // multiplier on crit
    var lifesteal: Float = 0f            // 0..1 fraction of damage healed
    var cooldownReduction: Float = 0f    // 0..0.6
    var aoeSize: Float = 0f              // bonus AoE radius
    var knockback: Float = 0f            // impulse applied on hit
    var hpRegen: Float = 0f              // hp per second

    // ---- resistances (0..1) ----
    var slowResist: Float = 0f
    var stunResist: Float = 0f
    var elementalResist: Float = 0f      // burn/poison/freeze damage reduction

    // ---- run-time modifiers (buffs + upgrades) ----
    val flat = Modifiers()
    val pct = Modifiers()

    class Modifiers {
        var maxHp = 0f
        var moveSpeed = 0f
        var attackDamage = 0f
        var attackSpeed = 0f
        var attackRange = 0f
        var armor = 0f
        var critChance = 0f
        var critDamage = 0f
        var lifesteal = 0f
        var cooldownReduction = 0f
        var aoeSize = 0f
        var knockback = 0f
        var projectileCount = 0
        var hpRegen = 0f

        fun reset() {
            maxHp = 0f; moveSpeed = 0f; attackDamage = 0f; attackSpeed = 0f
            attackRange = 0f; armor = 0f; critChance = 0f; critDamage = 0f
            lifesteal = 0f; cooldownReduction = 0f; aoeSize = 0f; knockback = 0f
            projectileCount = 0; hpRegen = 0f
        }
    }

    fun compute(value: Float, flatBonus: Float, pctBonus: Float): Float {
        val out = (value + flatBonus) * (1f + pctBonus)
        if (out.isNaN() || out.isInfinite()) return 0f
        return out
    }

    val finalMaxHp: Float get() = compute(maxHp, flat.maxHp, pct.maxHp).coerceAtLeast(1f)
    val finalMoveSpeed: Float get() = compute(moveSpeed, flat.moveSpeed, pct.moveSpeed).coerceIn(0f, 900f)
    val finalAttackDamage: Float get() = compute(attackDamage, flat.attackDamage, pct.attackDamage).coerceAtLeast(0f)
    val finalAttackSpeed: Float get() = compute(attackSpeed, flat.attackSpeed, pct.attackSpeed).coerceIn(0.05f, 12f)
    val finalAttackRange: Float get() = compute(attackRange, flat.attackRange, pct.attackRange).coerceIn(20f, 800f)
    val finalProjectileSpeed: Float get() = projectileSpeed.coerceIn(80f, 2000f)
    val finalProjectileCount: Int get() = (projectileCount + flat.projectileCount).coerceIn(1, 24)
    val finalArmor: Float get() = compute(armor, flat.armor, pct.armor).coerceIn(0f, 400f)
    val finalCritChance: Float get() = compute(critChance, flat.critChance, pct.critChance).coerceIn(0f, 1f)
    val finalCritDamage: Float get() = compute(critDamage, flat.critDamage, pct.critDamage).coerceIn(1f, 8f)
    val finalLifesteal: Float get() = compute(lifesteal, flat.lifesteal, pct.lifesteal).coerceIn(0f, 1f)
    val finalCdr: Float get() = compute(cooldownReduction, flat.cooldownReduction, pct.cooldownReduction).coerceIn(0f, 0.6f)
    val finalAoeSize: Float get() = compute(aoeSize, flat.aoeSize, pct.aoeSize).coerceIn(0f, 400f)
    val finalKnockback: Float get() = compute(knockback, flat.knockback, pct.knockback).coerceIn(0f, 600f)
    val finalHpRegen: Float get() = compute(hpRegen, flat.hpRegen, pct.hpRegen).coerceIn(0f, 100f)

    /** Effective move speed including slow status factor. */
    fun moveSpeedWithSlow(slowFactor: Float): Float =
        (finalMoveSpeed * (1f - slowFactor.coerceIn(0f, 0.95f))).coerceIn(0f, 900f)

    fun copyFrom(other: StatBlock) {
        maxHp = other.maxHp; moveSpeed = other.moveSpeed
        attackDamage = other.attackDamage; attackSpeed = other.attackSpeed
        attackRange = other.attackRange; projectileSpeed = other.projectileSpeed
        projectileCount = other.projectileCount; armor = other.armor
        critChance = other.critChance; critDamage = other.critDamage
        lifesteal = other.lifesteal; cooldownReduction = other.cooldownReduction
        aoeSize = other.aoeSize; knockback = other.knockback
        hpRegen = other.hpRegen
        slowResist = other.slowResist; stunResist = other.stunResist; elementalResist = other.elementalResist
    }

    fun clone(): StatBlock = StatBlock().also { it.copyFrom(this) }
}
