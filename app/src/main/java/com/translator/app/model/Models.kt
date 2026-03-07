package com.translator.app.model

data class Language(
    val code: String,
    val ttsCode: String,
    val displayName: String,
    val nativeName: String,
    val flag: String,
    val whisperCode: String
)

object Languages {
    val KOREAN = Language("ko", "ko-KR", "한국어", "한국어", "🇰🇷", "korean")
    val ENGLISH = Language("en", "en-US", "영어", "English", "🇺🇸", "english")
    val JAPANESE = Language("ja", "ja-JP", "일본어", "日本語", "🇯🇵", "japanese")
    val CHINESE_MAINLAND = Language("zh", "zh-CN", "북경어 (중국)", "普通話 (中国)", "🇨🇳", "chinese")
    val CHINESE_TAIWAN = Language("zh-TW", "zh-TW", "북경어 (대만)", "普通話 (台灣)", "🇹🇼", "chinese")
    val CANTONESE = Language("yue", "zh-HK", "광동어 (홍콩)", "粵語 (香港)", "🇭🇰", "cantonese")
    val THAI = Language("th", "th-TH", "태국어", "ภาษาไทย", "🇹🇭", "thai")
    val VIETNAMESE = Language("vi", "vi-VN", "베트남어", "Tiếng Việt", "🇻🇳", "vietnamese")
    val SPANISH = Language("es", "es-ES", "스페인어", "Español", "🇪🇸", "spanish")

    val TARGET_LANGUAGES = listOf(
        ENGLISH, JAPANESE, CHINESE_MAINLAND, CHINESE_TAIWAN, CANTONESE, THAI, VIETNAMESE, SPANISH
    )

    fun fromWhisperCode(code: String): Language? {
        val normalized = code.lowercase().trim()
        return listOf(KOREAN, ENGLISH, JAPANESE, CHINESE_MAINLAND, CHINESE_TAIWAN, CANTONESE, THAI, VIETNAMESE, SPANISH)
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
    KOREAN_TO_FOREIGN,
    FOREIGN_TO_KOREAN
}

sealed class TranslatorState {
    object Idle : TranslatorState()
    object Recording : TranslatorState()
    object Transcribing : TranslatorState()
    object Translating : TranslatorState()
    data class Success(val result: TranslationResult) : TranslatorState()
    data class Error(val message: String) : TranslatorState()
}
