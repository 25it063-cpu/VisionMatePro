package com.visionmate.pro.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.TextWhite

@Composable
fun VisionMateLogo(modifier: Modifier = Modifier) {
    Text(
        text = "VISIONMATE",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 4.sp,
        color = TextWhite,
        modifier = modifier
    )
}
