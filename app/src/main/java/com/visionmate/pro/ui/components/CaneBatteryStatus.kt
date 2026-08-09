package com.visionmate.pro.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.visionmate.pro.model.BatteryLevelState
import com.visionmate.pro.ui.theme.CautionOrange
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun CaneBatteryStatus(
    batteryPercent: Int,
    batteryState: BatteryLevelState,
    modifier: Modifier = Modifier
) {
    val textColor = when (batteryState) {
        BatteryLevelState.CRITICAL -> DangerRed
        BatteryLevelState.LOW -> CautionOrange
        BatteryLevelState.NORMAL -> TextWhite
    }

    Text(
        text = "🔋 Cane: $batteryPercent%",
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = textColor,
        modifier = modifier
    )
}
