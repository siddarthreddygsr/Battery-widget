package dev.enco.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import kotlin.math.max

internal object GlassPlate {
    private const val TAG = "Enco"
    private var cacheKey = 0
    private var cache: Bitmap? = null

    fun create(context: Context, widthDp: Int, heightDp: Int, night: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt().coerceIn(48, 1400)
        val height = (heightDp * density).toInt().coerceIn(48, 1400)
        val wallpaperId = runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrDefault(-1)
        val key = wallpaperId * 31 + width * 17 + height * 13 + if (night) 1 else 0
        cache?.let { if (key == cacheKey && !it.isRecycled) return it }
        val plate = draw(context, width, height, density, night)
        cache = plate
        cacheKey = key
        return plate
    }

    private fun draw(context: Context, width: Int, height: Int, density: Float, night: Boolean): Bitmap {
        val plate = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(plate)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = 28f * density
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val wallpaper = blurredWallpaper(context, width, height)
        if (wallpaper != null) {
            val shaderScaleX = width.toFloat() / wallpaper.width
            val shaderScaleY = height.toFloat() / wallpaper.height
            canvas.save()
            canvas.scale(shaderScaleX, shaderScaleY)
            canvas.drawBitmap(wallpaper, 0f, 0f, paint)
            canvas.restore()
            wallpaper.recycle()
        } else {
            paint.color = if (night) 0xCC1A1C22.toInt() else 0xCCF4F4F6.toInt()
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
        paint.color = if (night) 0x33000000 else 0x38FFFFFF
        canvas.drawRect(bounds, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = if (night) 0x66FFFFFF else 0xCCFFFFFF.toInt()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val rounded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val mask = Canvas(rounded)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG)
        clear.color = 0xFFFFFFFF.toInt()
        mask.drawRoundRect(bounds, radius, radius, clear)
        clear.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        mask.drawBitmap(plate, 0f, 0f, clear)
        plate.recycle()
        return rounded
    }

    private fun blurredWallpaper(context: Context, width: Int, height: Int): Bitmap? {
        val source = loadWallpaper(context) ?: return null
        val sampleWidth = 96
        val sampleHeight = max(28, sampleWidth * height / width)
        val sample = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        val scale = max(sampleWidth.toFloat() / source.width, sampleHeight.toFloat() / source.height)
        val drawnWidth = source.width * scale
        val drawnHeight = source.height * scale
        val left = ((sampleWidth - drawnWidth) / 2f).toInt()
        val top = ((sampleHeight - drawnHeight) / 2f).toInt()
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(left, top, left + drawnWidth.toInt(), top + drawnHeight.toInt()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val pixel = sample.getPixel(sampleWidth / 2, sampleHeight / 2)
        if (pixel ushr 24 < 200) {
            Log.w(TAG, "wallpaper sample was transparent")
            sample.recycle()
            return null
        }
        return boxBlur(sample, 10)
    }

    private fun loadWallpaper(context: Context): Bitmap? {
        val manager = WallpaperManager.getInstance(context)
        val fromFile = runCatching {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { descriptor ->
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
            }
        }.onFailure { Log.w(TAG, "wallpaper file failed", it) }.getOrNull()
        val raw = fromFile ?: runCatching {
            val drawable = manager.drawable ?: return@runCatching null
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val sourceWidth = drawable.intrinsicWidth.coerceIn(1, 1600)
                val sourceHeight = drawable.intrinsicHeight.coerceIn(1, 1600)
                val bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, sourceWidth, sourceHeight)
                drawable.draw(canvas)
                bitmap
            }
        }.onFailure { Log.w(TAG, "wallpaper drawable failed", it) }.getOrNull()
        if (raw == null) {
            Log.w(TAG, "no wallpaper bitmap")
            return null
        }
        Log.i(TAG, "wallpaper ${raw.width}x${raw.height}")
        if (raw.config == Bitmap.Config.ARGB_8888) return raw
        val copy = raw.copy(Bitmap.Config.ARGB_8888, false)
        if (copy !== raw) raw.recycle()
        return copy
    }

    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val next = IntArray(pixels.size)
        blur(pixels, next, width, height, radius, horizontal = true)
        blur(next, pixels, width, height, radius, horizontal = false)
        blur(pixels, next, width, height, radius, horizontal = true)
        blur(next, pixels, width, height, radius, horizontal = false)
        source.setPixels(pixels, 0, width, 0, 0, width, height)
        return source
    }

    private fun blur(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val span = if (horizontal) width else height
        val passes = if (horizontal) height else width
        for (line in 0 until passes) {
            var red = 0
            var green = 0
            var blue = 0
            var alpha = 0
            for (i in -radius..radius) {
                val color = input[index(width, line, i.coerceIn(0, span - 1), horizontal)]
                alpha += color ushr 24
                red += color shr 16 and 0xFF
                green += color shr 8 and 0xFF
                blue += color and 0xFF
            }
            val window = radius * 2 + 1
            for (i in 0 until span) {
                output[index(width, line, i, horizontal)] =
                    (alpha / window shl 24) or (red / window shl 16) or (green / window shl 8) or (blue / window)
                val remove = input[index(width, line, (i - radius).coerceIn(0, span - 1), horizontal)]
                val add = input[index(width, line, (i + radius + 1).coerceIn(0, span - 1), horizontal)]
                alpha += (add ushr 24) - (remove ushr 24)
                red += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                green += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private fun index(width: Int, line: Int, position: Int, horizontal: Boolean): Int {
        return if (horizontal) line * width + position else position * width + line
    }
}
