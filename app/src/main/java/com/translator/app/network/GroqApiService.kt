package com.translator.app.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ─── Whisper Response ───
data class WhisperResponse(
    val text: String,
    val language: String?,          // 감지된 언어 코드
    val segments: List<WhisperSegment>?
)
data class WhisperSegment(val text: String)

// ─── Chat API ───
data class ChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<ChatMsg>,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Double = 0.3   // 번역은 낮은 temperature
)
data class ChatMsg(val role: String, val content: String)
data class ChatResponse(val choices: List<ChatChoice>)
data class ChatChoice(val message: ChatMsg)

// ─── Retrofit Interface ───
interface GroqApi {
    @Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") format: RequestBody,
        @Part("language") language: RequestBody?
    ): WhisperResponse

    @POST("openai/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

object NetworkClient {
    private val okhttp = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: GroqApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(okhttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }
}
