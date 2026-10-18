package dev.enco.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlin.concurrent.thread

open class EncoWidgetProvider : AppWidgetProvider() {
    protected open val defaultStyle: WidgetStyle = WidgetStyle.CIRCLES
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            thread(name = "enco-refresh", isDaemon = true) {
                try {
                    EncoRepository.refresh(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRenderer.updateAll(context)
        if (BatteryStore.updateIntervalSeconds(context) > 0) {
            onReceive(context, Intent(ACTION_REFRESH).setPackage(context.packageName))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.views(context, newOptions, defaultStyle))
    }

    companion object {
        const val ACTION_REFRESH = "dev.enco.widget.REFRESH"

        fun render(context: Context) {
            WidgetRenderer.updateAll(context)
        }
    }
}

class EncoWideWidget : EncoWidgetProvider() {
    override val defaultStyle = WidgetStyle.WIDE
}

class EncoCompactWidget : EncoWidgetProvider() {
    override val defaultStyle = WidgetStyle.COMPACT
}

class EncoListWidget : EncoWidgetProvider() {
    override val defaultStyle = WidgetStyle.LIST
}
