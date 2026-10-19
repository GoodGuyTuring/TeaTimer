package app.teatimer.brew

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.teatimer.MainActivity

/**
 * Schedules the end of the running infusion. Uses an alarm-clock alarm: it fires on
 * time even when the phone is idle (Doze), which plain timers and WorkManager don't.
 */
object AlarmScheduler {
    // USE_EXACT_ALARM (manifest) is granted at install; canScheduleExactAlarms() covers the rest.
    @SuppressLint("MissingPermission")
    fun schedule(context: Context, endsAtElapsed: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAtWall = System.currentTimeMillis() + (endsAtElapsed - SystemClock.elapsedRealtime())
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtWall, openAppIntent(context)),
                alarmIntent(context),
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsed, alarmIntent(context))
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
