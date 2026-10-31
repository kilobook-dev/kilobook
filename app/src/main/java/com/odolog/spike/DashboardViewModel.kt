package com.odolog.spike

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.odolog.spike.vehicle.DashboardState
import com.odolog.spike.vehicle.VehicleMonitor
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the vehicle connection for the screen's whole life.
 * It survives the screen being covered (e.g. by the driving block screen) and is cleared
 * only when the screen is finished, which is when listeners are unregistered and the SDK released.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val monitor = VehicleMonitor(application)
    val state: StateFlow<DashboardState> = monitor.state

    init {
        monitor.start()
    }

    override fun onCleared() {
        monitor.stop()
    }
}
