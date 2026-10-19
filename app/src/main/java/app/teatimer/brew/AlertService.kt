package app.teatimer.brew

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

/**
 * Rings and/or vibrates for the chosen number of seconds when an infusion is done.
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
        ServiceCompat.startForeground(
            this,
            Notifications.ID_DONE,
            Notifications.done(this, title, text),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0,
        )
        startAlert(BrewController.get(this).ringSeconds.value)
        return START_NOT_STICKY
    }

    private fun startAlert(seconds: Int) {
        stopSoundAndVibration()
        when (getSystemService(AudioManager::class.java).ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                playSound()
                vibrate()
            }
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> Unit
        }
        val durationMs = seconds * 1000L
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
        handler.removeCallbacks(stopRunnable)
        stopSoundAndVibration()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        stopSoundAndVibration()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.teatimer.action.STOP_ALERT"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, alert: BrewController.Alert) {
            context.startForegroundService(
                Intent(context, AlertService::class.java)
                    .putExtra(EXTRA_TITLE, alert.title)
                    .putExtra(EXTRA_TEXT, alert.text),
            )
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, AlertService::class.java).setAction(ACTION_STOP)
    }
}
