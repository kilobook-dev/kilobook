package com.kilobook.app.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * One month of trips in the terms of the National Tax Service log for business cars (업무용승용차 운행기록부):
 * ⑫ business use = ⑧ commute + ⑨ general business, ⑬ business use ratio = ⑫ ÷ ⑪ total distance.
 *
 * - A trip belongs to the month it started in.
 * - Trips without a purpose count in the total only, and are shown apart as unconfirmed distance.
 * - Open trips are left out until they end.
 */
data class MonthSummary(
    val month: YearMonth,
    val totalKm: Double = 0.0,
    val businessKm: Double = 0.0,
    val commuteKm: Double = 0.0,
    val personalKm: Double = 0.0,
    val unconfirmedKm: Double = 0.0,
    val tripCount: Int = 0,
    val unconfirmedCount: Int = 0,
    val sampleCount: Int = 0,
    val openTripCount: Int = 0,
) {
    /** ⑫ = ⑧ + ⑨ */
    val workKm: Double get() = businessKm + commuteKm

    /** ⑬ = ⑫ ÷ ⑪. Null while there is no distance (nothing to divide by). */
    val workRatio: Double? get() = if (totalKm > 0.0) workKm / totalKm else null

    companion object {
        fun of(trips: List<Trip>, month: YearMonth, zone: ZoneId): MonthSummary {
            val inMonth = trips.filter { YearMonth.from(Instant.ofEpochMilli(it.startTime).atZone(zone)) == month }
            var s = MonthSummary(month = month, openTripCount = inMonth.count { it.status == TripStatus.OPEN })
            for (trip in inMonth) {
                if (trip.status == TripStatus.OPEN) continue
                val km = (trip.distanceKm(null) ?: continue).coerceAtLeast(0.0)
                s = s.copy(
                    totalKm = s.totalKm + km,
                    tripCount = s.tripCount + 1,
                    sampleCount = s.sampleCount + if (trip.isSample) 1 else 0,
                )
                s = when (trip.purpose) {
                    Purpose.BUSINESS -> s.copy(businessKm = s.businessKm + km)
                    Purpose.COMMUTE -> s.copy(commuteKm = s.commuteKm + km)
                    Purpose.PERSONAL -> s.copy(personalKm = s.personalKm + km)
                    Purpose.UNSET -> s.copy(unconfirmedKm = s.unconfirmedKm + km, unconfirmedCount = s.unconfirmedCount + 1)
                }
            }
            return s
        }
    }
}
