package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.camera.CameraCaptureMode
import com.example.camera.CameraFlashMode
import com.example.camera.CameraTimerMode
import com.example.camera.ExposureCapabilities
import com.example.camera.NightModeSetting
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraBlack
import com.example.ui.theme.LiquidGlassBorderBright
import com.example.ui.theme.LiquidGlassBorderSubtle
import com.example.ui.theme.LiquidGlassFillBottom
import com.example.ui.theme.LiquidGlassFillTop
import com.example.ui.theme.LiquidGlassPill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VideoRecordRed
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

fun liquidGlassBorderBrush(): Brush = Brush.verticalGradient(
    colors = listOf(LiquidGlassBorderBright, LiquidGlassBorderSubtle)
)

fun liquidGlassPanelBrush(): Brush = Brush.verticalGradient(
    colors = listOf(LiquidGlassFillTop, LiquidGlassFillBottom)
)

@Composable
fun LiquidGlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    isActive: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color(0x33FFD60A) else LiquidGlassPill,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassCircleBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) AppleYellow.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.16f),
        label = "glassCircleBorder"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(0.9.dp, borderColor),
        modifier = modifier
            .size(size)
            .minimumInteractiveComponentSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * Minimalist iOS 27 Top Camera Bar matching Reference Image 1 with centered compact
 * Liquid Glass quick status controls (Resolution/Format, Flash, Night Mode, Exposure, Timer, Styles).
 */
@Composable
fun Ios27TopCameraBar(
    flashMode: CameraFlashMode,
    nightMode: NightModeSetting,
    isNightActive: Boolean,
    timerMode: CameraTimerMode,
    exposureEv: Float,
    supportsExposure: Boolean,
    isExposureOpen: Boolean,
    sensorMegapixels: Float,
    useMaxResolution: Boolean,
    rawDngEnabled: Boolean,
    supportsRawDng: Boolean,
    isStylesHighlighted: Boolean,
    isRecordingVideo: Boolean,
    recordingDurationSec: Int,
    onCycleFormatOrResolution: () -> Unit,
    onCycleFlash: () -> Unit,
    onCycleNightMode: () -> Unit,
    onToggleExposure: () -> Unit,
    onCycleTimer: () -> Unit,
    onOpenStyles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecordingVideo) {
            val mins = recordingDurationSec / 60
            val secs = recordingDurationSec % 60
            Surface(
                color = VideoRecordRed,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("recording_timer_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = Color.White
                    )
                }
            }
        } else {
            val mpValue = when {
                !useMaxResolution -> 12
                sensorMegapixels >= 40f -> 48
                sensorMegapixels >= 20f -> 24
                sensorMegapixels >= 10f -> sensorMegapixels.roundToInt()
                else -> 12
            }
            val formatLabel = if (rawDngEnabled && supportsRawDng) "RAW" else "JPG"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Format + MP stacked readout (e.g., JPG / 24 or RAW / 48)
                Surface(
                    onClick = onCycleFormatOrResolution,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_format_res_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = if (rawDngEnabled) AppleYellow else TextPrimary
                        )
                        Text(
                            text = mpValue.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextSecondary
                        )
                    }
                }

                // Flash
                TopBarIconToggle(
                    onClick = onCycleFlash,
                    isActive = flashMode == CameraFlashMode.ON,
                    isDimmed = flashMode == CameraFlashMode.OFF,
                    testTag = "flash_button"
                ) { tint ->
                    Icon(
                        imageVector = when (flashMode) {
                            CameraFlashMode.AUTO -> Icons.Rounded.FlashAuto
                            CameraFlashMode.ON -> Icons.Rounded.FlashOn
                            CameraFlashMode.OFF -> Icons.Rounded.FlashOff
                        },
                        contentDescription = stringResource(R.string.cd_toggle_flash),
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Timer
                TopBarIconToggle(
                    onClick = onCycleTimer,
                    isActive = timerMode != CameraTimerMode.OFF,
                    isDimmed = false,
                    testTag = "top_timer_button"
                ) { tint ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = stringResource(R.string.cd_toggle_timer),
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                        if (timerMode != CameraTimerMode.OFF) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = timerMode.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AppleYellow
                            )
                        }
                    }
                }

                // Night Mode
                TopBarIconToggle(
                    onClick = onCycleNightMode,
                    isActive = nightMode == NightModeSetting.ON || (nightMode == NightModeSetting.AUTO && isNightActive),
                    isDimmed = nightMode == NightModeSetting.OFF,
                    testTag = "top_night_button"
                ) { tint ->
                    Icon(
                        imageVector = Icons.Rounded.NightlightRound,
                        contentDescription = stringResource(R.string.cd_toggle_night),
                        tint = tint,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Exposure EV indicator (always interactive if supported)
                if (supportsExposure) {
                    val evActive = abs(exposureEv) > 0.05f || isExposureOpen
                    Surface(
                        onClick = onToggleExposure,
                        color = if (evActive) Color(0x28FFD60A) else LiquidGlassPill,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            0.8.dp,
                            if (evActive) AppleYellow.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_exposure_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Exposure,
                                contentDescription = stringResource(R.string.cd_toggle_exposure),
                                tint = if (evActive) AppleYellow else TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            if (abs(exposureEv) > 0.05f) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format("%+.1f", exposureEv),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = AppleYellow
                                )
                            }
                        }
                    }
                }

                // Photographic Styles shortcut
                TopBarIconToggle(
                    onClick = onOpenStyles,
                    isActive = isStylesHighlighted,
                    isDimmed = false,
                    testTag = "toggle_styles_drawer_button"
                ) { tint ->
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = stringResource(R.string.cd_toggle_styles),
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarIconToggle(
    onClick: () -> Unit,
    isActive: Boolean,
    isDimmed: Boolean,
    testTag: String,
    content: @Composable (tint: Color) -> Unit
) {
    val bgColor = if (isActive) Color(0x28FFD60A) else Color(0x521C1D22)
    val tint = when {
        isActive -> AppleYellow
        isDimmed -> TextTertiary
        else -> TextPrimary
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(
            0.7.dp,
            if (isActive) AppleYellow.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .size(34.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content(tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ios27ExposureBar(
    visible: Boolean,
    exposureEv: Float,
    exposureCapabilities: ExposureCapabilities,
    onExposureChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && exposureCapabilities.supported,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(120)),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xC8141519),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.9.dp, liquidGlassBorderBrush()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("exposure_slider_bar")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Exposure,
                    contentDescription = null,
                    tint = AppleYellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = exposureEv,
                    onValueChange = onExposureChanged,
                    valueRange = exposureCapabilities.minEv..exposureCapabilities.maxEv,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = AppleYellow,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("exposure_ev_slider")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%+.1f EV", exposureEv),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = if (abs(exposureEv) > 0.05f) AppleYellow else TextPrimary
                )
            }
        }
    }
}

/**
 * Floating iOS 27 Liquid Glass zoom stops bar above the shutter row.
 */
@Composable
fun Ios27ZoomPillBar(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (maxZoom < 1.3f) return

    val stops = remember(minZoom, maxZoom) {
        buildList {
            if (minZoom < 0.85f) add(0.5f)
            add(1f)
            if (maxZoom >= 2f) add(2f)
            if (maxZoom >= 4.8f) add(5f)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x66121317))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stops.forEach { stop ->
            val selected = abs(zoomRatio - stop) < 0.22f ||
                (stop == stops.last() && zoomRatio > stop)
            val circleSize by animateDpAsState(
                targetValue = if (selected) 34.dp else 28.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "zoomStopSize"
            )
            val label = if (selected) {
                val formatted = String.format("%.1fx", zoomRatio).replace(".0x", "x")
                formatted
            } else {
                if (stop < 1f) ".5" else "${stop.toInt()}"
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(
                        if (selected) Color(0x9E26272D) else Color.Transparent
                    )
                    .then(
                        if (selected) {
                            Modifier.border(0.9.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onZoomSelected(stop) }
                    .testTag("zoom_stop_${stop}")
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (selected) 11.sp else 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) AppleYellow else TextPrimary.copy(alpha = 0.86f)
                )
            }
        }
    }
}

/**
 * Hardware SensorManager-driven Horizon Spirit Level rendered inside the viewfinder when Level is toggled On.
 */
@Composable
fun HorizonLevelOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    var rawAngleDeg by remember { mutableFloatStateOf(0f) }
    var visibleTilt by remember { mutableFloatStateOf(1f) }

    DisposableEffect(enabled, context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || sensor == null) {
            onDispose {}
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val mag = sqrt(x * x + y * y + z * z).coerceAtLeast(0.1f)
                    val rollRad = atan2(x.toDouble(), y.toDouble())
                    var deg = Math.toDegrees(rollRad).toFloat()
                    // Normalize to nearest horizontal (-45..+45)
                    while (deg > 90f) deg -= 180f
                    while (deg < -90f) deg += 180f
                    rawAngleDeg = rawAngleDeg * 0.72f + deg * 0.28f
                    // Fade gently if phone is laid completely flat on a table
                    val verticalStrength = (sqrt(x * x + y * y) / mag).coerceIn(0f, 1f)
                    visibleTilt = if (verticalStrength > 0.28f) 1f else 0.25f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val isLevel = abs(rawAngleDeg) <= 1.5f
    val clampedDeg = rawAngleDeg.coerceIn(-35f, 35f)
    val lineColor by animateColorAsState(
        targetValue = (if (isLevel) AppleYellow else Color.White).copy(alpha = 0.85f * visibleTilt),
        label = "levelColor"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("horizon_level_overlay")
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val tickHalf = 46.dp.toPx()
        val gap = 28.dp.toPx()

        // Fixed left & right horizon reference ticks
        drawLine(
            color = Color.White.copy(alpha = 0.45f * visibleTilt),
            start = Offset(cx - tickHalf - gap, cy),
            end = Offset(cx - gap, cy),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.45f * visibleTilt),
            start = Offset(cx + gap, cy),
            end = Offset(cx + tickHalf + gap, cy),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Rotating live gravity bar
        val rad = Math.toRadians((-clampedDeg).toDouble())
        val barHalf = 24.dp.toPx()
        val dx = (cos(rad) * barHalf).toFloat()
        val dy = (sin(rad) * barHalf).toFloat()
        drawLine(
            color = lineColor,
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy),
            strokeWidth = if (isLevel) 2.4.dp.toPx() else 1.7.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * iOS 27 Bottom Deck matching Reference Image 1:
 * - Shutter Row: Left Styles glass button | Center Shutter | Right Controls Drawer (6-dot grid)
 * - Mode Row: Left Gallery thumbnail | Center swipeable VIDEO/PHOTO Liquid Glass pill | Right Switch Camera
 */
@Composable
fun Ios27BottomShutterDeck(
    cameraMode: CameraCaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    isStylesActive: Boolean,
    isStylesModified: Boolean,
    isDrawerOpen: Boolean,
    lastSavedThumbnail: Bitmap?,
    lastSavedIsVideo: Boolean,
    onOpenStyles: () -> Unit,
    onShutterClick: () -> Unit,
    onOpenControlsDrawer: () -> Unit,
    onSelectMode: (CameraCaptureMode) -> Unit,
    onGalleryClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnSelectMode by rememberUpdatedState(onSelectMode)
    val modeEntries = CameraCaptureMode.entries
    val modeIdx = modeEntries.indexOf(cameraMode).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraBlack)
            .pointerInput(cameraMode, isRecordingVideo) {
                if (isRecordingVideo) return@pointerInput
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (totalDrag < -44f && modeIdx < modeEntries.lastIndex) {
                            latestOnSelectMode(modeEntries[modeIdx + 1])
                        } else if (totalDrag > 44f && modeIdx > 0) {
                            latestOnSelectMode(modeEntries[modeIdx - 1])
                        }
                    }
                )
            }
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shutter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Photographic Styles shortcut (replaces decorative f/aperture in Reference Image 1 with real Styles!)
            Box(contentAlignment = Alignment.TopEnd) {
                LiquidGlassCircleButton(
                    onClick = onOpenStyles,
                    contentDescription = stringResource(R.string.cd_toggle_styles),
                    isActive = isStylesActive,
                    size = 46.dp,
                    modifier = Modifier.testTag("bottom_styles_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = if (isStylesActive) AppleYellow else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isStylesModified && !isStylesActive) {
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AppleYellow)
                    )
                }
            }

            // Center: Adaptive Photo / Video Shutter
            Ios27ShutterButton(
                cameraMode = cameraMode,
                isCapturing = isCapturing,
                isRecordingVideo = isRecordingVideo,
                onClick = onShutterClick
            )

            // Right: Camera Controls Drawer button (6-dot/apps icon matching Reference Image 1)
            LiquidGlassCircleButton(
                onClick = onOpenControlsDrawer,
                contentDescription = stringResource(R.string.cd_open_controls),
                isActive = isDrawerOpen,
                size = 46.dp,
                modifier = Modifier.testTag("controls_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = if (isDrawerOpen) AppleYellow else TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Navigation Row: Gallery | Mode Pill | Switch Camera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Circular Gallery Thumbnail
            Surface(
                onClick = onGalleryClick,
                shape = CircleShape,
                color = LiquidGlassPill,
                border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier
                    .size(46.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("last_photo_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (lastSavedThumbnail != null && !lastSavedIsVideo) {
                        Image(
                            bitmap = lastSavedThumbnail.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_last_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (lastSavedIsVideo) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = stringResource(R.string.cd_last_photo),
                            tint = AppleYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = stringResource(R.string.cd_last_photo),
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Horizontal Swipeable Liquid Glass Camera Mode Selector Pill
            Ios27ModeSelectorPill(
                selectedMode = cameraMode,
                enabled = !isRecordingVideo,
                onSelectMode = onSelectMode
            )

            // Circular Camera Switch Button
            LiquidGlassCircleButton(
                onClick = onSwitchCamera,
                contentDescription = stringResource(R.string.cd_switch_camera),
                enabled = !isRecordingVideo,
                size = 46.dp,
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
}

@Composable
fun Ios27ShutterButton(
    cameraMode: CameraCaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val innerScale by animateFloatAsState(
        targetValue = when {
            isCapturing -> 0.78f
            isPressed -> 0.87f
            isRecordingVideo -> 0.46f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ios27ShutterScale"
    )
    val cornerPercent by animateFloatAsState(
        targetValue = if (isRecordingVideo) 22f else 50f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ios27ShutterShape"
    )
    val innerColor by animateColorAsState(
        targetValue = when {
            cameraMode == CameraCaptureMode.VIDEO -> VideoRecordRed
            isCapturing -> AppleYellow
            else -> Color.White
        },
        label = "ios27ShutterColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(width = 3.5.dp, color = Color.White, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isCapturing,
                onClick = onClick
            )
            .testTag("shutter_button")
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .scale(innerScale)
                .clip(RoundedCornerShape(percent = cornerPercent.roundToInt()))
                .background(innerColor)
        )
    }
}

@Composable
fun Ios27ModeSelectorPill(
    selectedMode: CameraCaptureMode,
    enabled: Boolean,
    onSelectMode: (CameraCaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnSelect by rememberUpdatedState(onSelectMode)
    val modes = CameraCaptureMode.entries
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .width(176.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB8141519))
            .border(0.9.dp, liquidGlassBorderBrush(), RoundedCornerShape(22.dp))
            .pointerInput(selectedMode, enabled) {
                if (!enabled) return@pointerInput
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (totalDrag < -28f && selectedIndex < modes.lastIndex) {
                            latestOnSelect(modes[selectedIndex + 1])
                        } else if (totalDrag > 28f && selectedIndex > 0) {
                            latestOnSelect(modes[selectedIndex - 1])
                        }
                    }
                )
            }
            .padding(3.dp)
            .testTag("camera_mode_selector")
    ) {
        val segmentWidth = maxWidth / modes.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "modePillOffset"
        )

        // Glowing Liquid Glass active pill indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(19.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x52FFFFFF), Color(0x28FFFFFF))
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(19.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val isSelected = mode == selectedMode
                val textColor by animateColorAsState(
                    targetValue = when {
                        isSelected && mode == CameraCaptureMode.PHOTO -> AppleYellow
                        isSelected && mode == CameraCaptureMode.VIDEO -> Color.White
                        else -> Color.White.copy(alpha = 0.62f)
                    },
                    label = "modeLabelColor"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(19.dp))
                        .clickable(enabled = enabled) { onSelectMode(mode) }
                        .testTag("mode_tab_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = when (mode) {
                            CameraCaptureMode.VIDEO -> stringResource(R.string.mode_video)
                            CameraCaptureMode.PHOTO -> stringResource(R.string.mode_photo)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.9.sp
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}
