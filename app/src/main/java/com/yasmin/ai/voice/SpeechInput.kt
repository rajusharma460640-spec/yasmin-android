package com.yasmin.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Push-to-talk speech input using Android's SpeechRecognizer.
 * Must be used from the main thread. The mic is open only between start() and a result/error/cancel().
 * Recognition is done by the phone's speech service (usually Google); on many phones it is online.
 */
class SpeechInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    var isListening by mutableStateOf(false)
        private set

    var partial by mutableStateOf("")
        private set

    /** Returns null if listening started, otherwise a user-readable reason. */
    fun start(langTag: String, onFinal: (String) -> Unit, onProblem: (String) -> Unit): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return "Is phone mein speech recognition service nahi mili. Google app ya Speech Services install/enable karo."
        }
        cancel()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                finish()
                if (text.isNotEmpty()) onFinal(text) else onProblem(describe(SpeechRecognizer.ERROR_NO_MATCH))
            }

            override fun onError(error: Int) {
                finish()
                onProblem(describe(error))
            }
        })
        recognizer = rec

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        partial = ""
        isListening = true
        rec.startListening(intent)
        return null
    }

    /** Ask the recognizer to finish; the result (or an error) arrives shortly after. */
    fun stopListening() {
        recognizer?.stopListening()
    }

    /** Immediately stop the microphone and discard anything heard. */
    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        isListening = false
        partial = ""
    }

    private fun finish() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
        partial = ""
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Kuch sunai nahi diya. Mic dabake dobara bolo."
        SpeechRecognizer.ERROR_AUDIO ->
            "Mic se audio nahi mila. Koi aur app mic use kar rahi ho sakti hai."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition ke liye internet chahiye (is phone par ye online chalta hai). Network check karo."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Mic permission nahi hai."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech service busy hai. Thodi der baad try karo."
        SpeechRecognizer.ERROR_SERVER ->
            "Speech service mein dikkat aayi. Dobara try karo."
        12, 13 ->
            "Ye bhasha speech recognition mein available nahi. Phone Settings > Google > Speech Services mein language pack download karo."
        else ->
            "Voice input mein dikkat aayi (code $error)."
    }
}
