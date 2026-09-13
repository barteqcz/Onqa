package com.barteqcz.onqa.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object AnimationSystem {
    object Duration {
        const val MEDIUM = 300
        const val RELAXED = 600
    }

    // Natural FastOutSlowInEasing for consistent UI transitions
    fun <T> vividTween(
        duration: Int = Duration.MEDIUM,
        delay: Int = 0,
        easing: Easing = FastOutSlowInEasing,
    ): TweenSpec<T> = tween(
        durationMillis = duration,
        delayMillis = delay,
        easing = easing
    )

    fun <T> relaxedTween(
        duration: Int = Duration.RELAXED,
        delay: Int = 0,
        easing: Easing = FastOutSlowInEasing,
    ): TweenSpec<T> = tween(
        durationMillis = duration,
        delayMillis = delay,
        easing = easing
    )

    val VividSpringIntOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val RelaxedSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val RelaxedSpringIntOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val VividSpringIntSize: SpringSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
