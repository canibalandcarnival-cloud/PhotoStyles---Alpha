package com.example.colorscience

import android.content.Context
import java.io.File
import java.util.UUID

data class SavedCameraSessionState(
    val selectedStyle: PhotographicStyle = PhotographicStyle.STANDARD,
    val styleMemory: Map<PhotographicStyle, StyleParameters> = PhotographicStyle.entries.associateWith {
        StyleParameters(style = it, tone = it.defaultTone, warmth = it.defaultWarmth)
    },
    val activePalette: PaletteSettings = PaletteSettings.DEFAULT,
    val textureSettings: TextureSettings = TextureSettings.DEFAULT,
    val proMaxEnabled: Boolean = true,
    val rawDngEnabled: Boolean = false
)

/**
 * Dependency-free persistent storage for custom 2D Palette presets and user camera/style session state.
 * Works identically on Android devices and pure JVM unit tests.
 */
class PaletteSessionStorage(
    private val storageDir: File
) {
    constructor(context: Context) : this(File(context.filesDir, "photostyles_prefs"))

    private val presetsFile: File
        get() = File(storageDir, "custom_palettes_v2.tsv")

    private val sessionFile: File
        get() = File(storageDir, "camera_session_v2.tsv")

    fun loadCustomPresets(): List<SavedPalettePreset> {
        if (!presetsFile.exists()) return emptyList()
        return runCatching {
            presetsFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parsePresetLine(line) }
        }.getOrDefault(emptyList())
    }

    fun saveCustomPreset(name: String, settings: PaletteSettings): List<SavedPalettePreset> {
        val cleanName = name.trim().replace("\t", " ").replace("\n", " ").ifEmpty { "Custom Palette" }
        val existing = loadCustomPresets().toMutableList()
        val matchIdx = existing.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val id = if (matchIdx >= 0) existing[matchIdx].id else "custom_${UUID.randomUUID().toString().take(8)}"
        val preset = SavedPalettePreset(
            id = id,
            name = cleanName,
            settings = settings.coerced().copy(activePresetId = id),
            isCustom = true
        )
        if (matchIdx >= 0) {
            existing[matchIdx] = preset
        } else {
            existing.add(0, preset)
        }
        writePresets(existing)
        return existing
    }

    fun deleteCustomPreset(id: String): List<SavedPalettePreset> {
        val updated = loadCustomPresets().filterNot { it.id == id }
        writePresets(updated)
        return updated
    }

    fun saveSessionState(state: SavedCameraSessionState) {
        runCatching {
            if (!storageDir.exists()) storageDir.mkdirs()
            val lines = buildList {
                add("selectedStyle\t${state.selectedStyle.id}")
                add("proMaxEnabled\t${state.proMaxEnabled}")
                add("rawDngEnabled\t${state.rawDngEnabled}")
                val p = state.activePalette.coerced()
                add("palette\t${p.padX}\t${p.padY}\t${p.hue}\t${p.saturation}\t${p.tone}\t${p.warmth}\t${p.activePresetId.orEmpty()}")
                val t = state.textureSettings.coerced()
                add("texture\t${t.mode.id}\t${t.intensity}\t${t.grainEnabled}\t${t.grainIntensity}\t${t.focusingGrain}")
                PhotographicStyle.entries.forEach { style ->
                    val sp = state.styleMemory[style] ?: StyleParameters(style)
                    add("style\t${style.id}\t${sp.tone}\t${sp.warmth}")
                }
            }
            sessionFile.writeText(lines.joinToString("\n"))
        }
    }

    fun loadSessionState(): SavedCameraSessionState {
        if (!sessionFile.exists()) return SavedCameraSessionState()
        return runCatching {
            var selectedStyle = PhotographicStyle.STANDARD
            var proMaxEnabled = true
            var rawDngEnabled = false
            var activePalette = PaletteSettings.DEFAULT
            var textureSettings = TextureSettings.DEFAULT
            val styleMemory = PhotographicStyle.entries.associateWith {
                StyleParameters(style = it, tone = it.defaultTone, warmth = it.defaultWarmth)
            }.toMutableMap()

            sessionFile.readLines().forEach { line ->
                val parts = line.split("\t")
                when (parts.firstOrNull()) {
                    "selectedStyle" -> selectedStyle = PhotographicStyle.fromId(parts.getOrElse(1) { "standard" })
                    "proMaxEnabled" -> proMaxEnabled = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true
                    "rawDngEnabled" -> rawDngEnabled = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
                    "palette" -> if (parts.size >= 7) {
                        activePalette = PaletteSettings(
                            padX = parts[1].toFloatOrNull() ?: 0.5f,
                            padY = parts[2].toFloatOrNull() ?: 0.5f,
                            hue = parts[3].toIntOrNull() ?: 50,
                            saturation = parts[4].toIntOrNull() ?: 50,
                            tone = parts[5].toIntOrNull() ?: 50,
                            warmth = parts[6].toIntOrNull() ?: 0,
                            activePresetId = parts.getOrNull(7)?.takeIf { it.isNotBlank() }
                        ).coerced()
                    }
                    "texture" -> if (parts.size >= 5) {
                        textureSettings = TextureSettings(
                            mode = TextureMode.fromId(parts[1]),
                            intensity = parts[2].toIntOrNull() ?: 55,
                            grainEnabled = parts[3].toBooleanStrictOrNull() ?: false,
                            grainIntensity = parts[4].toIntOrNull() ?: 45,
                            focusingGrain = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: false
                        ).coerced()
                    }
                    "style" -> if (parts.size >= 4) {
                        val s = PhotographicStyle.fromId(parts[1])
                        val t = parts[2].toIntOrNull() ?: s.defaultTone
                        val w = parts[3].toIntOrNull() ?: s.defaultWarmth
                        styleMemory[s] = StyleParameters(style = s, tone = t, warmth = w).coerced()
                    }
                }
            }
            SavedCameraSessionState(
                selectedStyle = selectedStyle,
                styleMemory = styleMemory,
                activePalette = activePalette,
                textureSettings = textureSettings,
                proMaxEnabled = proMaxEnabled,
                rawDngEnabled = rawDngEnabled
            )
        }.getOrDefault(SavedCameraSessionState())
    }

    private fun writePresets(presets: List<SavedPalettePreset>) {
        runCatching {
            if (!storageDir.exists()) storageDir.mkdirs()
            val text = presets.joinToString("\n") { p ->
                val s = p.settings.coerced()
                "${p.id}\t${p.name}\t${s.padX}\t${s.padY}\t${s.hue}\t${s.saturation}\t${s.tone}\t${s.warmth}"
            }
            presetsFile.writeText(text)
        }
    }

    private fun parsePresetLine(line: String): SavedPalettePreset? {
        val parts = line.split("\t")
        if (parts.size < 8) return null
        val id = parts[0].takeIf { it.isNotBlank() } ?: return null
        val name = parts[1].takeIf { it.isNotBlank() } ?: "Custom"
        val settings = PaletteSettings(
            padX = parts[2].toFloatOrNull() ?: 0.5f,
            padY = parts[3].toFloatOrNull() ?: 0.5f,
            hue = parts[4].toIntOrNull() ?: 50,
            saturation = parts[5].toIntOrNull() ?: 50,
            tone = parts[6].toIntOrNull() ?: 50,
            warmth = parts[7].toIntOrNull() ?: 0,
            activePresetId = id
        ).coerced()
        return SavedPalettePreset(id = id, name = name, settings = settings, isCustom = true)
    }
}
