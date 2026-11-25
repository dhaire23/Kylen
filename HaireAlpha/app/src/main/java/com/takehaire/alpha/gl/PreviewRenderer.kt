package com.takehaire.alpha.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.lut.Lut3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Statistics read back from the GPU a few times per second. */
data class FrameStats(
    /** Centre-weighted linear luminance of the camera's (pre-look) preview. */
    val meterLinear: Float,
    /** 64-bin luminance histograms of the rendered (post-look) image. */
    val histY: IntArray,
    val histR: IntArray,
    val histG: IntArray,
    val histB: IntArray,
    val clippedFraction: Float,
    val fps: Float,
)

/**
 * OpenGL ES 3.0 viewfinder. The camera writes into an external OES texture; one fragment shader
 * applies the look as a 3D LUT (baked from the same LookRenderer as the stills), plus optional zebras
 * and focus peaking. All expensive look work happens once per LUT bake, never per frame.
 *
 * Threading: all GL calls on the GLSurfaceView thread; setters are thread-safe hand-offs.
 */
class PreviewRenderer(
    private val onSurfaceTexture: (SurfaceTexture) -> Unit,
    private val requestRender: () -> Unit,
    private val onStats: (FrameStats) -> Unit,
    private val onError: (String) -> Unit = {},
) : GLSurfaceView.Renderer {

    @Volatile var bufferWidth = 1440
    @Volatile var bufferHeight = 1080
    /** Display rotation in degrees (0/90/180/270) — counter-rotates the content. */
    @Volatile var displayRotation = 0
    /** Viewfinder aspect (width / height) of the view; content is centre-cropped to it. */
    @Volatile var zebras = false
    @Volatile var zebraLevel = 0.95f
    @Volatile var peaking = false
    @Volatile var lutEnabled = true
    @Volatile var frozen = false
    /** Focus magnification (1 = off); magnifies around the centre while focusing manually. */
    @Volatile var magnification = 1f

    private val pendingLut = AtomicReference<Lut3D?>(null)
    private val pendingThumb = AtomicReference<((IntArray, Int, Int) -> Unit)?>(null)

    private var surfaceTexture: SurfaceTexture? = null
    private var camTex = 0
    private var lutTex = 0
    private var lutSize = 2
    private var program = 0
    private var statsProgram = 0
    private var fbo = IntArray(2)
    private var fboTex = IntArray(2)
    private var viewW = 1
    private var viewH = 1
    private val texMatrix = FloatArray(16)
    private val uvMatrix = FloatArray(16)
    private var frameCount = 0
    private var fpsT0 = SystemClock.elapsedRealtime()
    private var fpsFrames = 0
    private var fps = 0f

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }

    fun setLut(lut: Lut3D) {
        pendingLut.set(lut)
        requestRender()
    }

    /** Requests one small pre-look frame (sRGB-encoded camera values) for live look thumbnails. */
    fun requestThumbnail(cb: (IntArray, Int, Int) -> Unit) {
        pendingThumb.set(cb)
        requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        camTex = t[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTex)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glGenTextures(1, t, 0)
        lutTex = t[0]
        uploadLut(Lut3D.identity(2))

        // A shader failure must never take the camera down: fall back to the plain (no-overlay) program.
        statsProgram = try { buildProgram(VERTEX, FRAGMENT_STATS) } catch (e: Exception) { onError(e.message ?: "shader"); 0 }
        program = try { buildProgram(VERTEX, FRAGMENT) } catch (e: Exception) { onError(e.message ?: "shader"); statsProgram }

        GLES30.glGenFramebuffers(2, fbo, 0)
        GLES30.glGenTextures(2, fboTex, 0)
        for (i in 0..1) {
            val (w, h) = if (i == 0) STATS_W to STATS_H else THUMB_W to THUMB_W
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTex[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[i])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTex[i], 0)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val st = SurfaceTexture(camTex)
        st.setOnFrameAvailableListener { requestRender() }
        surfaceTexture?.release()
        surfaceTexture = st
        onSurfaceTexture(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width.coerceAtLeast(1)
        viewH = height.coerceAtLeast(1)
    }

    private fun uploadLut(lut: Lut3D) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        val buf = ByteBuffer.allocateDirect(lut.size * lut.size * lut.size * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(lut.toRgba()).position(0)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA16F, lut.size, lut.size, lut.size, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        for (w in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, w, GLES30.GL_CLAMP_TO_EDGE)
        lutSize = lut.size
    }

    /** uv (0..1 of the view) → camera texture coordinates: centre-crop to view aspect, rotate, SurfaceTexture transform. */
    private fun computeUvMatrix() {
        // Content is upright in the device's natural orientation after the SurfaceTexture transform.
        val rot = displayRotation
        val contentW: Float
        val contentH: Float
        // Camera buffers are landscape (w>h); in natural portrait they appear as h×w.
        val natW = minOf(bufferWidth, bufferHeight).toFloat()
        val natH = maxOf(bufferWidth, bufferHeight).toFloat()
        if (rot == 90 || rot == 270) { contentW = natH; contentH = natW } else { contentW = natW; contentH = natH }
        val viewAspect = viewW.toFloat() / viewH
        val contentAspect = contentW / contentH
        var sx = 1f
        var sy = 1f
        if (contentAspect > viewAspect) sx = viewAspect / contentAspect else sy = contentAspect / viewAspect
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, 0.5f, 0.5f, 0f)
        // Same convention as Camera2Basic configureTransform(): ROTATION_90 → content rotated −90°.
        Matrix.rotateM(m, 0, -rot.toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(m, 0, sx, sy, 1f)
        val mag = magnification.coerceIn(1f, 8f)
        Matrix.scaleM(m, 0, 1f / mag, 1f / mag, 1f)
        Matrix.translateM(m, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(uvMatrix, 0, texMatrix, 0, m, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingLut.getAndSet(null)?.let { uploadLut(it) }
        val st = surfaceTexture ?: return
        if (!frozen) {
            try {
                st.updateTexImage()
            } catch (_: Exception) {
                return
            }
        }
        st.getTransformMatrix(texMatrix)
        computeUvMatrix()

        // ---- main pass
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        if (program == 0) return
        draw(program, applyLut = lutEnabled, overlays = true, texelW = 1f / bufferWidth, texelH = 1f / bufferHeight)

        frameCount++
        fpsFrames++
        val now = SystemClock.elapsedRealtime()
        if (now - fpsT0 >= 1000) {
            fps = fpsFrames * 1000f / (now - fpsT0); fpsFrames = 0; fpsT0 = now
        }
        if (frameCount % 4 == 0 && statsProgram != 0) readStats()
        pendingThumb.getAndSet(null)?.let { cb -> readThumbnail(cb) }
    }

    private fun draw(prog: Int, applyLut: Boolean, overlays: Boolean, texelW: Float, texelH: Float) {
        GLES30.glUseProgram(prog)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uCam"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uLut"), 1)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(prog, "uUv"), 1, false, uvMatrix, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uLutScale"), (lutSize - 1f) / lutSize)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uLutOffset"), 0.5f / lutSize)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uApplyLut"), if (applyLut) 1 else 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uZebra"), if (overlays && zebras) 1 else 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uZebraLevel"), zebraLevel)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uPeaking"), if (overlays && peaking) 1 else 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uTexel"), texelW, texelH)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uView"), viewW.toFloat(), viewH.toFloat())
        val pos = GLES30.glGetAttribLocation(prog, "aPos")
        GLES30.glEnableVertexAttribArray(pos)
        GLES30.glVertexAttribPointer(pos, 2, GLES30.GL_FLOAT, false, 8, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(pos)
    }

    private val statsBuf = ByteBuffer.allocateDirect(STATS_W * STATS_H * 4).order(ByteOrder.nativeOrder())
    private val decode = FloatArray(256) { ColorMath.srgbDecodeF(it / 255f) }

    private fun readStats() {
        // Pass 1: pre-look camera values (for metering) — stats program writes raw camera RGB.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glViewport(0, 0, STATS_W, STATS_H)
        draw(statsProgram, applyLut = false, overlays = false, texelW = 0f, texelH = 0f)
        statsBuf.position(0)
        GLES30.glReadPixels(0, 0, STATS_W, STATS_H, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, statsBuf)
        var wSum = 0f
        var lSum = 0f
        for (y in 0 until STATS_H) for (x in 0 until STATS_W) {
            val i = (y * STATS_W + x) * 4
            val r = decode[statsBuf.get(i).toInt() and 0xFF]
            val g = decode[statsBuf.get(i + 1).toInt() and 0xFF]
            val b = decode[statsBuf.get(i + 2).toInt() and 0xFF]
            val dx = (x - STATS_W / 2f) / STATS_W
            val dy = (y - STATS_H / 2f) / STATS_H
            val w = 1f / (1f + 12f * (dx * dx + dy * dy)) // centre weighted
            lSum += (0.2126f * r + 0.7152f * g + 0.0722f * b) * w
            wSum += w
        }
        // Pass 2: post-look output for histogram / clipping.
        draw(statsProgram, applyLut = lutEnabled, overlays = false, texelW = 0f, texelH = 0f)
        statsBuf.position(0)
        GLES30.glReadPixels(0, 0, STATS_W, STATS_H, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, statsBuf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        val hy = IntArray(64); val hr = IntArray(64); val hg = IntArray(64); val hb = IntArray(64)
        var clipped = 0
        for (i in 0 until STATS_W * STATS_H) {
            val r = statsBuf.get(i * 4).toInt() and 0xFF
            val g = statsBuf.get(i * 4 + 1).toInt() and 0xFF
            val b = statsBuf.get(i * 4 + 2).toInt() and 0xFF
            val y = (54 * r + 183 * g + 19 * b) shr 8
            hy[y shr 2]++; hr[r shr 2]++; hg[g shr 2]++; hb[b shr 2]++
            if (r >= 254 || g >= 254 || b >= 254) clipped++
        }
        onStats(FrameStats(lSum / wSum, hy, hr, hg, hb, clipped.toFloat() / (STATS_W * STATS_H), fps))
    }

    private fun readThumbnail(cb: (IntArray, Int, Int) -> Unit) {
        if (statsProgram == 0) return
        // Match the viewfinder's aspect so thumbnails are not stretched.
        val (tw, th) = if (viewW >= viewH) THUMB_W to THUMB_H else THUMB_H to THUMB_W
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[1])
        GLES30.glViewport(0, 0, tw, th)
        draw(statsProgram, applyLut = false, overlays = false, texelW = 0f, texelH = 0f)
        val buf = ByteBuffer.allocateDirect(tw * th * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        val px = IntArray(tw * th)
        for (y in 0 until th) for (x in 0 until tw) {
            val i = ((th - 1 - y) * tw + x) * 4 // GL origin is bottom-left
            px[y * tw + x] = (0xFF shl 24) or ((buf.get(i).toInt() and 0xFF) shl 16) or
                ((buf.get(i + 1).toInt() and 0xFF) shl 8) or (buf.get(i + 2).toInt() and 0xFF)
        }
        cb(px, tw, th)
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("Shader compile failed: " + GLES30.glGetShaderInfoLog(s))
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("Program link failed: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    companion object {
        const val STATS_W = 48
        const val STATS_H = 36
        const val THUMB_W = 96
        const val THUMB_H = 64

        private const val VERTEX = """#version 300 es
in vec2 aPos;
uniform mat4 uUv;
out vec2 vTex;
out vec2 vScreen;
void main() {
    vec2 uv = aPos * 0.5 + 0.5;
    vScreen = uv;
    vTex = (uUv * vec4(uv, 0.0, 1.0)).xy;
    gl_Position = vec4(aPos, 0.0, 1.0);
}"""

        private const val FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES uCam;
uniform highp sampler3D uLut;
uniform float uLutScale;
uniform float uLutOffset;
uniform int uApplyLut;
uniform int uZebra;
uniform float uZebraLevel;
uniform int uPeaking;
uniform vec2 uTexel;
uniform vec2 uView;
in vec2 vTex;
in vec2 vScreen;
out vec4 fragColor;
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
void main() {
    vec3 cam = texture(uCam, vTex).rgb;
    vec3 c = uApplyLut == 1 ? texture(uLut, cam * uLutScale + uLutOffset).rgb : cam;
    if (uPeaking == 1) {
        float l = luma(texture(uCam, vTex + vec2(uTexel.x, 0.0)).rgb) - luma(texture(uCam, vTex - vec2(uTexel.x, 0.0)).rgb);
        float m = luma(texture(uCam, vTex + vec2(0.0, uTexel.y)).rgb) - luma(texture(uCam, vTex - vec2(0.0, uTexel.y)).rgb);
        float g = sqrt(l * l + m * m);
        if (g > 0.22) c = mix(c, vec3(0.91, 0.54, 0.17), 0.85);
    }
    if (uZebra == 1 && luma(c) >= uZebraLevel) {
        vec2 p = vScreen * uView;
        if (mod(p.x + p.y, 14.0) < 6.0) c = mix(c, vec3(0.1), 0.55);
    }
    fragColor = vec4(c, 1.0);
}"""

        private const val FRAGMENT_STATS = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES uCam;
uniform highp sampler3D uLut;
uniform float uLutScale;
uniform float uLutOffset;
uniform int uApplyLut;
in vec2 vTex;
in vec2 vScreen;
out vec4 fragColor;
void main() {
    vec3 cam = texture(uCam, vTex).rgb;
    vec3 c = uApplyLut == 1 ? texture(uLut, cam * uLutScale + uLutOffset).rgb : cam;
    fragColor = vec4(c, 1.0);
}"""
    }
}
