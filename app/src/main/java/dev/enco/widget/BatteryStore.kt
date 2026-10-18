package dev.enco.widget

import android.content.Context
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class Reading(
    val left: Level?,
    val right: Level?,
    val caseLevel: Level?,
    val status: String,
    val known: Boolean = false,
) {
    val hasAny: Boolean get() = left != null || right != null || caseLevel != null

    fun summary(): String {
        return listOf(
            part("L", left),
            part("R", right),
            part("Case", caseLevel),
        ).joinToString("  ")
    }

    private fun part(label: String, level: Level?): String {
        return when {
            level != null -> "$label ${level.percent}%"
            known -> "$label off"
            else -> "$label —"
        }
    }
}

object BatteryStore {
    private const val PREFS = "enco_widget"
    private const val LEFT = "left"
    private const val RIGHT = "right"
    private const val CASE = "case"
    private const val STATUS = "status"
    private const val KNOWN = "known"
    private const val BACKGROUND = "background"
    private const val INTERVAL = "interval_seconds"
    private const val GLASS = "glass"
    val INTERVALS = intArrayOf(0, 60, 300, 900, 1800)

    fun read(context: Context): Reading {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Reading(
            left = level(prefs, LEFT),
            right = level(prefs, RIGHT),
            caseLevel = level(prefs, CASE),
            status = prefs.getString(STATUS, null) ?: context.getString(R.string.tap_refresh),
            known = prefs.getBoolean(KNOWN, false),
        )
    }

    fun save(context: Context, levels: Map<Int, Level>, status: String) {
        write(
            context,
            Reading(
                left = levels[EncoProtocol.LEFT],
                right = levels[EncoProtocol.RIGHT],
                caseLevel = levels[EncoProtocol.CASE],
                status = status,
                known = true,
            ),
        )
    }

    fun clear(context: Context, status: String) {
        write(context, Reading(null, null, null, status, known = true))
    }

    fun setStatus(context: Context, status: String) {
        val current = read(context)
        write(context, current.copy(status = status))
    }

    fun updateIntervalSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(INTERVAL) && prefs.getBoolean(BACKGROUND, false)) return 60
        val value = prefs.getInt(INTERVAL, 0)
        return if (value in INTERVALS) value else 0
    }

    fun setUpdateIntervalSeconds(context: Context, seconds: Int) {
        val stored = if (seconds in INTERVALS) seconds else 0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(INTERVAL, stored)
            .putBoolean(BACKGROUND, stored > 0)
            .apply()
    }

    fun glass(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(GLASS, false)
    }

    fun setGlass(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GLASS, enabled)
            .apply()
    }

    fun updatedNow(context: Context): String {
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.now())
        return context.getString(R.string.updated, time)
    }

    private fun write(context: Context, reading: Reading) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLevel(LEFT, reading.left)
            .putLevel(RIGHT, reading.right)
            .putLevel(CASE, reading.caseLevel)
            .putString(STATUS, reading.status)
            .putBoolean(KNOWN, reading.known)
            .apply()
    }

    private fun level(prefs: android.content.SharedPreferences, key: String): Level? {
        val percent = prefs.getInt(key, -1)
        if (percent < 0) return null
        return Level(percent, prefs.getBoolean("${key}_charging", false))
    }

    private fun android.content.SharedPreferences.Editor.putLevel(key: String, level: Level?) =
        if (level == null) {
            putInt(key, -1).remove("${key}_charging")
        } else {
            putInt(key, level.percent).putBoolean("${key}_charging", level.charging)
        }
}
