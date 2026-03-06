package com.translator.app.ui

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.translator.app.model.Language
import com.translator.app.model.Languages
import com.translator.app.model.TranslatorState
import com.translator.app.service.AudioRecorder
import com.translator.app.service.TranslationService
import com.translator.app.service.TTSService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TranslatorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("translator_prefs", Context.MODE_PRIVATE)

    val state = MutableLiveData<TranslatorState>(TranslatorState.Idle)
    val selectedLanguage = MutableLiveData<Language>(Languages.ENGLISH)
    val currentAmplitude = MutableLiveData<Int>(0)

    private val recorder = AudioRecorder(app)
    private val ttsService = TTSService(app)
    private var translationService: TranslationService? = null

    private val amplitudeHandler = Handler(Looper.getMainLooper())
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (recorder.isRecording()) {
                currentAmplitude.postValue(recorder.getAmplitude())
                amplitudeHandler.postDelayed(this, 80)
            }
        }
    }

    init {
        val savedKey = getApiKey()
        if (savedKey.isNotEmpty()) {
            translationService = TranslationService(savedKey)
        }
    }

    fun getApiKey(): String = prefs.getString("api_key", "") ?: ""

    fun saveApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
        translationService = TranslationService(key)
    }

    fun selectLanguage(lang: Language) {
        selectedLanguage.postValue(lang)
    }

    // ─── 녹음 시작 ───
    fun startRecording() {
        if (state.value is TranslatorState.Recording) return
        ttsService.stop()
        state.postValue(TranslatorState.Recording)
        recorder.startRecording()

        // 진폭 모니터링 시작
        amplitudeHandler.post(amplitudeRunnable)
    }

    // ─── 녹음 중지 + 번역 ───
    fun stopRecordingAndTranslate() {
        if (state.value !is TranslatorState.Recording) return
        amplitudeHandler.removeCallbacks(amplitudeRunnable)

        val audioFile = recorder.stopRecording() ?: run {
            state.postValue(TranslatorState.Error("녹음 파일을 저장하지 못했습니다."))
            return
        }

        val service = translationService ?: run {
            state.postValue(TranslatorState.Error("API 키를 먼저 설정해주세요.\n오른쪽 상단 ⚙️ 를 눌러주세요."))
            return
        }

        val target = selectedLanguage.value ?: Languages.ENGLISH

        viewModelScope.launch(Dispatchers.IO) {
            try {
                state.postValue(TranslatorState.Transcribing)
                val result = service.translateAudio(audioFile, target)
                state.postValue(TranslatorState.Success(result))

                // 자동 TTS 재생
                autoPlayTTS(result.translatedText, if (result.direction == com.translator.app.model.TranslationDirection.KOREAN_TO_FOREIGN) target else Languages.KOREAN)

            } catch (e: Exception) {
                state.postValue(TranslatorState.Error(e.message ?: "오류가 발생했습니다."))
            } finally {
                audioFile.delete()
            }
        }
    }

    // ─── 취소 ───
    fun cancelRecording() {
        amplitudeHandler.removeCallbacks(amplitudeRunnable)
        recorder.cancelRecording()
        state.postValue(TranslatorState.Idle)
    }

    // ─── 텍스트 직접 번역 ───
    fun translateText(text: String) {
        val service = translationService ?: run {
            state.postValue(TranslatorState.Error("API 키를 먼저 설정해주세요."))
            return
        }
        val target = selectedLanguage.value ?: Languages.ENGLISH

        viewModelScope.launch(Dispatchers.IO) {
            try {
                state.postValue(TranslatorState.Translating)
                val result = service.translateText(text, target)
                state.postValue(TranslatorState.Success(result))
                autoPlayTTS(result.translatedText, if (result.direction == com.translator.app.model.TranslationDirection.KOREAN_TO_FOREIGN) target else Languages.KOREAN)
            } catch (e: Exception) {
                state.postValue(TranslatorState.Error(e.message ?: "오류가 발생했습니다."))
            }
        }
    }

    // ─── TTS 재생 ───
    fun speakText(text: String, language: Language) {
        ttsService.speak(text, language)
    }

    private fun autoPlayTTS(text: String, language: Language) {
        ttsService.speak(text, language)
    }

    fun resetState() {
        state.postValue(TranslatorState.Idle)
    }

    override fun onCleared() {
        super.onCleared()
        amplitudeHandler.removeCallbacks(amplitudeRunnable)
        ttsService.shutdown()
    }
}
