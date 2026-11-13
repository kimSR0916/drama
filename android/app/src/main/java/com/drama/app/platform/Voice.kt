package com.drama.app.platform

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import com.drama.app.core.Choice
import java.util.Locale

private const val TAG = "DramaVoice"

fun matchChoice(heard: String, choices: List<Choice>): Choice? {
    val said = heard.replace(" ", "")
    if (said.isEmpty()) return null
    choices.firstOrNull { said.contains(it.label.replace(" ", "")) }?.let { return it }
    val ordinals = listOf(listOf("첫", "1번", "일번"), listOf("두", "2번", "이번"), listOf("세", "3번", "삼번"))
    return choices.indices.firstOrNull { i -> ordinals.getOrNull(i)?.any { said.contains(it) } == true }?.let { choices[it] }
}

class Voice(context: Context, private val onHeard: (String) -> Unit) {
    private var pleosTts: TextToSpeech? = TextToSpeech(context, TtsMode.ON_DEVICE)
    private var pleosTtsReady = false
    private var fallback: AndroidTts? = null
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private var sttReady = false

    init {
        runCatching {
            pleosTts?.initialize(object : TtsInit {
                override fun onSuccess() {
                    pleosTtsReady = true
                    pleosTts?.setLocale(Locale.KOREA)
                }
                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "Pleos TTS init failed: $errorType")
                }
            })
        }.onFailure { Log.w(TAG, "Pleos TTS unavailable", it) }
        fallback = AndroidTts(context) { status ->
            if (status == AndroidTts.SUCCESS) fallback?.language = Locale.KOREA
        }
        runCatching {
            stt.initialize(object : SttInit {
                override fun onSuccess() {
                    sttReady = true
                    stt.setLocale(Locale.KOREA)
                }
                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "Pleos STT init failed: $errorType")
                }
            })
            stt.addListener(object : ResultListener {
                override fun onUpdated(text: String, isFinal: Boolean) {
                    if (isFinal) onHeard(text)
                }
                override fun onUpdatedEpdData(start: Long, end: Long) = Unit
                override fun onStartedRecognition() = Unit
                override fun onEndedRecognition() = Unit
                override fun onError(errorType: SttError) = Log.w(TAG, "STT error: $errorType").let { }
                override fun onReady() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
            })
        }.onFailure { Log.w(TAG, "Pleos STT unavailable", it) }
    }

    fun speak(text: String) {
        if (pleosTtsReady) runCatching { pleosTts?.speak(text, false) }.onSuccess { return }
        fallback?.speak(text, AndroidTts.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun stopSpeaking() {
        runCatching { pleosTts?.stop() }
        fallback?.stop()
    }

    fun listen() {
        if (sttReady) runCatching { stt.request() }.onFailure { Log.w(TAG, "STT request failed", it) }
    }

    fun stopListening() {
        if (sttReady) runCatching { stt.stop() }
    }

    fun release() {
        runCatching { pleosTts?.release() }
        runCatching { stt.release() }
        fallback?.shutdown()
        pleosTts = null
    }
}
