package com.barteqcz.loqa.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LoqaGreen,
    secondary = TextGrey,
    tertiary = CardBackground,
    background = DarkBackground,
    surface = DarkBackground,
    onPrimary = Color.Black,
    onBackground = TextWhite,
    onSurface = TextWhite,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextGrey
)

@Composable
fun LoqaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    accentColor: Color = LoqaGreen,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme.copy(primary = accentColor)
    }
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
