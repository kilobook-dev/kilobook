package com.kilobook.app.data

import java.time.Instant
import java.time.ZoneId

/**
 * Example trips for the debug panel (5-8): a filled month for demos and screenshots.
 * Every trip is marked isSample, shown as "예시", and removed together by TripDao.deleteSamples().
 */
object SampleTrips {
    private data class Spec(
        val hoursAgo: Int,
        val minutes: Int,
        val km: Double,
        val purpose: Purpose,
        val source: TripSource = TripSource.DETECTED,
    )

    private val specs = listOf(
        Spec(hoursAgo = 150, minutes = 35, km = 18.4, purpose = Purpose.BUSINESS),
        Spec(hoursAgo = 128, minutes = 28, km = 12.7, purpose = Purpose.COMMUTE),
        Spec(hoursAgo = 101, minutes = 15, km = 6.2, purpose = Purpose.PERSONAL),
        Spec(hoursAgo = 77, minutes = 52, km = 32.5, purpose = Purpose.BUSINESS),
        Spec(hoursAgo = 54, minutes = 27, km = 12.9, purpose = Purpose.COMMUTE),
        // Distance that grew while the app was not recording: start/end are the last-seen and next-seen times.
        Spec(hoursAgo = 30, minutes = 240, km = 4.3, purpose = Purpose.UNSET, source = TripSource.GAP),
        // The most recent one waits for a purpose, so the home card has something to ask.
        Spec(hoursAgo = 2, minutes = 22, km = 9.8, purpose = Purpose.UNSET),
    )

    val count: Int get() = specs.size

    /** Trips spread over the last week but never before the first day of this month. */
    fun build(now: Long, zone: ZoneId): List<Trip> {
        val monthStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        var odometer = 1_000.0
        return specs.mapIndexed { index, spec ->
            val start = maxOf(now - spec.hoursAgo * HOUR, monthStart + index * 10 * MINUTE)
            val end = minOf(start + spec.minutes * MINUTE, now)
            val startKm = odometer
            odometer = round1(odometer + spec.km)
            Trip(
                startOdometer = startKm,
                endOdometer = odometer,
                startTime = start,
                endTime = end,
                purpose = spec.purpose,
                status = if (spec.purpose == Purpose.UNSET) TripStatus.AWAITING_PURPOSE else TripStatus.DONE,
                source = spec.source,
                endReason = if (spec.source == TripSource.GAP) null else EndReason.GEAR_PARK,
                isSample = true,
            )
        }
    }

    private fun round1(value: Double) = Math.round(value * 10) / 10.0

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}
