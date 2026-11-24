package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.colorscience.BuiltIn2DPalettePresets
import com.example.colorscience.PaletteSettings
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.SavedPalettePreset
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraBlack
import com.example.ui.theme.CameraHairline
import com.example.ui.theme.CameraSurface
import com.example.ui.theme.CameraSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact rounded translucent control bar displaying live HUE, SATURATION, and TONE
 * palette telemetry values along with a Reset button to restore defaults.
 */
@Composable
fun TopPaletteAdjustmentBar(
    palette: PaletteSettings,
    onResetPalette: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPresets: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.testTag("top_palette_adjustment_bar"),
        color = Color(0xBF141518),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CameraHairline)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaletteMetricReadout(
                label = stringResource(R.string.label_hue),
                value = palette.hue,
                highlighted = palette.hue != 50
            )
            PaletteMetricReadout(
                label = stringResource(R.string.label_saturation),
                value = palette.saturation,
                highlighted = palette.saturation != 50
            )
            PaletteMetricReadout(
                label = stringResource(R.string.label_tone),
                value = palette.tone,
                highlighted = palette.tone != 50
            )

            if (onOpenPresets != null) {
                IconButton(
                    onClick = onOpenPresets,
                    modifier = Modifier
                        .size(34.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("open_palette_presets_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmarks,
                        contentDescription = stringResource(R.string.cd_palette_presets),
                        tint = if (palette.activePresetId != null) AppleYellow else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = onResetPalette,
                enabled = palette.isModified,
                modifier = Modifier
                    .size(34.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("reset_palette_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.label_reset_palette),
                    tint = if (palette.isModified) AppleYellow else TextTertiary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun PaletteMetricReadout(
    label: String,
    value: Int,
    highlighted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.7.sp
            ),
            color = TextSecondary
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = if (highlighted) AppleYellow else TextPrimary
        )
    }
}

/**
 * Interactive 2D rounded-square Photographic Styles Palette control pad.
 * Horizontal drag controls HUE & horizontal chroma; vertical drag controls TONE & intensity.
 */
@Composable
fun InteractiveColorPalettePad(
    padX: Float,
    padY: Float,
    style: PhotographicStyle,
    onPadChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    padSize: Dp = 128.dp
) {
    val latestOnPadChanged by rememberUpdatedState(onPadChanged)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val spectrumStops = remember(style) {
        when (style) {
            PhotographicStyle.WARM -> listOf(
                Color(0xFF4A4846),
                Color(0xFF7D6949),
                Color(0xFFC18D3C),
                Color(0xFFE09E36),
                Color(0xFFD67855)
            )
            PhotographicStyle.COOL -> listOf(
                Color(0xFF285F9C),
                Color(0xFF3E779A),
                Color(0xFF6B8596),
                Color(0xFF8A7A9C),
                Color(0xFFAA6F8E)
            )
            PhotographicStyle.VIBRANT -> listOf(
                Color(0xFF3378B8),
                Color(0xFF4F967B),
                Color(0xFFBFA04E),
                Color(0xFFE58C38),
                Color(0xFFD9596E)
            )
            PhotographicStyle.RICH_CONTRAST -> listOf(
                Color(0xFF2F567E),
                Color(0xFF4E685C),
                Color(0xFF8F7448),
                Color(0xFFB56E37),
                Color(0xFFA84C52)
            )
            PhotographicStyle.STANDARD -> listOf(
                Color(0xFF3C6FA2),
                Color(0xFF5D8178),
                Color(0xFF9C8658),
                Color(0xFFC98744),
                Color(0xFFC96868)
            )
        }
    }

    Box(
        modifier = modifier
            .size(padSize)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC18191D))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val inset = 12.dp.toPx()
                    val usableW = (w - inset * 2f).coerceAtLeast(1f)
                    val usableH = (h - inset * 2f).coerceAtLeast(1f)
                    val nx = ((offset.x - inset) / usableW).coerceIn(0f, 1f)
                    val ny = ((offset.y - inset) / usableH).coerceIn(0f, 1f)
                    latestOnPadChanged(nx, ny)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val inset = 12.dp.toPx()
                    val usableW = (w - inset * 2f).coerceAtLeast(1f)
                    val usableH = (h - inset * 2f).coerceAtLeast(1f)
                    val nx = ((change.position.x - inset) / usableW).coerceIn(0f, 1f)
                    val ny = ((change.position.y - inset) / usableH).coerceIn(0f, 1f)
                    latestOnPadChanged(nx, ny)
                }
            }
            .testTag("palette_2d_pad")
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val inset = 12.dp.toPx()
            val usableW = (w - inset * 2f).coerceAtLeast(1f)
            val usableH = (h - inset * 2f).coerceAtLeast(1f)
            val corner = CornerRadius(20.dp.toPx(), 20.dp.toPx())

            // Base horizontal multi-hue color field
            drawRoundRect(
                brush = Brush.horizontalGradient(colors = spectrumStops),
                cornerRadius = corner
            )

            // Vertical luminance & shadow depth shading (luminous top -> rich mid -> deep shadow bottom)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to Color(0x47FFFFFF),
                    0.44f to Color(0x14000000),
                    1.00f to Color(0xE60B0C10)
                ),
                cornerRadius = corner
            )

            // Subtle ambient highlight under the thumb
            val thumbCenter = Offset(
                x = inset + padX.coerceIn(0f, 1f) * usableW,
                y = inset + padY.coerceIn(0f, 1f) * usableH
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                    center = thumbCenter,
                    radius = w * 0.34f
                ),
                radius = w * 0.34f,
                center = thumbCenter
            )

            // 9x9 dotted crosshair matrix matching the iPhone Control Pad aesthetic
            val gridSteps = 8
            val activeCol = (padX.coerceIn(0f, 1f) * gridSteps).roundToInt()
            val activeRow = (padY.coerceIn(0f, 1f) * gridSteps).roundToInt()

            for (row in 0..gridSteps) {
                for (col in 0..gridSteps) {
                    val dotX = inset + (col.toFloat() / gridSteps) * usableW
                    val dotY = inset + (row.toFloat() / gridSteps) * usableH
                    val isCenterAxis = row == 4 || col == 4
                    val isActiveCrosshair = row == activeRow || col == activeCol
                    val dotAlpha = when {
                        isActiveCrosshair -> 0.72f
                        isCenterAxis -> 0.36f
                        else -> 0.18f
                    }
                    val dotRadius = when {
                        row == activeRow && col == activeCol -> 2.1.dp.toPx()
                        isActiveCrosshair -> 1.35.dp.toPx()
                        else -> 1.15.dp.toPx()
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = dotAlpha),
                        radius = dotRadius,
                        center = Offset(dotX, dotY)
                    )
                }
            }

            // Outer translucent halo + crisp white circular thumb
            drawCircle(
                color = Color.Black.copy(alpha = 0.36f),
                radius = 9.5.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = 9.dp.toPx(),
                center = thumbCenter,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Horizontal Color Temperature (Warmth) slider placed directly beneath the square 2D palette.
 * Gradient runs from cool blue (-100) through neutral white (0) to warm orange (+100).
 */
@Composable
fun WarmthGradientSlider(
    warmth: Int,
    onWarmthChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sliderWidth: Dp = 148.dp
) {
    val latestOnWarmthChanged by rememberUpdatedState(onWarmthChanged)
    val clamped = warmth.coerceIn(-100, 100)
    val norm = (clamped + 100f) / 200f

    Box(
        modifier = modifier
            .width(sliderWidth)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC18191D))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val inset = 12.dp.toPx()
                    val usable = (w - inset * 2f).coerceAtLeast(1f)
                    val t = ((offset.x - inset) / usable).coerceIn(0f, 1f)
                    latestOnWarmthChanged(((t * 200f) - 100f).roundToInt().coerceIn(-100, 100))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val inset = 12.dp.toPx()
                    val usable = (w - inset * 2f).coerceAtLeast(1f)
                    val t = ((change.position.x - inset) / usable).coerceIn(0f, 1f)
                    latestOnWarmthChanged(((t * 200f) - 100f).roundToInt().coerceIn(-100, 100))
                }
            }
            .testTag("palette_warmth_slider")
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0.00f to Color(0xFF3E84E5),
                    0.35f to Color(0xFF9AC2F4),
                    0.50f to Color(0xFFECEEF2),
                    0.65f to Color(0xFFF3BF83),
                    1.00f to Color(0xFFE0742B)
                ),
                cornerRadius = corner
            )

            // Center neutral tick mark
            val centerX = size.width / 2f
            drawLine(
                color = Color.Black.copy(alpha = 0.28f),
                start = Offset(centerX, 5.dp.toPx()),
                end = Offset(centerX, size.height - 5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )

            val inset = 12.dp.toPx()
            val usable = (size.width - inset * 2f).coerceAtLeast(1f)
            val thumbCenter = Offset(inset + norm * usable, size.height / 2f)

            drawCircle(
                color = Color.Black.copy(alpha = 0.30f),
                radius = 10.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = thumbCenter
            )
            if (abs(clamped) > 2) {
                drawCircle(
                    color = if (clamped > 0) Color(0xFFE0742B) else Color(0xFF3E84E5),
                    radius = 2.5.dp.toPx(),
                    center = thumbCenter
                )
            }
        }
    }
}

/**
 * Compact reusable Palette deck for Preview & Calibration Lab screens where the full
 * camera viewfinder isn't hosting the floating bottom pad directly.
 */
@Composable
fun CompactPaletteDeck(
    palette: PaletteSettings,
    style: PhotographicStyle,
    onPadChanged: (Float, Float) -> Unit,
    onWarmthChanged: (Int) -> Unit,
    onResetPalette: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color(0xCC141518),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CameraHairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopPaletteAdjustmentBar(
                    palette = palette,
                    onResetPalette = onResetPalette
                )

                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color(0xFF23252A),
                    border = BorderStroke(1.dp, CameraHairline),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_palette_button")
                ) {
                    Text(
                        text = stringResource(R.string.label_done),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleYellow,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            InteractiveColorPalettePad(
                padX = palette.padX,
                padY = palette.padY,
                style = style,
                onPadChanged = onPadChanged,
                padSize = 124.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            WarmthGradientSlider(
                warmth = palette.warmth,
                onWarmthChanged = onWarmthChanged,
                sliderWidth = 148.dp
            )
        }
    }
}

@Composable
fun PalettePresetsDialog(
    activePalette: PaletteSettings,
    customPresets: List<SavedPalettePreset>,
    onSelectPreset: (SavedPalettePreset) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var presetNameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CameraSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(
                text = stringResource(R.string.label_palette_presets_title),
                style = MaterialTheme.typography.titleMedium,
                color = AppleYellow
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "BUILT-IN PALETTES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BuiltIn2DPalettePresets.ALL, key = { it.id }) { preset ->
                        val selected = activePalette.activePresetId == preset.id
                        Surface(
                            onClick = { onSelectPreset(preset) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) AppleYellow else CameraSurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) AppleYellow else CameraHairline),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("preset_chip_${preset.id}")
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) CameraBlack else TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (customPresets.isNotEmpty()) {
                    Text(
                        text = "SAVED CUSTOM PALETTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(customPresets, key = { it.id }) { preset ->
                            val selected = activePalette.activePresetId == preset.id
                            Surface(
                                onClick = { onSelectPreset(preset) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) AppleYellow else CameraSurfaceElevated,
                                border = BorderStroke(1.dp, if (selected) AppleYellow else CameraHairline),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("custom_preset_chip_${preset.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) CameraBlack else TextPrimary
                                    )
                                    IconButton(
                                        onClick = { onDeleteCustomPreset(preset.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Delete ${preset.name}",
                                            tint = if (selected) CameraBlack else TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "SAVE CURRENT (HUE ${activePalette.hue} · SAT ${activePalette.saturation} · TONE ${activePalette.tone})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.hint_preset_name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppleYellow,
                            unfocusedBorderColor = CameraHairline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AppleYellow
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_preset_name_input")
                    )
                    Button(
                        onClick = {
                            val name = presetNameInput.trim()
                            if (name.isNotEmpty()) {
                                onSaveCustomPreset(name)
                                presetNameInput = ""
                            }
                        },
                        enabled = presetNameInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleYellow,
                            contentColor = CameraBlack
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("save_custom_preset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = stringResource(R.string.label_save_custom_palette),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.label_done),
                    color = AppleYellow
                )
            }
        }
    )
}
