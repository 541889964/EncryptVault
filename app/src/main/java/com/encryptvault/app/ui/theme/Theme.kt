
package com.encryptvault.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AnimeColorScheme = darkColorScheme(
    primary = Color(0xFFFF6EC7),
    onPrimary = Color.White,
    secondary = Color(0xFFA78BFA),
    onSecondary = Color.White,
    tertiary = Color(0xFF7DD3FC),
    background = Color(0xFF1A0B2E),
    surface = Color(0x22FFB6D9),
    onSurface = Color(0xFFFFF5FA),
    onBackground = Color(0xFFFFF5FA))

private val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black, fontSize = 22.sp))

@Composable
fun EncryptVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AnimeColorScheme, typography = Typography, content = content)
}
