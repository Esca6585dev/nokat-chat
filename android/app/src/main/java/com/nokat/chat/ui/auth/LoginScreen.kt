package com.nokat.chat.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nokat.chat.R
import com.nokat.chat.ui.common.GradientButton
import com.nokat.chat.ui.common.StatusBarAppearance
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.ui.theme.BrandPrimary
import com.nokat.chat.util.asString

@Composable
fun LoginScreen(onCodeSent: (phone: String, devCode: String?) -> Unit) {
    val vm = nokatViewModel { LoginViewModel(it.authRepository) }
    val currentOnCodeSent by rememberUpdatedState(onCodeSent)
    val focusManager = LocalFocusManager.current
    val error = vm.error

    LaunchedEffect(vm) {
        vm.events.collect { currentOnCodeSent(it.phone, it.devCode) }
    }

    StatusBarAppearance(darkIcons = !isSystemInDarkTheme())

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Soft brand glow at the top.
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(BrandPrimary.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(26.dp), clip = false),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            OutlinedTextField(
                value = vm.phone,
                onValueChange = vm::onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.phone_label)) },
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error.asString()) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        vm.submit()
                    },
                ),
            )
            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = stringResource(R.string.send_code),
                onClick = {
                    focusManager.clearFocus()
                    vm.submit()
                },
                loading = vm.loading,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.login_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
