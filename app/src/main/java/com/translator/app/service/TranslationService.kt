package com.translator.app.service

import com.translator.app.model.*
import com.translator.app.network.ChatMsg
import com.translator.app.network.ChatRequest
import com.translator.app.network.NetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class TranslationService(private val apiKey: String) {

    /**
     * 오디오 파일 → 전사 + 언어 감지 + 번역
     * @param audioFile 녹음 파일
     * @param targetLanguage 목표 언어
     */
    suspend fun translateAudio(
        audioFile: File,
        targetLanguage: Language
    ): TranslationResult {
        val auth = "Bearer $apiKey"

        // ─── Step 1: Groq Whisper로 STT + 언어 감지 ───
        val audioPart = MultipartBody.Part.createFormData(
            "file", audioFile.name,
            audioFile.asRequestBody("audio/m4a".toMediaType())
        )
        val modelBody = "whisper-large-v3".toRequestBody("text/plain".toMediaType())
        val formatBody = "verbose_json".toRequestBody("text/plain".toMediaType())

        val whisperResult = NetworkClient.api.transcribe(
            auth = auth,
            file = audioPart,
            model = modelBody,
            format = formatBody,
            language = null  // null = 자동 감지
        )

        val originalText = whisperResult.text.trim()
        if (originalText.isEmpty()) throw Exception("음성을 인식하지 못했습니다.\n다시 말씀해 주세요.")

        // 감지된 언어
        val detectedLangCode = whisperResult.language ?: "unknown"
        val detectedLanguage = Languages.fromWhisperCode(detectedLangCode)

        // ─── Step 2: 방향 결정 ───
        val isKorean = detectedLangCode.startsWith("ko") || detectedLanguage == Languages.KOREAN
        val direction = if (isKorean) TranslationDirection.KOREAN_TO_FOREIGN
                        else TranslationDirection.FOREIGN_TO_KOREAN

        val fromLang = detectedLanguage ?: if (isKorean) Languages.KOREAN else targetLanguage
        val toLang   = if (isKorean) targetLanguage else Languages.KOREAN

        // ─── Step 3: LLaMA로 번역 ───
        val translatedText = translate(auth, originalText, fromLang, toLang)

        return TranslationResult(
            originalText = originalText,
            translatedText = translatedText,
            detectedLanguage = detectedLanguage,
            targetLanguage = targetLanguage,
            direction = direction
        )
    }

    private suspend fun translate(
        auth: String,
        text: String,
        from: Language,
        to: Language
    ): String {
        val systemPrompt = """당신은 전문 통역사입니다.
주어진 텍스트를 ${from.displayName}에서 ${to.displayName}(으)로 번역하세요.

규칙:
1. 오직 번역된 텍스트만 출력하세요 (설명, 주석 없음)
2. 자연스럽고 구어체로 번역하세요 (통역 상황임)
3. 원문의 감정과 뉘앙스를 살려주세요
4. ${to.displayName}이 광동어(粵語)인 경우 홍콩 광동어로 번역하세요
5. 중국어 번체인 경우 대만 표준 중국어로 번역하세요"""

        val response = NetworkClient.api.chat(
            auth = auth,
            request = ChatRequest(
                messages = listOf(
                    ChatMsg("system", systemPrompt),
                    ChatMsg("user", text)
                )
            )
        )

        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw Exception("번역 결과를 받지 못했습니다.")
    }

    /**
     * 텍스트 직접 번역 (텍스트 입력 모드)
     */
    suspend fun translateText(
        text: String,
        targetLanguage: Language
    ): TranslationResult {
        val auth = "Bearer $apiKey"

        // 간단한 언어 감지 (한국어 여부)
        val isKorean = containsKorean(text)
        val fromLang = if (isKorean) Languages.KOREAN else targetLanguage
        val toLang   = if (isKorean) targetLanguage else Languages.KOREAN
        val direction = if (isKorean) TranslationDirection.KOREAN_TO_FOREIGN
                        else TranslationDirection.FOREIGN_TO_KOREAN

        val translated = translate(auth, text, fromLang, toLang)

        return TranslationResult(
            originalText = text,
            translatedText = translated,
            detectedLanguage = fromLang,
            targetLanguage = targetLanguage,
            direction = direction
        )
    }

    private fun containsKorean(text: String): Boolean {
        return text.any { it in '\uAC00'..'\uD7A3' || it in '\u1100'..'\u11FF' }
    }
}
