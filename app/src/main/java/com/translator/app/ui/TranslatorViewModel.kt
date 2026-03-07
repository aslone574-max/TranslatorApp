package com.translator.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.translator.app.model.*
import com.translator.app.service.SpeechService
import com.translator.app.service.TranslationService
import com.translator.app.service.TTSService
import kotlinx.coroutines.launch

class TranslatorViewModel(app: Application) : AndroidViewModel(app) {

    val state = MutableLiveData<TranslatorState>(TranslatorState.Idle)
    val selectedLanguage = MutableLiveData<Language>(Languages.ENGLISH)
    val currentAmplitude = MutableLiveData<Int>(0)
    val modeStatus = MutableLiveData<String>()
    val fontSize = MutableLiveData<Float>(20f)

    val translationService = TranslationService(app)
    private val speechService = SpeechService(app)
    private val ttsService = TTSService(app)
    private val prefs = app.getSharedPreferences("translator_prefs", Context.MODE_PRIVATE)

    init {
        updateModeStatus()
        fontSize.value = prefs.getFloat("font_size", 20f)
    }

    fun updateModeStatus() {
        val online = translationService.isOnline()
        val hasKey = translationService.hasApiKey()
        modeStatus.postValue(when {
            online && hasKey -> "🌐 온라인 (고품질)"
            online && !hasKey -> "🔑 API 키 없음 → 오프라인"
            else -> "📴 오프라인"
        })
    }

    fun getApiKey() = translationService.getApiKey()
    fun saveApiKey(key: String) { translationService.saveApiKey(key); updateModeStatus() }
    fun selectLanguage(lang: Language) { selectedLanguage.value = lang }
    fun setFontSize(size: Float) { fontSize.value = size; prefs.edit().putFloat("font_size", size).apply() }

    // ─── 탭 한 번 → 말하기 → 자동 번역 ───
    fun startRecording() {
        if (state.value is TranslatorState.Recording) return
        ttsService.stop()
        state.postValue(TranslatorState.Recording)
        updateModeStatus()

        val target = selectedLanguage.value ?: Languages.ENGLISH

        // 온라인/오프라인 모두 SpeechService 사용 (자동 감지)
        speechService.startListening(target, object : SpeechService.Callback {
            override fun onReady() {}
            override fun onAmplitude(rms: Float) {
                currentAmplitude.postValue(((rms + 2) * 1500).toInt().coerceIn(0, 32767))
            }
            override fun onResult(text: String) {
                currentAmplitude.postValue(0)
                viewModelScope.launch {
                    try {
                        state.postValue(TranslatorState.Translating)
                        val result = translationService.translateText(text, target)
                        state.postValue(TranslatorState.Success(result))
                        autoPlayTTS(result)
                    } catch (e: Exception) {
                        state.postValue(TranslatorState.Error(e.message ?: "오류"))
                    }
                }
            }
            override fun onError(message: String) {
                currentAmplitude.postValue(0)
                state.postValue(TranslatorState.Error(message))
            }
        })
    }

    fun cancelRecording() {
        speechService.destroy()
        currentAmplitude.postValue(0)
        state.postValue(TranslatorState.Idle)
    }

    fun translateText(text: String) {
        val target = selectedLanguage.value ?: Languages.ENGLISH
        viewModelScope.launch {
            try {
                state.postValue(TranslatorState.Translating)
                val result = translationService.translateText(text, target)
                state.postValue(TranslatorState.Success(result))
                autoPlayTTS(result)
            } catch (e: Exception) {
                state.postValue(TranslatorState.Error(e.message ?: "오류"))
            }
        }
    }

    fun speakText(text: String, language: Language) = ttsService.speak(text, language)

    private fun autoPlayTTS(result: TranslationResult) {
        val lang = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN) result.targetLanguage else Languages.KOREAN
        ttsService.speak(result.translatedText, lang)
    }

    fun resetState() { state.postValue(TranslatorState.Idle) }

    override fun onCleared() {
        super.onCleared()
        speechService.destroy()
        ttsService.shutdown()
    }
}
