package com.zaid.latch.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.zaid.latch.R

val Lime = Color(0xFFC8F56B)
val Ink = Color(0xFF171C17)
private val light = lightColorScheme(
    primary = Ink, onPrimary = Color.White, primaryContainer = Lime, onPrimaryContainer = Ink,
    background = Color(0xFFF6F7F2), onBackground = Ink,
    surface = Color(0xFFFFFFFF), onSurface = Ink,
    surfaceVariant = Color(0xFFEAEEE5), onSurfaceVariant = Color(0xFF687064),
    outline = Color(0xFFB9C1B2), outlineVariant = Color(0xFFE1E6DB),
    secondaryContainer = Color(0xFFEAF5D7), onSecondaryContainer = Ink,
    error = Color(0xFFB23E31), errorContainer = Color(0xFFFFE9E2)
)
private val dark = darkColorScheme(
    primary = Lime, onPrimary = Ink, primaryContainer = Lime, onPrimaryContainer = Ink,
    background = Color(0xFF111510), onBackground = Color(0xFFF4F5EF),
    surface = Color(0xFF1B201A), onSurface = Color(0xFFF4F5EF),
    surfaceVariant = Color(0xFF292F26), onSurfaceVariant = Color(0xFFAEB8A5),
    outline = Color(0xFF65715D), outlineVariant = Color(0xFF323B2C),
    secondaryContainer = Color(0xFF2B3920), onSecondaryContainer = Lime,
    error = Color(0xFFFFB4A7), errorContainer = Color(0xFF4B2822)
)
@Composable
fun LatchTheme(theme: String = "system", content: @Composable () -> Unit) {
    val isDark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(
        colorScheme = if (isDark) dark else light,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.8).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
        ),
        content = content
    )
}
@Composable
fun Brand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (compact) 34.dp else 42.dp).clip(RoundedCornerShape(12.dp)).background(Ink), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_latch), contentDescription = null, tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize().padding(4.dp))
        }
        Text("Latch", fontSize = if (compact) 24.sp else 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    }
}
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
