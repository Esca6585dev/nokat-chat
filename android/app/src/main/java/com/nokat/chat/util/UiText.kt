package com.nokat.chat.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

/** Text that is either a raw string (e.g. a server message) or a string resource. */
@Immutable
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Res(@StringRes val id: Int) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> stringResource(id)
}
