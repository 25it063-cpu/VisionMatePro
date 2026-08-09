package com.visionmate.pro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.visionmate.pro.model.AssistantState
import com.visionmate.pro.ui.theme.CautionOrange
import com.visionmate.pro.ui.theme.CyanAccent
import com.visionmate.pro.ui.theme.DangerRed
import com.visionmate.pro.ui.theme.SafeGreen
import com.visionmate.pro.ui.theme.SoftBlue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AssistantOrb(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Pulse animation
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 800
                    AssistantState.PROCESSING -> 500
                    AssistantState.TOO_NEAR, AssistantState.EMERGENCY -> 350
                    AssistantState.NEAR -> 700
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Rotation angle for energy rings
    val rotationAngle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    // Color semantics
    val (primaryColor, secondaryColor) = when (state) {
        AssistantState.SAFE -> Pair(SafeGreen, Color(0xFF10B981))
        AssistantState.NEAR -> Pair(CautionOrange, Color(0xFFF59E0B))
        AssistantState.TOO_NEAR -> Pair(DangerRed, Color(0xFFDC2626))
        AssistantState.EMERGENCY -> Pair(DangerRed, Color(0xFF991B1B))
        AssistantState.WARNING -> Pair(CautionOrange, DangerRed)
        AssistantState.LISTENING -> Pair(CyanAccent, SoftBlue)
        AssistantState.PROCESSING -> Pair(CyanAccent, Color(0xFF3B82F6))
        AssistantState.SPEAKING -> Pair(CyanAccent, SafeGreen)
        AssistantState.IDLE -> Pair(CyanAccent, SoftBlue)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = (size.minDimension / 2) * 0.7f
            val scaledRadius = baseRadius * pulseScale.value

            // 1. Outer Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.45f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = scaledRadius * 1.45f
                ),
                radius = scaledRadius * 1.45f,
                center = center
            )

            // 2. Rotating Energy Ring
            val rad = Math.toRadians(rotationAngle.value.toDouble())
            val rx = center.x + (scaledRadius * 1.15f) * cos(rad).toFloat()
            val ry = center.y + (scaledRadius * 1.15f) * sin(rad).toFloat()
            
            drawCircle(
                color = primaryColor.copy(alpha = 0.6f),
                radius = scaledRadius * 1.1f,
                center = center,
                style = Stroke(width = 3f)
            )

            drawCircle(
                color = primaryColor,
                radius = 7f,
                center = Offset(rx, ry)
            )

            // 3. Liquid Chrome Inner Core Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryColor,
                        secondaryColor,
                        Color(0xFF030712)
                    ),
                    center = Offset(center.x - scaledRadius * 0.25f, center.y - scaledRadius * 0.25f),
                    radius = scaledRadius * 1.2f
                ),
                radius = scaledRadius,
                center = center
            )

            // 4. Iridescent Highlight Rim
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.8f),
                        Color.White.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.8f),
                        primaryColor.copy(alpha = 0.8f)
                    ),
                    center = center
                ),
                radius = scaledRadius,
                center = center,
                style = Stroke(width = 4f)
            )
        }
    }
}
