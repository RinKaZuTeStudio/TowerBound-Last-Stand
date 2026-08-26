package com.towerbound.laststand.meta

import android.content.Context
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.save.SaveManager

/**
 * Process-wide facade over all persistent meta state (design: PERSISTENT
 * PROGRESSION + DAILY QUESTS + ACHIEVEMENTS + ECONOMY). Gameplay code only
 * ever talks to this object; the save system serializes what it exposes.
 *
 * Lifecycle:
 *  - TowerboundApp initializes this on app start (background thread)
 *  - run end reports through [onRunFinished]
 *  - MainActivity persists on pause so nothing is ever lost
 */
object MetaSession {

    @Volatile var initialized: Boolean = false
        private set

    lateinit var save: SaveManager
        private set
    val profile: PlayerProfile = PlayerProfile()
    val quests: QuestManager = QuestManager()
    val achievements: AchievementManager = AchievementManager()

    /** True when the load path recovered from a corrupted primary save. */
    @Volatile var recoveredFromBackup: Boolean = false
        private set

    /** Daily free reward state (epoch day of last claim). */
    @Volatile var lastDailyRewardDay: Int = -1
        private set

    /* ---------------- lifecycle ---------------- */

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            save = SaveManager(context)
            val result = save.load()
            loadFrom(result.profile, result.quests, result.achievements)
            recoveredFromBackup = result.recoveredFromBackup
            if (result.corrupted && !result.recoveredFromBackup) {
                GameLog.w("MetaSession", "save corrupted and unrecoverable — fresh start")
            }
            quests.ensureDailySet()
            GameSettings.initialize(context)

            if (!profile.starterCurrencyGranted) {
                profile.economy.grant(500, 10)
                profile.starterCurrencyGranted = true
                GameLog.i("MetaSession", "Granted starter currency 500 gold, 10 gems")
                save.save(profile, quests, achievements)
            }

            lastDailyRewardDay = profile.lastDailyResetDay
            initialized = true
            GameLog.i("MetaSession", "initialized (bestWave=${profile.bestWave}, gold=${profile.balanceGoldSnapshot()})")
        }
    }

    /** Test hook: inject state directly. */
    fun loadFrom(p: PlayerProfile, q: QuestManager, a: AchievementManager) {
        copyProfile(p)
        copyQuests(q)
        copyAchievements(a)
    }

    private fun copyProfile(p: PlayerProfile) {
        profile.fromJson(p.toJson())
    }

    private fun copyQuests(q: QuestManager) {
        quests.fromJson(q.toJson())
    }

    private fun copyAchievements(a: AchievementManager) {
        achievements.fromJson(a.toJson())
    }

    /** Persist everything now (atomic + checksummed + backup). */
    fun persist() {
        if (!initialized) return
        profile.lastDailyResetDay = lastDailyRewardDay
        save.save(profile, quests, achievements)
    }

    /* ---------------- run integration ---------------- */

    /**
     * Run-end entry point: grants rewards, updates quests/achievements and
     * persists. Called exactly once per finished run by GameFlow.
     */
    fun onRunFinished(
        wave: Int,
        kills: Int,
        goldEarned: Int,
        heroXp: Long,
        heroId: String,
        playtimeSeconds: Long
    ): PlayerProfile.RunSummary {
        val summary = profile.onRunEnded(wave, kills, goldEarned, heroId, heroXp)

        // daily quest progress
        quests.onEnemyKilled(kills)
        quests.onWaveSurvived(wave)
        quests.onGoldCollected(goldEarned)

        // achievement metrics (absolute where the profile tracks totals)
        achievements.report(AchievementManager.Metric.KILLS, kills.toLong())
        achievements.report(AchievementManager.Metric.RUNS, profile.totalRuns, absolute = true)
        achievements.report(AchievementManager.Metric.BEST_WAVE, profile.bestWave.toLong(), absolute = true)
        achievements.report(
            AchievementManager.Metric.UNLOCKED_HEROES,
            profile.heroes.values.count { it.unlocked }.toLong(), absolute = true
        )
        achievements.report(AchievementManager.Metric.GOLD_EARNED, goldEarned.toLong())

        profile.totalPlaytimeSeconds += playtimeSeconds.coerceAtLeast(0)
        persist()
        GameLog.i("MetaSession", "run finished: wave=$wave kills=$kills gold=$goldEarned xp=$heroXp")
        return summary
    }

    /** Live mid-run kill report (achievement pacing). */
    fun onKillDuringRun() {
        achievements.report(AchievementManager.Metric.KILLS, 1)
    }

    /* ---------------- daily reward ---------------- */

    fun todayEpochDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    fun dailyRewardAvailable(): Boolean = lastDailyRewardDay != todayEpochDay()

    /**
     * Claim the daily free reward: 120 gold + 1 gem (escalating streak later).
     * Returns the granted amounts, or null when already claimed today.
     */
    fun claimDailyReward(): Triple<Int, Int, Int>? {
        val today = todayEpochDay()
        if (lastDailyRewardDay == today) return null
        val gold = 120
        val gems = 1
        val result = profile.economy.grant(gold.toLong(), gems.toLong())
        if (result is EconomyManager.Transaction.Failure) return null
        lastDailyRewardDay = today
        persist()
        return Triple(gold, gems, 0)
    }

    /* ---------------- shop helpers ---------------- */

    fun balanceGold(): Long = profile.economy.balanceGold()
    fun balanceGems(): Long = profile.economy.balanceGems()

    /** Format gold with k/M suffixes for compact HUD/shop labels. */
    fun formatAmount(v: Long): String = when {
        v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000.0)
        v >= 10_000 -> String.format("%dk", v / 1000)
        else -> v.toString()
    }
}

/** Convenience read for logging. */
private fun PlayerProfile.balanceGoldSnapshot(): Long = economy.balanceGold()
