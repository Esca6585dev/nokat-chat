package com.nokat.chat.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens [url] with ACTION_VIEW (browser / viewer downloads it). Returns false if no app can handle it. */
fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** Shares [url] as plain text through the system share sheet. */
fun Context.shareUrl(url: String, chooserTitle: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(send, chooserTitle))
    true
} catch (e: ActivityNotFoundException) {
    false
}
