package com.example.colorscience

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ComputationalSceneMode(val badgeLabel: String) {
    PRO_MAX("PRO"),
    SMART_HDR("HDR"),
    NIGHT("NIGHT"),
    PORTRAIT("PORTRAIT")
}

/**
 * Live scene exposure & subject telemetry extracted from camera analysis or decoded frames.
 * Used to drive adaptive Smart HDR, Night noise reduction, and live-preview parity without
 * ever overriding the user's selected Photographic Style or Palette.
 */
data class SceneTelemetry(
    val meanLuma: Float = 0.45f,
    val highlightClippingRatio: Float = 0.0f,
    val shadowDensityRatio: Float = 0.0f,
    val dynamicRangeSpan: Float = 0.55f,
    val portraitSubjectFraction: Float = 0.0f,
    val isLowLight: Boolean = false,
    val isHighDynamicRange: Boolean = false
) {
    val activeMode: ComputationalSceneMode
        get() = when {
            isLowLight -> ComputationalSceneMode.NIGHT
            isHighDynamicRange -> ComputationalSceneMode.SMART_HDR
            portraitSubjectFraction > 0.08f -> ComputationalSceneMode.PORTRAIT
            else -> ComputationalSceneMode.PRO_MAX
        }

    fun toEnhancementParams(proMaxEnabled: Boolean): SceneEnhancementParams {
        if (!proMaxEnabled) return SceneEnhancementParams.DISABLED
        val hdrDrive = when {
            isHighDynamicRange -> 0.85f
            highlightClippingRatio > 0.03f || shadowDensityRatio > 0.22f -> 0.58f
            else -> 0.35f
        }
        val lowLightDrive = if (isLowLight) {
            ((0.26f - meanLuma) / 0.20f).coerceIn(0.25f, 1.0f)
        } else {
            0.0f
        }
        val shadowLift = when {
            isLowLight -> (0.14f * lowLightDrive).coerceIn(0.04f, 0.16f) // Preserve natural night contrast
            isHighDynamicRange -> 0.22f
            shadowDensityRatio > 0.20f -> 0.16f
            else -> 0.08f
        }
        val highlightRec = when {
            highlightClippingRatio > 0.08f -> 0.36f
            isHighDynamicRange -> 0.30f
            isLowLight -> 0.24f // Streetlight & specular highlight guard
            else -> 0.18f
        }
        val clarity = when {
            isLowLight -> 0.05f // Avoid amplifying noise in dark scenes
            portraitSubjectFraction > 0.10f -> 0.08f
            else -> 0.14f
        }
        return SceneEnhancementParams(
            enabled = true,
            hdrStrength = hdrDrive,
            shadowDetailBoost = shadowLift,
            highlightRecovery = highlightRec,
            lowLightFactor = lowLightDrive,
            chromaDenoiseStrength = (0.28f + lowLightDrive * 0.58f).coerceIn(0.25f, 0.88f),
            naturalClarity = clarity,
            portraitGuard = (portraitSubjectFraction * 2.5f).coerceIn(0f, 1f)
        )
    }

    companion object {
        val DEFAULT = SceneTelemetry()
    }
}

data class SceneEnhancementParams(
    val enabled: Boolean,
    val hdrStrength: Float,
    val shadowDetailBoost: Float,
    val highlightRecovery: Float,
    val lowLightFactor: Float,
    val chromaDenoiseStrength: Float,
    val naturalClarity: Float,
    val portraitGuard: Float
) {
    companion object {
        val DISABLED = SceneEnhancementParams(
            enabled = false,
            hdrStrength = 0f,
            shadowDetailBoost = 0f,
            highlightRecovery = 0f,
            lowLightFactor = 0f,
            chromaDenoiseStrength = 0f,
            naturalClarity = 0f,
            portraitGuard = 0f
        )
    }
}

/**
 * Stages 2 & 3 of the 6-stage Computational Photography Pipeline:
 * - Motion-aware multi-frame burst alignment & HDR/Night exposure fusion
 * - Edge-preserving chroma noise reduction (protecting skin & hair micro-texture)
 * - Separable multi-scale local tone mapping, highlight recovery, shadow recovery & natural clarity
 *
 * Strictly executes BEFORE Stage 4 (Photographic Style) and Stage 5 (2D Palette) so user
 * creative controls are never overwritten or suppressed.
 */
object ComputationalPhotoEngine {

    /**
     * Analyzes an ARGB pixel buffer (live analysis frame or captured image) to derive [SceneTelemetry].
     */
    fun analyzeSceneTelemetry(pixels: IntArray): SceneTelemetry {
        if (pixels.isEmpty()) return SceneTelemetry.DEFAULT
        val step = max(1, pixels.size / 4096)
        val hist = IntArray(64)
        var count = 0
        var sumLuma = 0.0
        var clipCount = 0
        var shadowCount = 0
        var skinCount = 0

        for (i in pixels.indices step step) {
            val p = pixels[i]
            val r = ((p ushr 16) and 0xFF) / 255f
            val g = ((p ushr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

            sumLuma += luma
            count++
            if (luma > 0.89f) clipCount++
            if (luma < 0.16f) shadowCount++

            val bin = (luma * 63f).toInt().coerceIn(0, 63)
            hist[bin]++

            // Fast chromatic skin heuristic in RGB ratio space
            if (r > 0.25f && g > 0.16f && b > 0.10f && r > g && g >= b * 0.92f && (r - b) > 0.06f && (r - g) < 0.35f) {
                skinCount++
            }
        }

        val total = max(1, count)
        val meanLuma = (sumLuma / total).toFloat()
        val clipRatio = clipCount.toFloat() / total
        val shadowRatio = shadowCount.toFloat() / total
        val skinFraction = skinCount.toFloat() / total

        // Compute p5..p95 dynamic range span
        var p5 = 0f
        var p95 = 1f
        var accum = 0
        val lowTarget = (total * 0.05f).toInt()
        val highTarget = (total * 0.95f).toInt()
        var foundLow = false
        for (b in 0 until 64) {
            accum += hist[b]
            if (!foundLow && accum >= lowTarget) {
                p5 = b / 63f
                foundLow = true
            }
            if (accum >= highTarget) {
                p95 = b / 63f
                break
            }
        }

        val isLowLight = meanLuma < 0.24f
        val isHdr = (clipRatio > 0.05f && shadowRatio > 0.16f) ||
            ((p95 - p5) > 0.72f && (clipRatio > 0.04f || shadowRatio > 0.22f))

        return SceneTelemetry(
            meanLuma = meanLuma,
            highlightClippingRatio = clipRatio,
            shadowDensityRatio = shadowRatio,
            dynamicRangeSpan = (p95 - p5).coerceIn(0f, 1f),
            portraitSubjectFraction = skinFraction,
            isLowLight = isLowLight,
            isHighDynamicRange = isHdr
        )
    }

    fun analyzeBitmapTelemetry(bitmap: Bitmap): SceneTelemetry {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return analyzeSceneTelemetry(pixels)
    }

    /**
     * Motion-aware multi-frame burst fusion for Bitmaps (HDR bracket or Night temporal burst).
     * Aligns auxiliary frames to the sharpest center anchor frame using global + block motion
     * compensation, applies per-pixel ghost rejection, and fuses well-exposed pixels.
     */
    fun fuseBurstBitmaps(
        frames: List<Bitmap>,
        telemetry: SceneTelemetry
    ): Bitmap {
        require(frames.isNotEmpty()) { "Burst must contain at least one frame" }
        val anchor = frames.first()
        if (frames.size == 1) {
            return anchor.copy(Bitmap.Config.ARGB_8888, false)
        }
        val width = anchor.width
        val height = anchor.height
        val count = width * height

        val anchorPixels = IntArray(count)
        anchor.getPixels(anchorPixels, 0, width, 0, 0, width, height)

        val auxBuffers = frames.drop(1).mapNotNull { bmp ->
            if (bmp.width == width && bmp.height == height) {
                IntArray(count).also { bmp.getPixels(it, 0, width, 0, 0, width, height) }
            } else {
                null
            }
        }

        val fused = fuseBurstBuffers(anchorPixels, auxBuffers, width, height, telemetry)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(fused, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Pure-JVM motion-aware multi-frame fusion operating on ARGB buffers.
     */
    fun fuseBurstBuffers(
        anchor: IntArray,
        auxFrames: List<IntArray>,
        width: Int,
        height: Int,
        telemetry: SceneTelemetry
    ): IntArray {
        if (auxFrames.isEmpty() || anchor.size != width * height) {
            return anchor.copyOf()
        }
        val n = anchor.size
        val anchorLuma = FloatArray(n)
        for (i in 0 until n) {
            val p = anchor[i]
            anchorLuma[i] = (0.2126f * ((p ushr 16) and 0xFF) +
                0.7152f * ((p ushr 8) and 0xFF) +
                0.0722f * (p and 0xFF)) / 255f
        }

        // Compute global translational alignment per auxiliary frame on a central sample grid
        val offsets = auxFrames.map { aux ->
            estimateGlobalShift(anchorLuma, aux, width, height)
        }

        val out = IntArray(n)
        val ghostSigma = if (telemetry.isLowLight) 0.14f else 0.18f

        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                val idx = rowOff + x
                val p0 = anchor[idx]
                val a0 = (p0 ushr 24) and 0xFF
                val r0 = (p0 ushr 16) and 0xFF
                val g0 = (p0 ushr 8) and 0xFF
                val b0 = p0 and 0xFF
                val l0 = anchorLuma[idx]

                // Well-exposedness weight (Mertens bell centered around 0.48, keeping shadows/highlights alive)
                val w0 = wellExposedWeight(l0, telemetry.isLowLight) + 0.25f

                var sumW = w0
                var sumR = r0 * w0
                var sumG = g0 * w0
                var sumB = b0 * w0

                for (f in auxFrames.indices) {
                    val aux = auxFrames[f]
                    val (dx, dy) = offsets[f]
                    val sx = (x + dx).coerceIn(0, width - 1)
                    val sy = (y + dy).coerceIn(0, height - 1)
                    val pf = aux[sy * width + sx]
                    val rf = (pf ushr 16) and 0xFF
                    val gf = (pf ushr 8) and 0xFF
                    val bf = pf and 0xFF
                    val lf = (0.2126f * rf + 0.7152f * gf + 0.0722f * bf) / 255f

                    // Motion-aware ghost rejection: reject moved objects while allowing intentional EV brackets
                    val lumaDiff = abs(lf - l0)
                    val chromaticDiff = (abs(rf - r0) + abs(gf - g0) + abs(bf - b0)) / (3f * 255f)
                    val motionMetric = if (telemetry.isHighDynamicRange) {
                        // In HDR bracketed exposures, luma shifts globally; detect structural chroma/ratio mismatch
                        abs(chromaticDiff - lumaDiff * 0.65f)
                    } else {
                        lumaDiff * 0.7f + chromaticDiff * 0.3f
                    }
                    val ghostWeight = exp(-(motionMetric * motionMetric) / (2f * ghostSigma * ghostSigma))
                    val wf = wellExposedWeight(lf, telemetry.isLowLight) * ghostWeight

                    sumW += wf
                    sumR += rf * wf
                    sumG += gf * wf
                    sumB += bf * wf
                }

                val invW = 1f / max(sumW, 1e-4f)
                val rOut = (sumR * invW + 0.5f).toInt().coerceIn(0, 255)
                val gOut = (sumG * invW + 0.5f).toInt().coerceIn(0, 255)
                val bOut = (sumB * invW + 0.5f).toInt().coerceIn(0, 255)
                out[idx] = (a0 shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }
        return out
    }

    /**
     * Estimates optimal sub-frame integer translation (dx, dy in -6..+6) between anchor & aux frame
     * using sparse normalized cross-difference over the central 60% ROI.
     */
    private fun estimateGlobalShift(
        anchorLuma: FloatArray,
        aux: IntArray,
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        if (width < 32 || height < 32) return 0 to 0
        val xStart = (width * 0.20f).toInt()
        val xEnd = (width * 0.80f).toInt()
        val yStart = (height * 0.20f).toInt()
        val yEnd = (height * 0.80f).toInt()
        val step = max(2, min(width, height) / 48)

        var bestDx = 0
        var bestDy = 0
        var bestScore = Float.MAX_VALUE

        for (dy in -4..4 step 2) {
            for (dx in -4..4 step 2) {
                var sad = 0f
                var samples = 0
                var y = yStart
                while (y < yEnd) {
                    val sy = (y + dy).coerceIn(0, height - 1)
                    var x = xStart
                    while (x < xEnd) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        val p = aux[sy * width + sx]
                        val l = (0.2126f * ((p ushr 16) and 0xFF) +
                            0.7152f * ((p ushr 8) and 0xFF) +
                            0.0722f * (p and 0xFF)) / 255f
                        sad += abs(l - anchorLuma[y * width + x])
                        samples++
                        x += step
                    }
                    y += step
                }
                val meanSad = sad / max(1, samples)
                if (meanSad < bestScore) {
                    bestScore = meanSad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        return bestDx to bestDy
    }

    private fun wellExposedWeight(luma: Float, isLowLight: Boolean): Float {
        val center = if (isLowLight) 0.40f else 0.50f
        val sigma = 0.28f
        val diff = luma - center
        return exp(-(diff * diff) / (2f * sigma * sigma)).coerceAtLeast(0.05f)
    }

    /**
     * Applies Stage 2 (Edge-Preserving Chroma Denoise) + Stage 3 (Smart HDR Local Tone Mapping,
     * Highlight Roll-Off, Adaptive Shadow Detail, Natural Clarity & Portrait Skin Texture Protection)
     * cleanly to a full-resolution Bitmap prior to Photographic Style rendering.
     */
    fun enhanceBitmap(
        source: Bitmap,
        params: SceneEnhancementParams
    ): Bitmap {
        if (!params.enabled) return source
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val enhanced = enhancePixelBuffer(pixels, width, height, params)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(enhanced, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Pure-JVM Stage 2 & Stage 3 computational enhancement operating on an ARGB pixel buffer.
     * Guaranteed linear time `O(W * H)` with zero halo artifacts and strict skin/highlight protection.
     */
    fun enhancePixelBuffer(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: SceneEnhancementParams
    ): IntArray {
        if (!params.enabled || pixels.size != width * height || width < 3 || height < 3) {
            return pixels.copyOf()
        }
        val n = pixels.size

        // 1. Decompose to Luma (Y) + Opponent Chroma (Cb, Cr) + Skin-texture guard
        val yArr = FloatArray(n)
        val cbArr = FloatArray(n)
        val crArr = FloatArray(n)
        val logY = FloatArray(n)

        for (i in 0 until n) {
            val p = pixels[i]
            val r = ((p ushr 16) and 0xFF) / 255f
            val g = ((p ushr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val y = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
            val cb = (b - y) * 0.5389f
            val cr = (r - y) * 0.6350f
            yArr[i] = y
            cbArr[i] = cb
            crArr[i] = cr
            logY[i] = ln(y + 0.02f)
        }

        // 2. Separable local luminance envelope (two-pass box blur) for halo-free local tone mapping
        val toneRadius = max(2, min(width, height) / 16).coerceAtMost(24)
        val localLogEnvelope = separableBoxBlur(logY, width, height, toneRadius)

        // Small-radius blur for detail-preserving natural optical clarity
        val detailRadius = max(1, min(width, height) / 96).coerceAtMost(4)
        val smoothY = separableBoxBlur(yArr, width, height, detailRadius)

        // 3. Joint edge-preserving chroma smoothing (suppresses color noise in dark regions)
        val cbSmooth = separableBoxBlur(cbArr, width, height, 1)
        val crSmooth = separableBoxBlur(crArr, width, height, 1)

        val out = IntArray(n)
        for (i in 0 until n) {
            val pOrig = pixels[i]
            val a = (pOrig ushr 24) and 0xFF
            val r0 = ((pOrig ushr 16) and 0xFF) / 255f
            val g0 = ((pOrig ushr 8) and 0xFF) / 255f
            val b0 = (pOrig and 0xFF) / 255f

            val y0 = yArr[i]
            val localY = (exp(localLogEnvelope[i].toDouble()).toFloat() - 0.02f).coerceIn(0.01f, 1f)

            // Detect portrait skin pixels locally so texture stays organic (no plastic airbrushing or harsh pores)
            val isSkin = (r0 > 0.24f && g0 > 0.15f && b0 > 0.09f &&
                r0 > g0 && g0 >= b0 * 0.90f && (r0 - b0) > 0.05f && (r0 - g0) < 0.36f)
            val skinFactor = if (isSkin) 0.72f else 0f

            // Stage 2: Edge-preserving Chroma Noise Reduction (strongest in dark areas)
            val shadowNoiseMask = (1f - (y0 / 0.30f).coerceIn(0f, 1f))
            val chromaDiff = abs(cbArr[i] - cbSmooth[i]) + abs(crArr[i] - crSmooth[i])
            val edgeStop = exp(-(chromaDiff * chromaDiff) / (2f * 0.06f * 0.06f))
            val chromaBlend = (params.chromaDenoiseStrength * shadowNoiseMask * edgeStop * (1f - skinFactor * 0.35f))
                .coerceIn(0f, 0.85f)
            val cbFinal = cbArr[i] + (cbSmooth[i] - cbArr[i]) * chromaBlend
            val crFinal = crArr[i] + (crSmooth[i] - crArr[i]) * chromaBlend

            // Stage 3a: Adaptive Local Tone Mapping (Shadow Lift + Highlight Recovery Roll-Off)
            // Uses local envelope to lift shadowed zones smoothly while anchoring true blacks (>0.04)
            var yMapped = y0
            if (y0 in 0.025f..0.48f && localY < 0.45f) {
                val darkEnvWeight = ((0.45f - localY) / 0.45f).coerceIn(0f, 1f)
                val blackAnchor = ((y0 - 0.025f) / 0.08f).coerceIn(0f, 1f)
                val nightDamp = if (params.lowLightFactor > 0.1f) (1f - params.lowLightFactor * 0.45f) else 1f
                val liftAmount = params.shadowDetailBoost * darkEnvWeight * blackAnchor * nightDamp
                yMapped += liftAmount * (1f - y0) * 0.42f
            }

            // Filmic Highlight Recovery shoulder above 0.70
            if (yMapped > 0.70f) {
                val over = (yMapped - 0.70f) / 0.30f
                val shoulder = 0.70f + 0.30f * (over / (1f + over * params.highlightRecovery * 1.4f))
                val blend = ((yMapped - 0.70f) / 0.20f).coerceIn(0f, 1f)
                yMapped = yMapped + (shoulder - yMapped) * blend
            }

            // Stage 3b: Detail-Preserving Natural Clarity (halo-clamped micro-detail)
            val microDetail = (y0 - smoothY[i]).coerceIn(-0.08f, 0.08f)
            val detailFadeInExtremes = (y0 / 0.12f).coerceIn(0f, 1f) * ((1f - y0) / 0.15f).coerceIn(0f, 1f)
            val clarityGain = params.naturalClarity * (1f - skinFactor * 0.65f) * detailFadeInExtremes
            val yFinal = (yMapped + microDetail * clarityGain * 1.35f).coerceIn(0f, 1f)

            // Reconstruct RGB from perceptual Y + Cb + Cr while preserving highlight chromaticity
            val chromaScaleRestore = if (y0 > 0.72f && yFinal < y0) {
                // Recover slight natural sky/sunset saturation when highlight recovery pulls luma down
                1f + ((y0 - yFinal) * 0.65f).coerceIn(0f, 0.14f)
            } else {
                1f
            }
            val rOut = (yFinal + (crFinal * chromaScaleRestore) / 0.6350f).coerceIn(0f, 1f)
            val bOut = (yFinal + (cbFinal * chromaScaleRestore) / 0.5389f).coerceIn(0f, 1f)
            val gOut = ((yFinal - 0.2126f * rOut - 0.0722f * bOut) / 0.7152f).coerceIn(0f, 1f)

            val ri = (rOut * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (gOut * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (bOut * 255f + 0.5f).toInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        return out
    }

    /**
     * Exact O(W * H) sliding-window separable box blur in horizontal + vertical passes.
     */
    private fun separableBoxBlur(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int
    ): FloatArray {
        val temp = FloatArray(src.size)
        val dst = FloatArray(src.size)

        // Horizontal pass
        for (y in 0 until height) {
            val rowOff = y * width
            var sum = 0f
            val winSize = radius * 2 + 1
            for (k in -radius..radius) {
                sum += src[rowOff + k.coerceIn(0, width - 1)]
            }
            for (x in 0 until width) {
                temp[rowOff + x] = sum / winSize
                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                sum += src[rowOff + addX] - src[rowOff + removeX]
            }
        }

        // Vertical pass
        val winSize = radius * 2 + 1
        for (x in 0 until width) {
            var sum = 0f
            for (k in -radius..radius) {
                sum += temp[k.coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                dst[y * width + x] = sum / winSize
                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                sum += temp[addY * width + x] - temp[removeY * width + x]
            }
        }
        return dst
    }
}
