package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.StringRes
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

private const val TAG = "WanderCastTtsManager"

enum class TtsVoicePersona(
    val displayName: String,
    @StringRes val displayNameRes: Int,
    val pitch: Float,
    val defaultRate: Float
) {
    PUBLIC_RADIO_HOST(
        displayName = "Baritone Host",
        displayNameRes = R.string.voice_persona_host,
        pitch = 0.94f,
        defaultRate = 1.0f
    ),
    FIELD_REPORTER(
        displayName = "Field Reporter",
        displayNameRes = R.string.voice_persona_reporter,
        pitch = 1.04f,
        defaultRate = 1.08f
    ),
    NIGHT_ARCHIVIST(
        displayName = "Night Archivist",
        displayNameRes = R.string.voice_persona_archivist,
        pitch = 0.87f,
        defaultRate = 0.93f
    )
}

data class NarratorState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val episodeTitle: String = "",
    val activeSentence: String = "",
    val activeWord: String = "",
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val progressFraction: Float = 0f,
    val speechRate: Float = 1.0f,
    val pitch: Float = TtsVoicePersona.PUBLIC_RADIO_HOST.pitch,
    val voicePersona: TtsVoicePersona = TtsVoicePersona.PUBLIC_RADIO_HOST,
    val engineError: String? = null
)

/**
 * Manages Android's [TextToSpeech] engine to synthesize Gemini-generated radio documentary
 * narratives with audio focus ducking, sentence-by-sentence queueing, live word-range
 * teleprompter tracking, and adjustable documentary voice personas.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var tts: TextToSpeech? = null
    private var isEngineInitialized = false
    private var preferredLocale: Locale = Locale.US

    private val sentences = mutableListOf<String>()
    private var currentIndex: Int = 0
    private var currentTitle: String = ""
    private var pendingSynthesisCallback: (() -> Unit)? = null

    private val _state = MutableStateFlow(NarratorState())
    val state: StateFlow<NarratorState> = _state.asStateFlow()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_state.value.isSpeaking) {
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Ready to continue speaking
            }
        }
    }

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val speechAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(speechAudioAttrs)

            val langResult = engine.setLanguage(preferredLocale).let { res ->
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                } else {
                    res
                }
            }
            val isLangAvailable = langResult != TextToSpeech.LANG_MISSING_DATA &&
                langResult != TextToSpeech.LANG_NOT_SUPPORTED

            val persona = _state.value.voicePersona
            engine.setPitch(persona.pitch)
            engine.setSpeechRate(_state.value.speechRate)
            configureUtteranceListener(engine)

            isEngineInitialized = isLangAvailable
            _state.update {
                it.copy(
                    isReady = isLangAvailable,
                    engineError = if (isLangAvailable) null else "TTS voice data is unavailable on this device."
                )
            }

            if (isLangAvailable) {
                pendingSynthesisCallback?.invoke()
                pendingSynthesisCallback = null
            } else {
                pendingSynthesisCallback = null
                _state.update { it.copy(isSpeaking = false) }
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status=$status")
            isEngineInitialized = false
            pendingSynthesisCallback = null
            _state.update {
                it.copy(
                    isReady = false,
                    isSpeaking = false,
                    engineError = "TextToSpeech engine failed to initialize (code $status)."
                )
            }
        }
    }

    fun setLocale(locale: Locale) {
        preferredLocale = locale
        val engine = tts ?: return
        if (!isEngineInitialized) return
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
    }

    private fun configureUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val idx = parseIndex(utteranceId)
                currentIndex = idx
                val sentence = synchronized(sentences) { sentences.getOrNull(idx).orEmpty() }
                val total = synchronized(sentences) { sentences.size.coerceAtLeast(1) }
                _state.update {
                    it.copy(
                        isSpeaking = true,
                        isPaused = false,
                        activeSentence = sentence,
                        activeWord = "",
                        currentSentenceIndex = idx,
                        totalSentences = total,
                        progressFraction = (idx.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        engineError = null
                    )
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val idx = parseIndex(utteranceId)
                val sentence = synchronized(sentences) { sentences.getOrNull(idx).orEmpty() }
                if (start in 0..sentence.length && end in start..sentence.length) {
                    val word = sentence.substring(start, end).trim()
                    val total = synchronized(sentences) { sentences.size.coerceAtLeast(1) }
                    val intraSentenceFraction = if (sentence.isNotEmpty()) {
                        end.toFloat() / sentence.length.toFloat()
                    } else {
                        0f
                    }
                    val smoothProgress = ((idx.toFloat() + intraSentenceFraction) / total.toFloat())
                        .coerceIn(0f, 1f)

                    _state.update {
                        it.copy(
                            activeWord = word,
                            progressFraction = smoothProgress
                        )
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                val idx = parseIndex(utteranceId)
                val nextIdx = idx + 1
                val total = synchronized(sentences) { sentences.size }
                if (nextIdx < total) {
                    currentIndex = nextIdx
                    speakSentenceAt(nextIdx)
                } else {
                    abandonAudioFocus()
                    _state.update {
                        it.copy(
                            isSpeaking = false,
                            isPaused = false,
                            activeWord = "",
                            currentSentenceIndex = total,
                            progressFraction = 1f
                        )
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.w(TAG, "TTS synthesis error for utterance=$utteranceId")
                abandonAudioFocus()
                _state.update { it.copy(isSpeaking = false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "TTS synthesis error code=$errorCode for utterance=$utteranceId")
                abandonAudioFocus()
                _state.update {
                    it.copy(
                        isSpeaking = false,
                        engineError = "Voice synthesis interrupted (error $errorCode)."
                    )
                }
            }
        })
    }

    /**
     * Synthesizes a structured Gemini radio documentary episode including its opening hook,
     * main 60-second narrative script, optional field facts, and urban explorer observation prompt.
     */
    fun synthesizeEpisodeNarrative(
        title: String,
        hookLine: String,
        script: String,
        explorerPrompt: String,
        fieldFacts: List<String> = emptyList()
    ) {
        val fullNarrative = buildString {
            if (hookLine.isNotBlank()) {
                append(hookLine.trim())
                if (!hookLine.trim().endsWith(".") && !hookLine.trim().endsWith("!") && !hookLine.trim().endsWith("?")) {
                    append(".")
                }
                append(" ")
            }
            append(script.trim())
            if (fieldFacts.isNotEmpty()) {
                append(" Quick field note: ")
                append(fieldFacts.first().trim())
                if (!fieldFacts.first().trim().endsWith(".")) append(".")
            }
            if (explorerPrompt.isNotBlank()) {
                append(" Look around you right now: ")
                append(explorerPrompt.trim())
            }
        }
        synthesizeText(title = title, text = fullNarrative)
    }

    /**
     * Synthesizes arbitrary Gemini text narrative (e.g., live Q&A responses or episode scripts)
     * by splitting it into natural broadcast sentences.
     */
    fun synthesizeText(title: String, text: String) {
        val parsedSentences = splitIntoSentences(text)
        if (parsedSentences.isEmpty()) return

        val startTask = {
            currentTitle = title
            synchronized(sentences) {
                sentences.clear()
                sentences.addAll(parsedSentences)
            }
            currentIndex = 0
            requestAudioFocus()

            _state.value = _state.value.copy(
                episodeTitle = title,
                isSpeaking = true,
                isPaused = false,
                activeSentence = parsedSentences.first(),
                activeWord = "",
                currentSentenceIndex = 0,
                totalSentences = parsedSentences.size,
                progressFraction = 0f,
                engineError = null
            )
            speakSentenceAt(0)
        }

        if (isEngineInitialized) {
            startTask()
        } else {
            pendingSynthesisCallback = startTask
        }
    }

    /**
     * Appends a newly streamed sentence segment to the active synthesis queue without
     * cutting off the currently spoken sentence.
     */
    fun enqueueNarrativeSegment(title: String, segmentText: String) {
        val newChunks = splitIntoSentences(segmentText)
        if (newChunks.isEmpty()) return

        val shouldStartImmediately: Boolean
        val totalNow: Int
        synchronized(sentences) {
            val wasEmptyOrFinished = sentences.isEmpty() || (!_state.value.isSpeaking && !_state.value.isPaused)
            if (wasEmptyOrFinished && !_state.value.isSpeaking) {
                sentences.clear()
                currentIndex = 0
            }
            sentences.addAll(newChunks)
            totalNow = sentences.size
            shouldStartImmediately = !_state.value.isSpeaking
        }

        currentTitle = title
        _state.value = _state.value.copy(
            episodeTitle = title,
            totalSentences = totalNow
        )

        if (shouldStartImmediately && isEngineInitialized) {
            requestAudioFocus()
            speakSentenceAt(currentIndex)
        }
    }

    private fun speakSentenceAt(index: Int) {
        if (!isEngineInitialized) return
        val sentence = synchronized(sentences) { sentences.getOrNull(index) } ?: return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val res = tts?.speak(
            sentence,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "utt_$index"
        )
        if (res == TextToSpeech.ERROR) {
            Log.w(TAG, "TTS speak returned ERROR for utterance utt_$index")
            abandonAudioFocus()
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isSpeaking) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        tts?.stop()
        abandonAudioFocus()
        _state.value = _state.value.copy(
            isSpeaking = false,
            isPaused = true,
            activeWord = ""
        )
    }

    fun resume() {
        val total = synchronized(sentences) { sentences.size }
        if (total == 0) return
        val resumeIdx = if (currentIndex >= total) 0 else currentIndex
        currentIndex = resumeIdx
        val sentence = synchronized(sentences) { sentences.getOrElse(resumeIdx) { "" } }
        requestAudioFocus()
        _state.value = _state.value.copy(
            isSpeaking = true,
            isPaused = false,
            activeSentence = sentence,
            currentSentenceIndex = resumeIdx,
            totalSentences = total
        )
        speakSentenceAt(resumeIdx)
    }

    fun skipForwardSentence() {
        val total = synchronized(sentences) { sentences.size }
        if (total == 0) return
        val next = (currentIndex + 1).coerceAtMost(total - 1)
        currentIndex = next
        val sentence = synchronized(sentences) { sentences[next] }
        requestAudioFocus()
        _state.value = _state.value.copy(
            isSpeaking = true,
            isPaused = false,
            currentSentenceIndex = next,
            activeSentence = sentence,
            activeWord = "",
            progressFraction = (next.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
        speakSentenceAt(next)
    }

    fun skipBackwardSentence() {
        val total = synchronized(sentences) { sentences.size }
        if (total == 0) return
        val prev = (currentIndex - 1).coerceAtLeast(0)
        currentIndex = prev
        val sentence = synchronized(sentences) { sentences[prev] }
        requestAudioFocus()
        _state.value = _state.value.copy(
            isSpeaking = true,
            isPaused = false,
            currentSentenceIndex = prev,
            activeSentence = sentence,
            activeWord = "",
            progressFraction = (prev.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
        speakSentenceAt(prev)
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.75f, 1.5f)
        tts?.setSpeechRate(clamped)
        _state.value = _state.value.copy(speechRate = clamped)
        if (_state.value.isSpeaking) {
            speakSentenceAt(currentIndex)
        }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.7f, 1.4f)
        tts?.setPitch(clamped)
        _state.value = _state.value.copy(pitch = clamped)
        if (_state.value.isSpeaking) {
            speakSentenceAt(currentIndex)
        }
    }

    fun setVoicePersona(persona: TtsVoicePersona) {
        tts?.setPitch(persona.pitch)
        tts?.setSpeechRate(persona.defaultRate)
        _state.value = _state.value.copy(
            voicePersona = persona,
            pitch = persona.pitch,
            speechRate = persona.defaultRate
        )
        if (_state.value.isSpeaking) {
            speakSentenceAt(currentIndex)
        }
    }

    fun cycleVoicePersona() {
        val all = TtsVoicePersona.entries
        val nextIdx = (all.indexOf(_state.value.voicePersona) + 1) % all.size
        setVoicePersona(all[nextIdx])
    }

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
        _state.value = _state.value.copy(
            isSpeaking = false,
            isPaused = false,
            activeWord = "",
            progressFraction = 0f
        )
    }

    fun shutdown() {
        try {
            pendingSynthesisCallback = null
            tts?.stop()
            tts?.shutdown()
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TtsManager", e)
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest ?: AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                    .also { audioFocusRequest = it }
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to abandon audio focus", e)
        }
    }

    private fun parseIndex(utteranceId: String?): Int {
        return utteranceId?.removePrefix("utt_")?.toIntOrNull() ?: currentIndex
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
    }
}
