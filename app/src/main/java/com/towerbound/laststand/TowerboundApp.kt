package com.towerbound.laststand

import android.app.Application
import com.towerbound.laststand.audio.AudioDirector
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.haptics.Haptics
import com.towerbound.laststand.meta.GameSettings
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.ui.UiSfx

/**
 * Application entry point. Owns process-wide singletons whose lifetime must
 * exceed any single Activity recreation (rotation, memory pressure, etc.).
 *
 * Startup order:
 *  1. meta/save load on a background thread (never blocks the UI)
 *  2. haptics + audio subsystems initialize lazily on first activity resume
 */
class TowerboundApp : Application() {

    override fun onCreate() {
        super.onCreate()
        GameLog.i("App", "Towerbound: Last Stand starting, pid=${android.os.Process.myPid()}")
        appInstance = this

        // persistent meta state (profile/quests/achievements/settings)
        Thread({
            try {
                MetaSession.initialize(this)
            } catch (t: Throwable) {
                GameLog.e("App", "meta init failed", t)
            }
        }, "MetaInit").start()

        // haptics
        Haptics.initialize(this)
        Haptics.enabled = GameSettings.initialize(this).hapticsEnabled

        // UI click sound routes through the procedural audio engine
        UiSfx.clickHandler = { AudioDirector.play(AudioDirector.Sfx.CLICK) }
    }

    companion object {
        @Volatile
        private var appInstance: TowerboundApp? = null

        /** Returns the running application instance or null very early in startup. */
        fun get(): TowerboundApp? = appInstance
    }
}
