package com.metrolive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Apple 시스템 팔레트
val IosBg = Color(0xFFF2F2F7)
val IosCard = Color(0xFFFFFFFF)
val IosLabel = Color(0xFF1C1C1E)
val IosSecondary = Color(0xFF8E8E93)
val IosSeparator = Color(0x1F3C3C43)
val IosBlue = Color(0xFF0A84FF)
val IosRed = Color(0xFFFF3B30)
val IosOrange = Color(0xFFFF9500)
val IosYellow = Color(0xFFFFCC00)
val IosGreen = Color(0xFF34C759)
val Line2Green = Color(0xFF00A84D)
val GlassWhite = Color(0xB8FFFFFF)

private val scheme = lightColorScheme(
    primary = IosBlue,
    background = IosBg,
    surface = IosCard,
    onBackground = IosLabel,
    onSurface = IosLabel,
    error = IosRed,
)

// 시스템 폰트(원UI에선 SamsungOne/OneUI Sans) + SF 느낌의 타이트한 자간
private val type = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IosSecondary),
)

@Composable
fun MetroLiveTheme(content: @Composable () -> Unit) {
    // 1인용 M1: 라이트 고정 (다크는 백로그)
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
