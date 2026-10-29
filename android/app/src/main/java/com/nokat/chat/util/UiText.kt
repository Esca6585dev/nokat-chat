package com.nokat.chat.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

/** Text that is either a raw string (e.g. a server message) or a string resource. */
@Immutable
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
}

/** Non-composable variant (e.g. for snackbars shown from a coroutine). */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
}
