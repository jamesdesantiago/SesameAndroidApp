package com.gazzel.sesameapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS-inspired Light Theme
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),     // Apple System Blue
    onPrimary = Color.White,
    secondary = Color(0xFF8E8E93),   // Apple Secondary Label
    onSecondary = Color.White,
    background = Color(0xFFF2F2F7),  // Apple System Background
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,           // Apple Secondary System Background
    onSurface = Color(0xFF1C1C1E),
    error = Color(0xFFFF3B30),       // Apple System Red
    surfaceVariant = Color(0xFFF2F2F7),
    outline = Color(0xFFC6C6C8)
)

// iOS-inspired Dark Theme
private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),     // Apple System Blue (Dark)
    onPrimary = Color.White,
    secondary = Color(0xFF636366),   // Apple Secondary Label (Dark)
    onSecondary = Color.White,
    background = Color(0xFF1C1C1E),  // Apple System Background (Dark)
    onBackground = Color.White,
    surface = Color(0xFF2C2C2E),     // Apple Secondary System Background (Dark)
    onSurface = Color.White,
    error = Color(0xFFFF6969),       // Apple System Red (Dark)
    surfaceVariant = Color(0xFF2C2C2E),
    outline = Color(0xFF38383A)
)

// Apple-style typography (SF Pro-like proportions)
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp
    )
)

// iOS-style rounded corners
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SesameAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}