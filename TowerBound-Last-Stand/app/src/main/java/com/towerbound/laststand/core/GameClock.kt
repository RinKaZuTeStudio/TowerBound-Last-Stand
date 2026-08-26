package com.towerbound.laststand.core

/**
 * Game clock: fixed-timestep simulation with time scaling (slow motion),
 * hit-stop (micro freeze) and pause. All gameplay systems read time from the
 * clock — never from System.nanoTime directly — so global effects work.
 *
 * Pure Kotlin for unit testing.
 */
class GameClock {

    companion object {
        const val STEP_HZ = 60f
        const val STEP = 1f / STEP_HZ          // fixed simulation step (seconds)
        const val MAX_FRAME = 0.25f            // clamp huge frame gaps (app switch)
        const val MAX_STEPS_PER_FRAME = 5      // anti death-spiral
    }

    /** Elapsed unscaled real seconds since clock start. */
    var realTime: Float = 0f
        private set

    /** Elapsed scaled simulation seconds (affected by slow-mo, not by pause). */
    var simTime: Float = 0f
        private set

    /** Global time scale. 1 = normal, 0.25 = dramatic slow motion. */
    var timeScale: Float = 1f
        set(value) { field = value.coerceIn(0.05f, 4f) }

    /** Remaining hit-stop time in *real* seconds — freezes simulation ticks. */
    var hitStopRemaining: Float = 0f
        private set

    /** Active slow-motion envelope: target scale, remaining duration. */
    private var slowTarget = 1f
    private var slowRemaining = 0f
    private var slowFrom = 1f
    private var slowDuration = 0f

    private var accumulator = 0f

    val paused: Boolean
        get() = pauseDepth > 0
    private var pauseDepth = 0f

    /** Total simulated frames (diagnostics). */
    var frameCount: Long = 0
        private set

    fun pause() { pauseDepth += 1f }
    fun unpause() { pauseDepth = (pauseDepth - 1f).coerceAtLeast(0f) }
    fun forceUnpause() { pauseDepth = 0f }

    /**
     * Feed a real-time delta (seconds). Returns the number of fixed steps to
     * simulate now; each step should be consumed via [step].
     */
    fun advance(realDelta: Float): Int {
        var dt = realDelta
        if (dt > MAX_FRAME) dt = MAX_FRAME
        realTime += dt

        if (paused) {
            accumulator = 0f
            return 0
        }

        // Hit-stop freezes sim but still ticks down using real time.
        if (hitStopRemaining > 0f) {
            hitStopRemaining -= dt
            return 0
        }

        // Slow-motion envelope easing.
        if (slowRemaining > 0f) {
            slowRemaining -= dt
            val t = if (slowDuration <= 0f) 1f else (1f - (slowRemaining / slowDuration)).coerceIn(0f, 1f)
            timeScale = slowFrom + (slowTarget - slowFrom) * t
            if (slowRemaining <= 0f) timeScale = slowTarget
        }

        accumulator += dt * timeScale
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME) {
            accumulator -= STEP
            simTime += STEP
            frameCount++
            steps++
        }
        if (accumulator >= STEP) accumulator = STEP * 0.5f // drop backlog
        return steps
    }

    /** Duration (in seconds) of one fixed step. */
    fun step(): Float = STEP

    /** Trigger a micro-freeze of [seconds] real time (hit impact feel). */
    fun hitStop(seconds: Float) {
        hitStopRemaining = hitStopRemaining.coerceAtLeast(seconds.coerceIn(0f, 0.25f))
    }

    /** Ease time scale to [scale] over [duration] real seconds and hold it. */
    fun slowMotion(scale: Float, duration: Float) {
        slowFrom = timeScale
        slowTarget = scale.coerceIn(0.05f, 4f)
        slowDuration = duration.coerceAtLeast(0.01f)
        slowRemaining = slowDuration
    }

    /** Immediately restore normal speed. */
    fun clearSlowMotion() {
        slowRemaining = 0f
        slowTarget = 1f
        timeScale = 1f
    }

    fun reset() {
        realTime = 0f; simTime = 0f; accumulator = 0f
        hitStopRemaining = 0f; slowRemaining = 0f
        timeScale = 1f; slowTarget = 1f; slowFrom = 1f
        pauseDepth = 0f; frameCount = 0
    }
}
