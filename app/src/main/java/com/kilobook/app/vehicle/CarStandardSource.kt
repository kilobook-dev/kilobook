// CarPropertyValue.status and register/unregisterCallback are deprecated in the API 35 car library,
// but they are the API 34 way and this emulator runs Android 14 (API 34).
@file:Suppress("DEPRECATION")

package com.kilobook.app.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/**
 * Android Automotive standard APIs for values the Pleos Vehicle SDK does not document:
 * gear, parking brake, ignition (CarPropertyManager) and driving restrictions (CarUxRestrictionsManager).
 */
class CarStandardSource(
    context: Context,
    private val update: StateUpdater,
    private val log: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private val propertyCallbacks = mutableListOf<CarPropertyManager.CarPropertyEventCallback>()

    fun start() {
        try {
            car = Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (ready) onCarReady(connected) else onCarLost()
            }
            log("Car.createCar() -> requested")
        } catch (e: Exception) {
            log("Car.createCar() -> ${e.describe()}")
            update { it.copy(carSession = Capability.Failed(e.describe())) }
        }
    }

    fun stop() {
        clearSubscriptions()
        car?.disconnect()
        car = null
        log("Car.disconnect() -> ok")
    }

    private fun onCarReady(car: Car) {
        clearSubscriptions()
        log("Car service ready")
        update { it.copy(carSession = Capability.Supported("연결됨")) }

        val pm = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (pm == null) {
            log("getCarManager(PROPERTY_SERVICE) -> null")
        } else {
            propertyManager = pm
            watchProperty(pm, VehiclePropertyIds.GEAR_SELECTION, "GEAR_SELECTION", ::asInt,
                { it.gearSelection }, { s, r -> s.copy(gearSelection = r) })
            watchProperty(pm, VehiclePropertyIds.CURRENT_GEAR, "CURRENT_GEAR", ::asInt,
                { it.currentGear }, { s, r -> s.copy(currentGear = r) })
            watchProperty(pm, VENDOR_GEAR_SELECTION, "VENDOR_GEAR_SELECTION", ::asInt,
                { it.vendorGear }, { s, r -> s.copy(vendorGear = r) })
            watchProperty(pm, VehiclePropertyIds.PARKING_BRAKE_ON, "PARKING_BRAKE_ON", ::asBoolean,
                { it.parkingBrake }, { s, r -> s.copy(parkingBrake = r) })
            watchProperty(pm, VehiclePropertyIds.IGNITION_STATE, "IGNITION_STATE", ::asInt,
                { it.ignition }, { s, r -> s.copy(ignition = r) })
        }

        watchUxRestrictions(car)
    }

    private fun onCarLost() {
        log("Car service disconnected")
        clearSubscriptions()
        update { it.copy(carSession = Capability.Failed("Car service 연결 끊김")) }
    }

    /**
     * Records capability, the current value, and change events of one property.
     * A missing config means "unsupported or no permission"; the getProperty exception is kept to tell which.
     */
    private fun <T> watchProperty(
        pm: CarPropertyManager,
        propId: Int,
        name: String,
        parse: (Any?) -> T?,
        select: (DashboardState) -> Reading<T>,
        put: (DashboardState, Reading<T>) -> DashboardState,
    ) {
        fun set(change: (Reading<T>) -> Reading<T>) = update { s -> put(s, change(select(s))) }

        val config = try {
            pm.getCarPropertyConfig(propId)
        } catch (e: Exception) {
            log("getCarPropertyConfig($name) -> ${e.describe()}")
            set { it.copy(capability = Capability.Failed(e.describe())) }
            return
        }

        if (config == null) {
            val probe = try {
                val value = pm.getProperty<Any?>(propId, 0)
                "getProperty -> status=${statusName(value.status)} value=${value.value}"
            } catch (e: Exception) {
                "getProperty -> ${e.describe()}"
            }
            log("getCarPropertyConfig($name) -> null; $probe")
            set { it.copy(capability = Capability.Unsupported("config 없음 (미지원 또는 권한 없음) · $probe")) }
            return
        }

        log("getCarPropertyConfig($name) -> access=${accessName(config.access)}")
        set { it.copy(capability = Capability.Supported("access=${accessName(config.access)}")) }

        try {
            val value = pm.getProperty<Any?>(propId, 0)
            log("getProperty($name) -> status=${statusName(value.status)} value=${value.value}")
            set {
                it.copy(
                    value = parse(value.value),
                    updatedAtMillis = System.currentTimeMillis(),
                    lastError = statusError(value.status),
                )
            }
        } catch (e: Exception) {
            log("getProperty($name) -> ${e.describe()}")
            set { it.copy(lastError = "getProperty: ${e.describe()}") }
        }

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                log("onChangeEvent($name) -> status=${statusName(value.status)} value=${value.value}")
                set {
                    it.copy(
                        value = parse(value.value),
                        updatedAtMillis = System.currentTimeMillis(),
                        lastError = statusError(value.status),
                    )
                }
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                log("onErrorEvent($name) area=$areaId")
                set { it.copy(lastError = "onErrorEvent area=$areaId") }
            }
        }

        val registered = try {
            pm.registerCallback(callback, propId, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Exception) {
            log("registerCallback($name) -> ${e.describe()}")
            set { it.copy(lastError = "registerCallback: ${e.describe()}") }
            return
        }
        log("registerCallback($name) -> $registered")
        if (registered) propertyCallbacks += callback
        else set { it.copy(lastError = "registerCallback=false") }
    }

    private fun watchUxRestrictions(car: Car) {
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (manager == null) {
            log("getCarManager(CAR_UX_RESTRICTION_SERVICE) -> null")
            update { it.copy(uxRestrictions = it.uxRestrictions.copy(capability = Capability.Unsupported("manager null"))) }
            return
        }
        try {
            val current = manager.currentCarUxRestrictions
            log("getCurrentCarUxRestrictions -> ${current.describeUx()}")
            update {
                it.copy(
                    uxRestrictions = it.uxRestrictions.copy(
                        capability = Capability.Supported("registerListener"),
                        value = current?.toUxState(),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            manager.registerListener { restrictions ->
                log("onUxRestrictionsChanged -> ${restrictions.describeUx()}")
                update {
                    it.copy(
                        uxRestrictions = it.uxRestrictions.copy(
                            value = restrictions?.toUxState(),
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            uxManager = manager
        } catch (e: Exception) {
            log("CarUxRestrictionsManager -> ${e.describe()}")
            update { it.copy(uxRestrictions = it.uxRestrictions.copy(capability = Capability.Failed(e.describe()))) }
        }
    }

    private fun clearSubscriptions() {
        val pm = propertyManager
        val count = propertyCallbacks.size
        propertyCallbacks.forEach { callback ->
            try {
                pm?.unregisterCallback(callback)
            } catch (e: Exception) {
                log("unregisterCallback -> ${e.describe()}")
            }
        }
        propertyCallbacks.clear()
        if (count > 0) log("CarPropertyManager.unregisterCallback x$count -> done")
        try {
            uxManager?.let {
                it.unregisterListener()
                log("CarUxRestrictionsManager.unregisterListener -> ok")
            }
        } catch (e: Exception) {
            log("unregisterListener -> ${e.describe()}")
        }
        uxManager = null
        propertyManager = null
    }

    companion object {
        /** Hyundai vendor gear property the Virtual Console publishes (from its /vehicle/catalog). */
        const val VENDOR_GEAR_SELECTION = 0x21400400
    }
}

private fun asInt(value: Any?): Int? = value as? Int
private fun asBoolean(value: Any?): Boolean? = value as? Boolean

private fun CarUxRestrictions.toUxState() = UxState(isRequiresDistractionOptimization, activeRestrictions)

private fun CarUxRestrictions?.describeUx(): String =
    if (this == null) "null"
    else "requiresDO=$isRequiresDistractionOptimization active=0x${activeRestrictions.toString(16)}"

private fun accessName(access: Int) = when (access) {
    0 -> "NONE"
    1 -> "READ"
    2 -> "WRITE"
    3 -> "READ_WRITE"
    else -> access.toString()
}

private fun statusName(status: Int) = when (status) {
    CarPropertyValue.STATUS_AVAILABLE -> "AVAILABLE"
    CarPropertyValue.STATUS_UNAVAILABLE -> "UNAVAILABLE"
    CarPropertyValue.STATUS_ERROR -> "ERROR"
    else -> status.toString()
}

private fun statusError(status: Int): String? =
    if (status == CarPropertyValue.STATUS_AVAILABLE) null else "status=${statusName(status)}"
