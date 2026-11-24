package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.colorscience.ComputationalPhotoEngine
import com.example.colorscience.SceneTelemetry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import android.annotation.SuppressLint
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CameraFlashMode(val label: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

enum class CameraCaptureMode(val label: String) {
    VIDEO("VIDEO"),
    PHOTO("PHOTO")
}

enum class NightModeSetting(val label: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

enum class CameraTimerMode(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    THREE_SEC(3, "3s"),
    TEN_SEC(10, "10s")
}

data class ExposureCapabilities(
    val supported: Boolean = false,
    val minEv: Float = -2f,
    val maxEv: Float = 2f,
    val stepEv: Float = 0.25f
)

data class CameraHardwareCapabilities(
    val supportsRawDng: Boolean = false,
    val supportsUltraHdr: Boolean = false,
    val supportsOis: Boolean = false,
    val supportsDistortionCorrection: Boolean = false,
    val supportsAberrationCorrection: Boolean = false,
    val supportsExposureBracketing: Boolean = false,
    val maxSensorMegapixels: Float = 0f,
    val sensorHardwareLabel: String = "Standard",
    val exposureCapabilities: ExposureCapabilities = ExposureCapabilities()
)

data class CapturedMasterPhoto(
    val enhancedMasterBitmap: Bitmap,
    val rawDngTempFile: File? = null,
    val fusedFrameCount: Int = 1,
    val sceneTelemetry: SceneTelemetry = SceneTelemetry.DEFAULT
)

/**
 * Stage 1 Camera Controller:
 * - Selects the highest-capability physical wide sensor via Camera2CameraInfo
 * - Enables hardware distortion correction, chromatic aberration correction, OIS, and HQ ISP modes
 * - Configures highest native sensor resolution capture (with optional RAW DNG sidecar)
 * - Streams real-time exposure/skin/dynamic-range telemetry from ImageAnalysis
 * - Captures motion-aware HDR/Night multi-frame bursts processed through [ComputationalPhotoEngine]
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentCapabilities: CameraHardwareCapabilities = CameraHardwareCapabilities()
    private var activeRawCaptureEnabled: Boolean = false

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val captureExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var lastThumbnailTimeMs = 0L

    @OptIn(ExperimentalCamera2Interop::class)
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        useFrontCamera: Boolean,
        captureMode: CameraCaptureMode = CameraCaptureMode.PHOTO,
        flashMode: CameraFlashMode,
        enableRawDngIfSupported: Boolean = false,
        useMaxResolution: Boolean = true,
        onThumbnailAndTelemetry: (Bitmap, SceneTelemetry) -> Unit,
        onZoomStateAvailable: (minZoom: Float, maxZoom: Float) -> Unit,
        onCapabilitiesAvailable: (CameraHardwareCapabilities) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val targetFacing = if (useFrontCamera) {
                        CameraCharacteristics.LENS_FACING_FRONT
                    } else {
                        CameraCharacteristics.LENS_FACING_BACK
                    }

                    val bestInfo = selectBestCameraInfo(provider.availableCameraInfos, targetFacing)
                    val selector = if (bestInfo != null) {
                        val targetId = Camera2CameraInfo.from(bestInfo).cameraId
                        CameraSelector.Builder()
                            .addCameraFilter(CameraFilter { infos ->
                                val matched = infos.filter {
                                    runCatching { Camera2CameraInfo.from(it).cameraId == targetId }.getOrDefault(false)
                                }
                                matched.ifEmpty { infos.take(1) }
                            })
                            .build()
                    } else if (useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }

                    val caps = inspectCapabilities(bestInfo)
                    currentCapabilities = caps
                    onCapabilitiesAvailable(caps)

                    val previewBuilder = Preview.Builder()
                    applyPreviewOpticalConfig(previewBuilder, bestInfo)
                    val preview = previewBuilder.build().apply {
                        setSurfaceProvider(surfaceProvider)
                    }

                    activeRecording?.stop()
                    activeRecording = null
                    provider.unbindAll()

                    val boundCamera = if (captureMode == CameraCaptureMode.VIDEO) {
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                            .build()
                        val vidCapture = VideoCapture.withOutput(recorder)
                        imageCapture = null
                        imageAnalysis = null
                        videoCapture = vidCapture
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            vidCapture
                        )
                    } else {
                        videoCapture = null
                        val captureFlashMode = when (flashMode) {
                            CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                            CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                            CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                        }

                        val resStrategy = if (useMaxResolution) {
                            ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                        } else {
                            ResolutionStrategy(
                                Size(4032, 3024),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        }

                        val captureBuilder = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(captureFlashMode)
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(resStrategy)
                                    .build()
                            )

                        val useRawDual = enableRawDngIfSupported && caps.supportsRawDng
                        activeRawCaptureEnabled = useRawDual
                        if (useRawDual) {
                            captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                        }
                        applyCaptureOpticalConfig(captureBuilder, bestInfo)
                        val capture = captureBuilder.build()

                        val analysisSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(320, 240),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()

                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(analysisSelector)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            if (now - lastThumbnailTimeMs >= 350L) {
                                lastThumbnailTimeMs = now
                                try {
                                    val thumb = extractSquareThumbnail(imageProxy, useFrontCamera, 84)
                                    val telemetry = ComputationalPhotoEngine.analyzeBitmapTelemetry(thumb)
                                    onThumbnailAndTelemetry(thumb, telemetry)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Thumbnail/telemetry sample failed", t)
                                }
                            }
                            imageProxy.close()
                        }

                        imageCapture = capture
                        imageAnalysis = analysis
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            capture,
                            analysis
                        )
                    }

                    camera = boundCamera
                    val zoomState = boundCamera.cameraInfo.zoomState.value
                    if (zoomState != null) {
                        onZoomStateAvailable(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Camera binding failed", t)
                    onError(t)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun selectBestCameraInfo(
        available: List<CameraInfo>,
        targetFacing: Int
    ): CameraInfo? {
        val candidates = available.filter { info ->
            runCatching {
                Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == targetFacing
            }.getOrDefault(false)
        }.ifEmpty { available }

        return candidates.maxByOrNull { info ->
            runCatching {
                val c2 = Camera2CameraInfo.from(info)
                val hwLevel = c2.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: 0
                val hwScore = when (hwLevel) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> 40
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> 30
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> 15
                    else -> 5
                }
                val arr = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val mpScore = if (arr != null) ((arr.width.toLong() * arr.height.toLong()) / 1_000_000L).toInt().coerceAtMost(200) else 0
                val oisModes = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                val oisScore = if (oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) 25 else 0
                val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val rawScore = if (caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true) 15 else 0
                hwScore + mpScore + oisScore + rawScore
            }.getOrDefault(0)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun inspectCapabilities(info: CameraInfo?): CameraHardwareCapabilities {
        if (info == null) return CameraHardwareCapabilities()
        return runCatching {
            val c2 = Camera2CameraInfo.from(info)
            val imageCaps = ImageCapture.getImageCaptureCapabilities(info)
            val supportedFormats = imageCaps.supportedOutputFormats

            val supportsRaw = supportedFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
            val supportsUltraHdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                supportedFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
            } else {
                false
            }

            val oisModes = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            val supportsOis = oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

            val aberrationModes = c2.getCameraCharacteristic(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
            val supportsAberration = aberrationModes?.contains(
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
            ) == true

            val supportsDistortion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val distModes = c2.getCameraCharacteristic(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
                distModes?.contains(CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY) == true
            } else {
                false
            }

            val arr = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val mp = if (arr != null) (arr.width.toLong() * arr.height.toLong()) / 1_000_000f else 0f

            val hwLevel = c2.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val hwLabel = when (hwLevel) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Pro Level 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full ISP"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited ISP"
                else -> "Standard"
            }

            val exposureState = info.exposureState
            val stepEv = exposureState.exposureCompensationStep.toFloat().coerceAtLeast(0.1f)
            val expCaps = if (exposureState.isExposureCompensationSupported) {
                ExposureCapabilities(
                    supported = true,
                    minEv = (exposureState.exposureCompensationRange.lower * stepEv).coerceAtLeast(-3f),
                    maxEv = (exposureState.exposureCompensationRange.upper * stepEv).coerceAtMost(3f),
                    stepEv = stepEv
                )
            } else {
                ExposureCapabilities(supported = false)
            }
            val supportsBracketing = exposureState.isExposureCompensationSupported &&
                exposureState.exposureCompensationRange.lower <= -2 &&
                exposureState.exposureCompensationRange.upper >= 2

            CameraHardwareCapabilities(
                supportsRawDng = supportsRaw,
                supportsUltraHdr = supportsUltraHdr,
                supportsOis = supportsOis,
                supportsDistortionCorrection = supportsDistortion,
                supportsAberrationCorrection = supportsAberration,
                supportsExposureBracketing = supportsBracketing,
                maxSensorMegapixels = mp,
                sensorHardwareLabel = hwLabel,
                exposureCapabilities = expCaps
            )
        }.getOrDefault(CameraHardwareCapabilities())
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyPreviewOpticalConfig(builder: Preview.Builder, info: CameraInfo?) {
        if (info == null) return
        runCatching {
            val c2 = Camera2CameraInfo.from(info)
            val ext = Camera2Interop.Extender(builder)

            val nrModes = c2.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            if (nrModes?.contains(CameraMetadata.NOISE_REDUCTION_MODE_FAST) == true) {
                ext.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            }

            val oisModes = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                ext.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val distModes = c2.getCameraCharacteristic(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
                if (distModes?.contains(CameraMetadata.DISTORTION_CORRECTION_MODE_FAST) == true) {
                    ext.setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE, CameraMetadata.DISTORTION_CORRECTION_MODE_FAST)
                }
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyCaptureOpticalConfig(builder: ImageCapture.Builder, info: CameraInfo?) {
        if (info == null) return
        runCatching {
            val c2 = Camera2CameraInfo.from(info)
            val ext = Camera2Interop.Extender(builder)

            val nrModes = c2.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            if (nrModes?.contains(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true) {
                ext.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            }

            val edgeModes = c2.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
            if (edgeModes?.contains(CameraMetadata.EDGE_MODE_HIGH_QUALITY) == true) {
                ext.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
            }

            val hotPixelModes = c2.getCameraCharacteristic(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
            if (hotPixelModes?.contains(CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY) == true) {
                ext.setCaptureRequestOption(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
            }

            val aberrationModes = c2.getCameraCharacteristic(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
            if (aberrationModes?.contains(CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY) == true) {
                ext.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
            }

            val oisModes = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                ext.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val distModes = c2.getCameraCharacteristic(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
                if (distModes?.contains(CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY) == true) {
                    ext.setCaptureRequestOption(
                        CaptureRequest.DISTORTION_CORRECTION_MODE,
                        CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY
                    )
                }
            }
        }
    }

    fun setFlashMode(flashMode: CameraFlashMode) {
        imageCapture?.flashMode = when (flashMode) {
            CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        val clamped = if (zoomState != null) {
            ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        } else {
            ratio
        }
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun setExposureCompensationEv(evStops: Float) {
        val cam = camera ?: return
        val expState = cam.cameraInfo.exposureState
        if (!expState.isExposureCompensationSupported) return
        val stepEv = expState.exposureCompensationStep.toFloat().coerceAtLeast(0.1f)
        val index = (evStops / stepEv).roundToInt().coerceIn(
            expState.exposureCompensationRange.lower,
            expState.exposureCompensationRange.upper
        )
        cam.cameraControl.setExposureCompensationIndex(index)
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        withAudio: Boolean,
        onRecordingStarted: () -> Unit,
        onVideoSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val vidCapture = videoCapture ?: run {
            onError("Video mode not ready")
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_${timestamp}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PhotoStyles")
            }
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = vidCapture.output.prepareRecording(context, options)
        if (withAudio) {
            pending = pending.withAudioEnabled()
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onRecordingStarted()
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (event.hasError()) {
                        onError("Recording failed (${event.error})")
                    } else {
                        onVideoSaved(event.outputResults.outputUri)
                    }
                }
            }
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun focusAtPoint(normalizedX: Float, normalizedY: Float, viewWidth: Float, viewHeight: Float) {
        val cam = camera ?: return
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
        val point = factory.createPoint(normalizedX * viewWidth, normalizedY * viewHeight)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /**
     * Stages 1 -> 2 -> 3 Full-Resolution Computational Capture:
     * Captures the native full-res frame (or an HDR/Night burst when Pro Max is enabled),
     * optionally writes a hardware RAW DNG file when enabled & supported, fuses burst frames
     * with motion-aware alignment, and applies base denoise + local Smart HDR conditioning.
     */
    suspend fun captureProMaxPhoto(
        isFrontCamera: Boolean,
        proMaxEnabled: Boolean,
        nightMode: NightModeSetting = NightModeSetting.AUTO,
        userExposureEv: Float = 0f,
        telemetry: SceneTelemetry
    ): CapturedMasterPhoto {
        val capture = imageCapture ?: throw IllegalStateException("Camera is not ready")

        // If RAW+JPEG dual capture is active on hardware, capture RAW DNG + anchor JPEG cleanly
        if (activeRawCaptureEnabled && currentCapabilities.supportsRawDng) {
            val (anchorBmp, dngFile) = captureRawAndJpegToFiles(capture, isFrontCamera)
            val measured = applyNightModeOverride(ComputationalPhotoEngine.analyzeBitmapTelemetry(anchorBmp), nightMode)
            val enhParams = measured.toEnhancementParams(proMaxEnabled)
            val enhanced = withContext(Dispatchers.Default) {
                if (enhParams.enabled) {
                    ComputationalPhotoEngine.enhanceBitmap(anchorBmp, enhParams).also {
                        if (it !== anchorBmp) anchorBmp.recycle()
                    }
                } else {
                    anchorBmp
                }
            }
            return CapturedMasterPhoto(
                enhancedMasterBitmap = enhanced,
                rawDngTempFile = dngFile,
                fusedFrameCount = 1,
                sceneTelemetry = measured
            )
        }

        val anchorBitmap = captureSingleFullResBitmap(capture, isFrontCamera)
        val rawMeasured = if (proMaxEnabled) {
            withContext(Dispatchers.Default) { ComputationalPhotoEngine.analyzeBitmapTelemetry(anchorBitmap) }
        } else {
            telemetry
        }
        val effectiveTelemetry = applyNightModeOverride(rawMeasured, nightMode)

        if (!proMaxEnabled) {
            return CapturedMasterPhoto(
                enhancedMasterBitmap = anchorBitmap,
                rawDngTempFile = null,
                fusedFrameCount = 1,
                sceneTelemetry = effectiveTelemetry
            )
        }

        // Capture auxiliary burst frames for HDR / Night fusion when hardware permits fast completion
        val burstFrames = mutableListOf(anchorBitmap)
        val cam = camera
        val expState = cam?.cameraInfo?.exposureState
        val canBracket = currentCapabilities.supportsExposureBracketing && expState != null

        if (effectiveTelemetry.isHighDynamicRange && canBracket && cam != null) {
            val stepEv = expState!!.exposureCompensationStep.toFloat().coerceAtLeast(0.1f)
            val baseIndex = (userExposureEv / stepEv).roundToInt().coerceIn(
                expState.exposureCompensationRange.lower,
                expState.exposureCompensationRange.upper
            )
            val evMinusIndex = (baseIndex + (-1.0f / stepEv).roundToInt()).coerceIn(expState.exposureCompensationRange.lower, baseIndex)
            val evPlusIndex = (baseIndex + (0.85f / stepEv).roundToInt()).coerceIn(baseIndex, expState.exposureCompensationRange.upper)

            try {
                if (evMinusIndex < baseIndex) {
                    withTimeoutOrNull(750L) {
                        awaitListenableFuture(cam.cameraControl.setExposureCompensationIndex(evMinusIndex))
                        delay(70L)
                        burstFrames.add(captureSingleFullResBitmap(capture, isFrontCamera))
                    }
                }
                if (evPlusIndex > baseIndex) {
                    withTimeoutOrNull(750L) {
                        awaitListenableFuture(cam.cameraControl.setExposureCompensationIndex(evPlusIndex))
                        delay(70L)
                        burstFrames.add(captureSingleFullResBitmap(capture, isFrontCamera))
                    }
                }
            } finally {
                runCatching { cam.cameraControl.setExposureCompensationIndex(baseIndex) }
            }
        } else if (effectiveTelemetry.isLowLight) {
            // 2 extra frames for Night temporal SNR boost with motion-aware ghost rejection
            withTimeoutOrNull(1100L) {
                repeat(2) {
                    burstFrames.add(captureSingleFullResBitmap(capture, isFrontCamera))
                }
            }
        }

        val enhParams = effectiveTelemetry.toEnhancementParams(proMaxEnabled = true)
        val finalMaster = withContext(Dispatchers.Default) {
            val fused = if (burstFrames.size > 1) {
                ComputationalPhotoEngine.fuseBurstBitmaps(burstFrames, effectiveTelemetry).also { fusedBmp ->
                    burstFrames.forEach { if (it !== fusedBmp) it.recycle() }
                }
            } else {
                anchorBitmap
            }
            ComputationalPhotoEngine.enhanceBitmap(fused, enhParams).also { enhanced ->
                if (enhanced !== fused) fused.recycle()
            }
        }

        return CapturedMasterPhoto(
            enhancedMasterBitmap = finalMaster,
            rawDngTempFile = null,
            fusedFrameCount = burstFrames.size,
            sceneTelemetry = effectiveTelemetry
        )
    }

    private fun applyNightModeOverride(telemetry: SceneTelemetry, nightMode: NightModeSetting): SceneTelemetry =
        when (nightMode) {
            NightModeSetting.OFF -> telemetry.copy(isLowLight = false)
            NightModeSetting.ON -> telemetry.copy(isLowLight = telemetry.meanLuma < 0.48f)
            NightModeSetting.AUTO -> telemetry
        }

    private suspend fun captureSingleFullResBitmap(
        capture: ImageCapture,
        isFrontCamera: Boolean
    ): Bitmap = suspendCancellableCoroutine { continuation ->
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = decodeFullResolutionImageProxy(image, isFrontCamera)
                        image.close()
                        if (continuation.isActive) {
                            continuation.resume(bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    } catch (t: Throwable) {
                        image.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }

    private suspend fun captureRawAndJpegToFiles(
        capture: ImageCapture,
        isFrontCamera: Boolean
    ): Pair<Bitmap, File?> = suspendCancellableCoroutine { continuation ->
        val cacheDir = File(context.cacheDir, "raw_capture").apply { mkdirs() }
        val jpegFile = File(cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        val dngFile = File(cacheDir, "cap_${System.currentTimeMillis()}.dng")

        val jpegOptions = ImageCapture.OutputFileOptions.Builder(jpegFile).build()
        val rawOptions = ImageCapture.OutputFileOptions.Builder(dngFile).build()

        capture.takePicture(
            rawOptions,
            jpegOptions,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val bytes = jpegFile.readBytes()
                        val bmp = decodeJpegBytesWithOrientation(bytes, fallbackRotation = 0, isFrontCamera = isFrontCamera)
                        jpegFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(bmp to dngFile.takeIf { it.exists() && it.length() > 0L })
                        } else {
                            bmp.recycle()
                        }
                    } catch (t: Throwable) {
                        jpegFile.delete()
                        dngFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(t)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    jpegFile.delete()
                    dngFile.delete()
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }
        )
    }

    fun release() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
    }

    private fun extractSquareThumbnail(
        imageProxy: ImageProxy,
        mirrorHorizontally: Boolean,
        targetSize: Int
    ): Bitmap {
        val raw = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (mirrorHorizontally) postScale(-1f, 1f)
        }
        val rotated = if (rotation != 0 || mirrorHorizontally) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                if (it !== raw) raw.recycle()
            }
        } else {
            raw
        }
        val side = min(rotated.width, rotated.height)
        val xOff = (rotated.width - side) / 2
        val yOff = (rotated.height - side) / 2
        val cropped = Bitmap.createBitmap(rotated, xOff, yOff, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped !== rotated) cropped.recycle()
        if (scaled !== rotated) rotated.recycle()
        return scaled
    }

    private fun decodeFullResolutionImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return decodeJpegBytesWithOrientation(
            bytes = bytes,
            fallbackRotation = imageProxy.imageInfo.rotationDegrees,
            isFrontCamera = isFrontCamera,
            fallbackBitmapProvider = { imageProxy.toBitmap() }
        )
    }

    private fun decodeJpegBytesWithOrientation(
        bytes: ByteArray,
        fallbackRotation: Int,
        isFrontCamera: Boolean,
        fallbackBitmapProvider: (() -> Bitmap)? = null
    ): Bitmap {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: fallbackBitmapProvider?.invoke()
            ?: error("Unable to decode captured JPEG buffer")

        val exifOrientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_UNDEFINED
        }

        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> {
                if (fallbackRotation != 0) matrix.postRotate(fallbackRotation.toFloat())
            }
        }
        if (isFrontCamera && exifOrientation != ExifInterface.ORIENTATION_FLIP_HORIZONTAL) {
            matrix.postScale(-1f, 1f)
        }

        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }

    private suspend fun <T> awaitListenableFuture(
        future: com.google.common.util.concurrent.ListenableFuture<T>
    ): T = suspendCancellableCoroutine { continuation ->
        future.addListener(
            {
                try {
                    if (continuation.isActive) {
                        continuation.resume(future.get())
                    }
                } catch (t: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(t)
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
