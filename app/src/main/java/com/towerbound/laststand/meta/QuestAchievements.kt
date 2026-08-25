package com.towerbound.laststand.meta

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Daily quests (design: QUESTS — Kill 50 enemies / Survive 10 waves /
 * Collect 500 gold, with progress, claim state and daily reset).
 */
class QuestManager {

    class Quest(
        val id: String,
        val titleKey: String,          // resolved against localized quest strings
        val target: Int,
        val rewardGold: Int,
        val rewardGems: Int,
        var progress: Int = 0,
        var claimed: Boolean = false
    ) {
        val complete: Boolean get() = progress >= target
        fun fraction(): Float = (progress.toFloat() / target).coerceIn(0f, 1f)
    }

    val quests = mutableListOf<Quest>()

    /** Epoch day of the current quest set. */
    var resetDay: Int = 0
        private set

    /** Listeners. */
    var onQuestCompleted: ((Quest) -> Unit)? = null

    private fun todayEpochDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    /** Build or refresh the daily quest set. */
    fun ensureDailySet() {
        val today = todayEpochDay()
        if (resetDay == today && quests.isNotEmpty()) return
        resetDay = today
        quests.clear()
        quests.add(Quest("daily_kill", "quest_kill", 50, 200, 2))
        quests.add(Quest("daily_waves", "quest_waves", 10, 150, 2))
        quests.add(Quest("daily_gold", "quest_gold", 500, 250, 3))
    }

    /** Progress events from gameplay. */
    fun onEnemyKilled(count: Int = 1) = progress("daily_kill", count)
    fun onWaveSurvived(wave: Int) = progress("daily_waves", 1)
    fun onGoldCollected(amount: Int) = progress("daily_gold", amount)

    private fun progress(questId: String, amount: Int) {
        if (amount <= 0) return
        val q = quests.firstOrNull { it.id == questId } ?: return
        if (q.claimed) return
        val wasComplete = q.complete
        q.progress += amount
        if (!wasComplete && q.complete) onQuestCompleted?.invoke(q)
    }

    /** Claim a quest reward through the economy (validates). */
    fun claim(quest: Quest, economy: EconomyManager): Boolean {
        if (!quest.complete || quest.claimed) return false
        quest.claimed = true
        economy.grant(quest.rewardGold.toLong(), quest.rewardGems.toLong())
        return true
    }

    fun allClaimed(): Boolean = quests.isNotEmpty() && quests.all { it.claimed }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("resetDay", resetDay)
        val arr = JSONArray()
        for (q in quests) {
            val jq = JSONObject()
            jq.put("id", q.id)
            jq.put("progress", q.progress)
            jq.put("claimed", q.claimed)
            arr.put(jq)
        }
        json.put("quests", arr)
        return json
    }

    fun fromJson(json: JSONObject) {
        ensureDailySet()
        resetDay = json.optInt("resetDay", 0)
        json.optJSONArray("quests")?.let { arr ->
            for (i in 0 until arr.length()) {
                val jq = arr.optJSONObject(i) ?: continue
                val q = quests.firstOrNull { it.id == jq.optString("id") } ?: continue
                q.progress = jq.optInt("progress", 0)
                q.claimed = jq.optBoolean("claimed", false)
            }
        }
    }
}

/**
 * Achievements (design: ACHIEVEMENTS — progress, unlock condition, reward,
 * claim state, progress bar UI, chest reward animation).
 */
class AchievementManager {

    class Achievement(
        val id: String,
        val titleKey: String,
        val descriptionKey: String,
        val target: Long,
        val rewardGold: Int,
        val rewardGems: Int,
        val metric: Metric
    ) {
        var progress: Long = 0
        var claimed: Boolean = false

        val complete: Boolean get() = progress >= target
        fun fraction(): Float = (progress.toFloat() / target).coerceIn(0f, 1f)
    }

    enum class Metric { KILLS, BEST_WAVE, UNLOCKED_HEROES, RUNS, BOSSES, UPGRADES_CHOSEN, GOLD_EARNED }

    val achievements = mutableListOf<AachievementHolder>()

    /** Simple id->progress map persisted; definitions are static. */
    val progressMap = LinkedHashMap<String, Long>()
    val claimedSet = mutableSetOf<String>()

    var onAchievementCompleted: ((Achievement) -> Unit)? = null

    init {
        define()
    }

    private fun define() {
        achievements.clear()
        fun add(id: String, titleKey: String, descKey: String, target: Long, gold: Int, gems: Int, metric: Metric) {
            achievements.add(Achievement(id, titleKey, descKey, target, gold, gems, metric))
        }
        add("ach_kill_100", "ach_kill_100", "ach_kill_100_desc", 100, 150, 1, Metric.KILLS)
        add("ach_kill_1000", "ach_kill_1000", "ach_kill_1000_desc", 1000, 500, 3, Metric.KILLS)
        add("ach_kill_10000", "ach_kill_10000", "ach_kill_10000_desc", 10000, 2000, 10, Metric.KILLS)
        add("ach_wave_10", "ach_wave_10", "ach_wave_10_desc", 10, 100, 1, Metric.BEST_WAVE)
        add("ach_wave_25", "ach_wave_25", "ach_wave_25_desc", 25, 300, 2, Metric.BEST_WAVE)
        add("ach_wave_50", "ach_wave_50", "ach_wave_50_desc", 50, 1000, 5, Metric.BEST_WAVE)
        add("ach_heroes_3", "ach_heroes_3", "ach_heroes_3_desc", 3, 200, 2, Metric.UNLOCKED_HEROES)
        add("ach_heroes_all", "ach_heroes_all", "ach_heroes_all_desc", 8, 1500, 10, Metric.UNLOCKED_HEROES)
        add("ach_runs_10", "ach_runs_10", "ach_runs_10_desc", 10, 120, 1, Metric.RUNS)
        add("ach_runs_50", "ach_runs_50", "ach_runs_50_desc", 50, 400, 3, Metric.RUNS)
        add("ach_boss_5", "ach_boss_5", "ach_boss_5_desc", 5, 250, 2, Metric.BOSSES)
        add("ach_boss_20", "ach_boss_20", "ach_boss_20_desc", 20, 800, 5, Metric.BOSSES)
        add("ach_upgrades_50", "ach_upgrades_50", "ach_upgrades_50_desc", 50, 180, 2, Metric.UPGRADES_CHOSEN)
        add("ach_gold_10000", "ach_gold_10000", "ach_gold_10000_desc", 10000, 350, 3, Metric.GOLD_EARNED)
    }

    /** Report a metric change; returns newly completed achievements. */
    fun report(metric: Metric, value: Long, absolute: Boolean = false): List<Achievement> {
        val newlyCompleted = mutableListOf<Achievement>()
        for (a in achievements.filter { it.metric == metric }) {
            val newProgress = if (absolute) maxOf(value, a.progress) else a.progress + value
            val wasComplete = a.progress >= a.target
            a.progress = newProgress
            if (!wasComplete && a.progress >= a.target) {
                newlyCompleted.add(a)
                onAchievementCompleted?.invoke(a)
            }
        }
        return newlyCompleted
    }

    /** Claim through the economy (validates completion + claim state). */
    fun claim(achievement: Achievement, economy: EconomyManager): Boolean {
        if (!achievement.complete || achievement.claimed) return false
        achievement.claimed = true
        claimedSet.add(achievement.id)
        economy.grant(achievement.rewardGold.toLong(), achievement.rewardGems.toLong())
        return true
    }

    fun syncFrom(profile: PlayerProfile, bossesKilled: Long, upgradesChosen: Long, goldEarnedTotal: Long) {
        report(Metric.KILLS, profile.totalKills, absolute = true)
        report(Metric.BEST_WAVE, profile.bestWave.toLong(), absolute = true)
        report(Metric.UNLOCKED_HEROES, profile.heroes.values.count { it.unlocked }.toLong(), absolute = true)
        report(Metric.RUNS, profile.totalRuns, absolute = true)
        report(Metric.BOSSES, bossesKilled, absolute = true)
        report(Metric.UPGRADES_CHOSEN, upgradesChosen, absolute = true)
        report(Metric.GOLD_EARNED, goldEarnedTotal, absolute = true)
    }

    fun toJson(): JSONObject {
        persistState()
        val json = JSONObject()
        val progress = JSONObject()
        for ((k, v) in progressMap) progress.put(k, v)
        json.put("progress", progress)
        json.put("claimed", JSONArray(claimedSet))
        return json
    }

    fun fromJson(json: JSONObject) {
        progressMap.clear()
        claimedSet.clear()
        json.optJSONObject("progress")?.let { p ->
            val keys = p.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                progressMap[k] = p.optLong(k, 0)
            }
        }
        json.optJSONArray("claimed")?.let { arr ->
            for (i in 0 until arr.length()) claimedSet.add(arr.optString(i))
        }
        // restore static achievement state
        for (a in achievements) {
            a.progress = progressMap[a.id] ?: 0
            a.claimed = claimedSet.contains(a.id)
        }
    }

    fun persistState() {
        progressMap.clear()
        for (a in achievements) progressMap[a.id] = a.progress
    }
}

/** Alias holder to keep constructor name stable. */
typealias AachievementHolder = AchievementManager.Achievement
