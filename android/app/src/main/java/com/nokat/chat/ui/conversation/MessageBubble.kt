package com.nokat.chat.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nokat.chat.R
import com.nokat.chat.data.model.Message
import com.nokat.chat.ui.common.MessageTicks
import com.nokat.chat.ui.common.avatarAccent
import com.nokat.chat.ui.theme.NokatTheme

private val BubbleRadius = 18.dp
private val TailRadius = 5.dp

@Composable
fun MessageBubble(
    item: ConvItem.Msg,
    onRetry: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NokatTheme.colors
    val message = item.message
    val outgoing = item.outgoing
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    val shape = remember(outgoing, item.lastInRun) {
        RoundedCornerShape(
            topStart = BubbleRadius,
            topEnd = BubbleRadius,
            bottomEnd = if (outgoing && item.lastInRun) TailRadius else BubbleRadius,
            bottomStart = if (!outgoing && item.lastInRun) TailRadius else BubbleRadius,
        )
    }
    val textColor = if (outgoing) colors.onOutgoingBubble else colors.onIncomingBubble
    val metaColor = if (outgoing) colors.outgoingMeta else colors.incomingMeta

    // Invisible trailing placeholder reserves room for the time/ticks overlay on the last line.
    val placeholder = "  " + item.time + if (outgoing) "   " else ""
    val body = remember(message.text, placeholder) {
        buildAnnotatedString {
            append(message.text)
            withStyle(SpanStyle(color = Color.Transparent, fontSize = 12.sp)) { append(placeholder) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (outgoing) 48.dp else 0.dp,
                end = if (outgoing) 0.dp else 48.dp,
                top = if (item.firstInRun) 6.dp else 1.5.dp,
                bottom = 1.5.dp,
            ),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleModifier = Modifier
            .widthIn(max = maxWidth)
            .shadow(elevation = if (outgoing) 0.dp else 0.5.dp, shape = shape, clip = false)
            .clip(shape)
            .let { if (outgoing) it.background(colors.outgoingBubble) else it.background(colors.incomingBubble) }
            .let { if (message.failed) it.clickable { onRetry(message) } else it }
            .padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 6.dp)

        Column(bubbleModifier) {
            if (item.senderName != null) {
                Text(
                    text = item.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = avatarAccent(item.senderSeed),
                    maxLines = 1,
                )
            }
            Box {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.time,
                        fontSize = 11.sp,
                        color = metaColor,
                        maxLines = 1,
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(3.dp))
                        MessageTicks(
                            status = message.status,
                            failed = message.failed,
                            tint = metaColor,
                            readTint = colors.readTickOnBubble,
                        )
                    }
                }
            }
            if (message.failed) {
                Text(
                    text = stringResource(R.string.message_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outgoing) Color.White else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun DateChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = NokatTheme.colors.onDateChip,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NokatTheme.colors.dateChip)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
