package com.nokat.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme: ColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    inversePrimary = Blue300,
    secondary = BrandSky,
    onSecondary = Slate900,
    secondaryContainer = Sky100,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = BrandPrimaryDark,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE8EEF9),
    onSurfaceVariant = Slate500,
    surfaceTint = BrandPrimary,
    inverseSurface = Slate800,
    inverseOnSurface = Slate100,
    error = ErrorRed,
    onError = Color.White,
    outline = Slate300,
    outlineVariant = Slate200,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3EAF6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = Color(0xFFF1F5FD),
    surfaceContainerHigh = Color(0xFFEAF0FB),
    surfaceContainerHighest = Color(0xFFE3EAF7),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = BrandBlue500,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = Blue100,
    inversePrimary = BrandPrimary,
    secondary = BrandSky,
    onSecondary = Slate900,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Sky100,
    tertiary = Blue400,
    onTertiary = Slate900,
    background = DarkBackground,
    onBackground = Slate100,
    surface = Color(0xFF0F172A),
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    surfaceTint = BrandBlue500,
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    error = ErrorRedDark,
    onError = Slate900,
    outline = Slate600,
    outlineVariant = Slate700,
    surfaceBright = Slate800,
    surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = Color(0xFF111A2C),
    surfaceContainer = Color(0xFF131C2F),
    surfaceContainerHigh = Color(0xFF1A2438),
    surfaceContainerHighest = Color(0xFF222D43),
)

/** Messenger-specific colours not covered by the Material scheme. */
@Immutable
data class NokatColors(
    val topBar: Brush,
    val accentGradient: Brush,
    val outgoingBubble: Brush,
    val onOutgoingBubble: Color,
    val outgoingMeta: Color,
    val incomingBubble: Color,
    val onIncomingBubble: Color,
    val incomingMeta: Color,
    /** ✓✓ on an outgoing (blue) bubble once read. */
    val readTickOnBubble: Color,
    /** ✓✓ in the chat list once read. */
    val readTick: Color,
    val chatBackground: Color,
    val composerField: Color,
    val dateChip: Color,
    val onDateChip: Color,
    val online: Color,
    val onTopBarSecondary: Color,
    /** Secret-chat accent (lock icon, titles). */
    val secret: Color,
    /** Secret-chat accent on top of the blue top bar. */
    val secretOnTopBar: Color,
)

private val OutgoingGradient = Brush.linearGradient(listOf(BrandPrimary, BrandBlue500))

private val LightNokatColors = NokatColors(
    topBar = Brush.horizontalGradient(listOf(BrandPrimary, BrandPrimaryDark)),
    accentGradient = Brush.linearGradient(listOf(BrandSky, BrandPrimary, BrandPrimaryDark)),
    outgoingBubble = OutgoingGradient,
    onOutgoingBubble = Color.White,
    outgoingMeta = Color.White.copy(alpha = 0.78f),
    incomingBubble = IncomingLight,
    onIncomingBubble = Slate900,
    incomingMeta = Slate400,
    readTickOnBubble = Sky300,
    readTick = BrandSky,
    chatBackground = Color(0xFFE9F1FF),
    composerField = Slate100,
    dateChip = Color(0x332563EB),
    onDateChip = BrandPrimaryDark,
    online = OnlineGreen,
    onTopBarSecondary = Color.White.copy(alpha = 0.78f),
    secret = Color(0xFF0D9488),
    secretOnTopBar = Color(0xFF5EEAD4),
)

private val DarkNokatColors = NokatColors(
    topBar = Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF172554))),
    accentGradient = Brush.linearGradient(listOf(BrandSky, BrandPrimary, BrandPrimaryDark)),
    outgoingBubble = OutgoingGradient,
    onOutgoingBubble = Color.White,
    outgoingMeta = Color.White.copy(alpha = 0.72f),
    incomingBubble = IncomingDark,
    onIncomingBubble = Slate100,
    incomingMeta = Slate400,
    readTickOnBubble = Sky300,
    readTick = BrandSky,
    chatBackground = DarkBackground,
    composerField = Slate800,
    dateChip = Color(0x991E293B),
    onDateChip = Slate300,
    online = OnlineGreen,
    onTopBarSecondary = Color.White.copy(alpha = 0.72f),
    secret = Color(0xFF2DD4BF),
    secretOnTopBar = Color(0xFF5EEAD4),
)

val LocalNokatColors = staticCompositionLocalOf { LightNokatColors }

private val BaseTypography = Typography()

private val NokatTypography = BaseTypography.copy(
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
)

private val NokatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NokatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extra = if (darkTheme) DarkNokatColors else LightNokatColors
    CompositionLocalProvider(LocalNokatColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NokatTypography,
            shapes = NokatShapes,
            content = content,
        )
    }
}

object NokatTheme {
    val colors: NokatColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNokatColors.current
}
