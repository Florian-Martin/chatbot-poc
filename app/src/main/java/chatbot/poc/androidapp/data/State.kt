package chatbot.poc.androidapp.data

import java.time.Instant

data class ChatBotUiState(
    val selectedOutputMode: OutputMode = OutputMode.Transcribe,
    val chatHistory: List<ChatHistoryItem> = emptyList(),
    val transcription: String = "",
    val botResponse: String = "",
    val isProcessing: Boolean = false,
    val showBottomSheet: Boolean = false
)

data class ChatHistoryItem(
    val role: GPTRole,
    val content: String,
    val timestamp: Instant = Instant.now()
)

sealed class GPTRole {

    abstract val role: String

    data object User : GPTRole() {
        override val role: String
            get() = "user"
    }

    data object System : GPTRole() {
        override val role: String
            get() = "system"
    }

    data object Assistant : GPTRole() {
        override val role: String
            get() = "assistant"
    }
}

sealed class OutputMode {
    data object Transcribe : OutputMode()
    data object Interpret : OutputMode()
    data object AndroidTextToSpeech : OutputMode()
    data object AiAudio : OutputMode()
}

sealed class AIModel {

    abstract val model: String

    data object Whisper : AIModel() {
        override val model: String
            get() = "whisper-1"
    }

    data object GPT4oMini : AIModel() {
        override val model: String
            get() = "gpt-4o-mini"
    }

    data object GPT4oAudioPreview : AIModel() {
        override val model: String
            get() = "gpt-4o-audio-preview"
    }
}