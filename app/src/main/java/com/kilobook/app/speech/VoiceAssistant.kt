package com.kilobook.app.speech

import android.content.Context
import com.kilobook.app.AppLog
import com.kilobook.app.data.Purpose
import com.kilobook.app.data.Trip
import com.kilobook.app.data.TripSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class VoiceState { IDLE, SPEAKING, LISTENING }

/**
 * Voice purpose check (Phase 7), owned by the screen and used only while the screen is visible.
 *
 * - Speaks only when the purpose card is shown, a signal says the car is stopped, and it is not driving.
 *   Driving, the card going away, the screen hiding or an audio focus loss stop it at once.
 * - Says one thing per card and never repeats it for the same trip (until the app process restarts).
 *   A trip counts as spoken only when the sentence finished; one cut off by driving or the screen hiding
 *   is said again at the next stop. The set lives at process level, so a new screen does not repeat it.
 * - One exchange: audio focus → 200 ms pre-roll silence → question → (listen for the answer) → abandon focus.
 *   What to say is fixed right before speaking: if the card's list changes first (on opening, a missed trip
 *   is added a moment later), the waiting notice for the list on screen is said instead.
 *   Pre-roll: the Pleos guide prefers silence inside the audio data; the SDK plays through the Pleos speech
 *   service and exposes neither the PCM nor a silence option, so the delay is applied before speak().
 */
class VoiceAssistant(
    context: Context,
    private val scope: CoroutineScope,
    private val guideEnabled: () -> Boolean,
    private val answerEnabled: () -> Boolean,
    /** Re-checked right before speaking or listening: stopped and not driving. */
    private val mayTalk: () -> Boolean,
    private val onAnswer: (tripId: Long, purpose: Purpose) -> Unit,
) {
    private val focus = VoiceFocus(context, ::log)
    private val output = VoiceOutput(context, focus.attributes, ::log)
    private val input = VoiceInput(context, ::log)
    private var exchange: Job? = null
    private var exchangeTripIds: Set<Long> = emptySet()
    /** What the exchange in preparation will say; null once it starts speaking. */
    private var pending: Pending? = null

    /** [question] about [askTripId], or (both null) the waiting notice for [waitingIds]. */
    private class Pending(val question: String?, val askTripId: Long?, val waitingIds: Set<Long>)

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    init {
        focus.onLost = { reason -> cancel("오디오 포커스 상실 ($reason)") }
    }

    /** Screen visible. */
    fun open() {
        if (guideEnabled()) output.open()
        if (guideEnabled() && answerEnabled()) input.open()
    }

    /** Screen hidden: stop, and end both SDK sessions. */
    fun close() {
        cancel("화면 숨김")
        output.close()
        input.close()
    }

    /** Settings changed while the screen is visible. */
    fun refresh() {
        if (!guideEnabled()) {
            cancel("음성 안내 꺼짐")
            output.close()
            input.close()
            return
        }
        output.open()
        if (answerEnabled()) input.open() else input.close()
    }

    /**
     * The purpose card now shows [trip]; [waitingIds] are all trips the card will go through.
     * Just-ended trip → question (and listening); otherwise → how many trips are waiting.
     */
    fun onCardShown(trip: Trip, waitingIds: List<Long>, now: Long) {
        if (!guideEnabled()) return
        if (exchange?.isActive == true) {
            val before = pending
            if (before != null && waitingIds.toSet() != before.waitingIds) {
                pending = Pending(null, null, waitingIds.toSet())
                exchangeTripIds = waitingIds.toSet()
                log("Voice: 말하기 전에 카드 목록이 바뀜 -> 대기 안내 ${waitingIds.size}건")
            }
            return
        }
        if (trip.id in spokenTripIds) return
        if (!mayTalk()) {
            log("Voice: 말하지 않음 (정차 신호 없음 또는 주행 중)")
            return
        }
        val km = trip.distanceKm(null)
        val justEnded = trip.source == TripSource.DETECTED && trip.askCount == 0 && km != null &&
            trip.endTime?.let { now - it in 0..JUST_ENDED_MILLIS } == true
        pending = if (justEnded) {
            Pending(VoicePrompts.tripQuestion(km!!), trip.id, waitingIds.toSet())
        } else {
            Pending(null, null, waitingIds.toSet())
        }
        exchangeTripIds = if (justEnded) setOf(trip.id) else waitingIds.toSet()
        exchange = scope.launch { run() }
    }

    /** The card for [tripId] went away (answered by touch, hidden): stop talking about it. */
    fun onCardHidden(tripId: Long) {
        if (tripId in exchangeTripIds && exchange?.isActive == true && _state.value != VoiceState.IDLE) {
            cancel("카드 닫힘 #$tripId")
        }
    }

    fun cancel(reason: String) {
        val job = exchange ?: return
        if (!job.isActive) return
        log("Voice: 중단 ($reason)")
        job.cancel()
        pending = null
        output.stop()
        input.stop()
        focus.release()
        _state.value = VoiceState.IDLE
    }

    private suspend fun run() {
        try {
            // The screen may have just opened: wait briefly for the engine instead of skipping.
            val ready = withTimeoutOrNull(ENGINE_WAIT_MILLIS) { output.state.first { it != EngineState.INITIALIZING } }
            if (ready != EngineState.READY) {
                log("Voice: 말하지 않음 (TextToSpeech ${ready ?: "초기화 시간 초과"}). 버튼으로 고른다")
                return
            }
            if (focus.inCall()) {
                log("Voice: 말하지 않음 (통화 중)")
                return
            }
            if (!focus.acquire()) {
                log("Voice: 말하지 않음 (오디오 포커스 거부)")
                return
            }
            delay(PRE_ROLL_MILLIS)
            if (!mayTalk()) {
                log("Voice: 말하지 않음 (선행 묵음 중 주행 시작)")
                return
            }
            val say = pending ?: return
            pending = null
            val askTripId = say.askTripId
            val coveredIds = if (askTripId != null) setOf(askTripId) else say.waitingIds
            exchangeTripIds = coveredIds
            val text = say.question ?: VoicePrompts.waitingNotice(coveredIds.size)
            _state.value = VoiceState.SPEAKING
            val spoken = output.speak(text)
            if (!spoken) return
            spokenTripIds += coveredIds
            if (askTripId == null || !answerEnabled()) return
            if (input.state.value != EngineState.READY) {
                log("Voice: 듣지 않음 (SpeechToText ${input.state.value})")
                return
            }
            if (!mayTalk()) return
            _state.value = VoiceState.LISTENING
            val heard = input.listen(LISTEN_MILLIS) ?: return
            val purpose = VoicePrompts.purposeFromSpeech(heard)
            if (purpose == null) {
                log("Voice: \"$heard\" -> 용도로 알아듣지 못함. 버튼으로 고른다")
                return
            }
            log("Voice: \"$heard\" -> $purpose (#$askTripId)")
            onAnswer(askTripId, purpose)
        } finally {
            focus.release()
            _state.value = VoiceState.IDLE
        }
    }

    private fun log(message: String) = AppLog.i(message)

    companion object {
        /** Trips already spoken about in this process (main thread only). */
        private val spokenTripIds = mutableSetOf<Long>()

        /** 150–200 ms recommended by the Pleos pre-roll silence guide. */
        const val PRE_ROLL_MILLIS = 200L
        /** A trip that ended within this time is "이번 주행" (asked as a question). */
        const val JUST_ENDED_MILLIS = 120_000L
        const val ENGINE_WAIT_MILLIS = 3_000L
        const val LISTEN_MILLIS = 7_000L
    }
}
