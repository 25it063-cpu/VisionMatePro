package com.visionmate.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.SurfaceDark
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun BottomControls(
    isListening: Boolean,
    onChatClicked: () -> Unit,
    onMicClicked: () -> Unit,
    onSosClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Conversation / Settings Button (💬)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .clickable { onChatClicked() }
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = "Conversation Settings",
                tint = CyanAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        // Central Primary Microphone Button (🎤)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (isListening) CyanAccent else SurfaceDark)
                .clickable { onMicClicked() }
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant Microphone",
                tint = if (isListening) Color(0xFF070B14) else CyanAccent,
                modifier = Modifier.size(36.dp)
            )
        }

        // Emergency SOS Button (SOS)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DangerRed)
                .clickable { onSosClicked() }
        ) {
            Text(
                text = "SOS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }
    }
}
