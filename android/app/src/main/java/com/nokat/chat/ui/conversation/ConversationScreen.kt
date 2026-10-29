package com.nokat.chat.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nokat.chat.R
import com.nokat.chat.ui.common.Avatar
import com.nokat.chat.ui.common.GradientTopBar
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.ui.theme.NokatTheme
import com.nokat.chat.ui.theme.Sky300
import com.nokat.chat.util.asString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Load older messages when the user scrolls within this many rows of the top. */
private const val PREFETCH_DISTANCE = 8

@Composable
fun ConversationScreen(chatId: String, onBack: () -> Unit) {
    val vm = nokatViewModel {
        ConversationViewModel(chatId, it.chatRepository, it.sessionManager, it.realtime, it.timeFormatter)
    }
    val rows by vm.items.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val paging by vm.paging.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
        topBar = { ConversationTopBar(header = header, onBack = onBack) },
        containerColor = NokatTheme.colors.chatBackground,
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
                                onRetry = vm::retry,
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
                    rows.isEmpty() && paging.loadingInitial ->
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
                                text = error?.asString() ?: stringResource(R.string.empty_conversation),
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

            Composer(
                text = vm.draft,
                onTextChange = vm::onDraftChange,
                onSend = vm::send,
            )
        }
    }
}

@Composable
private fun ConversationTopBar(header: ConversationHeader?, onBack: () -> Unit) {
    GradientTopBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        title = {
            if (header != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(
                        title = header.title,
                        seed = header.avatarSeed,
                        size = 40.dp,
                        avatarUrl = header.avatarUrl,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = header.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HeaderStatusText(header.status)
                    }
                }
            }
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
