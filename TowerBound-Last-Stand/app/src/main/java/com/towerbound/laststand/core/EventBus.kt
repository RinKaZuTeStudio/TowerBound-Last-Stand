package com.towerbound.laststand.core

/**
 * Lightweight synchronous event bus. The game is single-threaded (logic runs
 * on the render thread inside the engine tick), so listener lists are simply
 * guarded against concurrent UI-thread mutation.
 *
 * Events are small data classes. Dispatch targets the exact event class.
 */
class EventBus {

    private val map = HashMap<Class<*>, MutableList<(GameEvent) -> Unit>>()

    fun <T : GameEvent> subscribe(clazz: Class<T>, handler: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val boxed = handler as (GameEvent) -> Unit
        synchronized(map) { map.getOrPut(clazz) { mutableListOf() }.add(boxed) }
    }

    fun <T : GameEvent> unsubscribeAll(clazz: Class<T>) {
        synchronized(map) { map.remove(clazz) }
    }

    /** Post an event to all subscribers of its exact class. */
    fun post(event: GameEvent) {
        val list: List<(GameEvent) -> Unit>
        synchronized(map) {
            list = map[event.javaClass]?.toList() ?: return
        }
        for (handler in list) {
            try {
                handler(event)
            } catch (t: Throwable) {
                // A failing subscriber must never break the game loop.
                GameLog.e("EventBus", "handler failed for ${event.javaClass.simpleName}", t)
            }
        }
    }

    fun clear() = synchronized(map) { map.clear() }
}

/** Marker interface for all game events. */
interface GameEvent

/* ---------- Global event declarations ---------- */

data class StateChangedEvent(val from: GameState, val to: GameState) : GameEvent

/** Wave lifecycle */
data class WaveStartedEvent(val wave: Int, val isElite: Boolean, val isBoss: Boolean) : GameEvent
data class WaveClearedEvent(val wave: Int) : GameEvent

/** Combat */
data class EnemyKilledEvent(
    val enemyType: String,
    val x: Float,
    val y: Float,
    val isBoss: Boolean,
    val isElite: Boolean
) : GameEvent

data class DamageDealtEvent(val amount: Float, val isCrit: Boolean, val source: String) : GameEvent
data class TowerDamagedEvent(val hpFraction: Float) : GameEvent
data class PlayerDamagedEvent(val amount: Float) : GameEvent
data class LevelUpEvent(val newLevel: Int) : GameEvent
data class UpgradeChosenEvent(val upgradeId: String) : GameEvent
data class AbilityUsedEvent(val heroId: String, val abilityId: String, val isUltimate: Boolean) : GameEvent
data class BossDefeatedEvent(val bossId: String, val wave: Int) : GameEvent
data class GoldCollectedEvent(val amount: Int) : GameEvent
data class RunEndedEvent(val wave: Int, val kills: Int, val damage: Float, val gold: Int, val xp: Float) : GameEvent

/** Meta */
data class PurchaseCompletedEvent(val itemId: String, val cost: Int) : GameEvent
data class QuestProgressEvent(val questId: String, val progress: Int, val target: Int) : GameEvent
data class AchievementUnlockedEvent(val achievementId: String) : GameEvent
data class HeroUnlockedEvent(val heroId: String) : GameEvent
data class SkinEquippedEvent(val skinId: String) : GameEvent
data class TutorialStepEvent(val stepId: String) : GameEvent
