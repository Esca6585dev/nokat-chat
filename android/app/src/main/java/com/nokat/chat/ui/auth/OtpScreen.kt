package com.nokat.chat.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nokat.chat.R
import com.nokat.chat.ui.common.GradientButton
import com.nokat.chat.ui.common.StatusBarAppearance
import com.nokat.chat.ui.common.nokatViewModel
import com.nokat.chat.util.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    phone: String,
    initialDevCode: String?,
    onBack: () -> Unit,
    onVerified: (needsProfile: Boolean) -> Unit,
) {
    val vm = nokatViewModel { OtpViewModel(phone, initialDevCode, it.authRepository) }
    val currentOnVerified by rememberUpdatedState(onVerified)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is OtpViewModel.Event.Verified -> {
                    keyboard?.hide()
                    currentOnVerified(event.needsProfile)
                }
                OtpViewModel.Event.CodeResent ->
                    snackbarHostState.showSnackbar(context.getString(R.string.otp_code_resent))
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(vm.errorCount) {
        if (vm.errorCount > 0) {
            for (target in listOf(-18f, 16f, -12f, 8f, -4f, 0f)) {
                shake.animateTo(target, tween(durationMillis = 45))
            }
        }
    }

    StatusBarAppearance(darkIcons = !isSystemInDarkTheme())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.otp_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.otp_subtitle, vm.phone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            val devCode = vm.devCode
            AnimatedVisibility(
                visible = devCode != null,
                enter = fadeIn() + expandVertically(),
            ) {
                DevCodeBanner(
                    code = devCode.orEmpty(),
                    onClick = vm::fillDevCode,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            // One hidden text field drives the six boxes.
            BasicTextField(
                value = vm.code,
                onValueChange = vm::onCodeChange,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                decorationBox = { _ ->
                    OtpBoxes(code = vm.code, isError = vm.error != null)
                },
            )

            val error = vm.error
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))
            GradientButton(
                text = stringResource(R.string.verify),
                onClick = vm::verify,
                enabled = vm.code.length == OtpViewModel.CODE_LENGTH,
                loading = vm.loading,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = vm::resend, enabled = vm.secondsLeft == 0 && !vm.loading) {
                val seconds = vm.secondsLeft
                Text(
                    if (seconds > 0) {
                        stringResource(R.string.otp_resend_in, "0:%02d".format(seconds))
                    } else {
                        stringResource(R.string.otp_resend)
                    },
                )
            }
        }
    }
}

@Composable
private fun OtpBoxes(code: String, isError: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(OtpViewModel.CODE_LENGTH) { index ->
            val char = code.getOrNull(index)
            val focused = index == code.length ||
                (index == OtpViewModel.CODE_LENGTH - 1 && code.length == OtpViewModel.CODE_LENGTH)
            val borderColor by animateColorAsState(
                targetValue = when {
                    isError -> MaterialTheme.colorScheme.error
                    focused -> MaterialTheme.colorScheme.primary
                    char != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outline
                },
                label = "otpBorder",
            )
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = char?.toString().orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DevCodeBanner(code: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.otp_dev_code, code),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = stringResource(R.string.otp_dev_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )
    }
}
