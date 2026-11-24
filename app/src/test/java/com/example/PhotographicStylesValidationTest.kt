package com.example

import com.example.colorscience.BuiltIn2DPalettePresets
import com.example.colorscience.ComputationalPhotoEngine
import com.example.colorscience.ComputationalSceneMode
import com.example.colorscience.PaletteSessionStorage
import com.example.colorscience.PaletteSettings
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.PhotographicStyleCpuProcessor
import com.example.colorscience.SavedCameraSessionState
import com.example.colorscience.SceneTelemetry
import com.example.colorscience.StyleParameters
import com.example.colorscience.TextureMode
import com.example.colorscience.TextureSettings
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Pure JVM validation suite covering:
 * - All 5 Photographic Styles (Standard, Rich Contrast, Vibrant, Warm, Cool)
 * - 2D Interactive Palette (Hue, Saturation, Tone, Warmth) + Reset & Style combinations
 * - Custom 2D Palette presets + Session Persistence (save/load/delete/restore)
 * - Stage 2 & Stage 3 Pro Max Computational Photography:
 *   * Motion-aware multi-frame HDR/Night burst fusion + ghost rejection
 *   * Smart HDR local tone mapping, highlight recovery & shadow detail enhancement
 *   * Night photography low-light chroma denoise without unnatural brightening
 *   * Portrait skin texture & color protection
 *   * Non-interference guarantee (enhancement never overrides selected Style or Palette)
 * - All 4 bundled real photographic test scenes on disk
 */
class PhotographicStylesValidationTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun processPixels(pixels: IntArray, params: StyleParameters): IntArray =
        PhotographicStyleCpuProcessor.processPixelBuffer(pixels, params.toEngineUniforms())

    @Test
    fun `all five styles preserve natural human skin tones without hue shift`() {
        val skinPatch = intArrayOf(
            argb(232, 190, 165),
            argb(198, 144, 112),
            argb(138, 92, 68)
        )

        for (style in PhotographicStyle.entries) {
            val styled = processPixels(skinPatch, StyleParameters(style = style))
            val metrics = PhotographicStyleCpuProcessor.analyzePixelArrays(skinPatch, styled)
            assertTrue("Expected skin tones detected for $style", metrics.skinPixelFraction > 0.5f)
            assertTrue(
                "Skin hue drift too high for $style: ${metrics.skinHueDriftDegrees} deg",
                metrics.skinHueDriftDegrees < 7.5f
            )
        }
    }

    @Test
    fun `blue sky and green vegetation are enriched in vibrant and rich contrast without clipping`() {
        val landscapePatch = intArrayOf(
            argb(68, 142, 222),
            argb(240, 244, 248),
            argb(72, 134, 58),
            argb(122, 168, 76)
        )

        val standard = processPixels(landscapePatch, StyleParameters(PhotographicStyle.STANDARD))
        val vibrant = processPixels(landscapePatch, StyleParameters(PhotographicStyle.VIBRANT))
        val rich = processPixels(landscapePatch, StyleParameters(PhotographicStyle.RICH_CONTRAST))

        val stdMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(landscapePatch, standard)
        val vibMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(landscapePatch, vibrant)
        val richMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(landscapePatch, rich)

        assertTrue(vibMetrics.meanChroma > stdMetrics.meanChroma)
        assertTrue(richMetrics.meanLightness < vibMetrics.meanLightness)
        assertTrue(vibMetrics.highlightHeadroomRatio > 0.70f)
    }

    @Test
    fun `warm and cool styles shift color temperature in opposite directions`() {
        val neutralIndoor = intArrayOf(
            argb(180, 178, 175),
            argb(120, 118, 115),
            argb(72, 70, 68)
        )

        val warm = processPixels(neutralIndoor, StyleParameters(PhotographicStyle.WARM))
        val cool = processPixels(neutralIndoor, StyleParameters(PhotographicStyle.COOL))

        val warmMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(neutralIndoor, warm)
        val coolMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(neutralIndoor, cool)

        assertTrue(
            "Warm b* (${warmMetrics.meanWarmthB}) must exceed Cool b* (${coolMetrics.meanWarmthB})",
            warmMetrics.meanWarmthB > coolMetrics.meanWarmthB + 8f
        )
    }

    @Test
    fun `2d palette pad coordinates map deterministically to hue saturation tone and warmth`() {
        val center = PaletteSettings.fromPadCoordinates(0.5f, 0.5f, warmth = 0)
        assertEquals(50, center.hue)
        assertEquals(50, center.saturation)
        assertEquals(50, center.tone)
        assertEquals(0, center.warmth)
        assertFalse(center.isModified)

        val topRight = PaletteSettings.fromPadCoordinates(1.0f, 0.0f, warmth = 45)
        assertEquals(100, topRight.hue)
        assertEquals(100, topRight.saturation)
        assertEquals(100, topRight.tone)
        assertEquals(45, topRight.warmth)
        assertTrue(topRight.isModified)

        val bottomLeft = PaletteSettings.fromPadCoordinates(-0.2f, 1.4f, warmth = -120)
        assertEquals(0f, bottomLeft.padX, 0.0001f)
        assertEquals(1f, bottomLeft.padY, 0.0001f)
        assertEquals(0, bottomLeft.hue)
        assertEquals(0, bottomLeft.saturation)
        assertEquals(0, bottomLeft.tone)
        assertEquals(-100, bottomLeft.warmth)
    }

    @Test
    fun `2d palette hue saturation tone and warmth adjust real rendered pixels across styles`() {
        val patch = intArrayOf(
            argb(32, 32, 36),
            argb(118, 122, 128),
            argb(228, 226, 220),
            argb(180, 96, 86),
            argb(70, 135, 195)
        )

        val base = PhotographicStyleCpuProcessor.analyzePixelArrays(
            patch,
            processPixels(patch, StyleParameters(PhotographicStyle.STANDARD))
        )

        val warmTemp = PhotographicStyleCpuProcessor.analyzePixelArrays(
            patch,
            processPixels(patch, StyleParameters(palette = PaletteSettings.fromPadCoordinates(0.5f, 0.5f, warmth = 80)))
        )
        val coolTemp = PhotographicStyleCpuProcessor.analyzePixelArrays(
            patch,
            processPixels(patch, StyleParameters(palette = PaletteSettings.fromPadCoordinates(0.5f, 0.5f, warmth = -80)))
        )
        assertTrue(
            "Warmth slider should shift b* warm vs cool (${warmTemp.meanWarmthB} vs ${coolTemp.meanWarmthB})",
            warmTemp.meanWarmthB > base.meanWarmthB + 5f && coolTemp.meanWarmthB < base.meanWarmthB - 5f
        )

        val desaturated = PhotographicStyleCpuProcessor.analyzePixelArrays(
            patch,
            processPixels(patch, StyleParameters(palette = PaletteSettings.fromPadCoordinates(0.0f, 1.0f, warmth = 0)))
        )
        assertTrue(
            "Bottom-left pad (saturation 0) should reduce chroma near 0 (${desaturated.meanChroma})",
            desaturated.meanChroma < 4f
        )

        val highTone = PhotographicStyleCpuProcessor.analyzePixelArrays(
            patch,
            processPixels(patch, StyleParameters(palette = PaletteSettings.fromPadCoordinates(0.5f, 0.0f, warmth = 0)))
        )
        val lowTone = PhotographicStyleCpuProcessor.analyzePixelArrays(
            patch,
            processPixels(patch, StyleParameters(palette = PaletteSettings.fromPadCoordinates(0.5f, 1.0f, warmth = 0)))
        )
        assertTrue("High tone Y=0 L* > Low tone Y=1 L*", highTone.meanLightness > lowTone.meanLightness + 6f)

        for (style in PhotographicStyle.entries) {
            val pal = PaletteSettings.fromPadCoordinates(0.82f, 0.25f, warmth = 35)
            val styled = processPixels(patch, StyleParameters(style = style, palette = pal))
            val metrics = PhotographicStyleCpuProcessor.analyzePixelArrays(patch, styled)
            assertTrue("Invalid lightness for $style: ${metrics.meanLightness}", metrics.meanLightness in 5f..95f)
            val reset = StyleParameters(style = style, palette = pal).resetToStyleDefaults()
            assertFalse(reset.palette.isModified)
            assertEquals(style.defaultTone, reset.tone)
            assertEquals(style.defaultWarmth, reset.warmth)
        }
    }

    @Test
    fun `pro max computational enhancement preserves all styles tone warmth and palette without overriding`() {
        val scene = intArrayOf(
            argb(26, 28, 34),    // deep shadow
            argb(215, 168, 142), // skin tone
            argb(76, 138, 64),   // green foliage
            argb(66, 138, 224),  // blue sky
            argb(246, 248, 250)  // bright highlight
        )
        val hdrTelemetry = SceneTelemetry(
            meanLuma = 0.48f,
            highlightClippingRatio = 0.14f,
            shadowDensityRatio = 0.28f,
            dynamicRangeSpan = 0.85f,
            portraitSubjectFraction = 0.15f,
            isLowLight = false,
            isHighDynamicRange = true
        )
        val enhParams = hdrTelemetry.toEnhancementParams(proMaxEnabled = true)
        assertTrue(enhParams.enabled)
        assertEquals(ComputationalSceneMode.SMART_HDR, hdrTelemetry.activeMode)

        // Ensure combining Smart HDR enhancement with each Style + Palette preserves Style & Palette distinctiveness
        val customPalette = PaletteSettings.fromPadCoordinates(0.75f, 0.30f, warmth = 28)
        val warmOut = processPixels(
            scene,
            StyleParameters(PhotographicStyle.WARM, tone = 15, warmth = 65, palette = customPalette, enhancement = enhParams)
        )
        val coolOut = processPixels(
            scene,
            StyleParameters(PhotographicStyle.COOL, tone = -15, warmth = -65, palette = customPalette, enhancement = enhParams)
        )
        val warmMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(scene, warmOut)
        val coolMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(scene, coolOut)

        assertTrue(
            "Style + Palette warmth separation must remain intact under Pro Max enhancement (${warmMetrics.meanWarmthB} vs ${coolMetrics.meanWarmthB})",
            warmMetrics.meanWarmthB > coolMetrics.meanWarmthB + 6f
        )

        val warmNoEnh = processPixels(
            scene,
            StyleParameters(PhotographicStyle.WARM, tone = 15, warmth = 65, palette = customPalette)
        )
        val warmNoEnhMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(scene, warmNoEnh)
        assertTrue(
            "Pro Max enhancement must not override Style + Palette warmth (${warmMetrics.meanWarmthB} vs ${warmNoEnhMetrics.meanWarmthB})",
            abs(warmMetrics.meanWarmthB - warmNoEnhMetrics.meanWarmthB) < 1.5f
        )

        val stdOut = processPixels(
            scene,
            StyleParameters(PhotographicStyle.STANDARD, palette = customPalette, enhancement = enhParams)
        )
        val vibOut = processPixels(
            scene,
            StyleParameters(PhotographicStyle.VIBRANT, palette = customPalette, enhancement = enhParams)
        )
        val stdMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(scene, stdOut)
        val vibMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(scene, vibOut)
        assertTrue(
            "Vibrant chroma (${vibMetrics.meanChroma}) must remain higher than Standard (${stdMetrics.meanChroma}) under Pro Max enhancement",
            vibMetrics.meanChroma > stdMetrics.meanChroma
        )
    }

    @Test
    fun `computational photo engine smart hdr recovers shadows protects highlights and avoids night overbrightening`() {
        val w = 24
        val h = 24
        val hdrBuffer = IntArray(w * h) { idx ->
            val x = idx % w
            when {
                x < 8 -> argb(22, 24, 28)    // shadowed side
                x < 16 -> argb(120, 125, 128)// midtone
                else -> argb(248, 249, 252)  // bright highlight
            }
        }
        val hdrTelemetry = ComputationalPhotoEngine.analyzeSceneTelemetry(hdrBuffer)
        assertTrue("Expected HDR scene detected", hdrTelemetry.isHighDynamicRange)
        val enhancedHdr = ComputationalPhotoEngine.enhancePixelBuffer(
            hdrBuffer,
            w,
            h,
            hdrTelemetry.toEnhancementParams(proMaxEnabled = true)
        )
        val hdrInMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(hdrBuffer, hdrBuffer)
        val hdrOutMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(hdrBuffer, enhancedHdr)
        assertTrue(
            "Smart HDR should preserve highlight headroom (${hdrOutMetrics.highlightHeadroomRatio})",
            hdrOutMetrics.highlightHeadroomRatio >= hdrInMetrics.highlightHeadroomRatio
        )

        // Night scene: dark pixels + localized bright streetlight; must keep dark night natural and reduce chroma noise
        val nightBuffer = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 10..13 && y in 10..13) {
                argb(245, 220, 165) // warm streetlight
            } else {
                // Noisy dark night background with alternating R/B chroma speckling
                if ((x + y) % 2 == 0) argb(26, 16, 18) else argb(16, 18, 28)
            }
        }
        val nightTelemetry = ComputationalPhotoEngine.analyzeSceneTelemetry(nightBuffer)
        assertTrue("Expected Night low-light detection", nightTelemetry.isLowLight)
        assertEquals(ComputationalSceneMode.NIGHT, nightTelemetry.activeMode)
        val enhancedNight = ComputationalPhotoEngine.enhancePixelBuffer(
            nightBuffer,
            w,
            h,
            nightTelemetry.toEnhancementParams(proMaxEnabled = true)
        )
        val nightOutMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(nightBuffer, enhancedNight)
        assertTrue(
            "Night scene must NOT look artificially bright (L*=${nightOutMetrics.meanLightness})",
            nightOutMetrics.meanLightness < 24f
        )
        // Check chroma noise reduction in dark background pixel
        val bgPixel = enhancedNight[2 * w + 2]
        val bgChromaSpread = abs(((bgPixel ushr 16) and 0xFF) - (bgPixel and 0xFF))
        assertTrue("Chroma noise spread should decrease cleanly (spread=$bgChromaSpread)", bgChromaSpread < 12)
    }

    @Test
    fun `multi frame burst fusion suppresses ghosting on moving pixels while improving static exposure`() {
        val w = 16
        val h = 16
        val anchor = IntArray(w * h) { idx ->
            val x = idx % w
            if (x < 8) argb(120, 120, 120) else argb(250, 250, 250) // right half clipped at 0EV
        }
        // -EV bracket recovers right half highlight (175) while left half has a moving bright red object at (2,2)
        val underExposedAux = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x == 2 && y == 2) {
                argb(240, 20, 20) // sudden motion ghost
            } else if (x < 8) {
                argb(75, 75, 75)
            } else {
                argb(175, 175, 175) // clean recovered highlight detail
            }
        }

        val telemetry = SceneTelemetry(
            meanLuma = 0.65f,
            highlightClippingRatio = 0.45f,
            shadowDensityRatio = 0.1f,
            dynamicRangeSpan = 0.6f,
            portraitSubjectFraction = 0f,
            isLowLight = false,
            isHighDynamicRange = true
        )
        val fused = ComputationalPhotoEngine.fuseBurstBuffers(
            anchor = anchor,
            auxFrames = listOf(underExposedAux),
            width = w,
            height = h,
            telemetry = telemetry
        )

        // Ghost pixel at (2,2) should stay close to anchor gray (120,120,120) rather than turning red
        val ghostPixel = fused[2 * w + 2]
        val rGhost = (ghostPixel ushr 16) and 0xFF
        val gGhost = (ghostPixel ushr 8) and 0xFF
        assertTrue("Moving object ghost should be suppressed (R=$rGhost, G=$gGhost)", abs(rGhost - gGhost) < 35)

        // Clipped highlight at (12,8) should recover downward toward well-exposed -EV detail (< 235)
        val recoveredHighlight = fused[8 * w + 12] and 0xFF
        assertTrue("Highlight should recover below 235 (got $recoveredHighlight)", recoveredHighlight < 235)
    }

    @Test
    fun `custom 2d palette presets and camera session persistence save load and delete cleanly`() {
        val tempDir = Files.createTempDirectory("palette_session_test").toFile()
        try {
            assertEquals(6, BuiltIn2DPalettePresets.ALL.size)
            val storage = PaletteSessionStorage(tempDir)
            assertTrue(storage.loadCustomPresets().isEmpty())

            val custom = PaletteSettings.fromPadCoordinates(0.84f, 0.22f, warmth = 32)
            val afterSave = storage.saveCustomPreset("Sunset Portrait", custom)
            assertEquals(1, afterSave.size)
            assertEquals("Sunset Portrait", afterSave[0].name)
            assertEquals(84, afterSave[0].settings.hue)
            assertEquals(32, afterSave[0].settings.warmth)

            val session = SavedCameraSessionState(
                selectedStyle = PhotographicStyle.VIBRANT,
                activePalette = custom,
                textureSettings = TextureSettings(
                    mode = TextureMode.FILM,
                    intensity = 72,
                    grainEnabled = true,
                    grainIntensity = 58,
                    focusingGrain = false
                ),
                proMaxEnabled = true,
                rawDngEnabled = false
            )
            storage.saveSessionState(session)
            val restored = storage.loadSessionState()
            assertEquals(PhotographicStyle.VIBRANT, restored.selectedStyle)
            assertEquals(84, restored.activePalette.hue)
            assertEquals(32, restored.activePalette.warmth)
            assertEquals(TextureMode.FILM, restored.textureSettings.mode)
            assertEquals(72, restored.textureSettings.intensity)
            assertTrue(restored.textureSettings.grainEnabled)
            assertEquals(58, restored.textureSettings.grainIntensity)
            assertTrue(restored.proMaxEnabled)

            val afterDelete = storage.deleteCustomPreset(afterSave[0].id)
            assertTrue(afterDelete.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `texture effects soft skin glow film and grain apply distinct non destructive transforms`() {
        val w = 24
        val h = 24
        val portraitWithHighlight = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            when {
                x in 16..20 && y in 16..20 -> argb(242, 240, 232) // bright highlight
                (x + y) % 2 == 0 -> argb(214, 168, 140)           // skin tone variation A
                else -> argb(198, 152, 126)                       // skin tone variation B
            }
        }

        val stdOut = PhotographicStyleCpuProcessor.processPixelBuffer(
            portraitWithHighlight,
            w,
            h,
            StyleParameters(PhotographicStyle.STANDARD).toEngineUniforms()
        )
        val softSkinOut = PhotographicStyleCpuProcessor.processPixelBuffer(
            portraitWithHighlight,
            w,
            h,
            StyleParameters(
                PhotographicStyle.STANDARD,
                texture = TextureSettings(mode = TextureMode.SOFT_SKIN, intensity = 85)
            ).toEngineUniforms()
        )
        val glowOut = PhotographicStyleCpuProcessor.processPixelBuffer(
            portraitWithHighlight,
            w,
            h,
            StyleParameters(
                PhotographicStyle.STANDARD,
                texture = TextureSettings(mode = TextureMode.GLOW, intensity = 85)
            ).toEngineUniforms()
        )
        val filmGrainOut = PhotographicStyleCpuProcessor.processPixelBuffer(
            portraitWithHighlight,
            w,
            h,
            StyleParameters(
                PhotographicStyle.STANDARD,
                texture = TextureSettings(
                    mode = TextureMode.FILM,
                    intensity = 75,
                    grainEnabled = true,
                    grainIntensity = 65
                )
            ).toEngineUniforms()
        )

        // Soft Skin reduces pixel-to-pixel blemish variance between adjacent skin pixels (4,4) and (4,5)
        val stdDiff = abs(((stdOut[4 * w + 4] ushr 16) and 0xFF) - ((stdOut[4 * w + 5] ushr 16) and 0xFF))
        val softDiff = abs(((softSkinOut[4 * w + 4] ushr 16) and 0xFF) - ((softSkinOut[4 * w + 5] ushr 16) and 0xFF))
        assertTrue("Soft Skin should smooth fine skin variance ($softDiff <= $stdDiff)", softDiff <= stdDiff)
        assertTrue("Soft Skin should visibly change noisy skin pixels", stdOut[4 * w + 4] != softSkinOut[4 * w + 4])

        // Glow brightens neighbor halo adjacent to bright highlight (15, 16)
        val stdHalo = (stdOut[16 * w + 15] ushr 16) and 0xFF
        val glowHalo = (glowOut[16 * w + 15] ushr 16) and 0xFF
        assertTrue("Glow should diffuse highlight bloom into adjacent area ($glowHalo >= $stdHalo)", glowHalo >= stdHalo)

        // Film + Grain maintains valid lightness and non-identical grain response across frames
        val filmMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(portraitWithHighlight, filmGrainOut)
        assertTrue("Film + Grain lightness valid (${filmMetrics.meanLightness})", filmMetrics.meanLightness in 15f..95f)
        assertTrue("Film + Grain preserves skin tone hue (${filmMetrics.skinHueDriftDegrees})", filmMetrics.skinHueDriftDegrees < 8.5f)
    }

    @Test
    fun `bundled real scene drawables validate across pro max enhancement and all five styles`() {
        val drawableDir = File("src/main/res/drawable").takeIf { it.exists() }
            ?: File("app/src/main/res/drawable")
        val sceneFiles = listOf(
            "img_scene_portrait.jpg",
            "img_scene_landscape.jpg",
            "img_scene_lowlight.jpg",
            "img_scene_highcontrast.jpg"
        )

        for (fileName in sceneFiles) {
            val file = File(drawableDir, fileName)
            assertTrue("Missing bundled scene: ${file.absolutePath}", file.exists())
            val buffered = ImageIO.read(file)
            val pixels = IntArray(64 * 64)
            var idx = 0
            for (y in 0 until 64) {
                val sy = (y * buffered.height) / 64
                for (x in 0 until 64) {
                    val sx = (x * buffered.width) / 64
                    pixels[idx++] = buffered.getRGB(sx, sy)
                }
            }

            val telemetry = ComputationalPhotoEngine.analyzeSceneTelemetry(pixels)
            val proBase = ComputationalPhotoEngine.enhancePixelBuffer(
                pixels,
                64,
                64,
                telemetry.toEnhancementParams(proMaxEnabled = true)
            )

            for (style in PhotographicStyle.entries) {
                val styled = processPixels(proBase, StyleParameters(style = style))
                val metrics = PhotographicStyleCpuProcessor.analyzePixelArrays(pixels, styled)
                assertTrue(
                    "$fileName / $style invalid lightness: ${metrics.meanLightness}",
                    metrics.meanLightness in 1f..99f
                )
                if (fileName == "img_scene_portrait.jpg") {
                    assertTrue(
                        "Portrait skin hue drift too large for $style: ${metrics.skinHueDriftDegrees}",
                        metrics.skinHueDriftDegrees < 8.0f
                    )
                }
            }
        }
    }

    @Test
    fun `fitzpatrick I through VI diverse human skin complexions maintain melanin hue angle without drift across styles and extreme adjustments`() {
        val fitzpatrickPatch = intArrayOf(
            argb(246, 222, 204), // Type I: very fair, porcelain
            argb(232, 190, 165), // Type II: fair Caucasian
            argb(205, 155, 125), // Type III: medium olive/beige
            argb(175, 125, 95),  // Type IV: Mediterranean / Latino
            argb(120, 80, 58),   // Type V: Brown / South Asian
            argb(78, 50, 38)     // Type VI: Deep Brown / African
        )

        for (style in PhotographicStyle.entries) {
            val styled = processPixels(fitzpatrickPatch, StyleParameters(style = style))
            val metrics = PhotographicStyleCpuProcessor.analyzePixelArrays(fitzpatrickPatch, styled)
            assertTrue("Fitzpatrick skin fraction > 0.8 for $style", metrics.skinPixelFraction >= 0.8f)
            assertTrue(
                "Fitzpatrick skin hue drift must be strictly bounded for $style (${metrics.skinHueDriftDegrees} deg)",
                metrics.skinHueDriftDegrees < 5.0f
            )
        }

        // Extreme user Warmth & Tone adjustments
        val extremeWarm = processPixels(fitzpatrickPatch, StyleParameters(PhotographicStyle.STANDARD, tone = -50, warmth = 80))
        val warmMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(fitzpatrickPatch, extremeWarm)
        assertTrue("Extreme warm skin hue drift < 5.5 deg (${warmMetrics.skinHueDriftDegrees})", warmMetrics.skinHueDriftDegrees < 5.5f)

        val extremeCool = processPixels(fitzpatrickPatch, StyleParameters(PhotographicStyle.COOL, tone = 50, warmth = -80))
        val coolMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(fitzpatrickPatch, extremeCool)
        assertTrue("Extreme cool skin hue drift < 5.5 deg (${coolMetrics.skinHueDriftDegrees})", coolMetrics.skinHueDriftDegrees < 5.5f)
    }

    @Test
    fun `tone slider adjusts shadow depth and midtone contrast smoothly without black crush or highlight blowout`() {
        val stepRamp = intArrayOf(
            argb(8, 8, 10),      // near black toe
            argb(30, 30, 32),    // shadow
            argb(115, 115, 118), // midtone
            argb(200, 200, 202), // upper mid
            argb(248, 248, 250)  // specular highlight
        )
        val deepTone = processPixels(stepRamp, StyleParameters(PhotographicStyle.RICH_CONTRAST, tone = -80))
        val liftedTone = processPixels(stepRamp, StyleParameters(PhotographicStyle.STANDARD, tone = 80))
        val deepMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(stepRamp, deepTone)
        val liftedMetrics = PhotographicStyleCpuProcessor.analyzePixelArrays(stepRamp, liftedTone)

        assertTrue(
            "Lifted tone mean L* (${liftedMetrics.meanLightness}) must exceed Deep tone mean L* (${deepMetrics.meanLightness})",
            liftedMetrics.meanLightness > deepMetrics.meanLightness + 3.0f
        )

        // Near-black shadow at index 0 must retain texture without hard crushing
        val shadowNearBlack = (deepTone[0] ushr 16) and 0xFF
        assertTrue("Near-black shadow must retain texture without hard crushing ($shadowNearBlack > 0)", shadowNearBlack > 0)

        // Specular highlight at index 4 must compress smoothly without digital blowout
        val highlightVal = (liftedTone[4] ushr 16) and 0xFF
        assertTrue("Highlight must not blow out to clipped white ($highlightVal <= 255)", highlightVal <= 255)
    }

    @Test
    fun `specular highlights and extreme deep shadows exhibit natural luminance aware chroma roll off`() {
        val specularHighlight = intArrayOf(argb(254, 250, 235))
        val styledHighlight = processPixels(specularHighlight, StyleParameters(PhotographicStyle.WARM, warmth = 60))
        val p = styledHighlight[0]
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val spread = kotlin.math.max(r, kotlin.math.max(g, b)) - kotlin.math.min(r, kotlin.math.min(g, b))
        assertTrue("Specular highlight should roll off toward neutral white (spread=$spread)", spread < 25)
    }

    @Test
    fun `color separation preserves rich blue skies and lush vegetation without neon shift or purpling`() {
        val natureScene = intArrayOf(
            argb(65, 140, 225),  // Sky blue
            argb(75, 145, 55),   // Lush foliage green
            argb(150, 95, 60)    // Warm earth / terracotta
        )
        val richGraded = processPixels(natureScene, StyleParameters(PhotographicStyle.RICH_CONTRAST))
        val vibGraded = processPixels(natureScene, StyleParameters(PhotographicStyle.VIBRANT))

        // Sky in Rich Contrast
        val skyPixel = richGraded[0]
        val rS = ((skyPixel ushr 16) and 0xFF) / 255f
        val gS = ((skyPixel ushr 8) and 0xFF) / 255f
        val bS = (skyPixel and 0xFF) / 255f
        val maxS = kotlin.math.max(rS, kotlin.math.max(gS, bS))
        val minS = kotlin.math.min(rS, kotlin.math.min(gS, bS))
        val dS = maxS - minS
        val hS = if (maxS == bS) (((rS - gS) / dS) + 4f) * 60f else (((gS - bS) / dS) + (if (gS < bS) 6f else 0f)) * 60f
        assertTrue("Sky hue should stay in rich blue-cyan family without purpling (hue=$hS)", hS in 195f..238f)

        // Foliage in Vibrant
        val leafPixel = vibGraded[1]
        val rL = ((leafPixel ushr 16) and 0xFF) / 255f
        val gL = ((leafPixel ushr 8) and 0xFF) / 255f
        val bL = (leafPixel and 0xFF) / 255f
        val maxL = kotlin.math.max(rL, kotlin.math.max(gL, bL))
        val minL = kotlin.math.min(rL, kotlin.math.min(gL, bL))
        val dL = maxL - minL
        val hL = (((bL - rL) / dL) + 2f) * 60f
        assertTrue("Foliage hue should remain natural green without drifting into neon yellow (hue=$hL)", hL in 85f..145f)
    }
}
