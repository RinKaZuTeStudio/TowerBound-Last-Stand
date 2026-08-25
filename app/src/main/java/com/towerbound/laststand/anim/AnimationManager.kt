package com.towerbound.laststand.anim

import com.towerbound.laststand.assets.AnimState
import kotlin.math.floor

/**
 * Animation event types synchronized with VFX/SFX/damage/haptics (design:
 * ANIMATION EVENTS section).
 */
enum class AnimEvent {
    ON_ATTACK_START,
    ON_ATTACK_IMPACT,
    ON_CAST,
    ON_PROJECTILE_SPAWN,
    ON_HIT,
    ON_DEATH,
    ON_ABILITY_END,
    ON_BOSS_PHASE,
    ON_TOWER_DAMAGE
}

/**
 * Per-entity animation controller.
 *
 * - frame-based playback driven by fixed sim steps
 * - speed scaling
 * - smooth transitions with cross-fade timer (visual blend factor)
 * - frame-indexed events fired once per cycle (attack impact frames etc.)
 * - one-shot completion callback
 *
 * Pure Kotlin — unit tested on the JVM.
 */
class AnimationController(initial: AnimState = AnimState.IDLE) {

    var state: AnimState = initial
        private set
    var prevState: AnimState = initial
        private set

    /** Playback position in frames within the current cycle (float). */
    var time: Float = 0f
        private set

    /** Unwrapped playback position (frames since state start). */
    private var totalTime: Float = 0f

    /** Playback speed multiplier. */
    var speed: Float = 1f

    /** Seconds since the state changed — used for cross-fade blending. */
    var transitionAge: Float = 0f
        private set

    /** Number of completed loops of the current state. */
    var completedLoops: Int = 0
        private set

    /** True once a non-looping state finished. */
    val finished: Boolean
        get() = !state.loop && totalTime >= state.frameCount

    private val pendingEvents = mutableListOf<Pair<AnimEvent, Float>>() // event + normalized time
    private var externalListener: ((AnimEvent) -> Unit)? = null
    private val firedEvents = HashSet<Int>()

    /** Event map: frame index -> event. Configured per entity/ability. */
    private val events = HashMap<Int, AnimEvent>()

    private var onCompleteAction: (() -> Unit)? = null

    fun addEvent(frame: Int, event: AnimEvent) {
        events[frame] = event
    }

    fun clearEvents() = events.clear()

    fun onEvent(listener: (AnimEvent) -> Unit) { externalListener = listener }

    fun onComplete(action: (() -> Unit)?) { onCompleteAction = action }

    /**
     * Transition to [next]. Same-state calls only adjust speed unless
     * [force] restarts playback.
     */
    fun play(next: AnimState, force: Boolean = false, speed: Float = 1f) {
        if (next == state && !force) {
            this.speed = speed
            return
        }
        prevState = state
        state = next
        time = 0f
        totalTime = 0f
        transitionAge = 0f
        completedLoops = 0
        firedEvents.clear()
        this.speed = speed
        onCompleteAction = null
    }

    /** Fixed-step update. */
    fun update(dt: Float) {
        transitionAge += dt
        val prevTotal = totalTime
        totalTime += dt * state.fps * speed
        val cycleLen = state.frameCount.toFloat()

        // ---- frame events (support multi-cycle wraps in one step) ----
        if (events.isNotEmpty()) {
            val firstCycle = floor(prevTotal / cycleLen).toInt()
            val lastCycle = floor(totalTime / cycleLen).toInt()
            for ((frame, event) in events) {
                for (c in firstCycle..lastCycle) {
                    val absFrame = c * cycleLen + frame
                    if (absFrame > prevTotal && absFrame <= totalTime) {
                        val eventId = if (state.loop) c * 1000 + frame else frame
                        if (firedEvents.add(eventId)) {
                            pendingEvents.add(event to (totalTime / cycleLen.coerceAtLeast(1f)))
                            externalListener?.invoke(event)
                        }
                    }
                }
            }
        }

        // ---- playback position / completion ----
        if (state.loop) {
            completedLoops = (totalTime / cycleLen).toInt()
            time = totalTime % cycleLen
        } else {
            time = totalTime.coerceAtMost(cycleLen)
            if (totalTime >= cycleLen && firedEvents.add(-1)) {
                onCompleteAction?.invoke()
            }
        }
    }

    /** Consume and clear events fired this step. */
    fun drainEvents(): List<Pair<AnimEvent, Float>> {
        if (pendingEvents.isEmpty()) return emptyList()
        val out = pendingEvents.toList()
        pendingEvents.clear()
        return out
    }

    /** Current frame index for rendering. */
    fun frameIndex(): Int = time.toInt().coerceIn(0, state.frameCount - 1)

    /** Interpolated frame position for smooth rendering. */
    fun frameFloat(): Float = time

    /** Blend factor 0..1 for cross-fading from the previous state. */
    fun blend(): Float = (transitionAge / 0.12f).coerceIn(0f, 1f)
}
