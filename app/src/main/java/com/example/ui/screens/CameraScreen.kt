package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.camera.CameraCaptureMode
import com.example.camera.CameraController
import com.example.camera.CameraFlashMode
import com.example.camera.CameraHardwareCapabilities
import com.example.camera.CameraPreviewSurface
import com.example.camera.CameraTimerMode
import com.example.camera.CapturedMasterPhoto
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.SavedPalettePreset
import com.example.colorscience.SceneTelemetry
import com.example.colorscience.StylesPanelTab
import com.example.colorscience.TextureMode
import com.example.ui.CameraUiState
import com.example.ui.components.AppleShutterButton
import com.example.ui.components.CameraControlsDrawer
import com.example.ui.components.CircularStylesControlPanel
import com.example.ui.components.HorizonLevelOverlay
import com.example.ui.components.InteractiveColorPalettePad
import com.example.ui.components.Ios27BottomShutterDeck
import com.example.ui.components.Ios27ExposureBar
import com.example.ui.components.Ios27ShutterButton
import com.example.ui.components.Ios27TopCameraBar
import com.example.ui.components.Ios27ZoomPillBar
import com.example.ui.components.LiquidGlassCircleButton
import com.example.ui.components.PalettePresetsDialog
import com.example.ui.components.StylesTopHeaderBar
import com.example.ui.components.TopPaletteAdjustmentBar
import com.example.ui.components.WarmthGradientSlider
import androidx.compose.material.icons.rounded.Apps
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraBlack
import com.example.ui.theme.CameraGlass
import com.example.ui.theme.CameraHairline
import com.example.ui.theme.CameraSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onSelectStylesTab: (StylesPanelTab) -> Unit,
    onSelectStyle: (PhotographicStyle) -> Unit,
    onToneChanged: (Int) -> Unit,
    onWarmthChanged: (Int) -> Unit,
    onSelectTextureMode: (TextureMode) -> Unit,
    onToggleGrain: () -> Unit,
    onTextureIntensityChanged: (Int) -> Unit,
    onResetStyle: () -> Unit,
    onLiveCompareHoldChanged: (Boolean) -> Unit,
    onToggleStylesTray: () -> Unit,
    onOpenStylesFromDrawer: () -> Unit,
    onOpenPaletteFromDrawer: () -> Unit,
    onSetControlsDrawerOpen: (Boolean) -> Unit,
    onSelectCameraMode: (CameraCaptureMode) -> Unit,
    onSwitchCamera: () -> Unit,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onTimerCountdownTick: (Int?) -> Unit,
    onCycleNightMode: () -> Unit,
    onToggleExposureBar: () -> Unit,
    onExposureChanged: (Float) -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleLevel: () -> Unit,
    onToggleMaxResolution: () -> Unit,
    onZoomBoundsAvailable: (Float, Float) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onThumbnailAndTelemetry: (android.graphics.Bitmap, SceneTelemetry) -> Unit,
    onCapabilitiesAvailable: (CameraHardwareCapabilities) -> Unit,
    onToggleProMax: () -> Unit,
    onToggleRawDng: () -> Unit,
    onCaptureStarted: () -> Unit,
    onMasterPhotoCaptured: (CapturedMasterPhoto) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onVideoRecordingStarted: () -> Unit,
    onVideoDurationTick: (Int) -> Unit,
    onVideoRecordingSaved: (Uri) -> Unit,
    onVideoRecordingFailed: (String) -> Unit,
    onOpenCalibrationLab: () -> Unit,
    onSetPalettePanelOpen: (Boolean) -> Unit,
    onUpdatePalettePad: (Float, Float) -> Unit,
    onUpdatePaletteWarmth: (Int) -> Unit,
    onSelectPalettePreset: (SavedPalettePreset) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onResetPalette: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var requestedCameraOnce by remember { mutableStateOf(false) }
    var requestedAudioOnce by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val cameraController = remember(context) { CameraController(context) }
    val previewSurface = remember(context) { CameraPreviewSurface(context) }

    val startActualVideoRecording: (Boolean) -> Unit = { audioAllowed ->
        cameraController.startVideoRecording(
            withAudio = audioAllowed,
            onRecordingStarted = onVideoRecordingStarted,
            onVideoSaved = onVideoRecordingSaved,
            onError = onVideoRecordingFailed
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        startActualVideoRecording(granted)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !requestedCameraOnce) {
            requestedCameraOnce = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
            previewSurface.releaseResources()
        }
    }

    LaunchedEffect(uiState.activeParameters) {
        previewSurface.updateStyleParameters(uiState.activeParameters)
    }

    LaunchedEffect(uiState.flashMode) {
        cameraController.setFlashMode(uiState.flashMode)
    }

    LaunchedEffect(uiState.zoomRatio) {
        cameraController.setZoomRatio(uiState.zoomRatio)
    }

    LaunchedEffect(uiState.exposureEv) {
        cameraController.setExposureCompensationEv(uiState.exposureEv)
    }

    LaunchedEffect(
        hasCameraPermission,
        uiState.isFrontCamera,
        uiState.cameraMode,
        uiState.rawDngEnabled,
        uiState.useMaxResolution
    ) {
        if (hasCameraPermission) {
            cameraController.bindCamera(
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = previewSurface,
                useFrontCamera = uiState.isFrontCamera,
                captureMode = uiState.cameraMode,
                flashMode = uiState.flashMode,
                enableRawDngIfSupported = uiState.rawDngEnabled,
                useMaxResolution = uiState.useMaxResolution,
                onThumbnailAndTelemetry = onThumbnailAndTelemetry,
                onZoomStateAvailable = onZoomBoundsAvailable,
                onCapabilitiesAvailable = onCapabilitiesAvailable,
                onError = { err -> onCaptureFailed(err.localizedMessage ?: "Camera unavailable") }
            )
        }
    }

    // Track recording timer ticks cleanly
    LaunchedEffect(uiState.isRecordingVideo) {
        if (uiState.isRecordingVideo) {
            var elapsed = 0
            while (true) {
                delay(1000L)
                elapsed++
                onVideoDurationTick(elapsed)
            }
        }
    }

    // Sub-panel back navigation support
    BackHandler(enabled = uiState.isPalettePanelOpen && !uiState.isControlsDrawerOpen) {
        onSetPalettePanelOpen(false)
    }
    BackHandler(enabled = uiState.isStylesTrayExpanded && !uiState.isPalettePanelOpen && !uiState.isControlsDrawerOpen) {
        onToggleStylesTray()
    }
    BackHandler(enabled = uiState.isExposureBarOpen && !uiState.isControlsDrawerOpen) {
        onToggleExposureBar()
    }

    var showPresetsDialog by remember { mutableStateOf(false) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.shutterFlashTick) {
        if (uiState.shutterFlashTick > 0) {
            flashAlpha.snapTo(0.8f)
            flashAlpha.animateTo(0f, tween(durationMillis = 190))
        }
    }

    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1300L)
            focusTapOffset = null
        }
    }

    val executePhotoCapture: () -> Unit = {
        onCaptureStarted()
        scope.launch {
            runCatching {
                cameraController.captureProMaxPhoto(
                    isFrontCamera = uiState.isFrontCamera,
                    proMaxEnabled = uiState.proMaxEnabled,
                    nightMode = uiState.nightMode,
                    userExposureEv = uiState.exposureEv,
                    telemetry = uiState.liveSceneTelemetry
                )
            }.onSuccess { master ->
                onMasterPhotoCaptured(master)
            }.onFailure { err ->
                onCaptureFailed(err.localizedMessage ?: "Capture failed")
            }
        }
    }

    val triggerShutter: () -> Unit = {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (uiState.cameraMode == CameraCaptureMode.VIDEO) {
            if (uiState.isRecordingVideo) {
                cameraController.stopVideoRecording()
            } else if (hasAudioPermission || requestedAudioOnce) {
                startActualVideoRecording(hasAudioPermission)
            } else {
                requestedAudioOnce = true
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            val existingJob = countdownJob
            if (existingJob != null && existingJob.isActive) {
                existingJob.cancel()
                countdownJob = null
                onTimerCountdownTick(null)
            } else if (uiState.timerMode == CameraTimerMode.OFF) {
                executePhotoCapture()
            } else {
                countdownJob = scope.launch {
                    for (sec in uiState.timerMode.seconds downTo 1) {
                        onTimerCountdownTick(sec)
                        delay(1000L)
                    }
                    onTimerCountdownTick(null)
                    executePhotoCapture()
                }
            }
        }
    }

    val openLastSavedOrLab: () -> Unit = {
        val uri = uiState.lastSavedUri
        if (uri != null) {
            val mime = if (uiState.lastSavedIsVideo) "video/mp4" else "image/jpeg"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(viewIntent) }
                .onFailure { onOpenCalibrationLab() }
        } else {
            onOpenCalibrationLab()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isPalettePanelOpen) {
                // Preserved Reference-Image-1 Top Palette Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onCycleFlash,
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("flash_button")
                    ) {
                        Icon(
                            imageVector = when (uiState.flashMode) {
                                CameraFlashMode.AUTO -> Icons.Rounded.FlashAuto
                                CameraFlashMode.ON -> Icons.Rounded.FlashOn
                                CameraFlashMode.OFF -> Icons.Rounded.FlashOff
                            },
                            contentDescription = stringResource(R.string.cd_toggle_flash),
                            tint = if (uiState.flashMode == CameraFlashMode.ON) AppleYellow else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    TopPaletteAdjustmentBar(
                        palette = uiState.activePalette,
                        onResetPalette = onResetPalette,
                        onOpenPresets = { showPresetsDialog = true }
                    )

                    IconButton(
                        onClick = onOpenCalibrationLab,
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("open_lab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = stringResource(R.string.cd_open_lab),
                            tint = AppleYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (uiState.isStylesTrayExpanded) {
                // Image-1 minimalist pure-black header: Back | STYLES | Reset
                StylesTopHeaderBar(
                    isModified = uiState.activeParameters.isModified || uiState.selectedStyle != PhotographicStyle.STANDARD,
                    onBack = onToggleStylesTray,
                    onReset = onResetStyle
                )
            } else {
                // iOS 27 Top Camera Bar
                Ios27TopCameraBar(
                    flashMode = uiState.flashMode,
                    nightMode = uiState.nightMode,
                    isNightActive = uiState.effectiveLiveTelemetry.isLowLight,
                    timerMode = uiState.timerMode,
                    exposureEv = uiState.exposureEv,
                    supportsExposure = uiState.hardwareCapabilities.exposureCapabilities.supported,
                    isExposureOpen = uiState.isExposureBarOpen,
                    sensorMegapixels = uiState.hardwareCapabilities.maxSensorMegapixels,
                    useMaxResolution = uiState.useMaxResolution,
                    rawDngEnabled = uiState.rawDngEnabled,
                    supportsRawDng = uiState.hardwareCapabilities.supportsRawDng,
                    isStylesHighlighted = uiState.isStylesTrayExpanded || uiState.activeParameters.isModified || uiState.selectedStyle != PhotographicStyle.STANDARD,
                    isRecordingVideo = uiState.isRecordingVideo,
                    recordingDurationSec = uiState.recordingDurationSec,
                    onCycleFormatOrResolution = {
                        if (uiState.hardwareCapabilities.supportsRawDng) {
                            onToggleRawDng()
                        } else {
                            onToggleMaxResolution()
                        }
                    },
                    onCycleFlash = onCycleFlash,
                    onCycleNightMode = onCycleNightMode,
                    onToggleExposure = onToggleExposureBar,
                    onCycleTimer = onCycleTimer,
                    onOpenStyles = onToggleStylesTray
                )

                Ios27ExposureBar(
                    visible = uiState.isExposureBarOpen && !uiState.isRecordingVideo,
                    exposureEv = uiState.exposureEv,
                    exposureCapabilities = uiState.hardwareCapabilities.exposureCapabilities,
                    onExposureChanged = onExposureChanged
                )
            }

            // Full-Screen Edge-to-Edge Rounded Viewfinder
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    val targetRatio by animateFloatAsState(
                        targetValue = if (uiState.cameraMode == CameraCaptureMode.VIDEO) {
                            9f / 16f
                        } else {
                            uiState.aspectRatio.ratio
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "aspectRatio"
                    )

                    val viewfinderModifier = if (uiState.isPalettePanelOpen) {
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(22.dp))
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(22.dp))
                    }

                    Box(
                        modifier = viewfinderModifier
                            .onSizeChanged { viewfinderSize = it }
                            .pointerInput(viewfinderSize) {
                                detectTapGestures(
                                    onTap = { tap ->
                                        focusTapOffset = tap
                                        if (viewfinderSize.width > 0 && viewfinderSize.height > 0) {
                                            cameraController.focusAtPoint(
                                                normalizedX = tap.x / viewfinderSize.width,
                                                normalizedY = tap.y / viewfinderSize.height,
                                                viewWidth = viewfinderSize.width.toFloat(),
                                                viewHeight = viewfinderSize.height.toFloat()
                                            )
                                        }
                                    }
                                )
                            }
                            .pointerInput(uiState.minZoom, uiState.maxZoom) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1f) {
                                        onZoomChanged(uiState.zoomRatio * zoom)
                                    }
                                }
                            }
                    ) {
                        AndroidView(
                            factory = { previewSurface },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Rule-of-thirds grid overlay
                        if (uiState.showGrid && !uiState.isPalettePanelOpen) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lineColor = Color.White.copy(alpha = 0.22f)
                                val w = size.width
                                val h = size.height
                                drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.4f)
                                drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.4f)
                                drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.4f)
                                drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.4f)
                            }
                        }

                        // Real SensorManager Horizon Level Overlay
                        HorizonLevelOverlay(enabled = uiState.showLevel && !uiState.isPalettePanelOpen)

                        if (uiState.isPalettePanelOpen) {
                            // Preserved Reference-Image-1 Top Pill Row (PRO/HDR/NIGHT + UNDERTONES + RAW)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    onClick = onToggleProMax,
                                    color = if (uiState.proMaxEnabled) Color(0xA6141518) else Color(0x66141518),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (uiState.proMaxEnabled) AppleYellow.copy(alpha = 0.65f) else CameraHairline
                                    ),
                                    modifier = Modifier.testTag("pro_max_mode_badge")
                                ) {
                                    Text(
                                        text = if (uiState.proMaxEnabled) {
                                            uiState.effectiveLiveTelemetry.activeMode.badgeLabel
                                        } else {
                                            "RAW ISP"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (uiState.proMaxEnabled) AppleYellow else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    color = if (uiState.isComparingOriginalLive) AppleYellow else Color(0x99141518),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (uiState.isComparingOriginalLive) AppleYellow else CameraHairline
                                    ),
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    onLiveCompareHoldChanged(true)
                                                    tryAwaitRelease()
                                                    onLiveCompareHoldChanged(false)
                                                }
                                            )
                                        }
                                        .testTag("live_hold_compare")
                                ) {
                                    Text(
                                        text = if (uiState.isComparingOriginalLive) {
                                            stringResource(R.string.label_original).uppercase()
                                        } else {
                                            stringResource(R.string.label_undertones)
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            letterSpacing = 0.8.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (uiState.isComparingOriginalLive) CameraBlack else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }

                                if (uiState.hardwareCapabilities.supportsRawDng) {
                                    Surface(
                                        onClick = onToggleRawDng,
                                        color = if (uiState.rawDngEnabled) AppleYellow else Color(0x99141518),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (uiState.rawDngEnabled) AppleYellow else CameraHairline
                                        ),
                                        modifier = Modifier.testTag("raw_dng_toggle")
                                    ) {
                                        Text(
                                            text = "RAW",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (uiState.rawDngEnabled) CameraBlack else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(36.dp))
                                }
                            }

                            // Preserved Reference-Image-1 Style Pill + 5 Dots + Shutter
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val entries = PhotographicStyle.entries
                                val activeIndex = entries.indexOf(uiState.selectedStyle).coerceAtLeast(0)

                                Surface(
                                    onClick = {
                                        val next = entries[(activeIndex + 1) % entries.size]
                                        onSelectStyle(next)
                                    },
                                    color = Color(0xA6141518),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CameraHairline),
                                    modifier = Modifier.testTag("palette_style_badge")
                                ) {
                                    Text(
                                        text = uiState.selectedStyle.shortName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            letterSpacing = 0.8.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    entries.forEach { style ->
                                        val selected = style == uiState.selectedStyle
                                        Box(
                                            modifier = Modifier
                                                .size(if (selected) 7.dp else 5.5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) Color.White else Color.White.copy(alpha = 0.40f)
                                                )
                                                .clickable { onSelectStyle(style) }
                                                .testTag("style_dot_${style.id}")
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                AppleShutterButton(
                                    isCapturing = uiState.isCapturing,
                                    onClick = triggerShutter
                                )
                            }

                            if (uiState.maxZoom >= 1.3f) {
                                Surface(
                                    onClick = {
                                        val next = if (uiState.zoomRatio < 1.5f) 2f else 1f
                                        onZoomChanged(next)
                                    },
                                    color = Color(0x8C141518),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, CameraHairline),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 14.dp, bottom = 28.dp)
                                        .size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = String.format("%.1fx", uiState.zoomRatio).replace(".0x", "x"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (uiState.zoomRatio > 1.2f) AppleYellow else TextPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            // Subtle Top Overlay Pills inside iOS 27 Viewfinder (only when Styles Tray open OR modified/comparing)
                            val showTopHud = uiState.isStylesTrayExpanded ||
                                uiState.selectedStyle != PhotographicStyle.STANDARD ||
                                uiState.activeParameters.isModified ||
                                uiState.isComparingOriginalLive

                            if (showTopHud) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val activeParams = uiState.activeParameters
                                    Surface(
                                        onClick = onToggleStylesTray,
                                        color = Color(0x88141519),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f))
                                    ) {
                                        Text(
                                            text = if (uiState.isComparingOriginalLive) {
                                                stringResource(R.string.label_original).uppercase()
                                            } else {
                                                "${activeParams.style.shortName.uppercase()} · T ${signed(activeParams.tone)} · W ${signed(activeParams.warmth)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (uiState.isComparingOriginalLive) TextPrimary else AppleYellow,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        color = if (uiState.isComparingOriginalLive) AppleYellow else Color(0x88141519),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (uiState.isComparingOriginalLive) AppleYellow else Color.White.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        onLiveCompareHoldChanged(true)
                                                        tryAwaitRelease()
                                                        onLiveCompareHoldChanged(false)
                                                    }
                                                )
                                            }
                                            .testTag("live_hold_compare")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Compare,
                                                contentDescription = stringResource(R.string.label_hold_compare),
                                                tint = if (uiState.isComparingOriginalLive) CameraBlack else TextPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.label_original),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (uiState.isComparingOriginalLive) CameraBlack else TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // Floating Liquid Glass Zoom Pill Bar at bottom center of viewfinder (matching Reference Image 1)
                            Ios27ZoomPillBar(
                                zoomRatio = uiState.zoomRatio,
                                minZoom = uiState.minZoom,
                                maxZoom = uiState.maxZoom,
                                onZoomSelected = onZoomChanged,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            )
                        }

                        // Self-timer large countdown readout
                        uiState.timerCountdownSec?.let { sec ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99000000))
                                    .border(1.5.dp, AppleYellow.copy(alpha = 0.7f), CircleShape)
                                    .testTag("timer_countdown_badge")
                            ) {
                                Text(
                                    text = sec.toString(),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }

                        // Tap-to-focus reticle
                        focusTapOffset?.let { pos ->
                            val density = LocalDensity.current
                            val boxPx = with(density) { 68.dp.toPx() }
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (pos.x - boxPx / 2f).roundToInt(),
                                            (pos.y - boxPx / 2f).roundToInt()
                                        )
                                    }
                                    .size(68.dp)
                                    .border(1.5.dp, AppleYellow, RoundedCornerShape(4.dp))
                            )
                        }

                        // Shutter flash
                        if (flashAlpha.value > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(flashAlpha.value)
                                    .background(Color.White)
                            )
                        }
                    }
                } else {
                    CameraPermissionPlaceholder(
                        onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onOpenLab = onOpenCalibrationLab
                    )
                }
            }

            if (uiState.isPalettePanelOpen) {
                // Preserved Reference-Image-1 Bottom Palette Deck
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = { onSetPalettePanelOpen(false) },
                        shape = CircleShape,
                        color = Color(0xCC222429),
                        border = BorderStroke(1.dp, CameraHairline),
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("close_palette_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_close_palette),
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        InteractiveColorPalettePad(
                            padX = uiState.activePalette.padX,
                            padY = uiState.activePalette.padY,
                            style = uiState.selectedStyle,
                            onPadChanged = onUpdatePalettePad,
                            padSize = 126.dp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        WarmthGradientSlider(
                            warmth = uiState.activePalette.warmth,
                            onWarmthChanged = onUpdatePaletteWarmth,
                            sliderWidth = 148.dp
                        )
                    }

                    Surface(
                        onClick = onSwitchCamera,
                        shape = CircleShape,
                        color = Color(0xCC222429),
                        border = BorderStroke(1.dp, CameraHairline),
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("switch_camera_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = stringResource(R.string.cd_switch_camera),
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } else if (uiState.isStylesTrayExpanded) {
                // Pure black Image-1 Circular Styles & Texture panel + compact live capture row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CameraBlack)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularStylesControlPanel(
                        selectedTab = uiState.stylesPanelTab,
                        parameters = uiState.activeParameters.copy(bypassOriginal = false),
                        onSelectTab = onSelectStylesTab,
                        onSelectStyle = onSelectStyle,
                        onToneChanged = onToneChanged,
                        onWarmthChanged = onWarmthChanged,
                        onOpenPalette = { onSetPalettePanelOpen(true) },
                        onSelectTextureMode = onSelectTextureMode,
                        onToggleGrain = onToggleGrain,
                        onTextureIntensityChanged = onTextureIntensityChanged
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LiquidGlassCircleButton(
                            onClick = { onSetControlsDrawerOpen(true) },
                            contentDescription = stringResource(R.string.cd_open_controls),
                            isActive = uiState.isControlsDrawerOpen,
                            size = 44.dp,
                            modifier = Modifier.testTag("controls_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Ios27ShutterButton(
                            cameraMode = uiState.cameraMode,
                            isCapturing = uiState.isCapturing,
                            isRecordingVideo = uiState.isRecordingVideo,
                            onClick = triggerShutter,
                            modifier = Modifier.size(66.dp)
                        )

                        LiquidGlassCircleButton(
                            onClick = onSwitchCamera,
                            contentDescription = stringResource(R.string.cd_switch_camera),
                            enabled = !uiState.isRecordingVideo,
                            size = 44.dp,
                            modifier = Modifier.testTag("switch_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // iOS 27 Liquid Glass Bottom Shutter + Mode Selector Deck
                Ios27BottomShutterDeck(
                    cameraMode = uiState.cameraMode,
                    isCapturing = uiState.isCapturing,
                    isRecordingVideo = uiState.isRecordingVideo,
                    isStylesActive = uiState.isStylesTrayExpanded,
                    isStylesModified = uiState.selectedStyle != PhotographicStyle.STANDARD || uiState.activeParameters.isModified,
                    isDrawerOpen = uiState.isControlsDrawerOpen,
                    lastSavedThumbnail = uiState.lastSavedThumbnail,
                    lastSavedIsVideo = uiState.lastSavedIsVideo,
                    onOpenStyles = onToggleStylesTray,
                    onShutterClick = triggerShutter,
                    onOpenControlsDrawer = { onSetControlsDrawerOpen(true) },
                    onSelectMode = onSelectCameraMode,
                    onGalleryClick = openLastSavedOrLab,
                    onSwitchCamera = onSwitchCamera
                )
            }
        }

        // iOS 27 Liquid Glass Camera Controls Drawer (Bottom Sheet Overlay)
        CameraControlsDrawer(
            visible = uiState.isControlsDrawerOpen && !uiState.isPalettePanelOpen,
            uiState = uiState,
            onDismiss = { onSetControlsDrawerOpen(false) },
            onCycleFlash = onCycleFlash,
            onCycleTimer = onCycleTimer,
            onExposureChanged = onExposureChanged,
            onOpenStyles = onOpenStylesFromDrawer,
            onOpenPalette = onOpenPaletteFromDrawer,
            onCycleAspectRatio = onCycleAspectRatio,
            onCycleNightMode = onCycleNightMode,
            onToggleGrid = onToggleGrid,
            onToggleLevel = onToggleLevel,
            onToggleMaxResolution = onToggleMaxResolution,
            onToggleFormatOrProMax = {
                if (uiState.hardwareCapabilities.supportsRawDng) {
                    onToggleRawDng()
                } else {
                    onToggleProMax()
                }
            },
            onOpenLab = onOpenCalibrationLab
        )

        if (showPresetsDialog) {
            PalettePresetsDialog(
                activePalette = uiState.activePalette,
                customPresets = uiState.customPalettePresets,
                onSelectPreset = { preset ->
                    onSelectPalettePreset(preset)
                    showPresetsDialog = false
                },
                onSaveCustomPreset = onSaveCustomPreset,
                onDeleteCustomPreset = onDeleteCustomPreset,
                onDismiss = { showPresetsDialog = false }
            )
        }
    }
}

@Composable
private fun CameraPermissionPlaceholder(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLab: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        color = CameraSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CameraHairline)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppleYellow.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = AppleYellow,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.permission_camera_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_camera_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleYellow,
                    contentColor = CameraBlack
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("enable_camera_button")
            ) {
                Text(
                    text = stringResource(R.string.permission_camera_grant),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    border = BorderStroke(1.dp, CameraHairline),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.permission_camera_settings),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                OutlinedButton(
                    onClick = onOpenLab,
                    border = BorderStroke(1.dp, AppleYellow.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("permission_open_lab_button")
                ) {
                    Text(
                        text = stringResource(R.string.permission_camera_open_lab),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleYellow
                    )
                }
            }
        }
    }
}

private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"
