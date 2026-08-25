package com.towerbound.laststand.perf

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.meta.GameSettings

/**
 * Dynamic quality governor + performance overlay (design: PERFORMANCE).
 *
 * Quality tiers (LOW / MEDIUM / HIGH / AUTO):
 *  - LOW:    particle spawn 0.45x, camera shake 0.5x
 *  - MEDIUM: particle spawn 0.75x, camera shake 0.8x
 *  - HIGH:   everything at full fidelity
 *  - AUTO:   starts HIGH; drops a tier after sustained under-target FPS and
 *            climbs back after sustained recovery (hysteresis prevents flapping)
 *
 * The governor reads GameEngine.fps (updated by the engine every 0.5 s) from
 * the HUD update hook — no extra threads.
 */
object PerformanceManager {

    const val QUALITY_LOW = 0
    const val QUALITY_MEDIUM = 1
    const val QUALITY_HIGH = 2
    const val QUALITY_AUTO = 3

    /** Effective runtime tier (never AUTO — that resolves to a concrete tier). */
    @Volatile var effectiveTier: Int = QUALITY_HIGH
        private set

    @Volatile var showOverlay: Boolean = false

    /** Target frame rate the AUTO governor defends. */
    private const val TARGET_FPS = 58f
    private const val DOWNGRADE_FPS = 48f
    private const val UPGRADE_FPS = 59f
    private const val SAMPLES_TO_ACT = 6      // 0.5s samples => 3s sustained

    private var userQuality = QUALITY_AUTO
    private var belowStreak = 0
    private var aboveStreak = 0
    private var overlayView: TextView? = null

    /* ---------------- configuration ---------------- */

    fun applyQuality(settings: GameSettings, pick: Int) {
        userQuality = pick.coerceIn(QUALITY_LOW, QUALITY_AUTO)
        applyTier(if (userQuality == QUALITY_AUTO) QUALITY_HIGH else userQuality, settings)
        belowStreak = 0
        aboveStreak = 0
        GameLog.i("Perf", "quality -> ${qualityName(effectiveTier)}")
    }

    fun applyVfxReduction(reduced: Boolean) {
        GameEngine.get().let { engine ->
            (engine.system<com.towerbound.laststand.fx.ParticleManager>())?.reducedVfx = reduced
        }
    }

    private fun qualityName(tier: Int): String = when (tier) {
        QUALITY_LOW -> "LOW"
        QUALITY_MEDIUM -> "MEDIUM"
        else -> "HIGH"
    }

    private fun applyTier(tier: Int, settings: GameSettings) {
        effectiveTier = tier
        val engine = GameEngine.get()
        val particles = engine.system<com.towerbound.laststand.fx.ParticleManager>()
        particles?.spawnScale = when (tier) {
            QUALITY_LOW -> 0.45f
            QUALITY_MEDIUM -> 0.75f
            else -> 1f
        }
        particles?.reducedVfx = settings.reduceVfx || tier == QUALITY_LOW
        engine.cameraRef?.shakeScale = when (tier) {
            QUALITY_LOW -> 0.5f
            QUALITY_MEDIUM -> 0.8f
            else -> 1f
        } * (if (settings.reduceShake) 0.3f else 1f)
    }

    /* ---------------- runtime governor ---------------- */

    /**
     * Called from the HUD update hook (already throttled to ~10 Hz). Feeds
     * the AUTO governor and refreshes the overlay text.
     */
    fun onHudTick(engine: GameEngine) {
        if (userQuality == QUALITY_AUTO && engine.worldActive) {
            val fps = engine.fps
            if (fps < DOWNGRADE_FPS) {
                belowStreak++
                aboveStreak = 0
                if (belowStreak >= SAMPLES_TO_ACT && effectiveTier > QUALITY_LOW) {
                    applyTier(effectiveTier - 1, GameSettings.get())
                    belowStreak = 0
                    GameLog.d("Perf", "AUTO downgraded to ${qualityName(effectiveTier)} (fps=$fps)")
                }
            } else if (fps > UPGRADE_FPS) {
                aboveStreak++
                belowStreak = 0
                if (aboveStreak >= SAMPLES_TO_ACT * 2 && effectiveTier < QUALITY_HIGH) {
                    applyTier(effectiveTier + 1, GameSettings.get())
                    aboveStreak = 0
                    GameLog.d("Perf", "AUTO upgraded to ${qualityName(effectiveTier)} (fps=$fps)")
                }
            } else {
                belowStreak = 0
                aboveStreak = 0
            }
        }
        if (showOverlay) refreshOverlay(engine)
    }

    /* ---------------- debug overlay ---------------- */

    fun ensureOverlay(activity: Activity) {
        if (overlayView?.parent != null) return
        val root = activity.findViewById<FrameLayout>(com.towerbound.laststand.R.id.hud_layer) ?: return
        val tv = overlayView ?: TextView(activity).apply {
            textSize = 11f
            setTextColor(0xFF69F0AE.toInt())
            setShadowLayer(4f, 0f, 2f, 0xFF000000.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 10, 16, 10)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = 12f
            }
        }.also { overlayView = it }
        if (tv.parent == null) {
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.START
            )
            lp.topMargin = 46
            lp.leftMargin = 12
            root.addView(tv, lp)
        }
        tv.visibility = if (showOverlay) View.VISIBLE else View.GONE
    }

    private fun refreshOverlay(engine: GameEngine) {
        val tv = overlayView ?: return
        val particles = engine.system<com.towerbound.laststand.fx.ParticleManager>()
        val enemies = engine.system<com.towerbound.laststand.enemies.EnemyManager>()
        tv.post {
            tv.text = "fps ${engine.fps.toInt()}  frame ${String.format("%.1f", engine.lastFrameMs)}ms\n" +
                "tier ${qualityName(effectiveTier)}  particles ${particles?.activeCount() ?: 0}\n" +
                "enemies ${enemies?.activeCount() ?: 0}"
        }
    }

    fun hideOverlay() {
        overlayView?.visibility = View.GONE
    }
}
