package com.kilobook.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilobook.app.DashboardViewModel
import com.kilobook.app.data.MonthSummary
import com.kilobook.app.data.Purpose
import com.kilobook.app.data.Trip
import com.kilobook.app.data.TripSource
import com.kilobook.app.data.TripStatus
import com.kilobook.app.recorder.RecorderConfig
import com.kilobook.app.recorder.RecorderStatus
import com.kilobook.app.service.RecorderService
import com.kilobook.app.speech.VoiceState
import com.kilobook.app.ui.theme.KiloSize
import com.kilobook.app.ui.theme.KiloType
import com.kilobook.app.vehicle.Reading
import com.kilobook.app.vehicle.label
import kotlinx.coroutines.delay
import java.time.YearMonth
import java.time.ZoneId

/**
 * Driver home while not driving (5-2, 5-3, 5-5).
 * Purpose questions follow rule 4: only while a signal says the car is stopped, and not for trips answered "나중에".
 */
@Composable
fun HomeScreen(viewModel: DashboardViewModel, onOpenTrips: () -> Unit) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val recorder by viewModel.recorder.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val owners by viewModel.owners.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val month by produceState(YearMonth.now()) {
        while (true) {
            delay(60_000)
            value = YearMonth.now()
        }
    }

    val openTrip = trips.firstOrNull { it.status == TripStatus.OPEN }
    // Newest first: the trip that just ended is asked first, older ones follow one by one (5-3).
    val awaiting = trips.filter { it.status == TripStatus.AWAITING_PURPOSE }
        .sortedWith(compareByDescending<Trip> { it.endTime ?: it.startTime }.thenByDescending { it.id })
    val toAsk = if (recorder.vehicleStopped) awaiting.filter { it.id !in recorder.snoozedTripIds } else emptyList()
    val summary = remember(trips, month) { MonthSummary.of(trips, month, ZoneId.systemDefault()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(KiloSize.screenPadding),
        verticalArrangement = Arrangement.spacedBy(KiloSize.sectionGap),
    ) {
        val status: @Composable () -> Unit = {
            StatusCard(
                openTrip = openTrip,
                odometer = vehicle.odometerKm,
                recorder = recorder,
                config = config,
                backgroundRecording = RecorderService.OWNER in owners,
                awaitingCount = awaiting.size,
                notAskedCount = awaiting.size - toAsk.size,
                onEndTrip = viewModel::endTripManually,
                onOpenTrips = onOpenTrips,
            )
        }
        // The 주행 종료 button sits above the purpose card while a trip is open.
        if (openTrip != null) status()
        toAsk.firstOrNull()?.let { trip ->
            val waitingIds = toAsk.map { it.id }
            // Phase 7: the card appearing is the moment to ask by voice; its leaving stops the voice.
            LaunchedEffect(trip.id) { viewModel.onPurposeCardShown(trip, waitingIds) }
            DisposableEffect(trip.id) { onDispose { viewModel.onPurposeCardHidden(trip.id) } }
            PurposeCard(
                trip = trip,
                remaining = toAsk.size,
                voice = voiceState,
                onPurpose = { viewModel.choosePurpose(trip.id, it) },
                onLater = { viewModel.later(trip.id) },
            )
        }
        if (openTrip == null) status()
        SummarySection(summary)
    }
}

// ---------- 5-2 / 5-3: purpose card, one trip at a time ----------

@Composable
private fun PurposeCard(trip: Trip, remaining: Int, voice: VoiceState, onPurpose: (Purpose) -> Unit, onLater: () -> Unit) {
    // One answer per card. The next trip replaces this one right after a tap, so taps in the first moment
    // are ignored too: a double tap must not answer the next trip.
    val shownAt = remember(trip.id) { SystemClock.uptimeMillis() }
    var answered by remember(trip.id) { mutableStateOf(false) }
    fun guarded(action: () -> Unit) {
        if (answered || SystemClock.uptimeMillis() - shownAt < TAP_GUARD_MILLIS) return
        answered = true
        action()
    }

    KiloCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap), verticalAlignment = Alignment.CenterVertically) {
            Text("주행 용도를 골라 주세요", style = KiloType.title, modifier = Modifier.weight(1f))
            if (trip.isSample) Tag("예시", MaterialTheme.colorScheme.tertiaryContainer)
            if (remaining > 1) Tag("확인 대기 ${remaining}건", MaterialTheme.colorScheme.surface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.sectionGap), verticalAlignment = Alignment.Bottom) {
            Text(kmText(trip.distanceKm(null)), style = KiloType.headlineLarge)
            Column {
                if (trip.source == TripSource.GAP) Text("기록되지 않은 주행 · 앱이 꺼져 있던 동안 늘어난 거리", style = KiloType.label)
                Text("${shortDayText(trip.startTime)} ${tripTimeText(trip)}", style = KiloType.body)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap), modifier = Modifier.fillMaxWidth()) {
            BigButton("업무", Modifier.weight(1f)) { guarded { onPurpose(Purpose.BUSINESS) } }
            BigButton("출퇴근", Modifier.weight(1f)) { guarded { onPurpose(Purpose.COMMUTE) } }
            BigButton("개인", Modifier.weight(1f)) { guarded { onPurpose(Purpose.PERSONAL) } }
            BigOutlinedButton("나중에", Modifier.weight(1f)) { guarded(onLater) }
        }
        when (voice) {
            VoiceState.SPEAKING -> Text("음성으로 묻는 중", style = KiloType.label, color = mutedColor())
            VoiceState.LISTENING -> Text(
                "● 듣고 있어요 · \"업무\", \"출퇴근\", \"개인\" 중 하나를 말해 주세요",
                style = KiloType.labelStrong,
                color = MaterialTheme.colorScheme.primary,
            )
            VoiceState.IDLE -> Unit
        }
    }
}

private const val TAP_GUARD_MILLIS = 500L

// ---------- Recording status ----------

@Composable
private fun StatusCard(
    openTrip: Trip?,
    odometer: Reading<Float>,
    recorder: RecorderStatus,
    config: RecorderConfig,
    backgroundRecording: Boolean,
    awaitingCount: Int,
    notAskedCount: Int,
    onEndTrip: () -> Unit,
    onOpenTrips: () -> Unit,
) {
    KiloCard {
        if (openTrip != null) {
            Text("● 주행 기록 중", style = KiloType.title, color = MaterialTheme.colorScheme.primary)
            Text(
                "이번 주행 ${kmText(odometer.value?.let { it.toDouble() - openTrip.startOdometer })}",
                style = KiloType.headline,
            )
            if (recorder.fallbackActive) {
                val why = if (config.forceFallback) "디버그 설정 '대체 규칙 강제'가 켜져 있어" else "기어와 주차 브레이크를 읽을 수 없어"
                Text(
                    "$why, 총 주행거리가 ${config.idleMinutes}분 동안 그대로이거나 '주행 종료'를 누르면 주행을 끝냅니다.",
                    style = KiloType.label,
                    color = mutedColor(),
                )
                BigButton("주행 종료", Modifier.fillMaxWidth(), onClick = onEndTrip)
            }
        } else {
            Text("기록 대기 중", style = KiloType.title)
            Text("차가 움직이면 총 주행거리로 주행을 자동으로 기록합니다.", style = KiloType.body)
        }
        Text(odometerLine(odometer), style = KiloType.label, color = mutedColor())
        if (backgroundRecording) Text("앱 화면을 닫아도 기록합니다.", style = KiloType.label, color = mutedColor())

        if (notAskedCount > 0) {
            val why = if (recorder.vehicleStopped) {
                "나중에 고르기로 한 주행이 있습니다. 앱 화면을 다시 열거나 다음에 정차하면 다시 물어봅니다."
            } else {
                "정차한 것을 확인하면 물어봅니다."
            }
            Text("확인 대기 ${awaitingCount}건 · $why", style = KiloType.body)
            BigOutlinedButton("기록에서 고르기", height = KiloSize.buttonMedium, onClick = onOpenTrips)
        }
    }
}

/** The odometer value, or the API result as-is when there is no value (rule: errors are shown, not hidden). */
private fun odometerLine(odometer: Reading<Float>): String =
    odometer.value?.let { "차량 총 주행거리 ${kmText(it.toDouble())}" }
        ?: "차량 총 주행거리: ${odometer.capability.label()}${odometer.lastError?.let { " · $it" } ?: ""}"

// ---------- 5-5: this month ----------

@Composable
private fun SummarySection(summary: MonthSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(KiloSize.itemGap)) {
        SectionTitle("이번 달 · ${summary.month.monthValue}월")
        BoxWithConstraints {
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> SummaryTile("총 주행거리", kmText(summary.totalKm), "주행 ${summary.tripCount}건", m) },
                { m ->
                    SummaryTile(
                        "업무용 사용거리",
                        kmText(summary.workKm),
                        "업무 ${kmText(summary.businessKm)} · 출퇴근 ${kmText(summary.commuteKm)}",
                        m,
                    )
                },
                { m -> SummaryTile("업무사용비율", percentText(summary.workRatio), "(업무 + 출퇴근) ÷ 총 주행거리", m) },
                { m -> SummaryTile("용도 미확인", kmText(summary.unconfirmedKm), "${summary.unconfirmedCount}건 · 비율의 분모에만 들어감", m) },
            )
            if (maxWidth >= 1100.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap)) {
                    tiles.forEach { it(Modifier.weight(1f)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(KiloSize.itemGap)) {
                    tiles.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(KiloSize.itemGap)) {
                            pair.forEach { it(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        val notes = buildList {
            add("개인 ${kmText(summary.personalKm)}")
            if (summary.openTripCount > 0) add("진행 중인 주행은 끝난 뒤 더합니다")
            if (summary.sampleCount > 0) add("예시 기록 ${summary.sampleCount}건 포함")
        }
        Text(notes.joinToString("  ·  "), style = KiloType.label, color = mutedColor())
    }
}

@Composable
private fun SummaryTile(title: String, value: String, detail: String, modifier: Modifier) {
    KiloCard(modifier) {
        Text(title, style = KiloType.label)
        Text(value, style = KiloType.headline)
        Text(detail, style = KiloType.labelSmall, color = mutedColor())
    }
}

// ---------- 5-7: while driving ----------

/** Driving view: no questions and no buttons, only that recording is running and this trip's distance. */
@Composable
fun DrivingView(openTrip: Trip?, odometerKm: Float?) {
    val distance = if (openTrip == null) 0.0 else odometerKm?.let { it.toDouble() - openTrip.startOdometer }
    Column(
        Modifier.fillMaxSize().padding(KiloSize.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("● 기록 중", style = KiloType.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(KiloSize.sectionGap))
        Text("이번 주행 ${kmText(distance)}", style = KiloType.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(KiloSize.sectionGap))
        Text("정차하면 주행 용도를 물어봅니다", style = KiloType.body, color = mutedColor())
    }
}
