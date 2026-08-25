package com.towerbound.laststand.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Daily quests + achievements coverage (design: QUESTS + ACHIEVEMENTS).
 */
class QuestAchievementsTest {

    /* ---------------- quests ---------------- */

    @Test
    fun `daily set contains three quests with rewards`() {
        val quests = QuestManager()
        quests.ensureDailySet()
        assertEquals(3, quests.quests.size)
        assertTrue(quests.quests.all { it.target > 0 })
        assertTrue(quests.quests.all { it.rewardGold > 0 })
    }

    @Test
    fun `quest progress accumulates and completes`() {
        val quests = QuestManager()
        quests.ensureDailySet()
        val killQuest = quests.quests.first { it.id == "daily_kill" }
        repeat(25) { quests.onEnemyKilled(2) }   // 50 total
        assertTrue(killQuest.complete)
        assertEquals(1f, killQuest.fraction(), 0.0001f)
    }

    @Test
    fun `quest progress stops after claim`() {
        val quests = QuestManager()
        quests.ensureDailySet()
        val economy = EconomyManager()
        val killQuest = quests.quests.first { it.id == "daily_kill" }
        quests.onEnemyKilled(killQuest.target)
        assertTrue(quests.claim(killQuest, economy))
        val goldAfter = economy.balanceGold()
        // further kills no longer matter
        quests.onEnemyKilled(100)
        assertFalse(quests.claim(killQuest, economy))
        assertEquals(goldAfter, economy.balanceGold())
    }

    @Test
    fun `incomplete quest cannot be claimed`() {
        val quests = QuestManager()
        quests.ensureDailySet()
        val economy = EconomyManager()
        val killQuest = quests.quests.first { it.id == "daily_kill" }
        quests.onEnemyKilled(killQuest.target - 1)
        assertFalse(quests.claim(killQuest, economy))
    }

    @Test
    fun `quest json roundtrip restores progress`() {
        val quests = QuestManager()
        quests.ensureDailySet()
        quests.onEnemyKilled(17)
        quests.onGoldCollected(220)
        val restored = QuestManager()
        restored.fromJson(quests.toJson())
        assertEquals(17, restored.quests.first { it.id == "daily_kill" }.progress)
        assertEquals(220, restored.quests.first { it.id == "daily_gold" }.progress)
    }

    /* ---------------- achievements ---------------- */

    @Test
    fun `achievements define the full catalog`() {
        val achievements = AchievementManager()
        assertEquals(14, achievements.achievements.size)
        assertTrue(achievements.achievements.all { it.target > 0 })
    }

    @Test
    fun `report accumulates relative metrics`() {
        val achievements = AchievementManager()
        achievements.report(AchievementManager.Metric.KILLS, 60)
        val first = achievements.achievements.first { it.id == "ach_kill_100" }
        assertEquals(60L, first.progress)
        achievements.report(AchievementManager.Metric.KILLS, 45)
        assertEquals(105L, first.progress)
        assertTrue(first.complete)
    }

    @Test
    fun `absolute reports never lower progress`() {
        val achievements = AchievementManager()
        achievements.report(AchievementManager.Metric.RUNS, 500, absolute = true)
        achievements.report(AchievementManager.Metric.RUNS, 3, absolute = true)
        assertEquals(500L, achievements.achievements.first { it.id == "ach_runs_50" }.progress)
    }

    @Test
    fun `claim pays through economy exactly once`() {
        val achievements = AchievementManager()
        val economy = EconomyManager()
        val ach = achievements.achievements.first { it.id == "ach_wave_10" }
        achievements.report(AchievementManager.Metric.BEST_WAVE, 10, absolute = true)
        assertTrue(ach.complete)
        assertTrue(achievements.claim(ach, economy))
        val gold = economy.balanceGold()
        val gems = economy.balanceGems()
        assertFalse(achievements.claim(ach, economy))
        assertEquals(gold, economy.balanceGold())
        assertEquals(gems, economy.balanceGems())
        assertEquals(ach.rewardGold.toLong(), gold)
        assertEquals(ach.rewardGems.toLong(), gems)
    }

    @Test
    fun `achievement json roundtrip restores progress and claims`() {
        val achievements = AchievementManager()
        achievements.report(AchievementManager.Metric.KILLS, 100, absolute = true)
        achievements.report(AchievementManager.Metric.BOSSES, 5, absolute = true)
        val ach = achievements.achievements.first { it.id == "ach_boss_5" }
        achievements.claim(ach, EconomyManager())

        val restored = AchievementManager()
        restored.fromJson(achievements.toJson())
        assertEquals(100L, restored.achievements.first { it.id == "ach_kill_100" }.progress)
        assertTrue(restored.achievements.first { it.id == "ach_boss_5" }.claimed)
        assertFalse(restored.achievements.first { it.id == "ach_kill_1000" }.complete)
    }

    @Test
    fun `syncFrom pulls profile totals`() {
        val achievements = AchievementManager()
        val profile = PlayerProfile()
        profile.bestWave = 25
        profile.totalRuns = 10
        achievements.syncFrom(profile, bossesKilled = 6, upgradesChosen = 0, goldEarnedTotal = 0)
        assertTrue(achievements.achievements.first { it.id == "ach_wave_25" }.complete)
        assertTrue(achievements.achievements.first { it.id == "ach_runs_10" }.complete)
        assertTrue(achievements.achievements.first { it.id == "ach_boss_5" }.complete)
        assertFalse(achievements.achievements.first { it.id == "ach_boss_20" }.complete)
    }
}
