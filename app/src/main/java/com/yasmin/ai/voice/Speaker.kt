package com.yasmin.ai.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Wrapper around Android TextToSpeech. Uses whatever TTS engine the phone has (e.g. Google). */
class Speaker(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var counter = 0

    var ready by mutableStateOf(false)
        private set

    var initFailed by mutableStateOf(false)
        private set

    var isSpeaking by mutableStateOf(false)
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            main.post { isSpeaking = true }
                        }

                        override fun onDone(utteranceId: String?) {
                            main.post { isSpeaking = false }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            main.post { isSpeaking = false }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            main.post { isSpeaking = false }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            main.post { isSpeaking = false }
                        }
                    })
                    ready = true
                } else {
                    initFailed = true
                }
            }
        }
    }

    /** Voices the engine offers for a language code ("hi" or "en"). Android does not expose gender. */
    fun voicesFor(lang: String): List<Voice> =
        try {
            engine?.voices?.filter { it.locale.language == lang }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Speaks [text]. Returns null on success, or a user-readable explanation if speech was not possible.
     */
    fun speak(
        text: String,
        langMode: String,
        rate: Float,
        volume: Float,
        voiceHi: String,
        voiceEn: String
    ): String? {
        val tts = engine
        if (initFailed || tts == null) {
            return "Text-to-speech engine start nahi hua. Phone mein Google Text-to-speech installed aur enabled hai ya nahi check karo."
        }
        if (!ready) return "Voice engine abhi taiyaar ho raha hai. Thodi der baad try karo."

        val clean = cleanForSpeech(text)
        if (clean.isBlank()) return null

        val lang = when (langMode) {
            "hi" -> "hi"
            "en" -> "en"
            else -> detectLang(clean)
        }
        val locale = if (lang == "hi") Locale("hi", "IN") else Locale("en", "IN")
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return if (lang == "hi") {
                "Hindi voice is phone mein available nahi hai. Phone Settings > Text-to-speech > Google engine > Install voice data mein Hindi (India) download karo."
            } else {
                "English voice is phone mein available nahi hai. Phone Settings > Text-to-speech mein English voice data download karo."
            }
        }

        val wanted = if (lang == "hi") voiceHi else voiceEn
        if (wanted.isNotEmpty()) {
            try {
                tts.voices?.firstOrNull { it.name == wanted }?.let { tts.voice = it }
            } catch (e: Exception) {
                // Keep the default voice for this language.
            }
        }

        tts.setSpeechRate(rate)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        val id = "yasmin-" + counter++
        val res = tts.speak(clean, TextToSpeech.QUEUE_FLUSH, params, id)
        if (res == TextToSpeech.ERROR) return "Awaaz nahi chal paayi (TTS error)."
        return null
    }

    fun stop() {
        engine?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        isSpeaking = false
    }

    companion object {
        private val HINGLISH = setOf(
            "hai", "hain", "hoon", "ho", "main", "mein", "aap", "tum", "kya", "kaise", "kaisi", "kaisa",
            "nahi", "nahin", "haan", "ka", "ki", "ke", "ko", "se", "par", "aur", "toh", "bhi", "mujhe",
            "aapko", "kaun", "kyun", "kuch", "bahut", "accha", "acha", "theek", "madad", "chahiye",
            "karo", "kar", "raha", "rahi", "hoga", "tha", "thi", "yeh", "ye", "woh", "wo", "mera",
            "meri", "tera", "apna", "apni", "kaam", "baat", "batao", "bolo", "dost"
        )

        private val MARKDOWN = Regex("[*_`#>~|]")
        private val EMOJI = Regex("[\\p{So}\\x{1F300}-\\x{1FAFF}\\x{200D}\\x{FE0F}]")
        private val SPACES = Regex("\\s+")
        private val NON_LETTERS = Regex("[^a-z]+")

        fun cleanForSpeech(s: String): String =
            s.replace(MARKDOWN, " ")
                .replace(EMOJI, "")
                .replace(SPACES, " ")
                .trim()
                .take(3500)

        /** Devanagari text -> Hindi. Roman text with several common Hindi words -> Hindi (Hinglish). Otherwise English. */
        fun detectLang(text: String): String {
            if (text.any { it in '\u0900'..'\u097F' }) return "hi"
            val words = text.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }
            if (words.isEmpty()) return "en"
            val hits = words.count { it in HINGLISH }
            return if (hits >= 2 || hits * 5 >= words.size) "hi" else "en"
        }
    }
}
