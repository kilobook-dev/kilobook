package com.kilobook.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screen options the driver sets in 설정 (saved across restarts). Kept apart from the recorder's rules. */
class DisplaySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    private val _showDebugPanel = MutableStateFlow(prefs.getBoolean(KEY_SHOW_DEBUG, false))
    /** Shows the 디버그 tab (vehicle API results, recorder state, debug log). Off by default. */
    val showDebugPanel: StateFlow<Boolean> = _showDebugPanel.asStateFlow()

    private val _voiceGuide = MutableStateFlow(prefs.getBoolean(KEY_VOICE_GUIDE, true))
    /** Phase 7: ask the purpose by voice when the card appears while stopped. On by default. */
    val voiceGuide: StateFlow<Boolean> = _voiceGuide.asStateFlow()

    private val _voiceAnswer = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ANSWER, VOICE_ANSWER_DEFAULT))
    /** Phase 7: after the question, listen for "업무 / 출퇴근 / 개인". */
    val voiceAnswer: StateFlow<Boolean> = _voiceAnswer.asStateFlow()

    fun setShowDebugPanel(show: Boolean) = save(KEY_SHOW_DEBUG, show, _showDebugPanel)
    fun setVoiceGuide(on: Boolean) = save(KEY_VOICE_GUIDE, on, _voiceGuide)
    fun setVoiceAnswer(on: Boolean) = save(KEY_VOICE_ANSWER, on, _voiceAnswer)

    private fun save(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    private companion object {
        const val KEY_SHOW_DEBUG = "showDebugPanel"
        const val KEY_VOICE_GUIDE = "voiceGuide"
        const val KEY_VOICE_ANSWER = "voiceAnswer"
        /** On: recognition confirmed on the emulator (docs/phase7, V8). */
        const val VOICE_ANSWER_DEFAULT = true
    }
}
