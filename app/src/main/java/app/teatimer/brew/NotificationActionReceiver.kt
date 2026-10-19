package app.teatimer.brew

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The timer notification's buttons: Pause / Resume and Stop, usable from the lock screen. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val controller = BrewController.get(context)
                when (intent.action) {
                    ACTION_TOGGLE -> controller.toggle()
                    ACTION_STOP -> controller.stop()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "app.teatimer.action.TOGGLE_TIMER"
        const val ACTION_STOP = "app.teatimer.action.STOP_TIMER"
    }
}
