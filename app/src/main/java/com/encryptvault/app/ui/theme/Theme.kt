package com.encryptvault.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6584),
    background = Color(0xFF0A0E27),
    surface = Color(0x33111111),
    onSurface = Color.White
)

private val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold, fontSize = 22.sp)
)

@Composable
fun EncryptVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
