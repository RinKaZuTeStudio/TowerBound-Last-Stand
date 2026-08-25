package com.towerbound.laststand.meta

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Economy / inventory / hero progression / tower upgrade coverage
 * (design: ECONOMY + PERSISTENT PROGRESSION).
 */
class MetaSystemsTest {

    private lateinit var economy: EconomyManager

    @Before
    fun setup() {
        economy = EconomyManager()
        economy.resetForTesting()
    }

    /* ---------------- economy ---------------- */

    @Test
    fun `grant adds gold and gems`() {
        val result = economy.grant(100, 5)
        assertTrue(result is EconomyManager.Transaction.Success)
        assertEquals(100L, economy.balanceGold())
        assertEquals(5L, economy.balanceGems())
    }

    @Test
    fun `grant rejects negative amounts`() {
        val result = economy.grant(-10)
        assertTrue(result is EconomyManager.Transaction.Failure)
        assertEquals(EconomyManager.Transaction.Reason.INVALID_AMOUNT,
            (result as EconomyManager.Transaction.Failure).reason)
    }

    @Test
    fun `spend validates balance`() {
        economy.grant(50)
        assertTrue(economy.spend(30) is EconomyManager.Transaction.Success)
        assertEquals(20L, economy.balanceGold())
        assertTrue(economy.spend(100) is EconomyManager.Transaction.Failure)
        assertEquals(EconomyManager.Transaction.Reason.INSUFFICIENT_GOLD,
            (economy.spend(100) as EconomyManager.Transaction.Failure).reason)
    }

    @Test
    fun `grant clamps at max currency and reports overflow`() {
        val result = economy.grant(EconomyManager.MAX_CURRENCY + 500)
        assertTrue(result is EconomyManager.Transaction.Failure)
        assertEquals(EconomyManager.MAX_CURRENCY, economy.balanceGold())
    }

    @Test
    fun `grantOnce blocks duplicate reward ids`() {
        assertTrue(economy.grantOnce("reward_a", 10) is EconomyManager.Transaction.Success)
        val dup = economy.grantOnce("reward_a", 10)
        assertTrue(dup is EconomyManager.Transaction.Failure)
        assertEquals(EconomyManager.Transaction.Reason.DUPLICATE, (dup as EconomyManager.Transaction.Failure).reason)
        assertEquals(10L, economy.balanceGold())
    }

    @Test
    fun `economy json roundtrip preserves state`() {
        economy.grant(1234, 77)
        economy.grantOnce("one_time", 5)
        val json = economy.toJson()
        val restored = EconomyManager()
        restored.fromJson(JSONObject(json.toString()))
        assertEquals(1239L, restored.balanceGold())
        assertEquals(77L, restored.balanceGems())
        // duplicate guard survives the roundtrip
        assertTrue(restored.grantOnce("one_time", 5) is EconomyManager.Transaction.Failure)
    }

    /* ---------------- hero progression ---------------- */

    @Test
    fun `hero xp curve is positive and monotonically increasing`() {
        var prev = 0L
        for (level in 1..HeroProgress.MAX_LEVEL) {
            val need = HeroProgress.xpForLevel(level)
            assertTrue(need > 0)
            assertTrue(need >= prev)
            prev = need
        }
    }

    @Test
    fun `addXp levels up and returns gained levels`() {
        val p = HeroProgress("TEST")
        assertEquals(1, p.level)
        val gained = p.addXp(HeroProgress.xpForLevel(1) + HeroProgress.xpForLevel(2))
        assertEquals(2, gained)
        assertEquals(3, p.level)
    }

    @Test
    fun `hero stops leveling at max`() {
        val p = HeroProgress("TEST")
        p.level = HeroProgress.MAX_LEVEL
        assertEquals(0, p.addXp(10_000_000))
        assertEquals(HeroProgress.MAX_LEVEL, p.level)
    }

    @Test
    fun `statScale grows with level`() {
        val p = HeroProgress("TEST")
        assertEquals(1f, p.statScale(), 0.0001f)
        p.level = 21
        assertEquals(1.9f, p.statScale(), 0.0001f)
    }

    @Test
    fun `hero progress json roundtrip`() {
        val p = HeroProgress("KAEL", unlocked = true, level = 7, xp = 123L, shards = 4)
        val restored = HeroProgress.fromJson(p.toJson())
        assertEquals("KAEL", restored.id)
        assertTrue(restored.unlocked)
        assertEquals(7, restored.level)
        assertEquals(123L, restored.xp)
        assertEquals(4, restored.shards)
    }

    @Test
    fun `hero progress json clamps invalid level`() {
        val json = JSONObject().put("id", "X").put("level", 9999)
        val restored = HeroProgress.fromJson(json)
        assertEquals(HeroProgress.MAX_LEVEL, restored.level)
    }

    /* ---------------- inventory ---------------- */

    @Test
    fun `inventory add and consume`() {
        val inv = InventoryManager()
        inv.add("shard_ARIC", 5)
        assertEquals(5, inv.count("shard_ARIC"))
        assertTrue(inv.consume("shard_ARIC", 3))
        assertEquals(2, inv.count("shard_ARIC"))
        assertFalse(inv.consume("shard_ARIC", 3))
    }

    @Test
    fun `inventory rejects non-positive amounts`() {
        val inv = InventoryManager()
        inv.add("x", 0)
        inv.add("x", -5)
        assertEquals(0, inv.count("x"))
        assertFalse(inv.consume("x", 0))
    }

    @Test
    fun `equip only owned matching skins`() {
        val inv = InventoryManager()
        inv.grantSkin("aric_royal")
        assertTrue(inv.equipSkin("ARIC", "aric_royal"))
        assertFalse(inv.equipSkin("KAEL", "aric_royal"))  // wrong hero
        assertFalse(inv.equipSkin("ARIC", "aric_void"))   // not owned
        assertEquals("aric_royal", inv.equippedSkinFor("ARIC").id)
    }

    @Test
    fun `unequipped hero falls back to default skin`() {
        val inv = InventoryManager()
        assertEquals(Skins.DEFAULT.id, inv.equippedSkinFor("ARIC").id)
    }

    @Test
    fun `inventory json roundtrip keeps skins and loadout`() {
        val inv = InventoryManager()
        inv.add("relic", 2)
        inv.grantSkin("aric_royal")
        inv.equipSkin("ARIC", "aric_royal")
        inv.equipSlot("trinket", "relic")
        val restored = InventoryManager()
        restored.fromJson(inv.toJson())
        assertEquals(2, restored.count("relic"))
        assertTrue(restored.ownsSkin("aric_royal"))
        assertEquals("aric_royal", restored.equippedSkinFor("ARIC").id)
        assertEquals("relic", restored.loadout["trinket"])
    }

    /* ---------------- tower upgrades ---------------- */

    @Test
    fun `tower upgrade costs grow with level`() {
        var prev = 0
        for (level in 0 until TowerUpgrades.MAX_LEVEL) {
            val cost = TowerUpgrades.cost(TowerUpgrades.Kind.MAX_HP, level)
            assertTrue(cost >= prev)
            prev = cost
        }
        assertTrue(prev > TowerUpgrades.Kind.MAX_HP.baseCost)
    }

    @Test
    fun `tower effect scales linearly`() {
        assertEquals(0f, TowerUpgrades.effect(TowerUpgrades.Kind.REGEN, 0), 0.0001f)
        assertEquals(TowerUpgrades.Kind.REGEN.perLevel * 5,
            TowerUpgrades.effect(TowerUpgrades.Kind.REGEN, 5), 0.0001f)
    }

    /* ---------------- player profile ---------------- */

    @Test
    fun `profile starts with two unlocked heroes`() {
        val profile = PlayerProfile()
        assertEquals(8, profile.heroes.size)
        assertTrue(profile.heroProgress("ARIC").unlocked)
        assertTrue(profile.heroProgress("KAEL").unlocked)
        assertFalse(profile.heroProgress("NYX").unlocked)
    }

    @Test
    fun `onRunEnded grants rewards and tracks records`() {
        val profile = PlayerProfile()
        profile.economy.grant(0)
        val summary = profile.onRunEnded(wave = 12, kills = 340, goldEarned = 900, heroId = "ARIC", heroXp = 1500)
        assertEquals(900L, profile.economy.balanceGold())
        assertEquals(12, profile.bestWave)
        assertEquals(1L, profile.totalRuns)
        assertEquals(340L, profile.totalKills)
        assertTrue(summary.newBestWave)
        assertEquals(1, summary.shardsGranted)   // wave 12 => 12/10 = 1 shard
    }

    @Test
    fun `second weaker run does not lower best wave`() {
        val profile = PlayerProfile()
        profile.onRunEnded(wave = 20, kills = 10, goldEarned = 0, heroId = "ARIC", heroXp = 0)
        val summary = profile.onRunEnded(wave = 5, kills = 10, goldEarned = 0, heroId = "ARIC", heroXp = 0)
        assertEquals(20, profile.bestWave)
        assertFalse(summary.newBestWave)
    }

    @Test
    fun `unlock hero consumes shards`() {
        val profile = PlayerProfile()
        val shardId = "shard_NYX"
        profile.inventory.add(shardId, HeroProgress.UNLOCK_SHARDS)
        assertTrue(profile.unlockHero("NYX"))
        assertTrue(profile.heroProgress("NYX").unlocked)
        assertEquals(0, profile.inventory.count(shardId))
        assertFalse(profile.unlockHero("NYX"))  // already unlocked
    }

    @Test
    fun `unlock hero with gold validates balance`() {
        val profile = PlayerProfile()
        assertFalse(profile.unlockHeroWithGold("VOLT"))
        profile.economy.grant(HeroProgress.UNLOCK_GOLD)
        assertTrue(profile.unlockHeroWithGold("VOLT"))
        assertEquals(0L, profile.economy.balanceGold())
    }

    @Test
    fun `purchase tower upgrade charges and levels`() {
        val profile = PlayerProfile()
        val kind = TowerUpgrades.Kind.SHIELD
        profile.economy.grant(10_000)
        assertTrue(profile.purchaseTowerUpgrade(kind))
        assertEquals(1, profile.towerLevels[kind])
        assertTrue(profile.economy.balanceGold() < 10_000L)
    }

    @Test
    fun `purchase skin requires wave and gold`() {
        val profile = PlayerProfile()
        val skin = Skins.all.first { it.unlockGold > 0 && it.unlockWave == 0 }
        assertFalse(profile.purchaseSkin(skin))  // no gold
        profile.economy.grant(skin.unlockGold.toLong())
        assertTrue(profile.purchaseSkin(skin))
        assertTrue(profile.inventory.ownsSkin(skin.id))
        assertFalse(profile.purchaseSkin(skin))  // already owned
    }

    @Test
    fun `wave-gated skin requires best wave`() {
        val profile = PlayerProfile()
        val skin = Skins.all.first { it.unlockWave > 0 }
        profile.economy.grant(1_000_000)
        assertFalse(profile.purchaseSkin(skin))
        profile.bestWave = skin.unlockWave
        assertTrue(profile.purchaseSkin(skin))
    }

    @Test
    fun `profile json roundtrip`() {
        val profile = PlayerProfile()
        profile.economy.grant(555, 3)
        profile.inventory.add("shard_VOLT", 9)
        profile.bestWave = 14
        profile.totalKills = 900
        profile.selectedHero = "LYRA"
        profile.tutorialDone = true
        val restored = PlayerProfile()
        restored.fromJson(profile.toJson())
        assertEquals(555L, restored.economy.balanceGold())
        assertEquals(3L, restored.economy.balanceGems())
        assertEquals(9, restored.inventory.count("shard_VOLT"))
        assertEquals(14, restored.bestWave)
        assertEquals(900L, restored.totalKills)
        assertEquals("LYRA", restored.selectedHero)
        assertTrue(restored.tutorialDone)
        assertTrue(restored.heroProgress("ARIC").unlocked)
    }
}
