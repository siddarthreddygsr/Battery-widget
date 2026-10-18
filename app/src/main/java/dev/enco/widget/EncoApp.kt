package dev.enco.widget

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class EncoApp : Application() {
    private val batteryChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            EncoWidgetProvider.render(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(batteryChanges, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryChanges, filter)
        }
    }
}
