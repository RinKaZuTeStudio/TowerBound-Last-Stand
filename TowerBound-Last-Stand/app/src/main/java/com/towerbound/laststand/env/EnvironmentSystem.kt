package com.towerbound.laststand.env

import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.Rng
import com.towerbound.laststand.core.SimSystem

/**
 * Time-of-day phases (design: TIME OF DAY — DAWN/DAY/DUSK/NIGHT/MIDNIGHT,
 * full cycle 5-8 minutes, MIDNIGHT boosts enemy aggression).
 */
enum class TimeOfDay(val label: String) {
    DAWN("Dawn"),
    DAY("Day"),
    DUSK("Dusk"),
    NIGHT("Night"),
    MIDNIGHT("Midnight")
}

/**
 * Environment system:
 *  - continuous day/night cycle (~6 min) with tint overlays
 *  - ambient particle emission (dust by day, fireflies at night)
 *  - midnight aggression buff on enemies
 */
class EnvironmentSystem : SimSystem {

    override val systemName: String = "EnvironmentSystem"

    /** Cycle position 0..1. */
    var cycleT: Float = 0.12f    // start mid-morning
        private set

    var timeOfDay: TimeOfDay = TimeOfDay.DAY
        private set

    /** Overlay tint the renderer applies over the world (RGBA 0..1). */
    val tintR: Float get() = currentTint[0]
    val tintG: Float get() = currentTint[1]
    val tintB: Float get() = currentTint[2]
    val tintA: Float get() = currentTint[3]
    private val currentTint = floatArrayOf(1f, 1f, 1f, 0f)

    /** Ambient intensity for HUD clock + lighting. */
    val brightness: Float get() = 1f - currentTint[3] * 0.8f

    /** Midnight aggression multiplier read by enemies. */
    val enemyAggressionMult: Float
        get() = if (timeOfDay == TimeOfDay.MIDNIGHT) 1.25f else 1f

    /** Ambient emission control. */
    private var ambientTimer = 0f
    private val rng = Rng(System.nanoTime())

    /** Clock indicator fraction for HUD (0..1 across the day). */
    fun clockFraction(): Float = cycleT

    override fun onEnterWorld(engine: GameEngine) {
        cycleT = 0.12f
        timeOfDay = TimeOfDay.DAY
    }

    override fun update(engine: GameEngine, dt: Float) {
        cycleT = (cycleT + dt / CYCLE_SECONDS) % 1f

        // determine phase + normalized progress within phase
        var acc = 0f
        var phase: TimeOfDay = PHASES.last().first
        var phaseStart = 0f
        var phaseEnd = 1f
        for ((p, w) in PHASES) {
            if (cycleT < acc + w) {
                phase = p
                phaseStart = acc
                phaseEnd = acc + w
                break
            }
            acc += w
        }
        if (phase != timeOfDay) {
            timeOfDay = phase
        }
        val k = ((cycleT - phaseStart) / (phaseEnd - phaseStart)).coerceIn(0f, 1f)

        // color ramps (start -> end per phase)
        val (from, to) = when (phase) {
            TimeOfDay.DAWN -> RAMP_DAWN
            TimeOfDay.DAY -> RAMP_DAY
            TimeOfDay.DUSK -> RAMP_DUSK
            TimeOfDay.NIGHT -> RAMP_NIGHT
            TimeOfDay.MIDNIGHT -> RAMP_MIDNIGHT
        }
        for (i in 0 until 4) {
            currentTint[i] = from[i] + (to[i] - from[i]) * k
        }

        // ambient particles: dust at day, fireflies at night
        val fx = engine.system<com.towerbound.laststand.fx.ParticleManager>() ?: return
        ambientTimer -= dt
        if (ambientTimer <= 0f) {
            ambientTimer = 0.5f
            val night = phase == TimeOfDay.NIGHT || phase == TimeOfDay.MIDNIGHT || phase == TimeOfDay.DUSK
            val cam = engine.cameraRef
            if (cam != null) {
                repeat(3) {
                    val x = cam.x + rng.range(-600f, 600f)
                    val y = cam.y + rng.range(-380f, 380f)
                    fx.emitAmbient(x, y, firefly = night)
                }
            }
        }
    }

    companion object {
        const val CYCLE_SECONDS = 360f          // 6 minutes, spec range 5-8 min
        val PHASES = listOf(
            TimeOfDay.DAWN to 0.06f,
            TimeOfDay.DAY to 0.34f,
            TimeOfDay.DUSK to 0.12f,
            TimeOfDay.NIGHT to 0.36f,
            TimeOfDay.MIDNIGHT to 0.12f
        )
        // [r, g, b, alpha] start/end pairs per phase
        private val RAMP_DAWN = floatArrayOf(1.0f, 0.82f, 0.66f, 0.16f) to floatArrayOf(1.0f, 0.96f, 0.88f, 0.05f)
        private val RAMP_DAY = floatArrayOf(1.0f, 0.98f, 0.94f, 0.02f) to floatArrayOf(1.0f, 0.99f, 0.96f, 0.03f)
        private val RAMP_DUSK = floatArrayOf(1.0f, 0.88f, 0.72f, 0.12f) to floatArrayOf(0.82f, 0.62f, 0.72f, 0.26f)
        private val RAMP_NIGHT = floatArrayOf(0.62f, 0.68f, 0.92f, 0.30f) to floatArrayOf(0.55f, 0.60f, 0.86f, 0.34f)
        private val RAMP_MIDNIGHT = floatArrayOf(0.48f, 0.52f, 0.84f, 0.38f) to floatArrayOf(0.44f, 0.46f, 0.80f, 0.42f)
    }
}
