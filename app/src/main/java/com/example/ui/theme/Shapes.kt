package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BlueLightShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun segmentedGroupShape(
    isFirst: Boolean,
    isLast: Boolean,
    pressed: Boolean = false,
    outer: Dp = 20.dp,
    inner: Dp = 5.dp
): Shape {
    val spec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val top by animateDpAsState(
        targetValue = if (isFirst || pressed) outer else inner,
        animationSpec = spec,
        label = "segmentTop"
    )
    val bottom by animateDpAsState(
        targetValue = if (isLast || pressed) outer else inner,
        animationSpec = spec,
        label = "segmentBottom"
    )
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom
    )
}
