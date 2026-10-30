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
import com.yasmin.ai.data.SavedMemory
import com.yasmin.ai.data.LlmException
import com.yasmin.ai.voice.Speaker
import com.yasmin.ai.voice.SpeechInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ChatRepository(app)
    val speaker = Speaker(app)

    val messages = mutableStateListOf<ChatMessage>()

    val savedMemories = mutableStateListOf<SavedMemory>()

    var isThinking by mutableStateOf(false)
        private set

    val speech = SpeechInput(app)

    val isListening: Boolean get() = speech.isListening

    var micLang by mutableStateOf(repo.settings.micLang)
        private set

    var micDenied by mutableStateOf(false)
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
            speech.isListening -> AvatarState.Listening
            isThinking -> AvatarState.Thinking
            speaker.isSpeaking -> AvatarState.Speaking
            else -> AvatarState.Idle
        }

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { repo.loadHistory() }
            messages.addAll(saved)

            val memories = withContext(Dispatchers.IO) { repo.loadMemories() }
            savedMemories.clear()
            savedMemories.addAll(memories)
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

    /** Push-to-talk: first tap starts listening, second tap stops and sends what was heard. */
    fun toggleMic() {
        if (speech.isListening) speech.stopListening() else startListening()
    }

    fun startListening() {
        if (isThinking || speech.isListening) return
        speaker.stop() // don't let Yasmin's own voice be picked up as input
        error = null
        voiceNotice = null
        val tag = if (micLang == "en") "en-IN" else "hi-IN"
        val problem = speech.start(
            tag,
            onFinal = { heard -> send(heard) },
            onProblem = { msg -> voiceNotice = msg }
        )
        if (problem != null) voiceNotice = problem
    }

    fun cancelListening() {
        speech.cancel()
    }

    fun onMicPermissionResult(granted: Boolean) {
        micDenied = !granted
        if (granted) startListening()
    }

    fun updateMicLang(lang: String) {
        if (speech.isListening) return
        micLang = lang
        repo.settings.micLang = lang
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

    fun refreshMemories() {
        viewModelScope.launch {
            val memories = withContext(Dispatchers.IO) {
                repo.loadMemories()
            }
            savedMemories.clear()
            savedMemories.addAll(memories)
        }
    }

    fun addMemory(content: String) {
        val value = content.trim()
        if (value.isEmpty()) {
            error = "Memory khaali nahi ho sakti."
            return
        }
        if (value.length > 500) {
            error = "Memory 500 characters se chhoti honi chahiye."
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.saveMemory(value) }
                refreshMemories()
                error = null
            } catch (e: Exception) {
                error = "Memory save nahi hui: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun removeMemory(id: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.deleteMemory(id) }
                refreshMemories()
            } catch (e: Exception) {
                error = "Memory delete nahi hui: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun removeAllMemories() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.clearMemories() }
                savedMemories.clear()
            } catch (e: Exception) {
                error = "Memories clear nahi hui: ${e.message ?: "unknown error"}"
            }
        }
    }

    override fun onCleared() {
        speech.cancel()
        speaker.shutdown()
        super.onCleared()
    }
}
