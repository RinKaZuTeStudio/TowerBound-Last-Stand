package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.audio.AudioDirector
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.haptics.Haptics
import com.towerbound.laststand.meta.GameSettings
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Settings screen (design: SETTINGS + ACCESSIBILITY): audio volumes & mute,
 * haptics, graphics quality, accessibility toggles (shake/vfx/flash/
 * contrast/colorblind, UI scale), language, controls (joystick size,
 * auto-attack) and tutorial replay. Changes apply live and persist to
 * SharedPreferences.
 */
class SettingsScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()
    private val settings = GameSettings.get()

    init {
        applyBackdrop(Color.parseColor("#070A18"), Color.parseColor("#111834"), Color.parseColor("#070A18"))

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 18f), UiKit.dp(context, 26f), UiKit.dp(context, 12f))

        root.addView(UiKit.title(context, context.getString(R.string.settings_title), 30f))

        /* ---------------- audio ---------------- */
        val audio = Widgets.panel(context)
        audio.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_audio)))
        audio.addView(Widgets.sliderRow(context, context.getString(R.string.settings_music), settings.musicVolume) {
            settings.musicVolume = it
            AudioDirector.applySettings(settings)
            persist()
        })
        audio.addView(Widgets.sliderRow(context, context.getString(R.string.settings_sfx), settings.sfxVolume) {
            settings.sfxVolume = it
            AudioDirector.applySettings(settings)
            persist()
        })
        audio.addView(Widgets.toggleRow(context, context.getString(R.string.settings_mute), settings.muted) {
            settings.muted = it
            AudioDirector.applySettings(settings)
            persist()
        })
        audio.addView(Widgets.toggleRow(context, context.getString(R.string.settings_haptics), settings.hapticsEnabled) {
            settings.hapticsEnabled = it
            Haptics.enabled = it
            persist()
        })
        root.addView(audio, sectionParams())

        /* ---------------- graphics ---------------- */
        val gfx = Widgets.panel(context)
        gfx.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_graphics)))
        val qualityRow = LinearLayout(context)
        qualityRow.orientation = LinearLayout.VERTICAL
        qualityRow.addView(UiKit.text(context, context.getString(R.string.settings_quality), 14f, UiKit.cText, bold = false).apply {
            gravity = Gravity.START
        })
        qualityRow.addView(Widgets.choiceRow(context, listOf(
            context.getString(R.string.quality_low),
            context.getString(R.string.quality_medium),
            context.getString(R.string.quality_high),
            context.getString(R.string.quality_auto)
        ), settings.quality) { pick ->
            settings.quality = pick
            com.towerbound.laststand.perf.PerformanceManager.applyQuality(settings, pick)
            persist()
        })
        gfx.addView(qualityRow)
        gfx.addView(Widgets.toggleRow(context, context.getString(R.string.settings_debug), settings.showPerfOverlay) {
            settings.showPerfOverlay = it
            com.towerbound.laststand.perf.PerformanceManager.showOverlay = it
            persist()
        })
        root.addView(gfx, sectionParams())

        /* ---------------- accessibility ---------------- */
        val access = Widgets.panel(context)
        access.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_accessibility)))
        access.addView(Widgets.sliderRow(context, context.getString(R.string.settings_ui_scale), (settings.uiScale - 0.8f) / 0.5f) { frac ->
            settings.uiScale = 0.8f + frac.coerceIn(0f, 1f) * 0.5f
            persist()
        })
        access.addView(Widgets.toggleRow(context, context.getString(R.string.settings_reduce_shake), settings.reduceShake) {
            settings.reduceShake = it; persist()
        })
        access.addView(Widgets.toggleRow(context, context.getString(R.string.settings_reduce_vfx), settings.reduceVfx) {
            settings.reduceVfx = it
            com.towerbound.laststand.perf.PerformanceManager.applyVfxReduction(it)
            persist()
        })
        access.addView(Widgets.toggleRow(context, context.getString(R.string.settings_reduce_flash), settings.reduceFlash) {
            settings.reduceFlash = it; persist()
        })
        access.addView(Widgets.toggleRow(context, context.getString(R.string.settings_high_contrast), settings.highContrast) {
            settings.highContrast = it; persist()
        })
        access.addView(Widgets.toggleRow(context, context.getString(R.string.settings_colorblind), settings.colorblindMode) {
            settings.colorblindMode = it; persist()
        })
        root.addView(access, sectionParams())

        /* ---------------- language ---------------- */
        val lang = Widgets.panel(context)
        lang.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_language)))
        lang.addView(Widgets.choiceRow(context, listOf(
            context.getString(R.string.lang_system),
            context.getString(R.string.lang_english),
            context.getString(R.string.lang_arabic)
        ), settings.language) { pick ->
            settings.language = pick
            persist()
            (context as? MainActivity)?.applyLanguage(pick)
        })
        root.addView(lang, sectionParams())

        /* ---------------- controls ---------------- */
        val controls = Widgets.panel(context)
        controls.addView(Widgets.sectionHeader(context, "Controls"))
        controls.addView(Widgets.sliderRow(context, context.getString(R.string.settings_joystick), (settings.joystickScale - 0.8f) / 0.6f) { frac ->
            settings.joystickScale = 0.8f + frac.coerceIn(0f, 1f) * 0.6f
            persist()
        })
        controls.addView(Widgets.toggleRow(context, context.getString(R.string.settings_auto_attack), settings.autoAttack) {
            settings.autoAttack = it; persist()
        })
        root.addView(controls, sectionParams())

        /* ---------------- misc ---------------- */
        val misc = Widgets.panel(context)
        misc.addView(Widgets.sectionHeader(context, "Game"))
        val tutorial = GameButton(context, context.getString(R.string.settings_tutorial_replay), GameButton.Style.SECONDARY)
        tutorial.onButtonClick = {
            (context as? MainActivity)?.screenManager?.push(TutorialOverlay(context, onDone = {
                (context as? MainActivity)?.screenManager?.pop(false)
            }))
        }
        misc.addView(tutorial, LinearLayout.LayoutParams(UiKit.dp(context, 220f), UiKit.dp(context, 46f)).apply {
            topMargin = UiKit.dp(context, 6f)
        })
        root.addView(misc, sectionParams())

        val close = GameButton(context, context.getString(R.string.close), GameButton.Style.SECONDARY)
        close.onButtonClick = {
            (context as? MainActivity)?.screenManager?.pop(false)
        }
        root.addView(close, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 48f)).apply {
            topMargin = UiKit.dp(context, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        addView(Widgets.scroll(context, root), UiKit.params(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun persist() {
        val ctx = context.applicationContext
        ctx.getSharedPreferences("towerbound_settings", Context.MODE_PRIVATE).edit().apply {
            putFloat("musicVolume", settings.musicVolume)
            putFloat("sfxVolume", settings.sfxVolume)
            putBoolean("muted", settings.muted)
            putBoolean("hapticsEnabled", settings.hapticsEnabled)
            putInt("quality", settings.quality)
            putBoolean("showPerfOverlay", settings.showPerfOverlay)
            putFloat("uiScale", settings.uiScale)
            putBoolean("reduceShake", settings.reduceShake)
            putBoolean("reduceVfx", settings.reduceVfx)
            putBoolean("reduceFlash", settings.reduceFlash)
            putBoolean("highContrast", settings.highContrast)
            putBoolean("colorblindMode", settings.colorblindMode)
            putInt("language", settings.language)
            putFloat("joystickScale", settings.joystickScale)
            putBoolean("autoAttack", settings.autoAttack)
        }.apply()
    }

    private fun sectionParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UiKit.dp(context, 12f)
        return lp
    }

    override fun onBack(): Boolean {
        (context as? MainActivity)?.screenManager?.pop(false)
        return true
    }
}
