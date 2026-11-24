package com.example.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.example.colorscience.EngineUniforms
import com.example.colorscience.GlOffscreenRenderer
import com.example.colorscience.PhotographicStyleShaderSource
import com.example.colorscience.StyleParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Hardware-accelerated OpenGL ES 2.0 camera viewfinder implementing CameraX [Preview.SurfaceProvider].
 * Renders the live camera OES texture through [PhotographicStyleShaderSource] at display refresh rate
 * with zero CPU frame-copy overhead and accurate aspect-ratio center-crop scaling.
 */
class CameraPreviewSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), Preview.SurfaceProvider {

    private val renderer = CameraShaderRenderer { requestRender() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val glCallbackExecutor = Executors.newSingleThreadExecutor()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateStyleParameters(parameters: StyleParameters) {
        renderer.updateUniforms(parameters.toEngineUniforms())
        requestRender()
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        val resolution = request.resolution
        request.setTransformationInfoListener(glCallbackExecutor) { info ->
            renderer.updateStreamGeometry(
                bufferWidth = resolution.width,
                bufferHeight = resolution.height,
                rotationDegrees = info.rotationDegrees
            )
            requestRender()
        }

        queueEvent {
            val surfaceTexture = renderer.createOrResetSurfaceTexture(resolution.width, resolution.height)
            val surface = Surface(surfaceTexture)
            mainHandler.post {
                request.provideSurface(surface, glCallbackExecutor) {
                    surface.release()
                }
            }
        }
    }

    fun releaseResources() {
        queueEvent { renderer.release() }
        glCallbackExecutor.shutdown()
    }

    private class CameraShaderRenderer(
        private val onFrameNeedsRender: () -> Unit
    ) : Renderer {

        private val quadVertices = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )
        private val quadTexCoords = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )

        private val vertexBuf: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(quadVertices).apply { position(0) }
        private val texBuf: FloatBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(quadTexCoords).apply { position(0) }

        private var program = 0
        private var oesTextureId = 0
        private var surfaceTexture: SurfaceTexture? = null

        @Volatile
        private var frameAvailable = false

        @Volatile
        private var currentUniforms: EngineUniforms = EngineUniforms.IDENTITY

        private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
        private val vertexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

        private var viewWidth = 1
        private var viewHeight = 1
        private var bufferWidth = 1
        private var bufferHeight = 1
        private var rotationDegrees = 0

        fun updateUniforms(uniforms: EngineUniforms) {
            currentUniforms = uniforms
        }

        fun updateStreamGeometry(bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int) {
            this.bufferWidth = bufferWidth.coerceAtLeast(1)
            this.bufferHeight = bufferHeight.coerceAtLeast(1)
            this.rotationDegrees = rotationDegrees
            recomputeVertexMatrix()
        }

        fun createOrResetSurfaceTexture(width: Int, height: Int): SurfaceTexture {
            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()

            if (oesTextureId == 0) {
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                oesTextureId = ids[0]
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
                )
            }

            val st = SurfaceTexture(oesTextureId)
            st.setDefaultBufferSize(width, height)
            st.setOnFrameAvailableListener {
                frameAvailable = true
                onFrameNeedsRender()
            }
            surfaceTexture = st
            return st
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            program = GlOffscreenRenderer.createProgram(
                PhotographicStyleShaderSource.VERTEX_SHADER_OES,
                PhotographicStyleShaderSource.buildFragmentShader(useOesExternalTexture = true)
            )
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewWidth = width.coerceAtLeast(1)
            viewHeight = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            recomputeVertexMatrix()
        }

        override fun onDrawFrame(gl: GL10?) {
            val st = surfaceTexture
            if (st != null && frameAvailable) {
                frameAvailable = false
                try {
                    st.updateTexImage()
                    st.getTransformMatrix(texMatrix)
                } catch (_: RuntimeException) {
                    return
                }
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (st == null || oesTextureId == 0 || program == 0) return

            GLES20.glUseProgram(program)

            val aPos = GLES20.glGetAttribLocation(program, "aPosition")
            val aTex = GLES20.glGetAttribLocation(program, "aTexCoord")
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuf)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)

            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrix, 0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uVertexMatrix"), 1, false, vertexMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

            GLES20.glUniform2f(
                GLES20.glGetUniformLocation(program, "uTexelStep"),
                1f / viewWidth.coerceAtLeast(1).toFloat(),
                1f / viewHeight.coerceAtLeast(1).toFloat()
            )
            GlOffscreenRenderer.bindUniforms(program, currentUniforms)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun recomputeVertexMatrix() {
            Matrix.setIdentityM(vertexMatrix, 0)
            val rotatedPortrait = (rotationDegrees % 180) != 0
            val orientedWidth = if (rotatedPortrait) bufferHeight else bufferWidth
            val orientedHeight = if (rotatedPortrait) bufferWidth else bufferHeight
            val frameAspect = orientedWidth.toFloat() / orientedHeight.toFloat()
            val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
            if (frameAspect > viewAspect) {
                Matrix.scaleM(vertexMatrix, 0, frameAspect / viewAspect, 1f, 1f)
            } else {
                Matrix.scaleM(vertexMatrix, 0, 1f, viewAspect / frameAspect, 1f)
            }
        }

        fun release() {
            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            surfaceTexture = null
            if (oesTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                oesTextureId = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
    }
}
