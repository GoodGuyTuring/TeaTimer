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
import app.teatimer.model.formatRemaining

object Notifications {
    /**
     * The active timer. Default importance (not low) so it shows on the lock screen, which
     * hides "silent" notifications by default; it still makes no sound or vibration.
     */
    private const val CHANNEL_TIMER = "timer"
    /** Replaced by [CHANNEL_TIMER] in 0.5.0: a channel's importance can't be raised later. */
    private const val OLD_CHANNEL_RUNNING = "running"
    private const val CHANNEL_DONE = "done"
    private const val ID_TIMER = 1
    const val ID_DONE = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_RUNNING)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Active timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The running or paused infusion, also on the lock screen"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            // Silent channel: AlertService plays the sound itself, for the chosen ring duration.
            NotificationChannel(CHANNEL_DONE, "Infusion done", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while the alarm rings"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /**
     * Ongoing notification for a running (live countdown) or paused infusion, with Pause /
     * Resume and Stop. Shown on the lock screen; on Android 16+ it asks to be a Live Update
     * (countdown chip in the status bar).
     */
    fun showTimer(context: Context, sequence: BrewingSequence, state: BrewState) {
        // Notification permission exists from Android 13; before that notifications are always allowed.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val running = state.phase == BrewState.Phase.RUNNING
        val step = sequence.steps[state.step]
        val infusion = buildString {
            append("Infusion ${state.step + 1} of ${sequence.steps.size}")
            step.temp?.let { append(" · $it") }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sequence.tea)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .setRequestPromotedOngoing(true)
        if (running) {
            val remainingMs = state.endsAt - SystemClock.elapsedRealtime()
            builder
                .setContentText("${sequence.detail} · $infusion")
                .setWhen(System.currentTimeMillis() + remainingMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, "Pause", timerAction(context, NotificationActionReceiver.ACTION_TOGGLE))
        } else {
            builder
                .setContentText("Paused · ${formatRemaining(state.remainingMs)} left · $infusion")
                .setShowWhen(false)
                .setShortCriticalText("Paused")
                .addAction(0, "Resume", timerAction(context, NotificationActionReceiver.ACTION_TOGGLE))
        }
        builder.addAction(0, "Stop", timerAction(context, NotificationActionReceiver.ACTION_STOP))
        NotificationManagerCompat.from(context).notify(ID_TIMER, builder.build())
    }

    fun cancelTimer(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_TIMER)
    }

    /** Notification shown while the alarm rings (AlertService's foreground notification). */
    fun done(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

    private fun timerAction(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, NotificationActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
