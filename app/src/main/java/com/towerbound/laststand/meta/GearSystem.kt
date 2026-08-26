package com.towerbound.laststand.meta

import com.towerbound.laststand.combat.StatBlock
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

enum class GearSlot(val label: String) {
    WEAPON("Weapon"), HELMET("Helmet"), ARMOR("Armor"), GLOVES("Gloves"),
    BOOTS("Boots"), RING_1("Ring"), RING_2("Ring"), NECKLACE("Necklace"),
    BELT("Belt"), ACCESSORY("Accessory")
}

enum class GearType(val slot: GearSlot) {
    SWORD(GearSlot.WEAPON), GREATSWORD(GearSlot.WEAPON), DAGGERS(GearSlot.WEAPON),
    BOW(GearSlot.WEAPON), STAFF(GearSlot.WEAPON), WAND(GearSlot.WEAPON),
    ORB(GearSlot.WEAPON), CANNON(GearSlot.WEAPON), SHIELD(GearSlot.WEAPON),
    HELMET(GearSlot.HELMET), ARMOR(GearSlot.ARMOR), GLOVES(GearSlot.GLOVES),
    BOOTS(GearSlot.BOOTS), RING(GearSlot.RING_1), // Can go to RING_1 or RING_2
    NECKLACE(GearSlot.NECKLACE), BELT(GearSlot.BELT), ACCESSORY(GearSlot.ACCESSORY)
}

enum class GearRarity(val label: String, val color: Int, val statMult: Float) {
    COMMON("Common", 0xFFAAAAAA.toInt(), 1.0f),
    UNCOMMON("Uncommon", 0xFF65D95C.toInt(), 1.3f),
    RARE("Rare", 0xFF4DA6FF.toInt(), 1.7f),
    EPIC("Epic", 0xFFB359FF.toInt(), 2.2f),
    LEGENDARY("Legendary", 0xFFFFB300.toInt(), 3.0f),
    MYTHIC("Mythic", 0xFFFF4D4D.toInt(), 4.5f),
    ANCIENT("Ancient", 0xFF00FFCC.toInt(), 6.5f),
    DIVINE("Divine", 0xFFFFFFFF.toInt(), 10.0f)
}

data class GearInstance(
    val id: String = UUID.randomUUID().toString(),
    val type: GearType,
    val rarity: GearRarity,
    var level: Int = 1,
    var isLocked: Boolean = false,
    
    // Stats
    val flatAtk: Float = 0f,
    val pctAtk: Float = 0f,
    val flatHp: Float = 0f,
    val pctHp: Float = 0f,
    val flatArmor: Float = 0f,
    val critChance: Float = 0f,
    val critDmg: Float = 0f,
    val attackSpeed: Float = 0f,
    val moveSpeed: Float = 0f,
    val lifesteal: Float = 0f,
    val cooldownReduction: Float = 0f,
    val aoeSize: Float = 0f,
    val dropRate: Float = 0f,
    val goldGain: Float = 0f,
    val xpGain: Float = 0f
) {
    val levelMultiplier: Float get() = 1f + (level - 1) * 0.1f

    fun applyToStats(stats: StatBlock) {
        val m = levelMultiplier
        stats.flat.attackDamage += flatAtk * m
        stats.pct.attackDamage += pctAtk * m
        stats.flat.maxHp += flatHp * m
        stats.pct.maxHp += pctHp * m
        stats.flat.armor += flatArmor * m
        stats.pct.critChance += critChance * m
        stats.pct.critDamage += critDmg * m
        stats.pct.attackSpeed += attackSpeed * m
        stats.pct.moveSpeed += moveSpeed * m
        stats.pct.lifesteal += lifesteal * m
        stats.pct.cooldownReduction += cooldownReduction * m
        stats.pct.aoeSize += aoeSize * m
    }

    fun getUpgradeCost(): Long {
        return (100 * rarity.statMult * levelMultiplier * levelMultiplier).toLong()
    }

    fun getSellValue(): Long {
        return (20 * rarity.statMult * levelMultiplier).toLong()
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name)
        json.put("rarity", rarity.name)
        json.put("level", level)
        json.put("isLocked", isLocked)
        json.put("flatAtk", flatAtk.toDouble())
        json.put("pctAtk", pctAtk.toDouble())
        json.put("flatHp", flatHp.toDouble())
        json.put("pctHp", pctHp.toDouble())
        json.put("flatArmor", flatArmor.toDouble())
        json.put("critChance", critChance.toDouble())
        json.put("critDmg", critDmg.toDouble())
        json.put("attackSpeed", attackSpeed.toDouble())
        json.put("moveSpeed", moveSpeed.toDouble())
        json.put("lifesteal", lifesteal.toDouble())
        json.put("cooldownReduction", cooldownReduction.toDouble())
        json.put("aoeSize", aoeSize.toDouble())
        json.put("dropRate", dropRate.toDouble())
        json.put("goldGain", goldGain.toDouble())
        json.put("xpGain", xpGain.toDouble())
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): GearInstance {
            return GearInstance(
                id = json.optString("id", UUID.randomUUID().toString()),
                type = GearType.valueOf(json.optString("type", GearType.SWORD.name)),
                rarity = GearRarity.valueOf(json.optString("rarity", GearRarity.COMMON.name)),
                level = json.optInt("level", 1),
                isLocked = json.optBoolean("isLocked", false),
                flatAtk = json.optDouble("flatAtk", 0.0).toFloat(),
                pctAtk = json.optDouble("pctAtk", 0.0).toFloat(),
                flatHp = json.optDouble("flatHp", 0.0).toFloat(),
                pctHp = json.optDouble("pctHp", 0.0).toFloat(),
                flatArmor = json.optDouble("flatArmor", 0.0).toFloat(),
                critChance = json.optDouble("critChance", 0.0).toFloat(),
                critDmg = json.optDouble("critDmg", 0.0).toFloat(),
                attackSpeed = json.optDouble("attackSpeed", 0.0).toFloat(),
                moveSpeed = json.optDouble("moveSpeed", 0.0).toFloat(),
                lifesteal = json.optDouble("lifesteal", 0.0).toFloat(),
                cooldownReduction = json.optDouble("cooldownReduction", 0.0).toFloat(),
                aoeSize = json.optDouble("aoeSize", 0.0).toFloat(),
                dropRate = json.optDouble("dropRate", 0.0).toFloat(),
                goldGain = json.optDouble("goldGain", 0.0).toFloat(),
                xpGain = json.optDouble("xpGain", 0.0).toFloat()
            )
        }

        fun generateRandom(waveLevel: Int): GearInstance {
            val type = GearType.entries.random()
            
            // Higher waves give better rarity chances
            val roll = Random.nextFloat()
            val rarity = when {
                roll < 0.005f + (waveLevel * 0.0005f) -> GearRarity.DIVINE
                roll < 0.01f + (waveLevel * 0.001f) -> GearRarity.ANCIENT
                roll < 0.03f + (waveLevel * 0.002f) -> GearRarity.MYTHIC
                roll < 0.08f + (waveLevel * 0.003f) -> GearRarity.LEGENDARY
                roll < 0.20f + (waveLevel * 0.005f) -> GearRarity.EPIC
                roll < 0.40f + (waveLevel * 0.010f) -> GearRarity.RARE
                roll < 0.70f -> GearRarity.UNCOMMON
                else -> GearRarity.COMMON
            }

            val m = rarity.statMult * (1f + waveLevel * 0.1f)
            
            var flatAtk = 0f
            var pctAtk = 0f
            var flatHp = 0f
            var pctHp = 0f
            var flatArmor = 0f
            
            when (type.slot) {
                GearSlot.WEAPON -> { flatAtk = 10f * m; pctAtk = 0.05f * m }
                GearSlot.HELMET -> { flatHp = 50f * m; flatArmor = 5f * m }
                GearSlot.ARMOR -> { flatHp = 100f * m; flatArmor = 15f * m }
                GearSlot.GLOVES -> { pctAtk = 0.02f * m; flatArmor = 5f * m }
                GearSlot.BOOTS -> { flatHp = 20f * m; flatArmor = 3f * m }
                else -> { flatAtk = 2f * m; flatHp = 15f * m }
            }

            return GearInstance(
                type = type,
                rarity = rarity,
                level = 1, // upgradable
                flatAtk = flatAtk,
                pctAtk = pctAtk,
                flatHp = flatHp,
                pctHp = pctHp,
                flatArmor = flatArmor,
                critChance = if (Random.nextFloat() < 0.3f) 0.05f * m else 0f,
                critDmg = if (Random.nextFloat() < 0.3f) 0.2f * m else 0f,
                attackSpeed = if (Random.nextFloat() < 0.3f) 0.05f * m else 0f,
                moveSpeed = if (type.slot == GearSlot.BOOTS) 0.1f * m else 0f,
                lifesteal = if (Random.nextFloat() < 0.1f) 0.02f * m else 0f,
                cooldownReduction = if (Random.nextFloat() < 0.2f) 0.03f * m else 0f,
                dropRate = if (Random.nextFloat() < 0.1f) 0.05f * m else 0f,
                goldGain = if (Random.nextFloat() < 0.15f) 0.1f * m else 0f,
                xpGain = if (Random.nextFloat() < 0.15f) 0.1f * m else 0f
            )
        }
    }
}
