package com.example.colorscience

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Offscreen EGL14 + OpenGL ES 2.0 renderer that processes full-resolution captured photos
 * using the exact same [PhotographicStyleShaderSource] GLSL pipeline as the live camera preview.
 * Supports automatic tiling when an image exceeds GL_MAX_TEXTURE_SIZE and gracefully falls back
 * to [PhotographicStyleCpuProcessor] if EGL is unavailable.
 */
object GlOffscreenRenderer {

    private const val TAG = "GlOffscreenRenderer"

    private val QUAD_VERTICES = floatArrayOf(
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f
    )

    // Note: GLUtils.texImage2D uploads bitmap top-to-bottom at v=0..1, and glReadPixels reads
    // bottom-to-top. Using standard flipped v coordinates aligns output orientation cleanly.
    private val QUAD_TEX_COORDS = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    fun renderFullResolution(source: Bitmap, parameters: StyleParameters): Bitmap {
        val uniforms = parameters.toEngineUniforms()
        if (uniforms.bypass) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        return try {
            renderWithEgl(source, uniforms)
        } catch (t: Throwable) {
            Log.w(TAG, "EGL offscreen render unavailable, using CPU processor fallback", t)
            PhotographicStyleCpuProcessor.applyStyleToBitmap(source, parameters)
        }
    }

    private fun renderWithEgl(source: Bitmap, uniforms: EngineUniforms): Bitmap {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            require(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

            val version = IntArray(2)
            require(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            require(EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
                "eglChooseConfig failed"
            }
            val config = configs[0]!!

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            require(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

            // Query max texture / viewport size using tiny 1x1 pbuffer first
            val probeAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, probeAttribs, 0)
            require(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
            require(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }

            val maxTexArr = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexArr, 0)
            val maxDims = IntArray(2)
            GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, maxDims, 0)
            val maxTileSize = min(
                max(2048, min(maxTexArr[0], min(maxDims[0], maxDims[1]))),
                4096
            )

            EGL14.eglDestroySurface(display, surface)
            surface = EGL14.EGL_NO_SURFACE

            val srcWidth = source.width
            val srcHeight = source.height
            val outBitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)

            // Process in tiles up to maxTileSize so 12MP/48MP photos preserve 100% original resolution
            val tileW = min(srcWidth, maxTileSize)
            val tileH = min(srcHeight, maxTileSize)
            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, tileW, EGL14.EGL_HEIGHT, tileH, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
            require(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface tile failed" }
            require(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent tile failed" }

            val program = createProgram(
                PhotographicStyleShaderSource.VERTEX_SHADER_2D,
                PhotographicStyleShaderSource.buildFragmentShader(useOesExternalTexture = false)
            )
            GLES20.glUseProgram(program)

            val vertexBuf = floatBufferOf(QUAD_VERTICES)
            val texBuf = floatBufferOf(QUAD_TEX_COORDS)

            val aPos = GLES20.glGetAttribLocation(program, "aPosition")
            val aTex = GLES20.glGetAttribLocation(program, "aTexCoord")
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuf)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)

            bindUniforms(program, uniforms)

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            val texId = texIds[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

            var y = 0
            while (y < srcHeight) {
                val curH = min(tileH, srcHeight - y)
                var x = 0
                while (x < srcWidth) {
                    val curW = min(tileW, srcWidth - x)
                    val tileSrc = if (x == 0 && y == 0 && curW == srcWidth && curH == srcHeight) {
                        source
                    } else {
                        Bitmap.createBitmap(source, x, y, curW, curH)
                    }

                    GLES20.glViewport(0, 0, curW, curH)
                    GLES20.glUniform2f(
                        GLES20.glGetUniformLocation(program, "uTexelStep"),
                        1f / curW.coerceAtLeast(1).toFloat(),
                        1f / curH.coerceAtLeast(1).toFloat()
                    )
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, tileSrc, 0)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    GLES20.glFinish()

                    val pixelBuf = ByteBuffer.allocateDirect(curW * curH * 4).order(ByteOrder.nativeOrder())
                    GLES20.glReadPixels(0, 0, curW, curH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf)
                    pixelBuf.rewind()

                    val tileOut = Bitmap.createBitmap(curW, curH, Bitmap.Config.ARGB_8888)
                    tileOut.copyPixelsFromBuffer(pixelBuf)

                    val rowPixels = IntArray(curW * curH)
                    tileOut.getPixels(rowPixels, 0, curW, 0, 0, curW, curH)
                    outBitmap.setPixels(rowPixels, 0, curW, x, y, curW, curH)

                    tileOut.recycle()
                    if (tileSrc !== source) tileSrc.recycle()
                    x += tileW
                }
                y += tileH
            }

            GLES20.glDeleteTextures(1, texIds, 0)
            GLES20.glDeleteProgram(program)
            return outBitmap
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }
    }

    internal fun bindUniforms(program: Int, uniforms: EngineUniforms) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBypass"), if (uniforms.bypass) 1f else 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uShadowLift"), uniforms.shadowLift)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uMidtoneLift"), uniforms.midtoneLift)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), uniforms.contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uHighlightShoulder"), uniforms.highlightShoulder)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uVibrance"), uniforms.vibrance)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaScale"), uniforms.chromaScale)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uWarmthShift"), uniforms.warmthShift)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTintShift"), uniforms.tintShift)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSkinProtection"), uniforms.skinProtection)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uCoolSkinGuard"), uniforms.coolSkinGuard)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSkyFoliageBoost"), uniforms.skyFoliageBoost)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uPaletteHueShiftDeg"), uniforms.paletteHueShiftDeg)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uProMaxEnabled"), if (uniforms.proMaxEnabled) 1f else 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uHdrHighlightRecovery"), uniforms.hdrHighlightRecovery)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uHdrShadowLift"), uniforms.hdrShadowLift)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uLowLightFactor"), uniforms.lowLightFactor)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uNaturalClarity"), uniforms.naturalClarity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSoftSkinAmount"), uniforms.softSkinAmount)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uGlowAmount"), uniforms.glowAmount)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFilmAmount"), uniforms.filmAmount)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uGrainAmount"), uniforms.grainAmount)
    }

    internal fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val err = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("Program link failed: $err")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val err = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $err")
        }
        return shader
    }

    private fun floatBufferOf(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
            .apply { position(0) }
}
