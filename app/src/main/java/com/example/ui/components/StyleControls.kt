package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.StyleParameters
import com.example.colorscience.StylesPanelTab
import com.example.colorscience.TextureMode
import com.example.colorscience.TextureSettings
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraBlack
import com.example.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val CircleInactiveBg = Color(0xFF1C1C1E)
private val CircleInactiveIcon = Color(0xFF8E8E93)
private val CircleActiveBg = Color(0xFFFFFFFF)
private val CircleActiveIcon = Color(0xFF141416)
private val SegmentTrackBg = Color(0xFF1C1C1E)
private val SegmentActiveBg = Color(0xFF48484A)
private val MutedLabelColor = Color(0xFF8E8E93)
private val DividerHairline = Color(0xFF2C2C2E)

private enum class PaletteAxis {
    TONE,
    WARMTH
}

/**
 * Clean pure-black Styles Top Header matching the reference layout:
 * Left circular '<' back | Center 'STYLES' | Right circular Reset icon.
 */
@Composable
fun StylesTopHeaderBar(
    isModified: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraBlack)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = CircleInactiveBg,
            modifier = Modifier
                .size(38.dp)
                .minimumInteractiveComponentSize()
                .testTag("styles_back_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.label_styles_title),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF9E9EA6)
        )

        Surface(
            onClick = onReset,
            enabled = isModified,
            shape = CircleShape,
            color = CircleInactiveBg,
            modifier = Modifier
                .size(38.dp)
                .minimumInteractiveComponentSize()
                .testTag("reset_style_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.label_reset),
                    tint = if (isModified) Color.White else Color(0xFF5A5A60),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Minimal pure-black circular Styles & Texture control panel inspired by iPhone's Styles UI:
 * - Centered pill segmented control: PALETTE | TEXTURE
 * - Horizontal row of circular icon buttons + centered uppercase labels below
 * - Compact yellow vertical-tick adjustment bar near the bottom
 */
@Composable
fun CircularStylesControlPanel(
    selectedTab: StylesPanelTab,
    parameters: StyleParameters,
    onSelectTab: (StylesPanelTab) -> Unit,
    onSelectStyle: (PhotographicStyle) -> Unit,
    onToneChanged: (Int) -> Unit,
    onWarmthChanged: (Int) -> Unit,
    onOpenPalette: () -> Unit,
    onSelectTextureMode: (TextureMode) -> Unit,
    onToggleGrain: () -> Unit,
    onTextureIntensityChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeAxis by remember { mutableStateOf(PaletteAxis.TONE) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraBlack)
            .padding(top = 10.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered pill segmented switcher: PALETTE | TEXTURE
        SegmentedStylesTabBar(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab
        )

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(140)) },
            label = "stylesPanelTabContent"
        ) { tab ->
            when (tab) {
                StylesPanelTab.PALETTE -> {
                    PaletteCircularContent(
                        parameters = parameters,
                        activeAxis = activeAxis,
                        onSelectAxis = { activeAxis = it },
                        onSelectStyle = onSelectStyle,
                        onToneChanged = onToneChanged,
                        onWarmthChanged = onWarmthChanged,
                        onOpenPalette = onOpenPalette
                    )
                }

                StylesPanelTab.TEXTURE -> {
                    TextureCircularContent(
                        texture = parameters.texture,
                        onSelectTextureMode = onSelectTextureMode,
                        onToggleGrain = onToggleGrain,
                        onIntensityChanged = onTextureIntensityChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedStylesTabBar(
    selectedTab: StylesPanelTab,
    onSelectTab: (StylesPanelTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = SegmentTrackBg,
        modifier = Modifier.testTag("styles_segmented_tabs")
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StylesPanelTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) SegmentActiveBg else Color.Transparent,
                    animationSpec = tween(160),
                    label = "segmentTabBg"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MutedLabelColor,
                    animationSpec = tween(160),
                    label = "segmentTabText"
                )
                val labelText = when (tab) {
                    StylesPanelTab.PALETTE -> stringResource(R.string.tab_palette)
                    StylesPanelTab.TEXTURE -> stringResource(R.string.tab_texture)
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bgColor)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("styles_tab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.7.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = labelColor
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteCircularContent(
    parameters: StyleParameters,
    activeAxis: PaletteAxis,
    onSelectAxis: (PaletteAxis) -> Unit,
    onSelectStyle: (PhotographicStyle) -> Unit,
    onToneChanged: (Int) -> Unit,
    onWarmthChanged: (Int) -> Unit,
    onOpenPalette: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Horizontal row of 5 circular Photographic Style buttons (zero pixel-art thumbnails)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("style_selector_strip"),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top
        ) {
            PhotographicStyle.entries.forEach { style ->
                val isSelected = style == parameters.style
                val label = when (style) {
                    PhotographicStyle.STANDARD -> "STANDARD"
                    PhotographicStyle.RICH_CONTRAST -> "RICH\nCONTRAST"
                    PhotographicStyle.VIBRANT -> "VIBRANT"
                    PhotographicStyle.WARM -> "WARM"
                    PhotographicStyle.COOL -> "COOL"
                }

                CircularEffectButton(
                    label = label,
                    selected = isSelected,
                    hasIndicatorDot = isSelected && parameters.isModified,
                    onClick = { onSelectStyle(style) },
                    testTag = "style_card_${style.id}"
                ) { iconTint ->
                    StyleVectorGlyph(style = style, tint = iconTint)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact axis selector pills + 2D Palette trigger
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AxisPillChip(
                label = "TONE ${signedValue(parameters.tone)}",
                selected = activeAxis == PaletteAxis.TONE,
                modified = parameters.tone != parameters.style.defaultTone,
                onClick = { onSelectAxis(PaletteAxis.TONE) },
                testTag = "axis_pill_tone"
            )
            AxisPillChip(
                label = "WARMTH ${signedValue(parameters.warmth)}",
                selected = activeAxis == PaletteAxis.WARMTH,
                modified = parameters.warmth != parameters.style.defaultWarmth,
                onClick = { onSelectAxis(PaletteAxis.WARMTH) },
                testTag = "axis_pill_warmth"
            )
            Surface(
                onClick = onOpenPalette,
                shape = RoundedCornerShape(14.dp),
                color = if (parameters.palette.isModified) AppleYellow else CircleInactiveBg,
                border = BorderStroke(
                    0.8.dp,
                    if (parameters.palette.isModified) AppleYellow else DividerHairline
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics(mergeDescendants = true) {}
                    .testTag("open_palette_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = stringResource(R.string.cd_open_palette),
                        tint = if (parameters.palette.isModified) CameraBlack else AppleYellow,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.label_2d_palette),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = if (parameters.palette.isModified) CameraBlack else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Yellow vertical-tick scrubber bar adjusting Tone or Warmth smoothly (-100..+100)
        val scrubberValue = if (activeAxis == PaletteAxis.TONE) parameters.tone else parameters.warmth
        YellowTickScrubberBar(
            value = scrubberValue,
            minValue = -100,
            maxValue = 100,
            onValueChange = { v ->
                if (activeAxis == PaletteAxis.TONE) onToneChanged(v) else onWarmthChanged(v)
            },
            testTag = if (activeAxis == PaletteAxis.TONE) "tone_slider" else "warmth_slider"
        )
    }
}

@Composable
private fun TextureCircularContent(
    texture: TextureSettings,
    onSelectTextureMode: (TextureMode) -> Unit,
    onToggleGrain: () -> Unit,
    onIntensityChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Exact reference layout: STANDARD · SOFT SKIN · GLOW · FILM | GRAIN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .testTag("texture_selector_strip"),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top
        ) {
            CircularEffectButton(
                label = stringResource(R.string.texture_standard),
                selected = texture.mode == TextureMode.STANDARD && !texture.grainEnabled,
                onClick = { onSelectTextureMode(TextureMode.STANDARD) },
                testTag = "texture_btn_standard"
            ) { tint ->
                CrossedCircleGlyph(tint = tint)
            }

            CircularEffectButton(
                label = stringResource(R.string.texture_soft_skin),
                selected = texture.mode == TextureMode.SOFT_SKIN,
                onClick = { onSelectTextureMode(TextureMode.SOFT_SKIN) },
                testTag = "texture_btn_soft_skin"
            ) { tint ->
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(25.dp)
                )
            }

            CircularEffectButton(
                label = stringResource(R.string.texture_glow),
                selected = texture.mode == TextureMode.GLOW,
                onClick = { onSelectTextureMode(TextureMode.GLOW) },
                testTag = "texture_btn_glow"
            ) { tint ->
                DottedRingGlowGlyph(tint = tint)
            }

            CircularEffectButton(
                label = stringResource(R.string.texture_film),
                selected = texture.mode == TextureMode.FILM,
                onClick = { onSelectTextureMode(TextureMode.FILM) },
                testTag = "texture_btn_film"
            ) { tint ->
                FilmFrameGlyph(tint = tint)
            }

            // Thin vertical hairline divider between FILM and GRAIN matching reference image
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, start = 2.dp, end = 2.dp)
                    .width(1.dp)
                    .height(38.dp)
                    .background(DividerHairline)
            )

            CircularEffectButton(
                label = stringResource(R.string.texture_grain),
                selected = texture.grainEnabled,
                onClick = onToggleGrain,
                testTag = "texture_btn_grain"
            ) { tint ->
                GrainDotMatrixGlyph(tint = tint)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (texture.isModified) {
            Text(
                text = "${texture.activeEffectLabel} · ${texture.activeIntensityValue}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = AppleYellow
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Yellow vertical-tick intensity scrubber matching reference image
        YellowTickScrubberBar(
            value = texture.activeIntensityValue,
            minValue = 0,
            maxValue = 100,
            enabled = texture.isModified,
            onValueChange = onIntensityChanged,
            testTag = "texture_intensity_scrubber"
        )
    }
}

@Composable
private fun CircularEffectButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    hasIndicatorDot: Boolean = false,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) CircleActiveBg else CircleInactiveBg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "effectCircleBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) CircleActiveIcon else CircleInactiveIcon,
        animationSpec = tween(150),
        label = "effectIconTint"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) Color.White else MutedLabelColor,
        animationSpec = tween(150),
        label = "effectLabelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(60.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {}
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .then(
                    if (selected) {
                        Modifier.shadow(
                            elevation = 14.dp,
                            shape = CircleShape,
                            ambientColor = Color.White,
                            spotColor = Color.White.copy(alpha = 0.85f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(bgColor)
        ) {
            iconContent(iconTint)

            if (hasIndicatorDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AppleYellow)
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                lineHeight = 11.5.sp,
                letterSpacing = 0.4.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AxisPillChip(
    label: String,
    selected: Boolean,
    modified: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF2C2C2E) else CircleInactiveBg,
        border = BorderStroke(
            0.8.dp,
            when {
                selected && modified -> AppleYellow.copy(alpha = 0.7f)
                selected -> Color.White.copy(alpha = 0.25f)
                else -> DividerHairline
            }
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.4.sp
            ),
            color = when {
                modified -> AppleYellow
                selected -> Color.White
                else -> MutedLabelColor
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * Compact yellow vertical-tick adjustment bar matching the reference `||||||||||` indicator.
 * Supports tap or horizontal drag to scrub real-time intensity/tone/warmth values.
 */
@Composable
fun YellowTickScrubberBar(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    testTag: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val latestOnChange by rememberUpdatedState(onValueChange)
    val span = (maxValue - minValue).coerceAtLeast(1)
    val fraction = ((value - minValue).toFloat() / span.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(136.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(enabled, minValue, maxValue) {
                if (!enabled) return@pointerInput
                detectTapGestures { pos ->
                    val frac = (pos.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    latestOnChange((minValue + frac * span).roundToInt().coerceIn(minValue, maxValue))
                }
            }
            .pointerInput(enabled, minValue, maxValue) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    latestOnChange((minValue + frac * span).roundToInt().coerceIn(minValue, maxValue))
                }
            }
            .testTag(testTag)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .padding(horizontal = 6.dp)
        ) {
            val tickCount = 13
            val spacing = size.width / (tickCount - 1)
            val activePos = fraction * (tickCount - 1)
            val isBipolar = minValue < 0 && maxValue > 0

            for (i in 0 until tickCount) {
                val x = i * spacing
                val isCenter = isBipolar && i == tickCount / 2
                val isLit = if (!enabled) {
                    false
                } else if (isBipolar) {
                    val mid = (tickCount - 1) / 2f
                    (i >= mid && i <= activePos + 0.45f && fraction > 0.505f) ||
                        (i <= mid && i >= activePos - 0.45f && fraction < 0.495f) ||
                        isCenter
                } else {
                    i <= activePos + 0.45f
                }

                val tickColor = when {
                    !enabled -> Color(0xFF424246)
                    isLit -> AppleYellow
                    else -> Color(0xFF4E4A36)
                }
                val strokeW = if (isCenter) 2.2.dp.toPx() else 1.8.dp.toPx()
                val topY = if (isCenter) 0f else size.height * 0.14f
                val bottomY = if (isCenter) size.height else size.height * 0.86f
                drawLine(
                    color = tickColor,
                    start = Offset(x, topY),
                    end = Offset(x, bottomY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun StyleVectorGlyph(style: PhotographicStyle, tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.36f
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round)
        when (style) {
            PhotographicStyle.STANDARD -> {
                drawCircle(color = tint, radius = r, center = c, style = stroke)
                drawCircle(color = tint, radius = r * 0.32f, center = c)
            }
            PhotographicStyle.RICH_CONTRAST -> {
                drawCircle(color = tint, radius = r, center = c, style = stroke)
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2f, r * 2f)
                )
            }
            PhotographicStyle.VIBRANT -> {
                drawCircle(color = tint, radius = r * 0.44f, center = c)
                for (i in 0 until 8) {
                    val angle = i * (PI / 4.0)
                    val inner = r * 0.70f
                    val outer = r * 1.06f
                    drawLine(
                        color = tint,
                        start = Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
                        end = Offset(c.x + (cos(angle) * outer).toFloat(), c.y + (sin(angle) * outer).toFloat()),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            PhotographicStyle.WARM -> {
                drawCircle(color = tint, radius = r * 0.56f, center = c, style = stroke)
                for (i in 0 until 6) {
                    val angle = i * (PI / 3.0)
                    val inner = r * 0.84f
                    val outer = r * 1.10f
                    drawLine(
                        color = tint,
                        start = Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
                        end = Offset(c.x + (cos(angle) * outer).toFloat(), c.y + (sin(angle) * outer).toFloat()),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            PhotographicStyle.COOL -> {
                for (i in 0 until 3) {
                    val angle = i * (PI / 3.0)
                    val dx = (cos(angle) * r * 0.98f).toFloat()
                    val dy = (sin(angle) * r * 0.98f).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(c.x - dx, c.y - dy),
                        end = Offset(c.x + dx, c.y + dy),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color = tint, radius = r * 0.24f, center = c)
            }
        }
    }
}

@Composable
private fun CrossedCircleGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.39f
        val sw = 1.8.dp.toPx()
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = sw))
        val d = r * 0.707f
        drawLine(
            color = tint,
            start = Offset(c.x - d, c.y - d),
            end = Offset(c.x + d, c.y + d),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun DottedRingGlowGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(23.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.36f
        val dotRadius = 1.4.dp.toPx()
        val dots = 10
        for (i in 0 until dots) {
            val a = i * (2.0 * PI / dots)
            drawCircle(
                color = tint,
                radius = dotRadius,
                center = Offset(
                    c.x + (cos(a) * r).toFloat(),
                    c.y + (sin(a) * r).toFloat()
                )
            )
        }
    }
}

@Composable
private fun FilmFrameGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width * 0.78f
        val h = size.height * 0.66f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val sw = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(2.2.dp.toPx()),
            style = Stroke(width = sw)
        )
        val sidePad = w * 0.25f
        drawLine(tint, Offset(left + sidePad, top), Offset(left + sidePad, top + h), strokeWidth = 1.3.dp.toPx())
        drawLine(tint, Offset(left + w - sidePad, top), Offset(left + w - sidePad, top + h), strokeWidth = 1.3.dp.toPx())
        drawLine(tint, Offset(left + sidePad, top + h / 2f), Offset(left + w - sidePad, top + h / 2f), strokeWidth = 1.3.dp.toPx())
        val stepY = h / 3f
        for (i in 1..2) {
            val y = top + i * stepY
            drawLine(tint, Offset(left, y), Offset(left + sidePad, y), strokeWidth = 1.2.dp.toPx())
            drawLine(tint, Offset(left + w - sidePad, y), Offset(left + w, y), strokeWidth = 1.2.dp.toPx())
        }
    }
}

@Composable
private fun GrainDotMatrixGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val cols = 5
        val margin = size.width * 0.16f
        val step = (size.width - margin * 2f) / (cols - 1)
        val r = 1.15.dp.toPx()
        for (row in 0 until cols) {
            for (col in 0 until cols) {
                val offsetShift = if (row % 2 == 1) step * 0.15f else 0f
                drawCircle(
                    color = tint,
                    radius = r,
                    center = Offset(
                        x = (margin + col * step + offsetShift).coerceAtMost(size.width - margin * 0.5f),
                        y = margin + row * step
                    )
                )
            }
        }
    }
}

private fun signedValue(value: Int): String = if (value > 0) "+$value" else "$value"

@Composable
fun AppleShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val innerScale by animateFloatAsState(
        targetValue = when {
            isCapturing -> 0.78f
            isPressed -> 0.86f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "shutterInnerScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(width = 3.5.dp, color = TextPrimary, shape = CircleShape)
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
                .clip(CircleShape)
                .background(if (isCapturing) AppleYellow else TextPrimary)
        )
    }
}
