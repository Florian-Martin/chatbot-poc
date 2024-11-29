package chatbot.poc.androidapp.data

import com.google.gson.annotations.SerializedName


data class WhisperResponse(
    val text: String
)

data class GPTRequest(
    val model: String = AIModel.GPT4oMini.model,
    val messages: List<GPTMessage>,
    @SerializedName("max_completion_tokens")
    val maxTokens: Int = 100,
    @SerializedName("n")
    val chatCompletionChoices: Int = 1
)

data class GPTResponse(
    val choices: List<GPTChoice>
)

data class GPTChoice(
    val message: GPTMessage
)

data class GPTMessage(
    val role: String,
    val content: String
)

val GPTMessage.gptRole: GPTRole
    get() = when (role) {
        "system" -> GPTRole.System
        "user" -> GPTRole.User
        "assistant" -> GPTRole.Assistant
        else -> GPTRole.System
    }

data class ChatWithAudioRequest(
    val model: String = AIModel.GPT4oAudioPreview.model,
    val modalities: List<String>,
    val audio: AudioConfig,
    val messages: List<ChatMessage>,
    @SerializedName("max_completion_tokens")
    val maxTokens: Int = 100,
    @SerializedName("n")
    val chatCompletionChoices: Int = 1
)

data class AudioConfig(
    val voice: String = "alloy",
    val format: String = "wav"
)

data class ChatMessage(
    val role: String,
    val content: List<MessageContent>
)

data class MessageContent(
    val type: String,
    val text: String? = null,
    val input_audio: InputAudio? = null,
    val audio: AudioData? = null
)

data class InputAudio(
    val data: String,
    val format: String
)

data class AudioData(
    val data: String,
    val format: String
)

data class ChatWithAudioResponse(
    val choices: List<ChatWithAudioChoice>
)

data class ChatWithAudioChoice(
    val message: ChatWithAudioMessage
)

data class ChatWithAudioMessage(
    val role: String,
    val content: List<MessageContent>,
    val audio: AudioResponse
)

data class AudioResponse(
    val id: String,
    val data: String,
    val transcript: String
)