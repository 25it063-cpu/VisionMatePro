package com.visionmate.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionmate.pro.model.AssistantState
import com.visionmate.pro.ui.components.AssistantOrb
import com.visionmate.pro.ui.components.AssistantStateText
import com.visionmate.pro.ui.components.BluetoothStatus
import com.visionmate.pro.ui.components.BottomControls
import com.visionmate.pro.ui.components.CaneBatteryStatus
import com.visionmate.pro.ui.components.ProximityIndicator
import com.visionmate.pro.ui.components.SystemStatus
import com.visionmate.pro.ui.components.VisionMateLogo
import com.visionmate.pro.ui.theme.BgDark
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.TextGray
import com.visionmate.pro.ui.theme.TextWhite
import com.visionmate.pro.viewmodel.VisionMateViewModel

@Composable
fun MainScreen(
    viewModel: VisionMateViewModel,
    modifier: Modifier = Modifier
) {
    val caneState by viewModel.caneState.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val proximityState by viewModel.proximityState.collectAsState()
    val alertText by viewModel.currentAlertText.collectAsState()
    val systemHealth by viewModel.systemHealth.collectAsState()
    val emergencyState by viewModel.emergencyState.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val emergencyContact by viewModel.contactManager.contact.collectAsState()
    val recognizedText by viewModel.speechRecognizerManager.recognizedText.collectAsState()
    val preferences by viewModel.preferencesRepository.preferences.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var cameraUrlInput by remember(preferences.cameraStreamUrl) { mutableStateOf(preferences.cameraStreamUrl) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                viewModel.onMicClicked()
            }
            .padding(vertical = 24.dp, horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VisionMateLogo(modifier = Modifier.padding(top = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
                BluetoothStatus(status = caneState.connectionState.bluetoothStatus, onClick = { viewModel.refreshPairedDevices() })
                Spacer(modifier = Modifier.height(8.dp))
                CaneBatteryStatus(
                    batteryPercent = caneState.sensorData.caneBatteryPercent,
                    batteryState = caneState.batteryState
                )
                Spacer(modifier = Modifier.height(8.dp))
                SystemStatus(
                    isVisionReady = systemHealth.cameraStream.isReady,
                    onClick = { showCameraDialog = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProximityIndicator(proximityState = proximityState)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AssistantOrb(state = assistantState, onClick = { viewModel.onMicClicked() })
                Spacer(modifier = Modifier.height(24.dp))
                AssistantStateText(text = alertText, state = assistantState)
                
                // Debug / Help text showing what the phone actually heard
                if (recognizedText.isNotEmpty() && assistantState != AssistantState.LISTENING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Heard: \"$recognizedText\"",
                        color = CyanAccent.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (assistantState) {
                        AssistantState.LISTENING -> "🎤 Listening..."
                        AssistantState.PROCESSING -> "⚡ Thinking..."
                        AssistantState.SPEAKING -> "🔊 Speaking..."
                        AssistantState.EMERGENCY -> "🚨 EMERGENCY ACTIVE"
                        else -> "Tap anywhere or hold cane button to speak"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (assistantState == AssistantState.EMERGENCY) DangerRed else TextGray
                )
            }

            BottomControls(
                isListening = assistantState == AssistantState.LISTENING,
                onChatClicked = { showSettingsDialog = true },
                onMicClicked = { viewModel.onMicClicked() },
                onSosClicked = { viewModel.onSosClicked() },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (showSettingsDialog) {
            SettingsScreen(
                currentLanguage = currentLanguage,
                initialContactName = emergencyContact.name,
                initialContactPhone = emergencyContact.phoneNumber,
                onLanguageSelected = { viewModel.setLanguage(it) },
                onSaveContact = { name, phone -> viewModel.updateEmergencyContact(name, phone) },
                onFindCaneClicked = { viewModel.toggleFindMyCane() },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showCameraDialog) {
            AlertDialog(
                onDismissRequest = { showCameraDialog = false },
                title = { Text(text = "📷 ESP32-CAM Stream Settings", color = TextWhite) },
                text = {
                    Column {
                        Text(
                            text = if (systemHealth.cameraStream.isReady) "Status: Connected & Streaming" else "Status: Not Connected (Close browser tab & turn off mobile data)",
                            color = if (systemHealth.cameraStream.isReady) CyanAccent else DangerRed,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cameraUrlInput,
                            onValueChange = { cameraUrlInput = it },
                            label = { Text("Stream URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateCameraStreamUrl(cameraUrlInput)
                            showCameraDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Connect", color = BgDark)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCameraDialog = false }) {
                        Text("Cancel", color = TextGray)
                    }
                },
                containerColor = BgDark
            )
        }
    }
}
