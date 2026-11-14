package com.ziaee.zabankhan.speech.local

import android.content.Context

object LocalTtsPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("zabankhan_local_tts", Context.MODE_PRIVATE)
    fun serverUrl(context: Context) = prefs(context).getString("server_url", "").orEmpty()
    fun setServerUrl(context: Context, value: String) { prefs(context).edit().putString("server_url", value).apply() }
    fun token(context: Context) = prefs(context).getString("token", "").orEmpty()
    fun setToken(context: Context, value: String) { prefs(context).edit().putString("token", value).apply() }
    fun speaker(context: Context) = prefs(context).getString("speaker", "").orEmpty()
    fun setSpeaker(context: Context, value: String) { prefs(context).edit().putString("speaker", value).apply() }
    fun usePaired(context: Context) = prefs(context).getBoolean("use_paired", true)
    fun setUsePaired(context: Context, value: Boolean) { prefs(context).edit().putBoolean("use_paired", value).apply() }
}
