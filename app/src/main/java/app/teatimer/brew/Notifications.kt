package app.teatimer.brew

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.teatimer.MainActivity
import app.teatimer.R
import app.teatimer.model.BrewState
import app.teatimer.model.BrewingSequence

object Notifications {
    private const val CHANNEL_RUNNING = "running"
    private const val CHANNEL_DONE = "done"
    private const val ID_RUNNING = 1
    const val ID_DONE = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown of the current infusion"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            // Silent channel: AlertService plays the sound itself, for the chosen ring duration.
            NotificationChannel(CHANNEL_DONE, "Infusion done", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while the alarm rings"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** Ongoing notification with a live countdown, e.g. on the lock screen. */
    fun showRunning(context: Context, sequence: BrewingSequence, state: BrewState) {
        // Notification permission exists from Android 13; before that notifications are always allowed.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val remainingMs = state.endsAt - SystemClock.elapsedRealtime()
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sequence.name)
            .setContentText("Infusion ${state.step + 1} of ${sequence.steepSeconds.size}")
            .setWhen(System.currentTimeMillis() + remainingMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_RUNNING, notification)
    }

    fun cancelRunning(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
    }

    /** Notification shown while the alarm rings (AlertService's foreground notification). */
    fun done(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context))
            .addAction(
                0,
                "Stop",
                PendingIntent.getService(
                    context,
                    0,
                    AlertService.stopIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
