package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.colorscience.PhotographicStyle
import com.example.colorscience.StylesPanelTab
import com.example.colorscience.TextureMode
import com.example.ui.CameraUiState
import com.example.ui.components.CircularStylesControlPanel
import com.example.ui.components.CompactPaletteDeck
import com.example.ui.theme.AppleYellow
import com.example.ui.theme.CameraBlack
import com.example.ui.theme.CameraGlass
import com.example.ui.theme.CameraHairline
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Non-destructive post-capture preview screen shown before saving to Photos.
 * Users can compare Original vs Styled by pressing & holding, switch styles,
 * or fine-tune Tone & Warmth before exporting the full-resolution JPEG.
 */
@Composable
fun PhotoPreviewScreen(
    uiState: CameraUiState,
    onSelectStylesTab: (StylesPanelTab) -> Unit,
    onSelectStyle: (PhotographicStyle) -> Unit,
    onToneChanged: (Int) -> Unit,
    onWarmthChanged: (Int) -> Unit,
    onSelectTextureMode: (TextureMode) -> Unit,
    onToggleGrain: () -> Unit,
    onTextureIntensityChanged: (Int) -> Unit,
    onResetStyle: () -> Unit,
    onCompareHoldChanged: (Boolean) -> Unit,
    onSetPalettePanelOpen: (Boolean) -> Unit,
    onUpdatePalettePad: (Float, Float) -> Unit,
    onUpdatePaletteWarmth: (Int) -> Unit,
    onResetPalette: () -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit
) {
    BackHandler(enabled = !uiState.isSavingPhoto) {
        onRetake()
    }

    val previewBitmap = uiState.capturedStyledPreview ?: uiState.capturedPreviewProxy
    val fullRes = uiState.capturedOriginalFullRes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onRetake,
                enabled = !uiState.isSavingPhoto,
                modifier = Modifier.testTag("preview_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (uiState.isPreviewComparingOriginal) {
                        stringResource(R.string.label_original).uppercase()
                    } else {
                        stringResource(uiState.selectedStyle.titleRes).uppercase()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.isPreviewComparingOriginal) TextSecondary else AppleYellow
                )
                if (fullRes != null) {
                    val fusionSuffix = if (uiState.capturedFusedFrameCount > 1) {
                        " · ${uiState.capturedFusedFrameCount}-Frame HDR"
                    } else if (uiState.proMaxEnabled) {
                        " · Pro"
                    } else {
                        ""
                    }
                    val rawSuffix = if (uiState.capturedRawDngFile != null) " + DNG" else ""
                    Text(
                        text = "${fullRes.width} × ${fullRes.height}$fusionSuffix$rawSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Hold-to-compare Original + Reset actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.previewParameters.isModified || uiState.selectedStyle != PhotographicStyle.STANDARD) {
                    IconButton(
                        onClick = onResetStyle,
                        modifier = Modifier.testTag("preview_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.label_reset),
                            tint = AppleYellow
                        )
                    }
                }
                Surface(
                    color = if (uiState.isPreviewComparingOriginal) AppleYellow else CameraGlass,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, if (uiState.isPreviewComparingOriginal) AppleYellow else CameraHairline),
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onCompareHoldChanged(true)
                                    tryAwaitRelease()
                                    onCompareHoldChanged(false)
                                }
                            )
                        }
                        .testTag("preview_hold_compare")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Compare,
                            contentDescription = stringResource(R.string.label_hold_compare),
                            tint = if (uiState.isPreviewComparingOriginal) CameraBlack else TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.label_original),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.isPreviewComparingOriginal) CameraBlack else TextPrimary
                        )
                    }
                }
            }
        }

        // Photo Viewport (press & hold anywhere on photo to compare original!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onCompareHoldChanged(true)
                            tryAwaitRelease()
                            onCompareHoldChanged(false)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Captured photo preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(color = AppleYellow)
            }

            if (uiState.isPreviewComparingOriginal) {
                Surface(
                    color = CameraGlass,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CameraHairline),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_original).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Non-destructive Style & Palette Controls
        if (uiState.isPalettePanelOpen) {
            CompactPaletteDeck(
                palette = uiState.activePalette,
                style = uiState.selectedStyle,
                onPadChanged = onUpdatePalettePad,
                onWarmthChanged = onUpdatePaletteWarmth,
                onResetPalette = onResetPalette,
                onClose = { onSetPalettePanelOpen(false) }
            )
        } else {
            CircularStylesControlPanel(
                selectedTab = uiState.stylesPanelTab,
                parameters = uiState.previewParameters.copy(bypassOriginal = false),
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

        // Bottom Retake / Save Action Deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !uiState.isSavingPhoto,
                border = BorderStroke(1.dp, CameraHairline),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("retake_button")
            ) {
                Text(
                    text = stringResource(R.string.label_retake),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Button(
                onClick = onSave,
                enabled = !uiState.isSavingPhoto && fullRes != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleYellow,
                    contentColor = CameraBlack
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(50.dp)
                    .testTag("save_photo_button")
            ) {
                if (uiState.isSavingPhoto) {
                    CircularProgressIndicator(
                        color = CameraBlack,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.label_saving),
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.label_save_photo),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
