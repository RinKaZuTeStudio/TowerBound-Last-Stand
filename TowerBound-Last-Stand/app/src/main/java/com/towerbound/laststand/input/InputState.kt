package com.towerbound.laststand.input

/**
 * Shared input state between the Android UI thread (writers: joystick,
 * buttons) and the render thread (reader: player controller). Fields are
 * volatile / atomic-safe; edge triggers use compare-and-swap consumption.
 */
class InputState {

    /** Normalized movement vector, magnitude 0..1. */
    @Volatile var moveX: Float = 0f
    @Volatile var moveY: Float = 0f

    /** Auto-attack toggle (persisted with settings later). */
    @Volatile var autoAttack: Boolean = true

    // ---- edge-triggered actions (set by UI, consumed by sim) ----
    @Volatile private var abilityQueued = false
    @Volatile private var ultimateQueued = false
    @Volatile private var dashQueued = false

    fun queueAbility() { abilityQueued = true }
    fun queueUltimate() { ultimateQueued = true }
    fun queueDash() { dashQueued = true }

    fun consumeAbility(): Boolean {
        if (abilityQueued) { abilityQueued = false; return true }
        return false
    }
    fun consumeUltimate(): Boolean {
        if (ultimateQueued) { ultimateQueued = false; return true }
        return false
    }
    fun consumeDash(): Boolean {
        if (dashQueued) { dashQueued = false; return true }
        return false
    }

    fun reset() {
        moveX = 0f; moveY = 0f
        abilityQueued = false; ultimateQueued = false; dashQueued = false
    }

    val moving: Boolean get() = kotlin.math.abs(moveX) > 0.01f || kotlin.math.abs(moveY) > 0.01f
    val magnitude: Float get() = kotlin.math.sqrt(moveX * moveX + moveY * moveY).coerceAtMost(1f)
}
