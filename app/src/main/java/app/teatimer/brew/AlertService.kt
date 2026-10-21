package app.teatimer.brew

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import app.teatimer.model.RingDuration

/**
 * Rings and/or vibrates when an infusion is done: for a number of seconds, or until it's
 * stopped (the notification's Stop, a widget tap, or opening the app; at most 10 minutes).
 * Follows the ringer mode: sound + vibration, vibration only, or nothing on silent.
 */
class AlertService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopAlert() }
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlert()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        val ringSeconds = intent?.getIntExtra(EXTRA_RING_SECONDS, DEFAULT_RING_SECONDS) ?: DEFAULT_RING_SECONDS
        val untilStopped = ringSeconds == RingDuration.UNTIL_STOPPED
        val notification = Notifications.done(this, title, if (untilStopped) "$text Rings until you stop it." else text)
        val mayRunLong = startInForeground(notification, untilStopped)
        startAlert(
            when {
                !untilStopped -> ringSeconds * 1000L
                mayRunLong -> MAX_RING_MS
                else -> SHORT_SERVICE_RING_MS
            },
        )
        return START_NOT_STICKY
    }

    /**
     * Starts as a foreground service; returns whether it may run long. Ringing until stopped
     * uses the "system exempted" type, which Android allows for apps with exact-alarm
     * permission continuing an alarm. If it's refused, the short type ends it after ~3 min.
     */
    private fun startInForeground(notification: Notification, untilStopped: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 34) {
            ServiceCompat.startForeground(this, Notifications.ID_DONE, notification, 0)
            return true
        }
        if (untilStopped) {
            try {
                ServiceCompat.startForeground(
                    this,
                    Notifications.ID_DONE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
                return true
            } catch (_: RuntimeException) {
                // Not allowed on this phone: fall back to the short type below.
            }
        }
        ServiceCompat.startForeground(
            this,
            Notifications.ID_DONE,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
        )
        return false
    }

    // The short type's time limit (about 3 minutes) ran out.
    override fun onTimeout(startId: Int, fgsType: Int) = stopAlert()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int) = stopAlert()

    private fun startAlert(durationMs: Long) {
        isRinging = true
        stopSoundAndVibration()
        when (getSystemService(AudioManager::class.java).ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                playSound()
                vibrate()
            }
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> Unit
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeaTimer:alert")
            .apply { acquire(durationMs + 2_000) }
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, durationMs)
    }

    private fun playSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            // Notification usage: plays at notification volume and stays quiet on silent / Do Not Disturb.
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 400), 0)
        vibrator = getSystemService(VibratorManager::class.java).defaultVibrator.apply {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                vibrate(effect)
            }
        }
    }

    private fun stopSoundAndVibration() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAlert() {
        isRinging = false
        handler.removeCallbacks(stopRunnable)
        stopSoundAndVibration()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRinging = false
        handler.removeCallbacks(stopRunnable)
        stopSoundAndVibration()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.teatimer.action.STOP_ALERT"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_RING_SECONDS = "ring_seconds"
        private const val DEFAULT_RING_SECONDS = 5

        /** "Until stopped" still ends after 10 minutes, like the clock app's alarms. */
        private const val MAX_RING_MS = 10 * 60_000L
        /** Below the short service type's ~3 minute limit. */
        private const val SHORT_SERVICE_RING_MS = 150_000L

        /** Whether the alarm is ringing right now (in this process). */
        @Volatile
        var isRinging = false
            private set

        fun start(context: Context, alert: BrewController.Alert) {
            context.startForegroundService(
                Intent(context, AlertService::class.java)
                    .putExtra(EXTRA_TITLE, alert.title)
                    .putExtra(EXTRA_TEXT, alert.text)
                    .putExtra(EXTRA_RING_SECONDS, alert.ringSeconds),
            )
        }

        /**
         * Stops the alarm if it's ringing. Only then: while ringing, the service is in the
         * foreground, which is what allows starting it from the background with this intent.
         */
        fun stop(context: Context) {
            if (isRinging) runCatching { context.startService(stopIntent(context)) }
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, AlertService::class.java).setAction(ACTION_STOP)
    }
}
