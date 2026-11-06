package com.kilobook.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One log for the whole process: goes to logcat (tag KiloBook) and to the on-screen debug log.
 * Kept in memory only, so it covers what happened since the process started (e.g. a boot start).
 */
object AppLog {
    const val TAG = "KiloBook"
    private const val MAX_ENTRIES = 300

    /**
     * Messages are "API or step -> result", e.g. "getCurrentDistanceDriven -> 12.3".
     * The same message arriving again in a row (the odometer callback repeats every few seconds) is folded
     * into one entry: [atMillis] is the latest time, [firstAtMillis] the first, [count] how many.
     */
    data class Entry(val atMillis: Long, val text: String, val firstAtMillis: Long = atMillis, val count: Int = 1) {
        val name: String get() = text.substringBefore(" -> ")
        val result: String get() = if (" -> " in text) text.substringAfter(" -> ") else ""

        /** App decisions (recorder, session, service, boot) as opposed to SDK / car API calls. */
        val isAppEvent: Boolean get() = APP_PREFIXES.any { text.startsWith(it) }
    }

    private val APP_PREFIXES = listOf("Recorder:", "Session:", "RecorderService:", "BootReceiver:")

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun i(text: String) {
        Log.i(TAG, text)
        val now = System.currentTimeMillis()
        _entries.update { list ->
            val last = list.lastOrNull()
            if (last != null && last.text == text) {
                list.dropLast(1) + last.copy(atMillis = now, count = last.count + 1)
            } else {
                (list + Entry(now, text)).takeLast(MAX_ENTRIES)
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
