package com.kilobook.app.ui

import com.kilobook.app.data.Purpose
import com.kilobook.app.data.Trip
import com.kilobook.app.data.TripSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Text formats for the driver screens. Times follow the device time zone at the moment of drawing. */

private val KOREAN = Locale.KOREA
private val TIME = DateTimeFormatter.ofPattern("a h:mm", KOREAN)
private val DAY_HEADER = DateTimeFormatter.ofPattern("M월 d일 EEEE", KOREAN)
private val SHORT_DAY = DateTimeFormatter.ofPattern("M월 d일", KOREAN)

fun zoned(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

fun localDate(millis: Long): LocalDate = zoned(millis).toLocalDate()

/** "12.3 km", "1,234.5 km"; "—" when unknown. */
fun kmText(km: Double?): String = km?.let { String.format(KOREAN, "%,.1f km", it) } ?: "—"

/** "64.8%"; "—" when there is nothing to divide by. */
fun percentText(ratio: Double?): String = ratio?.let { String.format(KOREAN, "%.1f%%", it * 100) } ?: "—"

/** "오후 2:10" */
fun timeText(millis: Long): String = zoned(millis).format(TIME)

/** "11월 9일 월요일" */
fun dayHeaderText(date: LocalDate): String = date.format(DAY_HEADER)

/** "11월 9일" */
fun shortDayText(millis: Long): String = zoned(millis).format(SHORT_DAY)

/**
 * "오후 2:10 ~ 오후 2:45"; the end gets its date when it is on another day.
 * A gap trip's real times are unknown, so it reads "… 사이" (somewhere between the two).
 */
fun tripTimeText(trip: Trip): String {
    val start = timeText(trip.startTime)
    val end = trip.endTime?.let { end ->
        if (localDate(end) == localDate(trip.startTime)) timeText(end) else "${shortDayText(end)} ${timeText(end)}"
    } ?: "진행 중"
    return if (trip.source == TripSource.GAP) "$start ~ $end 사이" else "$start ~ $end"
}

fun purposeText(purpose: Purpose): String = when (purpose) {
    Purpose.BUSINESS -> "업무"
    Purpose.COMMUTE -> "출퇴근"
    Purpose.PERSONAL -> "개인"
    Purpose.UNSET -> "용도 미확인"
}
