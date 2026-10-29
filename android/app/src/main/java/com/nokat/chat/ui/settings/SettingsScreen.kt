package com.nokat.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nokat.chat.BuildConfig
import com.nokat.chat.R
import com.nokat.chat.data.model.displayName
import com.nokat.chat.ui.common.Avatar
import com.nokat.chat.ui.common.GradientButton
import com.nokat.chat.ui.common.GradientTopBar
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.ui.profile.ProfileFields
import com.nokat.chat.ui.profile.ProfileViewModel
import com.nokat.chat.ui.theme.NokatTheme

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = nokatViewModel { ProfileViewModel(it.userRepository, it.authRepository, it.sessionManager) }
    val user by vm.user.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.saved.collect { snackbarHostState.showSnackbar(context.getString(R.string.saved)) }
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header continuing the top bar gradient.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NokatTheme.colors.topBar)
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val current = user
                    Avatar(
                        title = current?.displayName ?: "?",
                        seed = current?.id ?: "me",
                        size = 72.dp,
                        avatarUrl = current?.avatarUrl.orEmpty(),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = current?.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = current?.phone.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NokatTheme.colors.onTopBarSecondary,
                        )
                        if (!current?.username.isNullOrEmpty()) {
                            Text(
                                text = "@" + current?.username.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NokatTheme.colors.onTopBarSecondary,
                            )
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.profile)) {
                ProfileFields(vm)
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = stringResource(R.string.save),
                    onClick = vm::save,
                    enabled = vm.isDirty(user) && vm.name.isNotBlank(),
                    loading = vm.saving,
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { confirmLogout = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.logout),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.server_label, BuildConfig.API_BASE_URL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        vm.logout()
                    },
                ) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
