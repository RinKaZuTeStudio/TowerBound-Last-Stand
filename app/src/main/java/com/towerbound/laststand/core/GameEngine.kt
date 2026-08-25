package com.towerbound.laststand.core

import android.os.SystemClock

/**
 * Systems that participate in the fixed-timestep simulation implement this.
 * Registration order == update order.
 */
interface SimSystem {
    val systemName: String
    /** Called once when the system joins a running world. */
    fun onEnterWorld(engine: GameEngine) {}
    /** Fixed-step update (dt = GameClock.STEP). */
    fun update(engine: GameEngine, dt: Float) {}
    /** Called when leaving gameplay (not on pause — pause skips update entirely). */
    fun onExitWorld(engine: GameEngine) {}
}

/**
 * Central game orchestrator. One instance per process (owned by the app,
 * reachable via [instance]). The renderer calls [onFrame] every vsync; the
 * engine steps the simulation at a fixed 60 Hz and lets subsystems render.
 *
 * Android-dependent subsystems (GL renderer, audio, haptics, input) are
 * attached by MainActivity; the engine core itself only depends on
 * android.os.SystemClock for timing and can be swapped in tests.
 */
class GameEngine(
    val stateManager: StateManager = StateManager(),
    val eventBus: EventBus = EventBus(),
    val clock: GameClock = GameClock(),
    masterSeed: Long = System.nanoTime()
) {

    val masterRng: Rng = Rng(masterSeed)

    /** Separate RNG streams so gameplay rolls never desync meta rolls. */
    val simRng: Rng = Rng(masterRng.nextSeed())
    val lootRng: Rng = Rng(masterRng.nextSeed())
    val upgradeRng: Rng = Rng(masterRng.nextSeed())
    val eventRng: Rng = Rng(masterRng.nextSeed())

    /** Registered simulation systems, updated in order. */
    private val systems = mutableListOf<SimSystem>()
    private val systemsByName = HashMap<String, SimSystem>()

    /** Render hook set by the GL renderer (Phase 4). */
    @Volatile var frameRenderHook: ((deltaSeconds: Float) -> Unit)? = null

    /** Camera follow hook set by the activity. */
    @Volatile var cameraHook: ((x: Float, y: Float, dt: Float) -> Unit)? = null

    /** HUD refresh hook (throttled by the UI side). */
    @Volatile var hudUpdateHook: (() -> Unit)? = null

    /** Shared gameplay input state (joystick + buttons write here). */
    val input = com.towerbound.laststand.input.InputState()

    /** Combat target lookup: given a team, returns opposing combatants. */
    @Volatile var targetProvider: ((team: Int) -> List<com.towerbound.laststand.combat.Combatant>)? = null

    /** Camera reference for ambient FX (set by MainActivity). */
    @Volatile var cameraRef: com.towerbound.laststand.render.Camera? = null

    @Volatile var appPaused: Boolean = false
        private set

    /** Diagnostics for PerformanceManager (Phase 24). */
    @Volatile var lastFrameMs: Float = 0f
        private set
    @Volatile var fps: Float = 60f
        private set
    private var fpsAccumFrames = 0
    private var fpsAccumTime = 0f

    /** True while a run is being simulated. */
    var worldActive: Boolean = false
        private set

    init {
        stateManager.addListener(object : StateManager.Listener {
            override fun onStateEnter(state: GameState) {
                eventBus.post(StateChangedEvent(stateManager.previousState() ?: GameState.BOOT, state))
                if (state == GameState.GAMEPLAY && !worldActive) {
                    beginWorld()
                } else if (!state.inRun && worldActive && state != GameState.PAUSED) {
                    endWorld()
                }
            }
        })
    }

    /* ---------------- systems registry ---------------- */

    fun registerSystem(system: SimSystem) {
        if (systemsByName.containsKey(system.systemName)) return
        systems.add(system)
        systemsByName[system.systemName] = system
        if (worldActive) system.onEnterWorld(this)
        GameLog.d("Engine", "registered system '${system.systemName}'")
    }

    fun unregisterSystem(system: SimSystem) {
        systems.remove(system)
        systemsByName.remove(system.systemName)
        if (worldActive) system.onExitWorld(this)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : SimSystem> system(name: String): T? = systemsByName[name] as? T

    inline fun <reified T : SimSystem> system(): T? =
        system(T::class.java.simpleName)

    /* ---------------- world lifecycle ---------------- */

    private fun beginWorld() {
        GameLog.i("Engine", "world begin")
        worldActive = true
        clock.reset()
        for (s in systems) s.onEnterWorld(this)
    }

    private fun endWorld() {
        GameLog.i("Engine", "world end")
        for (s in systems) s.onExitWorld(this)
        worldActive = false
        clock.reset()
    }

    /* ---------------- frame entry point ---------------- */

    /**
     * Called by the GL renderer every frame. Computes real dt, steps the
     * simulation, then invokes the render hook.
     */
    fun onFrame() {
        val t0 = SystemClock.elapsedRealtime()
        val now = System.nanoTime()
        if (lastNano == 0L) lastNano = now
        val dt = ((now - lastNano) / 1_000_000_000.0).toFloat().coerceIn(0f, GameClock.MAX_FRAME)
        lastNano = now

        val steps = clock.advance(dt)
        if (steps > 0 && stateManager.current.simulates) {
            for (i in 0 until steps) {
                for (s in systems) {
                    s.update(this, clock.step())
                }
            }
        }

        frameRenderHook?.invoke(dt)

        // FPS statistics
        val frameMs = (SystemClock.elapsedRealtime() - t0).toFloat()
        lastFrameMs = frameMs
        fpsAccumFrames++
        fpsAccumTime += dt
        if (fpsAccumTime >= 0.5f) {
            fps = fpsAccumFrames / fpsAccumTime
            fpsAccumFrames = 0
            fpsAccumTime = 0f
        }
    }

    private var lastNano = 0L

    fun resetFrameTiming() { lastNano = 0L }

    /* ---------------- app lifecycle ---------------- */

    fun onAppPause() {
        appPaused = true
        clock.pause()
        GameLog.i("Engine", "app pause")
    }

    fun onAppResume() {
        appPaused = false
        clock.unpause()
        resetFrameTiming()
        GameLog.i("Engine", "app resume")
    }

    /* ---------------- run control (fleshed out in later phases) ---------------- */

    fun startRun(heroId: String, seed: Long = System.nanoTime()) {
        GameLog.i("Engine", "startRun hero=$heroId seed=$seed")
        currentRunSeed = seed
        currentHeroId = heroId
        world = com.towerbound.laststand.world.GameWorld(seed)
        towerHpFraction = 1f
        stateManager.transition(GameState.LOADING_GAME)
    }

    @Volatile var currentHeroId: String = "ARIC"
        private set
    @Volatile var currentRunSeed: Long = 0L
        private set

    /** Active battlefield; null outside a run. */
    @Volatile var world: com.towerbound.laststand.world.GameWorld? = null
        internal set

    /** Placeholder tower HP readout (replaced by TowerSystem in Phase 8). */
    @Volatile var towerHpFraction: Float = 1f


    companion object {
        @Volatile private var engineInstance: GameEngine? = null

        /** Process-wide engine. Created on first access from the main thread. */
        fun get(): GameEngine = engineInstance ?: synchronized(this) {
            engineInstance ?: GameEngine().also { engineInstance = it }
        }

        /** Test/testing helper — injects a specific instance. */
        fun setForTesting(engine: GameEngine?) {
            engineInstance = engine
        }
    }
}
