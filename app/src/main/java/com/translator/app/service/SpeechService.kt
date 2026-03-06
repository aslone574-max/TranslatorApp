package com.translator.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.translator.app.model.Language

class SpeechService(private val context: Context) {

    interface Callback {
        fun onReady()
        fun onAmplitude(rms: Float)
        fun onResult(text: String)
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(targetLanguage: Language, callback: Callback) {
        mainHandler.post {
            recognizer?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                callback.onError("이 기기에서 음성 인식을 사용할 수 없습니다.")
                return@post
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = callback.onReady()
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) = callback.onAmplitude(rms)
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성을 인식하지 못했습니다. 다시 말씀해 주세요."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "오프라인 음성 팩이 필요합니다.\n설정 > 언어 > 오프라인 음성인식을 설치해주세요."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "잠시 후 다시 시도해주세요."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
                        else -> "음성 인식 오류 ($error)"
                    }
                    callback.onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    if (text.isEmpty()) callback.onError("음성을 인식하지 못했습니다. 다시 말씀해 주세요.")
                    else callback.onResult(text)
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                    arrayOf(targetLanguage.ttsCode, "en-US", "ja-JP", "zh-CN", "th-TH", "vi-VN"))
            }
            recognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post { recognizer?.stopListening() }
    }

    fun destroy() {
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }
}
