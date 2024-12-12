package chatbot.poc.androidapp.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import chatbot.poc.androidapp.R
import chatbot.poc.androidapp.data.OutputMode
import chatbot.poc.androidapp.ui.theme.FbBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ChatScreen(viewModel: SpeakViewModel) {

    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ModeSelectionButtons(
                selectedOutputMode = uiState.selectedOutputMode,
                onModeSelected = { viewModel.setSelectedOutputMode(it) }
            )
        }

        HoldToSpeakButton(
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecording() },
            isProcessing = uiState.isProcessing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }

    if (uiState.showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                viewModel.dismissBottomSheet()
            },
            containerColor = Color.White,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
                    .background(Color.White)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val maxHeight = LocalContext.current.resources.displayMetrics.heightPixels * 0.9f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(0.dp, maxHeight.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (uiState.selectedOutputMode) {
                            OutputMode.Transcribe -> uiState.transcription
                            OutputMode.Interpret -> uiState.botResponse
                            OutputMode.AndroidTextToSpeech,
                            OutputMode.AiAudio -> ""
                            OutputMode.AndroidSpeechToText -> TODO()
                        },
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HoldToSpeakButton(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    isProcessing: Boolean,
    baseSize: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animatedSize = remember { Animatable(baseSize.value) }
    val isPressed = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(animatedSize.value.dp)
            .clip(CircleShape)
            .background(if (isProcessing) Color.White else FbBlue)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed.value = true
                        scope.launch {
                            animatedSize.animateTo(
                                targetValue = baseSize.value * 0.8f,
                                animationSpec = tween(1500)
                            )
                        }
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            onStartRecording()
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isPressed.value) {
                            isPressed.value = false
                            scope.launch {
                                animatedSize.animateTo(
                                    targetValue = baseSize.value,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )
                            }
                            onStopRecording()
                        }
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                color = FbBlue,
                modifier = Modifier.size((animatedSize.value * 0.5).dp)
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_micro),
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size((animatedSize.value * 0.5).dp)
            )
        }
    }
}

@Composable
fun ModeSelectionButtons(
    selectedOutputMode: OutputMode,
    onModeSelected: (OutputMode) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ModeButton(
            text = "Transcribe",
            isSelected = selectedOutputMode == OutputMode.Transcribe,
            onClick = { onModeSelected(OutputMode.Transcribe) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModeButton(
            text = "Interpret",
            isSelected = selectedOutputMode == OutputMode.Interpret,
            onClick = { onModeSelected(OutputMode.Interpret) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModeButton(
            text = "Android Text-to-Speech",
            isSelected = selectedOutputMode == OutputMode.AndroidTextToSpeech,
            onClick = { onModeSelected(OutputMode.AndroidTextToSpeech) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModeButton(
            text = "Android Speech-to-Text",
            isSelected = selectedOutputMode == OutputMode.AndroidSpeechToText,
            onClick = { onModeSelected(OutputMode.AndroidSpeechToText) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModeButton(
            text = "AI Audio Output",
            isSelected = selectedOutputMode == OutputMode.AiAudio,
            onClick = { onModeSelected(OutputMode.AiAudio) }
        )
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color.White else FbBlue
    val contentColor = if (isSelected) FbBlue else Color.White
    val border = if (isSelected) BorderStroke(1.dp, FbBlue) else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(
                if (border != null)
                    Modifier.border(border, RoundedCornerShape(8.dp))
                else
                    Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}