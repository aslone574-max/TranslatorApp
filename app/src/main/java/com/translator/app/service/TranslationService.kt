package com.translator.app.service

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translator.app.model.*
import com.translator.app.network.ChatMsg
import com.translator.app.network.ChatRequest
import com.translator.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class TranslationService(private val context: Context) {

    private val prefs = context.getSharedPreferences("translator_prefs", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString("api_key", "") ?: ""
    fun saveApiKey(key: String) = prefs.edit().putString("api_key", key).apply()

    // ─── 음성 번역 (하이브리드) ───
    suspend fun translateAudio(audioFile: File, targetLanguage: Language): TranslationResult =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            val isOnline = NetworkChecker.isOnline(context)

            if (isOnline && apiKey.isNotEmpty()) {
                translateAudioGroq(audioFile, targetLanguage, apiKey)
            } else {
                // 오프라인: 안드로이드 음성인식은 SpeechService에서 처리
                // 여기선 텍스트 번역만 (SpeechService 결과를 받아서)
                throw Exception("음성 번역은 SpeechService를 통해 처리됩니다.")
            }
        }

    // ─── 텍스트 번역 (하이브리드) ───
    suspend fun translateText(text: String, targetLanguage: Language): TranslationResult =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            val isOnline = NetworkChecker.isOnline(context)

            if (isOnline && apiKey.isNotEmpty()) {
                translateTextGroq(text, targetLanguage, apiKey)
            } else {
                translateTextMlKit(text, targetLanguage)
            }
        }

    // ─── Groq 온라인 번역 ───
    private suspend fun translateAudioGroq(
        audioFile: File, targetLanguage: Language, apiKey: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val auth = "Bearer $apiKey"
        val audioPart = MultipartBody.Part.createFormData(
            "file", audioFile.name,
            audioFile.asRequestBody("audio/m4a".toMediaType())
        )
        val whisperResult = NetworkClient.api.transcribe(
            auth = auth,
            file = audioPart,
            model = "whisper-large-v3".toRequestBody("text/plain".toMediaType()),
            format = "verbose_json".toRequestBody("text/plain".toMediaType()),
            language = null
        )
        val text = whisperResult.text.trim()
        if (text.isEmpty()) throw Exception("음성을 인식하지 못했습니다.")

        val detectedCode = whisperResult.language ?: "unknown"
        val detectedLang = Languages.fromWhisperCode(detectedCode)
        val isKorean = detectedCode.startsWith("ko") || detectedLang == Languages.KOREAN
        val fromLang = detectedLang ?: if (isKorean) Languages.KOREAN else targetLanguage
        val toLang = if (isKorean) targetLanguage else Languages.KOREAN
        val direction = if (isKorean) TranslationDirection.KOREAN_TO_FOREIGN else TranslationDirection.FOREIGN_TO_KOREAN

        val translated = groqChat(auth, text, fromLang, toLang)
        TranslationResult(text, translated, detectedLang, targetLanguage, direction)
    }

    private suspend fun translateTextGroq(
        text: String, targetLanguage: Language, apiKey: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val auth = "Bearer $apiKey"
        val isKorean = containsKorean(text)
        val fromLang = if (isKorean) Languages.KOREAN else targetLanguage
        val toLang = if (isKorean) targetLanguage else Languages.KOREAN
        val direction = if (isKorean) TranslationDirection.KOREAN_TO_FOREIGN else TranslationDirection.FOREIGN_TO_KOREAN
        val translated = groqChat(auth, text, fromLang, toLang)
        TranslationResult(text, translated, fromLang, targetLanguage, direction)
    }

    private suspend fun groqChat(auth: String, text: String, from: Language, to: Language): String {
        val system = "당신은 전문 통역사입니다. ${from.displayName}에서 ${to.displayName}으로 번역하세요. 번역된 텍스트만 출력하세요."
        val response = NetworkClient.api.chat(
            auth = auth,
            request = ChatRequest(messages = listOf(ChatMsg("system", system), ChatMsg("user", text)))
        )
        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw Exception("번역 결과를 받지 못했습니다.")
    }

    // ─── ML Kit 오프라인 번역 ───
    suspend fun translateTextMlKit(text: String, targetLanguage: Language): TranslationResult =
        withContext(Dispatchers.IO) {
            val isKorean = containsKorean(text)
            val fromLang = if (isKorean) Languages.KOREAN else targetLanguage
            val toLang = if (isKorean) targetLanguage else Languages.KOREAN
            val direction = if (isKorean) TranslationDirection.KOREAN_TO_FOREIGN else TranslationDirection.FOREIGN_TO_KOREAN

            val fromCode = toMlKitCode(fromLang.code) ?: throw Exception("${fromLang.displayName} 오프라인 번역 미지원")
            val toCode = toMlKitCode(toLang.code) ?: throw Exception("${toLang.displayName} 오프라인 번역 미지원")

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(fromCode)
                .setTargetLanguage(toCode)
                .build()
            val translator = Translation.getClient(options)
            try {
                Tasks.await(translator.downloadModelIfNeeded())
                val translated = Tasks.await(translator.translate(text))
                TranslationResult(text, translated, fromLang, targetLanguage, direction)
            } finally {
                translator.close()
            }
        }

    fun isOnline() = NetworkChecker.isOnline(context)
    fun hasApiKey() = getApiKey().isNotEmpty()

    private fun containsKorean(text: String) =
        text.any { it in '\uAC00'..'\uD7A3' || it in '\u1100'..'\u11FF' }

    private fun toMlKitCode(code: String): String? = when (code) {
        "ko" -> TranslateLanguage.KOREAN
        "en" -> TranslateLanguage.ENGLISH
        "ja" -> TranslateLanguage.JAPANESE
        "zh", "zh-TW", "yue" -> TranslateLanguage.CHINESE
        "th" -> TranslateLanguage.THAI
        "vi" -> TranslateLanguage.VIETNAMESE
        else -> null
    }
}
