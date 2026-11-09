package com.kilobook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilobook.app.AppLog
import com.kilobook.app.DashboardViewModel
import com.kilobook.app.data.EndReason
import com.kilobook.app.data.Purpose
import com.kilobook.app.data.SampleTrips
import com.kilobook.app.data.Trip
import com.kilobook.app.data.TripSource
import com.kilobook.app.data.TripStatus
import com.kilobook.app.recorder.RecorderConfig
import com.kilobook.app.recorder.RecorderStatus
import com.kilobook.app.vehicle.DashboardState
import com.kilobook.app.vehicle.Reading
import com.kilobook.app.vehicle.label
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug panel (shown when 설정 › 디버그 패널 보기 is on): live vehicle values with each API's result,
 * recorder state and settings, example trips (5-8), every trip with its raw fields, and the debug log.
 * A developer tool, so it keeps the Phase 2–4 layout and smaller text.
 */
@Composable
fun DebugScreen(viewModel: DashboardViewModel) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val recorder by viewModel.recorder.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val owners by viewModel.owners.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val bootRecord by viewModel.bootRecord.collectAsStateWithLifecycle()
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var sdkOnly by rememberSaveable { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    val openTrip = trips.firstOrNull { it.status == TripStatus.OPEN }
    val awaiting = trips.filter { it.status == TripStatus.AWAITING_PURPOSE }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        item { LivePanel(vehicle, openTrip, now) }

        item {
            RecorderPanel(
                recorder = recorder,
                config = config,
                owners = owners,
                awaitingCount = awaiting.size,
                hiddenWhileDriving = awaiting.isNotEmpty() && !recorder.vehicleStopped,
                bootRecord = bootRecord,
                onConfig = viewModel::updateConfig,
                onReset = viewModel::resetRecords,
            )
        }

        item {
            SamplePanel(
                sampleCount = trips.count { it.isSample },
                onAdd = viewModel::addSampleTrips,
                onClear = viewModel::clearSampleTrips,
            )
        }

        item { Text("주행 기록 ${trips.size}건 (최신순, 디버그 상세)", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (trips.isEmpty()) item { Text("아직 기록이 없습니다", fontSize = 16.sp) }
        items(trips, key = { it.id }) { trip -> TripRow(trip, vehicle.odometerKm.value?.toDouble()) }

        // Phase 4: collapsible debug log (newest first)
        item {
            DebugLogHeader(
                count = log.size,
                open = logOpen,
                sdkOnly = sdkOnly,
                onToggle = { logOpen = !logOpen },
                onFilter = { sdkOnly = !sdkOnly },
                onClear = viewModel::clearLog,
            )
        }
        if (logOpen) {
            val shown = log.asReversed().filter { !sdkOnly || !it.isAppEvent }
            if (shown.isEmpty()) item { Text("기록된 로그가 없습니다", fontSize = 15.sp) }
            items(shown) { entry -> LogRow(entry) }
        }
        item { Spacer(Modifier.height(32.dp)) }
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
            details = listOf(
                state.odometerKm.statusLine(),
                "시작 기준값: " + (state.odometerAtStart.value?.let { "%.1f km".format(it) } ?: state.odometerAtStart.capability.label()) +
                    " (getCurrentDistanceDriven, 누락 감지·재시작 정리에 사용)",
            ),
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

// ---------- Example trips (5-8) ----------

@Composable
private fun SamplePanel(sampleCount: Int, onAdd: () -> Unit, onClear: () -> Unit) {
    Panel {
        Text("예시 기록 (5-8)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "이번 달에 예시 주행 ${SampleTrips.count}건(용도 확인 대기 2건 포함)을 넣습니다. 화면과 기록에 '예시'로 표시되고, " +
                "이번 달 요약에도 더해집니다. 실제 기록은 지우지 않습니다. 지금 예시 기록: ${sampleCount}건",
            fontSize = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("예시 기록 넣기", onAdd)
            SmallButton("예시 기록 모두 지우기", onClear)
        }
    }
}

// ---------- Trip list ----------

@Composable
private fun TripRow(trip: Trip, currentKm: Double?) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(statusLabel(trip.status))
            if (trip.source == TripSource.GAP) Badge("기록되지 않은 주행")
            if (trip.isSample) Badge("예시")
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
    bootRecord: String?,
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
        Text("부팅 시 자동 시작(S8): ${bootRecord ?: "BOOT_COMPLETED를 받은 기록 없음"}", fontSize = 15.sp)
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

// ---------- Debug log (Phase 4) ----------

@Composable
private fun DebugLogHeader(
    count: Int,
    open: Boolean,
    sdkOnly: Boolean,
    onToggle: () -> Unit,
    onFilter: () -> Unit,
    onClear: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("디버그 로그 ${count}건 (최신순, 최근 300건)", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(420.dp))
        SmallButton(if (open) "접기" else "펼치기", onToggle)
        if (open) {
            SmallButton(if (sdkOnly) "SDK·차량 API만 보는 중" else "전체 보는 중", onFilter)
            SmallButton("로그 지우기", onClear)
        }
    }
}

@Composable
private fun LogRow(entry: AppLog.Entry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(logClock(entry.atMillis), fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(140.dp))
        Text(entry.name, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        val repeats = if (entry.count > 1) "  (×${entry.count}, 처음 ${logClock(entry.firstAtMillis)})" else ""
        Text(entry.result + repeats, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
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

private fun logClock(millis: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA).format(Date(millis))
private fun clock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
private fun dateTime(millis: Long): String = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(millis))
