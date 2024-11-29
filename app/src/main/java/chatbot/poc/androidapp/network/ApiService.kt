package chatbot.poc.androidapp.network

import chatbot.poc.androidapp.BuildConfig
import chatbot.poc.androidapp.data.ChatWithAudioRequest
import chatbot.poc.androidapp.data.ChatWithAudioResponse
import chatbot.poc.androidapp.data.GPTRequest
import chatbot.poc.androidapp.data.GPTResponse
import chatbot.poc.androidapp.data.WhisperResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

object RetrofitClient {

    private const val BASE_URL = "https://api.openai.com/"
    private const val API_KEY = BuildConfig.OPEN_AI_API_KEY

    interface ApiService {
        @Multipart
        @POST("v1/audio/transcriptions")
        suspend fun transcribeAudio(
            @Header("Authorization") apiKey: String = API_KEY,
            @Part file: MultipartBody.Part,
            @Part("model") model: RequestBody
        ): WhisperResponse

        @POST("v1/chat/completions")
        suspend fun interpretRequest(
            @Header("Authorization") apiKey: String = API_KEY,
            @Body request: GPTRequest
        ): GPTResponse

        @POST("v1/chat/completions")
        suspend fun chatWithAudio(
            @Header("Authorization") apiKey: String = API_KEY,
            @Body request: ChatWithAudioRequest
        ): ChatWithAudioResponse
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}