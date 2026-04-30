package pl.filebit.gymtracker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.MainActivity
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.util.formatTimerSeconds

/**
 * Foreground service który odlicza odpoczynek między seriami.
 * Singleton — w aplikacji jest jeden timer naraz.
 *
 * Komunikacja z ViewModel poprzez statyczny StateFlow (kopia stanu).
 * To proste i wystarczy dla hobby — w produkcji użyłbym bound service'a.
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var mp: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var flashEnabled: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 120)
                flashEnabled = intent.getBooleanExtra(EXTRA_FLASH, false)
                startTimer(seconds)
            }
            ACTION_ADD -> adjustTimer(15)
            ACTION_SUB -> adjustTimer(-15)
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(totalSeconds: Int) {
        tickJob?.cancel()
        _state.value = TimerState(remainingSec = totalSeconds, totalSec = totalSeconds, running = true)
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(totalSeconds),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
        )

        tickJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.value = _state.value.copy(remainingSec = remaining)
                updateNotification(remaining)
            }
            if (isActive) {
                _state.value = _state.value.copy(remainingSec = 0, running = false)
                playDoneSound()
                vibrateDone()
                if (flashEnabled) {
                    scope.launch { blinkFlash() }
                }
                // Daj dźwiękowi czas wybrzmieć — bez tego stopSelf() zabija audio.
                delay(2500)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun adjustTimer(delta: Int) {
        val s = _state.value
        if (!s.running) return
        val newRemaining = (s.remainingSec + delta).coerceAtLeast(1)
        val newTotal = (s.totalSec + delta).coerceAtLeast(newRemaining)
        _state.value = s.copy(remainingSec = newRemaining, totalSec = newTotal)
        updateNotification(newRemaining)
    }

    private fun stopTimer() {
        tickJob?.cancel()
        _state.value = TimerState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(remainingSec: Int): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RestTimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.timer_rest))
            .setContentText(formatTimerSeconds(remainingSec))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.timer_skip), stopIntent)
            .build()
    }

    private fun updateNotification(remainingSec: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(remainingSec))
    }

    private fun playDoneSound() {
        // Sekwencja 3 wysokich tonów na strumieniu ALARM — głośna, dynamiczna,
        // niezależna od systemowego dźwięku powiadomień.
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            scope.launch {
                try {
                    tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 200)
                    delay(280)
                    tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 200)
                    delay(280)
                    tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500)
                    delay(600)
                } finally {
                    tg.release()
                }
            }
        } catch (e: Throwable) {
            Log.e("RestTimerService", "ToneGenerator failed, falling back to Ringtone", e)
            playRingtoneFallback()
        }
    }

    private fun playRingtoneFallback() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(this, uri)?.also { rt ->
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                rt.play()
            }
        } catch (e: Throwable) {
            Log.e("RestTimerService", "Ringtone fallback failed", e)
        }
    }

    private suspend fun blinkFlash() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val ch = cm.getCameraCharacteristics(id)
                ch.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            repeat(3) {
                runCatching { cm.setTorchMode(cameraId, true) }
                delay(150)
                runCatching { cm.setTorchMode(cameraId, false) }
                delay(150)
            }
        } catch (e: Throwable) {
            Log.w("RestTimerService", "Flash blink failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateDone() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
        } else {
            vib.vibrate(longArrayOf(0, 250, 150, 250), -1)
        }
    }

    override fun onDestroy() {
        mp?.release()
        mp = null
        ringtone?.stop()
        ringtone = null
        scope.cancel()
        super.onDestroy()
    }

    data class TimerState(
        val remainingSec: Int = 0,
        val totalSec: Int = 0,
        val running: Boolean = false
    )

    companion object {
        const val CHANNEL_ID = "rest_timer_channel"
        const val NOTIF_ID = 1001

        const val ACTION_START = "pl.filebit.gymtracker.timer.START"
        const val ACTION_STOP = "pl.filebit.gymtracker.timer.STOP"
        const val ACTION_ADD = "pl.filebit.gymtracker.timer.ADD"
        const val ACTION_SUB = "pl.filebit.gymtracker.timer.SUB"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_FLASH = "extra_flash"

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun start(context: Context, seconds: Int, flash: Boolean = false) {
            val i = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_FLASH, flash)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, RestTimerService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun addSeconds(context: Context, delta: Int) {
            val i = Intent(context, RestTimerService::class.java)
                .setAction(if (delta > 0) ACTION_ADD else ACTION_SUB)
            context.startService(i)
        }
    }
}
