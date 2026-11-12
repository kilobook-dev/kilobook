package com.kilobook.app.speech

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import com.kilobook.app.vehicle.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

enum class EngineState { CLOSED, INITIALIZING, READY, FAILED }

/**
 * Pleos TextToSpeech SDK 3.3.1 in ON_DEVICE mode (no network, no app registration).
 * Order from the Pleos guide: initialize → addEventListener → setLocale → speak → stop → removeEventListener → release.
 * Every SDK result goes to the log as "API -> result"; a failure leaves the buttons as the only way to answer.
 */
class VoiceOutput(context: Context, private val attributes: AudioAttributes, private val log: (String) -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var finish: ((Boolean) -> Unit)? = null

    private val _state = MutableStateFlow(EngineState.CLOSED)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = main.post { log("TextToSpeech.speak -> onStart") }.let { }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = main.post { log("TextToSpeech.speak -> onDone"); complete(true) }.let { }
        override fun onStop() = main.post { log("TextToSpeech.stop -> onStop"); complete(false) }.let { }
        override fun onError(errorType: ErrorType) = main.post { log("TextToSpeech -> onError($errorType)"); complete(false) }.let { }
    }

    /** Main thread. Starts the SDK session; [state] becomes READY or FAILED. */
    fun open() {
        if (engine != null) return
        val tts = try {
            TextToSpeech(appContext, Mode.ON_DEVICE)
        } catch (e: Exception) {
            log("TextToSpeech(ON_DEVICE) -> ${e.describe()}")
            _state.value = EngineState.FAILED
            return
        }
        engine = tts
        _state.value = EngineState.INITIALIZING
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (engine !== tts) return@post
                        log("TextToSpeech.initialize(ON_DEVICE) -> onSuccess")
                        try {
                            tts.addEventListener(events)
                            tts.setLocale(Locale.KOREA)
                            tts.setAudioAttributes(attributes)
                            log("TextToSpeech.addEventListener / setLocale(ko_KR) / setAudioAttributes(NOTIFICATION, SPEECH) -> ok")
                            _state.value = EngineState.READY
                        } catch (e: Exception) {
                            log("TextToSpeech setup -> ${e.describe()}")
                            _state.value = EngineState.FAILED
                        }
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post {
                        if (engine !== tts) return@post
                        log("TextToSpeech.initialize(ON_DEVICE) -> onFailure($errorType)")
                        _state.value = EngineState.FAILED
                    }
                }
            })
        } catch (e: Exception) {
            log("TextToSpeech.initialize -> ${e.describe()}")
            _state.value = EngineState.FAILED
        }
    }

    /** Main thread. Speaks [text] and returns true when it played to the end (false: stopped, error, not ready). */
    suspend fun speak(text: String): Boolean {
        val tts = engine
        if (tts == null || _state.value != EngineState.READY) return false
        return suspendCancellableCoroutine { cont ->
            finish = { done -> if (cont.isActive) cont.resume(done) }
            cont.invokeOnCancellation { main.post { stop() } }
            try {
                tts.speak(text, true)
                log("TextToSpeech.speak(\"$text\") -> requested")
            } catch (e: Exception) {
                log("TextToSpeech.speak -> ${e.describe()}")
                complete(false)
            }
        }
    }

    /** Main thread. Stops any speech in progress. */
    fun stop() {
        if (finish == null) return
        try {
            engine?.stop()
        } catch (e: Exception) {
            log("TextToSpeech.stop -> ${e.describe()}")
        }
        complete(false)
    }

    /** Main thread. Ends the SDK session (screen hidden). */
    fun close() {
        stop()
        val tts = engine ?: return
        engine = null
        try {
            tts.removeEventListener(events)
            tts.release()
            log("TextToSpeech.removeEventListener / release -> ok")
        } catch (e: Exception) {
            log("TextToSpeech.release -> ${e.describe()}")
        }
        _state.value = EngineState.CLOSED
    }

    private fun complete(done: Boolean) {
        val callback = finish ?: return
        finish = null
        callback(done)
    }
}
