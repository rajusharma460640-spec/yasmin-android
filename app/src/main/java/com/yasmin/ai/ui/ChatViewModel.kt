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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ChatRepository(app)

    val messages = mutableStateListOf<ChatMessage>()

    var isThinking by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var hasApiKey by mutableStateOf(repo.settings.apiKey.isNotBlank())
        private set

    val baseUrl: String get() = repo.settings.baseUrl
    val model: String get() = repo.settings.model

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
        viewModelScope.launch {
            val userMsg = withContext(Dispatchers.IO) { repo.saveMessage("user", trimmed) }
            messages.add(userMsg)
            isThinking = true
            try {
                val reply = repo.askLlm(messages.toList())
                val aiMsg = withContext(Dispatchers.IO) { repo.saveMessage("assistant", reply) }
                messages.add(aiMsg)
            } catch (e: LlmException) {
                error = e.message
            } catch (e: Exception) {
                error = "Network error: ${e.message ?: "unknown"}"
            } finally {
                isThinking = false
            }
        }
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
}
