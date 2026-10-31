package com.yasmin.ai.wake

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Lets the UI observe the wake word service. */
object WakeBus {
    /** Emits once each time "Hey Yasmin" is heard. */
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Human-readable status for the UI. */
    val status = MutableStateFlow("Off")
}

/**
 * Optional "Hey Yasmin" listener. Runs ONLY while the user has it switched on,
 * as a visible foreground service with a Stop button. Audio is processed
 * on-device by Vosk and is never saved or sent anywhere.
 */
class WakeWordService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WakeBus.status.value = "Off"
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            fail("Cannot start: ${e.message}")
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fail("Microphone permission missing")
            return START_NOT_STICKY
        }
        if (!WakeModel.isInstalled(this)) {
            fail("Voice model not downloaded")
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        started = true
        WakeBus.status.value = "Loading voice model..."

        Thread {
            try {
                val m = Model(WakeModel.dir(this).absolutePath)
                main.post {
                    if (started) {
                        model = m
                        listen()
                    } else {
                        m.close()
                    }
                }
            } catch (e: Throwable) {
                main.post { fail("Voice model failed to load: ${e.message}") }
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hey Yasmin", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Listening for \"Hey Yasmin\"")
            .setContentText("Microphone is ON. Tap Stop to turn it off.")
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun listen() {
        val m = model ?: return
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE, GRAMMAR)
            val s = SpeechService(recognizer, SAMPLE_RATE)
            s.startListening(listener)
            speech = s
            WakeBus.status.value = "Listening for \"Hey Yasmin\""
        } catch (e: Throwable) {
            fail("Could not start microphone: ${e.message}")
        }
    }

    private fun releaseMic() {
        speech?.stop()
        speech?.shutdown()
        speech = null
    }

    private fun heard(hypothesis: String?) {
        if (speech == null) return
        val text = hypothesis?.lowercase() ?: return
        if (PHRASES.none { text.contains(it) }) return
        // Give the mic back so push-to-talk can use it, then resume later.
        releaseMic()
        WakeBus.status.value = "Heard \"Hey Yasmin\""
        WakeBus.events.tryEmit(Unit)
        main.postDelayed({ if (started) listen() }, RESUME_DELAY_MS)
    }

    private fun fail(message: String) {
        WakeBus.status.value = message
        stopSelf()
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = heard(hypothesis)
        override fun onResult(hypothesis: String?) = heard(hypothesis)
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) {
            fail("Microphone error: ${exception?.message}")
        }
        override fun onTimeout() {}
    }

    override fun onDestroy() {
        started = false
        main.removeCallbacksAndMessages(null)
        releaseMic()
        model?.close()
        model = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.yasmin.ai.wake.STOP"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIF_ID = 42
        private const val SAMPLE_RATE = 16000.0f
        private const val RESUME_DELAY_MS = 10_000L
        private val PHRASES = listOf("hey yasmin", "hey yasmine", "hey jasmine")
        private const val GRAMMAR =
            "[\"hey yasmin\", \"hey yasmine\", \"hey jasmine\", \"[unk]\"]"

        /** Call only after mic permission is granted and the model is installed. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, WakeWordService::class.java)
            )
        }

        fun stop(context: Context) {
            WakeBus.status.value = "Off"
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
