package com.visionmate.pro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.ui.theme.BgDark
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.SurfaceDark
import com.visionmate.pro.ui.theme.TextGray
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    initialContactName: String,
    initialContactPhone: String,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSaveContact: (String, String) -> Unit,
    onFindCaneClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    var cautionRange by remember { mutableFloatStateOf(150f) }
    var criticalRange by remember { mutableFloatStateOf(30f) }

    var contactName by remember { mutableStateOf(initialContactName) }
    var contactPhone by remember { mutableStateOf(initialContactPhone) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text("VisionMate Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(modifier = Modifier.height(16.dp))

                // Language Selection
                Text("Select Assistant Language", fontSize = 14.sp, color = TextGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppLanguage.values().forEach { lang ->
                        val isSelected = (lang == currentLanguage)
                        Button(
                            onClick = { onLanguageSelected(lang) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) CyanAccent else BgDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(lang.displayName, color = if (isSelected) BgDark else TextWhite, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Emergency Contact Fields
                Text("Guardian Emergency Contact", fontSize = 14.sp, color = TextWhite)
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Guardian Name", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("Phone Number", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onFindCaneClicked() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔔 Find My Cane", color = BgDark, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { 
                        onSaveContact(contactName, contactPhone)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BgDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Done", color = TextWhite)
                }
            }
        }
    }
}
