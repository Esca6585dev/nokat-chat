package com.nokat.chat.ui.newchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nokat.chat.R
import com.nokat.chat.ui.common.GradientTopBar
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.util.asString

@Composable
fun UserSearchScreen(
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onChatOpened: (String) -> Unit,
) {
    val vm = nokatViewModel { UserSearchViewModel(it.userRepository, it.chatRepository, it.sessionManager) }
    val currentOnChatOpened by rememberUpdatedState(onChatOpened)
    LaunchedEffect(vm) { vm.openChat.collect { currentOnChatOpened(it) } }
    val recent = remember(vm) { vm.recentPeople }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GradientTopBar(
                title = {
                    SearchTitleField(
                        value = vm.query,
                        onValueChange = vm::onQueryChange,
                        placeholder = stringResource(R.string.search_users_hint),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            val showingQuery = vm.query.isNotBlank()
            val people = if (showingQuery) vm.results else recent
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "new-group", contentType = "action") {
                    NewGroupRow(onClick = onNewGroup)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
                val error = vm.error
                if (error != null) {
                    item(key = "error", contentType = "info") {
                        Text(
                            text = error.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (!showingQuery && people.isNotEmpty()) {
                    item(key = "recent-header", contentType = "header") {
                        SectionHeader(stringResource(R.string.recent_chats))
                    }
                }
                items(items = people, key = { it.id }, contentType = { "user" }) { user ->
                    UserRow(
                        user = user,
                        onClick = { vm.openDirectChat(user) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (people.isEmpty() && !vm.searching) {
                    item(key = "hint", contentType = "info") {
                        Text(
                            text = if (showingQuery) {
                                stringResource(R.string.no_results)
                            } else {
                                stringResource(R.string.search_users_empty)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 40.dp),
                        )
                    }
                }
            }
            if (vm.searching || vm.working) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun NewGroupRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.new_group),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

