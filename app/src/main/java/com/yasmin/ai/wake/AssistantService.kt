package com.yasmin.ai.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yasmin.ai.MainActivity
import com.yasmin.ai.data.ChatRepository
import com.yasmin.ai.voice.Speaker
import com.yasmin.ai.voice.SpeechInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Screen-off "Hey Yasmin" assistant.
 *
 * Runs as a microphone foreground service (visible notification with a Stop button).
 * Flow: offline Vosk listens for "Hey Yasmin" -> beep -> Android speech recognizer hears the question
 * -> AI provider answers -> Android TTS speaks -> back to listening.
 * Must be started while the app is on screen (Android blocks starting a mic service from the background).
 * It is independent of the older WakeWordService: keep that switch OFF while this one is ON.
 */
class AssistantService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var vosk: SpeechService? = null
    private var hiModel: Model? = null
    private var hiRecognizer: Recognizer? = null
    private var hiVosk: SpeechService? = null
    private lateinit var repo: ChatRepository
    private lateinit var speaker: Speaker
    private lateinit var speech: SpeechInput
    private var started = false
    private var busy = false
    private var destroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Yasmin mic", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        repo = ChatRepository(applicationContext)
        speaker = Speaker(applicationContext)
        speech = SpeechInput(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Must be called promptly after startForegroundService().
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification("Taiyaar ho rahi hoon..."),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        } catch (e: Exception) {
            message = "Mic service start nahi hui: ${e.message ?: "error"}"
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        started = true
        running = true
        acquireWakeLock()
        loadModelAndListen()
        return START_NOT_STICKY // never auto-restart: Android would not allow a mic service to start from the background
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yasmin:assistant").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // safety timeout: 12 hours
        }
    }

    private fun loadModelAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Mic permission nahi hai.")
            return
        }
        if (!WakeModel.isInstalled(this)) {
            fail("Offline model install nahi hai. Settings ke purane Hey Yasmin switch se ek baar download karo.")
            return
        }
        status("Model load ho raha hai...")
        scope.launch {
            try {
                val path = WakeModel.dir(applicationContext).absolutePath
                val m = withContext(Dispatchers.Default) { Model(path) }
                if (destroyed) {
                    m.close()
                    return@launch
                }
                model = m
                recognizer = Recognizer(m, 16000.0f)
                if (HindiModel.isInstalled(applicationContext)) {
                    try {
                        val hm = withContext(Dispatchers.Default) {
                            Model(HindiModel.dir(applicationContext).absolutePath)
                        }
                        hiModel = hm
                        hiRecognizer = Recognizer(hm, 16000.0f)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        hiModel = null
                        hiRecognizer = null
                    }
                }
                startWake()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Model load nahi hua: ${e.message ?: "error"}")
            }
        }
    }

    // ---- Wake-word listening (offline Vosk) ----

    private fun startWake() {
        val rec = recognizer ?: return
        if (destroyed) return
        stopWake()
        try {
            rec.reset()
            val service = SpeechService(rec, 16000.0f)
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) = inspect(hypothesis, "partial")
                override fun onResult(hypothesis: String?) = inspect(hypothesis, "text")
                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) {
                    status("Mic error: ${exception?.message ?: "unknown"}")
                }
                override fun onTimeout() {}
            })
            vosk = service
            status(if (hiRecognizer != null) "\"Hey Yasmin\" ka intezaar... (Hindi mic ON)" else "\"Hey Yasmin\" ka intezaar... (Hindi model NAHI mila)")
        } catch (e: Exception) {
            fail("Mic start nahi hua: ${e.message ?: "error"}")
        }
    }

    private fun stopWake() {
        try {
            vosk?.cancel()
            vosk?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        vosk = null
    }

    private fun inspect(json: String?, key: String) {
        if (json == null || busy || destroyed) return
        val text = try {
            JSONObject(json).optString(key, "")
        } catch (e: Exception) {
            ""
        }
        if (text.isNotBlank() && isWake(text)) onWake()
    }

    /**
     * Vosk's small English model has no word "yasmin", so it often writes "jasmine" etc.
     * Tune this list if it triggers too rarely or too often.
     */
    private fun isWake(raw: String): Boolean {
        val t = raw.lowercase().trim()
        val name = "(yasmin|yasmine|jasmine|jasmin|yas min|yes min|yes mean)"
        return Regex("\\b(hey|hay|hi|hello|okay|ok)\\s+$name\\b").containsMatchIn(t) ||
            Regex("^(a\\s+|eh\\s+)?$name\$").matches(t)
    }

    // ---- After the wake word ----

    private fun onWake() {
        if (busy) return
        busy = true
        stopWake() // release the mic for the speech recognizer, and so she never hears herself
        speaker.stop()
        beep()
        status("Sun rahi hoon, sawaal bolo...")
        main.postDelayed({ listenForQuestion() }, 350)
    }

    private fun stopHindi() {
        try {
            hiVosk?.cancel()
            hiVosk?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        hiVosk = null
    }

    /** Background-safe question listening: offline Hindi Vosk inside this service. */
    private fun listenHindi() {
        val rec = hiRecognizer ?: return
        var done = false
        var last = ""
        fun textOf(json: String?, key: String): String = try {
            JSONObject(json ?: "").optString(key, "").trim()
        } catch (e: Exception) {
            ""
        }
        fun give(t: String) {
            if (done) return
            done = true
            main.post { stopHindi(); answer(t) }
        }
        fun giveUp(msg: String) {
            if (done) return
            done = true
            main.post { stopHindi(); status(msg); resumeWake() }
        }
        try {
            rec.reset()
            val svc = SpeechService(rec, 16000.0f)
            hiVosk = svc
            svc.startListening(object : RecognitionListener {
                override fun onPartialResult(h: String?) {
                    val p = textOf(h, "partial")
                    if (p.isNotBlank()) last = p
                }
                override fun onResult(h: String?) {
                    val t = textOf(h, "text")
                    if (t.isNotBlank()) give(t)
                }
                override fun onFinalResult(h: String?) {
                    val t = textOf(h, "text")
                    if (t.isNotBlank()) give(t)
                }
                override fun onError(e: Exception?) = giveUp("Mic error: ${e?.message ?: "unknown"}")
                override fun onTimeout() {
                    if (last.isNotBlank()) give(last) else giveUp("Kuch sunai nahi diya")
                }
            }, 15000)
        } catch (e: Exception) {
            giveUp("Hindi mic start nahi hua: ${e.message ?: "error"}")
        }
    }

    private fun listenForQuestion() {
        if (hiRecognizer != null && !destroyed) {
            listenHindi()
            return
        }
        if (destroyed) return
        val tag = if (repo.settings.micLang == "en") "en-IN" else "hi-IN"
        val problem = speech.start(
            tag,
            onFinal = { heard -> answer(heard) },
            onProblem = { msg ->
                status(msg)
                resumeWake()
            }
        )
        if (problem != null) {
            status(problem)
            resumeWake()
        }
    }

    private fun answer(heard: String) {
        status("Jawab la rahi hoon...")
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    repo.saveMessage("user", heard)
                    val r = repo.askLlm(repo.loadHistory())
                    repo.saveMessage("assistant", r)
                    r
                }
                status("Bol rahi hoon...")
                speakAndWait(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status("Jawab nahi mila: ${e.message ?: "error"}")
                speakAndWait("Maaf karo, abhi jawab nahi mil paaya.")
            } finally {
                resumeWake()
            }
        }
    }

    private suspend fun speakAndWait(text: String) {
        val s = repo.settings
        if (!s.voiceOutput) return
        val notice = speaker.speak(text, s.langMode, s.speechRate, s.volume, s.voiceHi, s.voiceEn)
        if (notice != null) {
            status(notice)
            return
        }
        var waited = 0
        while (!speaker.isSpeaking && waited < 4000) {
            delay(100)
            waited += 100
        }
        var total = 0
        while (speaker.isSpeaking && total < 120_000) {
            delay(150)
            total += 150
        }
    }

    private fun resumeWake() {
        busy = false
        if (destroyed) return
        main.postDelayed({
            if (!destroyed && !busy) startWake()
        }, 400)
    }

    // ---- Helpers ----

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            main.postDelayed({ tone.release() }, 500)
        } catch (e: Exception) {
            // no beep, not critical
        }
    }

    private fun status(text: String) {
        message = text
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun fail(text: String) {
        message = text
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Yasmin sun rahi hai (mic ON)")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        destroyed = true
        running = false
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        speech.cancel()
        stopWake()
        try {
            recognizer?.close()
        } catch (e: Exception) {
            // ignore
        }
        try {
            model?.close()
        } catch (e: Exception) {
            // ignore
        }
        stopHindi()
        try {
            hiRecognizer?.close()
            hiModel?.close()
        } catch (e: Exception) {
            // ignore
        }
        speaker.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.yasmin.ai.ASSISTANT_STOP"
        private const val CHANNEL_ID = "yasmin_assistant"
        private const val NOTIF_ID = 4711

        /** True while the service is running (observable from Compose). */
        var running by mutableStateOf(false)
            private set

        /** Latest status line, shown in the notification and in the app. */
        var message by mutableStateOf("")
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AssistantService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantService::class.java))
        }
    }
}
