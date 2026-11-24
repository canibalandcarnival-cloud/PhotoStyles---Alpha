package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.PhotoStylesViewModel
import com.example.ui.screens.CalibrationLabScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.PhotoPreviewScreen
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraGlass
import com.example.ui.theme.PhotoStylesTheme
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay

const val ROUTE_CAMERA = "camera"
const val ROUTE_PHOTO_PREVIEW = "photo_preview"
const val ROUTE_CALIBRATION_LAB = "calibration_lab"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        setContent {
            PhotoStylesTheme {
                PhotoStylesApp()
            }
        }
    }
}

@Composable
fun PhotoStylesApp(
    viewModel: PhotoStylesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(2200L)
            viewModel.clearToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_CAMERA
        ) {
            composable(ROUTE_CAMERA) {
                CameraScreen(
                    uiState = uiState,
                    onSelectStylesTab = viewModel::selectStylesTab,
                    onSelectStyle = viewModel::selectStyle,
                    onToneChanged = viewModel::updateTone,
                    onWarmthChanged = viewModel::updateWarmth,
                    onSelectTextureMode = viewModel::selectTextureMode,
                    onToggleGrain = viewModel::toggleGrainTexture,
                    onTextureIntensityChanged = viewModel::updateTextureIntensity,
                    onResetStyle = viewModel::resetCurrentStyle,
                    onLiveCompareHoldChanged = viewModel::setLiveCompareOriginal,
                    onToggleStylesTray = viewModel::toggleStylesTray,
                    onOpenStylesFromDrawer = viewModel::openStylesFromDrawer,
                    onOpenPaletteFromDrawer = viewModel::openPaletteFromDrawer,
                    onSetControlsDrawerOpen = viewModel::setControlsDrawerOpen,
                    onSelectCameraMode = viewModel::setCameraMode,
                    onSwitchCamera = viewModel::toggleCameraFacing,
                    onCycleFlash = viewModel::cycleFlashMode,
                    onCycleTimer = viewModel::cycleTimerMode,
                    onTimerCountdownTick = viewModel::setTimerCountdownSec,
                    onCycleNightMode = viewModel::cycleNightMode,
                    onToggleExposureBar = viewModel::toggleExposureBar,
                    onExposureChanged = viewModel::setExposureEv,
                    onCycleAspectRatio = viewModel::cycleAspectRatio,
                    onToggleGrid = viewModel::toggleGrid,
                    onToggleLevel = viewModel::toggleLevel,
                    onToggleMaxResolution = viewModel::toggleMaxResolution,
                    onZoomBoundsAvailable = viewModel::updateZoomBounds,
                    onZoomChanged = viewModel::setZoomRatio,
                    onThumbnailAndTelemetry = viewModel::onLiveThumbnailAndTelemetry,
                    onCapabilitiesAvailable = viewModel::onHardwareCapabilitiesUpdated,
                    onToggleProMax = viewModel::toggleProMaxPipeline,
                    onToggleRawDng = viewModel::toggleRawDngCapture,
                    onCaptureStarted = viewModel::onCaptureStarted,
                    onMasterPhotoCaptured = { master ->
                        viewModel.onMasterPhotoCaptured(master) {
                            navController.navigate(ROUTE_PHOTO_PREVIEW) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onCaptureFailed = viewModel::onCaptureFailed,
                    onVideoRecordingStarted = viewModel::onVideoRecordingStarted,
                    onVideoDurationTick = viewModel::onVideoDurationTick,
                    onVideoRecordingSaved = viewModel::onVideoRecordingSaved,
                    onVideoRecordingFailed = viewModel::onVideoRecordingFailed,
                    onOpenCalibrationLab = {
                        navController.navigate(ROUTE_CALIBRATION_LAB) {
                            launchSingleTop = true
                        }
                    },
                    onSetPalettePanelOpen = viewModel::setPalettePanelOpen,
                    onUpdatePalettePad = viewModel::updatePalettePad,
                    onUpdatePaletteWarmth = viewModel::updatePaletteWarmth,
                    onSelectPalettePreset = viewModel::selectPalettePreset,
                    onSaveCustomPreset = viewModel::saveCustomPreset,
                    onDeleteCustomPreset = viewModel::deleteCustomPreset,
                    onResetPalette = viewModel::resetPalette
                )
            }

            composable(ROUTE_PHOTO_PREVIEW) {
                PhotoPreviewScreen(
                    uiState = uiState,
                    onSelectStylesTab = viewModel::selectStylesTab,
                    onSelectStyle = viewModel::selectStyle,
                    onToneChanged = viewModel::updateTone,
                    onWarmthChanged = viewModel::updateWarmth,
                    onSelectTextureMode = viewModel::selectTextureMode,
                    onToggleGrain = viewModel::toggleGrainTexture,
                    onTextureIntensityChanged = viewModel::updateTextureIntensity,
                    onResetStyle = viewModel::resetCurrentStyle,
                    onCompareHoldChanged = viewModel::setPreviewComparingOriginal,
                    onSetPalettePanelOpen = viewModel::setPalettePanelOpen,
                    onUpdatePalettePad = viewModel::updatePalettePad,
                    onUpdatePaletteWarmth = viewModel::updatePaletteWarmth,
                    onResetPalette = viewModel::resetPalette,
                    onRetake = {
                        viewModel.discardCapturedPhoto()
                        navController.popBackStack()
                    },
                    onSave = {
                        viewModel.saveCapturedPhoto {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(ROUTE_CALIBRATION_LAB) {
                CalibrationLabScreen(
                    uiState = uiState,
                    onSelectScene = viewModel::loadCalibrationScene,
                    onImportPhoto = viewModel::importCustomPhotoForLab,
                    onSetLabViewMode = viewModel::setLabViewMode,
                    onSelectStylesTab = viewModel::selectStylesTab,
                    onSelectStyle = viewModel::selectStyle,
                    onToneChanged = viewModel::updateTone,
                    onWarmthChanged = viewModel::updateWarmth,
                    onSelectTextureMode = viewModel::selectTextureMode,
                    onToggleGrain = viewModel::toggleGrainTexture,
                    onTextureIntensityChanged = viewModel::updateTextureIntensity,
                    onResetStyle = viewModel::resetCurrentStyle,
                    onSetPalettePanelOpen = viewModel::setPalettePanelOpen,
                    onUpdatePalettePad = viewModel::updatePalettePad,
                    onUpdatePaletteWarmth = viewModel::updatePaletteWarmth,
                    onResetPalette = viewModel::resetPalette,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Minimalist floating status toast
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            Surface(
                color = CameraGlass,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AppleYellow.copy(alpha = 0.6f))
            ) {
                Text(
                    text = uiState.toastMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
}
