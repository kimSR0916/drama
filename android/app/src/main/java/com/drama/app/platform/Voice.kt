package com.drama.app.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEvents
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import com.drama.app.core.Branch
import com.drama.app.core.LEAD_SILENCE_MS
import com.drama.app.core.RouteHint
import java.util.Locale

private const val TAG = "DramaVoice"
private const val WATCHDOG_BASE_MS = 3_000L
private const val WATCHDOG_PER_CHAR_MS = 250L

fun matchChoice(heard: String, branch: Branch): RouteHint? {
    val said = heard.replace(" ", "")
    if (said.isEmpty()) return null
    val detour = said.contains(branch.detour.keyword.replace(" ", ""))
    val keep = said.contains(branch.keep.keyword.replace(" ", ""))
    return when {
        detour && !keep -> RouteHint.DETOUR
        keep && !detour -> RouteHint.KEEP
        listOf("1번", "첫번째").any { said.contains(it) } -> RouteHint.DETOUR
        listOf("2번", "두번째").any { said.contains(it) } -> RouteHint.KEEP
        else -> null
    }
}

class Voice(context: Context, private val onHeard: (String) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pleosTts: TextToSpeech? = TextToSpeech(context, TtsMode.ON_DEVICE)
    private var pleosTtsReady = false
    private var fallback: AndroidTts? = null
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private var sttReady = false
    private var listenWhenQuiet = false
    private val queue = ArrayDeque<String>()
    private var current: String? = null
    private val watchdog = Runnable { onSpeechDone() }

    private fun playNext() {
        if (current != null) return
        val text = queue.removeFirstOrNull()
        if (text == null) {
            if (listenWhenQuiet) {
                listenWhenQuiet = false
                requestStt()
            }
            return
        }
        current = text
        mainHandler.postDelayed(watchdog, LEAD_SILENCE_MS + WATCHDOG_BASE_MS + text.length * WATCHDOG_PER_CHAR_MS)
        mainHandler.postDelayed({ if (current == text) start(text) }, LEAD_SILENCE_MS)
    }

    private fun start(text: String) {
        val started = (pleosTtsReady && runCatching { pleosTts?.speak(text, true) }.isSuccess) ||
            fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, text.hashCode().toString()) == AndroidTts.SUCCESS
        if (!started) onSpeechDone()
    }

    private fun onSpeechDone() {
        if (current == null) return
        mainHandler.removeCallbacks(watchdog)
        current = null
        playNext()
    }

    init {
        runCatching {
            pleosTts?.initialize(object : TtsInit {
                override fun onSuccess() {
                    pleosTtsReady = true
                    pleosTts?.setLocale(Locale.KOREA)
                    pleosTts?.addEventListener(object : TtsEvents {
                        override fun onStart() = Unit
                        override fun onReady() = Unit
                        override fun onDone() = mainHandler.post { onSpeechDone() }.let { }
                        override fun onError(errorType: TtsError) = mainHandler.post { onSpeechDone() }.let { }
                        override fun onStop() = Unit
                        override fun onUpdatedRms(rms: Double) = Unit
                    })
                }
                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "Pleos TTS init failed: $errorType")
                }
            })
        }.onFailure { Log.w(TAG, "Pleos TTS unavailable", it) }
        fallback = AndroidTts(context) { status ->
            if (status == AndroidTts.SUCCESS) fallback?.language = Locale.KOREA
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = mainHandler.post { onSpeechDone() }.let { }
                @Deprecated("required override")
                override fun onError(utteranceId: String?) = mainHandler.post { onSpeechDone() }.let { }
            })
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
        queue.addLast(text)
        playNext()
    }

    fun stopSpeaking() {
        queue.clear()
        current = null
        mainHandler.removeCallbacks(watchdog)
        listenWhenQuiet = false
        runCatching { pleosTts?.stop() }
        fallback?.stop()
    }

    fun listen() {
        if (current == null && queue.isEmpty()) requestStt() else listenWhenQuiet = true
    }

    private fun requestStt() {
        if (sttReady) runCatching { stt.request() }.onFailure { Log.w(TAG, "STT request failed", it) }
    }

    fun stopListening() {
        listenWhenQuiet = false
        if (sttReady) runCatching { stt.stop() }
    }

    fun release() {
        runCatching { pleosTts?.release() }
        runCatching { stt.release() }
        fallback?.shutdown()
        pleosTts = null
    }
}
