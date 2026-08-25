package com.towerbound.laststand.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * EGL context factory: requests a GLES 3.0 context and transparently falls
 * back to 2.0 on legacy devices (design requirement).
 */
class FallbackContextFactory : GLSurfaceView.EGLContextFactory {
    override fun createContext(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext {
        var ctx = EGL10.EGL_NO_CONTEXT
        try {
            val attr3 = intArrayOf(0x3098, 3, EGL10.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION
            ctx = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attr3)
        } catch (t: Throwable) {
            GameLog.w("EGL", "GLES3 context creation threw: ${t.message}")
        }
        if (ctx == EGL10.EGL_NO_CONTEXT) {
            GameLog.w("EGL", "GLES3 unavailable — falling back to GLES2 context")
            val attr2 = intArrayOf(0x3098, 2, EGL10.EGL_NONE)
            ctx = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attr2)
        }
        return ctx
    }

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        egl.eglDestroyContext(display, context)
    }
}

/**
 * Main OpenGL renderer. Owns the render-side singletons and drives the
 * engine frame callback. Context loss is handled by re-creating shaders and
 * re-uploading textures; the game never loses sim state.
 */
class GLRenderer(private val engine: GameEngine) : GLSurfaceView.Renderer {

    val batch = SpriteBatch()
    val textures = TextureManager()
    val textAtlas = TextAtlas()
    val camera = Camera()
    val worldRenderer = WorldRenderer(engine, this)
    val menuScene = MenuSceneRenderer(engine)

    @Volatile var surfaceReady = false
        private set
    @Volatile var frameCount = 0L
        private set

    var firstSurface = true

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ShaderUtil.detectGles3()
        val reloaded = !firstSurface
        if (firstSurface) firstSurface = false
        batch.compileShaders()
        if (reloaded) {
            // OpenGL context was lost — re-upload every texture.
            textures.reloadAll()
        } else {
            textAtlas.build(textures)
        }
        // load base world textures lazily through TextureManager; it caches
        // source bitmaps so context loss can always re-upload.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0.043f, 0.063f, 0.13f, 1f)
        surfaceReady = true
        GameLog.i("Renderer", "surface created (context recovered=$reloaded)")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
        GameLog.i("Renderer", "surface ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        frameCount++
        // Engine steps the simulation, then calls back into worldRenderer
        // through the frame render hook.
        engine.onFrame()
    }

    /** Called by the engine each frame — world & entities, or the animated
     *  menu scene when no run is active. */
    fun renderWorld(deltaSeconds: Float) {
        if (!surfaceReady) return
        camera.update(deltaSeconds)
        worldRenderer.cameraRef = camera
        val inWorld = engine.world != null
        if (!inWorld) {
            // static centered camera for the menu scene (no world clamping)
            camera.worldW = 0f
            camera.worldH = 0f
            camera.snapTo(0f, 0f)
        }
        val mvp = camera.computeMvp()
        batch.begin(mvp)
        try {
            if (inWorld) {
                worldRenderer.render(batch, textures, textAtlas, camera)
            } else {
                menuScene.render(batch, textures, camera, deltaSeconds)
            }
        } catch (t: Throwable) {
            GameLog.e("Renderer", "render error", t)
        } finally {
            batch.end()
        }
    }

    fun release() {
        surfaceReady = false
        batch.releasePrograms()
        textures.deleteAll()
    }
}
