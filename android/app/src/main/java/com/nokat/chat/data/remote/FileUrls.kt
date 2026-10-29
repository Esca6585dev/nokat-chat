package com.nokat.chat.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Attachment URLs are root-relative (`/api/v1/files/x.jpg`). They are resolved against the API
 * origin (`scheme://host[:port]`) of the configured base URL.
 */
class FileUrls(baseUrl: String) {

    val origin: String = run {
        val url = baseUrl.toHttpUrlOrNull()
        if (url == null) {
            baseUrl.substringBefore("/api/").trimEnd('/')
        } else {
            val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
            "${url.scheme}://${url.host}$port"
        }
    }

    fun resolve(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> origin + url
        else -> "$origin/$url"
    }
}
