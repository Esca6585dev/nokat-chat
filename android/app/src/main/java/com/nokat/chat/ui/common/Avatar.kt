package com.nokat.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nokat.chat.ui.theme.NokatTheme

private val AvatarPalettes: List<Pair<Color, Color>> = listOf(
    Color(0xFF60A5FA) to Color(0xFF2563EB), // blue
    Color(0xFF38BDF8) to Color(0xFF0284C7), // sky
    Color(0xFF34D399) to Color(0xFF059669), // green
    Color(0xFFF472B6) to Color(0xFFDB2777), // pink
    Color(0xFFFBBF24) to Color(0xFFD97706), // amber
    Color(0xFFA78BFA) to Color(0xFF7C3AED), // violet
    Color(0xFFF87171) to Color(0xFFDC2626), // red
    Color(0xFF22D3EE) to Color(0xFF0891B2), // cyan
)

private fun paletteFor(seed: String): Pair<Color, Color> =
    AvatarPalettes[(seed.hashCode() and 0x7fffffff) % AvatarPalettes.size]

/** Deterministic accent colour for a user (e.g. sender names in groups). */
fun avatarAccent(seed: String): Color = paletteFor(seed).second

fun initialsOf(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    if (trimmed.startsWith("+")) return trimmed.takeLast(2)
    val words = trimmed.removePrefix("@").split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words.take(2)
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }
}

/**
 * Circular avatar: deterministic gradient with initials, optional remote image on top,
 * and an online dot.
 */
@Composable
fun Avatar(
    title: String,
    seed: String,
    size: Dp,
    modifier: Modifier = Modifier,
    avatarUrl: String = "",
    online: Boolean = false,
) {
    val brush = remember(seed) {
        val (start, end) = paletteFor(seed)
        Brush.linearGradient(listOf(start, end))
    }
    val initials = remember(title) { initialsOf(title) }
    Box(modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(brush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp,
                maxLines = 1,
            )
            if (avatarUrl.startsWith("http")) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(size * 0.045f)
                    .background(NokatTheme.colors.online, CircleShape),
            )
        }
    }
}
