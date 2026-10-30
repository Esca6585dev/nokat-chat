package com.nokat.chat.ui.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nokat.chat.R
import com.nokat.chat.data.model.MessageKind
import com.nokat.chat.data.repo.SecretAccess
import com.nokat.chat.ui.common.Avatar
import com.nokat.chat.ui.common.EmptyState
import com.nokat.chat.ui.common.GradientTopBar
import com.nokat.chat.ui.common.MessageTicks
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.ui.newchat.SearchTitleField
import com.nokat.chat.ui.theme.NokatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onNewSecretChat: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = nokatViewModel {
        ChatListViewModel(it.chatRepository, it.sessionManager, it.realtime, it.timeFormatter, it.secretChats)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    fun closeSearch() {
        searching = false
        vm.onQueryChange("")
    }

    BackHandler(enabled = searching) { closeSearch() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GradientTopBar(
                title = {
                    if (searching) {
                        SearchTitleField(
                            value = vm.query,
                            onValueChange = vm::onQueryChange,
                            placeholder = stringResource(R.string.search),
                        )
                    } else {
                        TitleWithStatus(connected = state.connected)
                    }
                },
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (searching) {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.new_group)) },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onNewGroup()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.secret_chat_start)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = NokatTheme.colors.secret)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onNewSecretChat()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings)) },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onSettings()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.new_chat)) },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = onNewChat,
                    expanded = fabExpanded,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.items.isEmpty() && !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.query.isNotBlank()) {
                        EmptyState(title = stringResource(R.string.no_results), modifier = Modifier.padding(top = 96.dp))
                    } else {
                        EmptyState(
                            title = stringResource(R.string.empty_chats_title),
                            body = stringResource(R.string.empty_chats_body),
                            modifier = Modifier.padding(top = 96.dp),
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(items = state.items, key = { it.id }, contentType = { "chat" }) { item ->
                        ChatRow(
                            item = item,
                            onClick = { onOpenChat(item.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleWithStatus(connected: Boolean) {
    AnimatedContent(
        targetState = connected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "connectionTitle",
    ) { isConnected ->
        Text(
            text = if (isConnected) stringResource(R.string.chats_title) else stringResource(R.string.connecting),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatRow(item: ChatItemUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                title = item.title,
                seed = item.avatarSeed,
                size = 54.dp,
                avatarUrl = item.avatarUrl,
                online = item.online,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isSecret) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.secret_chat),
                            tint = NokatTheme.colors.secret,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.isSecret) NokatTheme.colors.secret else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.outgoingStatus != null) {
                        MessageTicks(
                            status = item.outgoingStatus,
                            tint = colors.primary,
                            readTint = NokatTheme.colors.readTick,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.unread > 0) colors.primary else colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewText(item = item, modifier = Modifier.weight(1f))
                    if (item.unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        UnreadBadge(count = item.unread)
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            thickness = 0.5.dp,
            color = colors.outlineVariant,
        )
    }
}

@Composable
private fun PreviewText(item: ChatItemUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val typingName = item.typingName
    if (typingName != null) {
        Text(
            text = if (typingName.isEmpty()) {
                stringResource(R.string.typing)
            } else {
                stringResource(R.string.typing_named, typingName)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val secretLine: String? = when {
        item.secretAccess == SecretAccess.PENDING_OUTGOING -> stringResource(R.string.secret_waiting)
        item.secretAccess == SecretAccess.PENDING_INCOMING -> stringResource(R.string.secret_invite_preview)
        item.previewEncrypted -> stringResource(R.string.secret_encrypted_preview)
        else -> null
    }
    if (secretLine != null) {
        Text(
            text = secretLine,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.secretAccess == SecretAccess.PENDING_INCOMING) {
                NokatTheme.colors.secret
            } else {
                colors.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val you = stringResource(R.string.you)
    val noMessages = stringResource(R.string.no_messages)
    val body: String = when (item.previewKind) {
        MessageKind.IMAGE -> {
            val caption = item.preview?.takeIf { it.isNotBlank() }
            if (caption != null) {
                stringResource(R.string.preview_photo_caption, caption)
            } else {
                stringResource(R.string.preview_photo)
            }
        }
        MessageKind.FILE -> stringResource(R.string.preview_file, item.attachmentName ?: item.preview.orEmpty())
        else -> item.preview ?: noMessages
    }
    val prefix: String? = when {
        item.preview == null && item.previewKind == MessageKind.TEXT -> null
        item.lastFromMe -> you
        item.lastSenderName != null -> item.lastSenderName
        else -> null
    }
    val text = remember(body, prefix, colors.onSurface) {
        buildAnnotatedString {
            if (prefix != null) {
                withStyle(SpanStyle(color = colors.onSurface)) { append("$prefix: ") }
            }
            append(body)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 999) "999+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
