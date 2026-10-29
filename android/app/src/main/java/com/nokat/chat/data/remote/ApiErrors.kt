package com.nokat.chat.data.remote

import com.nokat.chat.R
import com.nokat.chat.util.UiText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/** HTTP status code if this is an API error, else null. */
val Throwable.httpCode: Int?
    get() = (this as? HttpException)?.code()

/** Maps a failure to a user-visible message. Uses the backend `{"error": "..."}` text when present. */
fun Throwable.toUiText(): UiText = when (this) {
    is HttpException -> serverMessage()?.let { UiText.Raw(it) } ?: UiText.Res(R.string.error_server)
    is IOException -> UiText.Res(R.string.error_network)
    else -> UiText.Res(R.string.error_unknown)
}

private fun HttpException.serverMessage(): String? = try {
    // Retrofit buffers error bodies, so reading here does not hit the network.
    val raw = response()?.errorBody()?.string()
    if (raw.isNullOrBlank()) {
        null
    } else {
        Json.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }
} catch (e: Exception) {
    null
}
