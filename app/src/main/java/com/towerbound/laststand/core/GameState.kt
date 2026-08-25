package com.towerbound.laststand.core

/**
 * Global game states for TOWERBOUND: LAST STAND.
 *
 * The startup chain mandated by the design document:
 * BOOT -> SPLASH -> LOADING -> MAIN_MENU -> HERO_SELECT -> LOADING_GAME -> GAMEPLAY
 *
 * In-run states: GAMEPLAY <-> LEVEL_UP / BOSS_FIGHT / PAUSED, then GAME_OVER -> RESULTS.
 */
enum class GameState {
    BOOT,
    SPLASH,
    LOADING,
    MAIN_MENU,
    HERO_SELECT,
    LOADING_GAME,
    GAMEPLAY,
    LEVEL_UP,
    BOSS_FIGHT,
    PAUSED,
    GAME_OVER,
    RESULTS;

    /** True when the world simulation should tick. */
    val simulates: Boolean
        get() = this == GAMEPLAY || this == BOSS_FIGHT

    /** True when the player may interact with gameplay HUD controls. */
    val playerInput: Boolean
        get() = this == GAMEPLAY || this == BOSS_FIGHT || this == LEVEL_UP

    /** True when a run is active (resumable). */
    val inRun: Boolean
        get() = this == GAMEPLAY || this == BOSS_FIGHT || this == LEVEL_UP ||
            this == PAUSED || this == LOADING_GAME
}

/**
 * Valid state transition table. Anything not listed is illegal and will be
 * rejected by [StateManager] (defensive against logic bugs).
 */
object GameStateTransitions {
    private val allowed: Map<GameState, Set<GameState>> = mapOf(
        GameState.BOOT to setOf(GameState.SPLASH),
        GameState.SPLASH to setOf(GameState.LOADING),
        GameState.LOADING to setOf(GameState.MAIN_MENU),
        GameState.MAIN_MENU to setOf(
            GameState.HERO_SELECT, GameState.LOADING_GAME, GameState.GAME_OVER, GameState.BOOT
        ),
        GameState.HERO_SELECT to setOf(
            GameState.MAIN_MENU, GameState.LOADING_GAME, GameState.HERO_SELECT
        ),
        GameState.LOADING_GAME to setOf(GameState.GAMEPLAY, GameState.MAIN_MENU),
        GameState.GAMEPLAY to setOf(
            GameState.LEVEL_UP, GameState.PAUSED, GameState.GAME_OVER, GameState.BOSS_FIGHT
        ),
        GameState.LEVEL_UP to setOf(GameState.GAMEPLAY, GameState.PAUSED),
        GameState.BOSS_FIGHT to setOf(
            GameState.GAMEPLAY, GameState.LEVEL_UP, GameState.PAUSED, GameState.GAME_OVER
        ),
        GameState.PAUSED to setOf(
            GameState.GAMEPLAY, GameState.BOSS_FIGHT, GameState.GAME_OVER, GameState.MAIN_MENU,
            GameState.LEVEL_UP
        ),
        GameState.GAME_OVER to setOf(GameState.RESULTS, GameState.LOADING_GAME, GameState.MAIN_MENU),
        GameState.RESULTS to setOf(GameState.LOADING_GAME, GameState.MAIN_MENU, GameState.HERO_SELECT),
    )

    fun isAllowed(from: GameState, to: GameState): Boolean =
        if (from == to) true else allowed[from]?.contains(to) == true
}

/**
 * State machine core. Pure Kotlin (no Android imports) so it is unit-testable
 * on the JVM. Interested systems register as [Listener] and receive enter/exit
 * callbacks. All callbacks fire on the main thread because [StateManager] is
 * only mutated from the game/engine thread.
 */
class StateManager {

    interface Listener {
        fun onStateEnter(state: GameState) {}
        fun onStateExit(state: GameState) {}
        /** Called for every rejected transition attempt — used by debug tooling. */
        fun onIllegalTransition(from: GameState, to: GameState) {}
    }

    @Volatile
    var current: GameState = GameState.BOOT
        private set

    private val listeners = mutableListOf<Listener>()
    private val history = ArrayDeque<GameState>()

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Attempt a state change. Returns true when the transition happened.
     * Illegal transitions are logged and ignored — the game never crashes on
     * a state machine bug, it stays in a consistent state instead.
     */
    fun transition(to: GameState): Boolean {
        val from = current
        if (from == to) return true
        if (!GameStateTransitions.isAllowed(from, to)) {
            for (l in listeners) l.onIllegalTransition(from, to)
            GameLog.w("StateManager", "Rejected illegal transition $from -> $to")
            return false
        }
        history.addLast(from)
        if (history.size > 64) history.removeFirst()
        current = to
        for (l in listeners) l.onStateExit(from)
        for (l in listeners) l.onStateEnter(to)
        GameLog.i("StateManager", "$from -> $to")
        return true
    }

    /** Jump straight back to the previous state (pause <-> gameplay convenience). */
    fun returnToPrevious(): Boolean {
        val prev = history.removeLastOrNull() ?: return false
        val from = current
        if (!GameStateTransitions.isAllowed(from, prev)) {
            history.addLast(prev)
            return false
        }
        current = prev
        for (l in listeners) l.onStateExit(from)
        for (l in listeners) l.onStateEnter(prev)
        GameLog.i("StateManager", "$from -> $prev (previous)")
        return true
    }

    /**
     * Hard reset used by crash recovery: forces state without validation.
     * Only ever called from recovery paths where the machine must be
     * re-anchored to a known-good state.
     */
    fun forceState(to: GameState) {
        val from = current
        if (from == to) return
        current = to
        for (l in listeners) l.onStateExit(from)
        for (l in listeners) l.onStateEnter(to)
        GameLog.w("StateManager", "FORCED $from -> $to")
    }

    fun clearHistory() = history.clear()

    /** Test/debug helper. */
    fun previousState(): GameState? = history.lastOrNull()
}
