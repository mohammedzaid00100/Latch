package com.zaid.latch.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.latchPreferences by preferencesDataStore("latch_preferences")
data class UserPreferences(val wifiOnly: Boolean = false, val preferAudio: Boolean = false, val darkMode: String = "system")
class Preferences(private val context: Context) {
    private val wifiKey = booleanPreferencesKey("wifi_only")
    private val audioKey = booleanPreferencesKey("prefer_audio")
    private val themeKey = stringPreferencesKey("theme")
    val flow = context.latchPreferences.data.map {
        UserPreferences(it[wifiKey] ?: false, it[audioKey] ?: false, it[themeKey] ?: "system")
    }
    suspend fun current() = flow.first()
    suspend fun wifiOnly(value: Boolean) { context.latchPreferences.edit { it[wifiKey] = value } }
    suspend fun preferAudio(value: Boolean) { context.latchPreferences.edit { it[audioKey] = value } }
    suspend fun theme(value: String) { context.latchPreferences.edit { it[themeKey] = value } }
}
