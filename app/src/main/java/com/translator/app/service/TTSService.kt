package com.translator.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.translator.app.model.Language
import java.util.Locale

class TTSService(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
            }
        }
    }

    fun speak(
        text: String,
        language: Language,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (!isReady) return

        val locale = try {
            when (language.ttsCode) {
                "zh-CN" -> Locale.SIMPLIFIED_CHINESE
                "zh-TW" -> Locale.TRADITIONAL_CHINESE
                "zh-HK" -> Locale("zh", "HK")
                "ja-JP" -> Locale.JAPANESE
                "ko-KR" -> Locale.KOREAN
                "en-US" -> Locale.US
                "th-TH" -> Locale("th", "TH")
                "vi-VN" -> Locale("vi", "VN")
                else    -> Locale(language.code)
            }
        } catch (_: Exception) { Locale.ENGLISH }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // 지원 안 되면 기본 설정으로
            tts?.language = Locale.ENGLISH
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart?.invoke() }
            override fun onDone(utteranceId: String?)  { onDone?.invoke() }
            override fun onError(utteranceId: String?) { onDone?.invoke() }
        })

        // 속도와 피치 자연스럽게
        tts?.setSpeechRate(0.95f)
        tts?.setPitch(1.0f)

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
