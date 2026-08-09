package com.visionmate.pro.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.visionmate.pro.model.AssistantState
import com.visionmate.pro.ui.theme.CautionOrange
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.SafeGreen
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun AssistantStateText(
    text: String,
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val textColor = when (state) {
        AssistantState.SAFE -> SafeGreen
        AssistantState.NEAR -> CautionOrange
        AssistantState.TOO_NEAR, AssistantState.EMERGENCY -> DangerRed
        AssistantState.WARNING -> CautionOrange
        AssistantState.LISTENING, AssistantState.PROCESSING, AssistantState.SPEAKING -> CyanAccent
        AssistantState.IDLE -> TextWhite
    }

    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = textColor,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
