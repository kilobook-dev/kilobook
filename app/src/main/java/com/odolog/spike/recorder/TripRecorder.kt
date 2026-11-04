package com.odolog.spike.recorder

import com.odolog.spike.data.AppState
import com.odolog.spike.data.AppStateDao
import com.odolog.spike.data.EndReason
import com.odolog.spike.data.Purpose
import com.odolog.spike.data.Trip
import com.odolog.spike.data.TripDao
import com.odolog.spike.data.TripSource
import com.odolog.spike.data.TripStatus
import com.odolog.spike.vehicle.Capability
import com.odolog.spike.vehicle.DashboardState
import com.odolog.spike.vehicle.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RecorderEvent {
    /** The getCurrentDistanceDriven result when the session starts; km is null if it failed (result says why). */
    data class Startup(val km: Double?, val result: String) : RecorderEvent
    data class Odometer(val km: Double) : RecorderEvent
    data class Signals(val signals: EndSignals) : RecorderEvent
    data object SettingsChanged : RecorderEvent
    data object IdleTimeout : RecorderEvent
    data object ScreenStarted : RecorderEvent
    data object ScreenStopped : RecorderEvent
    data object ManualEnd : RecorderEvent
    data class ChoosePurpose(val tripId: Long, val purpose: Purpose) : RecorderEvent
    data class Later(val tripId: Long) : RecorderEvent
    data object Reset : RecorderEvent
}

/** The vehicle signals the trip rules depend on. */
data class EndSignals(
    val gear: Int? = null,
    val gearReadable: Boolean = false,
    val parkingBrake: Boolean? = null,
    val parkingBrakeReadable: Boolean = false,
    val requiresDistractionOptimization: Boolean? = null,
) {
    val gearPark: Boolean get() = gearReadable && gear == GEAR_PARK
    val parkingBrakeOn: Boolean get() = parkingBrakeReadable && parkingBrake == true

    /** Driving = driving restriction active, or gear readable and not P. */
    val driving: Boolean get() = requiresDistractionOptimization == true || (gearReadable && gear != GEAR_PARK)

    /** Stopped only when at least one signal says so; unknown counts as not stopped. */
    val stopped: Boolean get() = (requiresDistractionOptimization != null || gearReadable) && !driving

    companion object {
        const val GEAR_PARK = 0x4
    }
}

fun DashboardState.endSignals() = EndSignals(
    gear = gearSelection.value,
    gearReadable = gearSelection.capability is Capability.Supported && gearSelection.value != null,
    parkingBrake = parkingBrake.value,
    parkingBrakeReadable = parkingBrake.capability is Capability.Supported && parkingBrake.value != null,
    requiresDistractionOptimization = uxRestrictions.value?.requiresDistractionOptimization,
)

data class RecorderStatus(
    val running: Boolean = false,
    val openTripId: Long? = null,
    val fallbackActive: Boolean = false,
    val vehicleStopped: Boolean = false,
    val lastOdometerChangeAt: Long? = null,
    /** Trips the driver answered "나중에" to; hidden until the next opportunity. */
    val snoozedTripIds: Set<Long> = emptySet(),
    val lastDecision: String = "",
    /** Odometer readings lower than the current value. Ignored, but counted so they stay visible. */
    val ignoredDecreaseCount: Int = 0,
    val lastIgnoredDecrease: String? = null,
)

/**
 * Applies the trip rules. Every event goes through one channel and is handled in order,
 * so database reads and writes never race each other.
 */
class TripRecorder(
    private val trips: TripDao,
    private val appState: AppStateDao,
    private val settings: RecorderSettings,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val events = Channel<RecorderEvent>(Channel.UNLIMITED)
    private val _status = MutableStateFlow(RecorderStatus())
    val status: StateFlow<RecorderStatus> = _status.asStateFlow()

    // Touched only by the event loop.
    private lateinit var scope: CoroutineScope
    private var startupDone = false
    private var currentKm: Double? = null
    private var savedKm: Double? = null
    private var signals = EndSignals()
    private var lastChangeAt: Long? = null
    private var idleJob: Job? = null

    fun start(scope: CoroutineScope, vehicle: StateFlow<DashboardState>) {
        this.scope = scope
        _status.update { it.copy(running = true) }
        scope.launch { for (event in events) handleSafely(event) }
        scope.launch {
            // Startup checks (requirement A, rule 5) use getCurrentDistanceDriven, not the first callback:
            // after a restart the first registerDistanceDriven callback can carry an old value.
            val atStart = vehicle.map { it.odometerAtStart }.first { it.capability !is Capability.Checking }
            send(RecorderEvent.Startup(atStart.value?.toKm(), atStart.capability.label()))
            // Callback values only after the startup event, so the channel keeps them in order.
            vehicle.map { it.odometerKm.value }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { send(RecorderEvent.Odometer(it.toKm())) }
        }
        scope.launch {
            vehicle.map { it.endSignals() }.distinctUntilChanged().collect { send(RecorderEvent.Signals(it)) }
        }
        scope.launch {
            settings.state.drop(1).collect { send(RecorderEvent.SettingsChanged) }
        }
    }

    fun send(event: RecorderEvent) {
        events.trySend(event)
    }

    private suspend fun handleSafely(event: RecorderEvent) {
        try {
            handle(event)
        } catch (e: Exception) {
            decide("오류: $event -> ${e::class.java.simpleName}: ${e.message}")
        }
        refreshStatus()
    }

    private suspend fun handle(event: RecorderEvent) {
        val now = clock()
        when (event) {
            is RecorderEvent.Startup -> onStartup(event, now)
            is RecorderEvent.Odometer -> onOdometer(event.km, now)
            is RecorderEvent.Signals -> onSignals(event.signals, now)
            RecorderEvent.SettingsChanged -> {
                decide("설정 변경: ${settings.state.value}")
                evaluateEnd(now)
                scheduleIdleCheck()
            }
            RecorderEvent.IdleTimeout -> onIdleTimeout(now)
            RecorderEvent.ScreenStarted -> {
                clearSnooze("화면이 다시 보임")
                // Rule 5 on return: compare the current value with the saved one.
                currentKm?.let { if (startupDone) detectGap(it, now) }
            }
            RecorderEvent.ScreenStopped -> currentKm?.let { saveLastKnown(it, now, forcedBy = "화면 숨김") }
            RecorderEvent.ManualEnd -> {
                val open = trips.getOpen()
                if (open == null) decide("주행 종료 버튼: 열린 주행 없음")
                else closeTrip(open, currentKm ?: open.startOdometer, now, EndReason.MANUAL_BUTTON)
            }
            is RecorderEvent.ChoosePurpose -> {
                val trip = trips.get(event.tripId) ?: return
                trips.update(trip.copy(purpose = event.purpose, status = TripStatus.DONE))
                _status.update { it.copy(snoozedTripIds = it.snoozedTripIds - trip.id) }
                decide("용도 선택 #${trip.id}: ${event.purpose}")
            }
            is RecorderEvent.Later -> {
                val trip = trips.get(event.tripId) ?: return
                trips.update(trip.copy(askCount = trip.askCount + 1, lastAskedAt = now))
                _status.update { it.copy(snoozedTripIds = it.snoozedTripIds + trip.id) }
                decide("나중에 #${trip.id}: askCount ${trip.askCount + 1}")
            }
            RecorderEvent.Reset -> {
                trips.deleteAll()
                appState.clear()
                savedKm = null
                idleJob?.cancel()
                _status.update { it.copy(snoozedTripIds = emptySet()) }
                decide("기록 초기화")
                currentKm?.let { saveLastKnown(it, now, forcedBy = "초기화") }
            }
        }
    }

    private suspend fun onStartup(event: RecorderEvent.Startup, now: Long) {
        startup(event.km, event.result, now)
        startupDone = true
        event.km?.let {
            currentKm = it
            saveLastKnown(it, now)
        }
        evaluateEnd(now)
        scheduleIdleCheck()
    }

    // Rules 1, 6
    private suspend fun onOdometer(km: Double, now: Long) {
        if (!startupDone) return // not sent before Startup; kept as a guard

        val previous = currentKm
        if (previous != null && km > previous + EPSILON) {
            if (trips.getOpen() == null) {
                // Rule 1: start = the value just before the increase.
                val id = trips.insert(
                    Trip(
                        startOdometer = previous,
                        startTime = now,
                        status = TripStatus.OPEN,
                        source = TripSource.DETECTED,
                    ),
                )
                decide("주행 시작 #$id: 총 주행거리 $previous → $km km")
            }
            lastChangeAt = now
        } else if (previous != null && km < previous - EPSILON) {
            // An odometer never goes down, so a lower reading is not used: no trip start, no save.
            // Seen on the emulator: the first registerDistanceDriven callback after a restart carries an old value.
            val text = "$previous → $km km (${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(now))})"
            _status.update { it.copy(ignoredDecreaseCount = it.ignoredDecreaseCount + 1, lastIgnoredDecrease = text) }
            decide("총 주행거리 감소 값 무시: $text")
            return
        }
        currentKm = km
        saveLastKnown(km, now)
        evaluateEnd(now)
        scheduleIdleCheck()
    }

    /** Session start (app start, service start, boot): requirement A and rule 5 against getCurrentDistanceDriven. */
    private suspend fun startup(km: Double?, result: String, now: Long) {
        val state = appState.get()
        savedKm = state?.lastKnownOdometer
        val lastKm = state?.lastKnownOdometer
        val lastAt = state?.lastKnownAt
        decide("시작: getCurrentDistanceDriven ${km?.let { "$it km" } ?: "없음 ($result)"}, 마지막 기록 ${lastKm?.let { "$it km" } ?: "없음"}")
        if (km != null && lastKm != null && km < lastKm - EPSILON) {
            // Only at startup: a lower value becomes the new baseline (e.g. the emulator odometer reset to 0 on reboot).
            decide("시작 값($km km)이 마지막 기록($lastKm km)보다 작음. 현재 값을 새 기준으로 사용")
        }

        val open = trips.getOpen()
        if (open != null) {
            val staleMillis = settings.state.value.staleMinutes * 60_000L
            if (lastAt == null || now - lastAt >= staleMillis) {
                // Requirement A: end the old trip where the app last saw the car, so it does not swallow later drives.
                closeTrip(open, lastKm ?: open.startOdometer, lastAt ?: now, EndReason.STALE_ON_RESTART)
            } else {
                lastChangeAt = now
                decide("열린 주행 #${open.id} 이어서 기록 (마지막 확인 ${(now - lastAt) / 1000}초 전)")
                return
            }
        }
        if (km == null) {
            // No start value: skip gap detection rather than guess from a callback value. The first callback becomes the baseline.
            decide("누락 감지 건너뜀: 시작 기준 값 없음 ($result)")
            return
        }
        detectGap(km, now)
    }

    /** Rule 5: distance that grew while nothing was recording becomes a GAP trip. */
    private suspend fun detectGap(km: Double, now: Long) {
        if (trips.getOpen() != null) return
        val state = appState.get() ?: return
        val lastKm = state.lastKnownOdometer ?: return
        val delta = km - lastKm
        if (delta >= GAP_MIN_KM - EPSILON) {
            val id = trips.insert(
                Trip(
                    startOdometer = lastKm,
                    endOdometer = km,
                    startTime = state.lastKnownAt ?: now,
                    endTime = now,
                    status = TripStatus.AWAITING_PURPOSE,
                    source = TripSource.GAP,
                ),
            )
            decide("누락 감지 #$id: 기록되지 않은 주행 ${"%.1f".format(delta)} km ($lastKm → $km)")
        } else if (delta > EPSILON) {
            decide("누락 감지: ${"%.2f".format(delta)} km 증가는 기준(0.1 km) 미만이라 무시")
        }
    }

    // Rule 2
    private suspend fun onSignals(next: EndSignals, now: Long) {
        val wasStopped = signals.stopped
        signals = next
        if (!wasStopped && next.stopped) clearSnooze("정차로 바뀜")
        evaluateEnd(now)
        scheduleIdleCheck()
    }

    private suspend fun evaluateEnd(now: Long) {
        // Before the first odometer value, requirement A has not run yet and the end point is unknown.
        if (!startupDone || fallbackActive()) return
        val open = trips.getOpen() ?: return
        val endKm = currentKm ?: open.startOdometer
        when {
            signals.gearPark -> closeTrip(open, endKm, now, EndReason.GEAR_PARK)
            signals.parkingBrakeOn -> closeTrip(open, endKm, now, EndReason.PARKING_BRAKE)
        }
    }

    /** Fallback when neither gear nor parking brake can be read (or forced for testing). */
    private fun fallbackActive(): Boolean =
        settings.state.value.forceFallback || (!signals.gearReadable && !signals.parkingBrakeReadable)

    /** One delayed check per odometer change instead of polling. */
    private suspend fun scheduleIdleCheck() {
        idleJob?.cancel()
        idleJob = null
        if (!startupDone || !fallbackActive() || trips.getOpen() == null) return
        val since = lastChangeAt ?: clock().also { lastChangeAt = it }
        val due = since + settings.state.value.idleMinutes * 60_000L
        idleJob = scope.launch {
            delay((due - clock()).coerceAtLeast(0))
            send(RecorderEvent.IdleTimeout)
        }
    }

    private suspend fun onIdleTimeout(now: Long) {
        if (!fallbackActive()) return
        val open = trips.getOpen() ?: return
        val since = lastChangeAt ?: return
        if (now - since >= settings.state.value.idleMinutes * 60_000L - 500) {
            closeTrip(open, currentKm ?: open.startOdometer, since, EndReason.ODOMETER_IDLE)
        } else {
            scheduleIdleCheck()
        }
    }

    // Rule 3
    private suspend fun closeTrip(trip: Trip, endKm: Double, endAt: Long, reason: EndReason) {
        trips.update(
            trip.copy(
                endOdometer = endKm,
                endTime = endAt,
                status = TripStatus.AWAITING_PURPOSE,
                endReason = reason,
            ),
        )
        idleJob?.cancel()
        decide("주행 종료 #${trip.id} ($reason): ${trip.startOdometer} → $endKm km")
    }

    /** Rule 6 (amended): save only when the value changes, or when forced (screen going to background). */
    private suspend fun saveLastKnown(km: Double, now: Long, forcedBy: String? = null) {
        if (forcedBy == null && savedKm == km) return
        appState.upsert(AppState(lastKnownOdometer = km, lastKnownAt = now))
        savedKm = km
        log("Recorder: lastKnownOdometer=$km 저장${forcedBy?.let { " ($it)" } ?: ""}")
    }

    private fun clearSnooze(reason: String) {
        if (_status.value.snoozedTripIds.isEmpty()) return
        _status.update { it.copy(snoozedTripIds = emptySet()) }
        decide("다시 물을 기회: $reason")
    }

    private suspend fun refreshStatus() {
        val open = trips.getOpen()
        _status.update {
            it.copy(
                openTripId = open?.id,
                fallbackActive = fallbackActive(),
                vehicleStopped = signals.stopped,
                lastOdometerChangeAt = lastChangeAt,
            )
        }
    }

    private fun decide(message: String) {
        log("Recorder: $message")
        _status.update { it.copy(lastDecision = message) }
    }

    companion object {
        const val GAP_MIN_KM = 0.1
        private const val EPSILON = 1e-6
    }
}

/** Float -> shortest decimal text -> Double, so 12.3f stays 12.3 instead of 12.30000019. */
private fun Float.toKm(): Double = toString().toDouble()
