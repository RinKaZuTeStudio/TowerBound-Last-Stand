package com.towerbound.laststand.meta

import android.content.Context
import android.content.SharedPreferences

/**
 * Runtime game settings backed by SharedPreferences (design: SETTINGS /
 * ACCESSIBILITY / PERFORMANCE). Written by the settings screen, read by the
 * audio, haptics, renderer and input layers.
 */
class GameSettings private constructor(prefs: SharedPreferences) {

    /* ---------------- audio ---------------- */
    /** 0..1 music volume. */
    var musicVolume: Float = 0.6f
    /** 0..1 sfx volume. */
    var sfxVolume: Float = 0.9f
    /** Master mute switch. */
    var muted: Boolean = false
    /** Haptic feedback master switch. */
    var hapticsEnabled: Boolean = true

    /* ---------------- graphics ---------------- */
    /** 0 = LOW, 1 = MEDIUM, 2 = HIGH, 3 = AUTO (dynamic). */
    var quality: Int = 3
    /** On-screen performance overlay (fps / frame ms / draw stats). */
    var showPerfOverlay: Boolean = false

    /* ---------------- accessibility ---------------- */
    /** UI scale multiplier 0.85..1.3. */
    var uiScale: Float = 1f
    /** Reduce camera shake amplitude. */
    var reduceShake: Boolean = false
    /** Reduce particle/effect density. */
    var reduceVfx: Boolean = false
    /** Remove full-screen flashes (photosensitivity). */
    var reduceFlash: Boolean = false
    /** High contrast HUD text/outlines. */
    var highContrast: Boolean = false
    /** Colorblind-friendly status indicators (shape-coded, not color-coded). */
    var colorblindMode: Boolean = false

    /* ---------------- language ---------------- */
    /** 0 = system default, 1 = English, 2 = Arabic. */
    var language: Int = 0

    /* ---------------- controls ---------------- */
    /** Joystick size multiplier 0.8..1.4. */
    var joystickScale: Float = 1f
    /** Auto-attack enabled at run start. */
    var autoAttack: Boolean = true

    init {
        musicVolume = prefs.getFloat("musicVolume", 0.6f)
        sfxVolume = prefs.getFloat("sfxVolume", 0.9f)
        muted = prefs.getBoolean("muted", false)
        hapticsEnabled = prefs.getBoolean("hapticsEnabled", true)
        quality = prefs.getInt("quality", 3)
        showPerfOverlay = prefs.getBoolean("showPerfOverlay", false)
        uiScale = prefs.getFloat("uiScale", 1f)
        reduceShake = prefs.getBoolean("reduceShake", false)
        reduceVfx = prefs.getBoolean("reduceVfx", false)
        reduceFlash = prefs.getBoolean("reduceFlash", false)
        highContrast = prefs.getBoolean("highContrast", false)
        colorblindMode = prefs.getBoolean("colorblindMode", false)
        language = prefs.getInt("language", 0)
        joystickScale = prefs.getFloat("joystickScale", 1f)
        autoAttack = prefs.getBoolean("autoAttack", true)
    }

    companion object {
        @Volatile private var instance: GameSettings? = null

        fun get(): GameSettings =
            instance ?: throw IllegalStateException("GameSettings not initialized")

        fun initialize(context: Context): GameSettings {
            instance?.let { return it }
            synchronized(this) {
                instance ?: GameSettings(
                    context.getSharedPreferences("towerbound_settings", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
            return instance!!
        }

        /** Test hook — resets the singleton. */
        fun setForTesting(settings: GameSettings?) {
            instance = settings
        }
    }
}
