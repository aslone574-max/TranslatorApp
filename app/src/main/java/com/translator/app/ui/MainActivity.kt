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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TranslatorViewModel(app: Application) : AndroidViewModel(app) {

    val state = MutableLiveData<TranslatorState>(TranslatorState.Idle)
    val selectedLanguage = MutableLiveData<Language>(Languages.ENGLISH)
    val currentAmplitude = MutableLiveData<Int>(0)
    val modeStatus = MutableLiveData<String>()
    val fontSize = MutableLiveData<Float>(20f)
    val isListening = MutableLiveData<Boolean>(false)

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

    // ─── 항상 대기 모드 시작 ───
    fun startListening() {
        if (isListening.value == true) return
        isListening.postValue(true)
        ttsService.stop()
        listenLoop()
    }

    fun stopListening() {
        isListening.postValue(false)
        speechService.destroy()
        currentAmplitude.postValue(0)
        state.postValue(TranslatorState.Idle)
    }

    private fun listenLoop() {
        if (isListening.value != true) return
        state.postValue(TranslatorState.Recording)
        val target = selectedLanguage.value ?: Languages.ENGLISH

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
                        // 번역 후 잠깐 결과 보여주고 다시 대기
                        delay(3000)
                        if (isListening.value == true) listenLoop()
                    } catch (e: Exception) {
                        state.postValue(TranslatorState.Error(e.message ?: "오류"))
                        delay(1500)
                        if (isListening.value == true) listenLoop()
                    }
                }
            }
            override fun onError(message: String) {
                currentAmplitude.postValue(0)
                // 오류여도 자동으로 다시 대기
                viewModelScope.launch {
                    if (isListening.value == true) {
                        delay(500)
                        listenLoop()
                    }
                }
            }
        })
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