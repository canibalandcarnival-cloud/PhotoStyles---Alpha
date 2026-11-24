package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import com.example.R

enum class CalibrationSceneId(
    val title: String,
    val subtitle: String,
    val tags: List<String>,
    @param:DrawableRes val drawableRes: Int?
) {
    PORTRAIT_INDOOR(
        title = "Portrait & Skin",
        subtitle = "Human skin tones · Soft indoor window light",
        tags = listOf("Skin Tones", "Indoor"),
        drawableRes = R.drawable.img_scene_portrait
    ),
    LANDSCAPE_DAYLIGHT(
        title = "Highlands",
        subtitle = "Outdoor daylight · Blue sky · Green foliage",
        tags = listOf("Daylight", "Blue Sky", "Foliage"),
        drawableRes = R.drawable.img_scene_landscape
    ),
    LOW_LIGHT_CAFE(
        title = "Twilight Cafe",
        subtitle = "Low-light tungsten · Deep evening shadows",
        tags = listOf("Low-Light", "Shadows"),
        drawableRes = R.drawable.img_scene_lowlight
    ),
    HIGH_CONTRAST_ARCH(
        title = "Sunlit Arch",
        subtitle = "Direct sun highlights · Deep arch shadows",
        tags = listOf("High-Contrast", "Highlights"),
        drawableRes = R.drawable.img_scene_highcontrast
    ),
    CUSTOM_IMPORTED(
        title = "Imported",
        subtitle = "Custom photo selected from library",
        tags = listOf("Custom"),
        drawableRes = null
    )
}

object SceneBitmapLoader {

    fun loadScaledScene(context: Context, drawableRes: Int, maxEdge: Int = 960): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, drawableRes, opts)
        val sampleSize = computeSampleSize(opts.outWidth, opts.outHeight, maxEdge)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeResource(context.resources, drawableRes, decodeOpts)
        return scaleDownIfNeeded(decoded, maxEdge)
    }

    fun loadScaledUri(context: Context, uri: Uri, maxEdge: Int = 1080): Bitmap? {
        val resolver = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        return scaleDownIfNeeded(decoded, maxEdge)
    }

    private fun computeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val largest = maxOf(width, height)
        while (largest / sample > maxEdge * 2) {
            sample *= 2
        }
        return sample
    }

    fun scaleDownIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxEdge) {
            return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxEdge.toFloat() / largest.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
