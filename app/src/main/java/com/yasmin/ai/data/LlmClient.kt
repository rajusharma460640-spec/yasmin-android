package com.yasmin.ai.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal OpenAI-compatible chat client (works with Groq). Call from a background thread. */
class LlmClient {

    fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>
    ): String {
        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val msgArray = JSONArray()
        for ((role, content) in messages) {
            msgArray.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.8)
            .put("messages", msgArray)

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            // Groq's firewall blocked the default Python agent earlier (HTTP 403 / 1010), so send our own.
            conn.setRequestProperty("User-Agent", "YasminAI-Android/0.1")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw LlmException(friendlyError(code, text))

            val content = JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
                .trim()
            if (content.isEmpty()) throw LlmException("Khaali jawab aaya. Dobara try karo.")
            return content
        } finally {
            conn.disconnect()
        }
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).getJSONObject("error").optString("message", "")
        } catch (e: Exception) {
            ""
        }
        val hint = when (code) {
            401 -> "API key galat hai. Settings mein check karo."
            403 -> "Access block hua (403). Key ya network check karo."
            404 -> "Model ya Base URL galat hai. Settings mein check karo."
            429 -> "Bahut zyada requests. Thodi der baad try karo."
            else -> "Server error."
        }
        return "HTTP $code: $hint" + if (detail.isNotEmpty()) "\n$detail" else ""
    }
}
