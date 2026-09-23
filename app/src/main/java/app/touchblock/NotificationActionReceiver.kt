package app.touchblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Кнопки «Пауза» и «Выключить» в уведомлении. Служба сама подхватывает изменения настроек. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        when (intent.action) {
            ACTION_TOGGLE_PAUSE -> prefs.paused = !prefs.paused
            ACTION_STOP -> prefs.enabled = false
        }
    }

    companion object {
        const val ACTION_TOGGLE_PAUSE = "app.touchblock.action.TOGGLE_PAUSE"
        const val ACTION_STOP = "app.touchblock.action.STOP"
    }
}
