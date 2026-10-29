package com.nokat.chat.ui.newchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nokat.chat.R
import com.nokat.chat.data.model.User
import com.nokat.chat.data.model.displayName
import com.nokat.chat.ui.common.Avatar
import com.nokat.chat.ui.common.GradientTopBar
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.util.asString

@Composable
fun NewGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val vm = nokatViewModel { NewGroupViewModel(it.userRepository, it.chatRepository, it.sessionManager) }
    val currentOnCreated by rememberUpdatedState(onCreated)
    LaunchedEffect(vm) { vm.openChat.collect { currentOnCreated(it) } }
    val recent = remember(vm) { vm.recentPeople }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GradientTopBar(
                title = {
                    Column {
                        Text(stringResource(R.string.new_group))
                        if (vm.selected.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.members_selected, vm.selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = vm.canCreate || vm.working, enter = scaleIn(), exit = scaleOut()) {
                FloatingActionButton(
                    onClick = vm::create,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.imePadding(),
                ) {
                    if (vm.working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.create))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = vm::onTitleChange,
                label = { Text(stringResource(R.string.group_title_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (vm.selected.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(items = vm.selected, key = { it.id }) { user ->
                        SelectedChip(user = user, onRemove = { vm.toggle(user) }, modifier = Modifier.animateItem())
                    }
                }
            }

            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_members)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            val error = vm.error
            if (error != null) {
                Text(
                    text = error.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                val showingQuery = vm.query.isNotBlank()
                val people = if (showingQuery) vm.results else recent
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                ) {
                    if (!showingQuery && people.isNotEmpty()) {
                        item(key = "recent-header", contentType = "header") {
                            SectionHeader(stringResource(R.string.recent_chats))
                        }
                    }
                    items(items = people, key = { it.id }, contentType = { "user" }) { user ->
                        val checked = vm.isSelected(user)
                        UserRow(
                            user = user,
                            onClick = { vm.toggle(user) },
                            trailing = {
                                Checkbox(checked = checked, onCheckedChange = { vm.toggle(user) })
                            },
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
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 32.dp),
                            )
                        }
                    }
                }
                if (vm.searching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun SelectedChip(user: User, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onRemove)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title = user.displayName, seed = user.id, size = 26.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = user.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.remove),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}
