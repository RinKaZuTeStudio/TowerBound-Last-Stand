package com.towerbound.laststand.waves

import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem
import com.towerbound.laststand.enemies.EnemyManager
import com.towerbound.laststand.gameplay.PlayerController
import com.towerbound.laststand.tower.TowerSystem

/**
 * Wave events (design: special events SWARM / SIEGE / TREASURE GOBLIN /
 * DOUBLE XP).
 */
enum class WaveEvent { NONE, SWARM, SIEGE, TREASURE, DOUBLE_XP }

/**
 * Endless wave system.
 *
 *  - difficulty scales per wave (HP/damage/count budgets)
 *  - every 5th wave: ELITE WAVE (elite modifiers + champion spawns)
 *  - every 10th wave: BOSS WAVE (boss system phase consumes this)
 *  - random special events between milestones
 *  - soft-lock protection: waves always complete when all spawns are
 *    exhausted and the field drains below the completion threshold
 */
class WaveSystem : SimSystem {

    override val systemName: String = "WaveSystem"

    companion object {
        const val WAVE_BASE_COUNT = 8
        const val WAVE_COUNT_GROWTH = 1.35f      // exponential-ish budget growth
        const val HP_SCALE_PER_WAVE = 0.13f
        const val DMG_SCALE_PER_WAVE = 0.09f
        const val SPAWN_INTERVAL_START = 0.9f
        const val INTERMISSION_TIME = 4f
        const val MAX_ALIVE_SOFT_CAP = 90        // staged spawning pressure
    }

    var currentWave: Int = 0
        private set
    var waveActive: Boolean = false
        private set
    var elapsedWaveTime: Float = 0f
        private set
    var intermissionTimer: Float = 2f
        private set
    var specialEvent: WaveEvent = WaveEvent.NONE
        private set

    /** Enemies remaining to spawn this wave. */
    private var pendingSpawns = 0
    private var spawnTimer: Float = 0f
    private var hpScale = 1f
    private var dmgScale = 1f
    private var spawnInterval = SPAWN_INTERVAL_START

    /** Composition weights shift with wave number. */
    private val composition = mutableListOf<String>()

    private val rng = Rng(System.nanoTime())

    /** Listener for wave banners (UI) + bus events. */
    var onWaveStarted: ((wave: Int, isElite: Boolean, isBoss: Boolean, event: WaveEvent) -> Unit)? = null
    var onWaveCleared: ((wave: Int) -> Unit)? = null

    private var enemiesRef: EnemyManager? = null
    private var towerRef: TowerSystem? = null

    /** Resume offset: skip earlier waves after crash recovery (phase 22). */
    var resumeFromWave: Int = 0

    override fun onEnterWorld(engine: GameEngine) {
        currentWave = 0
        waveActive = false
        intermissionTimer = 2.5f
        pendingSpawns = 0
        hpScale = 1f
        dmgScale = 1f
        specialEvent = WaveEvent.NONE
        enemiesRef = engine.system<EnemyManager>()
        towerRef = engine.system<TowerSystem>()
    }

    override fun onExitWorld(engine: GameEngine) {
        currentWave = 0
        waveActive = false
        pendingSpawns = 0
    }

    val isEliteWave: Boolean get() = currentWave % 5 == 0 && currentWave % 10 != 0 && currentWave > 0
    val isBossWave: Boolean get() = currentWave % 10 == 0 && currentWave > 0

    override fun update(engine: GameEngine, dt: Float) {
        val mgr = enemiesRef ?: engine.system<EnemyManager>()?.also { enemiesRef = it } ?: return
        val tower = towerRef ?: engine.system<TowerSystem>()?.also { towerRef = it } ?: return
        if (!engine.stateManager.current.simulates) return

        if (!waveActive) {
            intermissionTimer -= dt
            if (intermissionTimer <= 0f) {
                startWave(mgr, engine)
            }
            return
        }

        elapsedWaveTime += dt

        // staged spawning keeps the field below the soft cap
        if (pendingSpawns > 0) {
            spawnTimer -= dt
            if (spawnTimer <= 0f && mgr.activeCount() < MAX_ALIVE_SOFT_CAP) {
                spawnTimer = spawnInterval
                val defId = pickNextSpawn(engine)
                val elite = rng.chance(eliteChance())
                val player = engine.system<PlayerController>()?.player
                mgr.spawnOnRing(
                    defId, hpScale, dmgScale, elite,
                    avoidX = player?.x ?: -99999f, avoidY = player?.y ?: -99999f
                )?.let { pendingSpawns-- }
            }
        }

        // wave completion: all spawns done and field nearly drained
        if (pendingSpawns <= 0 && mgr.activeCount() <= completionThreshold()) {
            completeWave(mgr, tower)
        }
    }

    private fun eliteChance(): Float = when {
        currentWave < 5 -> 0f
        isEliteWave -> 0.22f
        currentWave >= 15 -> 0.04f
        else -> 0.02f
    }

    private fun completionThreshold(): Int = if (specialEvent == WaveEvent.SWARM) 6 else 2

    private fun startWave(mgr: EnemyManager, engine: GameEngine) {
        currentWave++
        // crash-recovery resume: jump straight to the saved wave with scaled stats
        if (resumeFromWave > 0 && currentWave == 1) {
            currentWave = resumeFromWave
            resumeFromWave = 0
        }
        waveActive = true
        elapsedWaveTime = 0f
        spawnTimer = 0.6f

        hpScale = 1f + (currentWave - 1) * HP_SCALE_PER_WAVE
        dmgScale = 1f + (currentWave - 1) * DMG_SCALE_PER_WAVE
        spawnInterval = (SPAWN_INTERVAL_START * (1f - (currentWave - 1) * 0.02f)).coerceAtLeast(0.28f)

        // special event roll (disabled on elite/boss waves)
        specialEvent = if (isEliteWave || isBossWave) WaveEvent.NONE else rollEvent()

        pendingSpawns = buildComposition().also { composition.clear(); composition.addAll(it.first) }.second

        // event pre-spawns
        when (specialEvent) {
            WaveEvent.SWARM -> pendingSpawns = (pendingSpawns * 1.6f).toInt()
            WaveEvent.SIEGE -> { /* composition handles it */ }
            WaveEvent.TREASURE -> spawnTreasureGoblin(mgr)
            WaveEvent.DOUBLE_XP -> { /* xp system reads specialEvent */ }
            WaveEvent.NONE -> {}
        }

        val isBoss = isBossWave
        if (isBoss) {
            // BossSystem owns boss spawning (phases, banner, cinematics)
            engine.system<com.towerbound.laststand.bosses.BossSystem>()?.spawnForWave(currentWave, engine)
        }

        onWaveStarted?.invoke(currentWave, isEliteWave, isBossWave, specialEvent)
        engine.eventBus.post(com.towerbound.laststand.core.WaveStartedEvent(currentWave, isEliteWave, isBossWave))
        GameLog.i("Waves", "wave $currentWave started (elite=$isEliteWave boss=$isBossWave event=$specialEvent count=$pendingSpawns)")
    }

    private fun rollEvent(): WaveEvent {
        if (currentWave < 3) return WaveEvent.NONE
        val roll = rng.nextFloat()
        return when {
            roll < 0.08f -> WaveEvent.SWARM
            roll < 0.13f -> WaveEvent.SIEGE
            roll < 0.18f -> WaveEvent.TREASURE
            roll < 0.28f -> WaveEvent.DOUBLE_XP
            else -> WaveEvent.NONE
        }
    }

    /** Build the wave spawn table; returns (list, count). */
    private fun buildComposition(): Pair<List<String>, Int> {
        val w = currentWave
        val out = mutableListOf<String>()

        // core fodder always present
        val grunts = (WAVE_BASE_COUNT + w * 1.7f).toInt()
        repeat(grunts) { out.add("GRUNT") }
        repeat((w * 0.9f).toInt()) { out.add("RUNNER") }
        repeat((w * 0.5f).toInt()) { out.add("SWARMER") }

        // unlock schedule keeps early waves readable
        if (w >= 3) repeat((w * 0.4f).toInt()) { out.add("ARCHER") }
        if (w >= 4) repeat((w / 4)) { out.add("TANK") }
        if (w >= 6) repeat((w * 0.3f).toInt()) { out.add("MAGE") }
        if (w >= 8) repeat((w / 5)) { out.add("ASSASSIN") }
        if (w >= 11) repeat((w / 7)) { out.add("SIEGE") }
        if (isEliteWave) repeat(1 + w / 10) { out.add("ELITE") }

        if (specialEvent == WaveEvent.SIEGE) {
            repeat(2 + w / 5) { out.add("SIEGE") }
            repeat(1 + w / 8) { out.add("TANK") }
        }
        if (specialEvent == WaveEvent.SWARM) {
            repeat(w * 2) { out.add("SWARMER") }
        }

        // boss waves run a lighter escort
        if (isBossWave) {
            out.clear()
            repeat(4 + w / 4) { out.add("GRUNT") }
            repeat(2 + w / 6) { out.add("ARCHER") }
            repeat(1 + w / 10) { out.add("ELITE") }
        }

        val count = out.size
        return rng.shuffled(out) to count
    }

    private fun pickNextSpawn(engine: GameEngine): String {
        if (composition.isEmpty()) return "GRUNT"
        val idx = rng.nextInt(composition.size)
        return composition[idx].also { composition.removeAt(idx) }
    }

    private fun spawnTreasureGoblin(mgr: EnemyManager) {
        // Treasure goblin: fast, flees, huge gold on death (RUNNER stats x3 gold)
        mgr.spawnOnRing("RUNNER", hpScale * 0.8f, dmgScale * 0.2f, elite = false)
        GameLog.i("Waves", "treasure goblin spawned")
    }

    private fun completeWave(mgr: EnemyManager, tower: TowerSystem) {
        waveActive = false
        intermissionTimer = INTERMISSION_TIME
        val cleared = currentWave
        onWaveCleared?.invoke(cleared)
        // repair between waves (design: repair between waves)
        tower.tower?.repairBetweenWaves(0.12f)
        GameLog.i("Waves", "wave $cleared cleared — intermission ${INTERMISSION_TIME}s")
    }

    /** Debug: force-start the next wave immediately. */
    fun debugSkipIntermission() {
        if (!waveActive) intermissionTimer = 0.01f
    }

    fun debugSetWave(wave: Int) {
        currentWave = wave.coerceAtLeast(0)
        waveActive = false
        intermissionTimer = 0.01f
    }

    /** Difficulty multiplier exposed for the HUD/debug overlay. */
    fun difficultyMultiplier(): Float = hpScale
}
