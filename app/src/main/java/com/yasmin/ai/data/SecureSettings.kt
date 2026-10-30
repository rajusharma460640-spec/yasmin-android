package com.yasmin.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's own API key encrypted (Android Keystore-backed).
 * If encryption is unavailable on a device, the key is kept in memory only
 * and is never written to disk in plain text.
 * Non-secret preferences (voice, language, provider URL) live in normal private prefs.
 */
class SecureSettings(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("yasmin_settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "yasmin_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    private var memoryKey: String = ""

    var apiKey: String
        get() = secure?.getString("api_key", null) ?: memoryKey
        set(value) {
            memoryKey = value
            secure?.edit()?.putString("api_key", value)?.apply()
        }

    var baseUrl: String
        get() = plain.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            plain.edit().putString("base_url", value).apply()
        }

    var model: String
        get() = plain.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            plain.edit().putString("model", value).apply()
        }

    /** "auto", "hi" or "en" */
    var langMode: String
        get() = plain.getString("lang_mode", "auto") ?: "auto"
        set(value) {
            plain.edit().putString("lang_mode", value).apply()
        }

    var voiceOutput: Boolean
        get() = plain.getBoolean("voice_output", true)
        set(value) {
            plain.edit().putBoolean("voice_output", value).apply()
        }

    var speechRate: Float
        get() = plain.getFloat("speech_rate", 1.0f)
        set(value) {
            plain.edit().putFloat("speech_rate", value).apply()
        }

    var volume: Float
        get() = plain.getFloat("volume", 1.0f)
        set(value) {
            plain.edit().putFloat("volume", value).apply()
        }

    /** Selected TTS voice names. Empty string means the engine's default voice. */
    var voiceHi: String
        get() = plain.getString("voice_hi", "") ?: ""
        set(value) {
            plain.edit().putString("voice_hi", value).apply()
        }

    var voiceEn: String
        get() = plain.getString("voice_en", "") ?: ""
        set(value) {
            plain.edit().putString("voice_en", value).apply()
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
    }
}
