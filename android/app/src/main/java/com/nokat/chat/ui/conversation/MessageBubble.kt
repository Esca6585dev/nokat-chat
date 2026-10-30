package com.nokat.chat.ui.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nokat.chat.R
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.SecretFile
import com.nokat.chat.ui.common.MessageTicks
import com.nokat.chat.ui.common.avatarAccent
import com.nokat.chat.ui.theme.NokatTheme

private val BubbleRadius = 18.dp
private val TailRadius = 5.dp
private val ImageMaxWidth = 260.dp
private val MediaCorner = 14.dp

/** What the fullscreen viewer shows: a Coil model (URL, content Uri or decrypted bytes). */
class ViewerImage(val model: Any, val name: String, val remoteUrl: String?)

/**
 * One chat bubble: text, photo (+ optional caption) or file card (+ optional caption).
 *
 * [progress] is only invoked for pending attachment messages, so upload progress ticks
 * recompose just the uploading bubble.
 */
@Composable
fun MessageBubble(
    item: ConvItem.Msg,
    progress: () -> Float?,
    onRetry: (Message) -> Unit,
    onOpenImage: (ViewerImage) -> Unit,
    onOpenFile: (AttachmentUi) -> Unit,
    loadSecret: suspend (SecretFile) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val colors = NokatTheme.colors
    val message = item.message
    val outgoing = item.outgoing
    val attachment = item.attachment
    val isImage = attachment?.isImage == true
    val hasCaption = item.text.isNotBlank()
    val uploading = attachment != null && message.isPending && !message.failed
    val uploadFraction = if (uploading) progress() else null

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
    val retry = { onRetry(message) }

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
            .let { if (message.failed && attachment == null) it.clickable(onClick = retry) else it }
            .let {
                when {
                    isImage -> it.padding(3.dp)
                    attachment != null -> it.padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
                    else -> it.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 6.dp)
                }
            }

        Column(bubbleModifier) {
            if (item.senderName != null) {
                Text(
                    text = item.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = avatarAccent(item.senderSeed),
                    maxLines = 1,
                    modifier = if (isImage) Modifier.padding(start = 9.dp, end = 9.dp, top = 4.dp, bottom = 4.dp) else Modifier,
                )
            }

            val meta: @Composable (Color) -> Unit = { tint ->
                MetaRow(
                    time = item.time,
                    message = message,
                    outgoing = outgoing,
                    tint = tint,
                    readTint = colors.readTickOnBubble,
                )
            }

            when {
                attachment != null && isImage -> {
                    val overlay: (@Composable () -> Unit)? = if (hasCaption) {
                        null
                    } else {
                        { meta(Color.White) }
                    }
                    ImageContent(
                        attachment = attachment,
                        width = minOf(ImageMaxWidth, maxWidth - 6.dp),
                        uploading = uploading,
                        progress = uploadFraction,
                        onClick = { model ->
                            if (message.failed) {
                                retry()
                            } else if (model != null) {
                                onOpenImage(ViewerImage(model, attachment.name, attachment.remoteUrl))
                            }
                        },
                        overlay = overlay,
                        loadSecret = loadSecret,
                    )
                    if (hasCaption) {
                        TextWithMeta(
                            text = item.text,
                            time = item.time,
                            outgoing = outgoing,
                            textColor = textColor,
                            modifier = Modifier.padding(start = 9.dp, end = 7.dp, top = 5.dp, bottom = 3.dp),
                            meta = { meta(metaColor) },
                        )
                    }
                }
                attachment != null -> {
                    FileContent(
                        attachment = attachment,
                        outgoing = outgoing,
                        textColor = textColor,
                        metaColor = metaColor,
                        uploading = uploading,
                        progress = uploadFraction,
                        onClick = { if (message.failed) retry() else onOpenFile(attachment) },
                    )
                    if (hasCaption) {
                        TextWithMeta(
                            text = item.text,
                            time = item.time,
                            outgoing = outgoing,
                            textColor = textColor,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                            meta = { meta(metaColor) },
                        )
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                        ) { meta(metaColor) }
                    }
                }
                else -> TextWithMeta(
                    text = when {
                        item.decryptFailed -> stringResource(R.string.secret_decrypt_failed)
                        item.locked -> stringResource(R.string.secret_encrypted_preview)
                        else -> item.text
                    },
                    time = item.time,
                    outgoing = outgoing,
                    textColor = textColor,
                    italic = item.decryptFailed || item.locked,
                    meta = { meta(metaColor) },
                )
            }

            if (message.failed) {
                Text(
                    text = stringResource(R.string.message_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outgoing) Color.White else MaterialTheme.colorScheme.error,
                    modifier = if (attachment != null) Modifier.padding(horizontal = 6.dp, vertical = 3.dp) else Modifier,
                )
            }
        }
    }
}

/** Text with the time/ticks overlaid on its last line (an invisible placeholder reserves the room). */
@Composable
private fun TextWithMeta(
    text: String,
    time: String,
    outgoing: Boolean,
    textColor: Color,
    meta: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
) {
    val placeholder = "  " + time + if (outgoing) "   " else ""
    val body = remember(text, placeholder) {
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = Color.Transparent, fontSize = 12.sp)) { append(placeholder) }
        }
    }
    Box(modifier) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = if (italic) textColor.copy(alpha = 0.75f) else textColor,
            fontStyle = if (italic) FontStyle.Italic else null,
        )
        Box(Modifier.align(Alignment.BottomEnd)) { meta() }
    }
}

@Composable
private fun MetaRow(
    time: String,
    message: Message,
    outgoing: Boolean,
    tint: Color,
    readTint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = time,
            fontSize = 11.sp,
            color = tint,
            maxLines = 1,
        )
        if (outgoing) {
            Spacer(Modifier.width(3.dp))
            MessageTicks(
                status = message.status,
                failed = message.failed,
                tint = tint,
                readTint = readTint,
            )
        }
    }
}

@Composable
private fun ImageContent(
    attachment: AttachmentUi,
    width: Dp,
    uploading: Boolean,
    progress: Float?,
    onClick: (model: Any?) -> Unit,
    overlay: (@Composable () -> Unit)?,
    loadSecret: suspend (SecretFile) -> ByteArray?,
) {
    // Local preview (content Uri) or remote URL; received secret photos are downloaded and
    // decrypted in memory, then shown from bytes.
    val secretFile = attachment.secretFile
    val secretBytes = if (attachment.previewModel == null && secretFile != null) {
        produceState<SecretImage>(initialValue = SecretImage.Loading, secretFile.url) {
            value = loadSecret(secretFile)?.let { SecretImage.Ready(it) } ?: SecretImage.Failed
        }.value
    } else {
        null
    }
    val model: Any? = attachment.previewModel ?: (secretBytes as? SecretImage.Ready)?.bytes
    val ratio = if (attachment.width > 0 && attachment.height > 0) {
        (attachment.width.toFloat() / attachment.height).coerceIn(0.6f, 1.9f)
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(MediaCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick(model) },
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        when (secretBytes) {
            SecretImage.Loading -> UploadIndicator(
                progress = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                size = 40.dp,
            )
            SecretImage.Failed -> Text(
                text = stringResource(R.string.secret_decrypt_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Unit
        }
        if (uploading) {
            UploadIndicator(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                size = 40.dp,
            )
        }
        if (overlay != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) { overlay() }
        }
    }
}

@Composable
private fun FileContent(
    attachment: AttachmentUi,
    outgoing: Boolean,
    textColor: Color,
    metaColor: Color,
    uploading: Boolean,
    progress: Float?,
    onClick: () -> Unit,
) {
    val iconBackground = if (outgoing) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (uploading) {
                UploadIndicator(progress = progress, modifier = Modifier, size = 38.dp)
            } else {
                Icon(
                    painter = painterResource(
                        if (attachment.remoteUrl != null) R.drawable.ic_download else R.drawable.ic_file,
                    ),
                    contentDescription = stringResource(R.string.open_file),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (attachment.sizeText.isNotEmpty() || uploading) {
                Text(
                    text = if (uploading && progress != null && attachment.sizeText.isNotEmpty()) {
                        "${(progress * 100).toInt()}% · ${attachment.sizeText}"
                    } else if (uploading) {
                        stringResource(R.string.uploading)
                    } else {
                        attachment.sizeText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = metaColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** White circular progress: determinate when [progress] is known, else indeterminate. */
@Composable
private fun UploadIndicator(progress: Float?, modifier: Modifier, size: Dp) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (progress == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(size),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else {
            val animated by animateFloatAsState(targetValue = progress, label = "uploadProgress")
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(size),
                color = Color.White,
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
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

/** Loading state of a received secret photo. */
private sealed interface SecretImage {
    data object Loading : SecretImage
    data object Failed : SecretImage
    class Ready(val bytes: ByteArray) : SecretImage
}
