package com.visionmate.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.SafeGreen
import com.visionmate.pro.ui.theme.TextGray
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun BluetoothStatus(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val isConnected = (status == ConnectionStatus.CONNECTED)
    val indicatorColor = if (isConnected) SafeGreen else DangerRed
    val statusText = if (isConnected) "Bluetooth Connected" else "Bluetooth Disconnected"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (isConnected) TextWhite else DangerRed
        )
    }
}
