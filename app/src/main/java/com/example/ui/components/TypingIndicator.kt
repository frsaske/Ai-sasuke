package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = AccentCyan
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 160),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 320),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .scale(dot1Scale)
                .clip(CircleShape)
                .background(dotColor)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .scale(dot2Scale)
                .clip(CircleShape)
                .background(AccentIndigo)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .scale(dot3Scale)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}
