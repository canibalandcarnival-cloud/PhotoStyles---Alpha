package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RawOn
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.camera.CameraFlashMode
import com.example.camera.CameraTimerMode
import com.example.camera.NightModeSetting
import com.example.ui.CameraUiState
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS 27 Liquid Glass Camera Controls Drawer matching Reference Image 2:
 * Rounded translucent dark glass bottom sheet with 3-column circular control grid
 * and inline expandable exposure slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraControlsDrawer(
    visible: Boolean,
    uiState: CameraUiState,
    onDismiss: () -> Unit,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onExposureChanged: (Float) -> Unit,
    onOpenStyles: () -> Unit,
    onOpenPalette: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCycleNightMode: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleLevel: () -> Unit,
    onToggleMaxResolution: () -> Unit,
    onToggleFormatOrProMax: () -> Unit,
    onOpenLab: () -> Unit
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }

    var showInlineExposure by remember(visible) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(180)
            ) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val mpValue = when {
                !uiState.useMaxResolution -> 12
                uiState.hardwareCapabilities.maxSensorMegapixels >= 40f -> 48
                uiState.hardwareCapabilities.maxSensorMegapixels >= 20f -> 24
                uiState.hardwareCapabilities.maxSensorMegapixels >= 10f ->
                    uiState.hardwareCapabilities.maxSensorMegapixels.roundToInt()
                else -> 12
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(liquidGlassPanelBrush())
                    .border(1.dp, liquidGlassBorderBrush(), RoundedCornerShape(32.dp))
                    .pointerInput(Unit) {
                        var dragSum = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragSum = 0f },
                            onVerticalDrag = { _, delta -> dragSum += delta },
                            onDragEnd = {
                                if (dragSum > 70f) onDismiss()
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .testTag("camera_controls_drawer")
            ) {
                // Liquid Glass Sheet Handle Pill
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.36f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Expandable EV Slider when Exposure is tapped
                AnimatedVisibility(
                    visible = showInlineExposure && uiState.hardwareCapabilities.exposureCapabilities.supported,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val expCaps = uiState.hardwareCapabilities.exposureCapabilities
                    Surface(
                        color = Color(0x88101116),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_ev),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppleYellow
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = uiState.exposureEv,
                                onValueChange = onExposureChanged,
                                valueRange = expCaps.minEv..expCaps.maxEv,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppleYellow,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drawer_exposure_slider")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format("%+.1f", uiState.exposureEv),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (abs(uiState.exposureEv) > 0.05f) AppleYellow else TextPrimary
                            )
                        }
                    }
                }

                // Row 1: FLASH | TIMER | EXPOSURE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_flash),
                        subValue = uiState.flashMode.label.uppercase(),
                        isActive = uiState.flashMode == CameraFlashMode.ON,
                        isDimmed = uiState.flashMode == CameraFlashMode.OFF,
                        onClick = onCycleFlash,
                        testTag = "drawer_flash_button"
                    ) { tint ->
                        Icon(
                            imageVector = when (uiState.flashMode) {
                                CameraFlashMode.AUTO -> Icons.Rounded.FlashAuto
                                CameraFlashMode.ON -> Icons.Rounded.FlashOn
                                CameraFlashMode.OFF -> Icons.Rounded.FlashOff
                            },
                            contentDescription = stringResource(R.string.cd_toggle_flash),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_timer),
                        subValue = if (uiState.timerMode == CameraTimerMode.OFF) null else uiState.timerMode.label,
                        isActive = uiState.timerMode != CameraTimerMode.OFF,
                        onClick = onCycleTimer,
                        testTag = "drawer_timer_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = stringResource(R.string.cd_toggle_timer),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val expSupported = uiState.hardwareCapabilities.exposureCapabilities.supported
                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_exposure),
                        subValue = if (abs(uiState.exposureEv) > 0.05f) {
                            String.format("%+.1f", uiState.exposureEv)
                        } else {
                            null
                        },
                        isActive = showInlineExposure || abs(uiState.exposureEv) > 0.05f,
                        enabled = expSupported,
                        onClick = { showInlineExposure = !showInlineExposure },
                        testTag = "drawer_exposure_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Exposure,
                            contentDescription = stringResource(R.string.cd_toggle_exposure),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Row 2: STYLES | PALETTE | ASPECT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_styles),
                        subValue = uiState.selectedStyle.shortName.uppercase(),
                        isActive = uiState.isStylesTrayExpanded && !uiState.isPalettePanelOpen,
                        onClick = onOpenStyles,
                        testTag = "drawer_styles_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesomeMotion,
                            contentDescription = stringResource(R.string.cd_toggle_styles),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_palette),
                        subValue = if (uiState.activePalette.isModified) {
                            "H${uiState.activePalette.hue}"
                        } else {
                            null
                        },
                        isActive = uiState.isPalettePanelOpen || uiState.activePalette.isModified,
                        onClick = onOpenPalette,
                        testTag = "drawer_palette_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = stringResource(R.string.cd_open_palette),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_aspect),
                        isActive = uiState.aspectRatio.label != "4:3",
                        onClick = onCycleAspectRatio,
                        testTag = "drawer_aspect_button"
                    ) { tint ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .border(1.4.dp, tint, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = uiState.aspectRatio.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = tint
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Row 3: NIGHT MODE | GRID | LEVEL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_night_mode),
                        subValue = uiState.nightMode.label.uppercase(),
                        isActive = uiState.nightMode == NightModeSetting.ON ||
                            (uiState.nightMode == NightModeSetting.AUTO && uiState.effectiveLiveTelemetry.isLowLight),
                        isDimmed = uiState.nightMode == NightModeSetting.OFF,
                        onClick = onCycleNightMode,
                        testTag = "drawer_night_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.NightlightRound,
                            contentDescription = stringResource(R.string.cd_toggle_night),
                            tint = tint,
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_grid),
                        isActive = uiState.showGrid,
                        onClick = onToggleGrid,
                        testTag = "drawer_grid_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.GridOn,
                            contentDescription = stringResource(R.string.cd_toggle_grid),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_level),
                        isActive = uiState.showLevel,
                        onClick = onToggleLevel,
                        testTag = "drawer_level_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Straighten,
                            contentDescription = stringResource(R.string.cd_toggle_level),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Row 4: RESOLUTION | FORMAT | STYLE LAB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_resolution),
                        isActive = uiState.useMaxResolution,
                        onClick = onToggleMaxResolution,
                        testTag = "drawer_resolution_button"
                    ) { tint ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (uiState.useMaxResolution) "MAX" else "STD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = tint
                            )
                            Text(
                                text = "${mpValue}MP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = tint.copy(alpha = 0.85f)
                            )
                        }
                    }

                    val supportsRaw = uiState.hardwareCapabilities.supportsRawDng
                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_format),
                        isActive = if (supportsRaw) uiState.rawDngEnabled else uiState.proMaxEnabled,
                        onClick = onToggleFormatOrProMax,
                        testTag = "drawer_format_button"
                    ) { tint ->
                        if (supportsRaw) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.RawOn,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (uiState.rawDngEnabled) "DNG+JPG" else "JPG 97",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = tint
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.HighQuality,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = if (uiState.proMaxEnabled) "PRO HDR" else "JPG 97",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = tint
                                )
                            }
                        }
                    }

                    DrawerControlTile(
                        label = stringResource(R.string.ctrl_style_lab),
                        isActive = false,
                        onClick = {
                            onDismiss()
                            onOpenLab()
                        },
                        testTag = "drawer_lab_button"
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = stringResource(R.string.cd_open_lab),
                            tint = tint,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DrawerControlTile(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    subValue: String? = null,
    isDimmed: Boolean = false,
    enabled: Boolean = true,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val circleBg by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0x33141519)
            isActive -> Color(0x30FFD60A)
            else -> Color(0x70262830)
        },
        label = "drawerTileBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive && enabled) {
            AppleYellow.copy(alpha = 0.60f)
        } else {
            Color.White.copy(alpha = 0.14f)
        },
        label = "drawerTileBorder"
    )
    val tint = when {
        !enabled -> TextTertiary.copy(alpha = 0.5f)
        isActive -> AppleYellow
        isDimmed -> TextTertiary
        else -> TextPrimary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag)
    ) {
        Surface(
            color = circleBg,
            shape = CircleShape,
            border = BorderStroke(0.9.dp, borderColor),
            modifier = Modifier
                .size(56.dp)
                .minimumInteractiveComponentSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                iconContent(tint)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = if (isActive && enabled) AppleYellow else TextPrimary.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!subValue.isNullOrBlank()) {
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.4.sp
                ),
                color = if (isActive) AppleYellow.copy(alpha = 0.85f) else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
