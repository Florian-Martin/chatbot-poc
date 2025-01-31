package chatbot.poc.androidapp.screens.chat

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import chatbot.poc.androidapp.data.AIModel
import chatbot.poc.androidapp.data.AudioConfig
import chatbot.poc.androidapp.data.ChatBotUiState
import chatbot.poc.androidapp.data.ChatHistoryItem
import chatbot.poc.androidapp.data.ChatMessage
import chatbot.poc.androidapp.data.ChatWithAudioRequest
import chatbot.poc.androidapp.data.GPTMessage
import chatbot.poc.androidapp.data.GPTRequest
import chatbot.poc.androidapp.data.GPTRole
import chatbot.poc.androidapp.data.InputAudio
import chatbot.poc.androidapp.data.MessageContent
import chatbot.poc.androidapp.data.OutputMode
import chatbot.poc.androidapp.data.gptRole
import chatbot.poc.androidapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.S)
class SpeakViewModel(private val context: Context) : ViewModel(), TextToSpeech.OnInitListener {

    // region VARIABLES
    private var audioFile: File? = null
    private val recorder: AudioRecorder = AudioRecorder(context)
    private var mediaPlayer: MediaPlayer? = null

        private val textToSpeech: TextToSpeech = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionSupportCallback: RecognitionSupportCallback? = null
    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            20000
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
    }

    private val _uiState = MutableStateFlow(ChatBotUiState())
    val uiState: StateFlow<ChatBotUiState> = _uiState.asStateFlow()

    private val promptsContext =
        "You are a helpful assistant for a tourism app. Interpret user requests and provide contextual answers in less than 30 words"

    private val systemMessage = listOf(
        GPTMessage(
            role = GPTRole.System.role,
            content = promptsContext
        )
    )
    // endregion


    // region LIFECYCLE
    init {
        setupSpeechRecognizer()
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
    // endregion


    // region METHODS
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                error("Language not supported")
            }
        } else {
            error("TextToSpeech initialization failed")
        }
    }

    fun setSelectedOutputMode(outputMode: OutputMode) {
        _uiState.update { it.copy(selectedOutputMode = outputMode) }
    }

    fun startRecording() {
        speechRecognizer?.startListening(recognizerIntent)
        recorder.startRecording()
    }

    fun stopRecording() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            audioFile = recorder.stopRecording()
            audioFile?.let { file ->
                when (uiState.value.selectedOutputMode) {
                    OutputMode.Transcribe,
                    OutputMode.Interpret,
                    OutputMode.AndroidTextToSpeech -> {
                        transcribeAudio(file)
                    }

                    OutputMode.AiAudio -> {
                        val audioDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                        if (audioDir == null || !audioDir.exists()) {
                            error("Failed to access external music directory")
                        }

                        val outputWavFile =
                            File(audioDir, "audio_${System.currentTimeMillis()}.wav")

                        file.convertM4AToWAV(
                            outputFile = outputWavFile,
                            onSuccess = {
                                sendAudioChatRequest(outputWavFile, context)
                            },
                            onFailure = {
                                Log.e("******", "Error: ${file.name}: $it")
                            })
                    }

                    OutputMode.AndroidSpeechToText -> {
                        speechRecognizer?.stopListening()
                        _uiState.update { it.copy(isProcessing = false) }
                    }
                }
            } ?: run {
                _uiState.update { it.copy(isProcessing = false, showBottomSheet = false) }
            }
        }
    }

    private fun transcribeAudio(file: File) {
        viewModelScope.launch {
            try {
                val audioPart = MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    file.asRequestBody("audio/m4a".toMediaType())
                )

                val modelRequestBody =
                    RequestBody.create("text/plain".toMediaType(), AIModel.Whisper.model)

                val response = RetrofitClient.apiService.transcribeAudio(
                    file = audioPart,
                    model = modelRequestBody
                )

                _uiState.update { state ->
                    state.copy(
                        transcription = response.text,
                        showBottomSheet = state.selectedOutputMode == OutputMode.Transcribe ||
                                state.selectedOutputMode == OutputMode.Interpret
                    )
                }

                if (uiState.value.selectedOutputMode == OutputMode.Interpret
                    || uiState.value.selectedOutputMode == OutputMode.AndroidTextToSpeech
                ) {
                    interpretRequest()
                }
            } catch (e: Exception) {
                Log.e("******", "Error: ${(e as HttpException).response()?.errorBody()?.string()}")
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun interpretRequest() {
        viewModelScope.launch {
            try {
                val messages = systemMessage + listOf(
                    GPTMessage(
                        role = GPTRole.User.role,
                        content = uiState.value.transcription
                    )
                )

                val gptRequest = GPTRequest(messages = messages)
                val gptResponse = RetrofitClient.apiService.interpretRequest(request = gptRequest)

                val (role, content) = gptResponse.choices.firstOrNull()?.message
                    ?.let { it.gptRole to it.content }
                    ?: (null to null)

                if (role != null && content != null) {
                    _uiState.update { state ->
                        state.copy(
                            botResponse = content,
                            chatHistory = state.chatHistory
                                    + ChatHistoryItem(GPTRole.User, state.transcription)
                                    + ChatHistoryItem(role, content)
                        )
                    }

                    if (uiState.value.selectedOutputMode == OutputMode.AndroidTextToSpeech) {
                        speakResponse(content)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun sendAudioChatRequest(audioFile: File, context: Context) {
        viewModelScope.launch {
            try {
                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                val messages = listOf(
                    ChatMessage(
                        role = GPTRole.User.role,
                        content = listOf(
                            MessageContent(
                                type = "text",
                                text = "Please answer based on the following audio."
//                                text = "$promptsContext, do it based on the following audio."
                            ),
                            MessageContent(
                                type = "input_audio",
                                input_audio = InputAudio(
                                    data = base64Audio,
                                    format = "wav"
                                )
                            )
                        )
                    )
                )

                val request = ChatWithAudioRequest(
                    model = AIModel.GPT4oAudioPreview.model,
                    modalities = listOf("text", "audio"),
                    audio = AudioConfig(voice = "ballad", format = "wav"),
                    messages = messages
                )

                val response = RetrofitClient.apiService.chatWithAudio(request = request)

                val choice = response.choices.firstOrNull()
                val data = choice?.message?.audio?.data

                if (data != null) {
                    val decodedAudioBytes = Base64.decode(data, Base64.DEFAULT)
                    val audioOutputFile = File(context.cacheDir, "bot_response_audio.wav")
                    audioOutputFile.writeBytes(decodedAudioBytes)

                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(audioOutputFile.absolutePath)
                        prepare()
                        start()
                    }
                }
            } catch (e: HttpException) {
                Log.e("******", "Error: ${e.response()?.errorBody()?.string()}")
            } catch (e: Exception) {
                Log.e("******", "Error: ${e.message ?: "no message"}")
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun speakResponse(response: String) {
        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun File.convertM4AToWAV(
        outputFile: File,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val command = "-i ${this.absolutePath} -ar 16000 -ac 1 ${outputFile.absolutePath}"
        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                onSuccess()
            } else {
                onFailure("Conversion failed with return code: $returnCode")
            }
        }
    }

    fun dismissBottomSheet() {
        _uiState.update { it.copy(showBottomSheet = false) }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("******", "onReadyForSpeech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("******", "onBeginningOfSpeech")
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onLanguageDetection(results: Bundle) {
                    Log.d("******", "onLanguageDetection results: $results")
                    super.onLanguageDetection(results)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    Log.d("******", "onBufferReceived buffer: $buffer")
                }

                override fun onEndOfSpeech() {
                    Log.d("******", "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    Log.d("******", "onError: $error")
                }

                override fun onResults(results: Bundle?) {
                    Log.d("******", "onResults")
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString()
                    data?.let { data ->
                        Log.d("******", "Transcription: $data")
                        _uiState.update { it.copy(transcription = data) }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    Log.d("******", "onPartialResults: $partialResults")
                    val data =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.joinToString()
                    data?.let { data ->
                        Log.d("******", "Partial Transcription: $data")
                        _uiState.update { it.copy(transcription = data) }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d("******", "onEvent eventType: $eventType")
                }
            }

            viewModelScope.launch(Dispatchers.Main) {
                speechRecognizer = SpeechRecognizer
                    .createSpeechRecognizer(context)
                    .also {
                        it.setRecognitionListener(listener)
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    recognitionSupportCallback = object : RecognitionSupportCallback {
                        override fun onSupportResult(p0: RecognitionSupport) {
                            Log.d("******", "RecognitionSupportCallback onSupportResult: $p0")
                        }

                        override fun onError(p0: Int) {
                            Log.e("******", "RecognitionSupportCallback onError: $p0")
                        }
                    }
                    recognitionSupportCallback?.let {
                        speechRecognizer?.checkRecognitionSupport(
                            /* recognizerIntent = */ recognizerIntent,
                            /* executor = */ Executors.newSingleThreadExecutor(),
                            /* supportListener = */ it
                        )
                    }
                }
            }
        }
    }
    // endregion
}

@RequiresApi(Build.VERSION_CODES.S)
class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startRecording() {
        val audioDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (audioDir == null || !audioDir.exists()) {
            throw IllegalStateException("Failed to access external music directory")
        }

        audioFile = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")

        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording(): File? {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        return audioFile
    }
}
