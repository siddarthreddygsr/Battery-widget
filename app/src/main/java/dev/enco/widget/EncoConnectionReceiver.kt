package dev.enco.widget

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlin.concurrent.thread

class EncoConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = deviceExtra(intent) ?: return
        if (!runCatching { device.matchesEnco() }.getOrDefault(false)) return
        val app = context.applicationContext
        if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            BatteryStore.clear(app, app.getString(R.string.not_connected))
            EncoWidgetProvider.render(app)
            EncoUpdateService.updateNotification(app, BatteryStore.read(app).summary())
            return
        }
        val pending = goAsync()
        thread(name = "enco-connect", isDaemon = true) {
            try {
                Thread.sleep(1_500)
                EncoRepository.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }

    private fun deviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }
}
