package com.towerbound.laststand.render

import android.opengl.GLES20
import android.opengl.GLES30
import com.towerbound.laststand.core.GameLog

/**
 * Shader compilation helpers with automatic GLES3 -> GLES2 fallback.
 * The renderer tries GLSL ES 3.00 first; if context creation or shader
 * compilation fails, it falls back to GLSL ES 1.00 (compatibility rule
 * from the design document).
 */
object ShaderUtil {

    /** True when the running context supports GLES 3.0. */
    @Volatile var useGles3: Boolean = false
        private set

    fun detectGles3(): Boolean {
        val version = java.lang.String(GLES30.glGetString(GLES30.GL_VERSION))
        val supports3 = version.contains("OpenGL ES 3")
        useGles3 = supports3
        GameLog.i("Shader", "GL version: $version -> using ${if (supports3) "GLES3" else "GLES2"} path")
        return supports3
    }

    fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            GameLog.e("Shader", "glCreateShader failed")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GameLog.e("Shader", "compile error: ${GLES20.glGetShaderInfoLog(shader)}\n--- source ---\n$source")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun link(vertexSource: String, fragmentSource: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vs == 0 || fs == 0) return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (status[0] == 0) {
            GameLog.e("Shader", "link error: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    /** Sprinkle error checking over GL calls during development. */
    fun checkGlError(tag: String) {
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) {
            GameLog.e("Shader", "GL error 0x${Integer.toHexString(err)} at $tag")
        }
    }

    /* ---------- shader sources: GLES2-compatible (works in both modes) ---------- */

    const val SPRITE_VS = """
        uniform mat4 uMVP;
        attribute vec2 aPos;
        attribute vec2 aUV;
        attribute vec4 aColor;
        varying vec2 vUV;
        varying vec4 vColor;
        void main() {
            vUV = aUV;
            vColor = aColor;
            gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
        }
    """

    const val SPRITE_FS = """
        precision mediump float;
        varying vec2 vUV;
        varying vec4 vColor;
        uniform sampler2D uTex;
        void main() {
            vec4 c = texture2D(uTex, vUV);
            gl_FragColor = c * vColor;
        }
    """

    /** Same quad shader with additive blending (particles/glows). */
    const val SPRITE_FS_ADD = """
        precision mediump float;
        varying vec2 vUV;
        varying vec4 vColor;
        uniform sampler2D uTex;
        void main() {
            vec4 c = texture2D(uTex, vUV);
            gl_FragColor = vec4(c.rgb * vColor.rgb * vColor.a, c.a * vColor.a);
        }
    """
}
