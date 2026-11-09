package com.kilobook.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Purpose { BUSINESS, COMMUTE, PERSONAL, UNSET }

enum class TripStatus { OPEN, AWAITING_PURPOSE, DONE }

/** DETECTED: recorded live from odometer changes. GAP: distance that grew while nothing was recording. */
enum class TripSource { DETECTED, GAP }

/** Which signal ended the trip. */
enum class EndReason {
    GEAR_PARK,          // GEAR_SELECTION read as P
    PARKING_BRAKE,      // PARKING_BRAKE_ON read as true
    ODOMETER_IDLE,      // fallback: odometer unchanged for N minutes
    MANUAL_BUTTON,      // fallback: "주행 종료" button
    STALE_ON_RESTART,   // app restarted long after the last known odometer (requirement A)
}

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startOdometer: Double,
    val endOdometer: Double? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val purpose: Purpose = Purpose.UNSET,
    val status: TripStatus,
    val source: TripSource,
    val askCount: Int = 0,
    val lastAskedAt: Long? = null,
    val endReason: EndReason? = null,
    /** Example trip from the debug panel (5-8). Shown as "예시" and deleted together. Added in DB version 2. */
    @ColumnInfo(defaultValue = "0") val isSample: Boolean = false,
) {
    fun distanceKm(currentOdometer: Double?): Double? =
        (endOdometer ?: currentOdometer)?.let { it - startOdometer }
}

/** Single row (id = 0): the last odometer value the app saw and when. */
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey val id: Int = 0,
    val lastKnownOdometer: Double?,
    val lastKnownAt: Long?,
)
