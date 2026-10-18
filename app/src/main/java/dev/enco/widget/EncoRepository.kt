package dev.enco.widget

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.locks.ReentrantLock

object EncoRepository {
    private const val TAG = "Enco"
    private val lock = ReentrantLock()

    fun refresh(context: Context): Reading {
        val app = context.applicationContext
        if (!lock.tryLock()) return BatteryStore.read(app)
        try {
            val reading = readBuds(app)
            EncoWidgetProvider.render(app)
            EncoUpdateService.updateNotification(app, reading.summary())
            return reading
        } finally {
            lock.unlock()
        }
    }

    private fun readBuds(context: Context): Reading {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return status(context, R.string.permission_needed)
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            return status(context, R.string.bluetooth_off)
        }
        val device = adapter.bondedDevices.orEmpty().firstOrNull { it.matchesEnco() }
        if (device == null) {
            return status(context, R.string.no_device)
        }
        return try {
            val levels = EncoClient.query(device)
            val reading = Reading(
                left = levels[EncoProtocol.LEFT],
                right = levels[EncoProtocol.RIGHT],
                caseLevel = levels[EncoProtocol.CASE],
                status = BatteryStore.updatedNow(context),
                known = true,
            )
            BatteryStore.save(context, levels, reading.status)
            reading
        } catch (error: Exception) {
            Log.w(TAG, "refresh failed", error)
            val fallback = if (BatteryStore.read(context).hasAny) {
                R.string.could_not_refresh
            } else {
                R.string.no_answer
            }
            status(context, fallback)
        }
    }

    private fun status(context: Context, message: Int): Reading {
        BatteryStore.setStatus(context, context.getString(message))
        return BatteryStore.read(context)
    }
}

internal fun BluetoothDevice.matchesEnco(): Boolean {
    val label = name ?: return false
    return label.contains("enco", ignoreCase = true)
}
