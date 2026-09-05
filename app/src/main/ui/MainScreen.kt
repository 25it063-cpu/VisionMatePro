package com.visionmate.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionmate.pro.model.AssistantState
import com.visionmate.pro.ui.components.*
import com.visionmate.pro.ui.theme.*
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
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val emergencyContact by viewModel.contactManager.contact.collectAsState()
    val recognizedText by viewModel.speechRecognizerManager.recognizedText.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

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
                
                BluetoothStatus(
                    status = caneState.connectionState.bluetoothStatus,
                    onClick = {
                        viewModel.refreshPairedDevices()
                        showDeviceDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                CaneBatteryStatus(
                    batteryPercent = caneState.sensorData.caneBatteryPercent,
                    batteryState = caneState.batteryState
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProximityIndicator(proximityState = proximityState)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AssistantOrb(state = assistantState, onClick = { viewModel.onMicClicked() })
                Spacer(modifier = Modifier.height(24.dp))
                AssistantStateText(text = alertText, state = assistantState)
                
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
                        AssistantState.SPEAKING -> "🗣️ Speaking..."
                        AssistantState.EMERGENCY -> "🆘 Emergency Countdown"
                        else -> "🎤 Ready"
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

        if (showDeviceDialog) {
            DeviceSelectionDialog(
                pairedDevices = pairedDevices,
                onDeviceSelected = { address -> viewModel.connectToDevice(address) },
                onDismiss = { showDeviceDialog = false }
            )
        }
    }
}
