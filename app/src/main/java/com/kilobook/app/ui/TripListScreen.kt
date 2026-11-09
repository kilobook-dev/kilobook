package com.kilobook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilobook.app.DashboardViewModel
import com.kilobook.app.data.Purpose
import com.kilobook.app.data.Trip
import com.kilobook.app.data.TripSource
import com.kilobook.app.data.TripStatus
import com.kilobook.app.ui.theme.KiloSize
import com.kilobook.app.ui.theme.KiloType
import java.time.LocalDate
import java.util.Locale

/** 5-4: trips grouped by the day they started, newest first. Unconfirmed trips can be answered here. */
@Composable
fun TripListScreen(viewModel: DashboardViewModel) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    var choosingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val currentKm = vehicle.odometerKm.value?.toDouble()
    // The DAO returns trips newest first, so the groups and the trips inside them keep that order.
    val days = remember(trips) { trips.groupBy { localDate(it.startTime) } }
    val awaitingCount = trips.count { it.status == TripStatus.AWAITING_PURPOSE }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(KiloSize.screenPadding),
        verticalArrangement = Arrangement.spacedBy(KiloSize.itemGap),
    ) {
        item {
            SectionTitle("주행 기록") {
                if (awaitingCount > 0) Tag("확인 대기 ${awaitingCount}건", MaterialTheme.colorScheme.primaryContainer)
            }
        }
        if (trips.isEmpty()) {
            item { Text("아직 기록이 없습니다. 차가 움직이면 자동으로 기록합니다.", style = KiloType.body) }
        }
        days.forEach { (date, dayTrips) ->
            item(key = "day-$date") { DayHeader(date, dayTrips) }
            items(dayTrips, key = { it.id }) { trip ->
                TripItem(
                    trip = trip,
                    currentKm = currentKm,
                    choosing = choosingId == trip.id,
                    onChoose = { choosingId = trip.id },
                    onCancel = { choosingId = null },
                    onPurpose = { purpose ->
                        viewModel.choosePurpose(trip.id, purpose)
                        choosingId = null
                    },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, trips: List<Trip>) {
    val ended = trips.filter { it.status != TripStatus.OPEN }
    val km = ended.sumOf { it.distanceKm(null) ?: 0.0 }
    Row(
        Modifier.fillMaxWidth().padding(top = KiloSize.itemGap),
        horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(dayHeaderText(date), style = KiloType.bodyStrong)
        Text("${trips.size}건 · ${kmText(km)}", style = KiloType.label, color = mutedColor())
    }
}

@Composable
private fun TripItem(
    trip: Trip,
    currentKm: Double?,
    choosing: Boolean,
    onChoose: () -> Unit,
    onCancel: () -> Unit,
    onPurpose: (Purpose) -> Unit,
) {
    KiloCard {
        Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.sectionGap), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tripTimeText(trip), style = KiloType.bodyStrong)
                    if (trip.source == TripSource.GAP) Tag("기록되지 않은 주행")
                    if (trip.isSample) Tag("예시", MaterialTheme.colorScheme.tertiaryContainer)
                }
                Text(odometerRange(trip), style = KiloType.labelSmall, color = mutedColor())
                if (trip.source == TripSource.GAP) {
                    Text("앱이 꺼져 있던 동안 늘어난 거리입니다.", style = KiloType.labelSmall, color = mutedColor())
                }
            }
            Text(
                kmText(trip.distanceKm(currentKm)),
                style = KiloType.title,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 180.dp),
            )
            Column(Modifier.widthIn(min = 220.dp), horizontalAlignment = Alignment.End) {
                when (trip.status) {
                    TripStatus.OPEN -> Tag("진행 중", MaterialTheme.colorScheme.primaryContainer)
                    TripStatus.DONE -> Text(purposeText(trip.purpose), style = KiloType.bodyStrong)
                    TripStatus.AWAITING_PURPOSE -> if (!choosing) {
                        BigButton("용도 고르기", height = KiloSize.buttonMedium, onClick = onChoose)
                    }
                }
            }
        }
        if (choosing && trip.status == TripStatus.AWAITING_PURPOSE) {
            Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap), modifier = Modifier.fillMaxWidth()) {
                BigButton("업무", Modifier.weight(1f), height = KiloSize.buttonMedium) { onPurpose(Purpose.BUSINESS) }
                BigButton("출퇴근", Modifier.weight(1f), height = KiloSize.buttonMedium) { onPurpose(Purpose.COMMUTE) }
                BigButton("개인", Modifier.weight(1f), height = KiloSize.buttonMedium) { onPurpose(Purpose.PERSONAL) }
                BigOutlinedButton("취소", Modifier.weight(1f), height = KiloSize.buttonMedium, onClick = onCancel)
            }
        }
    }
}

/** Dashboard odometer before and after, as on the paper log ("주행 전/후 계기판의 거리"). */
private fun odometerRange(trip: Trip): String {
    val start = String.format(Locale.KOREA, "%,.1f", trip.startOdometer)
    val end = trip.endOdometer?.let { String.format(Locale.KOREA, "%,.1f", it) } ?: "…"
    return "계기판 $start → $end km"
}
