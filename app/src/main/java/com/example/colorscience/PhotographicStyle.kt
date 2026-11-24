package com.example.colorscience

import androidx.annotation.StringRes
import com.example.R
import kotlin.math.abs

/**
 * Calibrated base color profile for each of the five Photographic Styles.
 */
data class StyleColorProfile(
    val shadowCurve: Float,
    val midtoneContrast: Float,
    val highlightShoulder: Float,
    val vibrance: Float,
    val chromaScale: Float,
    val warmthBias: Float,
    val tintBias: Float,
    val skinProtection: Float,
    val coolSkinGuard: Float,
    val skyFoliageBoost: Float
)

enum class PhotographicStyle(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val shortName: String,
    val defaultTone: Int,
    val defaultWarmth: Int,
    val defaultPadX: Float,
    val defaultPadY: Float,
    val profile: StyleColorProfile
) {
    STANDARD(
        id = "standard",
        titleRes = R.string.style_standard,
        descriptionRes = R.string.style_desc_standard,
        shortName = "Standard",
        defaultTone = 0,
        defaultWarmth = 0,
        defaultPadX = 0.50f,
        defaultPadY = 0.50f,
        profile = StyleColorProfile(
            shadowCurve = 0.0f,
            midtoneContrast = 1.02f,
            highlightShoulder = 0.16f,
            vibrance = 0.04f,
            chromaScale = 1.00f,
            warmthBias = 0.0f,
            tintBias = 0.0f,
            skinProtection = 0.88f,
            coolSkinGuard = 0.0f,
            skyFoliageBoost = 0.04f
        )
    ),
    RICH_CONTRAST(
        id = "rich_contrast",
        titleRes = R.string.style_rich_contrast,
        descriptionRes = R.string.style_desc_rich_contrast,
        shortName = "Rich Contrast",
        defaultTone = -50,
        defaultWarmth = 0,
        defaultPadX = 0.54f,
        defaultPadY = 0.72f,
        profile = StyleColorProfile(
            shadowCurve = -0.16f,
            midtoneContrast = 1.18f,
            highlightShoulder = 0.26f,
            vibrance = 0.12f,
            chromaScale = 1.08f,
            warmthBias = 0.02f,
            tintBias = 0.0f,
            skinProtection = 0.84f,
            coolSkinGuard = 0.0f,
            skyFoliageBoost = 0.14f
        )
    ),
    VIBRANT(
        id = "vibrant",
        titleRes = R.string.style_vibrant,
        descriptionRes = R.string.style_desc_vibrant,
        shortName = "Vibrant",
        defaultTone = 50,
        defaultWarmth = 0,
        defaultPadX = 0.64f,
        defaultPadY = 0.30f,
        profile = StyleColorProfile(
            shadowCurve = 0.09f,
            midtoneContrast = 1.05f,
            highlightShoulder = 0.22f,
            vibrance = 0.32f,
            chromaScale = 1.14f,
            warmthBias = 0.03f,
            tintBias = 0.01f,
            skinProtection = 0.92f,
            coolSkinGuard = 0.0f,
            skyFoliageBoost = 0.20f
        )
    ),
    WARM(
        id = "warm",
        titleRes = R.string.style_warm,
        descriptionRes = R.string.style_desc_warm,
        shortName = "Gold",
        defaultTone = 0,
        defaultWarmth = 50,
        defaultPadX = 0.72f,
        defaultPadY = 0.46f,
        profile = StyleColorProfile(
            shadowCurve = 0.02f,
            midtoneContrast = 1.03f,
            highlightShoulder = 0.18f,
            vibrance = 0.08f,
            chromaScale = 1.04f,
            warmthBias = 0.20f,
            tintBias = 0.03f,
            skinProtection = 0.82f,
            coolSkinGuard = 0.0f,
            skyFoliageBoost = 0.05f
        )
    ),
    COOL(
        id = "cool",
        titleRes = R.string.style_cool,
        descriptionRes = R.string.style_desc_cool,
        shortName = "Cool",
        defaultTone = 0,
        defaultWarmth = -50,
        defaultPadX = 0.28f,
        defaultPadY = 0.48f,
        profile = StyleColorProfile(
            shadowCurve = 0.01f,
            midtoneContrast = 1.04f,
            highlightShoulder = 0.20f,
            vibrance = 0.09f,
            chromaScale = 1.03f,
            warmthBias = -0.20f,
            tintBias = -0.02f,
            skinProtection = 0.94f,
            coolSkinGuard = 0.78f,
            skyFoliageBoost = 0.10f
        )
    );

    companion object {
        fun fromId(id: String): PhotographicStyle =
            entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * Non-destructive style & 2D palette parameters.
 */
data class StyleParameters(
    val style: PhotographicStyle = PhotographicStyle.STANDARD,
    val tone: Int = style.defaultTone,
    val warmth: Int = style.defaultWarmth,
    val palette: PaletteSettings = PaletteSettings.DEFAULT,
    val texture: TextureSettings = TextureSettings.DEFAULT,
    val enhancement: SceneEnhancementParams = SceneEnhancementParams.DISABLED,
    val bypassOriginal: Boolean = false
) {
    val isModified: Boolean
        get() = tone != style.defaultTone ||
            warmth != style.defaultWarmth ||
            palette.isModified ||
            texture.isModified

    fun coerced(): StyleParameters = copy(
        tone = tone.coerceIn(-100, 100),
        warmth = warmth.coerceIn(-100, 100),
        palette = palette.coerced(),
        texture = texture.coerced()
    )

    fun resetToStyleDefaults(): StyleParameters = copy(
        tone = style.defaultTone,
        warmth = style.defaultWarmth,
        palette = PaletteSettings.DEFAULT,
        texture = TextureSettings.DEFAULT
    )

    /**
     * Maps Stages 3, 4, 5, and Texture (Smart HDR -> Photographic Style -> 2D Palette -> Texture)
     * into continuous GPU/CPU color-science coefficients without ever overriding the user's
     * Style or Palette controls.
     */
    fun toEngineUniforms(): EngineUniforms {
        if (bypassOriginal) {
            return EngineUniforms.IDENTITY
        }
        val p = style.profile
        val pal = palette.coerced()
        val tex = texture.coerced()

        val toneDelta = (tone - style.defaultTone) / 100f
        val warmthDelta = (warmth - style.defaultWarmth) / 100f

        // 2D Palette normalized deltas around neutral (50, 50, 50)
        val palHueNorm = (pal.hue - 50) / 50f       // -1..+1
        val palSatNorm = (pal.saturation - 50) / 50f // -1..+1
        val palToneNorm = (pal.tone - 50) / 50f     // -1..+1
        val palWarmthNorm = pal.warmth / 100f       // -1..+1

        val shadowLift = (p.shadowCurve + toneDelta * 0.26f + palToneNorm * 0.22f).coerceIn(-0.55f, 0.55f)
        val midtoneLift = (
            toneDelta * 0.10f +
                palToneNorm * 0.16f +
                if (style == PhotographicStyle.VIBRANT) 0.035f else 0f
            ).coerceIn(-0.35f, 0.35f)
        val contrast = (p.midtoneContrast - toneDelta * 0.16f - palToneNorm * 0.14f).coerceIn(0.70f, 1.55f)
        val highlightProtection = (p.highlightShoulder + abs(toneDelta) * 0.08f + abs(palToneNorm) * 0.06f)
            .coerceIn(0.10f, 0.40f)

        val warmthShift = (p.warmthBias + warmthDelta * 0.36f + palWarmthNorm * 0.42f).coerceIn(-0.75f, 0.75f)
        val tintShift = (p.tintBias + warmthDelta * 0.05f + palHueNorm * 0.08f).coerceIn(-0.25f, 0.25f)

        // Asymmetric saturation factor: 0 -> 0.0x (monochrome), 50 -> 1.0x, 100 -> 1.65x
        val satFactor = if (palSatNorm < 0f) (1f + palSatNorm) else (1f + palSatNorm * 0.65f)
        val chromaScale = (p.chromaScale * satFactor).coerceIn(0.0f, 1.85f)
        val vibrance = (p.vibrance + palSatNorm * 0.20f).coerceIn(-0.45f, 0.75f)

        // Smooth hue undertone rotation (-26 deg cool/cyan to +26 deg warm/rose)
        val paletteHueShiftDeg = palHueNorm * 26f

        val texIntensity = (tex.intensity / 100f).coerceIn(0f, 1f)
        val softSkinAmount = if (tex.mode == TextureMode.SOFT_SKIN) texIntensity else 0f
        val glowAmount = if (tex.mode == TextureMode.GLOW) texIntensity else 0f
        val filmAmount = if (tex.mode == TextureMode.FILM) texIntensity else 0f
        val grainAmount = if (tex.grainEnabled) (tex.grainIntensity / 100f).coerceIn(0f, 1f) else 0f

        return EngineUniforms(
            bypass = false,
            shadowLift = shadowLift,
            midtoneLift = midtoneLift,
            contrast = contrast,
            highlightShoulder = highlightProtection,
            vibrance = vibrance,
            chromaScale = chromaScale,
            warmthShift = warmthShift,
            tintShift = tintShift,
            skinProtection = p.skinProtection,
            coolSkinGuard = p.coolSkinGuard,
            skyFoliageBoost = p.skyFoliageBoost,
            paletteHueShiftDeg = paletteHueShiftDeg,
            proMaxEnabled = enhancement.enabled,
            hdrHighlightRecovery = if (enhancement.enabled) enhancement.highlightRecovery else 0f,
            hdrShadowLift = if (enhancement.enabled) enhancement.shadowDetailBoost else 0f,
            lowLightFactor = if (enhancement.enabled) enhancement.lowLightFactor else 0f,
            naturalClarity = if (enhancement.enabled) enhancement.naturalClarity else 0f,
            softSkinAmount = softSkinAmount,
            glowAmount = glowAmount,
            filmAmount = filmAmount,
            grainAmount = grainAmount
        )
    }
}

data class EngineUniforms(
    val bypass: Boolean,
    val shadowLift: Float,
    val midtoneLift: Float,
    val contrast: Float,
    val highlightShoulder: Float,
    val vibrance: Float,
    val chromaScale: Float,
    val warmthShift: Float,
    val tintShift: Float,
    val skinProtection: Float,
    val coolSkinGuard: Float,
    val skyFoliageBoost: Float,
    val paletteHueShiftDeg: Float = 0f,
    val proMaxEnabled: Boolean = false,
    val hdrHighlightRecovery: Float = 0f,
    val hdrShadowLift: Float = 0f,
    val lowLightFactor: Float = 0f,
    val naturalClarity: Float = 0f,
    val softSkinAmount: Float = 0f,
    val glowAmount: Float = 0f,
    val filmAmount: Float = 0f,
    val grainAmount: Float = 0f
) {
    companion object {
        val IDENTITY = EngineUniforms(
            bypass = true,
            shadowLift = 0f,
            midtoneLift = 0f,
            contrast = 1f,
            highlightShoulder = 0f,
            vibrance = 0f,
            chromaScale = 1f,
            warmthShift = 0f,
            tintShift = 0f,
            skinProtection = 0f,
            coolSkinGuard = 0f,
            skyFoliageBoost = 0f,
            paletteHueShiftDeg = 0f,
            proMaxEnabled = false,
            hdrHighlightRecovery = 0f,
            hdrShadowLift = 0f,
            lowLightFactor = 0f,
            naturalClarity = 0f,
            softSkinAmount = 0f,
            glowAmount = 0f,
            filmAmount = 0f,
            grainAmount = 0f
        )
    }
}
