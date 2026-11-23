package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BlueLightShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun segmentedGroupShape(
    isFirst: Boolean,
    isLast: Boolean,
    @Suppress("UNUSED_PARAMETER") pressed: Boolean = false,
    outer: Dp = 14.dp,
    inner: Dp = 4.dp
): Shape {
    return remember(isFirst, isLast, outer, inner) {
        val top = if (isFirst) outer else inner
        val bottom = if (isLast) outer else inner
        RoundedCornerShape(
            topStart = top,
            topEnd = top,
            bottomStart = bottom,
            bottomEnd = bottom
        )
    }
}
