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
    private var soundUri: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 120)
                flashEnabled = intent.getBooleanExtra(EXTRA_FLASH, false)
                soundUri = intent.getStringExtra(EXTRA_SOUND_URI)
                startTimer(seconds)
            }
            ACTION_ADD -> adjustTimer(15)
            ACTION_SUB -> adjustTimer(-15)
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(totalSeconds: Int) {
        tickJob?.cancel()
        _state.value = TimerState(
            remainingSec = totalSeconds,
            totalSec = totalSeconds,
            running = true,
            paused = false
        )
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(totalSeconds),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
        )

        // Tick czyta remainingSec ze _state.value zamiast lokalnej kopii,
        // dzięki czemu adjustTimer/pause działają od razu, a tick nie nadpisuje
        // wartości po 1 sekundzie.
        tickJob = scope.launch {
            while (isActive && _state.value.remainingSec > 0) {
                delay(1000)
                val s = _state.value
                if (!s.running || s.paused) continue
                val newRemaining = (s.remainingSec - 1).coerceAtLeast(0)
                _state.value = s.copy(remainingSec = newRemaining)
                updateNotification(newRemaining)
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
        // Total rośnie przy +, nie maleje przy − — bo procent progressu byłby
        // mylący ("dolałem do bieżącej przerwy" vs "przedłużyłem ją").
        val newTotal = if (delta > 0) s.totalSec + delta else s.totalSec
        _state.value = s.copy(
            remainingSec = newRemaining,
            totalSec = newTotal.coerceAtLeast(newRemaining)
        )
        updateNotification(newRemaining)
    }

    private fun pauseTimer() {
        val s = _state.value
        if (!s.running || s.paused) return
        _state.value = s.copy(paused = true)
        updateNotification(s.remainingSec)
    }

    private fun resumeTimer() {
        val s = _state.value
        if (!s.running || !s.paused) return
        _state.value = s.copy(paused = false)
        updateNotification(s.remainingSec)
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
        // v2.10.0: jeśli user wybrał własny dźwięk (URI) — odtwórz go. Inaczej
        // domyślna sekwencja beepów. Wszystko na wyjściu MEDIA (USAGE_MEDIA /
        // STREAM_MUSIC), żeby dźwięk szedł tam gdzie muzyka — słuchawki BT gdy
        // podłączone, inaczej głośnik (a NIE duplikowany na głośnik jak alarm).
        val customUri = soundUri
        if (!customUri.isNullOrBlank()) {
            playCustomSound(customUri)
            return
        }
        playDefaultBeeps()
    }

    private fun playDefaultBeeps() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
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

    private fun playCustomSound(uriStr: String) {
        try {
            mp?.runCatching { stop() }
            mp?.release()
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RestTimerService, android.net.Uri.parse(uriStr))
                setOnCompletionListener { it.release(); if (mp === it) mp = null }
                setOnErrorListener { p, _, _ -> p.release(); if (mp === p) mp = null; playDefaultBeeps(); true }
                prepare()
                start()
            }
        } catch (e: Throwable) {
            Log.e("RestTimerService", "Custom sound failed ($uriStr), default beeps", e)
            playDefaultBeeps()
        }
    }

    private fun playRingtoneFallback() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(this, uri)?.also { rt ->
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
        val running: Boolean = false,
        val paused: Boolean = false
    )

    companion object {
        const val CHANNEL_ID = "rest_timer_channel"
        const val NOTIF_ID = 1001

        const val ACTION_START = "pl.filebit.gymtracker.timer.START"
        const val ACTION_STOP = "pl.filebit.gymtracker.timer.STOP"
        const val ACTION_ADD = "pl.filebit.gymtracker.timer.ADD"
        const val ACTION_SUB = "pl.filebit.gymtracker.timer.SUB"
        const val ACTION_PAUSE = "pl.filebit.gymtracker.timer.PAUSE"
        const val ACTION_RESUME = "pl.filebit.gymtracker.timer.RESUME"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_FLASH = "extra_flash"
        const val EXTRA_SOUND_URI = "extra_sound_uri"

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun start(context: Context, seconds: Int, flash: Boolean = false, soundUri: String? = null) {
            val i = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_FLASH, flash)
                .putExtra(EXTRA_SOUND_URI, soundUri)
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

        fun togglePause(context: Context, paused: Boolean) {
            val i = Intent(context, RestTimerService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE)
            context.startService(i)
        }
    }
}
