package com.translator.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.translator.app.model.*
import com.translator.app.service.AudioRecorder
import com.translator.app.service.SpeechService
import com.translator.app.service.TranslationService
import com.translator.app.service.TTSService
import kotlinx.coroutines.launch

class TranslatorViewModel(app: Application) : AndroidViewModel(app) {

    val state = MutableLiveData<TranslatorState>(TranslatorState.Idle)
    val selectedLanguage = MutableLiveData<Language>(Languages.ENGLISH)
    val currentAmplitude = MutableLiveData<Int>(0)
    val modeStatus = MutableLiveData<String>()  // "🌐 온라인" or "📴 오프라인"

    val translationService = TranslationService(app)
    private val speechService = SpeechService(app)
    private val audioRecorder = AudioRecorder(app)
    private val ttsService = TTSService(app)

    init { updateModeStatus() }

    fun updateModeStatus() {
        val online = translationService.isOnline()
        val hasKey = translationService.hasApiKey()
        modeStatus.postValue(when {
            online && hasKey -> "🌐 온라인 (고품질)"
            online && !hasKey -> "🔑 API 키 없음 → 오프라인 모드"
            else -> "📴 오프라인 모드"
        })
    }

    fun getApiKey() = translationService.getApiKey()
    fun saveApiKey(key: String) {
        translationService.saveApiKey(key)
        updateModeStatus()
    }

    fun selectLanguage(lang: Language) { selectedLanguage.value = lang }

    fun startRecording() {
        if (state.value is TranslatorState.Recording) return
        ttsService.stop()
        state.postValue(TranslatorState.Recording)
        updateModeStatus()

        val target = selectedLanguage.value ?: Languages.ENGLISH
        val online = translationService.isOnline()
        val hasKey = translationService.hasApiKey()

        if (online && hasKey) {
            // 온라인: AudioRecorder로 녹음
            audioRecorder.startRecording()
        } else {
            // 오프라인: SpeechService 사용
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
                            val result = translationService.translateTextMlKit(text, target)
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
    }

    fun stopRecordingAndTranslate() {
        if (state.value !is TranslatorState.Recording) return
        currentAmplitude.postValue(0)
        val target = selectedLanguage.value ?: Languages.ENGLISH
        val online = translationService.isOnline()
        val hasKey = translationService.hasApiKey()

        if (online && hasKey) {
            val audioFile = audioRecorder.stopRecording() ?: run {
                state.postValue(TranslatorState.Error("녹음 파일 저장 실패"))
                return
            }
            viewModelScope.launch {
                try {
                    state.postValue(TranslatorState.Transcribing)
                    val result = translationService.translateAudio(audioFile, target)
                    state.postValue(TranslatorState.Success(result))
                    autoPlayTTS(result)
                } catch (e: Exception) {
                    state.postValue(TranslatorState.Error(e.message ?: "오류"))
                } finally {
                    audioFile.delete()
                }
            }
        } else {
            speechService.stopListening()
            state.postValue(TranslatorState.Transcribing)
        }
    }

    fun cancelRecording() {
        audioRecorder.cancelRecording()
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
        val lang = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN)
            result.targetLanguage else Languages.KOREAN
        ttsService.speak(result.translatedText, lang)
    }

    fun resetState() { state.postValue(TranslatorState.Idle) }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.cancelRecording()
        speechService.destroy()
        ttsService.shutdown()
    }
}
