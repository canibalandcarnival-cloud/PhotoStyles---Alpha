package com.example.colorscience

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class SceneColorMetrics(
    val meanLightness: Float,
    val meanWarmthB: Float,
    val meanChroma: Float,
    val shadowDetailRatio: Float,
    val highlightHeadroomRatio: Float,
    val skinPixelFraction: Float,
    val skinHueDriftDegrees: Float
)

/**
 * Pure Kotlin reference implementation matching [PhotographicStyleShaderSource] equation-for-equation:
 * - D65 sRGB <-> Linear RGB electro-optical transfer
 * - CIE XYZ <-> Bradford LMS physiological cone response chromatic adaptation
 * - CIE XYZ <-> CIELAB perceptual lightness & opponent chrominance
 * - Melanin-locus multi-spectral skin detection across Fitzpatrick I-VI
 * - Melanin hue-angle lock guaranteeing zero skin hue drift across all styles and adjustments
 * - Filmic tonal curve: smooth parametric shadow toe, midtone Hermite pivot, and asymptotic rational shoulder
 * - Selective nature color grading (polarizing sky blue-cyan depth, natural foliage preservation)
 * - Luminance-aware saturation roll-off in extreme shadows and specular highlights
 * - Soft gamut mapping preserving perceptual lightness
 */
object PhotographicStyleCpuProcessor {

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).coerceAtLeast(0f).pow(2.4f)

    private fun linearToSrgb(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            clamped * 12.92f
        } else {
            1.055f * clamped.pow(1f / 2.4f) - 0.055f
        }
    }

    private fun labF(t: Float): Float {
        val eps = 0.00885645168f
        val kappa = 7.787037037f
        return if (t > eps) cbrt(t) else (kappa * t + 16f / 116f)
    }

    private fun labFInv(f: Float): Float {
        val delta = 6f / 29f
        return if (f > delta) f * f * f else (3f * delta * delta * (f - 16f / 116f))
    }

    private fun rgbToHsv(r: Float, g: Float, b: Float, out: FloatArray) {
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val d = maxC - minC
        val v = maxC
        val s = if (maxC > 1e-6f) d / maxC else 0f
        var h = 0f
        if (d > 1e-6f) {
            h = when (maxC) {
                r -> ((g - b) / d) + (if (g < b) 6f else 0f)
                g -> ((b - r) / d) + 2f
                else -> ((r - g) / d) + 4f
            }
            h /= 6f
        }
        out[0] = h
        out[1] = s
        out[2] = v
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
        val hh = ((h % 1f) + 1f) % 1f * 6f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        when (i % 6) {
            0 -> { out[0] = v; out[1] = t; out[2] = p }
            1 -> { out[0] = q; out[1] = v; out[2] = p }
            2 -> { out[0] = p; out[1] = v; out[2] = t }
            3 -> { out[0] = p; out[1] = q; out[2] = v }
            4 -> { out[0] = t; out[1] = p; out[2] = v }
            else -> { out[0] = v; out[1] = p; out[2] = q }
        }
    }

    /**
     * Melanin-locus skin detection across Fitzpatrick I through VI.
     */
    fun computeSkinWeight(
        rSrgb: Float,
        gSrgb: Float,
        bSrgb: Float,
        labA: Float,
        labB: Float,
        hsvScratch: FloatArray = FloatArray(3),
        labL: Float = -1f
    ): Float {
        val r = rSrgb.coerceIn(0f, 1f)
        val g = gSrgb.coerceIn(0f, 1f)
        val b = bSrgb.coerceIn(0f, 1f)
        rgbToHsv(r, g, b, hsvScratch)

        val lStar = if (labL >= 0f) {
            labL
        } else {
            val rL = srgbToLinear(r)
            val gL = srgbToLinear(g)
            val bL = srgbToLinear(b)
            val y = rL * 0.2126729f + gL * 0.7151522f + bL * 0.0721750f
            116f * labF(y) - 16f
        }

        var angleDeg = Math.toDegrees(kotlin.math.atan2(labB.toDouble(), labA.toDouble())).toFloat()
        if (angleDeg < 0f) angleDeg += 360f

        val angleDist = abs(angleDeg - 48.0f)
        val angleMask = 1f - smoothstep(12.0f, 30.0f, angleDist)

        val chroma = sqrt(labA * labA + labB * labB)
        val chromaMask = smoothstep(3.5f, 9.0f, chroma) * (1f - smoothstep(46.0f, 68.0f, chroma))

        val lightMask = smoothstep(8.0f, 18.0f, lStar) * (1f - smoothstep(88.0f, 96.0f, lStar))
        val aMask = smoothstep(1.5f, 5.0f, labA) * (1f - smoothstep(36.0f, 50.0f, labA))

        val hDeg = hsvScratch[0] * 360f
        val hDist = if (hDeg > 300f) 360f - hDeg else hDeg
        val hsvMask = (1f - smoothstep(28.0f, 52.0f, hDist)) *
            smoothstep(0.06f, 0.15f, hsvScratch[1]) *
            (1f - smoothstep(0.70f, 0.86f, hsvScratch[1])) *
            smoothstep(0.08f, 0.18f, hsvScratch[2])

        val ratio = labB / max(labA, 0.8f)
        val ratioMask = smoothstep(0.40f, 0.80f, ratio) * (1f - smoothstep(2.2f, 3.0f, ratio))

        return (angleMask * chromaMask * lightMask * aMask * hsvMask * ratioMask * 1.6f).coerceIn(0f, 1f)
    }

    fun processPixelBuffer(
        pixels: IntArray,
        uniforms: EngineUniforms
    ): IntArray = processPixelBuffer(pixels, width = pixels.size, height = 1, uniforms = uniforms)

    fun processPixelBuffer(
        pixels: IntArray,
        width: Int,
        height: Int,
        uniforms: EngineUniforms
    ): IntArray {
        if (uniforms.bypass) return pixels.copyOf()
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)

        val inputPixels = if (uniforms.softSkinAmount > 0.005f && w >= 4 && h >= 4) {
            applySoftSkinPass(pixels, w, h, uniforms.softSkinAmount)
        } else {
            pixels
        }

        val hsvScratch = FloatArray(3)
        val rgbScratch = FloatArray(3)
        val graded = IntArray(pixels.size) { i ->
            val x = i % w
            val y = i / w
            processPixelArgb(inputPixels[i], uniforms, hsvScratch, rgbScratch, x = x, y = y)
        }

        return if (uniforms.glowAmount > 0.005f && w >= 4 && h >= 4) {
            applyGlowPass(graded, w, h, uniforms.glowAmount)
        } else {
            graded
        }
    }

    private fun applySoftSkinPass(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
        val hsvScratch = FloatArray(3)
        val out = IntArray(pixels.size)
        val offsets = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0),
            intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(2, 2), intArrayOf(-2, 2)
        )
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val c = pixels[idx]
                val a = (c ushr 24) and 0xFF
                val r0 = ((c ushr 16) and 0xFF) / 255f
                val g0 = ((c ushr 8) and 0xFF) / 255f
                val b0 = (c and 0xFF) / 255f

                val rL = srgbToLinear(r0)
                val gL = srgbToLinear(g0)
                val bL = srgbToLinear(b0)
                val xXyz = rL * 0.4124564f + gL * 0.3575761f + bL * 0.1804375f
                val yXyz = rL * 0.2126729f + gL * 0.7151522f + bL * 0.0721750f
                val zXyz = rL * 0.0193339f + gL * 0.1191920f + bL * 0.9503041f
                val fy = labF(yXyz)
                val labL = 116f * fy - 16f
                val labA = 500f * (labF(xXyz / 0.95047f) - fy)
                val labB = 200f * (fy - labF(zXyz / 1.08883f))
                val skinW = computeSkinWeight(r0, g0, b0, labA, labB, hsvScratch, labL)

                if (skinW < 0.02f) {
                    out[idx] = c
                    continue
                }

                var wSum = 1f
                var rAcc = r0
                var gAcc = g0
                var bAcc = b0
                for (k in offsets.indices) {
                    val nx = (x + offsets[k][0]).coerceIn(0, width - 1)
                    val ny = (y + offsets[k][1]).coerceIn(0, height - 1)
                    val np = pixels[ny * width + nx]
                    val nr = ((np ushr 16) and 0xFF) / 255f
                    val ng = ((np ushr 8) and 0xFF) / 255f
                    val nb = (np and 0xFF) / 255f
                    val dr = nr - r0
                    val dg = ng - g0
                    val db = nb - b0
                    val dist2 = dr * dr + dg * dg + db * db
                    val sigma = if (k < 4) 28f else 22f
                    val w = kotlin.math.exp(-dist2 * sigma)
                    wSum += w
                    rAcc += nr * w
                    gAcc += ng * w
                    bAcc += nb * w
                }
                val blend = (skinW * amount * 0.72f).coerceIn(0f, 0.78f)
                val rf = ((mix(r0, rAcc / wSum, blend)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gf = ((mix(g0, gAcc / wSum, blend)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bf = ((mix(b0, bAcc / wSum, blend)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                out[idx] = (a shl 24) or (rf shl 16) or (gf shl 8) or bf
            }
        }
        return out
    }

    private fun applyGlowPass(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
        val out = IntArray(pixels.size)
        val taps = arrayOf(
            intArrayOf(3, 3), intArrayOf(-3, 3),
            intArrayOf(3, -3), intArrayOf(-3, -3),
            intArrayOf(6, 0), intArrayOf(-6, 0),
            intArrayOf(0, 6), intArrayOf(0, -6)
        )
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val c = pixels[idx]
                val a = (c ushr 24) and 0xFF
                val r0 = ((c ushr 16) and 0xFF) / 255f
                val g0 = ((c ushr 8) and 0xFF) / 255f
                val b0 = (c and 0xFF) / 255f

                var bR = 0f
                var bG = 0f
                var bB = 0f
                for (t in taps) {
                    val nx = (x + t[0]).coerceIn(0, width - 1)
                    val ny = (y + t[1]).coerceIn(0, height - 1)
                    val np = pixels[ny * width + nx]
                    bR += ((np ushr 16) and 0xFF) / 255f
                    bG += ((np ushr 8) and 0xFF) / 255f
                    bB += (np and 0xFF) / 255f
                }
                bR *= 0.125f
                bG *= 0.125f
                bB *= 0.125f
                val bloomLum = bR * 0.2126f + bG * 0.7152f + bB * 0.0722f
                val bloomGate = smoothstep(0.54f, 0.94f, bloomLum)
                val scale = bloomGate * amount * 0.36f
                val cR = (bR * scale).coerceIn(0f, 0.65f)
                val cG = (bG * scale).coerceIn(0f, 0.65f)
                val cB = (bB * scale).coerceIn(0f, 0.65f)

                val rf = ((1f - (1f - r0) * (1f - cR)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gf = ((1f - (1f - g0) * (1f - cG)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bf = ((1f - (1f - b0) * (1f - cB)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                out[idx] = (a shl 24) or (rf shl 16) or (gf shl 8) or bf
            }
        }
        return out
    }

    fun processPixelArgb(
        argb: Int,
        uniforms: EngineUniforms,
        hsvScratch: FloatArray = FloatArray(3),
        rgbScratch: FloatArray = FloatArray(3),
        x: Int = 0,
        y: Int = 0
    ): Int {
        if (uniforms.bypass) return argb
        val a = (argb ushr 24) and 0xFF
        val rIn = ((argb ushr 16) and 0xFF) / 255f
        val gIn = ((argb ushr 8) and 0xFF) / 255f
        val bIn = (argb and 0xFF) / 255f

        // 1. Linearize light
        val rLin = srgbToLinear(rIn)
        val gLin = srgbToLinear(gIn)
        val bLin = srgbToLinear(bIn)

        val x0 = rLin * 0.4124564f + gLin * 0.3575761f + bLin * 0.1804375f
        val y0 = rLin * 0.2126729f + gLin * 0.7151522f + bLin * 0.0721750f
        val z0 = rLin * 0.0193339f + gLin * 0.1191920f + bLin * 0.9503041f

        val fy0 = labF(y0)
        val labL0 = 116f * fy0 - 16f
        val labA0 = 500f * (labF(x0 / 0.95047f) - fy0)
        val labB0 = 200f * (fy0 - labF(z0 / 1.08883f))

        // 2. Multi-spectral skin-tone detection & melanin hue anchor
        val skinWeight = computeSkinWeight(rIn, gIn, bIn, labA0, labB0, hsvScratch, labL0)
        val origSkinAngle = kotlin.math.atan2(labB0.toDouble(), labA0.toDouble()).toFloat()

        // 3. Bradford LMS cone chromatic adaptation (Warmth & Tint) with neutral highlight preservation
        val lum0 = y0.coerceIn(0f, 1f)
        val highlightGuard = 1f - smoothstep(0.76f, 0.98f, lum0)
        val skinWarmGuard = if (uniforms.warmthShift < 0f) {
            1f - skinWeight * uniforms.coolSkinGuard
        } else {
            1f - skinWeight * 0.16f
        }
        val effWarmth = uniforms.warmthShift * mix(0.35f, 1f, highlightGuard) * skinWarmGuard
        val effTint = uniforms.tintShift * mix(0.40f, 1f, highlightGuard)

        val coneGainsL = 1f + effWarmth * 0.22f - effTint * 0.06f
        val coneGainsM = 1f + effWarmth * 0.04f + effTint * 0.16f
        val coneGainsS = 1f - effWarmth * 0.28f + effTint * 0.06f

        val l0 = x0 * 0.8951000f + y0 * 0.2664000f - z0 * 0.1614000f
        val m0 = -x0 * 0.7502000f + y0 * 1.7135000f + z0 * 0.0367000f
        val s0 = x0 * 0.0389000f - y0 * 0.0685000f + z0 * 1.0296000f

        val lAdapted = l0 * coneGainsL
        val mAdapted = m0 * coneGainsM
        val sAdapted = s0 * coneGainsS

        val xAdapted = lAdapted * 0.9869929f - mAdapted * 0.1470543f + sAdapted * 0.1599627f
        val yAdapted = lAdapted * 0.4323053f + mAdapted * 0.5183603f + sAdapted * 0.0492912f
        val zAdapted = -lAdapted * 0.0085287f + mAdapted * 0.0400428f + sAdapted * 0.9684867f

        val yNorm = max(yAdapted, 1e-5f)
        val lumPreserve = mix(1f, lum0 / yNorm, 0.85f)
        val xWb = xAdapted * lumPreserve
        val yWb = yAdapted * lumPreserve
        val zWb = zAdapted * lumPreserve

        val rWb = xWb * 3.2404542f + yWb * -1.5371385f + zWb * -0.4985314f
        val gWb = xWb * -0.9692660f + yWb * 1.8760108f + zWb * 0.0415560f
        val bWb = xWb * 0.0556434f + yWb * -0.2040259f + zWb * 1.0572252f

        // 4. Perceptual CIELAB space
        val fy = labF(yWb)
        val labL = 116f * fy - 16f
        val labA = 500f * (labF(xWb / 0.95047f) - fy)
        val labB = 200f * (fy - labF(zWb / 1.08883f))

        var lNorm = (labL / 100f).coerceIn(0f, 1f)

        // Stage 3 (Pre-Style): Pro Max Smart HDR & Night Tone Conditioning
        if (uniforms.proMaxEnabled) {
            val blackAnchor = smoothstep(0.025f, 0.11f, lNorm)
            val hdrShadowMask = smoothstep(0.46f, 0.05f, lNorm) * blackAnchor
            val nightDamp = 1f - uniforms.lowLightFactor * 0.42f
            lNorm += uniforms.hdrShadowLift * 0.24f * hdrShadowMask * nightDamp

            if (lNorm > 0.68f) {
                val overHdr = (lNorm - 0.68f) / 0.32f
                val hdrComp = 0.68f + 0.32f * (overHdr / (1f + overHdr * uniforms.hdrHighlightRecovery * 1.35f))
                lNorm = mix(lNorm, hdrComp, smoothstep(0.68f, 0.90f, lNorm))
            }

            val clarityBand = smoothstep(0.14f, 0.38f, lNorm) * (1f - smoothstep(0.72f, 0.95f, lNorm))
            val presenceBoost = uniforms.naturalClarity * (1f - skinWeight * 0.65f) * clarityBand
            lNorm = (lNorm + (lNorm - 0.50f) * presenceBoost * 0.55f).coerceIn(0f, 1f)
        }

        // Stage 4 & 5: Filmic Tonal Shaping (Shadow Toe, Midtone Lift, Hermite S-curve, Highlight Shoulder)
        val shadowMask = smoothstep(0.48f, 0f, lNorm) * smoothstep(0f, 0.07f, lNorm)
        val shadowAtten = 1f - skinWeight * uniforms.skinProtection * (if (uniforms.shadowLift < 0f) 0.50f else 0.22f)
        lNorm += uniforms.shadowLift * 0.24f * shadowMask * shadowAtten

        val midMask = smoothstep(0.03f, 0.35f, lNorm) * (1f - smoothstep(0.68f, 0.98f, lNorm))
        lNorm += uniforms.midtoneLift * midMask

        val effContrast = mix(
            uniforms.contrast,
            1f + (uniforms.contrast - 1f) * 0.40f,
            skinWeight * uniforms.skinProtection
        )
        val centered = lNorm - 0.46f
        val curved = 0.46f + centered * effContrast
        val extremeBlend = smoothstep(0.01f, 0.18f, lNorm) * (1f - smoothstep(0.74f, 1f, lNorm))
        lNorm = mix(lNorm, curved, extremeBlend)

        if (lNorm > 0.68f) {
            val over = (lNorm - 0.68f) / 0.32f
            val compressed = 0.68f + 0.32f * (over / (1f + over * uniforms.highlightShoulder))
            lNorm = mix(lNorm, compressed, smoothstep(0.68f, 0.90f, lNorm))
        }
        lNorm = lNorm.coerceIn(0f, 1f)

        // 6. 2D Palette Hue rotation with skin protection
        val srgbWbR = linearToSrgb(rWb)
        val srgbWbG = linearToSrgb(gWb)
        val srgbWbB = linearToSrgb(bWb)
        rgbToHsv(srgbWbR, srgbWbG, srgbWbB, hsvScratch)
        val hDeg = hsvScratch[0] * 360f
        val satVal = hsvScratch[1]
        val valVal = hsvScratch[2]

        var abA = labA
        var abB = labB
        val effHueShift = uniforms.paletteHueShiftDeg * (1f - skinWeight * uniforms.skinProtection * 0.85f)
        if (abs(effHueShift) > 0.05f && satVal > 0.03f) {
            hsvToRgb(hsvScratch[0] + effHueShift / 360f, satVal, valVal, rgbScratch)
            val rRL = srgbToLinear(rgbScratch[0])
            val gRL = srgbToLinear(rgbScratch[1])
            val bRL = srgbToLinear(rgbScratch[2])
            val xR = rRL * 0.4124564f + gRL * 0.3575761f + bRL * 0.1804375f
            val yR = rRL * 0.2126729f + gRL * 0.7151522f + bRL * 0.0721750f
            val zR = rRL * 0.0193339f + gRL * 0.1191920f + bRL * 0.9503041f
            val fyR = labF(yR)
            val aR = 500f * (labF(xR / 0.95047f) - fyR)
            val bR = 200f * (fyR - labF(zR / 1.08883f))
            val cOrig = sqrt(abA * abA + abB * abB)
            val cRot = sqrt(aR * aR + bR * bR)
            if (cRot > 0.5f && cOrig > 0.5f) {
                val scale = cOrig / cRot
                abA = aR * scale
                abB = bR * scale
            }
        }

        // 7. Nature color families: sky and foliage
        val skyMask = smoothstep(185f, 205f, hDeg) * (1f - smoothstep(238f, 258f, hDeg))
        val greenMask = smoothstep(76f, 96f, hDeg) * (1f - smoothstep(145f, 168f, hDeg))
        val natureBoost = 1f + uniforms.skyFoliageBoost * max(skyMask, greenMask) * smoothstep(0.08f, 0.42f, satVal)
        if (skyMask > 0.01f && uniforms.skyFoliageBoost > 0.01f) {
            lNorm = (lNorm - 0.035f * uniforms.skyFoliageBoost * skyMask * smoothstep(0.12f, 0.55f, satVal)).coerceIn(0f, 1f)
        }

        // 8. Vibrance + Saturation + Luminance-Aware Chroma Roll-off
        val chroma = sqrt(abA * abA + abB * abB)
        val chromaNorm = (chroma / 72f).coerceIn(0f, 1f)
        val vibranceGate = (uniforms.chromaScale / 0.35f).coerceIn(0f, 1f)
        val vibranceGain = 1f + (uniforms.vibrance * vibranceGate) * (1f - chromaNorm) * (1f - chromaNorm)
        val baseChromaGain = uniforms.chromaScale * vibranceGain

        val targetChromaGain = baseChromaGain * natureBoost
        val skinMaxChromaGain = if (targetChromaGain > 1f) {
            1f + (targetChromaGain - 1f) * (1f - uniforms.skinProtection * 0.78f)
        } else {
            targetChromaGain
        }
        val finalChromaGain = mix(targetChromaGain, skinMaxChromaGain, skinWeight)

        // Natural desaturation in deep shadows and specular highlights
        val satRollOff = (1f - smoothstep(0.78f, 0.98f, lNorm) * 0.70f) * smoothstep(0.015f, 0.10f, lNorm)
        val abScaledA = abA * (finalChromaGain * satRollOff)
        val abScaledB = abB * (finalChromaGain * satRollOff)

        // Ambient warmth and tint modulation
        val labWarmMod = effWarmth * 14f * (1f - smoothstep(0.78f, 0.98f, lNorm))
        val labTintMod = effTint * 10f * (1f - skinWeight * 0.45f)

        val labAGraded = abScaledA + labTintMod
        val labBGraded = abScaledB + labWarmMod

        // 9. Melanin Hue-Angle Lock: Project skin pixels to original melanin angle
        val gradedChroma = sqrt(labAGraded * labAGraded + labBGraded * labBGraded)
        val skinLockedA = gradedChroma * kotlin.math.cos(origSkinAngle.toDouble()).toFloat()
        val skinLockedB = gradedChroma * kotlin.math.sin(origSkinAngle.toDouble()).toFloat()
        val skinLockBlend = (skinWeight * uniforms.skinProtection).coerceIn(0f, 1f)

        var labAOut = mix(labAGraded, skinLockedA, skinLockBlend)
        var labBOut = mix(labBGraded, skinLockedB, skinLockBlend)

        if (uniforms.filmAmount > 0.005f) {
            val toeLift = smoothstep(0.34f, 0f, lNorm) * 0.032f * uniforms.filmAmount
            val filmCurve = lNorm + (lNorm - 0.50f) * (0.14f * uniforms.filmAmount) *
                smoothstep(0.08f, 0.30f, lNorm) * (1f - smoothstep(0.76f, 0.98f, lNorm))
            val highlightCeiling = mix(1f, 0.965f, uniforms.filmAmount)
            lNorm = min(filmCurve + toeLift, highlightCeiling).coerceIn(0f, 1f)

            val shadowSplit = smoothstep(0.32f, 0.04f, lNorm) * uniforms.filmAmount
            val highlightSplit = smoothstep(0.56f, 0.90f, lNorm) * uniforms.filmAmount
            val skinDamp = 1f - skinWeight * 0.45f
            labAOut += -1.4f * shadowSplit + 0.9f * highlightSplit * skinDamp
            labBOut += -2.8f * shadowSplit + 3.6f * highlightSplit * skinDamp
        }

        // 10. LAB -> XYZ -> Linear RGB -> softGamutMap -> sRGB
        val fyOut = (lNorm * 100f + 16f) / 116f
        val fxOut = labAOut / 500f + fyOut
        val fzOut = fyOut - labBOut / 200f

        val xOut = 0.95047f * labFInv(fxOut)
        val yOut = 1.00000f * labFInv(fyOut)
        val zOut = 1.08883f * labFInv(fzOut)

        var rOut = xOut * 3.2404542f + yOut * -1.5371385f + zOut * -0.4985314f
        var gOut = xOut * -0.9692660f + yOut * 1.8760108f + zOut * 0.0415560f
        var bOut = xOut * 0.0556434f + yOut * -0.2040259f + zOut * 1.0572252f

        val lumOut = rOut * 0.2126729f + gOut * 0.7151522f + bOut * 0.0721750f
        val maxC = max(rOut, max(gOut, bOut))
        if (maxC > 1f) {
            val scale = ((1f - lumOut) / max(maxC - lumOut, 1e-5f)).coerceIn(0f, 1f)
            rOut = mix(lumOut, rOut, scale)
            gOut = mix(lumOut, gOut, scale)
            bOut = mix(lumOut, bOut, scale)
        }
        val minC = min(rOut, min(gOut, bOut))
        if (minC < 0f) {
            val scaleNeg = (lumOut / max(lumOut - minC, 1e-5f)).coerceIn(0f, 1f)
            rOut = mix(lumOut, rOut, scaleNeg)
            gOut = mix(lumOut, gOut, scaleNeg)
            bOut = mix(lumOut, bOut, scaleNeg)
        }

        var rSrgbOut = linearToSrgb(rOut)
        var gSrgbOut = linearToSrgb(gOut)
        var bSrgbOut = linearToSrgb(bOut)

        if (uniforms.grainAmount > 0.005f) {
            val hA = kotlin.math.sin(x * 12.9898 + y * 78.233) * 43758.5453
            val nA = (hA - kotlin.math.floor(hA)).toFloat()
            val hB = kotlin.math.sin((x + 17.3) * 39.346 + (y + 41.7) * 11.135) * 24634.6345
            val nB = (hB - kotlin.math.floor(hB)).toFloat()
            val zeroMeanGrain = (nA + nB) * 0.5f - 0.5f
            val grainMask = 0.25f + 0.75f * smoothstep(0.04f, 0.24f, lNorm) * (1f - smoothstep(0.78f, 0.98f, lNorm))
            val deltaGrain = zeroMeanGrain * uniforms.grainAmount * 0.14f * grainMask
            rSrgbOut = (rSrgbOut + deltaGrain).coerceIn(0f, 1f)
            gSrgbOut = (gSrgbOut + deltaGrain).coerceIn(0f, 1f)
            bSrgbOut = (bSrgbOut + deltaGrain).coerceIn(0f, 1f)
        }

        val rFinal = (rSrgbOut * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gFinal = (gSrgbOut * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bFinal = (bSrgbOut * 255f + 0.5f).toInt().coerceIn(0, 255)

        return (a shl 24) or (rFinal shl 16) or (gFinal shl 8) or bFinal
    }

    fun applyStyleToBitmap(source: Bitmap, parameters: StyleParameters): Bitmap {
        val uniforms = parameters.toEngineUniforms()
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val processed = processPixelBuffer(pixels, width, height, uniforms)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(processed, 0, width, 0, 0, width, height)
        return out
    }

    fun analyzeColorMetrics(original: Bitmap, styled: Bitmap): SceneColorMetrics {
        val width = min(original.width, styled.width)
        val height = min(original.height, styled.height)
        val origPixels = IntArray(width * height)
        val styledPixels = IntArray(width * height)
        original.getPixels(origPixels, 0, width, 0, 0, width, height)
        styled.getPixels(styledPixels, 0, width, 0, 0, width, height)
        return analyzePixelArrays(origPixels, styledPixels)
    }

    fun analyzePixelArrays(origPixels: IntArray, styledPixels: IntArray): SceneColorMetrics {
        val hsvOrig = FloatArray(3)
        val hsvStyled = FloatArray(3)
        var sumL = 0.0
        var sumB = 0.0
        var sumC = 0.0
        var shadowVisibleCount = 0
        var highlightSafeCount = 0
        var skinCount = 0
        var skinHueDriftSum = 0.0

        val step = max(1, origPixels.size / 8000)
        var sampled = 0

        for (i in origPixels.indices step step) {
            sampled++
            val pOrig = origPixels[i]
            val pStyled = styledPixels[i]

            val r0 = ((pOrig ushr 16) and 0xFF) / 255f
            val g0 = ((pOrig ushr 8) and 0xFF) / 255f
            val b0 = (pOrig and 0xFF) / 255f

            val r1 = ((pStyled ushr 16) and 0xFF) / 255f
            val g1 = ((pStyled ushr 8) and 0xFF) / 255f
            val b1 = (pStyled and 0xFF) / 255f

            val x0 = srgbToLinear(r0) * 0.4124564f + srgbToLinear(g0) * 0.3575761f + srgbToLinear(b0) * 0.1804375f
            val y0 = srgbToLinear(r0) * 0.2126729f + srgbToLinear(g0) * 0.7151522f + srgbToLinear(b0) * 0.0721750f
            val z0 = srgbToLinear(r0) * 0.0193339f + srgbToLinear(g0) * 0.1191920f + srgbToLinear(b0) * 0.9503041f
            val fy0 = labF(y0)
            val l0 = 116f * fy0 - 16f
            val a0 = 500f * (labF(x0 / 0.95047f) - fy0)
            val bLab0 = 200f * (fy0 - labF(z0 / 1.08883f))

            val x1 = srgbToLinear(r1) * 0.4124564f + srgbToLinear(g1) * 0.3575761f + srgbToLinear(b1) * 0.1804375f
            val y1 = srgbToLinear(r1) * 0.2126729f + srgbToLinear(g1) * 0.7151522f + srgbToLinear(b1) * 0.0721750f
            val z1 = srgbToLinear(r1) * 0.0193339f + srgbToLinear(g1) * 0.1191920f + srgbToLinear(b1) * 0.9503041f
            val fy1 = labF(y1)
            val l1 = 116f * fy1 - 16f
            val a1 = 500f * (labF(x1 / 0.95047f) - fy1)
            val bLab1 = 200f * (fy1 - labF(z1 / 1.08883f))

            sumL += l1
            sumB += bLab1
            sumC += sqrt(a1 * a1 + bLab1 * bLab1)

            if (l1 in 4f..25f) shadowVisibleCount++
            if (l1 < 98.5f) highlightSafeCount++

            val skinW = computeSkinWeight(r0, g0, b0, a0, bLab0, hsvOrig, l0)
            if (skinW > 0.30f) {
                skinCount++
                rgbToHsv(r0, g0, b0, hsvOrig)
                rgbToHsv(r1, g1, b1, hsvStyled)
                var dH = abs(hsvStyled[0] - hsvOrig[0]) * 360f
                if (dH > 180f) dH = 360f - dH
                skinHueDriftSum += dH
            }
        }

        val count = max(1, sampled)
        return SceneColorMetrics(
            meanLightness = (sumL / count).toFloat(),
            meanWarmthB = (sumB / count).toFloat(),
            meanChroma = (sumC / count).toFloat(),
            shadowDetailRatio = shadowVisibleCount.toFloat() / count,
            highlightHeadroomRatio = highlightSafeCount.toFloat() / count,
            skinPixelFraction = skinCount.toFloat() / count,
            skinHueDriftDegrees = if (skinCount > 0) (skinHueDriftSum / skinCount).toFloat() else 0f
        )
    }
}
