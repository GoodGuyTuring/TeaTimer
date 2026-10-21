package app.teatimer.brew

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.teatimer.widget.redrawWidgetsNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when an infusion's time is up: advances the brew, redraws the widget, starts the alert. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                BrewController.get(context).onTimerFinished()?.let { alert ->
                    AlertService.start(context, alert)
                    // A widget problem must never break the alarm, so this comes after and can't throw.
                    runCatching { redrawWidgetsNow(context) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
