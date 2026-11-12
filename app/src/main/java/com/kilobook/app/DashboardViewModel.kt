package com.kilobook.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kilobook.app.data.Purpose
import com.kilobook.app.data.SampleTrips
import com.kilobook.app.data.Trip
import com.kilobook.app.recorder.RecorderConfig
import com.kilobook.app.recorder.RecorderEvent
import com.kilobook.app.recorder.RecorderStatus
import com.kilobook.app.recorder.endSignals
import com.kilobook.app.service.BootRecord
import com.kilobook.app.service.RecorderService
import com.kilobook.app.speech.VoiceAssistant
import com.kilobook.app.speech.VoiceState
import com.kilobook.app.vehicle.DashboardState
import com.kilobook.app.vehicle.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

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
    val showDebugPanel: StateFlow<Boolean> = graph.display.showDebugPanel

    /**
     * Driving = driving restriction active, or gear readable and not P (the recorder's rule 4 signal).
     * Unknown signals count as not driving; the purpose card still waits for a "stopped" signal.
     */
    val driving: StateFlow<Boolean> = session.vehicle
        .map { it.endSignals().driving }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _bootRecord = MutableStateFlow(BootRecord.last(application))
    /** Last BOOT_COMPLETED handling (S8), saved by BootReceiver. */
    val bootRecord: StateFlow<String?> = _bootRecord.asStateFlow()

    val voiceGuide: StateFlow<Boolean> = graph.display.voiceGuide
    val voiceAnswer: StateFlow<Boolean> = graph.display.voiceAnswer

    /** Phase 7: voice question and answer for the purpose card. Only while the screen is visible. */
    private val voice = VoiceAssistant(
        context = application,
        scope = viewModelScope,
        guideEnabled = { voiceGuide.value },
        answerEnabled = { voiceAnswer.value },
        mayTalk = { !driving.value && recorder.value.vehicleStopped },
        onAnswer = { tripId, purpose -> choosePurpose(tripId, purpose) },
    )
    val voiceState: StateFlow<VoiceState> = voice.state

    init {
        session.acquire(OWNER)
        // Never talk while driving: stop the moment driving starts.
        viewModelScope.launch { driving.collect { if (it) voice.cancel("주행 시작") } }
    }

    /** Screen became visible: app start or return. A chance to re-ask and to check for gaps. */
    fun onScreenStart() {
        _bootRecord.value = BootRecord.last(getApplication())
        if (config.value.backgroundService) startService("screen")
        session.send(RecorderEvent.ScreenStarted)
        voice.open()
    }

    /** Screen went to the background: save the last known odometer once more, and stop any voice. */
    fun onScreenStop() {
        session.send(RecorderEvent.ScreenStopped)
        voice.close()
    }

    /** The purpose card now shows [trip]; [waitingIds] are the trips it will ask one by one. */
    fun onPurposeCardShown(trip: Trip, waitingIds: List<Long>) =
        voice.onCardShown(trip, waitingIds, System.currentTimeMillis())

    fun onPurposeCardHidden(tripId: Long) = voice.onCardHidden(tripId)

    fun setVoiceGuide(on: Boolean) {
        graph.display.setVoiceGuide(on)
        voice.refresh()
    }

    fun setVoiceAnswer(on: Boolean) {
        graph.display.setVoiceAnswer(on)
        voice.refresh()
    }

    fun choosePurpose(tripId: Long, purpose: Purpose) = session.send(RecorderEvent.ChoosePurpose(tripId, purpose))
    fun later(tripId: Long) = session.send(RecorderEvent.Later(tripId))
    fun endTripManually() = session.send(RecorderEvent.ManualEnd)
    fun resetRecords() = session.send(RecorderEvent.Reset)
    fun clearLog() = AppLog.clear()

    fun setShowDebugPanel(show: Boolean) = graph.display.setShowDebugPanel(show)

    /** 5-8: example trips for this month, marked "예시". */
    fun addSampleTrips() {
        viewModelScope.launch {
            val samples = SampleTrips.build(System.currentTimeMillis(), ZoneId.systemDefault())
            graph.db.tripDao().insertAll(samples)
            AppLog.i("Samples: 예시 기록 ${samples.size}건 추가")
        }
    }

    /** 5-8: removes every example trip at once; recorded trips stay. */
    fun clearSampleTrips() {
        viewModelScope.launch {
            val deleted = graph.db.tripDao().deleteSamples()
            AppLog.i("Samples: 예시 기록 ${deleted}건 삭제")
        }
    }

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
        voice.close()
        session.release(OWNER)
    }

    companion object {
        const val OWNER = "screen"
    }
}
