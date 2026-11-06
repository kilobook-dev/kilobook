package com.kilobook.app.vehicle

import android.content.Context
import com.kilobook.app.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Combines the Pleos SDK and AAOS sources into one observable state. */
class VehicleMonitor(context: Context) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val update: StateUpdater = { change -> _state.update(change) }
    private val pleos = PleosVehicleSource(context, update, ::log)
    private val car = CarStandardSource(context, update, ::log)

    fun start() {
        pleos.start()
        car.start()
    }

    /** Unregisters every listener, then releases the SDK session and disconnects from the car service. */
    fun stop() {
        pleos.stop()
        car.stop()
    }

    private fun log(message: String) {
        AppLog.i(message)
    }
}
