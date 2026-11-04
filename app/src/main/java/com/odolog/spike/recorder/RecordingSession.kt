package com.odolog.spike.recorder

import android.content.Context
import androidx.annotation.MainThread
import com.odolog.spike.AppLog
import com.odolog.spike.data.OdologDatabase
import com.odolog.spike.vehicle.Capability
import com.odolog.spike.vehicle.DashboardState
import com.odolog.spike.vehicle.VehicleMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The vehicle connection + trip recorder, shared by everything that needs it.
 *
 * Owners: the screen (while it exists) and the foreground service (while it runs).
 * The connection starts when the first owner acquires it and is released when the last owner lets go,
 * so closing the screen does not stop recording while the service is running.
 */
class RecordingSession(
    private val context: Context,
    private val db: OdologDatabase,
    private val settings: RecorderSettings,
) {
    private val owners = mutableSetOf<String>()
    private val _owners = MutableStateFlow<Set<String>>(emptySet())
    val activeOwners: StateFlow<Set<String>> = _owners.asStateFlow()

    private val _vehicle = MutableStateFlow(stoppedState())
    val vehicle: StateFlow<DashboardState> = _vehicle.asStateFlow()

    private val _recorder = MutableStateFlow(RecorderStatus())
    val recorder: StateFlow<RecorderStatus> = _recorder.asStateFlow()

    private var scope: CoroutineScope? = null
    private var monitor: VehicleMonitor? = null
    private var tripRecorder: TripRecorder? = null

    @MainThread
    fun acquire(owner: String) {
        if (!owners.add(owner)) return
        log("Session: acquire($owner) -> owners=$owners")
        _owners.value = owners.toSet()
        if (owners.size == 1) start()
    }

    @MainThread
    fun release(owner: String) {
        if (!owners.remove(owner)) return
        log("Session: release($owner) -> owners=$owners")
        _owners.value = owners.toSet()
        if (owners.isEmpty()) stop()
    }

    fun send(event: RecorderEvent) {
        tripRecorder?.send(event)
    }

    private fun start() {
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vehicleMonitor = VehicleMonitor(context)
        val recorder = TripRecorder(db.tripDao(), db.appStateDao(), settings, ::log)

        sessionScope.launch { vehicleMonitor.state.collect { _vehicle.value = it } }
        sessionScope.launch { recorder.status.collect { _recorder.value = it } }
        recorder.start(sessionScope, vehicleMonitor.state)
        vehicleMonitor.start()

        scope = sessionScope
        monitor = vehicleMonitor
        tripRecorder = recorder
        log("Session: started")
    }

    private fun stop() {
        monitor?.stop()
        scope?.cancel()
        monitor = null
        scope = null
        tripRecorder = null
        _vehicle.value = stoppedState()
        _recorder.value = RecorderStatus()
        log("Session: stopped")
    }

    private fun stoppedState() = DashboardState(
        sdkSession = Capability.Failed("기록 세션 꺼짐"),
        carSession = Capability.Failed("기록 세션 꺼짐"),
    )

    private fun log(message: String) {
        AppLog.i(message)
    }
}
