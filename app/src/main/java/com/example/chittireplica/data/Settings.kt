package com.example.chittireplica.data

import android.content.Context
import com.example.chittireplica.BuildConfig

/** Groq key + model. Settings-screen value wins; BuildConfig (local.properties) is the fallback. */
object Settings {
    private const val PREFS = "chitti"
    private const val KEY_GROQ = "groq_key"
    private const val KEY_MODEL = "groq_model"

    const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    const val WHISPER_MODEL = "whisper-large-v3-turbo"

    fun getGroqKey(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GROQ, "").orEmpty().trim()
        if (saved.isNotEmpty()) return saved
        return BuildConfig.GROQ_API_KEY.trim()
    }

    fun setGroqKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROQ, key.trim()).apply()
    }

    fun getModel(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
    }

    fun setModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL, model.trim().ifBlank { DEFAULT_MODEL }).apply()
    }
}
