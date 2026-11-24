package com.example.colorscience

import kotlin.math.roundToInt

/**
 * Interactive 2D Palette & Warmth state matching the iOS-inspired square control pad
 * and horizontal color temperature slider.
 *
 * - [padX] (0..1 left->right) drives HUE undertone (0..100) and horizontal saturation component.
 * - [padY] (0..1 top->bottom) drives TONE (100 at top -> 0 at bottom) and vertical intensity.
 * - [warmth] (-100..+100) drives cool-blue to warm-orange white-balance color temperature.
 */
data class PaletteSettings(
    val padX: Float = 0.5f,
    val padY: Float = 0.5f,
    val hue: Int = 50,
    val saturation: Int = 50,
    val tone: Int = 50,
    val warmth: Int = 0,
    val activePresetId: String? = null
) {
    val isModified: Boolean
        get() = hue != 50 || saturation != 50 || tone != 50 || warmth != 0

    fun coerced(): PaletteSettings = copy(
        padX = padX.coerceIn(0f, 1f),
        padY = padY.coerceIn(0f, 1f),
        hue = hue.coerceIn(0, 100),
        saturation = saturation.coerceIn(0, 100),
        tone = tone.coerceIn(0, 100),
        warmth = warmth.coerceIn(-100, 100)
    )

    companion object {
        val DEFAULT = PaletteSettings()

        fun fromPadCoordinates(
            x: Float,
            y: Float,
            warmth: Int = 0,
            activePresetId: String? = null
        ): PaletteSettings {
            val cx = x.coerceIn(0f, 1f)
            val cy = y.coerceIn(0f, 1f)
            val hue = (cx * 100f).roundToInt().coerceIn(0, 100)
            val tone = ((1f - cy) * 100f).roundToInt().coerceIn(0, 100)
            val satNorm = (cx * 0.65f + (1f - cy) * 0.35f).coerceIn(0f, 1f)
            val saturation = (satNorm * 100f).roundToInt().coerceIn(0, 100)
            return PaletteSettings(
                padX = cx,
                padY = cy,
                hue = hue,
                saturation = saturation,
                tone = tone,
                warmth = warmth.coerceIn(-100, 100),
                activePresetId = activePresetId
            )
        }
    }
}

data class SavedPalettePreset(
    val id: String,
    val name: String,
    val settings: PaletteSettings,
    val isCustom: Boolean = true
)

object BuiltIn2DPalettePresets {
    val ALL: List<SavedPalettePreset> = listOf(
        SavedPalettePreset(
            id = "preset_natural",
            name = "Natural",
            settings = PaletteSettings.fromPadCoordinates(0.50f, 0.50f, warmth = 0, activePresetId = "preset_natural"),
            isCustom = false
        ),
        SavedPalettePreset(
            id = "preset_golden_hour",
            name = "Golden Hour",
            settings = PaletteSettings.fromPadCoordinates(0.74f, 0.42f, warmth = 36, activePresetId = "preset_golden_hour"),
            isCustom = false
        ),
        SavedPalettePreset(
            id = "preset_cinematic",
            name = "Cinematic",
            settings = PaletteSettings.fromPadCoordinates(0.42f, 0.68f, warmth = 12, activePresetId = "preset_cinematic"),
            isCustom = false
        ),
        SavedPalettePreset(
            id = "preset_cool_film",
            name = "Cool Film",
            settings = PaletteSettings.fromPadCoordinates(0.28f, 0.44f, warmth = -34, activePresetId = "preset_cool_film"),
            isCustom = false
        ),
        SavedPalettePreset(
            id = "preset_luminous",
            name = "Luminous",
            settings = PaletteSettings.fromPadCoordinates(0.62f, 0.22f, warmth = 14, activePresetId = "preset_luminous"),
            isCustom = false
        ),
        SavedPalettePreset(
            id = "preset_muted_editorial",
            name = "Muted Pro",
            settings = PaletteSettings.fromPadCoordinates(0.30f, 0.64f, warmth = -6, activePresetId = "preset_muted_editorial"),
            isCustom = false
        )
    )
}

enum class StylesPanelTab {
    PALETTE,
    TEXTURE
}

enum class TextureMode(
    val id: String,
    val label: String,
    val defaultIntensity: Int
) {
    STANDARD("standard", "STANDARD", 0),
    SOFT_SKIN("soft_skin", "SOFT SKIN", 55),
    GLOW("glow", "GLOW", 50),
    FILM("film", "FILM", 60);

    companion object {
        fun fromId(id: String): TextureMode =
            entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

data class TextureSettings(
    val mode: TextureMode = TextureMode.STANDARD,
    val intensity: Int = 55,
    val grainEnabled: Boolean = false,
    val grainIntensity: Int = 45,
    val focusingGrain: Boolean = false
) {
    val isModified: Boolean
        get() = mode != TextureMode.STANDARD || grainEnabled

    val activeEffectLabel: String
        get() = when {
            focusingGrain && grainEnabled -> "GRAIN"
            mode != TextureMode.STANDARD -> mode.label
            grainEnabled -> "GRAIN"
            else -> "STANDARD"
        }

    val activeIntensityValue: Int
        get() = when {
            focusingGrain && grainEnabled -> grainIntensity
            mode != TextureMode.STANDARD -> intensity
            grainEnabled -> grainIntensity
            else -> 0
        }

    fun coerced(): TextureSettings = copy(
        intensity = intensity.coerceIn(0, 100),
        grainIntensity = grainIntensity.coerceIn(0, 100)
    )

    companion object {
        val DEFAULT = TextureSettings()
    }
}
