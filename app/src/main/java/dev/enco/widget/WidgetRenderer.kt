package dev.enco.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.min

enum class WidgetStyle {
    CIRCLES,
    WIDE,
    COMPACT,
    LIST,
}

internal object WidgetRenderer {
    private val providers = listOf(
        EncoWidgetProvider::class.java to WidgetStyle.CIRCLES,
        EncoWideWidget::class.java to WidgetStyle.WIDE,
        EncoCompactWidget::class.java to WidgetStyle.COMPACT,
        EncoListWidget::class.java to WidgetStyle.LIST,
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for ((provider, fallback) in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            for (id in ids) {
                manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id), fallback))
            }
        }
    }

    fun views(context: Context, options: Bundle, fallback: WidgetStyle = WidgetStyle.CIRCLES): RemoteViews {
        val reading = BatteryStore.read(context)
        val phone = PhoneBattery.read(context)
        val night = isNight(context)
        val glass = BatteryStore.glass(context)
        val text = if (night) 0xFFFFFFFF.toInt() else 0xFF1A1A1C.toInt()
        val muted = if (night) 0xFFB8B8C0.toInt() else 0xFF5C5C66.toInt()
        val (width, height) = span(context, options)
        val style = styleFor(width, height, fallback)
        val slots = ringSlots(reading, phone)
        val visible = slots.count { it.visible }
        val size = ringDp(style, width, height, visible.coerceAtLeast(1))
        return when (style) {
            WidgetStyle.LIST -> list(context, reading, night, glass, text, muted, size, width, height)
            WidgetStyle.COMPACT -> rings(context, reading, night, glass, text, muted, R.layout.widget_compact, size, width, height, slots)
            WidgetStyle.WIDE -> rings(context, reading, night, glass, text, muted, R.layout.widget_wide, size, width, height, slots)
            WidgetStyle.CIRCLES -> rings(context, reading, night, glass, text, muted, R.layout.widget_enco, size, width, height, slots)
        }
    }

    private fun rings(
        context: Context,
        reading: Reading,
        night: Boolean,
        glass: Boolean,
        text: Int,
        muted: Int,
        layout: Int,
        sizeDp: Float,
        widthDp: Int,
        heightDp: Int,
        slots: List<RingSlot>,
    ): RemoteViews {
        val plateWidth = if (widthDp > 0) widthDp else 288
        val plateHeight = if (heightDp > 0) heightDp else 110
        val plate = if (glass) GlassPlate.create(context, plateWidth, plateHeight, night) else null
        val columns = slots.count { it.visible }.coerceAtLeast(1)
        return base(context, layout, night, glass, plateWidth, plateHeight, plate).apply {
            var column = 0
            for (slot in slots) {
                setViewVisibility(
                    slot.slotId,
                    if (slot.visible) android.view.View.VISIBLE else android.view.View.GONE,
                )
                if (!slot.visible) continue
                val crop = if (plate != null && layout != R.layout.widget_compact) {
                    columnCrop(plate, column, columns, plateWidth, plateHeight, sizeDp)
                } else {
                    null
                }
                ring(context, this, slot.ringId, slot.percentId, reading, slot.level, slot.part, text, muted, sizeDp, glass, plate, crop)
                column += 1
            }
        }
    }

    private fun list(
        context: Context,
        reading: Reading,
        night: Boolean,
        glass: Boolean,
        text: Int,
        muted: Int,
        iconDp: Float,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val track = when {
            glass && night -> 0x66FFFFFF
            glass -> 0x33000000
            night -> 0xFF3A3A40.toInt()
            else -> 0xFFE6E6EB.toInt()
        }
        val phone = PhoneBattery.read(context)
        val rows = listOf(
            ListSlot(R.id.widget_phone_slot, R.id.widget_phone_icon, R.id.widget_phone_name, R.id.widget_phone_bar, R.id.widget_phone_percent, WidgetPart.PHONE, phone),
            ListSlot(R.id.widget_left_slot, R.id.widget_left_icon, R.id.widget_left_name, R.id.widget_left_bar, R.id.widget_left_percent, WidgetPart.LEFT, reading.left),
            ListSlot(R.id.widget_right_slot, R.id.widget_right_icon, R.id.widget_right_name, R.id.widget_right_bar, R.id.widget_right_percent, WidgetPart.RIGHT, reading.right),
            ListSlot(R.id.widget_case_slot, R.id.widget_case_icon, R.id.widget_case_name, R.id.widget_case_bar, R.id.widget_case_percent, WidgetPart.CASE, reading.caseLevel),
        )
        return base(context, R.layout.widget_list, night, glass, widthDp, heightDp).apply {
            for (slot in rows) {
                setViewVisibility(
                    slot.slotId,
                    if (slot.visible) android.view.View.VISIBLE else android.view.View.GONE,
                )
                if (!slot.visible) continue
                row(
                    context, this, slot.iconId, slot.nameId, slot.barId, slot.percentId,
                    reading, slot.level, slot.part, text, muted, track, iconDp, glass,
                )
            }
        }
    }

    private fun base(
        context: Context,
        layout: Int,
        night: Boolean,
        glass: Boolean,
        widthDp: Int,
        heightDp: Int,
        plate: Bitmap? = null,
    ): RemoteViews {
        return RemoteViews(context.packageName, layout).apply {
            setViewOutlinePreferredRadiusDimen(
                android.R.id.background,
                android.R.dimen.system_app_widget_background_radius,
            )
            setBoolean(android.R.id.background, "setClipToOutline", true)
            if (glass) {
                val frost = plate ?: GlassPlate.create(context, widthDp, heightDp, night)
                setInt(android.R.id.background, "setBackgroundColor", 0)
                setViewVisibility(R.id.widget_blur, android.view.View.VISIBLE)
                setImageViewBitmap(R.id.widget_blur, frost)
            } else {
                val background = if (night) R.drawable.widget_bg_dark else R.drawable.widget_bg_light
                setViewVisibility(R.id.widget_blur, android.view.View.GONE)
                setInt(android.R.id.background, "setBackgroundResource", background)
            }
            setOnClickPendingIntent(android.R.id.background, refresh(context))
        }
    }

    private fun ring(
        context: Context,
        views: RemoteViews,
        ringId: Int,
        percentId: Int,
        reading: Reading,
        level: Level?,
        part: WidgetPart,
        text: Int,
        muted: Int,
        sizeDp: Float,
        glass: Boolean,
        backdrop: Bitmap? = null,
        crop: Rect? = null,
    ) {
        views.setViewLayoutWidth(ringId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(ringId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setInt(ringId, "setBackgroundColor", 0)
        views.setImageViewBitmap(
            ringId,
            BatteryRing.create(context, level, part, sizeDp, glass = glass, backdrop = backdrop, crop = crop),
        )
        views.setTextViewText(percentId, ringLabel(context, reading, level, part))
        views.setTextColor(percentId, if (level == null && part != WidgetPart.PHONE) muted else text)
    }

    private fun row(
        context: Context,
        views: RemoteViews,
        iconId: Int,
        nameId: Int,
        barId: Int,
        percentId: Int,
        reading: Reading,
        level: Level?,
        part: WidgetPart,
        text: Int,
        muted: Int,
        track: Int,
        iconDp: Float,
        glass: Boolean,
    ) {
        views.setViewLayoutWidth(iconId, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(iconId, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setImageViewBitmap(iconId, BatteryRing.create(context, level, part, iconDp, ring = false, glass = glass))
        views.setTextColor(nameId, muted)
        views.setProgressBar(barId, 100, level?.percent ?: 0, false)
        views.setColorStateList(barId, "setProgressTintList", ColorStateList.valueOf(BatteryRing.progressColor(level)))
        views.setColorStateList(barId, "setProgressBackgroundTintList", ColorStateList.valueOf(track))
        views.setTextViewText(percentId, listLabel(context, reading, level, part))
        views.setTextColor(percentId, if (level == null && part != WidgetPart.PHONE) muted else text)
    }

    private fun ringLabel(context: Context, reading: Reading, level: Level?, part: WidgetPart): String {
        if (part == WidgetPart.PHONE) return level?.percent?.toString() ?: "—"
        return level?.percent?.toString() ?: "—"
    }

    private fun listLabel(context: Context, reading: Reading, level: Level?, part: WidgetPart): String {
        val number = ringLabel(context, reading, level, part)
        return if (level != null) "$number%" else number
    }

    private fun ringSlots(reading: Reading, phone: Level?): List<RingSlot> {
        return listOf(
            RingSlot(R.id.widget_phone_slot, R.id.widget_phone_ring, R.id.widget_phone_percent, WidgetPart.PHONE, phone),
            RingSlot(R.id.widget_left_slot, R.id.widget_left_ring, R.id.widget_left_percent, WidgetPart.LEFT, reading.left),
            RingSlot(R.id.widget_right_slot, R.id.widget_right_ring, R.id.widget_right_percent, WidgetPart.RIGHT, reading.right),
            RingSlot(R.id.widget_case_slot, R.id.widget_case_ring, R.id.widget_case_percent, WidgetPart.CASE, reading.caseLevel),
        )
    }

    private fun span(context: Context, options: Bundle): Pair<Int, Int> {
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        )
        val height = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        )
        return width to height
    }

    private fun ringDp(style: WidgetStyle, width: Int, height: Int, count: Int): Float {
        val columns = count.coerceAtLeast(1).toFloat()
        if (width <= 0 || height <= 0) {
            return when (style) {
                WidgetStyle.CIRCLES -> 72f
                WidgetStyle.WIDE -> 56f
                WidgetStyle.COMPACT -> 40f
                WidgetStyle.LIST -> 28f
            }
        }
        return when (style) {
            WidgetStyle.CIRCLES, WidgetStyle.WIDE -> min((width - 16f) / columns, height - 46f).coerceIn(32f, 140f)
            WidgetStyle.COMPACT -> min((height - 8f) / columns, width * 0.46f).coerceIn(28f, 80f)
            WidgetStyle.LIST -> ((height - 12f) / columns * 0.72f).coerceIn(22f, 48f)
        }
    }

    private fun styleFor(width: Int, height: Int, fallback: WidgetStyle): WidgetStyle {
        if (width <= 0 || height <= 0) return fallback
        return when {
            height >= 175 -> WidgetStyle.LIST
            width < 180 -> WidgetStyle.COMPACT
            height < 100 -> WidgetStyle.WIDE
            else -> WidgetStyle.CIRCLES
        }
    }

    private fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun columnCrop(
        plate: Bitmap,
        index: Int,
        columns: Int,
        widthDp: Int,
        heightDp: Int,
        sizeDp: Float,
    ): Rect {
        val pad = 4f
        val label = 18f
        val innerW = widthDp - pad * 2
        val innerH = heightDp - pad * 2
        val column = innerW / columns.coerceAtLeast(1).toFloat()
        val topDp = pad + (innerH - sizeDp - label) / 2f
        val leftDp = pad + index * column + (column - sizeDp) / 2f
        fun x(dp: Float) = (dp / widthDp * plate.width).toInt()
        fun y(dp: Float) = (dp / heightDp * plate.height).toInt()
        val left = x(leftDp).coerceIn(0, plate.width - 1)
        val top = y(topDp).coerceIn(0, plate.height - 1)
        val right = x(leftDp + sizeDp).coerceIn(left + 1, plate.width)
        val bottom = y(topDp + sizeDp).coerceIn(top + 1, plate.height)
        return Rect(left, top, right, bottom)
    }

    private fun refresh(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, EncoWidgetProvider::class.java).setAction(EncoWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class RingSlot(
        val slotId: Int,
        val ringId: Int,
        val percentId: Int,
        val part: WidgetPart,
        val level: Level?,
    ) {
        val visible: Boolean get() = level != null
    }

    private data class ListSlot(
        val slotId: Int,
        val iconId: Int,
        val nameId: Int,
        val barId: Int,
        val percentId: Int,
        val part: WidgetPart,
        val level: Level?,
    ) {
        val visible: Boolean get() = level != null
    }
}
