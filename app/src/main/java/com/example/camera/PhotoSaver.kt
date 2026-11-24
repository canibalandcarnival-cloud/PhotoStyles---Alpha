package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.colorscience.GlOffscreenRenderer
import com.example.colorscience.StyleParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stage 6 Photo Export:
 * Renders Stages 4 & 5 (active Photographic Style + 2D Palette) onto the Stage 2/3
 * computationally enhanced full-resolution master bitmap and persists high-quality JPEG
 * (quality 97) plus optional hardware DNG RAW sidecar to MediaStore.
 */
object PhotoSaver {

    suspend fun saveStyledPhoto(
        context: Context,
        originalBitmap: Bitmap,
        parameters: StyleParameters,
        rawDngTempFile: File? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val styledBitmap = GlOffscreenRenderer.renderFullResolution(originalBitmap, parameters)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val styleTag = parameters.style.id.uppercase(Locale.US)
            val displayName = "IMG_${timestamp}_${styleTag}.jpg"

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/PhotoStyles"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to allocate MediaStore entry")

            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    check(styledBitmap.compress(Bitmap.CompressFormat.JPEG, 97, stream)) {
                        "JPEG compression failed"
                    }
                } ?: error("Could not open MediaStore output stream")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, doneValues, null, null)
                }
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            } finally {
                if (styledBitmap !== originalBitmap) {
                    styledBitmap.recycle()
                }
            }

            if (rawDngTempFile != null && rawDngTempFile.exists()) {
                runCatching {
                    saveRawDngSidecar(context, rawDngTempFile, "IMG_${timestamp}_${styleTag}.dng")
                }.also {
                    rawDngTempFile.delete()
                }
            }

            uri
        }
    }

    private fun saveRawDngSidecar(context: Context, dngFile: File, displayName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/PhotoStyles/RAW"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        try {
            resolver.openOutputStream(uri)?.use { out ->
                dngFile.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
        }
    }
}
