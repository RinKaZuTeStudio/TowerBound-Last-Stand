package com.towerbound.laststand.meta

import com.towerbound.laststand.ui.Rarity
import org.json.JSONObject

/**
 * Centralized economy (design: ECONOMY — never modify currency directly).
 * All transactions validate balance, negative amounts, overflow and
 * duplicate rewards. Currencies: Gold + Gems.
 */
class EconomyManager {

    companion object {
        const val MAX_CURRENCY = 999_999_999L
    }

    @Volatile var gold: Long = 0
        private set
    @Volatile var gems: Long = 0
        private set

    /** Duplicate-reward guard: set of already-claimed reward ids. */
    private val claimedRewards = mutableSetOf<String>()

    sealed class Transaction {
        data class Success(val gold: Long, val gems: Long) : Transaction()
        data class Failure(val reason: Reason) : Transaction()
        enum class Reason { INSUFFICIENT_GOLD, INSUFFICIENT_GEMS, INVALID_AMOUNT, OVERFLOW, DUPLICATE }
    }

    fun balanceGold(): Long = gold
    fun balanceGems(): Long = gems

    /** Add currency; rejects invalid amounts and clamps overflow. */
    fun grant(goldAmount: Long = 0, gemsAmount: Long = 0): Transaction {
        if (goldAmount < 0 || gemsAmount < 0) return Transaction.Failure(Transaction.Reason.INVALID_AMOUNT)
        val newGold = gold + goldAmount
        val newGems = gems + gemsAmount
        if (newGold > MAX_CURRENCY || newGems > MAX_CURRENCY) {
            gold = newGold.coerceAtMost(MAX_CURRENCY)
            gems = newGems.coerceAtMost(MAX_CURRENCY)
            return Transaction.Failure(Transaction.Reason.OVERFLOW)
        }
        gold = newGold
        gems = newGems
        return Transaction.Success(gold, gems)
    }

    /** One-time reward claim guard. Returns failure on duplicate ids. */
    fun grantOnce(rewardId: String, goldAmount: Long = 0, gemsAmount: Long = 0): Transaction {
        if (!claimedRewards.add(rewardId)) return Transaction.Failure(Transaction.Reason.DUPLICATE)
        return grant(goldAmount, gemsAmount)
    }

    /** Spend with validation. */
    fun spend(goldCost: Long = 0, gemCost: Long = 0): Transaction {
        if (goldCost < 0 || gemCost < 0) return Transaction.Failure(Transaction.Reason.INVALID_AMOUNT)
        if (gold < goldCost) return Transaction.Failure(Transaction.Reason.INSUFFICIENT_GOLD)
        if (gems < gemCost) return Transaction.Failure(Transaction.Reason.INSUFFICIENT_GEMS)
        gold -= goldCost
        gems -= gemCost
        return Transaction.Success(gold, gems)
    }

    /** Load state from JSON (save system). */
    fun fromJson(json: JSONObject) {
        gold = json.optLong("gold", 0L).coerceIn(0, MAX_CURRENCY)
        gems = json.optLong("gems", 0L).coerceIn(0, MAX_CURRENCY)
        claimedRewards.clear()
        val arr = json.optJSONArray("claimed") ?: return
        for (i in 0 until arr.length()) claimedRewards.add(arr.optString(i))
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("gold", gold)
        json.put("gems", gems)
        json.put("claimed", org.json.JSONArray(claimedRewards))
        return json
    }

    fun resetForTesting() {
        gold = 0; gems = 0; claimedRewards.clear()
    }
}

/* ================================================================== */
/* Hero meta progression                                              */
/* ================================================================== */

/**
 * Per-hero permanent progression: XP, level, unlock state, shards.
 * Level ups grant stat growth applied to run base stats.
 */
class HeroProgress(
    val id: String,
    var unlocked: Boolean = false,
    var level: Int = 1,
    var xp: Long = 0,
    var shards: Int = 0
) {
    companion object {
        /** Hero XP for level N: 80 * N^1.4 */
        fun xpForLevel(level: Int): Long {
            if (level <= 1) return 80
            return (80 * Math.pow(level.toDouble(), 1.4)).toLong()
        }
        const val MAX_LEVEL = Int.MAX_VALUE
        const val UNLOCK_SHARDS = 30
        const val UNLOCK_GOLD = 900L

        fun fromJson(json: JSONObject): HeroProgress = HeroProgress(
            json.optString("id", "ARIC"),
            json.optBoolean("unlocked", false),
            json.optInt("level", 1).coerceAtLeast(1),
            json.optLong("xp", 0L).coerceAtLeast(0),
            json.optInt("shards", 0).coerceAtLeast(0)
        )
    }

    val xpToNext: Long get() = xpForLevel(level)

    /** Add XP; returns number of levels gained. */
    fun addXp(amount: Long): Int {
        if (amount <= 0 || level >= MAX_LEVEL) return 0
        xp += amount
        var gained = 0
        while (level < MAX_LEVEL && xp >= xpToNext) {
            xp -= xpToNext
            level++
            gained++
        }
        if (level >= MAX_LEVEL) xp = 0
        return gained
    }

    /** Permanent stat multipliers from level (applied to base template). */
    fun statScale(): Float = 1f + (level - 1) * 0.045f

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("unlocked", unlocked)
        json.put("level", level)
        json.put("xp", xp)
        json.put("shards", shards)
        return json
    }

}

/**
 * Tower permanent upgrade track (design: TOWER SYSTEM upgrades 1-5).
 */
object TowerUpgrades {
    const val MAX_LEVEL = Int.MAX_VALUE

    enum class Kind(val label: String, val baseCost: Int, val costGrowth: Float, val perLevel: Float) {
        MAX_HP("Maximum HP", 120f.toInt(), 1.5f, 160f),
        SHIELD("Shield Capacity", 150, 1.55f, 22f),
        REGEN("Regeneration", 130, 1.5f, 0.8f),
        TURRET("Auto Turret", 200, 1.6f, 0.12f),
        DAMAGE("Tower Damage", 140, 1.5f, 3f)
    }

    fun cost(kind: Kind, currentLevel: Int): Int =
        (kind.baseCost * Math.pow(kind.costGrowth.toDouble(), currentLevel.toDouble())).toInt()

    fun effect(kind: Kind, level: Int): Float = kind.perLevel * level
}

/* ================================================================== */
/* Skins & inventory                                                  */
/* ================================================================== */

data class SkinDef(
    val id: String,
    val heroId: String,
    val name: String,
    val rarity: Rarity,
    val tint: FloatArray,      // applied to hero sprite
    val unlockGold: Int = 0,
    val unlockWave: Int = 0    // 0 = purchasable only
)

/**
 * Skins alter visual appearance via tint + glow (procedural variant).
 */
object Skins {
    val DEFAULT = SkinDef("default", "", "Default", Rarity.COMMON, floatArrayOf(1f, 1f, 1f))

    val all = listOf(
        DEFAULT,
        SkinDef("aric_royal", "ARIC", "Royal Guard", Rarity.RARE, floatArrayOf(1.05f, 0.95f, 0.7f), unlockGold = 600),
        SkinDef("aric_void", "ARIC", "Void Knight", Rarity.EPIC, floatArrayOf(0.7f, 0.6f, 1.1f), unlockWave = 15),
        SkinDef("kael_forest", "KAEL", "Warden", Rarity.UNCOMMON, floatArrayOf(0.8f, 1.05f, 0.85f), unlockGold = 400),
        SkinDef("lyra_aurora", "LYRA", "Aurora", Rarity.EPIC, floatArrayOf(0.8f, 1.0f, 1.2f), unlockWave = 20),
        SkinDef("darius_magma", "DARIUS", "Magma", Rarity.RARE, floatArrayOf(1.2f, 0.75f, 0.6f), unlockGold = 700),
        SkinDef("nyx_phantom", "NYX", "Phantom", Rarity.LEGENDARY, floatArrayOf(0.9f, 0.85f, 1.15f), unlockWave = 25),
        SkinDef("volt_storm", "VOLT", "Storm Caller", Rarity.RARE, floatArrayOf(0.85f, 1.0f, 1.15f), unlockGold = 650),
        SkinDef("bronn_solar", "BRONN", "Solar Aegis", Rarity.EPIC, floatArrayOf(1.15f, 1.05f, 0.75f), unlockWave = 30),
        SkinDef("morvain_blight", "MORVAIN", "Blightborn", Rarity.RARE, floatArrayOf(0.9f, 1.1f, 0.8f), unlockGold = 550)
    )

    fun byId(id: String): SkinDef = all.firstOrNull { it.id == id } ?: DEFAULT
    fun forHero(heroId: String): List<SkinDef> = all.filter { it.heroId == heroId || it.id == "default" }
}

/**
 * Inventory: hero shards, skins, consumables, collectibles + equipped
 * loadout slots (extensible for relics/artifacts per design).
 */
class InventoryManager {

    /** item id -> quantity. */
    private val items = LinkedHashMap<String, Int>()

    /** Owned skin ids. */
    val ownedSkins = mutableSetOf<String>("default")

    /** Equipped skin per hero. */
    val equippedSkins = HashMap<String, String>()

    /** Extensible loadout slots (future relics/artifacts). */
    val loadout = HashMap<String, String>()   // slotName -> itemId

    fun add(itemId: String, qty: Int = 1) {
        if (qty <= 0) return
        items[itemId] = (items[itemId] ?: 0) + qty
    }

    fun count(itemId: String): Int = items[itemId] ?: 0

    /** Consume qty; returns false when insufficient or qty invalid. */
    fun consume(itemId: String, qty: Int = 1): Boolean {
        if (qty <= 0) return false
        val cur = items[itemId] ?: 0
        if (cur < qty) return false
        val left = cur - qty
        if (left > 0) items[itemId] = left else items.remove(itemId)
        return true
    }

    fun grantSkin(skinId: String) {
        ownedSkins.add(skinId)
    }

    fun ownsSkin(skinId: String): Boolean = ownedSkins.contains(skinId)

    fun equipSkin(heroId: String, skinId: String): Boolean {
        if (!ownedSkins.contains(skinId)) return false
        val skin = Skins.byId(skinId)
        if (skin.heroId != heroId && skin.id != "default") return false
        equippedSkins[heroId] = skinId
        return true
    }

    fun equippedSkinFor(heroId: String): SkinDef {
        val id = equippedSkins[heroId] ?: return Skins.DEFAULT
        return if (ownedSkins.contains(id)) Skins.byId(id) else Skins.DEFAULT
    }

    /** Loadout slot API (relics/artifacts arrive later; system is live). */
    fun equipSlot(slot: String, itemId: String): Boolean {
        loadout[slot] = itemId
        return true
    }

    fun unequipSlot(slot: String) {
        loadout.remove(slot)
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        val itemsArr = org.json.JSONArray()
        for ((k, v) in items) {
            val item = JSONObject()
            item.put("id", k); item.put("qty", v)
            itemsArr.put(item)
        }
        json.put("items", itemsArr)
        json.put("skins", org.json.JSONArray(ownedSkins))
        val equip = JSONObject()
        for ((hero, skin) in equippedSkins) equip.put(hero, skin)
        json.put("equippedSkins", equip)
        val slots = JSONObject()
        for ((slot, item) in loadout) slots.put(slot, item)
        json.put("loadout", slots)
        return json
    }

    fun fromJson(json: JSONObject) {
        items.clear()
        ownedSkins.clear()
        ownedSkins.add("default")
        equippedSkins.clear()
        loadout.clear()
        val itemsArr = json.optJSONArray("items") ?: return
        for (i in 0 until itemsArr.length()) {
            val item = itemsArr.optJSONObject(i) ?: continue
            items[item.optString("id")] = item.optInt("qty", 0)
        }
        json.optJSONArray("skins")?.let { arr ->
            for (i in 0 until arr.length()) ownedSkins.add(arr.optString(i))
        }
        json.optJSONObject("equippedSkins")?.let { equip ->
            val keys = equip.keys()
            while (keys.hasNext()) {
                val hero = keys.next()
                equippedSkins[hero] = equip.optString(hero)
            }
        }
        json.optJSONObject("loadout")?.let { slots ->
            val keys = slots.keys()
            while (keys.hasNext()) {
                val slot = keys.next()
                loadout[slot] = slots.optString(slot)
            }
        }
    }

    fun resetForTesting() {
        items.clear(); ownedSkins.clear(); ownedSkins.add("default")
        equippedSkins.clear(); loadout.clear()
    }
}

/* ================================================================== */
/* Player meta profile (facade over everything above)                */
/* ================================================================== */

/**
 * Single facade for all permanent progression. The save system serializes
 * this; gameplay reads from it at run start.
 */
class PlayerProfile {

    val economy = EconomyManager()
    val inventory = InventoryManager()

    /** Hero progression table (all 8 heroes). */
    val heroes = HashMap<String, HeroProgress>().apply {
        for (id in com.towerbound.laststand.gameplay.Heroes.allIds()) {
            put(id, HeroProgress(id, unlocked = (id == "ARIC" || id == "KAEL")))
        }
    }

    /** Tower upgrade levels. */
    val towerLevels = HashMap<TowerUpgrades.Kind, Int>().apply {
        for (k in TowerUpgrades.Kind.entries) put(k, 0)
    }

    /** Selected hero for the next run. */
    var selectedHero: String = "ARIC"

    /** Records. */
    var bestWave: Int = 0
    var totalKills: Long = 0
    var totalRuns: Long = 0
    var totalPlaytimeSeconds: Long = 0

    /** Tutorial state. */
    var tutorialDone: Boolean = false
    /** Starter currency flag (new account gets exactly 500 Gold, 10 Gems once). */
    var starterCurrencyGranted: Boolean = false

    /** Daily reset marker (epoch day). */
    var lastDailyResetDay: Int = 0

    fun heroProgress(id: String): HeroProgress = heroes.getOrPut(id) { HeroProgress(id) }

    fun unlockHero(id: String): Boolean {
        val p = heroProgress(id)
        if (p.unlocked) return false
        // unlock via shards (primary) or gold fallback (shop)
        val shardsItem = "shard_$id"
        if (inventory.count(shardsItem) >= HeroProgress.UNLOCK_SHARDS) {
            inventory.consume(shardsItem, HeroProgress.UNLOCK_SHARDS)
            p.unlocked = true
            return true
        }
        return false
    }

    /** Shop-style unlock with gold. */
    fun unlockHeroWithGold(id: String): Boolean {
        val p = heroProgress(id)
        if (p.unlocked) return false
        if (economy.spend(goldCost = HeroProgress.UNLOCK_GOLD) is EconomyManager.Transaction.Success) {
            p.unlocked = true
            return true
        }
        return false
    }

    fun purchaseTowerUpgrade(kind: TowerUpgrades.Kind): Boolean {
        val level = towerLevels[kind] ?: 0
        if (level >= TowerUpgrades.MAX_LEVEL) return false
        val cost = TowerUpgrades.cost(kind, level)
        if (economy.spend(cost.toLong()) is EconomyManager.Transaction.Success) {
            towerLevels[kind] = level + 1
            return true
        }
        return false
    }

    fun purchaseSkin(skin: SkinDef): Boolean {
        if (inventory.ownsSkin(skin.id)) return false
        if (skin.unlockWave > bestWave) return false
        if (skin.unlockGold > 0) {
            if (economy.spend(skin.unlockGold.toLong()) !is EconomyManager.Transaction.Success) return false
        }
        inventory.grantSkin(skin.id)
        return true
    }

    /** Run-end integration: returns summary of granted rewards. */
    fun onRunEnded(wave: Int, kills: Int, goldEarned: Int, heroId: String, heroXp: Long): RunSummary {
        val summary = RunSummary()
        totalRuns++
        totalKills += kills
        if (wave > bestWave) {
            bestWave = wave
            summary.newBestWave = true
        }
        economy.grant(goldEarned.toLong().coerceAtLeast(0))
        val progress = heroProgress(heroId)
        val levels = progress.addXp(heroXp.coerceAtLeast(0))
        summary.heroLevelsGained = levels
        // shard drops: 1 shard per boss wave milestone reached
        if (wave >= 10) {
            val shards = (wave / 10).coerceAtMost(4)
            inventory.add("shard_$heroId", shards)
            summary.shardsGranted = shards
        }
        return summary
    }

    class RunSummary {
        var newBestWave = false
        var heroLevelsGained = 0
        var shardsGranted = 0
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("economy", economy.toJson())
        json.put("inventory", inventory.toJson())
        val heroesArr = org.json.JSONArray()
        for (p in heroes.values) heroesArr.put(p.toJson())
        json.put("heroes", heroesArr)
        val tower = JSONObject()
        for ((k, v) in towerLevels) tower.put(k.name, v)
        json.put("tower", tower)
        json.put("selectedHero", selectedHero)
        json.put("bestWave", bestWave)
        json.put("totalKills", totalKills)
        json.put("totalRuns", totalRuns)
        json.put("playtime", totalPlaytimeSeconds)
        json.put("tutorialDone", tutorialDone)
        json.put("starterCurrencyGranted", starterCurrencyGranted)
        json.put("lastDailyResetDay", lastDailyResetDay)
        return json
    }

    fun fromJson(json: JSONObject) {
        economy.fromJson(json.optJSONObject("economy") ?: JSONObject())
        inventory.fromJson(json.optJSONObject("inventory") ?: JSONObject())
        heroes.clear()
        for (id in com.towerbound.laststand.gameplay.Heroes.allIds()) {
            heroes[id] = HeroProgress(id, unlocked = (id == "ARIC" || id == "KAEL"))
        }
        json.optJSONArray("heroes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = HeroProgress.fromJson(arr.optJSONObject(i))
                heroes[p.id] = p
            }
        }
        json.optJSONObject("tower")?.let { tower ->
            for (k in TowerUpgrades.Kind.entries) {
                towerLevels[k] = tower.optInt(k.name, 0).coerceAtLeast(0)
            }
        }
        selectedHero = json.optString("selectedHero", "ARIC")
        bestWave = json.optInt("bestWave", 0)
        totalKills = json.optLong("totalKills", 0)
        totalRuns = json.optLong("totalRuns", 0)
        totalPlaytimeSeconds = json.optLong("playtime", 0)
        tutorialDone = json.optBoolean("tutorialDone", false)
        starterCurrencyGranted = json.optBoolean("starterCurrencyGranted", false)
        lastDailyResetDay = json.optInt("lastDailyResetDay", 0)
    }

    fun resetForTesting() {
        economy.resetForTesting()
        inventory.resetForTesting()
        for (id in com.towerbound.laststand.gameplay.Heroes.allIds()) {
            heroes[id] = HeroProgress(id, unlocked = (id == "ARIC" || id == "KAEL"))
        }
        for (k in TowerUpgrades.Kind.entries) towerLevels[k] = 0
        selectedHero = "ARIC"
        bestWave = 0; totalKills = 0; totalRuns = 0; totalPlaytimeSeconds = 0
        tutorialDone = false
        lastDailyResetDay = 0
    }
}
