package com.visionmate.pro.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.visionmate.pro.ui.theme.*

@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionDialog(
    pairedDevices: List<BluetoothDevice>,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Select Your Smart Cane",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "No paired devices found. Please pair your cane in Android Settings first.",
                        color = TextGray,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(pairedDevices) { device ->
                            DeviceItem(
                                name = device.name ?: "Unknown Device",
                                address = device.address,
                                onClick = {
                                    onDeviceSelected(device.address)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BgDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = TextWhite)
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    name: String,
    address: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = name, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = address, color = TextGray, fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
}
