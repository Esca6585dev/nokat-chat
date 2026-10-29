package com.nokat.chat.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nokat.chat.data.model.MessageStatus

/**
 * Delivery indicator: clock while sending, ✓ sent, ✓✓ delivered, ✓✓ in [readTint] when read,
 * and an error icon when [failed].
 */
@Composable
fun MessageTicks(
    status: String,
    tint: Color,
    readTint: Color,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
) {
    when {
        failed -> Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier.size(14.dp),
        )
        status == MessageStatus.SENDING -> Canvas(modifier.size(12.dp)) {
            val stroke = 1.3.dp.toPx()
            val r = size.minDimension / 2f - stroke
            drawCircle(color = tint, radius = r, style = Stroke(width = stroke))
            drawLine(tint, center, Offset(center.x, center.y - r * 0.6f), stroke, StrokeCap.Round)
            drawLine(tint, center, Offset(center.x + r * 0.5f, center.y), stroke, StrokeCap.Round)
        }
        else -> {
            val double = status == MessageStatus.DELIVERED || status == MessageStatus.READ
            val color = if (status == MessageStatus.READ) readTint else tint
            Canvas(modifier.size(width = if (double) 17.dp else 12.dp, height = 10.dp)) {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val w = 11.dp.toPx()
                val h = size.height
                fun check(dx: Float, short: Boolean) {
                    val path = Path().apply {
                        if (short) {
                            // Second tick of a pair: only the long stroke, like Telegram.
                            moveTo(dx + w * 0.42f, h * 0.88f)
                        } else {
                            moveTo(dx + w * 0.06f, h * 0.55f)
                            lineTo(dx + w * 0.38f, h * 0.9f)
                        }
                        lineTo(dx + w * 0.95f, h * 0.1f)
                    }
                    drawPath(path, color, style = stroke)
                }
                check(0f, short = false)
                if (double) check(5.dp.toPx(), short = true)
            }
        }
    }
}
