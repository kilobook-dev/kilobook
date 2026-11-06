package com.kilobook.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kilobook.app.data.Purpose
import com.kilobook.app.data.Trip
import com.kilobook.app.recorder.RecorderConfig
import com.kilobook.app.recorder.RecorderEvent
import com.kilobook.app.recorder.RecorderStatus
import com.kilobook.app.service.BootRecord
import com.kilobook.app.service.RecorderService
import com.kilobook.app.vehicle.DashboardState
import com.kilobook.app.vehicle.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The screen's view of the shared recording session.
 * Holds the session while the screen exists (Phase 2 behavior); the service holds it independently.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.graph
    private val session = graph.session

    val vehicle: StateFlow<DashboardState> = session.vehicle
    val recorder: StateFlow<RecorderStatus> = session.recorder
    val owners: StateFlow<Set<String>> = session.activeOwners
    val config: StateFlow<RecorderConfig> = graph.settings.state
    val trips: StateFlow<List<Trip>> = graph.db.tripDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val log: StateFlow<List<AppLog.Entry>> = AppLog.entries

    private val _bootRecord = MutableStateFlow(BootRecord.last(application))
    /** Last BOOT_COMPLETED handling (S8), saved by BootReceiver. */
    val bootRecord: StateFlow<String?> = _bootRecord.asStateFlow()

    init {
        session.acquire(OWNER)
    }

    /** Screen became visible: app start or return. A chance to re-ask and to check for gaps. */
    fun onScreenStart() {
        _bootRecord.value = BootRecord.last(getApplication())
        if (config.value.backgroundService) startService("screen")
        session.send(RecorderEvent.ScreenStarted)
    }

    /** Screen went to the background: save the last known odometer once more. */
    fun onScreenStop() {
        session.send(RecorderEvent.ScreenStopped)
    }

    fun choosePurpose(tripId: Long, purpose: Purpose) = session.send(RecorderEvent.ChoosePurpose(tripId, purpose))
    fun later(tripId: Long) = session.send(RecorderEvent.Later(tripId))
    fun endTripManually() = session.send(RecorderEvent.ManualEnd)
    fun resetRecords() = session.send(RecorderEvent.Reset)
    fun clearLog() = AppLog.clear()

    fun updateConfig(change: (RecorderConfig) -> RecorderConfig) {
        val before = config.value.backgroundService
        graph.settings.update(change)
        val after = config.value.backgroundService
        if (!before && after) startService("toggle")
        if (before && !after) RecorderService.stop(getApplication())
    }

    private fun startService(reason: String) {
        try {
            RecorderService.start(getApplication(), reason)
        } catch (e: Exception) {
            AppLog.i("RecorderService.start($reason) -> ${e.describe()}")
        }
    }

    override fun onCleared() {
        session.release(OWNER)
    }

    companion object {
        const val OWNER = "screen"
    }
}
