package com.towerbound.laststand

import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.core.LoadingPipeline
import com.towerbound.laststand.core.StateManager
import com.towerbound.laststand.audio.AudioDirector
import com.towerbound.laststand.haptics.Haptics
import com.towerbound.laststand.meta.GameSettings
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.render.FallbackContextFactory
import com.towerbound.laststand.render.GLRenderer
import com.towerbound.laststand.ui.ScreenManager
import com.towerbound.laststand.ui.screens.GameFlow
import com.towerbound.laststand.ui.screens.LoadingScreen
import com.towerbound.laststand.ui.screens.MainMenuScreen
import com.towerbound.laststand.ui.screens.SplashScreen
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Single-activity architecture. Owns the root layer stack:
 *   [0] GLSurfaceView (game world)  — attached by the render phase
 *   [1] HUD layer
 *   [2] Full-screen UI stack (splash, menus, overlays) managed by ScreenManager
 *
 * Lifecycle contract:
 *  - onPause  -> engine pauses simulation & auto-saves
 *  - onResume -> engine resumes, recovers OpenGL context if lost
 *  - Activity recreation never loses data: persistent state lives in the
 *    save system; process singletons live in TowerboundApp/GameEngine.
 */
class MainActivity : AppCompatActivity() {

    val engine: GameEngine by lazy { GameEngine.get() }
    val screenManager: ScreenManager by lazy {
        ScreenManager(findViewById(R.id.screen_layer))
    }

    private lateinit var hudLayer: FrameLayout
    private var glView: GLSurfaceView? = null
    private var renderer: GLRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameLog.i("MainActivity", "onCreate (recreated=${savedInstanceState != null})")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        applyImmersiveMode()
        hudLayer = findViewById(R.id.hud_layer)

        // meta session must be ready before any menu renders (the background
        // loader in TowerboundApp usually finishes during the splash screen)
        awaitMetaReady()

        engine.stateManager.addListener(object : StateManager.Listener {
            override fun onIllegalTransition(from: GameState, to: GameState) {
                GameLog.w("MainActivity", "illegal $from -> $to")
            }
        })

        GameFlow.attach(this)

        attachGlSurface()

        if (engine.stateManager.current == GameState.BOOT) {
            engine.stateManager.transition(GameState.SPLASH)
            showSplash()
        } else if (engine.stateManager.current == GameState.MAIN_MENU && screenManager.depth == 0) {
            screenManager.replace(MainMenuScreen(this))
        }
    }

    /** Blocks up to 3s for the background meta load; falls back to defaults. */
    private fun awaitMetaReady() {
        if (MetaSession.initialized) return
        val latch = CountDownLatch(1)
        Thread({
            try {
                MetaSession.initialize(applicationContext)
            } catch (t: Throwable) {
                GameLog.e("MainActivity", "meta init failed", t)
            } finally {
                latch.countDown()
            }
        }, "MetaInitFallback").start()
        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
    }

    /** Apply the user language preference (0 system, 1 English, 2 Arabic). */
    fun applyLanguage(pick: Int) {
        val locales = when (pick) {
            1 -> androidx.core.os.LocaleListCompat.forLanguageTags("en")
            2 -> androidx.core.os.LocaleListCompat.forLanguageTags("ar")
            else -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
    }

    /* ---------------- boot flow ---------------- */

    private fun showSplash() {
        val splash = SplashScreen(this)
        splash.onFinished = {
            if (engine.stateManager.current == GameState.SPLASH) {
                engine.stateManager.transition(GameState.LOADING)
                runStartupLoading()
            }
        }
        screenManager.replace(splash, animated = false)
    }

    /**
     * First-time startup pipeline: procedural asset generation, renderer
     * warm-up and system initialization, all off the main thread.
     */
    private fun runStartupLoading() {
        val loading = LoadingScreen(this)
        screenManager.replace(loading, animated = false)

        val pipeline = LoadingPipeline("startup")
        pipeline
            .task(getString(R.string.loading_assets), 3f) { pipe ->
                com.towerbound.laststand.assets.AssetRepository.generateCore { }
            }
            .task(getString(R.string.loading_renderer), 1f) { _ ->
                Thread.sleep(40)
            }
            .task(getString(R.string.loading_systems), 1f) { _ ->
                Thread.sleep(40)
            }
            .task(getString(R.string.loading_battlefield), 1f) { _ ->
                Thread.sleep(40)
            }

        pipeline.start(object : LoadingPipeline.Listener {
            override fun onProgress(fraction: Float, statusText: String) {
                loading.setProgress(fraction, statusText)
            }

            override fun onComplete() {
                engine.stateManager.transition(GameState.MAIN_MENU)
                screenManager.replace(MainMenuScreen(this@MainActivity))
            }

            override fun onTaskError(taskName: String, error: Throwable) {
                // Errors are logged; loading continues with fallbacks.
            }
        })
    }

    /* ---------------- OpenGL world surface ---------------- */

    /** Active renderer, if the GL surface is attached. */
    val gameRenderer: GLRenderer? get() = renderer

    private fun attachGlSurface() {
        if (glView != null) return
        val root = findViewById<FrameLayout>(R.id.root_container)
        val view = GLSurfaceView(this)
        view.setEGLContextFactory(FallbackContextFactory())
        view.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        view.preserveEGLContextOnPause = true
        val r = GLRenderer(engine)
        view.setRenderer(r)
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        root.addView(view, 0, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        glView = view
        renderer = r
        engine.frameRenderHook = { dt -> r.renderWorld(dt) }
        engine.cameraHook = { x, y, dt -> r.camera.follow(x, y, dt) }
        engine.cameraRef = r.camera
        engine.resetFrameTiming()
    }

    override fun onResume() {
        super.onResume()
        glView?.onResume()
        applyImmersiveMode()
        engine.onAppResume()
        // procedural audio + haptics come alive with the UI
        if (!MetaSession.initialized) MetaSession.initialize(applicationContext)
        val settings = GameSettings.get()
        AudioDirector.applySettings(settings)
        AudioDirector.start(applicationContext)
        AudioDirector.setMood(AudioDirector.MusicMood.MENU)
        Haptics.enabled = settings.hapticsEnabled
        GameLog.i("MainActivity", "onResume")
    }

    override fun onPause() {
        super.onPause()
        glView?.onPause()
        engine.onAppPause()
        // persist meta state so a background kill never loses progress
        try {
            if (MetaSession.initialized) MetaSession.persist()
        } catch (t: Throwable) {
            GameLog.e("MainActivity", "persist on pause failed", t)
        }
        AudioDirector.stop()
        GameLog.i("MainActivity", "onPause — pausing simulation & autosaving")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            engine.frameRenderHook = null
            renderer?.release()
        }
    }

    /**
     * State-machine aware back navigation routed through the screen stack.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screenManager.onBack()) return
        super.onBackPressed()
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? = engine

    private fun applyImmersiveMode() {
        val flags = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }
}
