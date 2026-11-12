package com.kilobook.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Audio focus for one voice exchange (question, then listening), per the Pleos Connect audio focus guide:
 * request with AudioAttributes + AudioFocusRequest before playback, handle focus changes, abandon when done.
 *
 * Context NOTIFICATION (USAGE_NOTIFICATION) with AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK. In the Pleos Connect
 * Interaction Matrix v1.0 an incoming NOTIFICATION is C (plays with MUSIC and NAVIGATION, which keep their focus)
 * and R while VOICE_COMMAND (Gleo), CALL_RING or EMERGENCY holds focus.
 * CALL holding focus would be C; the app adds its own rule and does not speak during a call.
 *
 * The prompt cannot duck its own volume (the Pleos speech service plays it), so any loss stops the exchange.
 */
class VoiceFocus(context: Context, private val log: (String) -> Unit) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null

    /** Called on the main thread when focus is lost while held. */
    var onLost: ((String) -> Unit)? = null

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val name = changeName(change)
        log("AudioFocus.onAudioFocusChange -> $name")
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (request != null) onLost?.invoke(name)
            else -> Unit
        }
    }

    /** True while a phone call or ringing owns the audio (the app stays silent then). */
    fun inCall(): Boolean = when (audioManager.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    fun acquire(): Boolean {
        if (request != null) return true
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener, main)
            .build()
        val result = audioManager.requestAudioFocus(focusRequest)
        log("AudioFocus.requestAudioFocus(context=NOTIFICATION, usage=NOTIFICATION, gain=TRANSIENT_MAY_DUCK) -> ${resultName(result)}")
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        request = focusRequest
        return true
    }

    fun release() {
        val held = request ?: return
        request = null
        val result = audioManager.abandonAudioFocusRequest(held)
        log("AudioFocus.abandonAudioFocusRequest -> ${resultName(result)}")
    }

    private fun resultName(result: Int) = when (result) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
        else -> result.toString()
    }

    private fun changeName(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> change.toString()
    }
}
