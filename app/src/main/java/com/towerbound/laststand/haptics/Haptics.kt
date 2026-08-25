package com.towerbound.laststand.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback hub (design: HAPTICS). Presets encode feel, not raw
 * waveforms; amplitude scales with importance. Honors the settings master
 * switch and degrades gracefully on devices without a vibrator.
 */
object Haptics {

    @Volatile var enabled: Boolean = true
    @Volatile var strength: Float = 1f

    private var vibrator: Vibrator? = null

    enum class Preset {
        TICK,          // UI tick (selection change)
        CLICK,         // button press
        LIGHT_HIT,     // landing a hit
        HEAVY_HIT,     // taking damage
        CRITICAL,      // crit landed
        DASH,          // dash whoosh
        ABILITY,       // ability cast
        ULTIMATE,      // ultimate unleashed
        EXPLOSION,     // tower / boss explosion
        LEVEL_UP,      // level-up fanfare
        WAVE_START,    // wave announcement
        BOSS           // boss entrance
    }

    fun initialize(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    fun fire(preset: Preset, intensity: Float = 1f) {
        if (!enabled || strength <= 0f) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val scale = (intensity * strength).coerceIn(0f, 1f)
        if (scale <= 0.01f) return

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                when (preset) {
                    Preset.TICK -> vib.vibrate(VibrationEffect.createOneShot(8, amp(60f * scale)))
                    Preset.CLICK -> vib.vibrate(VibrationEffect.createOneShot(14, amp(120f * scale)))
                    Preset.LIGHT_HIT -> vib.vibrate(VibrationEffect.createOneShot(18, amp(140f * scale)))
                    Preset.HEAVY_HIT -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 26, 30, 40), intArrayOf(0, amp(220f * scale), 0, amp(120f * scale)), -1))
                    Preset.CRITICAL -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 12, 18, 22), intArrayOf(0, amp(255f * scale), 0, amp(180f * scale)), -1))
                    Preset.DASH -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 10, 14, 16), intArrayOf(0, amp(120f * scale), 0, amp(70f * scale)), -1))
                    Preset.ABILITY -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 18, 22, 30), intArrayOf(0, amp(200f * scale), 0, amp(160f * scale)), -1))
                    Preset.ULTIMATE -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 24, 46, 24, 60), intArrayOf(0, 255, 0, amp(230f * scale), 0, amp(180f * scale)), -1))
                    Preset.EXPLOSION -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 60, 40, 90), intArrayOf(0, 255, 0, amp(150f * scale)), -1))
                    Preset.LEVEL_UP -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 16, 40, 16, 40, 24), intArrayOf(0, 160, 0, 200, 0, 255), -1))
                    Preset.WAVE_START -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 22, 60, 30), intArrayOf(0, amp(180f * scale), 0, amp(140f * scale)), -1))
                    Preset.BOSS -> vib.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 50, 60, 80, 60, 120), intArrayOf(0, 255, 0, 210, 0, 160), -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(when (preset) {
                    Preset.TICK -> 8L
                    Preset.CLICK -> 14L
                    else -> 30L
                })
            }
        } catch (_: Throwable) {
            // haptics must never crash gameplay
        }
    }

    private fun amp(v: Float): Int = v.toInt().coerceIn(1, 255)
}
