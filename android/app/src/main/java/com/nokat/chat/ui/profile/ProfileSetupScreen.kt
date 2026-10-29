package com.nokat.chat.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nokat.chat.R
import com.nokat.chat.ui.common.Avatar
import com.nokat.chat.ui.common.GradientButton
import com.nokat.chat.ui.common.StatusBarAppearance
import com.nokat.chat.ui.common.nokatViewModel

@Composable
fun ProfileSetupScreen(onDone: () -> Unit) {
    val vm = nokatViewModel { ProfileViewModel(it.userRepository, it.authRepository, it.sessionManager) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(vm) { vm.saved.collect { currentOnDone() } }

    StatusBarAppearance(darkIcons = !isSystemInDarkTheme())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Avatar(
            title = vm.name.ifBlank { "?" },
            seed = vm.user.value?.id ?: "me",
            size = 96.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.profile_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.profile_setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        ProfileFields(vm)
        Spacer(Modifier.height(24.dp))
        GradientButton(
            text = stringResource(R.string.continue_btn),
            onClick = vm::save,
            enabled = vm.name.isNotBlank(),
            loading = vm.saving,
        )
        Spacer(Modifier.height(24.dp))
    }
}
