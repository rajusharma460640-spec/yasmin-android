package com.yasmin.ai.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yasmin.ai.data.ChatMessage
import com.yasmin.ai.data.ChatRepository
import com.yasmin.ai.data.LlmException
import com.yasmin.ai.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ChatRepository(app)
    val speaker = Speaker(app)

    val messages = mutableStateListOf<ChatMessage>()

    var isThinking by mutableStateOf(false)
        private set

    /** Reserved for Stage G (push-to-talk). Always false in Stage F. */
    var isListening by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var voiceNotice by mutableStateOf<String?>(null)
        private set

    var hasApiKey by mutableStateOf(repo.settings.apiKey.isNotBlank())
        private set

    var voiceOutput by mutableStateOf(repo.settings.voiceOutput)
        private set
    var langMode by mutableStateOf(repo.settings.langMode)
        private set
    var speechRate by mutableStateOf(repo.settings.speechRate)
        private set
    var volume by mutableStateOf(repo.settings.volume)
        private set
    var voiceHi by mutableStateOf(repo.settings.voiceHi)
        private set
    var voiceEn by mutableStateOf(repo.settings.voiceEn)
        private set

    val baseUrl: String get() = repo.settings.baseUrl
    val model: String get() = repo.settings.model

    val avatarState: AvatarState
        get() = when {
            isListening -> AvatarState.Listening
            isThinking -> AvatarState.Thinking
            speaker.isSpeaking -> AvatarState.Speaking
            else -> AvatarState.Idle
        }

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { repo.loadHistory() }
            messages.addAll(saved)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isThinking) return
        error = null
        voiceNotice = null
        speaker.stop()
        viewModelScope.launch {
            val userMsg = withContext(Dispatchers.IO) { repo.saveMessage("user", trimmed) }
            messages.add(userMsg)
            isThinking = true
            try {
                val reply = repo.askLlm(messages.toList())
                val aiMsg = withContext(Dispatchers.IO) { repo.saveMessage("assistant", reply) }
                messages.add(aiMsg)
                if (voiceOutput) {
                    voiceNotice = speaker.speak(reply, langMode, speechRate, volume, voiceHi, voiceEn)
                }
            } catch (e: LlmException) {
                error = e.message
            } catch (e: Exception) {
                error = "Network error: ${e.message ?: "unknown"}"
            } finally {
                isThinking = false
            }
        }
    }

    fun stopSpeaking() {
        speaker.stop()
    }

    fun updateVoiceOutput(on: Boolean) {
        voiceOutput = on
        repo.settings.voiceOutput = on
        if (!on) speaker.stop()
    }

    fun updateLangMode(mode: String) {
        langMode = mode
        repo.settings.langMode = mode
    }

    fun updateSpeechRate(rate: Float) {
        speechRate = rate
        repo.settings.speechRate = rate
    }

    fun updateVolume(v: Float) {
        volume = v
        repo.settings.volume = v
    }

    fun setVoice(lang: String, name: String) {
        if (lang == "hi") {
            voiceHi = name
            repo.settings.voiceHi = name
        } else {
            voiceEn = name
            repo.settings.voiceEn = name
        }
    }

    fun voicesFor(lang: String) = speaker.voicesFor(lang)

    fun previewVoice(lang: String, voiceName: String) {
        val sample = if (lang == "hi") {
            "नमस्ते, मैं यास्मीन हूँ। आप कैसे हैं?"
        } else {
            "Hello, I am Yasmin. How are you today?"
        }
        voiceNotice = speaker.speak(
            sample,
            lang,
            speechRate,
            volume,
            if (lang == "hi") voiceName else voiceHi,
            if (lang == "en") voiceName else voiceEn
        )
    }

    fun saveSettings(baseUrl: String, model: String, apiKey: String) {
        val url = baseUrl.trim()
        if (url.isNotEmpty()) {
            if (!url.startsWith("https://")) {
                error = "Base URL https:// se shuru hona chahiye."
                return
            }
            repo.settings.baseUrl = url
        }
        if (model.trim().isNotEmpty()) repo.settings.model = model.trim()
        if (apiKey.trim().isNotEmpty()) repo.settings.apiKey = apiKey.trim()
        hasApiKey = repo.settings.apiKey.isNotBlank()
        error = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearHistory() }
            messages.clear()
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }
}
