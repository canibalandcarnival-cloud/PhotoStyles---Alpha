package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.SceneColorMetrics
import com.example.colorscience.StylesPanelTab
import com.example.colorscience.TextureMode
import com.example.ui.CalibrationSceneId
import com.example.ui.CameraUiState
import com.example.ui.LabViewMode
import com.example.ui.components.CircularStylesControlPanel
import com.example.ui.components.CompactPaletteDeck
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraBlack
import com.example.ui.theme.CameraGlass
import com.example.ui.theme.CameraHairline
import com.example.ui.theme.CameraSurface
import com.example.ui.theme.CameraSurfaceElevated
import com.example.ui.theme.CoolCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

/**
 * Calibration & Validation Lab enabling verification of all 5 Photographic Styles
 * across skin tones, indoor light, outdoor daylight, landscapes, blue skies,
 * green vegetation, low-light, high-contrast scenes, and user-imported photos.
 */
@Composable
fun CalibrationLabScreen(
    uiState: CameraUiState,
    onSelectScene: (CalibrationSceneId) -> Unit,
    onImportPhoto: (Uri) -> Unit,
    onSetLabViewMode: (LabViewMode) -> Unit,
    onSelectStylesTab: (StylesPanelTab) -> Unit,
    onSelectStyle: (PhotographicStyle) -> Unit,
    onToneChanged: (Int) -> Unit,
    onWarmthChanged: (Int) -> Unit,
    onSelectTextureMode: (TextureMode) -> Unit,
    onToggleGrain: () -> Unit,
    onTextureIntensityChanged: (Int) -> Unit,
    onResetStyle: () -> Unit,
    onSetPalettePanelOpen: (Boolean) -> Unit,
    onUpdatePalettePad: (Float, Float) -> Unit,
    onUpdatePaletteWarmth: (Int) -> Unit,
    onResetPalette: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onImportPhoto(it) }
    }

    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("lab_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = TextPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.label_calibration_lab),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mode Toggle: Inspector vs Compare All 5
                Surface(
                    onClick = {
                        val next = if (uiState.labViewMode == LabViewMode.INSPECTOR) {
                            LabViewMode.COMPARE_ALL_5
                        } else {
                            LabViewMode.INSPECTOR
                        }
                        onSetLabViewMode(next)
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = CameraSurfaceElevated,
                    border = BorderStroke(1.dp, CameraHairline),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics(mergeDescendants = true) {}
                        .testTag("lab_mode_toggle")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.labViewMode == LabViewMode.INSPECTOR) {
                                Icons.Rounded.GridView
                            } else {
                                Icons.Rounded.Tune
                            },
                            contentDescription = null,
                            tint = AppleYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (uiState.labViewMode == LabViewMode.INSPECTOR) {
                                stringResource(R.string.label_compare_all)
                            } else {
                                stringResource(R.string.label_single_inspect)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                // Reset Style/Texture button
                if (uiState.activeParameters.isModified) {
                    IconButton(
                        onClick = onResetStyle,
                        modifier = Modifier.testTag("lab_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.label_reset),
                            tint = AppleYellow
                        )
                    }
                }

                // Zero-permission Photo Picker Import
                Surface(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = AppleYellow.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AppleYellow.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics(mergeDescendants = true) {}
                        .testTag("lab_import_photo_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.label_import_photo),
                            tint = AppleYellow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Scene Selector Pills (Skin Tones, Daylight/Sky/Foliage, Low-Light, High-Contrast)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val scenes = CalibrationSceneId.entries.filter {
                it != CalibrationSceneId.CUSTOM_IMPORTED || uiState.selectedSceneId == CalibrationSceneId.CUSTOM_IMPORTED
            }
            items(scenes, key = { it.name }) { scene ->
                val selected = scene == uiState.selectedSceneId
                Surface(
                    onClick = { onSelectScene(scene) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) AppleYellow else CameraSurfaceElevated,
                    border = BorderStroke(1.dp, if (selected) AppleYellow else CameraHairline),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scene_chip_${scene.name}")
                ) {
                    Text(
                        text = scene.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) CameraBlack else TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = uiState.selectedSceneId.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.labViewMode == LabViewMode.COMPARE_ALL_5) {
            // 5-Style Contact Sheet Grid Comparison
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(PhotographicStyle.entries, key = { it.id }) { style ->
                    val bmp = uiState.labComparisonThumbnails[style]
                    val isSelected = style == uiState.selectedStyle
                    val styleParams = uiState.styleMemory[style]
                    Surface(
                        onClick = {
                            onSelectStyle(style)
                            onSetLabViewMode(LabViewMode.INSPECTOR)
                        },
                        color = CameraSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AppleYellow else CameraHairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compare_card_${style.id}")
                    ) {
                        Column {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(style.titleRes),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 10f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(style.titleRes),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) AppleYellow else TextPrimary
                                    )
                                    Text(
                                        text = stringResource(style.descriptionRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                if (styleParams != null) {
                                    Text(
                                        text = "T ${signedLab(styleParams.tone)} · W ${signedLab(styleParams.warmth)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppleYellow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Inspector Mode: Before / After Drag-Wipe Split + Telemetry + Style Controls
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CameraSurface)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (size.width > 0) {
                                splitFraction = (change.position.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val orig = uiState.labOriginalBitmap
                val styled = uiState.labStyledBitmap
                if (orig != null && styled != null) {
                    // Styled Layer (Right side / full background)
                    Image(
                        bitmap = styled.asImageBitmap(),
                        contentDescription = "Styled scene preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Original Layer clipped to left side up to splitFraction
                    val clipShape = remember(splitFraction) { LeftFractionShape(splitFraction) }
                    Image(
                        bitmap = orig.asImageBitmap(),
                        contentDescription = "Original unstyled scene preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(clipShape)
                    )

                    // Split Wipe Line & Handle
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val lineOffsetPx = (widthPx * splitFraction).roundToInt()

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(lineOffsetPx - 1, 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(AppleYellow)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(lineOffsetPx - 36, 0) }
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AppleYellow)
                    ) {
                        Text(
                            text = "⇄",
                            style = MaterialTheme.typography.labelMedium,
                            color = CameraBlack
                        )
                    }

                    // Corner Badges
                    Surface(
                        color = CameraGlass,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_original).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = CameraGlass,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(uiState.selectedStyle.titleRes).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleYellow,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    CircularProgressIndicator(color = AppleYellow)
                }
            }

            // Live Color Science Telemetry Row
            uiState.labMetrics?.let { metrics ->
                Spacer(modifier = Modifier.height(8.dp))
                ColorScienceTelemetryStrip(metrics = metrics)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isPalettePanelOpen) {
                Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                    CompactPaletteDeck(
                        palette = uiState.activePalette,
                        style = uiState.selectedStyle,
                        onPadChanged = onUpdatePalettePad,
                        onWarmthChanged = onUpdatePaletteWarmth,
                        onResetPalette = onResetPalette,
                        onClose = { onSetPalettePanelOpen(false) }
                    )
                }
            } else {
                Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
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
                }
            }
        }
    }
}

@Composable
private fun ColorScienceTelemetryStrip(metrics: SceneColorMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryBadge(
            label = "L* TONE",
            value = String.format("%.1f", metrics.meanLightness),
            accent = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        TelemetryBadge(
            label = "b* WARMTH",
            value = String.format("%+.1f", metrics.meanWarmthB),
            accent = if (metrics.meanWarmthB >= 0f) WarmAmber else CoolCyan,
            modifier = Modifier.weight(1f)
        )
        TelemetryBadge(
            label = "C* CHROMA",
            value = String.format("%.1f", metrics.meanChroma),
            accent = AppleYellow,
            modifier = Modifier.weight(1f)
        )
        TelemetryBadge(
            label = if (metrics.skinPixelFraction > 0.01f) "SKIN Δh°" else "HL SAFE",
            value = if (metrics.skinPixelFraction > 0.01f) {
                String.format("%.1f°", metrics.skinHueDriftDegrees)
            } else {
                String.format("%.0f%%", metrics.highlightHeadroomRatio * 100f)
            },
            accent = SuccessGreen,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryBadge(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = CameraSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CameraHairline)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

private class LeftFractionShape(private val fraction: Float) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): androidx.compose.ui.graphics.Outline {
        val right = (size.width * fraction).coerceIn(0f, size.width)
        return androidx.compose.ui.graphics.Outline.Rectangle(
            androidx.compose.ui.geometry.Rect(0f, 0f, right, size.height)
        )
    }
}

private fun signedLab(value: Int): String = if (value > 0) "+$value" else "$value"
