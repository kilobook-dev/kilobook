package com.odolog.spike.vehicle

/**
 * Whether an API works on this vehicle. Shown on screen as-is:
 * Unsupported and errors are results too, so they are never replaced with other values.
 */
sealed interface Capability {
    data object Checking : Capability
    data class Supported(val detail: String) : Capability
    data class Unsupported(val reason: String) : Capability
    data class Failed(val message: String) : Capability
}

fun Capability.label(): String = when (this) {
    Capability.Checking -> "확인 중"
    is Capability.Supported -> "Supported · $detail"
    is Capability.Unsupported -> "Unsupported · $reason"
    is Capability.Failed -> "오류 · $message"
}

/** A value read through one API, plus that API's capability result. */
data class Reading<T>(
    val api: String,
    val capability: Capability = Capability.Checking,
    val value: T? = null,
    val updatedAtMillis: Long? = null,
    val lastError: String? = null,
)

data class UxState(
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
)

data class DashboardState(
    val sdkSession: Capability = Capability.Checking,
    val carSession: Capability = Capability.Checking,

    // Pleos Vehicle SDK (units from the Pleos API reference)
    val odometerKm: Reading<Float> = Reading("Odometer.getCurrentDistanceDriven / registerDistanceDriven"),
    /**
     * The getCurrentDistanceDriven result only, kept apart from callback values.
     * The recorder's startup checks use it, because the first registerDistanceDriven callback
     * after a restart can carry an old value.
     */
    val odometerAtStart: Reading<Float> = Reading("Odometer.getCurrentDistanceDriven (시작 기준)"),
    val tripDistanceM: Reading<Float> = Reading("Odometer.getTripCurrentDrivingDistance"),
    val tripInstantDistanceM: Reading<Float> = Reading("Odometer.getTripInstantaneousDrivingDistance"),
    val tripTimeMin: Reading<Int> = Reading("Odometer.getTripCurrentDrivingTime"),
    val pleosGear: Reading<String> = Reading("Gear.getCurrentGearSelectionState (SDK deprecated)"),

    // AAOS standard APIs
    val gearSelection: Reading<Int> = Reading("CarPropertyManager · GEAR_SELECTION"),
    val currentGear: Reading<Int> = Reading("CarPropertyManager · CURRENT_GEAR"),
    val vendorGear: Reading<Int> = Reading("CarPropertyManager · VENDOR_GEAR_SELECTION (0x21400400)"),
    val parkingBrake: Reading<Boolean> = Reading("CarPropertyManager · PARKING_BRAKE_ON"),
    val ignition: Reading<Int> = Reading("CarPropertyManager · IGNITION_STATE"),
    val uxRestrictions: Reading<UxState> = Reading("CarUxRestrictionsManager"),

    // App-calculated trip. Baseline = first odometer value after the screen opened.
    // Phase 3 replaces this with the stored Trip record.
    val tripBaselineKm: Float? = null,
    val tripStartedAtMillis: Long? = null,
)

/** Applies a new odometer value and updates the app-calculated trip. */
fun DashboardState.withOdometer(km: Float?, nowMillis: Long): DashboardState {
    val baseline = tripBaselineKm ?: km
    val started = tripStartedAtMillis
        ?: if (km != null && baseline != null && km > baseline) nowMillis else null
    return copy(
        odometerKm = odometerKm.copy(value = km, updatedAtMillis = nowMillis),
        tripBaselineKm = baseline,
        tripStartedAtMillis = started,
    )
}

typealias StateUpdater = ((DashboardState) -> DashboardState) -> Unit

fun Throwable.describe(): String {
    val name = this::class.java.simpleName.ifBlank { this::class.java.name }
    return if (message.isNullOrBlank()) name else "$name: $message"
}
