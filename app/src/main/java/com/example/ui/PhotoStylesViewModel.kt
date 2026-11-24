package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.camera.CameraCaptureMode
import com.example.camera.CameraFlashMode
import com.example.camera.CameraHardwareCapabilities
import com.example.camera.CameraTimerMode
import com.example.camera.CapturedMasterPhoto
import com.example.camera.NightModeSetting
import com.example.camera.PhotoSaver
import com.example.colorscience.ComputationalPhotoEngine
import com.example.colorscience.GlOffscreenRenderer
import com.example.colorscience.PaletteSessionStorage
import com.example.colorscience.PaletteSettings
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.PhotographicStyleCpuProcessor
import com.example.colorscience.SavedCameraSessionState
import com.example.colorscience.SavedPalettePreset
import com.example.colorscience.SceneColorMetrics
import com.example.colorscience.SceneEnhancementParams
import com.example.colorscience.SceneTelemetry
import com.example.colorscience.StyleParameters
import com.example.colorscience.StylesPanelTab
import com.example.colorscience.TextureMode
import com.example.colorscience.TextureSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CameraAspectRatio(val label: String, val ratio: Float) {
    RATIO_4_3("4:3", 3f / 4f),
    RATIO_16_9("16:9", 9f / 16f),
    RATIO_1_1("1:1", 1f)
}

enum class LabViewMode {
    COMPARE_ALL_5,
    INSPECTOR
}

data class CameraUiState(
    val selectedStyle: PhotographicStyle = PhotographicStyle.STANDARD,
    val styleMemory: Map<PhotographicStyle, StyleParameters> = PhotographicStyle.entries.associateWith {
        StyleParameters(style = it, tone = it.defaultTone, warmth = it.defaultWarmth)
    },
    val activePalette: PaletteSettings = PaletteSettings.DEFAULT,
    val textureSettings: TextureSettings = TextureSettings.DEFAULT,
    val stylesPanelTab: StylesPanelTab = StylesPanelTab.PALETTE,
    val customPalettePresets: List<SavedPalettePreset> = emptyList(),
    val isPalettePanelOpen: Boolean = false,
    val isComparingOriginalLive: Boolean = false,
    val isStylesTrayExpanded: Boolean = false,
    val isControlsDrawerOpen: Boolean = false,
    val isExposureBarOpen: Boolean = false,
    val cameraMode: CameraCaptureMode = CameraCaptureMode.PHOTO,
    val isFrontCamera: Boolean = false,
    val flashMode: CameraFlashMode = CameraFlashMode.AUTO,
    val timerMode: CameraTimerMode = CameraTimerMode.OFF,
    val timerCountdownSec: Int? = null,
    val nightMode: NightModeSetting = NightModeSetting.AUTO,
    val exposureEv: Float = 0f,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
    val showGrid: Boolean = true,
    val showLevel: Boolean = false,
    val useMaxResolution: Boolean = true,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 4f,
    val proMaxEnabled: Boolean = true,
    val rawDngEnabled: Boolean = false,
    val hardwareCapabilities: CameraHardwareCapabilities = CameraHardwareCapabilities(),
    val liveSceneTelemetry: SceneTelemetry = SceneTelemetry.DEFAULT,
    val liveStyleThumbnails: Map<PhotographicStyle, Bitmap> = emptyMap(),
    val isCapturing: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val recordingDurationSec: Int = 0,
    val shutterFlashTick: Int = 0,
    val capturedOriginalFullRes: Bitmap? = null,
    val capturedPreviewProxy: Bitmap? = null,
    val capturedStyledPreview: Bitmap? = null,
    val capturedRawDngFile: File? = null,
    val capturedFusedFrameCount: Int = 1,
    val isPreviewComparingOriginal: Boolean = false,
    val isSavingPhoto: Boolean = false,
    val lastSavedThumbnail: Bitmap? = null,
    val lastSavedUri: Uri? = null,
    val lastSavedIsVideo: Boolean = false,
    val toastMessage: String? = null,
    val selectedSceneId: CalibrationSceneId = CalibrationSceneId.PORTRAIT_INDOOR,
    val labViewMode: LabViewMode = LabViewMode.INSPECTOR,
    val labOriginalBitmap: Bitmap? = null,
    val labStyledBitmap: Bitmap? = null,
    val labComparisonThumbnails: Map<PhotographicStyle, Bitmap> = emptyMap(),
    val labMetrics: SceneColorMetrics? = null,
    val labIsProcessing: Boolean = false
) {
    val effectiveLiveTelemetry: SceneTelemetry
        get() = when (nightMode) {
            NightModeSetting.OFF -> liveSceneTelemetry.copy(isLowLight = false)
            NightModeSetting.ON -> liveSceneTelemetry.copy(isLowLight = liveSceneTelemetry.meanLuma < 0.48f)
            NightModeSetting.AUTO -> liveSceneTelemetry
        }

    /**
     * Live camera viewfinder parameters: Stages 3 (Smart HDR / Night live parity) -> 4 (Style) -> 5 (2D Palette).
     */
    val activeParameters: StyleParameters
        get() {
            val base = styleMemory[selectedStyle] ?: StyleParameters(selectedStyle)
            return base.copy(
                palette = activePalette,
                texture = textureSettings,
                enhancement = effectiveLiveTelemetry.toEnhancementParams(proMaxEnabled),
                bypassOriginal = isComparingOriginalLive
            )
        }

    /**
     * Post-capture preview & export parameters:
     * Stage 2 & 3 computational fusion/HDR/denoise are already baked into [capturedOriginalFullRes],
     * so only Stage 4 (Photographic Style), Stage 5 (2D Palette), and Texture run non-destructively here.
     */
    val previewParameters: StyleParameters
        get() {
            val base = styleMemory[selectedStyle] ?: StyleParameters(selectedStyle)
            return base.copy(
                palette = activePalette,
                texture = textureSettings,
                enhancement = SceneEnhancementParams.DISABLED,
                bypassOriginal = isPreviewComparingOriginal
            )
        }
}

class PhotoStylesViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStorage = PaletteSessionStorage(application)

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var thumbnailJob: Job? = null
    private var previewRenderJob: Job? = null
    private var labRenderJob: Job? = null
    private var baseThumbSource: Bitmap? = null
    private var customImportedBitmap: Bitmap? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val seed = SceneBitmapLoader.loadScaledScene(getApplication(), R.drawable.img_scene_portrait, 96)
            baseThumbSource = seed
            renderStyleThumbnailsFromSource(seed)
        }
        loadCalibrationScene(CalibrationSceneId.PORTRAIT_INDOOR)
    }

    private fun createInitialUiState(): CameraUiState {
        val restored = sessionStorage.loadSessionState()
        val presets = sessionStorage.loadCustomPresets()
        return CameraUiState(
            selectedStyle = restored.selectedStyle,
            styleMemory = restored.styleMemory,
            activePalette = restored.activePalette,
            textureSettings = restored.textureSettings,
            customPalettePresets = presets,
            proMaxEnabled = restored.proMaxEnabled,
            rawDngEnabled = restored.rawDngEnabled
        )
    }

    private fun persistCurrentSession() {
        val s = _uiState.value
        sessionStorage.saveSessionState(
            SavedCameraSessionState(
                selectedStyle = s.selectedStyle,
                styleMemory = s.styleMemory,
                activePalette = s.activePalette,
                textureSettings = s.textureSettings,
                proMaxEnabled = s.proMaxEnabled,
                rawDngEnabled = s.rawDngEnabled
            )
        )
    }

    fun selectStylesTab(tab: StylesPanelTab) {
        _uiState.update { it.copy(stylesPanelTab = tab) }
    }

    fun selectTextureMode(mode: TextureMode) {
        _uiState.update { state ->
            val current = state.textureSettings
            val updated = when (mode) {
                TextureMode.STANDARD -> TextureSettings.DEFAULT
                else -> current.copy(
                    mode = mode,
                    intensity = if (current.mode == mode) current.intensity else mode.defaultIntensity,
                    focusingGrain = false
                ).coerced()
            }
            state.copy(textureSettings = updated)
        }
        persistCurrentSession()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun toggleGrainTexture() {
        _uiState.update { state ->
            val cur = state.textureSettings
            val updated = when {
                !cur.grainEnabled -> cur.copy(
                    grainEnabled = true,
                    grainIntensity = cur.grainIntensity.takeIf { it > 0 } ?: 45,
                    focusingGrain = true
                )
                !cur.focusingGrain && cur.mode != TextureMode.STANDARD -> cur.copy(focusingGrain = true)
                else -> cur.copy(grainEnabled = false, focusingGrain = false)
            }.coerced()
            state.copy(textureSettings = updated)
        }
        persistCurrentSession()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun updateTextureIntensity(value: Int) {
        val clamped = value.coerceIn(0, 100)
        _uiState.update { state ->
            val cur = state.textureSettings
            val updated = when {
                cur.focusingGrain && cur.grainEnabled -> cur.copy(grainIntensity = clamped)
                cur.mode != TextureMode.STANDARD -> cur.copy(intensity = clamped)
                cur.grainEnabled -> cur.copy(grainIntensity = clamped)
                else -> cur
            }.coerced()
            state.copy(textureSettings = updated)
        }
        persistCurrentSession()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun selectStyle(style: PhotographicStyle) {
        _uiState.update { it.copy(selectedStyle = style) }
        persistCurrentSession()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun updateTone(tone: Int) {
        val clamped = tone.coerceIn(-100, 100)
        _uiState.update { state ->
            val current = state.styleMemory[state.selectedStyle] ?: StyleParameters(state.selectedStyle)
            val updated = current.copy(tone = clamped)
            state.copy(styleMemory = state.styleMemory + (state.selectedStyle to updated))
        }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun updateWarmth(warmth: Int) {
        val clamped = warmth.coerceIn(-100, 100)
        _uiState.update { state ->
            val current = state.styleMemory[state.selectedStyle] ?: StyleParameters(state.selectedStyle)
            val updated = current.copy(warmth = clamped)
            state.copy(styleMemory = state.styleMemory + (state.selectedStyle to updated))
        }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun resetCurrentStyle() {
        _uiState.update { state ->
            val current = state.styleMemory[state.selectedStyle] ?: StyleParameters(state.selectedStyle)
            val reset = current.resetToStyleDefaults()
            state.copy(
                styleMemory = state.styleMemory + (state.selectedStyle to reset),
                activePalette = PaletteSettings.DEFAULT,
                textureSettings = TextureSettings.DEFAULT
            )
        }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun setPalettePanelOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isPalettePanelOpen = open,
                isControlsDrawerOpen = false,
                isExposureBarOpen = false,
                isStylesTrayExpanded = if (open) true else it.isStylesTrayExpanded
            )
        }
    }

    fun updatePalettePad(padX: Float, padY: Float) {
        _uiState.update { state ->
            val updated = PaletteSettings.fromPadCoordinates(
                x = padX,
                y = padY,
                warmth = state.activePalette.warmth,
                activePresetId = null
            )
            state.copy(activePalette = updated)
        }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun updatePaletteWarmth(warmth: Int) {
        val clamped = warmth.coerceIn(-100, 100)
        _uiState.update { state ->
            state.copy(
                activePalette = state.activePalette.copy(
                    warmth = clamped,
                    activePresetId = null
                ).coerced()
            )
        }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun resetPalette() {
        _uiState.update { it.copy(activePalette = PaletteSettings.DEFAULT) }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun selectPalettePreset(preset: SavedPalettePreset) {
        _uiState.update {
            it.copy(activePalette = preset.settings.copy(activePresetId = preset.id).coerced())
        }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshCapturedPreviewIfPresent()
        refreshLabRenderedOutput()
    }

    fun saveCustomPreset(name: String) {
        val palette = _uiState.value.activePalette
        viewModelScope.launch(Dispatchers.IO) {
            val updated = sessionStorage.saveCustomPreset(name, palette)
            val savedId = updated.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id
            _uiState.update {
                it.copy(
                    customPalettePresets = updated,
                    activePalette = it.activePalette.copy(activePresetId = savedId),
                    toastMessage = getApplication<Application>().getString(R.string.label_palette_saved)
                )
            }
            persistCurrentSession()
        }
    }

    fun deleteCustomPreset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = sessionStorage.deleteCustomPreset(id)
            _uiState.update { state ->
                val cleared = if (state.activePalette.activePresetId == id) {
                    state.activePalette.copy(activePresetId = null)
                } else {
                    state.activePalette
                }
                state.copy(customPalettePresets = updated, activePalette = cleared)
            }
            persistCurrentSession()
        }
    }

    fun toggleProMaxPipeline() {
        _uiState.update { it.copy(proMaxEnabled = !it.proMaxEnabled) }
        persistCurrentSession()
        refreshThumbnailsFromCachedSource()
        refreshLabRenderedOutput()
    }

    fun toggleRawDngCapture() {
        _uiState.update { state ->
            if (!state.hardwareCapabilities.supportsRawDng) return@update state
            state.copy(rawDngEnabled = !state.rawDngEnabled)
        }
        persistCurrentSession()
    }

    fun onHardwareCapabilitiesUpdated(caps: CameraHardwareCapabilities) {
        _uiState.update { state ->
            val expCaps = caps.exposureCapabilities
            state.copy(
                hardwareCapabilities = caps,
                rawDngEnabled = state.rawDngEnabled && caps.supportsRawDng,
                exposureEv = if (expCaps.supported) {
                    state.exposureEv.coerceIn(expCaps.minEv, expCaps.maxEv)
                } else {
                    0f
                }
            )
        }
    }

    fun setLiveCompareOriginal(comparing: Boolean) {
        _uiState.update { it.copy(isComparingOriginalLive = comparing) }
    }

    fun toggleStylesTray() {
        _uiState.update {
            val next = !it.isStylesTrayExpanded
            it.copy(
                isStylesTrayExpanded = next,
                isControlsDrawerOpen = false,
                isExposureBarOpen = false,
                isPalettePanelOpen = if (!next) false else it.isPalettePanelOpen
            )
        }
    }

    fun openStylesFromDrawer() {
        _uiState.update {
            it.copy(
                isControlsDrawerOpen = false,
                isExposureBarOpen = false,
                isPalettePanelOpen = false,
                isStylesTrayExpanded = true
            )
        }
    }

    fun openPaletteFromDrawer() {
        _uiState.update {
            it.copy(
                isControlsDrawerOpen = false,
                isExposureBarOpen = false,
                isStylesTrayExpanded = true,
                isPalettePanelOpen = true
            )
        }
    }

    fun setControlsDrawerOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isControlsDrawerOpen = open,
                isExposureBarOpen = if (open) false else it.isExposureBarOpen
            )
        }
    }

    fun setCameraMode(mode: CameraCaptureMode) {
        if (_uiState.value.isRecordingVideo) return
        _uiState.update {
            it.copy(
                cameraMode = mode,
                timerCountdownSec = null,
                isControlsDrawerOpen = false
            )
        }
    }

    fun toggleCameraFacing() {
        if (_uiState.value.isRecordingVideo) return
        // Switching cameras MUST preserve active Style, Tone, Warmth, Palette, and Pro Max settings
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, zoomRatio = 1f, exposureEv = 0f) }
    }

    fun cycleFlashMode() {
        _uiState.update { state ->
            val next = when (state.flashMode) {
                CameraFlashMode.AUTO -> CameraFlashMode.ON
                CameraFlashMode.ON -> CameraFlashMode.OFF
                CameraFlashMode.OFF -> CameraFlashMode.AUTO
            }
            state.copy(flashMode = next)
        }
    }

    fun cycleTimerMode() {
        _uiState.update { state ->
            val next = when (state.timerMode) {
                CameraTimerMode.OFF -> CameraTimerMode.THREE_SEC
                CameraTimerMode.THREE_SEC -> CameraTimerMode.TEN_SEC
                CameraTimerMode.TEN_SEC -> CameraTimerMode.OFF
            }
            state.copy(timerMode = next, timerCountdownSec = null)
        }
    }

    fun setTimerCountdownSec(seconds: Int?) {
        _uiState.update { it.copy(timerCountdownSec = seconds) }
    }

    fun cycleNightMode() {
        _uiState.update { state ->
            val next = when (state.nightMode) {
                NightModeSetting.AUTO -> NightModeSetting.ON
                NightModeSetting.ON -> NightModeSetting.OFF
                NightModeSetting.OFF -> NightModeSetting.AUTO
            }
            state.copy(nightMode = next)
        }
        refreshThumbnailsFromCachedSource()
    }

    fun toggleExposureBar() {
        _uiState.update {
            it.copy(
                isExposureBarOpen = !it.isExposureBarOpen,
                isControlsDrawerOpen = false
            )
        }
    }

    fun setExposureEv(ev: Float) {
        val expCaps = _uiState.value.hardwareCapabilities.exposureCapabilities
        val minEv = if (expCaps.supported) expCaps.minEv else -2f
        val maxEv = if (expCaps.supported) expCaps.maxEv else 2f
        _uiState.update { it.copy(exposureEv = ev.coerceIn(minEv, maxEv)) }
    }

    fun cycleAspectRatio() {
        _uiState.update { state ->
            val next = when (state.aspectRatio) {
                CameraAspectRatio.RATIO_4_3 -> CameraAspectRatio.RATIO_16_9
                CameraAspectRatio.RATIO_16_9 -> CameraAspectRatio.RATIO_1_1
                CameraAspectRatio.RATIO_1_1 -> CameraAspectRatio.RATIO_4_3
            }
            state.copy(aspectRatio = next)
        }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleLevel() {
        _uiState.update { it.copy(showLevel = !it.showLevel) }
    }

    fun toggleMaxResolution() {
        _uiState.update { it.copy(useMaxResolution = !it.useMaxResolution) }
    }

    fun updateZoomBounds(minZoom: Float, maxZoom: Float) {
        _uiState.update { state ->
            state.copy(
                minZoom = minZoom,
                maxZoom = maxZoom,
                zoomRatio = state.zoomRatio.coerceIn(minZoom, maxZoom)
            )
        }
    }

    fun setZoomRatio(ratio: Float) {
        _uiState.update { state ->
            state.copy(zoomRatio = ratio.coerceIn(state.minZoom, state.maxZoom))
        }
    }

    fun onVideoRecordingStarted() {
        _uiState.update { it.copy(isRecordingVideo = true, recordingDurationSec = 0) }
    }

    fun onVideoDurationTick(seconds: Int) {
        _uiState.update { it.copy(recordingDurationSec = seconds) }
    }

    fun onVideoRecordingSaved(uri: Uri) {
        _uiState.update {
            it.copy(
                isRecordingVideo = false,
                recordingDurationSec = 0,
                lastSavedUri = uri,
                lastSavedIsVideo = true,
                toastMessage = getApplication<Application>().getString(R.string.label_video_saved)
            )
        }
    }

    fun onVideoRecordingFailed(message: String) {
        _uiState.update {
            it.copy(
                isRecordingVideo = false,
                recordingDurationSec = 0,
                toastMessage = message
            )
        }
    }

    fun onLiveThumbnailAndTelemetry(sample: Bitmap, telemetry: SceneTelemetry) {
        _uiState.update { it.copy(liveSceneTelemetry = telemetry) }
        if (thumbnailJob?.isActive == true) {
            sample.recycle()
            return
        }
        thumbnailJob = viewModelScope.launch(Dispatchers.Default) {
            baseThumbSource = sample
            renderStyleThumbnailsFromSource(sample)
        }
    }

    private fun refreshThumbnailsFromCachedSource() {
        val src = baseThumbSource ?: return
        if (thumbnailJob?.isActive == true) return
        thumbnailJob = viewModelScope.launch(Dispatchers.Default) {
            renderStyleThumbnailsFromSource(src)
        }
    }

    private suspend fun renderStyleThumbnailsFromSource(source: Bitmap) {
        val state = _uiState.value
        val memory = state.styleMemory
        val palette = state.activePalette
        val enh = state.liveSceneTelemetry.toEnhancementParams(state.proMaxEnabled)
        val map = withContext(Dispatchers.Default) {
            PhotographicStyle.entries.associateWith { style ->
                val params = (memory[style] ?: StyleParameters(style)).copy(
                    palette = palette,
                    enhancement = enh
                )
                PhotographicStyleCpuProcessor.applyStyleToBitmap(source, params)
            }
        }
        _uiState.update { it.copy(liveStyleThumbnails = map) }
    }

    fun onCaptureStarted() {
        _uiState.update {
            it.copy(isCapturing = true, shutterFlashTick = it.shutterFlashTick + 1)
        }
    }

    fun onMasterPhotoCaptured(master: CapturedMasterPhoto, onReadyForPreview: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            val masterBmp = master.enhancedMasterBitmap
            val proxy = createScaledCopy(masterBmp, 1280)
            // Master bitmap already completed Stages 1-3; now apply Stages 4 & 5 for preview
            val params = _uiState.value.previewParameters.copy(bypassOriginal = false)
            val styledPreview = GlOffscreenRenderer.renderFullResolution(proxy, params)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        capturedOriginalFullRes = masterBmp,
                        capturedPreviewProxy = proxy,
                        capturedStyledPreview = styledPreview,
                        capturedRawDngFile = master.rawDngTempFile,
                        capturedFusedFrameCount = master.fusedFrameCount,
                        isPreviewComparingOriginal = false
                    )
                }
                onReadyForPreview()
            }
        }
    }

    fun onCaptureFailed(errorMessage: String) {
        _uiState.update {
            it.copy(isCapturing = false, toastMessage = errorMessage)
        }
    }

    fun setPreviewComparingOriginal(comparing: Boolean) {
        _uiState.update { it.copy(isPreviewComparingOriginal = comparing) }
        refreshCapturedPreviewIfPresent()
    }

    private fun refreshCapturedPreviewIfPresent() {
        val proxy = _uiState.value.capturedPreviewProxy ?: return
        val params = _uiState.value.previewParameters
        previewRenderJob?.cancel()
        previewRenderJob = viewModelScope.launch(Dispatchers.Default) {
            val rendered = if (params.bypassOriginal) {
                proxy
            } else {
                PhotographicStyleCpuProcessor.applyStyleToBitmap(proxy, params)
            }
            _uiState.update { it.copy(capturedStyledPreview = rendered) }
        }
    }

    fun discardCapturedPhoto() {
        _uiState.value.capturedRawDngFile?.delete()
        _uiState.update {
            it.copy(
                capturedOriginalFullRes = null,
                capturedPreviewProxy = null,
                capturedStyledPreview = null,
                capturedRawDngFile = null,
                capturedFusedFrameCount = 1,
                isPreviewComparingOriginal = false
            )
        }
    }

    fun saveCapturedPhoto(onSaved: () -> Unit) {
        val state = _uiState.value
        val original = state.capturedOriginalFullRes ?: return
        val params = state.previewParameters.copy(bypassOriginal = false)
        val rawFile = state.capturedRawDngFile
        _uiState.update { it.copy(isSavingPhoto = true) }

        viewModelScope.launch {
            val result = PhotoSaver.saveStyledPhoto(getApplication(), original, params, rawFile)
            result.onSuccess { uri ->
                val thumbSource = _uiState.value.capturedStyledPreview ?: original
                val thumb = withContext(Dispatchers.Default) { createScaledCopy(thumbSource, 160) }
                _uiState.update {
                    it.copy(
                        isSavingPhoto = false,
                        lastSavedThumbnail = thumb,
                        lastSavedUri = uri,
                        lastSavedIsVideo = false,
                        capturedOriginalFullRes = null,
                        capturedPreviewProxy = null,
                        capturedStyledPreview = null,
                        capturedRawDngFile = null,
                        capturedFusedFrameCount = 1,
                        toastMessage = getApplication<Application>().getString(R.string.label_saved_toast)
                    )
                }
                onSaved()
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSavingPhoto = false,
                        toastMessage = getApplication<Application>().getString(R.string.label_save_failed)
                    )
                }
            }
        }
    }

    fun loadCalibrationScene(sceneId: CalibrationSceneId) {
        _uiState.update { it.copy(selectedSceneId = sceneId, labIsProcessing = true) }
        labRenderJob?.cancel()
        labRenderJob = viewModelScope.launch(Dispatchers.Default) {
            val orig = when (sceneId) {
                CalibrationSceneId.CUSTOM_IMPORTED -> customImportedBitmap
                else -> sceneId.drawableRes?.let { res ->
                    SceneBitmapLoader.loadScaledScene(getApplication(), res, 860)
                }
            }
            if (orig == null) {
                _uiState.update { it.copy(labIsProcessing = false) }
                return@launch
            }
            val sceneTelemetry = ComputationalPhotoEngine.analyzeBitmapTelemetry(orig)
            val state = _uiState.value
            val enh = sceneTelemetry.toEnhancementParams(state.proMaxEnabled)
            val baseForStyle = if (enh.enabled) ComputationalPhotoEngine.enhanceBitmap(orig, enh) else orig
            val thumbSource = createScaledCopy(baseForStyle, 320)

            val memory = state.styleMemory
            val palette = state.activePalette
            val texture = state.textureSettings
            val compareMap = PhotographicStyle.entries.associateWith { style ->
                val params = (memory[style] ?: StyleParameters(style)).copy(palette = palette, texture = texture)
                PhotographicStyleCpuProcessor.applyStyleToBitmap(thumbSource, params)
            }
            val activeParams = (memory[state.selectedStyle] ?: StyleParameters(state.selectedStyle))
                .copy(palette = palette, texture = texture, bypassOriginal = false)
            val styled = PhotographicStyleCpuProcessor.applyStyleToBitmap(baseForStyle, activeParams)
            val metrics = PhotographicStyleCpuProcessor.analyzeColorMetrics(orig, styled)

            _uiState.update {
                it.copy(
                    labOriginalBitmap = orig,
                    labStyledBitmap = styled,
                    labComparisonThumbnails = compareMap,
                    labMetrics = metrics,
                    labIsProcessing = false
                )
            }
        }
    }

    fun importCustomPhotoForLab(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            val loaded = SceneBitmapLoader.loadScaledUri(getApplication(), uri, 960)
            if (loaded != null) {
                customImportedBitmap = loaded
                loadCalibrationScene(CalibrationSceneId.CUSTOM_IMPORTED)
            } else {
                _uiState.update { it.copy(toastMessage = "Unable to load selected photo") }
            }
        }
    }

    fun setLabViewMode(mode: LabViewMode) {
        _uiState.update { it.copy(labViewMode = mode) }
        refreshLabRenderedOutput()
    }

    private fun refreshLabRenderedOutput() {
        val orig = _uiState.value.labOriginalBitmap ?: return
        val state = _uiState.value
        labRenderJob?.cancel()
        labRenderJob = viewModelScope.launch(Dispatchers.Default) {
            val telemetry = ComputationalPhotoEngine.analyzeBitmapTelemetry(orig)
            val enh = telemetry.toEnhancementParams(state.proMaxEnabled)
            val baseForStyle = if (enh.enabled) ComputationalPhotoEngine.enhanceBitmap(orig, enh) else orig
            val memory = state.styleMemory
            val palette = state.activePalette
            val texture = state.textureSettings
            val params = (memory[state.selectedStyle] ?: StyleParameters(state.selectedStyle))
                .copy(palette = palette, texture = texture, bypassOriginal = false)

            val styled = PhotographicStyleCpuProcessor.applyStyleToBitmap(baseForStyle, params)
            val metrics = PhotographicStyleCpuProcessor.analyzeColorMetrics(orig, styled)
            val thumbSource = createScaledCopy(baseForStyle, 320)
            val compareMap = PhotographicStyle.entries.associateWith { style ->
                val p = (memory[style] ?: StyleParameters(style)).copy(palette = palette, texture = texture)
                PhotographicStyleCpuProcessor.applyStyleToBitmap(thumbSource, p)
            }
            _uiState.update {
                it.copy(
                    labStyledBitmap = styled,
                    labComparisonThumbnails = compareMap,
                    labMetrics = metrics
                )
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun createScaledCopy(source: Bitmap, maxEdge: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxEdge) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxEdge.toFloat() / largest.toFloat()
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
