package com.shadow.tracker.plus.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

val MatrixBlack = Color(0xFF000000)
val MatrixDarkGray = Color(0xFF121212)
val MatrixNeonGreen = Color(0xFF00FF41)
val MatrixRed = Color(0xFFFF003C)

private val MatrixColorScheme = darkColorScheme(
    primary = MatrixNeonGreen,
    secondary = MatrixDarkGray,
    tertiary = MatrixRed,
    background = MatrixBlack,
    surface = MatrixDarkGray,
    onPrimary = MatrixBlack,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = MatrixNeonGreen,
    onSurface = MatrixNeonGreen,
)

@Composable
fun ShadowTrackerPlusTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MatrixColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
