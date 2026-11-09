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

    fun setShowDebugPanel(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DEBUG, show).apply()
        _showDebugPanel.value = show
    }

    private companion object {
        const val KEY_SHOW_DEBUG = "showDebugPanel"
    }
}
