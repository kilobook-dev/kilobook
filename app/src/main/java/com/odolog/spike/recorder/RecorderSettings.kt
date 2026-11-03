package com.odolog.spike.recorder

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tunable rules. Defaults are the debug values from the spec
 * (production: idle 10 min is not specified, stale 10 min).
 */
data class RecorderConfig(
    /** Fallback end rule: odometer unchanged for this many minutes. Debug N = 1. */
    val idleMinutes: Int = 1,
    /** Requirement A: an OPEN trip older than this at restart is closed at the last known point. Production 10, debug 1. */
    val staleMinutes: Int = 1,
    /** Ignore gear / parking brake and use the fallback rules (to test them on this emulator). */
    val forceFallback: Boolean = false,
    /** Run recording in a foreground service so it continues when the screen is not visible. */
    val backgroundService: Boolean = true,
)

class RecorderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RecorderConfig> = _state.asStateFlow()

    fun update(change: (RecorderConfig) -> RecorderConfig) {
        val next = change(_state.value)
        prefs.edit()
            .putInt("idleMinutes", next.idleMinutes)
            .putInt("staleMinutes", next.staleMinutes)
            .putBoolean("forceFallback", next.forceFallback)
            .putBoolean("backgroundService", next.backgroundService)
            .apply()
        _state.value = next
    }

    private fun load(): RecorderConfig {
        val d = RecorderConfig()
        return RecorderConfig(
            idleMinutes = prefs.getInt("idleMinutes", d.idleMinutes),
            staleMinutes = prefs.getInt("staleMinutes", d.staleMinutes),
            forceFallback = prefs.getBoolean("forceFallback", d.forceFallback),
            backgroundService = prefs.getBoolean("backgroundService", d.backgroundService),
        )
    }
}
