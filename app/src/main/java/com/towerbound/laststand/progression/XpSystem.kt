package com.towerbound.laststand.progression

import com.towerbound.laststand.core.Rng

/**
 * Roguelite in-run upgrade definitions (design: ROGUELITE LEVEL SYSTEM).
 * Every card modifies the player StatBlock via flat/pct aggregates.
 */
data class UpgradeDef(
    val id: String,
    val name: String,
    val description: String,
    val rarity: com.towerbound.laststand.ui.Rarity,
    val maxStacks: Int,
    val effect: (stats: com.towerbound.laststand.combat.StatBlock) -> Unit,
    val symbol: com.towerbound.laststand.assets.SymbolPainter.Symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.STAR,
    /** Synergy tag this upgrade contributes to. */
    val synergyTag: String? = null
)

/**
 * All in-run upgrades. Data-driven; [UpgradeSystem] samples 3 per level-up
 * with anti-duplication and rarity weighting.
 */
object UpgradePool {

    val ATK_PLUS = UpgradeDef("atk_plus", "Sharpened Steel", "+12% attack damage", com.towerbound.laststand.ui.Rarity.COMMON, 8, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.SWORD, effect = {
        it.pct.attackDamage += 0.12f
})
    val AS_PLUS = UpgradeDef("as_plus", "Quickened Grip", "+10% attack speed", com.towerbound.laststand.ui.Rarity.COMMON, 8, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.SWIFT, effect = {
        it.pct.attackSpeed += 0.10f
})
    val MS_PLUS = UpgradeDef("ms_plus", "Fleet Boots", "+10% move speed", com.towerbound.laststand.ui.Rarity.COMMON, 6, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.BOOT, effect = {
        it.pct.moveSpeed += 0.10f
})
    val CRIT_CHANCE = UpgradeDef("crit_chance", "Keen Eye", "+6% crit chance", com.towerbound.laststand.ui.Rarity.UNCOMMON, 6, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.CROSSHAIR, effect = {
        it.pct.critChance += 0.06f
})
    val CRIT_DMG = UpgradeDef("crit_dmg", "Executioner", "+25% crit damage", com.towerbound.laststand.ui.Rarity.UNCOMMON, 6, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.SKULL, effect = {
        it.pct.critDamage += 0.25f
})
    val LIFESTEAL = UpgradeDef("lifesteal", "Vampiric Edge", "+4% lifesteal", com.towerbound.laststand.ui.Rarity.RARE, 5, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.DROP, effect = {
        it.pct.lifesteal += 0.04f
})
    val ARMOR_PLUS = UpgradeDef("armor_plus", "Iron Skin", "+15 armor", com.towerbound.laststand.ui.Rarity.COMMON, 8, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.SHIELD, effect = {
        it.flat.armor += 15f
})
    val CDR_PLUS = UpgradeDef("cdr_plus", "Echoing Focus", "+8% cooldown reduction", com.towerbound.laststand.ui.Rarity.RARE, 6, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.MANA_SURGE, effect = {
        it.pct.cooldownReduction += 0.08f
})
    val AOE_PLUS = UpgradeDef("aoe_plus", "Amplified Force", "+20% area of effect", com.towerbound.laststand.ui.Rarity.UNCOMMON, 5, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.STATIC, effect = {
        it.pct.aoeSize += 0.20f
})
    val TOWER_HEAL = UpgradeDef("tower_heal", "Mason's Blessing", "Tower heals 15% HP now + +2 regen", com.towerbound.laststand.ui.Rarity.RARE, 5, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.HEART, effect = {
        it.flat.hpRegen += 2f
    })
    val PROJECTILE = UpgradeDef("projectile", "Split Shot", "+1 projectile", com.towerbound.laststand.ui.Rarity.EPIC, 4, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.BOW, effect = {
        it.flat.projectileCount += 1
})
    val TOWER_DMG = UpgradeDef("tower_dmg", "Arcane Conduits", "+25% tower damage", com.towerbound.laststand.ui.Rarity.RARE, 5, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.TURRET, effect = {
        it.pct.attackDamage += 0.25f
})
    val HP_PLUS = UpgradeDef("hp_plus", "Vigor", "+15% max HP", com.towerbound.laststand.ui.Rarity.COMMON, 8, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.HEART, effect = {
        it.pct.maxHp += 0.15f
})
    val RANGE_PLUS = UpgradeDef("range_plus", "Long Reach", "+12% attack range", com.towerbound.laststand.ui.Rarity.COMMON, 6, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.ARROW_UP, effect = {
        it.pct.attackRange += 0.12f
})
    val KNOCKBACK_PLUS = UpgradeDef("knockback", "Heavy Blows", "+knockback force", com.towerbound.laststand.ui.Rarity.COMMON, 5, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.UNSTOPPABLE, effect = {
        it.flat.knockback += 60f
})

    /* ---- elemental synergy upgrades (design: SYNERGY SYSTEM) ---- */
    val BURNING_CRIT = UpgradeDef("burning_crit", "Burning Crit", "Crits ignite enemies (burn synergy)", com.towerbound.laststand.ui.Rarity.EPIC, 1, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.FIRE,
        effect = { }, synergyTag = "burning_crit")
    val THUNDERSTORM = UpgradeDef("thunderstorm", "Thunderstorm", "Attacks chain lightning (storm synergy)", com.towerbound.laststand.ui.Rarity.LEGENDARY, 1, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.LIGHTNING,
        effect = { it.pct.attackSpeed += 0.1f }, synergyTag = "thunderstorm")
    val FROST_NOVA = UpgradeDef("frost_nova", "Frost Nova", "Attacks chill enemies (frost synergy)", com.towerbound.laststand.ui.Rarity.EPIC, 1, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.SNOWFLAKE,
        effect = { }, synergyTag = "frost_nova")
    val TOXIC_BARRAGE = UpgradeDef("toxic_barrage", "Toxic Barrage", "Attats poison on hit (toxin synergy)", com.towerbound.laststand.ui.Rarity.EPIC, 1, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.CURSE,
        effect = { }, synergyTag = "toxic_barrage")
    val GUARDIAN_PROTOCOL = UpgradeDef("guardian_protocol", "Guardian Protocol", "Tower gains an energy shield (guardian synergy)", com.towerbound.laststand.ui.Rarity.LEGENDARY, 1, symbol = com.towerbound.laststand.assets.SymbolPainter.Symbol.SHIELD,
        effect = { }, synergyTag = "guardian_protocol")

    val all: List<UpgradeDef> = listOf(
        ATK_PLUS, AS_PLUS, MS_PLUS, CRIT_CHANCE, CRIT_DMG, LIFESTEAL, ARMOR_PLUS,
        CDR_PLUS, AOE_PLUS, TOWER_HEAL, PROJECTILE, TOWER_DMG, HP_PLUS, RANGE_PLUS,
        KNOCKBACK_PLUS, BURNING_CRIT, THUNDERSTORM, FROST_NOVA, TOXIC_BARRAGE, GUARDIAN_PROTOCOL
    )

    fun byId(id: String): UpgradeDef? = all.firstOrNull { it.id == id }

    /** Rarity weights for the offer sampler. */
    fun weightOf(r: com.towerbound.laststand.ui.Rarity): Float = when (r) {
        com.towerbound.laststand.ui.Rarity.COMMON -> 1f
        com.towerbound.laststand.ui.Rarity.UNCOMMON -> 0.6f
        com.towerbound.laststand.ui.Rarity.RARE -> 0.32f
        com.towerbound.laststand.ui.Rarity.EPIC -> 0.14f
        com.towerbound.laststand.ui.Rarity.LEGENDARY -> 0.05f
        com.towerbound.laststand.ui.Rarity.MYTHIC -> 0.02f
    }
}

/**
 * In-run XP & level system.
 *  - XP orbs drop on enemy death, fly toward the player, absorb on contact
 *  - level-up pauses the sim and offers 3 randomized upgrade cards
 *  - chosen upgrades apply immediately to the player StatBlock
 *  - active synergies tracked by tag (combat layer reads them)
 */
class XpSystem : com.towerbound.laststand.core.SimSystem {

    override val systemName: String = "XpSystem"

    companion object {
        /** XP required for level N (1-indexed): 6 + (N-1)^1.35 * 4 */
        fun xpForLevel(level: Int): Int {
            if (level <= 1) return 6
            val l = (level - 1).toDouble()
            return (6 + Math.pow(l, 1.35) * 4).toInt()
        }
        const val ORB_MAGNET_RADIUS = 150f
        const val ORB_SPEED = 420f
        const val MAX_ORBS = 160
    }

    /** XP orb entity (pooled). */
    class Orb(var x: Float, var y: Float, var value: Int) {
        var alive = false
        var vx = 0f
        var vy = 0f
        var bobPhase: Float = 0f
    }

    private val orbs = Array(MAX_ORBS) { Orb(0f, 0f, 0) }
    private val activeOrbs = mutableListOf<Orb>()

    var level: Int = 1
        private set
    var xp: Float = 0f
        private set
    var xpToNext: Int = xpForLevel(1)
        private set

    /** Total XP earned this run (for run-end hero progression rewards). */
    var totalXpEarned: Float = 0f
        private set

    /** Pending level-ups waiting for card selection. */
    val pendingLevels: Int get() = pendingLevelUps

    private var pendingLevelUps = 0

    /** Chosen upgrade stacks: id -> count. */
    val chosen = LinkedHashMap<String, Int>()
    val activeSynergies = mutableSetOf<String>()

    /** UI hook: called when a card choice is required (pauses sim). */
    var onLevelUp: ((level: Int, offers: List<UpgradeDef>) -> Unit)? = null
    /** Callback after an upgrade is applied (HUD toast). */
    var onUpgradeChosen: ((upgrade: UpgradeDef, stacks: Int) -> Unit)? = null

    /** Combat hooks for synergy effects. */
    var onAttackHit: ((target: com.towerbound.laststand.combat.Combatant, isCrit: Boolean) -> Unit)? = null

    private var towerHealQueued = 0f

    fun addXp(amount: Float) {
        if (amount <= 0f) return
        xp += amount
        totalXpEarned += amount
        while (xp >= xpToNext) {
            xp -= xpToNext
            level++
            xpToNext = xpForLevel(level)
            pendingLevelUps++
        }
    }

    fun spawnOrb(x: Float, y: Float, value: Int) {
        if (value <= 0) return
        val orb = orbs.firstOrNull { !it.alive } ?: return
        orb.x = x; orb.y = y; orb.value = value
        orb.vx = (Math.random().toFloat() - 0.5f) * 120f
        orb.vy = (Math.random().toFloat() - 0.5f) * 120f
        orb.bobPhase = Math.random().toFloat() * 6.28f
        orb.alive = true
        activeOrbs.add(orb)
    }

    override fun onEnterWorld(engine: com.towerbound.laststand.core.GameEngine) {
        level = 1
        xp = 0f
        totalXpEarned = 0f
        xpToNext = xpForLevel(1)
        pendingLevelUps = 0
        chosen.clear()
        activeSynergies.clear()
        activeOrbs.clear()
        for (o in orbs) o.alive = false
    }

    override fun update(engine: com.towerbound.laststand.core.GameEngine, dt: Float) {
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player ?: return

        // orb physics: pop outward, then magnet to the player
        val it = activeOrbs.iterator()
        while (it.hasNext()) {
            val orb = it.next()
            orb.x += orb.vx * dt
            orb.y += orb.vy * dt
            orb.vx *= 0.9f
            orb.vy *= 0.9f
            orb.bobPhase += dt * 4f

            val dx = player.x - orb.x
            val dy = player.y - orb.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d < ORB_MAGNET_RADIUS && player.alive) {
                if (d < player.radius + 14f) {
                    addXp(orb.value.toFloat())
                    orb.alive = false
                    it.remove()
                    continue
                }
                if (d > 1f) {
                    orb.x += dx / d * ORB_SPEED * dt
                    orb.y += dy / d * ORB_SPEED * dt
                }
            }
        }

        // level-up gating
        if (pendingLevelUps > 0 && !levelUpShowing) {
            levelUpShowing = true
            val offers = sampleOffers(engine.upgradeRng)
            onLevelUp?.invoke(level, offers)
        }

        // queued tower heal from Mason's Blessing
        if (towerHealQueued > 0f) {
            val tower = engine.system<com.towerbound.laststand.tower.TowerSystem>()?.tower
            tower?.heal(towerHealQueued)
            towerHealQueued = 0f
        }
    }

    private var levelUpShowing = false

    /** Sample 3 distinct upgrade offers, respecting stack caps & weights. */
    fun sampleOffers(rng: Rng): List<UpgradeDef> {
        val available = UpgradePool.all.filter { (chosen[it.id] ?: 0) < it.maxStacks }
        if (available.isEmpty()) return emptyList()
        val picks = mutableListOf<UpgradeDef>()
        var guard = 0
        while (picks.size < 3 && guard < 60 && available.isNotEmpty()) {
            guard++
            val weighted = available.filter { it !in picks }
            if (weighted.isEmpty()) break
            var total = 0f
            for (u in weighted) total += UpgradePool.weightOf(u.rarity)
            var roll = rng.nextFloat() * total
            var selected = weighted.last()
            for (u in weighted) {
                roll -= UpgradePool.weightOf(u.rarity)
                if (roll <= 0f) { selected = u; break }
            }
            picks.add(selected)
        }
        return picks
    }

    /**
     * Apply a chosen upgrade. Returns the new stack count, or -1 when the
     * card is invalid / capped.
     */
    fun choose(upgradeId: String, engine: com.towerbound.laststand.core.GameEngine): Int {
        val def = UpgradePool.byId(upgradeId) ?: return -1
        val cur = chosen[upgradeId] ?: 0
        if (cur >= def.maxStacks) return -1

        chosen[upgradeId] = cur + 1
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player
        if (player != null) {
            val oldMax = player.stats.finalMaxHp
            def.effect(player.stats)
            val newMax = player.stats.finalMaxHp
            if (newMax > oldMax) {
                // heal the gained HP chunk
                player.heal(newMax - oldMax)
            }
        }
        // Mason's Blessing: heal tower immediately
        if (upgradeId == "tower_heal") {
            val tower = engine.system<com.towerbound.laststand.tower.TowerSystem>()?.tower
            towerHealQueued += tower?.maxHp()?.times(0.15f) ?: 0f
        }
        // Guardian Protocol: tower shield boost
        if (upgradeId == "guardian_protocol") {
            val towerSystem = engine.system<com.towerbound.laststand.tower.TowerSystem>()
            towerSystem?.tower?.let { t ->
                t.towerMaxShield += 80f
                t.restoreShield(80f)
            }
        }
        if (def.synergyTag != null) activeSynergies.add(def.synergyTag!!)
        onUpgradeChosen?.invoke(def, cur + 1)
        engine.eventBus.post(com.towerbound.laststand.core.UpgradeChosenEvent(upgradeId))

        pendingLevelUps--
        if (pendingLevelUps <= 0) {
            pendingLevelUps = 0
            levelUpShowing = false
        }
        return cur + 1
    }

    fun forEachOrb(action: (Orb) -> Unit) = activeOrbs.forEach(action)
    fun orbCount(): Int = activeOrbs.size

    /** Wire enemy deaths to orb drops + gold (called from EnemyManager). */
    fun onEnemyDeath(enemy: com.towerbound.laststand.enemies.EnemyEntity, engine: com.towerbound.laststand.core.GameEngine) {
        val waveScale = engine.system<com.towerbound.laststand.waves.WaveSystem>()?.difficultyMultiplier() ?: 1f
        val xpValue = (enemy.def.xpValue * waveScale * (if (enemy.isElite) 2.5f else 1f)).toInt().coerceAtLeast(1)
        // spread across 1-3 orbs for pickup satisfaction
        val orbCount = if (xpValue >= 8) 3 else if (xpValue >= 3) 2 else 1
        val per = xpValue / orbCount
        repeat(orbCount) { i ->
            spawnOrb(
                enemy.x + (i - orbCount / 2f) * 18f,
                enemy.y + (i - orbCount / 2f) * 10f,
                per
            )
        }
    }
}
