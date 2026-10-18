package dev.enco.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.core.content.ContextCompat
import kotlin.math.max

internal enum class WidgetPart {
    PHONE,
    LEFT,
    RIGHT,
    CASE,
}

internal object BatteryRing {
    private const val GREEN = 0xFF3DDE6C.toInt()
    private const val RED = 0xFFFF4D4F.toInt()
    private const val LOW_PERCENT = 15

    fun create(
        context: Context,
        level: Level?,
        part: WidgetPart,
        sizeDp: Float,
        ring: Boolean = true,
        glass: Boolean = false,
    ): Bitmap {
        val night = isNight(context)
        val size = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val iconColor = if (night) 0xFFF5F5F7.toInt() else 0xFF1A1A1C.toInt()
        val box = when {
            part == WidgetPart.PHONE && ring -> size * 0.40f
            part == WidgetPart.PHONE -> size * 0.72f
            !ring -> size * 0.92f
            part == WidgetPart.CASE -> size * 0.74f
            else -> size * 0.64f
        }
        if (ring) {
            val track = when {
                glass && night -> 0x66FFFFFF
                glass -> 0x40000000
                night -> 0xFF3A3A40.toInt()
                else -> 0xFFE6E6EB.toInt()
            }
            val badge = when {
                glass && night -> 0xCC1A1A1E.toInt()
                glass -> 0xE6FFFFFF.toInt()
                night -> 0xFF111113.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            val stroke = size * 0.07f
            val inset = stroke / 2f + size * 0.03f
            val oval = RectF(inset, inset, size - inset, size - inset)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = track
            canvas.drawArc(oval, 0f, 360f, false, paint)
            val percent = level?.percent?.coerceIn(0, 100)
            if (percent != null && percent > 0) {
                paint.color = if (percent <= LOW_PERCENT) RED else GREEN
                canvas.drawArc(oval, -90f, percent * 3.6f, false, paint)
            }
            drawMark(context, canvas, size / 2f, size / 2f, box, part, iconColor)
            if (level?.charging == true) {
                drawBolt(canvas, size / 2f, size - inset, size * 0.15f, paint, badge, iconColor)
            }
        } else {
            drawMark(context, canvas, size / 2f, size / 2f, box, part, iconColor)
        }
        return bitmap
    }

    fun progressColor(level: Level?): Int {
        val percent = level?.percent ?: return GREEN
        return if (percent <= LOW_PERCENT) RED else GREEN
    }

    private fun artwork(part: WidgetPart): Int {
        return when (part) {
            WidgetPart.LEFT -> R.drawable.ic_bud_left
            WidgetPart.RIGHT -> R.drawable.ic_bud_right
            WidgetPart.CASE -> R.drawable.ic_case
            WidgetPart.PHONE -> 0
        }
    }

    private fun drawMark(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        box: Float,
        part: WidgetPart,
        color: Int,
    ) {
        if (part == WidgetPart.PHONE) {
            drawPhoneGlyph(canvas, cx, cy, box, color)
            return
        }
        val drawable = ContextCompat.getDrawable(context, artwork(part))?.mutate() ?: return
        val width = drawable.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val height = drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val scale = box / max(width, height)
        val drawnWidth = width * scale
        val drawnHeight = height * scale
        val left = cx - drawnWidth / 2f
        val top = cy - drawnHeight / 2f
        drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        drawable.setBounds(left.toInt(), top.toInt(), (left + drawnWidth).toInt(), (top + drawnHeight).toInt())
        drawable.draw(canvas)
    }

    private fun drawPhoneGlyph(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        val bodyWidth = size * 0.62f
        val bodyHeight = size * 0.86f
        val left = cx - bodyWidth / 2f
        val top = cy - bodyHeight / 2f + size * 0.06f
        val radius = size * 0.1f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.09f
        canvas.drawRoundRect(RectF(left, top, left + bodyWidth, top + bodyHeight), radius, radius, paint)
        paint.style = Paint.Style.FILL
        val nubWidth = bodyWidth * 0.36f
        val nubHeight = size * 0.08f
        canvas.drawRoundRect(
            RectF(cx - nubWidth / 2f, top - nubHeight, cx + nubWidth / 2f, top + nubHeight * 0.15f),
            nubHeight / 2f,
            nubHeight / 2f,
            paint,
        )
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint, badge: Int, icon: Int) {
        paint.style = Paint.Style.FILL
        paint.color = badge
        canvas.drawCircle(cx, cy, size * 0.78f, paint)
        paint.color = icon
        val path = Path().apply {
            moveTo(cx + size * 0.08f, cy - size * 0.42f)
            lineTo(cx - size * 0.32f, cy + size * 0.02f)
            lineTo(cx - size * 0.04f, cy + size * 0.02f)
            lineTo(cx - size * 0.12f, cy + size * 0.42f)
            lineTo(cx + size * 0.36f, cy - size * 0.06f)
            lineTo(cx + size * 0.06f, cy - size * 0.06f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}
