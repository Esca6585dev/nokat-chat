package com.nokat.chat.ui.conversation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nokat.chat.R
import com.nokat.chat.data.repo.SecretAccess
import com.nokat.chat.ui.common.Avatar
import com.nokat.chat.ui.common.GradientTopBar
import com.nokat.chat.ui.common.SecureWindow
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.ui.common.openUrl
import com.nokat.chat.ui.theme.NokatTheme
import com.nokat.chat.ui.theme.Sky300
import com.nokat.chat.util.asString
import com.nokat.chat.util.resolve
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Load older messages when the user scrolls within this many rows of the top. */
private const val PREFETCH_DISTANCE = 8

/** Max photos per Photo Picker selection. */
private const val MAX_PICK = 10

@Composable
fun ConversationScreen(
    chatId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val vm = nokatViewModel {
        ConversationViewModel(
            chatId = chatId,
            chats = it.chatRepository,
            session = it.sessionManager,
            realtime = it.realtime,
            timeFormatter = it.timeFormatter,
            fileUrls = it.fileUrls,
            attachmentReader = it.attachmentReader,
            secret = it.secretChats,
        )
    }
    val rows by vm.items.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val paging by vm.paging.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var viewerImage by remember { mutableStateOf<ViewerImage?>(null) }
    var showFingerprint by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val keyLoading by vm.keyLoading.collectAsStateWithLifecycle()
    val noAppText = stringResource(R.string.error_no_app_to_open)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)

    val isSecret = header?.isSecret == true
    val access = header?.secretAccess
    // Like Telegram: no screenshots / screen recording / recents thumbnail of secret chats.
    SecureWindow(enabled = isSecret)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                ConversationEvent.Close -> currentOnBack()
                is ConversationEvent.OpenChat -> currentOnOpenChat(event.chatId)
                is ConversationEvent.OpenFile -> {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(event.uri, event.mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, noAppText, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // System Photo Picker (backported to API 26+ by androidx.activity) and the document picker.
    // Neither needs storage permissions.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK),
    ) { uris -> vm.sendAttachments(uris) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.sendAttachments(uris) }

    LaunchedEffect(vm) {
        vm.notices.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    // Mark read / set active only while actually visible (not when backgrounded).
    LifecycleResumeEffect(chatId) {
        vm.onResume()
        onPauseOrDispose { vm.onPause() }
    }

    // Paging: reverseLayout → the oldest rows are at the end of the list.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            (total > 0 && lastVisible >= total - PREFETCH_DISTANCE) to total
        }
            .distinctUntilChanged()
            .filter { it.first }
            .collect { vm.loadOlder() }
    }

    // Keep following new messages when already at the bottom.
    val newestKey = rows.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(vm) {
        vm.scrollToBottom.collect { listState.animateScrollToItem(0) }
    }

    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    Scaffold(
        topBar = {
            ConversationTopBar(
                header = header,
                onBack = onBack,
                onShowKey = { showFingerprint = true },
                onStartSecret = vm::startSecretChat,
                onDeleteSecret = { confirmDelete = true },
            )
        },
        containerColor = NokatTheme.colors.chatBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    items(
                        items = rows,
                        key = { it.key },
                        contentType = { item ->
                            when (item) {
                                is ConvItem.DateHeader -> 0
                                is ConvItem.Msg -> if (item.outgoing) 1 else 2
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ConvItem.DateHeader -> DateChip(item.label, Modifier.animateItem())
                            is ConvItem.Msg -> MessageBubble(
                                item = item,
                                progress = { vm.progressOf(item.message.clientIdOrNull) },
                                onRetry = vm::retry,
                                onOpenImage = { viewerImage = it },
                                onOpenFile = { attachment ->
                                    val url = attachment.remoteUrl
                                    if (attachment.secretFile != null) {
                                        vm.openSecretFile(attachment)
                                    } else if (url != null && !context.openUrl(url)) {
                                        Toast.makeText(context, noAppText, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                loadSecret = { file -> vm.loadSecretFile(file) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (paging.loadingOlder) {
                        item(key = "loading-older", contentType = 3) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }

                when {
                    rows.isEmpty() && (paging.loadingInitial || keyLoading) ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    rows.isEmpty() -> {
                        val error = paging.error
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp)
                                .background(NokatTheme.colors.dateChip, RoundedCornerShape(20.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = error?.asString() ?: stringResource(
                                    if (isSecret) R.string.secret_empty else R.string.empty_conversation,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NokatTheme.colors.onDateChip,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showScrollDown,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.scroll_to_bottom))
                    }
                }
            }

            when (access) {
                SecretAccess.PENDING_OUTGOING -> SecretStatusBar(
                    text = stringResource(R.string.secret_waiting_body, header?.title.orEmpty()),
                    hint = stringResource(R.string.secret_waiting_hint, header?.title.orEmpty()),
                )
                SecretAccess.PENDING_INCOMING -> SecretInvitation(
                    name = header?.title.orEmpty(),
                    busy = vm.secretBusy,
                    onAccept = vm::acceptSecretChat,
                    onDecline = vm::deleteSecretChat,
                )
                SecretAccess.NO_KEY -> SecretStatusBar(text = stringResource(R.string.secret_other_device), hint = null)
                else -> Composer(
                    text = vm.draft,
                    onTextChange = vm::onDraftChange,
                    onSend = vm::send,
                    onPickPhoto = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                )
            }
        }
    }

    viewerImage?.let { image ->
        ImageViewerDialog(image = image, onDismiss = { viewerImage = null })
    }

    val fingerprint = header?.fingerprint
    if (showFingerprint && fingerprint != null) {
        FingerprintDialog(
            fingerprint = fingerprint,
            peerName = header?.title.orEmpty(),
            onDismiss = { showFingerprint = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.secret_delete)) },
            text = { Text(stringResource(R.string.secret_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteSecretChat()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ConversationTopBar(
    header: ConversationHeader?,
    onBack: () -> Unit,
    onShowKey: () -> Unit,
    onStartSecret: () -> Unit,
    onDeleteSecret: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val secretAccent = NokatTheme.colors.secretOnTopBar
    GradientTopBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        title = {
            if (header != null) {
                val canShowKey = header.isSecret && header.fingerprint != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (canShowKey) Modifier.clickable(onClick = onShowKey) else Modifier,
                ) {
                    Avatar(
                        title = header.title,
                        seed = header.avatarSeed,
                        size = 40.dp,
                        avatarUrl = header.avatarUrl,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (header.isSecret) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = stringResource(R.string.secret_chat),
                                    tint = secretAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = header.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (header.isSecret) secretAccent else Color.Unspecified,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HeaderStatusText(header.status)
                    }
                }
            }
        },
        actions = {
            if (header != null && header.isSecret && header.fingerprint != null) {
                IconButton(onClick = onShowKey) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.secret_key_show),
                        tint = secretAccent,
                    )
                }
            }
            if (header != null && (header.isSecret || header.canStartSecret)) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (header.canStartSecret) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.secret_chat_start)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = NokatTheme.colors.secret)
                                },
                                onClick = {
                                    menuOpen = false
                                    onStartSecret()
                                },
                            )
                        }
                        if (header.isSecret) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.secret_delete), color = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuOpen = false
                                    onDeleteSecret()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Bottom bar replacing the composer when this device cannot write (pending / other device). */
@Composable
private fun SecretStatusBar(text: String, hint: String?) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = NokatTheme.colors.secret,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Peer side of a pending secret chat: accept (generates this device's key) or decline (deletes it). */
@Composable
private fun SecretInvitation(name: String, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = NokatTheme.colors.secret,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.secret_invite_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = NokatTheme.colors.secret,
                )
            }
            Text(
                text = stringResource(R.string.secret_invite_body, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.secret_decline))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NokatTheme.colors.secret),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(stringResource(R.string.secret_accept), color = Color.White)
                    }
                }
            }
        }
    }
}

/** "Şifrleme açary": fingerprint in 2 lines of 4 monospace groups + explanation. */
@Composable
private fun FingerprintDialog(fingerprint: String, peerName: String, onDismiss: () -> Unit) {
    val groups = fingerprint.split(' ')
    val formatted = groups.take(4).joinToString(" ") + "\n" + groups.drop(4).joinToString(" ")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = NokatTheme.colors.secret) },
        title = { Text(stringResource(R.string.secret_key_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                    color = NokatTheme.colors.secret,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = stringResource(R.string.secret_key_body, peerName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun HeaderStatusText(status: HeaderStatus) {
    Crossfade(targetState = status, label = "headerStatus") { s ->
        val highlight = s is HeaderStatus.Online || s is HeaderStatus.Typing
        val text = when (s) {
            HeaderStatus.None -> ""
            HeaderStatus.Online -> stringResource(R.string.online)
            is HeaderStatus.LastSeen -> s.text
            is HeaderStatus.Typing ->
                if (s.name.isNullOrBlank()) {
                    stringResource(R.string.typing)
                } else {
                    stringResource(R.string.typing_named, s.name)
                }
            is HeaderStatus.Members ->
                if (s.online > 0) {
                    stringResource(R.string.members_online, s.count, s.online)
                } else {
                    stringResource(R.string.members_count, s.count)
                }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) Sky300 else NokatTheme.colors.onTopBarSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
