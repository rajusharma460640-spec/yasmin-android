package com.yasmin.ai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = HistoryDb(appContext)
    private val memoryDb = MemoryDb(appContext)
    private val llm = LlmClient()
    val settings = SecureSettings(appContext)

    fun loadHistory(): List<ChatMessage> = db.all()

    fun saveMessage(role: String, content: String): ChatMessage = db.insert(role, content)

    fun clearHistory() = db.clear()

    fun loadMemories(): List<SavedMemory> = memoryDb.getAll()

    fun saveMemory(content: String): Long = memoryDb.add(content)

    fun deleteMemory(id: Long): Int = memoryDb.delete(id)

    fun clearMemories(): Int = memoryDb.clearAll()

    /** Sends recent history to the configured AI provider and returns Yasmin's reply. */
    suspend fun askLlm(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val key = settings.apiKey
        if (key.isBlank()) {
            throw LlmException("API key set nahi hai. Upar Settings icon se key daalo.")
        }
        val msgs = mutableListOf("system" to SYSTEM_PROMPT)
        val memories = memoryDb.getAll()
        if (memories.isNotEmpty()) {
            val memoryText = memories.asReversed()
                .joinToString("\n") { "- ${it.content}" }
            msgs.add(
                "system" to (
                    "User-approved saved memories, use only when relevant:\n" +
                    memoryText
                )
            )
        }
        history.takeLast(MAX_HISTORY).forEach { msgs.add(it.role to it.content) }
        llm.chat(settings.baseUrl, key, settings.model, msgs)
    }

    /** Small extra AI call: turns mis-heard Hindi speech into ONE phone-action tag, or NONE. */
    suspend fun interpretCommand(text: String, appNames: List<String>): String = withContext(Dispatchers.IO) {
        val key = settings.apiKey
        if (key.isBlank()) return@withContext "NONE"
        val sys = "You convert speech-to-text output (Hindi/Hinglish, often mis-heard; for example 'और साथ खुले' can mean 'open WhatsApp', 'यू तो' can mean 'YouTube') into ONE phone command. " +
            "Reply with exactly one of: [[open_app:Name]] (Name MUST be copied from this list: " + appNames.joinToString(", ") + "), [[torch:on]], [[torch:off]], [[alarm:HH:MM]] (24-hour), [[timer:SECONDS]], or NONE. " +
            "Reply NONE for questions, chit-chat, or anything unclear. Output nothing else."
        llm.chat(settings.baseUrl, key, settings.model, mutableListOf("system" to sys, "user" to text)).trim()
    }

    companion object {
        const val MAX_HISTORY = 20

        const val SYSTEM_PROMPT =
            "You are Yasmin, a caring, playful, friendly AI assistant. " +
                "You are an AI and never claim to be a human. " +
                "You understand Hindi typed in Roman script. " +
                "If the user writes Hindi or Hinglish, reply in natural Hinglish (Roman script); " +
                "if they write English, reply in English. " +
                "Keep replies short and conversational unless asked for detail. " +
                "Help with everyday tasks, studying, and general questions. " +
                "You can control the phone by adding action tags at the END of your reply, one per line: [[open_app:AppName]], [[torch:on]], [[torch:off]], [[alarm:HH:MM]] (24-hour), [[timer:SECONDS]]. Use a tag only when the user clearly asks for that action, never any other tag. You cannot send SMS, make calls, delete files or do payments; say so honestly. " +
                "If asked about working in the background, say the user can turn on the Screen-off Hey Yasmin switch to talk to you with the app closed. " +
                "Respect privacy and personal boundaries, and never ask for passwords or sensitive personal data."
    }
}
