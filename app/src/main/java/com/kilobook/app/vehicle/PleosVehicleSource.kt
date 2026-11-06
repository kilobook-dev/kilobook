package com.kilobook.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Odometer
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pleos Vehicle SDK session: odometer values and the (deprecated) Gear domain.
 *
 * Flow per value, as in the Pleos docs: checkXxxCapabilities -> getXxx -> registerXxx.
 * Results arrive on IPC callbacks; every outcome is written to the state unchanged.
 */
class PleosVehicleSource(
    context: Context,
    private val update: StateUpdater,
    private val log: (String) -> Unit,
) {
    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false

    /** False after stop(). Capability callbacks that arrive late must not register listeners on a released session. */
    @Volatile
    private var active = false

    /** Unregister actions for every listener that was registered. Run on stop(). */
    private val unregisterActions = CopyOnWriteArrayList<Pair<String, () -> Unit>>()

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            log("registerDistanceDriven -> onDistanceDrivenUpdated($value)")
            update { it.withOdometer(value, now()) }
        }

        override fun onFailed(e: Exception) {
            log("registerDistanceDriven -> onFailed(${sdkError(e)})")
            update { it.copy(odometerKm = it.odometerKm.copy(lastError = sdkError(e))) }
        }
    }

    private val tripDistanceListener = distanceListener("registerTripCurrentDrivingDistance") { s, r ->
        s.copy(tripDistanceM = r(s.tripDistanceM))
    }

    private val tripInstantListener = distanceListener("registerTripInstantaneousDrivingDistance") { s, r ->
        s.copy(tripInstantDistanceM = r(s.tripInstantDistanceM))
    }

    private val tripTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(value: Int?) {
            log("registerTripCurrentDrivingTime -> onDrivingTimeUpdated($value)")
            update { it.copy(tripTimeMin = it.tripTimeMin.copy(value = value, updatedAtMillis = now())) }
        }

        override fun onFailed(e: Exception) {
            log("registerTripCurrentDrivingTime -> onFailed(${sdkError(e)})")
            update { it.copy(tripTimeMin = it.tripTimeMin.copy(lastError = sdkError(e))) }
        }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            log("registerGearSelection -> onSelected(${gear?.name})")
            update { it.copy(pleosGear = it.pleosGear.copy(value = gear?.name, updatedAtMillis = now())) }
        }

        override fun onFailed(e: Exception) {
            log("registerGearSelection -> onFailed(${sdkError(e)})")
            update { it.copy(pleosGear = it.pleosGear.copy(lastError = sdkError(e))) }
        }
    }

    fun start() {
        try {
            vehicle.initialize()
            initialized = true
            active = true
            log("Vehicle.initialize() -> ok")
            update { it.copy(sdkSession = Capability.Supported("initialize 완료")) }
        } catch (e: Exception) {
            log("Vehicle.initialize() -> ${e.describe()}")
            update { it.copy(sdkSession = Capability.Failed(e.describe())) }
            return
        }

        val odometer = vehicle.getOdometer()
        watchOdometer(odometer)
        watchTripDistance(odometer)
        watchTripInstantDistance(odometer)
        watchTripTime(odometer)
        watchDeprecatedGear()
    }

    fun stop() {
        active = false
        unregisterActions.forEach { (name, action) ->
            try {
                action()
                log("$name -> ok")
            } catch (e: Exception) {
                log("$name -> ${e.describe()}")
            }
        }
        unregisterActions.clear()
        if (initialized) {
            try {
                vehicle.release()
                log("Vehicle.release() -> ok")
            } catch (e: Exception) {
                log("Vehicle.release() -> ${e.describe()}")
            }
            initialized = false
        }
    }

    // Total odometer (km)
    private fun watchOdometer(odometer: Odometer) {
        odometer.checkCurrentDistanceDrivenCapabilities(
            onComplete = { config ->
                log("checkCurrentDistanceDrivenCapabilities -> $config")
                update { it.copy(odometerKm = it.odometerKm.copy(capability = config.toCapability())) }
                if (config.accessType == AccessType.NONE) {
                    update { it.copy(odometerAtStart = it.odometerAtStart.copy(capability = config.toCapability())) }
                    return@checkCurrentDistanceDrivenCapabilities
                }
                if (!active) {
                    log("checkCurrentDistanceDrivenCapabilities -> stopped, listener not registered")
                    return@checkCurrentDistanceDrivenCapabilities
                }

                odometer.getCurrentDistanceDriven(
                    onComplete = { km ->
                        log("getCurrentDistanceDriven -> $km")
                        val at = now()
                        update {
                            it.withOdometer(km, at).copy(
                                odometerAtStart = it.odometerAtStart.copy(
                                    capability = if (km != null) Capability.Supported("getCurrentDistanceDriven") else Capability.Failed("값 없음 (null)"),
                                    value = km,
                                    updatedAtMillis = at,
                                ),
                            )
                        }
                    },
                    onFailed = { e ->
                        log("getCurrentDistanceDriven -> onFailed(${sdkError(e)})")
                        update {
                            it.copy(
                                odometerKm = it.odometerKm.copy(lastError = sdkError(e)),
                                odometerAtStart = it.odometerAtStart.copy(capability = Capability.Failed(sdkError(e))),
                            )
                        }
                    },
                )
                odometer.registerDistanceDriven(odometerListener)
                unregisterActions += "unregisterDistanceDriven" to { odometer.unregisterDistanceDriven(odometerListener) }
            },
            onFailed = { e ->
                log("checkCurrentDistanceDrivenCapabilities -> onFailed(${sdkError(e)})")
                update {
                    it.copy(
                        odometerKm = it.odometerKm.copy(capability = e.toCapability()),
                        odometerAtStart = it.odometerAtStart.copy(capability = e.toCapability()),
                    )
                }
            },
        )
    }

    // Trip distance of the current driving session (m)
    private fun watchTripDistance(odometer: Odometer) {
        odometer.checkTripCurrentDrivingDistanceCapabilities(
            onComplete = { config ->
                log("checkTripCurrentDrivingDistanceCapabilities -> $config")
                update { it.copy(tripDistanceM = it.tripDistanceM.copy(capability = config.toCapability())) }
                if (config.accessType == AccessType.NONE) return@checkTripCurrentDrivingDistanceCapabilities
                if (!active) {
                    log("checkTripCurrentDrivingDistanceCapabilities -> stopped, listener not registered")
                    return@checkTripCurrentDrivingDistanceCapabilities
                }

                odometer.getTripCurrentDrivingDistance(
                    onComplete = { m ->
                        log("getTripCurrentDrivingDistance -> $m")
                        update { it.copy(tripDistanceM = it.tripDistanceM.copy(value = m, updatedAtMillis = now())) }
                    },
                    onFailed = { e ->
                        log("getTripCurrentDrivingDistance -> onFailed(${sdkError(e)})")
                        update { it.copy(tripDistanceM = it.tripDistanceM.copy(lastError = sdkError(e))) }
                    },
                )
                odometer.registerTripCurrentDrivingDistance(tripDistanceListener)
                unregisterActions += "unregisterTripCurrentDrivingDistance" to { odometer.unregisterTripCurrentDrivingDistance(tripDistanceListener) }
            },
            onFailed = { e ->
                log("checkTripCurrentDrivingDistanceCapabilities -> onFailed(${sdkError(e)})")
                update { it.copy(tripDistanceM = it.tripDistanceM.copy(capability = e.toCapability())) }
            },
        )
    }

    // Instantaneous trip distance (m). Deprecated in SDK 2.3.2 ("uses TripStatisticType.INSTANTANEOUS")
    // and not listed in the sample app; checked here only to record its result.
    @Suppress("DEPRECATION")
    private fun watchTripInstantDistance(odometer: Odometer) {
        odometer.checkTripInstantaneousDrivingDistanceCapabilities(
            onComplete = { config ->
                log("checkTripInstantaneousDrivingDistanceCapabilities -> $config")
                update {
                    it.copy(tripInstantDistanceM = it.tripInstantDistanceM.copy(capability = config.toCapability()))
                }
                if (config.accessType == AccessType.NONE) return@checkTripInstantaneousDrivingDistanceCapabilities
                if (!active) {
                    log("checkTripInstantaneousDrivingDistanceCapabilities -> stopped, listener not registered")
                    return@checkTripInstantaneousDrivingDistanceCapabilities
                }

                odometer.getTripInstantaneousDrivingDistance(
                    onComplete = { m ->
                        log("getTripInstantaneousDrivingDistance -> $m")
                        update {
                            it.copy(tripInstantDistanceM = it.tripInstantDistanceM.copy(value = m, updatedAtMillis = now()))
                        }
                    },
                    onFailed = { e ->
                        log("getTripInstantaneousDrivingDistance -> onFailed(${sdkError(e)})")
                        update { it.copy(tripInstantDistanceM = it.tripInstantDistanceM.copy(lastError = sdkError(e))) }
                    },
                )
                odometer.registerTripInstantaneousDrivingDistance(tripInstantListener)
                unregisterActions += "unregisterTripInstantaneousDrivingDistance" to { odometer.unregisterTripInstantaneousDrivingDistance(tripInstantListener) }
            },
            onFailed = { e ->
                log("checkTripInstantaneousDrivingDistanceCapabilities -> onFailed(${sdkError(e)})")
                update { it.copy(tripInstantDistanceM = it.tripInstantDistanceM.copy(capability = e.toCapability())) }
            },
        )
    }

    // Trip driving time of the current driving session (minutes)
    private fun watchTripTime(odometer: Odometer) {
        odometer.checkTripCurrentDrivingTimeCapabilities(
            onComplete = { config ->
                log("checkTripCurrentDrivingTimeCapabilities -> $config")
                update { it.copy(tripTimeMin = it.tripTimeMin.copy(capability = config.toCapability())) }
                if (config.accessType == AccessType.NONE) return@checkTripCurrentDrivingTimeCapabilities
                if (!active) {
                    log("checkTripCurrentDrivingTimeCapabilities -> stopped, listener not registered")
                    return@checkTripCurrentDrivingTimeCapabilities
                }

                odometer.getTripCurrentDrivingTime(
                    onComplete = { min ->
                        log("getTripCurrentDrivingTime -> $min")
                        update { it.copy(tripTimeMin = it.tripTimeMin.copy(value = min, updatedAtMillis = now())) }
                    },
                    onFailed = { e ->
                        log("getTripCurrentDrivingTime -> onFailed(${sdkError(e)})")
                        update { it.copy(tripTimeMin = it.tripTimeMin.copy(lastError = sdkError(e))) }
                    },
                )
                odometer.registerTripCurrentDrivingTime(tripTimeListener)
                unregisterActions += "unregisterTripCurrentDrivingTime" to { odometer.unregisterTripCurrentDrivingTime(tripTimeListener) }
            },
            onFailed = { e ->
                log("checkTripCurrentDrivingTimeCapabilities -> onFailed(${sdkError(e)})")
                update { it.copy(tripTimeMin = it.tripTimeMin.copy(capability = e.toCapability())) }
            },
        )
    }

    // Gear domain exists in SDK 2.3.2 but is deprecated ("use the Google GEAR_SELECTION property")
    // and is not in the public docs. Read only for comparison with GEAR_SELECTION.
    @Suppress("DEPRECATION")
    private fun watchDeprecatedGear() {
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            onComplete = { value ->
                log("getCurrentGearSelectionState -> ${value?.name}")
                update {
                    it.copy(
                        pleosGear = it.pleosGear.copy(
                            capability = Capability.Supported("capability API 없음 · get 성공"),
                            value = value?.name,
                            updatedAtMillis = now(),
                        ),
                    )
                }
            },
            onFailed = { e ->
                log("getCurrentGearSelectionState -> onFailed(${sdkError(e)})")
                update { it.copy(pleosGear = it.pleosGear.copy(capability = e.toCapability())) }
            },
        )
        gear.registerGearSelection(gearListener)
        unregisterActions += "unregisterGearSelection" to { gear.unregisterGearSelection(gearListener) }
    }

    private fun distanceListener(
        api: String,
        put: (DashboardState, (Reading<Float>) -> Reading<Float>) -> DashboardState,
    ) = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            log("$api -> onDistanceDrivenUpdated($value)")
            update { s -> put(s) { it.copy(value = value, updatedAtMillis = now()) } }
        }

        override fun onFailed(e: Exception) {
            log("$api -> onFailed(${sdkError(e)})")
            update { s -> put(s) { it.copy(lastError = sdkError(e)) } }
        }
    }

    private fun CapabilityConfig.toCapability(): Capability =
        if (accessType == AccessType.NONE) Capability.Unsupported("accessType=NONE")
        else Capability.Supported("accessType=${accessType.name}")

    private fun Exception.toCapability(): Capability {
        val message = sdkError(this)
        return if (message.contains("NOT_SUPPORTED")) Capability.Unsupported(message)
        else Capability.Failed(message)
    }

    private fun now() = System.currentTimeMillis()
}

/** SDK exceptions are obfuscated classes; their message carries the error code (e.g. ERROR_NOT_SUPPORTED_PROPERTY). */
private fun sdkError(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: e.describe()
