package com.odolog.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.odolog.spike.vehicle.DashboardState
import com.odolog.spike.vehicle.Reading
import com.odolog.spike.vehicle.label
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(state: DashboardState) {
    // Ticks every second so the app-calculated trip time keeps moving.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("오도로그 스파이크 · 실시간 값", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Vehicle SDK: ${state.sdkSession.label()}    Car service: ${state.carSession.label()}",
            fontSize = 16.sp,
        )

        SignalRow(
            title = "총 주행거리",
            value = state.odometerKm.value?.let { "%.1f km".format(it) } ?: "—",
            sources = listOf(state.odometerKm),
        )
        SignalRow(
            title = "트립 주행거리",
            value = state.tripDistanceM.value?.let { "%.0f m".format(it) } ?: "—",
            sources = listOf(state.tripDistanceM, state.tripInstantDistanceM),
            appValue = appTripDistance(state),
            appNote = "화면을 연 뒤 늘어난 총 주행거리",
        )
        SignalRow(
            title = "트립 주행시간",
            value = state.tripTimeMin.value?.let { "${it}분" } ?: "—",
            sources = listOf(state.tripTimeMin),
            appValue = appTripTime(state, now),
            appNote = "총 주행거리가 처음 늘어난 시각부터",
        )

        val gear = gearDisplay(state)
        SignalRow(
            title = "기어",
            value = gear.first,
            valueNote = gear.second,
            sources = listOf(state.gearSelection, state.currentGear, state.vendorGear, state.pleosGear),
        )
        SignalRow(
            title = "주차 브레이크",
            value = when (state.parkingBrake.value) {
                true -> "ON"
                false -> "OFF"
                null -> "—"
            },
            sources = listOf(state.parkingBrake),
        )

        val ux = state.uxRestrictions.value
        SignalRow(
            title = "주행 규제",
            value = when (ux?.requiresDistractionOptimization) {
                true -> "주행 (규제 활성)"
                false -> "정차 (규제 없음)"
                null -> "—"
            },
            valueNote = ux?.let { "activeRestrictions=0x${it.activeRestrictions.toString(16)}" },
            sources = listOf(state.uxRestrictions),
        )
        SignalRow(
            title = "시동 (참고)",
            value = state.ignition.value?.let(::ignitionName) ?: "—",
            sources = listOf(state.ignition),
        )
    }
}

@Composable
private fun SignalRow(
    title: String,
    value: String,
    sources: List<Reading<*>>,
    valueNote: String? = null,
    appValue: String? = null,
    appNote: String? = null,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, fontSize = 24.sp, modifier = Modifier.width(220.dp))
                Column(Modifier.width(560.dp)) {
                    Text("SDK·API 값", fontSize = 14.sp)
                    Text(value, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    if (valueNote != null) Text(valueNote, fontSize = 14.sp)
                }
                if (appValue != null) {
                    Column {
                        Text("앱 계산 · ${appNote.orEmpty()}", fontSize = 14.sp)
                        Text(appValue, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            sources.forEach { reading ->
                Text(reading.statusLine(), fontSize = 15.sp)
            }
        }
    }
}

private fun Reading<*>.statusLine(): String = buildString {
    append(api).append("  →  ").append(capability.label())
    updatedAtMillis?.let { append("  · 갱신 ").append(clock(it)) }
    lastError?.let { append("  · ").append(it) }
}

private fun appTripDistance(state: DashboardState): String {
    val current = state.odometerKm.value ?: return "—"
    val baseline = state.tripBaselineKm ?: return "—"
    return "%.1f km".format(current - baseline)
}

private fun appTripTime(state: DashboardState, now: Long): String {
    val started = state.tripStartedAtMillis ?: return "주행 전"
    val seconds = ((now - started) / 1_000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}

/** First gear value that was actually read, and which API it came from. */
private fun gearDisplay(state: DashboardState): Pair<String, String?> {
    state.gearSelection.value?.let { return gearName(it) to "출처: GEAR_SELECTION" }
    state.currentGear.value?.let { return gearName(it) to "출처: CURRENT_GEAR" }
    state.vendorGear.value?.let { return gearName(it) to "출처: VENDOR_GEAR_SELECTION (raw=$it)" }
    state.pleosGear.value?.let { return it.removePrefix("GEAR_") to "출처: Pleos Gear (deprecated)" }
    return "—" to null
}

/** AAOS VehicleGear values. */
private fun gearName(value: Int): String = when (value) {
    0x0 -> "UNKNOWN"
    0x1 -> "N"
    0x2 -> "R"
    0x4 -> "P"
    0x8 -> "D"
    0x10 -> "1"
    0x20 -> "2"
    0x40 -> "3"
    0x80 -> "4"
    0x100 -> "5"
    0x200 -> "6"
    0x400 -> "7"
    0x800 -> "8"
    0x1000 -> "9"
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

private fun clock(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
