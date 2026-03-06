package com.translator.app.model

data class Language(
    val code: String,          // Whisper/BCP-47 code
    val ttsCode: String,       // Android TTS locale
    val displayName: String,   // 한국어 이름
    val nativeName: String,    // 현지 이름
    val flag: String,          // 이모지 국기
    val whisperCode: String    // Whisper 언어 코드
)

object Languages {
    val KOREAN = Language("ko", "ko-KR", "한국어", "한국어", "🇰🇷", "korean")
    val ENGLISH = Language("en", "en-US", "영어", "English", "🇺🇸", "english")
    val JAPANESE = Language("ja", "ja-JP", "일본어", "日本語", "🇯🇵", "japanese")
    val CHINESE_SIMPLIFIED = Language("zh", "zh-CN", "중국어(간체)", "中文(简体)", "🇨🇳", "chinese")
    val CHINESE_TRADITIONAL = Language("zh-TW", "zh-TW", "중국어(번체)", "中文(繁體)", "🇹🇼", "chinese")
    val CANTONESE = Language("yue", "zh-HK", "광동어", "粵語", "🇭🇰", "cantonese")
    val THAI = Language("th", "th-TH", "태국어", "ภาษาไทย", "🇹🇭", "thai")
    val VIETNAMESE = Language("vi", "vi-VN", "베트남어", "Tiếng Việt", "🇻🇳", "vietnamese")

    // 타겟 언어 목록 (한국어 제외)
    val TARGET_LANGUAGES = listOf(
        ENGLISH,
        JAPANESE,
        CHINESE_SIMPLIFIED,
        CHINESE_TRADITIONAL,
        CANTONESE,
        THAI,
        VIETNAMESE
    )

    // 코드로 Language 찾기
    fun fromWhisperCode(code: String): Language? {
        val normalized = code.lowercase().trim()
        return listOf(KOREAN, ENGLISH, JAPANESE, CHINESE_SIMPLIFIED, CHINESE_TRADITIONAL, CANTONESE, THAI, VIETNAMESE)
            .find { it.whisperCode == normalized || it.code == normalized || it.code.startsWith(normalized) }
    }
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val detectedLanguage: Language?,
    val targetLanguage: Language,
    val direction: TranslationDirection
)

enum class TranslationDirection {
    KOREAN_TO_FOREIGN,  // 한국어 → 외국어
    FOREIGN_TO_KOREAN   // 외국어 → 한국어
}

sealed class TranslatorState {
    object Idle : TranslatorState()
    object Recording : TranslatorState()
    object Transcribing : TranslatorState()
    object Translating : TranslatorState()
    data class Success(val result: TranslationResult) : TranslatorState()
    data class Error(val message: String) : TranslatorState()
}
