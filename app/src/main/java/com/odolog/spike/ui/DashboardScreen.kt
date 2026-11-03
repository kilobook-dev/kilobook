package com.odolog.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odolog.spike.DashboardViewModel
import com.odolog.spike.data.EndReason
import com.odolog.spike.data.Purpose
import com.odolog.spike.data.Trip
import com.odolog.spike.data.TripSource
import com.odolog.spike.data.TripStatus
import com.odolog.spike.recorder.RecorderConfig
import com.odolog.spike.recorder.RecorderStatus
import com.odolog.spike.vehicle.DashboardState
import com.odolog.spike.vehicle.Reading
import com.odolog.spike.vehicle.label
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BUTTON_HEIGHT = 72.dp

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val recorder by viewModel.recorder.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val owners by viewModel.owners.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    val openTrip = trips.firstOrNull { it.status == TripStatus.OPEN }
    val awaiting = trips.filter { it.status == TripStatus.AWAITING_PURPOSE }
    // Rule 4: ask only while stopped, and skip trips answered "나중에" until the next opportunity.
    val toAsk = if (recorder.vehicleStopped) awaiting.filter { it.id !in recorder.snoozedTripIds } else emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "오도로그 스파이크",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item { LivePanel(vehicle, openTrip, now) }

        if (recorder.fallbackActive && openTrip != null) {
            item {
                Button(
                    onClick = viewModel::endTripManually,
                    modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
                ) { Text("주행 종료", fontSize = 26.sp) }
            }
        }

        if (toAsk.isNotEmpty()) {
            item {
                AwaitingCard(
                    trips = toAsk,
                    onPurpose = viewModel::choosePurpose,
                    onLater = viewModel::later,
                )
            }
        }

        item { Text("주행 기록 ${trips.size}건 (최신순)", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (trips.isEmpty()) item { Text("아직 기록이 없습니다", fontSize = 16.sp) }
        items(trips, key = { it.id }) { trip -> TripRow(trip, vehicle.odometerKm.value?.toDouble()) }

        item {
            RecorderPanel(
                recorder = recorder,
                config = config,
                owners = owners,
                awaitingCount = awaiting.size,
                hiddenWhileDriving = awaiting.isNotEmpty() && !recorder.vehicleStopped,
                onConfig = viewModel::updateConfig,
                onReset = viewModel::resetRecords,
            )
        }
    }
}

// ---------- Live panel (Phase 2, compact) ----------

@Composable
private fun LivePanel(state: DashboardState, openTrip: Trip?, now: Long) {
    Panel {
        Text(
            "Vehicle SDK: ${state.sdkSession.label()}    Car service: ${state.carSession.label()}",
            fontSize = 14.sp,
        )
        LiveRow(
            title = "총 주행거리",
            value = state.odometerKm.value?.let { "%.1f km".format(it) } ?: "—",
            details = listOf(state.odometerKm.statusLine()),
        )
        LiveRow(
            title = "트립 주행거리",
            value = state.tripDistanceM.value?.let { "%.0f m".format(it) } ?: "—",
            appValue = openTrip?.let { trip ->
                state.odometerKm.value?.let { "%.1f km".format(it - trip.startOdometer) }
            } ?: "주행 중 아님",
            details = listOf(state.tripDistanceM.statusLine(), state.tripInstantDistanceM.statusLine()),
        )
        LiveRow(
            title = "트립 주행시간",
            value = state.tripTimeMin.value?.let { "${it}분" } ?: "—",
            appValue = openTrip?.let { elapsed(now - it.startTime) } ?: "주행 중 아님",
            details = listOf(state.tripTimeMin.statusLine()),
        )
        val gear = gearDisplay(state)
        LiveRow(
            title = "기어",
            value = gear.first,
            details = listOf(
                state.gearSelection.statusLine(),
                "비교 · CURRENT_GEAR: ${state.currentGear.value?.let(::gearName) ?: state.currentGear.capability.label()}" +
                    " · VENDOR: ${state.vendorGear.value?.let(::gearName) ?: state.vendorGear.capability.label()}" +
                    " · Pleos Gear(deprecated): ${state.pleosGear.value ?: state.pleosGear.capability.label()}",
            ),
        )
        LiveRow(
            title = "주차 브레이크",
            value = when (state.parkingBrake.value) {
                true -> "ON"
                false -> "OFF"
                null -> "—"
            },
            details = listOf(state.parkingBrake.statusLine()),
        )
        val ux = state.uxRestrictions.value
        LiveRow(
            title = "주행 규제",
            value = when (ux?.requiresDistractionOptimization) {
                true -> "주행"
                false -> "정차"
                null -> "—"
            },
            details = listOf(
                state.uxRestrictions.statusLine() +
                    (ux?.let { "  · activeRestrictions=0x${it.activeRestrictions.toString(16)}" } ?: ""),
            ),
        )
        LiveRow(
            title = "시동 (참고)",
            value = state.ignition.value?.let(::ignitionName) ?: "—",
            details = listOf(state.ignition.statusLine()),
        )
    }
}

@Composable
private fun LiveRow(title: String, value: String, details: List<String>, appValue: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(title, fontSize = 18.sp, modifier = Modifier.width(150.dp))
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(220.dp))
        Column(Modifier.width(230.dp)) {
            if (appValue != null) {
                Text("앱 계산 · 열린 주행 기준", fontSize = 12.sp)
                Text(appValue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            details.forEach { Text(it, fontSize = 12.sp, maxLines = 2) }
        }
    }
}

// ---------- Purpose card (rules 3 and 4) ----------

@Composable
private fun AwaitingCard(
    trips: List<Trip>,
    onPurpose: (Long, Purpose) -> Unit,
    onLater: (Long) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("용도 확인이 필요한 주행 ${trips.size}건", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            trips.forEach { trip ->
                Text(tripSummary(trip), fontSize = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PurposeButton("업무", Modifier.weight(1f)) { onPurpose(trip.id, Purpose.BUSINESS) }
                    PurposeButton("출퇴근", Modifier.weight(1f)) { onPurpose(trip.id, Purpose.COMMUTE) }
                    PurposeButton("개인", Modifier.weight(1f)) { onPurpose(trip.id, Purpose.PERSONAL) }
                    OutlinedButton(
                        onClick = { onLater(trip.id) },
                        modifier = Modifier.weight(1f).height(BUTTON_HEIGHT),
                    ) { Text("나중에", fontSize = 22.sp) }
                }
            }
        }
    }
}

@Composable
private fun PurposeButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(BUTTON_HEIGHT)) { Text(label, fontSize = 22.sp) }
}

private fun tripSummary(trip: Trip): String {
    val km = trip.distanceKm(null)?.let { "%.1f km".format(it) } ?: "—"
    return if (trip.source == TripSource.GAP) {
        "기록되지 않은 주행 $km · ${dateTime(trip.startTime)} ~ ${trip.endTime?.let(::dateTime) ?: "?"} 사이"
    } else {
        "${dateTime(trip.startTime)} ~ ${trip.endTime?.let(::clock) ?: "?"} · $km"
    }
}

// ---------- Trip list ----------

@Composable
private fun TripRow(trip: Trip, currentKm: Double?) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(statusLabel(trip.status))
            if (trip.source == TripSource.GAP) Badge("기록되지 않은 주행")
            Text(
                "#${trip.id}  ${dateTime(trip.startTime)} ~ ${trip.endTime?.let(::clock) ?: "진행 중"}",
                fontSize = 18.sp,
            )
        }
        val km = trip.distanceKm(currentKm)?.let { "%.1f km".format(it) } ?: "—"
        Text(
            "$km  (${"%.1f".format(trip.startOdometer)} → ${trip.endOdometer?.let { "%.1f".format(it) } ?: "…"} km)" +
                "  · 용도 ${purposeLabel(trip.purpose)}" +
                "  · 종료 ${trip.endReason?.let(::endReasonLabel) ?: "—"}" +
                "  · 나중에 ${trip.askCount}회${trip.lastAskedAt?.let { " (마지막 ${clock(it)})" } ?: ""}",
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

// ---------- Recorder status and settings ----------

@Composable
private fun RecorderPanel(
    recorder: RecorderStatus,
    config: RecorderConfig,
    owners: Set<String>,
    awaitingCount: Int,
    hiddenWhileDriving: Boolean,
    onConfig: ((RecorderConfig) -> RecorderConfig) -> Unit,
    onReset: () -> Unit,
) {
    Panel {
        Text("기록 상태 · 설정 (디버그)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("세션 소유: ${owners.ifEmpty { setOf("없음") }.joinToString()}  · 백그라운드 서비스 ${if ("service" in owners) "실행 중" else "꺼짐"}", fontSize = 15.sp)
        Text(
            "종료 판정: ${if (recorder.fallbackActive) "대체 규칙 (총 주행거리 ${config.idleMinutes}분 무변화 + 주행 종료 버튼)" else "기어 P 또는 주차 브레이크 ON"}" +
                "  · 차량 상태: ${if (recorder.vehicleStopped) "정차" else "주행/알 수 없음"}",
            fontSize = 15.sp,
        )
        Text(
            "열린 주행: ${recorder.openTripId?.let { "#$it" } ?: "없음"}  · 확인 대기 ${awaitingCount}건" +
                (if (hiddenWhileDriving) " (주행 중이라 숨김)" else "") +
                "  · 나중에 처리됨: ${recorder.snoozedTripIds.ifEmpty { null }?.joinToString { "#$it" } ?: "없음"}",
            fontSize = 15.sp,
        )
        Text("마지막 판단: ${recorder.lastDecision}", fontSize = 15.sp)
        Text(
            "무시한 총 주행거리 감소 값: ${recorder.ignoredDecreaseCount}회" +
                (recorder.lastIgnoredDecrease?.let { " (마지막: $it)" } ?: ""),
            fontSize = 15.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("무변화 종료 N=${config.idleMinutes}분", fontSize = 16.sp, modifier = Modifier.width(200.dp))
            SmallButton("−") { onConfig { it.copy(idleMinutes = (it.idleMinutes - 1).coerceAtLeast(1)) } }
            SmallButton("+") { onConfig { it.copy(idleMinutes = it.idleMinutes + 1) } }
            Text("재시작 시 열린 주행 종료 기준 ${config.staleMinutes}분 (운영 10분)", fontSize = 16.sp, modifier = Modifier.padding(start = 24.dp).width(330.dp))
            SmallButton("−") { onConfig { it.copy(staleMinutes = (it.staleMinutes - 1).coerceAtLeast(1)) } }
            SmallButton("+") { onConfig { it.copy(staleMinutes = it.staleMinutes + 1) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (config.forceFallback) "대체 규칙 강제: 켜짐" else "대체 규칙 강제: 꺼짐") {
                onConfig { it.copy(forceFallback = !it.forceFallback) }
            }
            SmallButton(if (config.backgroundService) "백그라운드 기록: 켜짐" else "백그라운드 기록: 꺼짐") {
                onConfig { it.copy(backgroundService = !it.backgroundService) }
            }
            SmallButton("기록 초기화", onReset)
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(56.dp)) { Text(label, fontSize = 16.sp) }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

// ---------- Formatting ----------

private fun Reading<*>.statusLine(): String = buildString {
    append(api).append(" → ").append(capability.label())
    updatedAtMillis?.let { append(" · 갱신 ").append(clock(it)) }
    lastError?.let { append(" · ").append(it) }
}

private fun statusLabel(status: TripStatus) = when (status) {
    TripStatus.OPEN -> "진행 중"
    TripStatus.AWAITING_PURPOSE -> "용도 확인 필요"
    TripStatus.DONE -> "완료"
}

private fun purposeLabel(purpose: Purpose) = when (purpose) {
    Purpose.BUSINESS -> "업무"
    Purpose.COMMUTE -> "출퇴근"
    Purpose.PERSONAL -> "개인"
    Purpose.UNSET -> "미정"
}

private fun endReasonLabel(reason: EndReason) = when (reason) {
    EndReason.GEAR_PARK -> "기어 P"
    EndReason.PARKING_BRAKE -> "주차 브레이크"
    EndReason.ODOMETER_IDLE -> "주행거리 무변화"
    EndReason.MANUAL_BUTTON -> "주행 종료 버튼"
    EndReason.STALE_ON_RESTART -> "재시작 시 오래된 주행 정리"
}

private fun gearDisplay(state: DashboardState): Pair<String, String?> {
    state.gearSelection.value?.let { return gearName(it) to "GEAR_SELECTION" }
    return "—" to null
}

/** AAOS VehicleGear values. */
private fun gearName(value: Int): String = when (value) {
    0x0 -> "UNKNOWN"
    0x1 -> "N"
    0x2 -> "R"
    0x4 -> "P"
    0x8 -> "D"
    else -> "raw=$value"
}

/** AAOS VehicleIgnitionState values. */
private fun ignitionName(value: Int): String = when (value) {
    0 -> "UNDEFINED"
    1 -> "LOCK"
    2 -> "OFF"
    3 -> "ACC"
    4 -> "ON"
    5 -> "START"
    else -> "raw=$value"
}

private fun elapsed(millis: Long): String {
    val s = (millis / 1_000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private fun clock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
private fun dateTime(millis: Long): String = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(millis))
