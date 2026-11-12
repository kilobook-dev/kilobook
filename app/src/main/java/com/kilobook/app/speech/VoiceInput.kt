package com.kilobook.app.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.kilobook.app.vehicle.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Pleos SpeechToText SDK 3.3.1 in ON_DEVICE mode: the vehicle microphone, one short answer at a time.
 * Order from the Pleos guide: initialize → addListener → setLocale → request → stop → removeListener → release.
 */
class VoiceInput(context: Context, private val log: (String) -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: SpeechToText? = null
    private var finish: ((String?) -> Unit)? = null
    /** Called once the microphone is open (onReady), so the listening time starts there. */
    private var ready: (() -> Unit)? = null
    /** Called on onEndedRecognition: the final onUpdated(completed=true) can still follow it. */
    private var ended: (() -> Unit)? = null
    /** Latest non-empty onUpdated text of this listen, used when no final result arrives. */
    private var partial: String? = null
    /** request() was sent and stop() not yet. The speech service keeps the mic open until stop(), even after a timeout. */
    private var micOpen = false

    /** Input levels during one listen, from onUpdatedRms (SDK thread). Show whether the mic hears anything. */
    private val rms = RmsStats()

    private val _state = MutableStateFlow(EngineState.CLOSED)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val results = object : ResultListener {
        override fun onReady() = main.post {
            log("SpeechToText.request -> onReady")
            ready?.invoke()
            ready = null
        }.let { }
        override fun onStartedRecognition() = main.post { log("SpeechToText -> onStartedRecognition") }.let { }
        override fun onUpdated(stt: String, completed: Boolean) {
            main.post {
                log("SpeechToText -> onUpdated(\"$stt\", completed=$completed)")
                if (stt.isNotBlank()) partial = stt
                if (completed) complete(stt.ifBlank { partial })
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = main.post { log("SpeechToText -> onUpdatedEpdData(on=$on, off=$off)") }.let { }
        override fun onUpdatedRms(rms: Double) = this@VoiceInput.rms.add(rms)
        override fun onEndedRecognition() = main.post {
            log("SpeechToText -> onEndedRecognition")
            ended?.invoke()
            ended = null
        }.let { }
        override fun onError(errorType: ErrorType) = main.post { log("SpeechToText -> onError($errorType)"); complete(null) }.let { }
    }

    /** Main thread. Starts the SDK session; [state] becomes READY or FAILED. */
    fun open() {
        if (engine != null) return
        val stt = try {
            SpeechToText(appContext, Mode.ON_DEVICE)
        } catch (e: Exception) {
            log("SpeechToText(ON_DEVICE) -> ${e.describe()}")
            _state.value = EngineState.FAILED
            return
        }
        engine = stt
        _state.value = EngineState.INITIALIZING
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (engine !== stt) return@post
                        log("SpeechToText.initialize(ON_DEVICE) -> onSuccess")
                        try {
                            stt.addListener(results)
                            stt.setLocale(Locale.KOREA)
                            log("SpeechToText.addListener / setLocale(ko_KR) -> ok")
                            _state.value = EngineState.READY
                        } catch (e: Exception) {
                            log("SpeechToText setup -> ${e.describe()}")
                            _state.value = EngineState.FAILED
                        }
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post {
                        if (engine !== stt) return@post
                        log("SpeechToText.initialize(ON_DEVICE) -> onFailure($errorType)")
                        _state.value = EngineState.FAILED
                    }
                }
            })
        } catch (e: Exception) {
            log("SpeechToText.initialize -> ${e.describe()}")
            _state.value = EngineState.FAILED
        }
    }

    /**
     * Main thread. Listens once and returns the final text, or null (no speech, error, timeout, not ready).
     * [listenMillis] counts from onReady (the mic is open); the mic gets [READY_WAIT_MILLIS] to open.
     * Without a final result (time up, or onEndedRecognition and no final within [FINAL_WAIT_MILLIS])
     * the latest partial text is returned.
     * The microphone is always turned off again before this returns.
     */
    suspend fun listen(listenMillis: Long): String? {
        val stt = engine
        if (stt == null || _state.value != EngineState.READY) return null
        rms.reset()
        partial = null
        var timeout: Runnable? = null
        val text = suspendCancellableCoroutine { cont ->
            finish = { text -> if (cont.isActive) cont.resume(text) }
            val giveUp = Runnable {
                partial?.let { log("SpeechToText: 최종 결과 없음 -> 중간 결과 \"$it\" 사용") }
                complete(partial)
            }
            timeout = giveUp
            ready = {
                main.removeCallbacks(giveUp)
                main.postDelayed(giveUp, listenMillis)
            }
            // Stopping right at onEndedRecognition makes the service drop the final result that follows it.
            ended = {
                main.removeCallbacks(giveUp)
                main.postDelayed(giveUp, FINAL_WAIT_MILLIS)
            }
            main.postDelayed(giveUp, READY_WAIT_MILLIS)
            cont.invokeOnCancellation { main.post { stop() } }
            try {
                micOpen = true
                stt.request()
                log("SpeechToText.request -> requested")
            } catch (e: Exception) {
                log("SpeechToText.request -> ${e.describe()}")
                complete(null)
            }
        }
        timeout?.let { main.removeCallbacks(it) }
        val level = rms.describe()
        if (text == null) log("SpeechToText: 결과 없음 (듣기 ${listenMillis / 1000}초, $level)") else log("SpeechToText: $level")
        stop()
        return text
    }

    /** Main thread. Stops listening and releases the microphone. */
    fun stop() {
        ready = null
        ended = null
        if (micOpen) {
            micOpen = false
            try {
                engine?.stop()
                log("SpeechToText.stop -> ok")
            } catch (e: Exception) {
                log("SpeechToText.stop -> ${e.describe()}")
            }
        }
        complete(null)
    }

    /** Main thread. Ends the SDK session (screen hidden). Without this the speech service keeps the request alive. */
    fun close() {
        stop()
        val stt = engine ?: return
        engine = null
        try {
            stt.stop()
            stt.removeListener(results)
            stt.release()
            log("SpeechToText.stop / removeListener / release -> ok")
        } catch (e: Exception) {
            log("SpeechToText.release -> ${e.describe()}")
        }
        _state.value = EngineState.CLOSED
    }

    /** min / max / count of onUpdatedRms values. */
    private class RmsStats {
        private var count = 0
        private var min = 0.0
        private var max = 0.0

        @Synchronized fun reset() {
            count = 0
        }

        @Synchronized fun add(value: Double) {
            if (count == 0) {
                min = value
                max = value
            } else {
                min = minOf(min, value)
                max = maxOf(max, value)
            }
            count++
        }

        @Synchronized fun describe(): String =
            if (count == 0) "onUpdatedRms 없음" else "RMS %d회 최소 %.1f 최대 %.1f".format(count, min, max)
    }

    private companion object {
        /** request() → onReady took about 1.3 s on the emulator. */
        const val READY_WAIT_MILLIS = 3_000L
        /** On the emulator the final result came about 30 ms after onEndedRecognition. */
        const val FINAL_WAIT_MILLIS = 1_000L
    }

    private fun complete(text: String?) {
        val callback = finish ?: return
        finish = null
        callback(text)
    }
}
