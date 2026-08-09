package com.visionmate.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionmate.pro.model.ProximityState
import com.visionmate.pro.ui.theme.CautionOrange
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.SafeGreen
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun ProximityIndicator(
    proximityState: ProximityState,
    modifier: Modifier = Modifier
) {
    val (bgColor, label) = when (proximityState) {
        ProximityState.SAFE -> Pair(SafeGreen.copy(alpha = 0.25f), "SAFE")
        ProximityState.NEAR -> Pair(CautionOrange.copy(alpha = 0.25f), "NEAR")
        ProximityState.TOO_NEAR -> Pair(DangerRed.copy(alpha = 0.25f), "TOO NEAR")
    }

    val textColor = when (proximityState) {
        ProximityState.SAFE -> SafeGreen
        ProximityState.NEAR -> CautionOrange
        ProximityState.TOO_NEAR -> DangerRed
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = textColor
        )
    }
}
